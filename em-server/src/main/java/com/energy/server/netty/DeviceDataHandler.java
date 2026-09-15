package com.energy.server.netty;

import com.energy.common.protocol.DataPayload;
import com.energy.common.protocol.DeviceFrame;
import com.energy.common.protocol.ProtocolConstants;
import com.energy.server.service.DeviceDataProcessor;
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
 * 设备帧业务分发：按命令字分流，并负责心跳超时后的连接清理。
 */
@Slf4j
@RequiredArgsConstructor
public class DeviceDataHandler extends SimpleChannelInboundHandler<DeviceFrame> {

    private static final DateTimeFormatter TIME_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault());

    private final DeviceDataProcessor processor;

    @Override
    public void channelActive(ChannelHandlerContext ctx) {
        log.info("设备连接建立: {}", ctx.channel().remoteAddress());
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) {
        log.info("设备连接断开: {}", ctx.channel().remoteAddress());
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, DeviceFrame frame) {
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
            log.warn("心跳超时（{} 秒未收到任何数据），主动关闭连接: {}",
                    ProtocolConstants.READER_IDLE_TIMEOUT_SECONDS, ctx.channel().remoteAddress());
            ctx.close();
            return;
        }
        super.userEventTriggered(ctx, evt);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        log.error("连接处理异常，关闭连接: {}", ctx.channel().remoteAddress(), cause);
        ctx.close();
    }
}
