package com.energy.common.protocol;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

@DisplayName("CRC16 (Modbus) 校验算法")
class Crc16Test {

    @Test
    @DisplayName("空数组返回初始值 0xFFFF")
    void emptyArray_returnsInitialValue() {
        assertEquals(0xFFFF, Crc16.calculate(new byte[0]));
    }

    @Test
    @DisplayName("标准测试向量 [0x01] 应得到 0x807E")
    void knownVector_singleByte() {
        assertEquals(0x807E, Crc16.calculate(new byte[]{0x01}));
    }

    @Test
    @DisplayName("标准测试向量 \"123456789\" 应得到 0x4B37")
    void knownVector_asciiDigits() {
        byte[] data = "123456789".getBytes(java.nio.charset.StandardCharsets.US_ASCII);
        assertEquals(0x4B37, Crc16.calculate(data));
    }

    @Test
    @DisplayName("相同数据重复计算结果稳定")
    void deterministic() {
        byte[] data = {0x01, 0x02, 0x03, 0x04};
        assertEquals(Crc16.calculate(data), Crc16.calculate(data));
    }

    @Test
    @DisplayName("单比特翻转必须被检出（不同数据得到不同 CRC）")
    void singleBitFlip_changesResult() {
        byte[] a = {0x12, 0x34, 0x56, 0x78};
        byte[] b = {0x12, 0x34, 0x56, 0x79};
        org.junit.jupiter.api.Assertions.assertNotEquals(Crc16.calculate(a), Crc16.calculate(b));
    }
}
