package com.energy.common.protocol;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("TCP 粘包 / 拆包处理")
class DeviceFramePacketTest {

    private static LengthFieldBasedFrameDecoder newDecoder() {
        return new LengthFieldBasedFrameDecoder(
                ProtocolConstants.MAX_FRAME_LENGTH,
                ProtocolConstants.OFFSET_LENGTH,
                ProtocolConstants.LENGTH_FIELD_SIZE,
                0,
                0);
    }

    private static DataPayload payload() {
        return DataPayload.builder()
                .voltage(220.5f).current(5.25f).power(1157.625f)
                .collectTime(1_757_976_000_000L)
                .build();
    }

    @Test
    @DisplayName("粘包：一次写入两帧，应被切成两个完整帧")
    void stickyPacket_splitsIntoTwoFrames() {
        ByteBuf sticky = Unpooled.buffer();
        DeviceFrameEncoder.encode(DeviceFrame.dataReport(1001L, payload()), sticky);
        DeviceFrameEncoder.encode(DeviceFrame.dataReport(1002L, payload()), sticky);

        EmbeddedChannel channel = new EmbeddedChannel(newDecoder());
        assertTrue(channel.writeInbound(sticky), "两个完整帧应都被解码器消费");

        ByteBuf first = channel.readInbound();
        ByteBuf second = channel.readInbound();
        assertNotNull(first);
        assertNotNull(second);
        assertEquals(32, first.readableBytes());
        assertEquals(32, second.readableBytes());
        assertNull(channel.readInbound(), "不应有第三帧");

        first.release();
        second.release();
        channel.finish();
    }

    @Test
    @DisplayName("拆包：半包到达时不产出帧，补齐后才产出")
    void halfPacket_waitsForRemainder() {
        ByteBuf whole = Unpooled.buffer();
        DeviceFrameEncoder.encode(DeviceFrame.dataReport(1001L, payload()), whole);
        byte[] bytes = new byte[whole.readableBytes()];
        whole.readBytes(bytes);
        assertEquals(32, bytes.length);

        EmbeddedChannel channel = new EmbeddedChannel(newDecoder());

        ByteBuf head = Unpooled.wrappedBuffer(bytes, 0, 15);
        assertFalse(channel.writeInbound(head), "半包不应产出完整帧");
        assertNull(channel.readInbound());

        ByteBuf tail = Unpooled.wrappedBuffer(bytes, 15, bytes.length - 15);
        assertTrue(channel.writeInbound(tail), "补齐后应产出完整帧");

        ByteBuf frame = channel.readInbound();
        assertNotNull(frame);
        assertEquals(32, frame.readableBytes());

        frame.release();
        channel.finish();
    }

    @Test
    @DisplayName("半包拆出的帧应能被业务解码器正确解析")
    void reassembledFrame_decodesCorrectly() {
        ByteBuf whole = Unpooled.buffer();
        DeviceFrameEncoder.encode(DeviceFrame.dataReport(2048L, payload()), whole);
        byte[] bytes = new byte[whole.readableBytes()];
        whole.readBytes(bytes);

        EmbeddedChannel channel = new EmbeddedChannel(newDecoder());
        channel.writeInbound(Unpooled.wrappedBuffer(bytes, 0, 7));
        channel.writeInbound(Unpooled.wrappedBuffer(bytes, 7, bytes.length - 7));

        ByteBuf frame = channel.readInbound();
        assertNotNull(frame);

        DeviceFrame decoded = DeviceFrameDecoder.decode(frame);
        assertEquals(2048L, decoded.getDeviceId());
        assertEquals(220.5f, decoded.asDataPayload().getVoltage(), 0.001f);

        frame.release();
        channel.finish();
    }
}
