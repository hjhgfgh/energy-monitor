# 设备接入协议设计（v1）

设备侧使用自定义二进制协议通过 TCP 长连接上报数据。选二进制而非 HTTP/JSON 的理由：
设备端资源受限，HTTP 头部开销大且请求-响应模型不适合持续上报；二进制单帧固定开销仅 12 字节。

## 1. 帧结构

| 偏移 | 字段 | 长度 | 说明 |
|---|---|---|---|
| 0 | 魔数 magic | 2 B | 固定 `0xEB 0x90`（"能源"谐音），用于快速识别帧起始 |
| 2 | 版本 version | 1 B | 当前为 `0x01`，为协议升级预留 |
| 3 | 命令字 cmd | 1 B | 见第 2 节 |
| 4 | 长度 length | 2 B | **大端无符号短整型**，表示该字段之后到帧尾的总字节数 |
| 6 | 设备ID deviceId | 4 B | 大端整型 |
| 10 | 载荷 payload | 变长 | 由 cmd 决定，见第 3 节 |
| 尾 | CRC16 | 2 B | 对「版本 + 命令字 + 长度 + 设备ID + 载荷」计算的校验值 |

**length 字段的语义**：`length = 4(deviceId) + payload.length + 2(CRC)`。
因此整帧长度 = `4 + 2 + length` = `6 + length`。

这个语义直接对应 Netty 的解码器参数：

```java
new LengthFieldBasedFrameDecoder(
    1024,   // maxFrameLength
    4,      // lengthFieldOffset
    2,      // lengthFieldLength
    0,      // lengthAdjustment：length 已含其后的全部字节
    0       // initialBytesToStrip：不剥离，后续仍需读魔数与校验 CRC
)
```

固定开销 = 2 + 1 + 1 + 2 + 4 + 2 = **12 字节**。

## 2. 命令字

| 值 | 名称 | 方向 | 载荷长度 |
|---|---|---|---|
| `0x01` | DATA_REPORT | 设备 → 服务端 | 20 B |
| `0x02` | HEARTBEAT | 设备 → 服务端 | 0 B |
| `0x03` | REGISTER | 设备 → 服务端 | 变长（设备编码 UTF-8） |
| `0x81` | CMD_DOWN | 服务端 → 设备 | 变长（指令字符串 UTF-8） |

## 3. 载荷格式

### 3.1 DATA_REPORT（20 字节）

| 偏移 | 字段 | 长度 | 类型 | 单位 |
|---|---|---|---|---|
| 0 | 电压 voltage | 4 B | IEEE 754 float，大端 | V |
| 4 | 电流 current | 4 B | IEEE 754 float，大端 | A |
| 8 | 功率 power | 4 B | IEEE 754 float，大端 | W |
| 12 | 采集时间 collectTime | 8 B | long，大端，毫秒时间戳 | ms |

用 float 而非整型，是因为电力参数需要小数精度（如 220.5 V）；
用 8 字节 long 存时间戳，避免 2038 年问题。

### 3.2 HEARTBEAT（0 字节）
仅靠帧头传递设备身份，服务端收到即刷新该连接的活跃时间。

### 3.3 REGISTER（变长）
载荷为设备编码的 UTF-8 字节串，服务端据此把连接与设备绑定。

## 4. CRC16 校验

采用 **Modbus CRC16** 算法（多项式 `0xA001`，初值 `0xFFFF`，结果低字节在前）。
校验范围：帧中从版本字段（偏移 2）到载荷结束的全部字节，即**排除魔数与 CRC 自身**。

选择 CRC16 而非 MD5/SHA：设备端算力有限，CRC16 只需一张查表即可完成，足够发现传输中的位翻转。

## 5. 粘包与拆包

TCP 是字节流，不保证消息边界，必然出现三种情况：

| 情况 | 表现 | 处理 |
|---|---|---|
| 正常 | 一次读到完整帧 | 直接解析 |
| 粘包 | 一次读到多个完整帧 | `LengthFieldBasedFrameDecoder` 按 length 切分，循环投递 |
| 半包 | 一次只读到半个帧 | 解码器缓存剩余字节，等后续数据补齐 |

**为什么魔数仍然必要**：`LengthFieldBasedFrameDecoder` 只信任 length 字段。
若数据流错位导致 length 被解析成异常值，会切出错误的帧。
因此还需一个 `FrameValidator` 校验魔数并重算 CRC——**双保险**，这也是协议设计上必须能讲清的一点。

## 6. 心跳保活

- 设备侧：每 **30 秒**发送一次 HEARTBEAT
- 服务端：`IdleStateHandler` 读空闲阈值设 **90 秒**（3 个心跳周期，容忍两次丢包）
- 超时动作：关闭连接 → 清空该连接的在线状态 → 记录离线日志

选择 90 秒而非 30 秒：网络抖动或设备短暂 GC 时不应立即判定离线，留两次重试余量。

## 7. 通道流水线（Pipeline）

```
LengthFieldBasedFrameDecoder   ← 拆包，只留完整帧
        ↓
FrameValidator                 ← 校验魔数 + CRC，不合法则丢弃并计数
        ↓
DeviceFrameDecoder             ← ByteBuf → DeviceFrame 对象
        ↓
DeviceDataHandler              ← 业务处理（P1 落库 / P2 投 Kafka）
        ↓
IdleStateHandler               ← 心跳超时检测（放在最前或最后均可，此处靠前）
```

编码方向（服务端下发指令）用 `DeviceFrameEncoder` 单独实现，与解码器对称。
