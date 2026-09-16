package com.energy.common.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 设备数据事件，即 Kafka 消息体。
 *
 * <p>不复用 {@code DeviceFrame} 是因为两者职责不同：帧是传输格式（含魔数、CRC 等校验字段），
 * 事件是业务语义（只保留电压/电流/功率/采集时间）。让它们各自独立演进，
 * 协议升级时不会牵连下游消费逻辑。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeviceDataEvent {

    private long deviceId;

    /** 电压 V */
    private float voltage;

    /** 电流 A */
    private float current;

    /** 功率 W */
    private float power;

    /** 设备侧采集时间，毫秒时间戳 */
    private long collectTime;
}
