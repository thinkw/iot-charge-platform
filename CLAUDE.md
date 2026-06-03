# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

---

## ⚠️ 项目构建状态

**当前阶段**：✅ 全部完成（8/8）
**目标阶段**：共 8 个阶段（详见 PRD.md 第十章）
**说明**：项目所有阶段已完成，进入维护阶段。

| 阶段 | 状态 | 说明 |
|------|------|------|
| 阶段1：环境搭建与基础设计 | ✅ 完成 | 项目骨架、pom依赖、数据库设计 |
| 阶段2：设备接入与模拟器开发 | ✅ 完成 | MQTT服务端、设备鉴权、心跳、虚拟模拟器 |
| 阶段3：用户端核心功能开发 | ✅ 完成 | 用户登录、站点查询、扫码启桩、充电控制 |
| 阶段4：订单与计费模块开发 | ✅ 完成 | 订单管理、支付、计费规则、退款 |
| 阶段5：运营后台与告警开发 | ✅ 完成 | 站点/充电桩/告警管理、数据统计、WebSocket推送、操作日志 |
| 阶段6：实时推送与大屏开发 | ✅ 完成 | Vue3前端大屏、WebSocket定时推送、ECharts图表、DashboardPushScheduler |
| 阶段7：联调测试与优化 | ✅ 完成 | 3个严重Bug修复、2个MQTT解码器Bug修复、8场景联调通过、Dashboard缓存、23个新测试、146测试全过 |
| 阶段8：文档整理与演示准备 | ✅ 完成 | API_DOC.md 接口文档、DEPLOY.md 部署文档、README.md 项目说明 |

---

## 构建与运行命令

```bash
# 编译全部模块
mvn clean compile -f pom.xml

# 运行全部测试
mvn test -f pom.xml

# 运行单个模块的测试
mvn test -pl iot-common -f pom.xml
mvn test -pl iot-core -f pom.xml

# 运行单个测试类
mvn test -pl iot-core -Dtest=ChargeServiceTest

# 打包可运行 JAR（主应用）
mvn clean package -pl iot-api -f pom.xml

# 打包模拟器 JAR
mvn clean package -pl iot-simulator -f pom.xml

# 启动主应用
java -jar iot-api/target/iot-api-1.0.0-SNAPSHOT.jar

# 启动模拟器
java -jar iot-simulator/target/iot-simulator-1.0.0-SNAPSHOT.jar
```

**注意**：从父 POM 根目录执行 `mvn` 命令时需要指定 `-f pom.xml`，因为根 POM 的 `<packaging>pom</packaging>`。

### 前端项目命令

```bash
# 管理端 Web
cd iot-frontend
npm install
npm run dev          # 开发服务器 (http://localhost:5173)
npm run build        # 构建生产版本（输出到 iot-api/src/main/resources/static/）

# 微信小程序
cd iot-miniapp
npm install
npm run dev:mp-weixin  # 开发模式（需微信开发者工具打开 dist/dev/mp-weixin）
npm run build:mp-weixin # 生产构建
```

---

## 项目架构

### 模块依赖关系

```
iot-frontend (Vue3 管理端 Web 项目)
iot-miniapp  (uni-app 微信小程序用户端项目)
     └── (通过 HTTP API:8080 + WebSocket:9090 连接后端)

iot-simulator (独立JAR，不依赖主应用)
     └── iot-common

iot-api (Spring Boot 启动模块)
     ├── iot-access
     │    ├── iot-core  →  iot-common
     │    └── iot-common
     ├── iot-core  →  iot-common
     └── iot-common
```

- **iot-common**：纯 Java 库，零外部框架依赖（仅 Lombok + Hutool）。包含枚举、异常、统一响应模型 `Result<T>`、基础实体 `BaseEntity`、雪花 ID 工具
- **iot-core**：业务层。包含实体(Entity)、Mapper(MyBatis-Plus)、Service、计费策略(Strategy)、RocketMQ 生产者/消费者、DTO(请求/响应 VO)、Spring事件
- **iot-access**：接入层。Netty MQTT 服务端 (端口1883) + Spring WebSocket 服务
- **iot-api**：HTTP 接口层 + 主启动类 `IotApiApplication`。包含 Controller、Security(JWT)、Filter、Aspect
- **iot-simulator**：独立可启动应用。使用 Paho MQTT 客户端模拟充电桩行为

### 包命名规范

- 基础包：`com.iot.{module}`
- Controller：`com.iot.api.controller.user` 或 `com.iot.api.controller.admin`
- Service 接口：`com.iot.core.service`，实现在 `com.iot.core.service.impl`
- Entity/Mapper：`com.iot.core.entity` / `com.iot.core.mapper`
- 配置类：`com.iot.{module}.config`

### 主启动类

`com.iot.api.IotApiApplication`（`iot-api` 模块），通过 `scanBasePackages = "com.iot"` 自动扫描所有模块的 Bean。

---

## 核心技术架构

### 设备通信链路

```
充电桩模拟器 --MQTT(TCP:1883)--> Netty MQTT Server
    ├── MqttDecoder (二进制报文 → MqttMessage)
    ├── MqttEncoder (MqttMessage → 二进制报文)
    ├── IdleStateHandler (90秒读空闲检测)
    └── MqttMessageHandler (按 MQTT 报文类型 + Topic 路由)
         ├── CONNECT → DeviceService.authenticateDevice()
         ├── PUBLISH device/heartbeat → handleHeartbeat()
         ├── PUBLISH device/status   → handleStatusReport()
         ├── PUBLISH device/data     → handleDataReport()
         ├── PUBLISH device/alarm    → handleAlarmReport()
         ├── PINGREQ → PINGRESP
         └── 断连 → handleOffline()
```

### 设备状态机

状态转换严格校验（`DeviceServiceImpl.validateStatusTransition()`）：
- `OFFLINE(0)` → `IDLE(1)`
- `IDLE(1)` → `LOCKED(4)`, `FAULT(3)`, `OFFLINE(0)`
- `CHARGING(2)` → `IDLE(1)`, `FAULT(3)`, `OFFLINE(0)`
- `FAULT(3)` → `IDLE(1)`, `OFFLINE(0)`
- `LOCKED(4)` → `CHARGING(2)`, `IDLE(1)`, `FAULT(3)`

### 扫码启桩流程

1. `POST /api/charge/start` → `ChargeController.startCharge()`
2. 验证充电桩状态为 IDLE
3. `RedissonClient.getLock("charge:lock:{chargerId}")` 获取分布式锁
4. 更新充电桩状态为 LOCKED → 创建订单(状态 CHARGING)
5. `DeviceService.sendCommand(sn, "START_CHARGE")` → MQTT 下发指令
6. WebSocket 实时推送充电进度（通过 `ChargeEventPublisher`）

### Redis Key 设计

| Key Pattern | 类型 | 说明 |
|-------------|------|------|
| `device:status:{sn}` | Hash | 设备状态(online, status, lastHeartbeat) |
| `device:data:{sn}` | Hash | 设备实时数据(voltage, current, power, energy, temperature) |
| `charge:lock:{chargerId}` | Redisson RLock | 启桩分布式锁 |
| `reservation:lock:{chargerId}:{date}:{start}` | Redisson RLock | 预约时段锁 |
| `charger:status:{id}` | String | 充电桩状态缓存，管理端修改状态时同步更新 |

### RocketMQ Topic 设计

| Topic | Consumer Group | 说明 |
|-------|---------------|------|
| `device_event` | `device_event_consumer` | 设备上下线/状态变更 → WebSocket 广播 |
| `order_event` | `order_event_consumer` | 订单创建/充电结束/支付完成 → WebSocket 推送 |
| `alarm_event` | `alarm_event_consumer` | 告警创建 → WebSocket 广播到运营端 |

### 模块间解耦模式

跨模块的服务调用通过接口 + `@Autowired(required = false)` 实现解耦：
- `DeviceService.sendCommand()` → `DeviceCommandSender`（接口在 iot-core，实现在 iot-access）
- 充电进度推送 → `ChargeEventPublisher`（接口在 iot-core，实现在 iot-api）
- 设备数据上报 → Spring `ApplicationEventPublisher` + `@EventListener`
- **阶段5新增**：WebSocket 推送 → `DeviceEventPublisher`（接口在 iot-core，实现在 iot-access 的 `WebSocketDeviceEventPublisher`），MQ 消费者通过接口推送，避免 iot-core → iot-access 循环依赖

### JWT 认证流程（阶段5优化）

```
登录/注册 → JwtUtil.generateToken(userId, phone, roles) → Token含 roles 声明
请求携带Token → JwtAuthFilter 解析Token → 直接从 Claims 提取 roles（不查DB）
  → 构建 UsernamePasswordAuthenticationToken(phone, null, authorities)
  → authentication.setDetails(userId) → SecurityContext
  → SecurityConfig: hasRole("ADMIN") 检查 authorities 中是否有 ROLE_ADMIN
```

**关键点**：角色信息由 JwtUtil 在生成 Token 时写入，JwtAuthFilter 从 Token Claims 直接提取，**不查询数据库**。Token 签名保证 roles 不可篡改，角色变更通过 Token 过期（2h）自然生效。

---

## 数据库

- **库名**：`iot_charge_platform`（MySQL 8.0）
- **建表 DDL**：`DESIGN.md` 第 3 章
- **测试数据**：`init-data.sql`（5 个充电站、100 台充电桩、3 个测试用户）
- **测试账号**：`13800000001` / `123456`（普通用户）、`13800000003` / `123456`（管理员）

---

## 关键文件速查

| 用途 | 文件路径 |
|:-----|----------|
| PRD 需求文档 | `PRD.md` |
| 技术设计文档 | `DESIGN.md` |
|                      |                                                              |
| 初始化数据 SQL | `init-data.sql` |
| Postman 接口集合 | `iot-charge-platform.postman_collection.json` |
| 父 POM | `pom.xml` |
| 主启动类 | `iot-api/src/main/java/com/iot/api/IotApiApplication.java` |
| 应用配置 | `iot-api/src/main/resources/application.yml` |
| MQTT 服务端 | `iot-access/.../mqtt/MqttServer.java` |
| MQTT 消息路由 | `iot-access/.../mqtt/handler/MqttMessageHandler.java` |
| 设备服务实现 | `iot-core/.../service/impl/DeviceServiceImpl.java` |
| 充电服务实现 | `iot-core/.../service/impl/ChargeServiceImpl.java` |
| 计费服务实现 | `iot-core/.../service/impl/PricingServiceImpl.java` |
| 告警服务实现 | `iot-core/.../service/impl/AlarmServiceImpl.java` |
| 统计服务实现 | `iot-core/.../service/impl/StatisticsServiceImpl.java` |
| 充电桩管理服务 | `iot-core/.../service/impl/ChargerServiceImpl.java` |
| Security 配置 | `iot-api/.../security/SecurityConfig.java` |
| JWT 过滤器 | `iot-api/.../security/JwtAuthFilter.java` |
| JWT 工具类 | `iot-api/.../security/JwtUtil.java` |
| 统一响应模型 | `iot-common/.../model/Result.java` |
| 设备事件发布接口 | `iot-core/.../service/DeviceEventPublisher.java` |
| 大屏定时推送器 | `iot-access/.../websocket/DashboardPushScheduler.java` |（阶段6新增）
| WebSocket 发布器实现 | `iot-access/.../websocket/WebSocketDeviceEventPublisher.java` |
| 操作日志切面 | `iot-api/.../aspect/LogAspect.java` |
| **前端大屏** | |
| 前端入口 | `iot-frontend/index.html` |
| 大屏主页面 | `iot-frontend/src/views/dashboard/DashboardView.vue` |
| 指标卡片 | `iot-frontend/src/views/dashboard/components/StatusCards.vue` |
| 趋势图表 | `iot-frontend/src/views/dashboard/components/TrendChart.vue` |
| 站点排名 | `iot-frontend/src/views/dashboard/components/StationRank.vue` |
| 故障饼图 | `iot-frontend/src/views/dashboard/components/FaultPieChart.vue` |
| WebSocket Hook | `iot-frontend/src/composables/useWebSocket.ts` |
| API 封装 | `iot-frontend/src/api/dashboard.ts` |
| WebSocket 发布器实现 | `iot-access/.../websocket/WebSocketDeviceEventPublisher.java` |
| 操作日志切面 | `iot-api/.../aspect/LogAspect.java` |

---

## 开发约定

- **注释语言**：所有公开方法必须有中文 Javadoc 注释，说明功能、参数和返回值
- **异常处理**：业务异常使用 `BusinessException`（code + message），由 `GlobalExceptionHandler` 统一处理
- **MQ 消息**：发送 RocketMQ 消息采用 best-effort 策略，失败只记日志不抛异常
- **数据库操作**：使用 MyBatis-Plus 的 `LambdaQueryWrapper` 避免字符串字段名硬编码
- **DTO/VO**：请求 DTO 在 `iot-core/.../dto/request/`，响应 VO 在 `dto/response/`
