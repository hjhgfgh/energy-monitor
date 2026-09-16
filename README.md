# energy-monitor —— 智慧能源监控系统

> 面向设备侧的能源监控全链路演示：**设备二进制协议 → TCP 接入 → Kafka 削峰 → 双消费组（落库/告警）→ Redis 缓存 → WebSocket 实时大屏**，微服务化部署，网关统一鉴权与限流。

## 架构总览

```
                          ┌─────────────────────────────────────────────┐
 设备/模拟器               │              微服务（Nacos 注册发现）          │
 ┌──────────┐   TCP:9000  │  ┌─────────────┐    ┌─────────────────────┐ │
 │ em-      │ ──────────► │  │ em-device-  │    │ em-gateway  :8090   │ │◄── 浏览器大屏
 │ simulator│  二进制帧    │  │ access      │    │ JWT / Sentinel 限流  │ │  (Vue3+ECharts)
 └──────────┘             │  │ Netty 接入  │    │ 静态资源 + WS 转发    │ │
                          │  │ Kafka 生产  │    └─────────────────────┘ │
                          │  └──────┬──────┘     ▲  ▲          ▲        │
                          │         ▼            │  │          │        │
                          │  ┌────────────┐  ┌───┴──┴──┐  ┌────┴─────┐ │
                          │  │ Kafka      │  │ em-web- │  │ em-data- │ │
                          │  │ topic:     │  │ api     │  │ process  │ │
                          │  │ device-data│─►│ 查询/登录│  │ 消费/落库 │ │
                          │  │ (3 分区)   │  │ Feign──►│  │ 告警/推送 │ │
                          │  └────────────┘  └─────────┘  └──────────┘ │
                          │      MySQL 8.0 ◄──┘   Redis 7 ◄──┘         │
                          └─────────────────────────────────────────────┘
```

数据流：设备以自定义二进制帧上报（魔数 0xEB90 + CRC16-Modbus 校验），Netty 拆包解帧后异步投递 Kafka（ack=all + 幂等生产者，按 deviceId 分区保序）；两个消费组独立消费——`em-data-persist` 批量落库 + 更新 Redis 最新状态 + WebSocket 推送大屏，`em-alarm` 规则判定 + Redis 分布式锁去重后写告警。

## 技术栈

| 层 | 技术 |
|---|---|
| 基础 | Java 17 / Spring Boot 3.5 / Maven 多模块 |
| 微服务 | Spring Cloud 2025.0 + Alibaba 2025.0.0.0（Nacos / Gateway / OpenFeign / Sentinel） |
| 数据 | MySQL 8.0（MyBatis-Plus 3.5.17）/ Redis 7（缓存 + 分布式锁） |
| 消息 | Kafka 4.3（KRaft，3 分区，双消费组） |
| 接入 | Netty 4.1（自定义二进制协议 / 粘包拆包 / 心跳保活）/ WebSocket |
| 安全 | JWT（jjwt）网关统一鉴权，设备密钥与用户令牌双信任体系 |
| 前端 | Vue 3.5 + Element Plus + ECharts（Vite 6） |
| 工程 | Git / Docker Compose 一键编排 / JUnit 5（21+ 单测） |

## 模块说明

```
energy-monitor
├── em-common          协议编解码（CRC16/帧/包）+ 共享实体/Mapper/DTO/JWT 工具/缓存组件
├── em-device-access   Netty TCP 接入(9000) + Kafka 生产 + 设备在线注册表
├── em-data-process    Kafka 消费（批量落库/缓存更新/规则告警/WS 推送）
├── em-web-api         查询接口 + 登录签发 + Feign 调用接入层
├── em-gateway         路由 / 全局 JWT 过滤器 / Sentinel 网关限流 / 大屏静态资源
├── em-simulator       虚拟设备（多设备并发、可注入异常电压触发告警）
├── em-frontend        Vue3 大屏
├── docker/            compose 初始化 SQL
├── scripts/           kafka/nacos 启停、start-all、网关压测
└── docs/              协议设计、建表脚本、P1-P4 验收报告
```

## 快速启动

### 方式一：本机运行（开发调试）

```bash
# 0. 前置：MySQL8 / Redis / JDK17 / Maven；Kafka 与 Nacos 用脚本拉起
powershell -File scripts/kafka-start.ps1
powershell -File scripts/nacos-start.ps1

# 1. 建库（root 密码按需调整，见各服务 application.yml）
mysql -uroot -p --default-character-set=utf8mb4 -e "SOURCE docs/schema.sql"

# 2. 构建并启动（顺序：接入 → 处理 → 查询 → 网关）
mvn clean package -DskipTests
powershell -File scripts/start-all.ps1          # 已处理 SERVER__PORT 环境变量污染

# 3. 启动虚拟设备
java -jar em-simulator/target/em-simulator-1.0.0-SNAPSHOT.jar 127.0.0.1 9000 3 500

# 4. 打开大屏
#    http://127.0.0.1:8090/  （admin / admin123）
```

### 方式二：Docker Compose

```bash
docker compose up -d          # 中间件 + 全部服务 + 健康检查 + 自动建库
docker compose ps             # 观察健康状态
```

## 关键验证命令

```bash
# 经网关登录并查询（无 token 应 401）
curl -X POST http://127.0.0.1:8090/api/auth/login -H "Content-Type: application/json" -d '{"username":"admin","password":"admin123"}'
curl http://127.0.0.1:8090/api/devices -H "Authorization: Bearer <token>"

# 实时推送（浏览器或 scripts/WsTest.java）
java scripts/WsTest.java "ws://127.0.0.1:8090/ws/realtime?token=<token>" 3

# 网关压测（20 并发 / 15 秒）
powershell -File scripts/gw-bench.ps1 -Threads 20 -Seconds 15

# 单元测试
mvn test
```

## 提交历史（各阶段验收报告见 docs/）

| 提交 | 阶段 |
|---|---|
| `e1d0df4` | P1 单体跑通：二进制协议 + Netty 接入 + 模拟器（TDD 14 测试） |
| `fa6e325` | P1 收尾：数据模型修正 + 路径 404 修复 + 接口测试 |
| `ccbbcfe` | P2 Kafka 削峰 + Redis 缓存/锁 + 告警引擎 + WS 推送（100 QPS 压测零积压） |
| `8f43dcb` | P3 微服务拆分 + Nacos + Gateway + JWT + Feign + Sentinel |
| `0ca3f57` | P4 Vue3 大屏 + Docker 编排 + 网关压测（11,204 QPS / P99 22ms） |

## 设计文档

- [`docs/protocol.md`](docs/protocol.md) —— 设备二进制协议帧格式
- [`docs/schema.sql`](docs/schema.sql) —— 建表脚本（含唯一索引用于消费幂等）
- 
