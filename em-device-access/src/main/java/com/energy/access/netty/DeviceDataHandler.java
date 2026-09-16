package com.energy.access.netty;

import com.energy.access.service.DeviceDataProcessor;
import com.energy.access.service.OnlineDeviceRegistry;
import com.energy.common.protocol.DataPayload;
import com.energy.common.protocol.DeviceFrame;
import com.energy.common.protocol.ProtocolConstants;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.timeout.IdleState;
import io.netty.handler.timeout.IdleStateEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * 设备帧业务分发：按命令字分流、维护在线状态、处理心跳超时。
 *
 * <p>每个设备连接对应一个本类实例（由 {@code DeviceChannelInitializer} 为每个 channel 创建），
 * 因此 {@link #deviceId} 这样的实例字段天然是「连接级」状态，无需额外的 Map 管理。
 */
@Slf4j
@RequiredArgsConstructor
public class DeviceDataHandler extends SimpleChannelInboundHandler<DeviceFrame> {

    private static final DateTimeFormatter TIME_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault());

    private final DeviceDataProcessor processor;
    private final OnlineDeviceRegistry registry;

    /**
     * 本连接对应的设备ID。
     *
     * <p>TCP 连接建立时并不知道对端是哪台设备——设备身份在协议帧里。
     * 所以要等首帧到达后才能确定，这也是为什么连接建立的日志里只打印远端地址。
     */
    private Long deviceId;

    @Override
    public void channelActive(ChannelHandlerContext ctx) {
        log.info("设备连接建立: {}（等待首帧确定设备身份）", ctx.channel().remoteAddress());
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) {
        if (deviceId != null) {
            registry.markOffline(deviceId);
        }
        log.info("设备连接断开: {} (deviceId={})", ctx.channel().remoteAddress(), deviceId);
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, DeviceFrame frame) {
        // 首帧到达，绑定设备身份并登记在线
        if (deviceId == null) {
            deviceId = frame.getDeviceId();
            registry.markOnline(deviceId);
        }

        switch (frame.getCommand()) {
            case HEARTBEAT -> log.debug("收到心跳: deviceId={}", frame.getDeviceId());

            case DATA_REPORT -> {
                DataPayload payload = frame.asDataPayload();
                log.info("收到数据上报: deviceId={}, 电压={}V, 电流={}A, 功率={}W, 采集时间={}",
                        frame.getDeviceId(),
                        payload.getVoltage(),
                        payload.getCurrent(),
                        payload.getPower(),
                        TIME_FORMAT.format(Instant.ofEpochMilli(payload.getCollectTime())));
                processor.process(frame);
            }

            case REGISTER -> log.info("收到设备注册: deviceId={}, 设备编码={}",
                    frame.getDeviceId(), frame.asText());

            default -> log.warn("未处理的命令字: {}", frame.getCommand());
        }
    }

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
        if (evt instanceof IdleStateEvent event && event.state() == IdleState.READER_IDLE) {
            log.warn("心跳超时（{} 秒未收到任何数据），主动关闭连接: {} (deviceId={})",
                    ProtocolConstants.READER_IDLE_TIMEOUT_SECONDS,
                    ctx.channel().remoteAddress(), deviceId);
            ctx.close();
            return;
        }
        super.userEventTriggered(ctx, evt);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        log.error("连接处理异常，关闭连接: {} (deviceId={})",
                ctx.channel().remoteAddress(), deviceId, cause);
        ctx.close();
    }
}
