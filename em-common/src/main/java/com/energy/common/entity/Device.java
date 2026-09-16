package com.energy.common.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 设备表实体。
 */
@Data
@TableName("device")
public class Device {

    /**
     * 主键即协议帧中的设备ID（如 1001），由接入方分配，不使用数据库自增。
     * 因此这里用 {@link IdType#INPUT} 而非 {@code AUTO}——用 AUTO 会导致
     * MyBatis-Plus 生成不含 id 的 INSERT，与协议设备ID 脱节。
     */
    @TableId(type = IdType.INPUT)
    private Long id;

    /** 业务设备编码，如 METER-A-001 */
    private String deviceCode;

    private String name;

    /** METER / INVERTER / TRANSFORMER */
    private String type;

    private String location;

    /** 设备密钥，用于设备侧接入鉴权（区别于用户 JWT） */
    private String secretKey;

    /** 1 在线 0 离线 */
    private Integer status;

    private LocalDateTime lastOnlineAt;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
