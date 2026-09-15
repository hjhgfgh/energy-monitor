package com.energy.common.protocol;

import io.netty.buffer.ByteBuf;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * 帧编码器：把 {@link DeviceFrame} 序列化为字节流。
 *
 * <p>实现要点：先按 CRC 覆盖范围构造待校验字节数组，算出 CRC 后再一次性写入 ByteBuf。
 * 这样写出的字节流天然满足协议定义的字段顺序，也避免了边写边回读缓冲区。
 */
public final class DeviceFrameEncoder {

    private DeviceFrameEncoder() {
    }

    /**
     * 编码一个完整帧。
     *
     * @param frame 待编码帧
     * @param out   输出缓冲区，readerIndex 不变，writerIndex 前移
     */
    public static void encode(DeviceFrame frame, ByteBuf out) {
        byte[] payload = frame.getPayload();

        // length 字段语义：其后的全部字节数 = 设备ID(4) + 载荷 + CRC(2)
        int length = 4 + payload.length + ProtocolConstants.CRC_SIZE;

        // CRC 覆盖：版本 + 命令字 + 长度 + 设备ID + 载荷（即跳过魔数，到载荷结束）
        byte[] crcInput = new byte[1 + 1 + ProtocolConstants.LENGTH_FIELD_SIZE + 4 + payload.length];
        ByteBuffer crcBuffer = ByteBuffer.wrap(crcInput).order(ByteOrder.BIG_ENDIAN);
        crcBuffer.put(frame.getVersion());
        crcBuffer.put(frame.getCommand().getCode());
        crcBuffer.putShort((short) length);
        crcBuffer.putInt((int) frame.getDeviceId());
        crcBuffer.put(payload);

        int crc = Crc16.calculate(crcInput);

        out.writeShort(ProtocolConstants.MAGIC);
        out.writeBytes(crcInput);
        out.writeByte(Crc16.lowByte(crc));
        out.writeByte(Crc16.highByte(crc));
    }

    /**
     * 估算编码后的帧长度，用于预分配缓冲区。
     */
    public static int estimateLength(DeviceFrame frame) {
        return ProtocolConstants.FIXED_OVERHEAD + frame.getPayload().length;
    }
}
