package com.energy.common.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 告警记录实体。
 */
@Data
@TableName("alarm")
public class Alarm {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long deviceId;

    private Long ruleId;

    /** 告警级别: 1提示 2警告 3严重 */
    private Integer level;

    /** 触发告警时的指标实际值 */
    private BigDecimal metricValue;

    private String content;

    /** 0 未处理 1 已处理 */
    private Integer status;

    private LocalDateTime createdAt;

    private LocalDateTime handledAt;
}
