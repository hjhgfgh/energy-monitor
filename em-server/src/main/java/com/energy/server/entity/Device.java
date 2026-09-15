package com.energy.server.entity;

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

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 设备编码，与协议帧中的设备ID对应 */
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
