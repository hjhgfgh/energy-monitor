package com.energy.common.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 设备上报数据实体。
 *
 * <p>用 {@link BigDecimal} 而非 float/double 承接 DECIMAL 列：
 * 电力计量对精度敏感，二进制浮点数会出现 0.1 + 0.2 类的误差。
 * 协议层用 float 传输是带宽考虑，落库时转换为精确十进制。
 */
@Data
@TableName("device_data")
public class DeviceData {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long deviceId;

    /** 电压 V */
    private BigDecimal voltage;

    /** 电流 A，对应列 electric_current */
    private BigDecimal electricCurrent;

    /** 功率 W */
    private BigDecimal power;

    /** 设备侧采集时间 */
    private LocalDateTime collectTime;

    /** 服务端入库时间 */
    private LocalDateTime createdAt;
}
