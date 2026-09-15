package com.energy.common.protocol;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 数据上报载荷，对应 DATA_REPORT 命令字，序列化后固定 20 字节（大端）。
 *
 * <p>字段顺序：电压(4B float) → 电流(4B float) → 功率(4B float) → 采集时间(8B long)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DataPayload {

    /** 电压，单位 V */
    private float voltage;

    /** 电流，单位 A */
    private float current;

    /** 功率，单位 W */
    private float power;

    /** 采集时间，毫秒时间戳 */
    private long collectTime;
}
