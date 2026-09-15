package com.energy.common.protocol;

import io.netty.buffer.ByteBuf;

/**
 * 帧解码器：把一段（已被 {@code LengthFieldBasedFrameDecoder} 切分好的）字节流解析为 {@link DeviceFrame}。
 *
 * <p>校验顺序刻意设计为「魔数 → 帧长一致性 → CRC」：
 * 魔数最便宜且能最快排除错位数据，CRC 最贵，放在最后。
 */
public final class DeviceFrameDecoder {

    private DeviceFrameDecoder() {
    }

    /**
     * 解析一个完整帧。
     *
     * <p>不改变传入 ByteBuf 的 readerIndex，由调用方决定何时释放。
     *
     * @throws ProtocolException 帧长不足、魔数不匹配、帧长与 length 字段不一致、CRC 校验失败
     */
    public static DeviceFrame decode(ByteBuf in) {
        int totalLength = in.readableBytes();
        if (totalLength < ProtocolConstants.FIXED_OVERHEAD) {
            throw new ProtocolException(String.format(
                    "帧长度不足，至少需要 %d 字节，实际 %d 字节",
                    ProtocolConstants.FIXED_OVERHEAD, totalLength));
        }

        int magic = in.getUnsignedShort(ProtocolConstants.OFFSET_MAGIC);
        if (magic != ProtocolConstants.MAGIC) {
            throw new ProtocolException(String.format(
                    "魔数校验失败，期望 0x%04X，实际 0x%04X",
                    ProtocolConstants.MAGIC, magic));
        }

        byte version = in.getByte(ProtocolConstants.OFFSET_VERSION);
        CommandType command = CommandType.fromCode(in.getByte(ProtocolConstants.OFFSET_COMMAND));
        int length = in.getUnsignedShort(ProtocolConstants.OFFSET_LENGTH);
        long deviceId = in.getUnsignedInt(ProtocolConstants.OFFSET_DEVICE_ID);

        // 帧总长 = 魔数(2) + 版本(1) + 命令字(1) + 长度字段(2) + length 值
        int expectedTotal = 2 + 1 + 1 + ProtocolConstants.LENGTH_FIELD_SIZE + length;
        if (expectedTotal != totalLength) {
            throw new ProtocolException(String.format(
                    "帧长与 length 字段不一致，length 字段推算为 %d 字节，实际可读 %d 字节",
                    expectedTotal, totalLength));
        }

        int payloadLength = length - 4 - ProtocolConstants.CRC_SIZE;
        if (payloadLength < 0) {
            throw new ProtocolException("非法载荷长度: " + payloadLength);
        }

        byte[] payload = new byte[payloadLength];
        in.getBytes(ProtocolConstants.OFFSET_PAYLOAD, payload);

        int crcOffset = ProtocolConstants.OFFSET_PAYLOAD + payloadLength;
        int actualCrc = Crc16.fromLowHigh(in.getByte(crcOffset), in.getByte(crcOffset + 1));

        byte[] crcInput = new byte[1 + 1 + ProtocolConstants.LENGTH_FIELD_SIZE + 4 + payloadLength];
        in.getBytes(ProtocolConstants.OFFSET_VERSION, crcInput);
        int expectedCrc = Crc16.calculate(crcInput);

        if (expectedCrc != actualCrc) {
            throw new ProtocolException(String.format(
                    "CRC 校验失败，期望 0x%04X，实际 0x%04X", expectedCrc, actualCrc));
        }

        return new DeviceFrame(version, command, deviceId, payload);
    }
}
