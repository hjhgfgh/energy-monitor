package com.energy.common.protocol;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("设备帧编解码")
class DeviceFrameCodecTest {

    private static DataPayload samplePayload() {
        return DataPayload.builder()
                .voltage(220.5f)
                .current(5.25f)
                .power(1157.625f)
                .collectTime(1_757_976_000_000L)
                .build();
    }

    private static ByteBuf encodeToBuffer(DeviceFrame frame) {
        ByteBuf buf = Unpooled.buffer();
        DeviceFrameEncoder.encode(frame, buf);
        return buf;
    }

    @Test
    @DisplayName("编码后再解码应完整还原所有字段")
    void encodeThenDecode_roundTrip() {
        DeviceFrame original = DeviceFrame.dataReport(1001L, samplePayload());

        DeviceFrame decoded = DeviceFrameDecoder.decode(encodeToBuffer(original));

        assertEquals(ProtocolConstants.VERSION, decoded.getVersion());
        assertEquals(CommandType.DATA_REPORT, decoded.getCommand());
        assertEquals(1001L, decoded.getDeviceId());

        DataPayload payload = decoded.asDataPayload();
        assertEquals(220.5f, payload.getVoltage(), 0.001f);
        assertEquals(5.25f, payload.getCurrent(), 0.001f);
        assertEquals(1157.625f, payload.getPower(), 0.001f);
        assertEquals(1_757_976_000_000L, payload.getCollectTime());
    }

    @Test
    @DisplayName("数据上报帧总长 32 字节（12 固定开销 + 20 载荷）")
    void dataReportFrame_totalLength() {
        ByteBuf buf = encodeToBuffer(DeviceFrame.dataReport(1001L, samplePayload()));
        assertEquals(ProtocolConstants.FIXED_OVERHEAD + ProtocolConstants.DATA_PAYLOAD_LENGTH,
                buf.readableBytes());
        assertEquals(32, buf.readableBytes());
    }

    @Test
    @DisplayName("length 字段等于 设备ID(4) + 载荷(20) + CRC(2) = 26")
    void lengthField_semantics() {
        ByteBuf buf = encodeToBuffer(DeviceFrame.dataReport(1001L, samplePayload()));
        assertEquals(26, buf.getUnsignedShort(ProtocolConstants.OFFSET_LENGTH));
    }

    @Test
    @DisplayName("心跳帧总长 12 字节（无载荷）")
    void heartbeatFrame_totalLength() {
        ByteBuf buf = encodeToBuffer(DeviceFrame.heartbeat(1001L));
        assertEquals(12, buf.readableBytes());
        assertEquals(6, buf.getUnsignedShort(ProtocolConstants.OFFSET_LENGTH));
    }

    @Test
    @DisplayName("载荷被篡改时解码抛 CRC 校验异常")
    void corruptedPayload_throwsProtocolException() {
        ByteBuf buf = encodeToBuffer(DeviceFrame.dataReport(1001L, samplePayload()));
        buf.setByte(10, buf.getByte(10) + 1);

        ProtocolException ex = assertThrows(ProtocolException.class, () -> DeviceFrameDecoder.decode(buf));
        assertTrue(ex.getMessage().contains("CRC"), "异常信息应指明 CRC 校验失败，实际: " + ex.getMessage());
    }

    @Test
    @DisplayName("魔数不匹配时解码抛异常")
    void badMagic_throwsProtocolException() {
        ByteBuf buf = encodeToBuffer(DeviceFrame.dataReport(1001L, samplePayload()));
        buf.setByte(0, 0x00);

        ProtocolException ex = assertThrows(ProtocolException.class, () -> DeviceFrameDecoder.decode(buf));
        assertTrue(ex.getMessage().contains("魔数"), "异常信息应指明魔数错误，实际: " + ex.getMessage());
    }
}
