package com.energy.common.protocol;

/**
 * 设备接入协议常量。
 *
 * <p>帧结构（大端）：
 * <pre>
 * 偏移  长度  字段
 *  0    2    魔数 magic
 *  2    1    版本 version
 *  3    1    命令字 cmd
 *  4    2    长度 length（表示该字段之后到帧尾的字节数）
 *  6    4    设备ID deviceId
 * 10    变长  载荷 payload
 * 尾    2    CRC16
 * </pre>
 */
public final class ProtocolConstants {

    private ProtocolConstants() {
    }

    /** 魔数 0xEB90，用于快速识别帧起始 */
    public static final int MAGIC = 0xEB90;

    /** 协议版本 */
    public static final byte VERSION = 0x01;

    /** 最大帧长，防止非法 length 值撑爆内存 */
    public static final int MAX_FRAME_LENGTH = 1024;

    /** 固定开销：魔数2 + 版本1 + 命令字1 + 长度2 + 设备ID4 + CRC2 */
    public static final int FIXED_OVERHEAD = 12;

    /** 数据上报载荷长度：电压4 + 电流4 + 功率4 + 采集时间8 */
    public static final int DATA_PAYLOAD_LENGTH = 20;

    public static final int OFFSET_MAGIC = 0;
    public static final int OFFSET_VERSION = 2;
    public static final int OFFSET_COMMAND = 3;
    public static final int OFFSET_LENGTH = 4;
    public static final int OFFSET_DEVICE_ID = 6;
    public static final int OFFSET_PAYLOAD = 10;

    /** length 字段占用字节数 */
    public static final int LENGTH_FIELD_SIZE = 2;

    /** CRC 字段占用字节数 */
    public static final int CRC_SIZE = 2;

    /** CRC 校验覆盖范围起始偏移（从版本字段开始，跳过魔数） */
    public static final int CRC_RANGE_START = OFFSET_VERSION;

    /** 心跳周期（秒） */
    public static final int HEARTBEAT_INTERVAL_SECONDS = 30;

    /** 读空闲超时（秒）：3 个心跳周期，容忍两次丢包 */
    public static final int READER_IDLE_TIMEOUT_SECONDS = 90;
}
