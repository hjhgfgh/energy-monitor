package com.energy.common.protocol;

/**
 * Modbus CRC16 校验（多项式 0xA001，初值 0xFFFF）。
 *
 * <p>校验范围：从版本字段到载荷结束（排除魔数与 CRC 自身）。
 */
public final class Crc16 {

    private static final int POLYNOMIAL = 0xA001;
    private static final int INITIAL_VALUE = 0xFFFF;

    /** 预计算查表，避免逐位运算 */
    private static final int[] TABLE = new int[256];

    static {
        for (int i = 0; i < 256; i++) {
            int crc = i;
            for (int bit = 0; bit < 8; bit++) {
                if ((crc & 0x0001) != 0) {
                    crc = (crc >>> 1) ^ POLYNOMIAL;
                } else {
                    crc >>>= 1;
                }
            }
            TABLE[i] = crc;
        }
    }

    private Crc16() {
    }

    /**
     * 计算 CRC16 值。
     *
     * @param data 待校验字节
     * @return 16 位无符号校验值（以 int 承载，范围 0x0000-0xFFFF）
     */
    public static int calculate(byte[] data) {
        int crc = INITIAL_VALUE;
        for (byte b : data) {
            crc = (crc >>> 8) ^ TABLE[(crc ^ b) & 0xFF];
        }
        return crc;
    }

    /** 计算 CRC16 的低字节（协议规定低字节在前） */
    public static byte lowByte(int crc) {
        return (byte) (crc & 0xFF);
    }

    /** 计算 CRC16 的高字节 */
    public static byte highByte(int crc) {
        return (byte) ((crc >>> 8) & 0xFF);
    }

    /** 由低字节在前的方式还原 CRC 值 */
    public static int fromLowHigh(byte low, byte high) {
        return ((high & 0xFF) << 8) | (low & 0xFF);
    }
}
