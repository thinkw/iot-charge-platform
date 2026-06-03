# MQTT 解码器 Bug 修复日志

> **日期**：2026-06-03  
> **模块**：`iot-access` — `MqttDecoder.java`  
> **影响范围**：MQTT 3.1.1 协议 SUBSCRIBE 与 PUBLISH 报文解析

---

## 一、Bug #1：SUBSCRIBE 解码未消费 Topic Filter 列表字节

### 1.1 发现问题

启动两个充电桩模拟器后，服务端日志在每条 `SUBSCRIBE` 之后连续出现 `RESERVED`、`PUBCOMP` 等垃圾消息：

```
[MQTT解码] SUBSCRIBE - packetId: 1
[MQTT解码] 跳过报文类型: RESERVED (code=0)      ← 异常！
[MQTT解码] 跳过报文类型: RESERVED (code=0)      ← 异常！
[MQTT解码] 跳过报文类型: PUBCOMP (code=7)        ← 异常！
```

同时，设备在连接成功后约 3.5 分钟内所有 PUBLISH 消息（心跳、状态上报）全部丢失，导致设备被误判为离线。

### 1.2 根因分析

**原始代码**（修复前）：

```java
private void decodeSubscribe(ByteBuf in, MqttMessage message) {
    message.setPacketId(in.readUnsignedShort());
    // 跳过 Topic Filter 列表（简化处理）
    log.debug("[MQTT解码] SUBSCRIBE - packetId: {}", message.getPacketId());
}
```

MQTT SUBSCRIBE 报文结构如下：

```
| 固定头 | 剩余长度 | packetId (2B) | Topic Filter 列表 (N字节) |
```

原代码仅读取了 2 字节的 `packetId`，**未消费后面的 Topic Filter 列表**（包含 `device/command/{sn}` 等订阅主题的 UTF-8 编码）。这些残留字节残留在 Netty `ByteBuf` 中，被后续的解码过程错误地当作新报文头来解析，导致 **全局消息解析错位**。

| 残留字节的偏移位置 | 被误解析为 |
|:--:|:--:|
| 某字节高位为 0 | `RESERVED (code=0)` |
| 某字节高位为 7 | `PUBCOMP (code=7)` |
| 某字节高位为 6 | `PUBREL (code=6)` |

### 1.3 解决方案

将 `remainingLength`（当前报文的精确剩余长度）传入 `decodeSubscribe()`，计算并跳过剩余的 Topic Filter 字节：

```java
// decode() 调用处修改
case SUBSCRIBE -> decodeSubscribe(in, message, remainingLength);

// decodeSubscribe 修改
private void decodeSubscribe(ByteBuf in, MqttMessage message, int remainingLength) {
    message.setPacketId(in.readUnsignedShort());
    // 消费剩余的 Topic Filter 列表字节
    int consumedBytes = 2; // packetId 已读 2 字节
    int toSkip = remainingLength - consumedBytes;
    if (toSkip > 0) {
        in.skipBytes(toSkip);
    }
    log.debug("[MQTT解码] SUBSCRIBE - packetId: {}", message.getPacketId());
}
```

**关键**：`toSkip = remainingLength - 2`，精确跳过当前 SUBSCRIBE 报文中未消费的字节，不留残留。

### 1.4 验证结果

修复后日志（垃圾消息完全消失）：

```
[MQTT解码] SUBSCRIBE - packetId: 1
[MQTT解码] PUBLISH - topic: device/status, qos: 1, payloadSize: 190
```

| 指标 | 修复前 | 修复后 |
|------|:--:|:--:|
| 垃圾消息 (RESERVED / PUBCOMP / PUBREL) | 🔴 大量出现 | ✅ 完全消失 |
| 设备心跳丢失 | 🔴 3.5 分钟丢失 | ✅ 正常到达 |
| 误判离线 | 🔴 触发超时检测 | ✅ 从未超时 |

---

## 二、Bug #2：PUBLISH 负载读取越界

### 2.1 发现问题

Bug #1 修复后，SUBSCRIBE 后的垃圾消息消失，但 STATUS 上报消息的 payload 中出现了混杂数据：

```
[MQTT解码] PUBLISH - topic: device/status, qos: 1, payloadSize: 190
[MQTT] PUBLISH - SN: CHARGER-001, topic: device/status, payload:
{"data":{...},"sn":"CHARGER-001","status":1}0@  device/heartbeat{"sn":"CHARGER-001","timestamp":...}
                                                  ^^^^^^^^^^^^^^^^^^^^^^^
                                                  心跳消息数据混入！
```

`payloadSize: 190` 远超正常 STATUS JSON（约 124 字节），**status 的 payload 末尾混入了紧随其后的心跳消息数据**。

### 2.2 根因分析

**原始代码**（修复前）：

```java
private void decodePublish(ByteBuf in, MqttMessage message, int flags) {
    // ...
    message.setTopic(readUtf8String(in));
    if (message.getQos() > 0) {
        message.setPacketId(in.readUnsignedShort());
    }
    // 负载：读取剩余的所有可读字节
    int payloadSize = in.readableBytes();  // ← 问题在这里！
    // ...
}
```

**根本原因**：`in.readableBytes()` 返回的是 **ByteBuf 中当前所有可读字节数**，而非当前 PUBLISH 报文的负载长度。

模拟器的 `start()` 方法中，STATUS 上报与首次心跳几乎同时发送（间隔毫秒级），两个 PUBLISH 报文被 TCP 粘包在一起。`readableBytes()` 错误地将第二个 PUBLISH 报文（心跳）的字节也计入了第一个报文（STATUS）的负载中。

```
TCP 段：[STATUS PUBLISH] [HEARTBEAT PUBLISH]
                        ↑
              readableBytes() 读到了心跳报文的数据
```

### 2.3 解决方案

利用 `remainingLength`（MQTT 固定头中声明的当前报文剩余长度），精确计算负载长度：

```java
// decode() 调用处修改
case PUBLISH -> decodePublish(in, message, flags, remainingLength);

// decodePublish 修改
private void decodePublish(ByteBuf in, MqttMessage message, int flags, int remainingLength) {
    message.setDup((flags & 0x08) != 0);
    message.setQos((flags >> 1) & 0x03);
    message.setRetain((flags & 0x01) != 0);

    // Topic
    message.setTopic(readUtf8String(in));
    int consumedBytes = 2 + message.getTopic().getBytes(StandardCharsets.UTF_8).length;

    // Packet Identifier（仅 QoS > 0 时）
    if (message.getQos() > 0) {
        message.setPacketId(in.readUnsignedShort());
        consumedBytes += 2;
    }

    // 负载精确长度 = 剩余长度 - 已消费可变头字节
    int payloadSize = remainingLength - consumedBytes;
    if (payloadSize > 0) {
        byte[] payload = new byte[payloadSize];
        in.readBytes(payload);
        message.setPayload(payload);
    }
}
```

**关键**：`payloadSize = remainingLength - consumedBytes`，只读取当前 PUBLISH 报文声明的负载长度，绝不多读一个字节。

### 2.4 验证结果

修复后 status payload 干净正确：

```
[MQTT解码] PUBLISH - topic: device/status, qos: 1, payloadSize: 124
[MQTT] PUBLISH - SN: CHARGER-001, topic: device/status, payload:
{"data":{"current":"0.0","temperature":"25.0","power":"0.00","voltage":"0.0","energy":"0.00"},"sn":"CHARGER-001","status":1}
```

首次心跳也独立解析成功：

```
[MQTT解码] PUBLISH - topic: device/heartbeat, qos: 0, payloadSize: 46
[MQTT] PUBLISH - SN: CHARGER-001, topic: device/heartbeat, payload: {"sn":"CHARGER-001","timestamp":1780460397228}
```

心跳每 30 秒稳定到达，无任何异常：

```
12:19:57  PUBLISH device/heartbeat CHARGER-001
12:20:27  PUBLISH device/heartbeat CHARGER-001  (30s)
12:20:57  PUBLISH device/heartbeat CHARGER-001  (30s)
12:21:27  PUBLISH device/heartbeat CHARGER-001  (30s)
...
```

| 指标 | 修复前 | 修复后 |
|------|:--:|:--:|
| STATUS payload 大小 | 🔴 190（含垃圾） | ✅ 124（纯 JSON） |
| Payload 内容清洁度 | 🔴 混入心跳数据 | ✅ 完全干净 |
| 首次心跳被吞并 | 🔴 丢失 | ✅ 独立解析 |
| 心跳稳定性 | 🔴 首个被吞 + 后续正常 | ✅ 全部 30s 准点 |

---

## 三、最终验证（全流程）

启动两个充电桩（CHARGER-001、CHARGER-002），完整日志如下：

```
12:19:27.214  [MQTT解码] CONNECT CHARGER-001
12:19:27.218  [设备鉴权] 验证成功
12:19:27.218  [会话管理] 注册会话 在线数:1
12:19:27.219  [设备上线] SN: CHARGER-001
12:19:27.229  [MQTT解码] SUBSCRIBE CHARGER-001       ✅
12:19:27.230  [MQTT解码] PUBLISH device/status        ✅ 纯净 JSON
12:19:27.270  [状态上报] 目标状态:1                   ✅
12:19:27.284  [MQTT解码] PUBLISH device/heartbeat     ✅ 独立解析
12:19:57.228  第2次心跳 (30s)                         ✅
12:20:27.224  第3次心跳 (30s)                         ✅
...（持续稳定，无超时、无垃圾消息）
```

---

## 四、总结

| 修复项 | 修复前 | 修复后 |
|--------|:--:|:--:|
| SUBSCRIBE 解码 | `decodeSubscribe(in, message)` | `decodeSubscribe(in, message, remainingLength)` |
| PUBLISH 负载计算 | `in.readableBytes()` | `remainingLength - consumedBytes` |
| 垃圾消息 | 大量 RESERVED/PUBCOMP/PUBREL | 完全消失 |
| 心跳丢失 | 首次 3.5 分钟丢失 | 全部正常 |
| 误判离线 | 触发 | 不再触发 |

**核心教训**：MQTT 解码器的每一帧处理都必须以 `remainingLength` 为边界，不能依赖 `in.readableBytes()`（它可能包含 TCP 粘包带来的后续报文）。所有分支都必须完整消费 `remainingLength` 声明的字节数，一个不多、一个不少。
