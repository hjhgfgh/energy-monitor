package com.energy.common.protocol;

/**
 * 命令字定义。0x01-0x7F 为设备上行，0x80-0xFF 为服务端下行。
 */
public enum CommandType {

    /** 数据上报（设备 → 服务端），载荷 20 字节 */
    DATA_REPORT((byte) 0x01),

    /** 心跳（设备 → 服务端），无载荷 */
    HEARTBEAT((byte) 0x02),

    /** 设备注册（设备 → 服务端），载荷为设备编码 UTF-8 字节串 */
    REGISTER((byte) 0x03),

    /** 指令下发（服务端 → 设备），载荷为指令字符串 UTF-8 字节串 */
    CMD_DOWN((byte) 0x81);

    private final byte code;

    CommandType(byte code) {
        this.code = code;
    }

    public byte getCode() {
        return code;
    }

    public static CommandType fromCode(byte code) {
        for (CommandType type : values()) {
            if (type.code == code) {
                return type;
            }
        }
        throw new ProtocolException("未知命令字: 0x" + String.format("%02X", code));
    }
}
