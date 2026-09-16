package com.energy.common.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 告警规则实体。
 *
 * <p>规则做成数据表而非硬编码，这样新增一条告警规则不需要改代码、不需要重启服务，
 * 也是后续「动态规则引擎」的落点。
 */
@Data
@TableName("alarm_rule")
public class AlarmRule {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String name;

    /** 适用设备类型，null 表示全部类型 */
    private String deviceType;

    /** 监控指标: voltage / electric_current / power */
    private String metric;

    /** 比较符: GT / LT / GE / LE */
    private String operator;

    private BigDecimal threshold;

    /** 告警级别: 1提示 2警告 3严重 */
    private Integer level;

    /** 是否启用: 1启用 0停用 */
    private Integer enabled;

    private LocalDateTime createdAt;
}
