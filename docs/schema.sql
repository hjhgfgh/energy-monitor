-- ============================================================
-- 智慧能源监控系统 数据库初始化脚本
-- MySQL 8.0
-- 执行方式: mysql -uroot -p --default-character-set=utf8mb4 -e "SOURCE docs/schema.sql"
-- Docker initdb 挂载执行时客户端默认字符集不可控（实测中文变乱码），
-- SET NAMES 强制本会话按 utf8mb4 解释后续字节流，两种执行方式都正确
-- ============================================================

SET NAMES utf8mb4;

CREATE DATABASE IF NOT EXISTS energy_monitor
    DEFAULT CHARACTER SET utf8mb4
    DEFAULT COLLATE utf8mb4_general_ci;

USE energy_monitor;

-- ------------------------------------------------------------
-- 设备表
--
-- 设计说明：主键 id 直接采用协议帧中的 4 字节设备ID（如 1001），不使用自增。
-- 这样协议层、存储层、接口层是同一个编号，不需要额外的映射表；
-- 代价是新增设备必须由接入方分配编号（现实中设备出厂即带编号，本来就该如此）。
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS device (
    id             BIGINT       NOT NULL COMMENT '设备ID（主键），即协议帧中的设备ID',
    device_code    VARCHAR(64)  NOT NULL COMMENT '业务设备编码，如 METER-A-001',
    name           VARCHAR(128) NOT NULL COMMENT '设备名称',
    type           VARCHAR(32)  NOT NULL COMMENT '设备类型: METER/INVERTER/TRANSFORMER',
    location       VARCHAR(128) DEFAULT NULL COMMENT '安装位置',
    secret_key     VARCHAR(64)  NOT NULL COMMENT '设备密钥，用于设备侧接入鉴权',
    status         TINYINT      NOT NULL DEFAULT 0 COMMENT '状态: 1在线 0离线',
    last_online_at DATETIME     DEFAULT NULL COMMENT '最后在线时间',
    created_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_device_code (device_code)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='设备表';

-- ------------------------------------------------------------
-- 设备上报数据表
-- 这张表是索引优化演示的主角，预期数据量千万级
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS device_data (
    id               BIGINT         NOT NULL AUTO_INCREMENT COMMENT '主键',
    device_id        BIGINT         NOT NULL COMMENT '设备ID，关联 device.id',
    voltage          DECIMAL(10, 2) DEFAULT NULL COMMENT '电压 V',
    electric_current DECIMAL(10, 2) DEFAULT NULL COMMENT '电流 A（避开 MySQL 关键字）',
    power            DECIMAL(10, 2) DEFAULT NULL COMMENT '功率 W',
    collect_time     DATETIME(3)    NOT NULL COMMENT '设备侧采集时间（毫秒精度）',
    created_at       DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '服务端入库时间',
    PRIMARY KEY (id),
    -- 唯一索引而非普通索引：既是查询走 range 扫描的基础，
    -- 也是消费端幂等的依据（重复消费时 INSERT ... ON DUPLICATE KEY UPDATE 会命中它）
    UNIQUE KEY uk_device_time (device_id, collect_time)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='设备上报数据表';

-- ------------------------------------------------------------
-- 告警规则表
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS alarm_rule (
    id          BIGINT         NOT NULL AUTO_INCREMENT COMMENT '主键',
    name        VARCHAR(128)   NOT NULL COMMENT '规则名称',
    device_type VARCHAR(32)    DEFAULT NULL COMMENT '适用设备类型，NULL 表示全部',
    metric      VARCHAR(32)    NOT NULL COMMENT '监控指标: voltage/electric_current/power',
    operator    VARCHAR(8)     NOT NULL COMMENT '比较符: GT/LT/GE/LE',
    threshold   DECIMAL(10, 2) NOT NULL COMMENT '阈值',
    level       TINYINT        NOT NULL DEFAULT 1 COMMENT '告警级别: 1提示 2警告 3严重',
    enabled     TINYINT        NOT NULL DEFAULT 1 COMMENT '是否启用',
    created_at  DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_device_type_enabled (device_type, enabled)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='告警规则表';

-- ------------------------------------------------------------
-- 告警记录表
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS alarm (
    id           BIGINT         NOT NULL AUTO_INCREMENT COMMENT '主键',
    device_id    BIGINT         NOT NULL COMMENT '设备ID，关联 device.id',
    rule_id      BIGINT         NOT NULL COMMENT '触发的规则ID',
    level        TINYINT        NOT NULL COMMENT '告警级别',
    metric_value DECIMAL(10, 2) DEFAULT NULL COMMENT '触发时的指标值',
    content      VARCHAR(255)   NOT NULL COMMENT '告警内容',
    status       TINYINT        NOT NULL DEFAULT 0 COMMENT '状态: 0未处理 1已处理',
    created_at   DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    handled_at   DATETIME       DEFAULT NULL,
    PRIMARY KEY (id),
    KEY idx_device_created (device_id, created_at),
    KEY idx_status_created (status, created_at)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='告警记录表';

-- ------------------------------------------------------------
-- 系统用户表
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS sys_user (
    id         BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    username   VARCHAR(64)  NOT NULL COMMENT '登录名',
    password   VARCHAR(128) NOT NULL COMMENT 'BCrypt 加密后的密码',
    nickname   VARCHAR(64)  DEFAULT NULL COMMENT '昵称',
    role       VARCHAR(32)  NOT NULL DEFAULT 'USER' COMMENT '角色: ADMIN/USER',
    status     TINYINT      NOT NULL DEFAULT 1 COMMENT '状态: 1启用 0禁用',
    created_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_username (username)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='系统用户表';

-- ------------------------------------------------------------
-- 初始化数据
-- 设备ID 与模拟器 em-simulator 的起始设备ID 保持一致，否则接入数据无法关联到设备
-- ------------------------------------------------------------
INSERT INTO device (id, device_code, name, type, location, secret_key, status)
VALUES (1001, 'METER-A-001', '1号车间总表', 'METER', 'A栋1层配电间', 'sk-1001-a1b2c3', 0),
       (1002, 'METER-A-002', '2号车间总表', 'METER', 'A栋2层配电间', 'sk-1002-d4e5f6', 0),
       (1003, 'INV-ROOF-001', '光伏逆变器-01', 'INVERTER', '屋顶光伏区', 'sk-1003-g7h8i9', 0),
       (1004, 'METER-B-001', '3号车间总表', 'METER', 'B栋1层配电间', 'sk-1004-j1k2l3', 0),
       (1005, 'TRANS-B-001', '主变压器', 'TRANSFORMER', 'B栋变电站', 'sk-1005-m4n5o6', 0)
ON DUPLICATE KEY UPDATE name    = VALUES(name),
                        type    = VALUES(type),
                        location = VALUES(location);

INSERT INTO alarm_rule (name, device_type, metric, operator, threshold, level, enabled)
VALUES ('电压过高告警', 'METER', 'voltage', 'GT', 240.00, 3, 1),
       ('电压过低告警', 'METER', 'voltage', 'LT', 200.00, 2, 1),
       ('电流过载告警', NULL, 'electric_current', 'GT', 10.00, 3, 1),
       ('功率异常告警', NULL, 'power', 'GT', 2000.00, 2, 1)
ON DUPLICATE KEY UPDATE name      = VALUES(name),
                        threshold = VALUES(threshold),
                        level     = VALUES(level);
