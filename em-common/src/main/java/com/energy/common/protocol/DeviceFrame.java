package com.energy.common.protocol;

import lombok.Getter;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * 一个已解析（或待编码）的设备协议帧。
 */
@Getter
public class DeviceFrame {

    private final byte version;
    private final CommandType command;
    private final long deviceId;
    private final byte[] payload;

    public DeviceFrame(byte version, CommandType command, long deviceId, byte[] payload) {
        this.version = version;
        this.command = command;
        this.deviceId = deviceId;
        this.payload = payload == null ? new byte[0] : payload;
    }

    /** 构造数据上报帧 */
    public static DeviceFrame dataReport(long deviceId, DataPayload data) {
        return new DeviceFrame(ProtocolConstants.VERSION, CommandType.DATA_REPORT,
                deviceId, encodeDataPayload(data));
    }

    /** 构造心跳帧 */
    public static DeviceFrame heartbeat(long deviceId) {
        return new DeviceFrame(ProtocolConstants.VERSION, CommandType.HEARTBEAT,
                deviceId, new byte[0]);
    }

    /** 构造注册帧 */
    public static DeviceFrame register(long deviceId, String deviceCode) {
        return new DeviceFrame(ProtocolConstants.VERSION, CommandType.REGISTER,
                deviceId, deviceCode.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    /** 构造指令下发帧 */
    public static DeviceFrame commandDown(long deviceId, String command) {
        return new DeviceFrame(ProtocolConstants.VERSION, CommandType.CMD_DOWN,
                deviceId, command.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    /**
     * 把载荷解析为数据上报对象。
     *
     * @throws ProtocolException 当前帧不是数据上报帧，或载荷长度不足
     */
    public DataPayload asDataPayload() {
        if (command != CommandType.DATA_REPORT) {
            throw new ProtocolException("当前帧不是数据上报帧: " + command);
        }
        return decodeDataPayload(payload);
    }

    /** 把载荷解析为 UTF-8 文本（用于 REGISTER / CMD_DOWN） */
    public String asText() {
        return new String(payload, java.nio.charset.StandardCharsets.UTF_8);
    }

    static byte[] encodeDataPayload(DataPayload data) {
        ByteBuffer buffer = ByteBuffer.allocate(ProtocolConstants.DATA_PAYLOAD_LENGTH)
                .order(ByteOrder.BIG_ENDIAN);
        buffer.putFloat(data.getVoltage());
        buffer.putFloat(data.getCurrent());
        buffer.putFloat(data.getPower());
        buffer.putLong(data.getCollectTime());
        return buffer.array();
    }

    static DataPayload decodeDataPayload(byte[] payload) {
        if (payload.length < ProtocolConstants.DATA_PAYLOAD_LENGTH) {
            throw new ProtocolException(
                    "数据上报载荷长度不足，期望 " + ProtocolConstants.DATA_PAYLOAD_LENGTH
                            + " 字节，实际 " + payload.length + " 字节");
        }
        ByteBuffer buffer = ByteBuffer.wrap(payload).order(ByteOrder.BIG_ENDIAN);
        return DataPayload.builder()
                .voltage(buffer.getFloat())
                .current(buffer.getFloat())
                .power(buffer.getFloat())
                .collectTime(buffer.getLong())
                .build();
    }

    @Override
    public String toString() {
        return "DeviceFrame{version=" + version
                + ", command=" + command
                + ", deviceId=" + deviceId
                + ", payloadLength=" + payload.length + '}';
    }
}
