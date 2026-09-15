package com.energy.server.netty;

import com.energy.common.protocol.DeviceFrame;
import com.energy.common.protocol.DeviceFrameDecoder;
import com.energy.common.protocol.ProtocolException;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.atomic.AtomicLong;

/**
 * 把切分好的字节流解析为 {@link DeviceFrame}（内部完成魔数与 CRC 校验）。
 *
 * <p>设计取舍：单帧校验失败只丢这一帧、**不断开连接**。
 * 工业现场线路干扰会导致偶发位翻转，若因此断连，设备端会陷入「重连—再被踢」的循环，
 * 反而放大故障。这里选择丢帧并计数，让运维能从日志发现异常。
 */
@Slf4j
public class DeviceFrameDecoderHandler extends SimpleChannelInboundHandler<ByteBuf> {

    private final AtomicLong droppedFrames = new AtomicLong();

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, ByteBuf msg) {
        try {
            DeviceFrame frame = DeviceFrameDecoder.decode(msg);
            ctx.fireChannelRead(frame);
        } catch (ProtocolException e) {
            long total = droppedFrames.incrementAndGet();
            log.warn("协议解析失败，丢弃该帧（本连接累计 {} 帧），来源 {}：{}",
                    total, ctx.channel().remoteAddress(), e.getMessage());
        }
    }
}
