package com.energy.access.netty;

import com.energy.common.protocol.ProtocolConstants;
import com.energy.access.config.NettyProperties;
import com.energy.access.service.DeviceDataProcessor;
import com.energy.access.service.OnlineDeviceRegistry;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;
import io.netty.handler.timeout.IdleStateHandler;
import lombok.RequiredArgsConstructor;

import java.util.concurrent.TimeUnit;

/**
 * 每个设备连接建立时装配一次流水线。
 *
 * <p>顺序即处理顺序：
 * <pre>
 * IdleStateHandler                 心跳超时检测
 *   -> LengthFieldBasedFrameDecoder  按 length 字段切出完整帧
 *     -> DeviceFrameDecoderHandler   解析为 DeviceFrame（含魔数与 CRC 校验）
 *       -> DeviceDataHandler         业务分发
 * </pre>
 */
@RequiredArgsConstructor
public class DeviceChannelInitializer extends ChannelInitializer<SocketChannel> {

    private final NettyProperties properties;
    private final DeviceDataProcessor processor;
    private final OnlineDeviceRegistry registry;

    @Override
    protected void initChannel(SocketChannel ch) {
        ChannelPipeline pipeline = ch.pipeline();

        pipeline.addLast("idleStateHandler", new IdleStateHandler(
                properties.getReaderIdleTimeoutSeconds(), 0, 0, TimeUnit.SECONDS));

        pipeline.addLast("frameDecoder", new LengthFieldBasedFrameDecoder(
                properties.getMaxFrameLength(),
                ProtocolConstants.OFFSET_LENGTH,
                ProtocolConstants.LENGTH_FIELD_SIZE,
                0,
                0));

        pipeline.addLast("protocolDecoder", new DeviceFrameDecoderHandler());

        pipeline.addLast("businessHandler", new DeviceDataHandler(processor, registry));
    }
}
