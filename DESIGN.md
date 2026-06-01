# 物联网充电桩智能运营平台 — 技术设计文档

**版本**：V1.0  
**最后更新**：2026-06-01  
**项目代码名**：iot-charge-platform  
**技术栈**：Java 21 + Spring Boot 3.5.14 + MyBatis-Plus 3.5.16 + MySQL 8.0 + Redis 8.2.3 + RocketMQ 5.5.0 + Netty 4.1.133 + MQTT 3.1.1 + Spring WebSocket + Vue 3 + Element Plus  
**项目形态**：单体架构 + 虚拟充电桩模拟器

---

## 目录

1. [系统架构设计](#1-系统架构设计)
2. [项目工程结构](#2-项目工程结构)
3. [数据库设计](#3-数据库设计)
4. [Redis 数据结构设计](#4-redis-数据结构设计)
5. [RocketMQ 消息队列设计](#5-rocketmq-消息队列设计)
6. [核心模块详细设计](#6-核心模块详细设计)
   - 6.1 IoT 设备接入模块
   - 6.2 用户端模块
   - 6.3 订单与计费模块
   - 6.4 运营后台模块
   - 6.5 告警模块
7. [接口设计](#7-接口设计)
8. [安全设计](#8-安全设计)
9. [虚拟充电桩模拟器设计](#9-虚拟充电桩模拟器设计)
10. [项目配置与部署](#10-项目配置与部署)

---

## 1. 系统架构设计

### 1.1 整体分层架构

系统采用四层分层单体架构，各层通过接口交互，低耦合高内聚。依赖方向严格从上到下：接入层 → 业务层 → 数据层。

```
┌─────────────────────────────────────────────────────────────────┐
│                     设备仿真层 (Simulator)                        │
│  虚拟充电桩模拟器 (独立 Java Application)                         │
│  ┌──────────┐ ┌──────────┐ ┌──────────┐ ┌──────────────────┐   │
│  │ 设备注册 │ │ 心跳模拟 │ │ 状态上报 │ │ 故障触发/指令响应 │   │
│  └──────────┘ └──────────┘ └──────────┘ └──────────────────┘   │
│  MQTT Client (Eclipse Paho 1.2.5)                                │
└──────────────────────────────┬──────────────────────────────────┘
                               │ MQTT Protocol (TCP:1883)
┌──────────────────────────────┴──────────────────────────────────┐
│                       接入层 (iot-access)                         │
│  ┌──────────────────┐ ┌──────────────────┐ ┌─────────────────┐  │
│  │ Netty MQTT Server│ │  HTTP REST API   │ │   WebSocket     │  │
│  │ (设备长连接/1883) │ │  (用户/后台/8080) │ │  (实时推送/9090) │  │
│  │ Netty 4.1.133    │ │  Spring MVC 6.x  │ │ Spring WebSocket│  │
│  └────────┬─────────┘ └────────┬─────────┘ └────────┬────────┘  │
└───────────┼────────────────────┼────────────────────┼───────────┘
            │                    │                    │
┌───────────┴────────────────────┴────────────────────┴───────────┐
│                       业务层 (iot-core)                           │
│  ┌──────────┐ ┌──────────┐ ┌──────────┐ ┌──────────┐ ┌───────┐ │
│  │ 设备服务 │ │ 用户服务 │ │ 订单服务 │ │ 计费服务 │ │告警服务│ │
│  │ Device   │ │ User     │ │ Order    │ │ Pricing  │ │ Alarm  │ │
│  │ Service  │ │ Service  │ │ Service  │ │ Service  │ │Service │ │
│  └────┬─────┘ └────┬─────┘ └────┬─────┘ └────┬─────┘ └───┬───┘ │
│       └─────────────┴────────────┴────────────┴──────────┘       │
│                          ↑ 接口依赖                              │
│  ┌──────────────────────────────────────────────────────────┐   │
│  │            iot-common (公共模块：工具/异常/常量/模型)      │   │
│  └──────────────────────────────────────────────────────────┘   │
└──────────────────────────────┬──────────────────────────────────┘
                               │
┌──────────────────────────────┴──────────────────────────────────┐
│                        数据层                                    │
│  ┌─────────────┐  ┌──────────────┐  ┌──────────────────────┐    │
│  │ MySQL 8.0   │  │ Redis 8.2.3  │  │ RocketMQ 5.5.0       │    │
│  │ (业务数据)  │  │ (状态/缓存/锁)│  │ (事件队列/延时消息)  │    │
│  └─────────────┘  └──────────────┘  └──────────────────────┘    │
└─────────────────────────────────────────────────────────────────┘
```

### 1.2 模块职责与依赖

| 模块 | 职责 | 依赖 |
|------|------|------|
| **iot-common** | 公共工具类、异常定义、常量枚举、统一响应模型、基础Entity | 无 |
| **iot-core** | 核心业务逻辑：设备管理、用户管理、订单管理、计费计算、告警处理 | iot-common |
| **iot-access** | 接入层：Netty MQTT 服务端、WebSocket 服务、MQTT 消息路由 | iot-common, iot-core |
| **iot-api** | HTTP 接口层：用户端 Controller、运营后台 Controller、Spring Security 配置 | iot-common, iot-core |
| **iot-simulator** | 独立虚拟充电桩模拟器，可独立启动，通过 MQTT 连接平台 | iot-common |

**依赖关系**：`iot-simulator` 独立于主应用；`iot-api` 和 `iot-access` 并行依赖 `iot-core`；`iot-core` 依赖 `iot-common`。

### 1.3 关键技术选型说明

| 技术组件 | 版本 | 选型理由 |
|----------|------|----------|
| Java 21 LTS | 21 | 虚拟线程(Virtual Threads)大幅提升长连接场景的并发处理能力 |
| Spring Boot | 3.5.14 | 最新稳定版，原生支持 Java 21 虚拟线程 |
| MyBatis-Plus | 3.5.16 | 简化 CRUD，内置分页、逻辑删除、乐观锁 |
| MySQL | 8.0 | 成熟的关系型数据库，支持窗口函数、CTE |
| Redis | 8.2.3 | 最新版，支持更多数据结构，用于设备状态存储和分布式锁 |
| RocketMQ | 5.5.0 | 阿里开源，支持事务消息和延时消息，适合 IoT 场景 |
| Netty | 4.1.133 | 业界最成熟的高性能网络框架，实现 MQTT 协议编解码 |
| Eclipse Paho | 1.2.5 | Eclipse 官方 MQTT 客户端库，用于模拟器 |
| Spring WebSocket | - | 与 Spring Boot 深度集成，实时推送充电进度和告警 |
| Redisson | 3.27+ | 分布式锁、分布式集合，看门狗自动续期 |

---

## 2. 项目工程结构

### 2.1 Maven 多模块划分

```
iot-charge-platform/                          # 父工程 (pom)
├── pom.xml                                   # 父POM：依赖版本管理、模块声明
├── DESIGN.md                                 # 本设计文档
├── PRD.md                                    # 产品需求文档
│
├── iot-common/                               # 公共模块 (jar)
│   ├── pom.xml
│   └── src/main/java/com/iot/common/
│       ├── constant/                         # 常量定义
│       │   ├── DeviceConstants.java          #   设备相关常量
│       │   ├── OrderConstants.java           #   订单相关常量
│       │   └── SystemConstants.java          #   系统常量
│       ├── enums/                            # 枚举定义
│       │   ├── DeviceStatusEnum.java         #   设备状态枚举
│       │   ├── OrderStatusEnum.java          #   订单状态枚举
│       │   ├── AlarmLevelEnum.java           #   告警级别枚举
│       │   └── PayStatusEnum.java            #   支付状态枚举
│       ├── exception/                        # 异常定义
│       │   ├── BusinessException.java        #   业务异常基类
│       │   ├── DeviceOfflineException.java   #   设备离线异常
│       │   ├── OrderException.java           #   订单异常
│       │   └── GlobalExceptionHandler.java   #   全局异常处理器(实际在iot-api)
│       ├── model/                            # 公共模型
│       │   ├── Result.java                   #   统一响应
│       │   ├── PageResult.java              #   分页响应
│       │   └── BaseEntity.java              #   基础实体(createTime,updateTime)
│       └── util/                             # 工具类
│           ├── JwtUtil.java                  #   JWT工具类
│           ├── SnowflakeIdUtil.java          #   雪花ID生成器
│           └── DateTimeUtil.java             #   日期时间工具
│
├── iot-core/                                # 核心业务模块 (jar)
│   ├── pom.xml
│   └── src/main/java/com/iot/core/
│       ├── entity/                           # 数据库实体
│       │   ├── Station.java
│       │   ├── Charger.java
│       │   ├── User.java
│       │   ├── ChargeOrder.java
│       │   ├── ReservationOrder.java
│       │   ├── PricingRule.java
│       │   ├── DeviceLog.java
│       │   ├── Alarm.java
│       │   ├── Role.java
│       │   └── Permission.java
│       ├── mapper/                           # MyBatis-Plus Mapper
│       │   ├── StationMapper.java
│       │   ├── ChargerMapper.java
│       │   ├── UserMapper.java
│       │   ├── ChargeOrderMapper.java
│       │   ├── ReservationOrderMapper.java
│       │   ├── PricingRuleMapper.java
│       │   ├── DeviceLogMapper.java
│       │   └── AlarmMapper.java
│       ├── service/                          # 业务服务接口
│       │   ├── DeviceService.java            #   设备服务(注册/鉴权/状态管理)
│       │   ├── UserService.java              #   用户服务(注册/登录/信息)
│       │   ├── OrderService.java             #   订单服务(创建/支付/退款)
│       │   ├── ChargeService.java            #   充电服务(启桩/停桩/进度)
│       │   ├── ReservationService.java       #   预约服务(创建/取消/超时)
│       │   ├── PricingService.java           #   计费服务(规则匹配/费用计算)
│       │   ├── AlarmService.java             #   告警服务(创建/处理/统计)
│       │   ├── StationService.java           #   站点服务(CRUD/查询)
│       │   └── StatisticsService.java        #   统计服务(数据大屏/报表)
│       ├── service/impl/                     # 业务服务实现
│       │   └── ...ServiceImp.java            #   各服务实现类
│       ├── strategy/                         # 策略模式
│       │   ├── PricingStrategy.java          #   计费策略接口
│       │   ├── BasePricingStrategy.java      #   基础电价策略
│       │   ├── PeakValleyPricingStrategy.java#   峰谷电价策略
│       │   └── PricingContext.java           #   计费上下文
│       ├── mq/                               # RocketMQ 生产者/消费者
│       │   ├── producer/
│       │   │   ├── DeviceEventProducer.java
│       │   │   ├── OrderEventProducer.java
│       │   │   └── AlarmEventProducer.java
│       │   └── consumer/
│       │       ├── DeviceEventConsumer.java
│       │       ├── OrderEventConsumer.java
│       │       └── AlarmEventConsumer.java
│       └── config/                           # 核心模块配置
│           ├── RedisConfig.java
│           ├── RedissonConfig.java
│           ├── MybatisPlusConfig.java
│           └── RocketMQConfig.java
│
├── iot-access/                              # 接入层模块 (jar)
│   ├── pom.xml
│   └── src/main/java/com/iot/access/
│       ├── mqtt/                             # Netty MQTT 服务端
│       │   ├── MqttServer.java               #   MQTT服务端启动类
│       │   ├── MqttServerConfig.java         #   MQTT服务端配置
│       │   ├── codec/                        #   协议编解码
│       │   │   ├── MqttEncoder.java
│       │   │   └── MqttDecoder.java
│       │   └── handler/                      #   消息处理器
│       │       ├── MqttConnectHandler.java   #     连接处理
│       │       ├── MqttPublishHandler.java   #     发布处理
│       │       ├── MqttSubscribeHandler.java #     订阅处理
│       │       ├── MqttHeartbeatHandler.java #     心跳处理
│       │       └── MqttDisconnectHandler.java#     断连处理
│       ├── websocket/                        # WebSocket 服务
│       │   ├── WebSocketConfig.java          #   WS配置
│       │   ├── WebSocketServer.java          #   WS服务端
│       │   └── WebSocketSessionManager.java  #   会话管理器
│       └── config/
│           └── NettyConfig.java
│
├── iot-api/                                 # HTTP接口层模块 (jar, 主启动类)
│   ├── pom.xml
│   └── src/main/java/com/iot/api/
│       ├── IotApplication.java               # Spring Boot 主启动类
│       ├── controller/                       # 控制器
│       │   ├── user/                         #   用户端
│       │   │   ├── UserController.java       #     用户注册/登录/信息
│       │   │   ├── StationController.java    #     站点查询
│       │   │   ├── ChargerController.java    #     充电桩查询
│       │   │   ├── ChargeController.java     #     充电控制(启桩/停桩)
│       │   │   ├── ReservationController.java#     预约管理
│       │   │   └── OrderController.java      #     订单管理
│       │   └── admin/                        #   运营后台
│       │       ├── AdminStationController.java
│       │       ├── AdminChargerController.java
│       │       ├── AdminOrderController.java
│       │       ├── AdminPricingController.java
│       │       ├── AdminAlarmController.java
│       │       └── AdminStatisticsController.java
│       ├── security/                         # 安全配置
│       │   ├── SecurityConfig.java           #   Spring Security配置
│       │   ├── JwtAuthFilter.java            #   JWT认证过滤器
│       │   ├── JwtAuthEntryPoint.java        #   认证入口点
│       │   └── UserDetailsServiceImpl.java   #   用户详情服务
│       ├── filter/                           # 过滤器
│       │   ├── XssFilter.java                #   XSS过滤
│       │   └── RateLimitFilter.java          #   限流过滤
│       ├── aspect/                           # AOP切面
│       │   ├── LogAspect.java                #   操作日志切面
│       │   └── IdempotentAspect.java         #   幂等性切面
│       └── config/
│           ├── WebConfig.java                #   Web MVC配置
│           └── CorsConfig.java               #   跨域配置
│
└── iot-simulator/                           # 虚拟充电桩模拟器 (独立启动 jar)
    ├── pom.xml
    └── src/main/java/com/iot/simulator/
        ├── SimulatorApplication.java         # 模拟器启动类
        ├── config/
        │   └── SimulatorConfig.java          #   模拟器配置
        ├── client/
        │   └── MqttClientManager.java        #   MQTT客户端管理
        ├── device/
        │   └── VirtualCharger.java           #   虚拟充电桩
        ├── task/
        │   ├── HeartbeatTask.java            #   心跳模拟任务
        │   ├── StatusReportTask.java         #   状态上报任务
        │   └── ChargeSimulateTask.java       #   充电模拟任务
        └── controller/
            └── SimulatorController.java      #   模拟器命令行控制台
```

### 2.2 父 POM 关键依赖版本管理

```xml
<properties>
    <java.version>21</java.version>
    <spring-boot.version>3.5.14</spring-boot.version>
    <mybatis-plus.version>3.5.16</mybatis-plus.version>
    <mysql.version>8.0.33</mysql.version>
    <redisson.version>3.40.2</redisson.version>
    <rocketmq.version>5.5.0</rocketmq.version>
    <netty.version>4.1.133.Final</netty.version>
    <paho.version>1.2.5</paho.version>
    <hutool.version>5.8.28</hutool.version>
    <lombok.version>1.18.36</lombok.version>
</properties>
```

### 2.3 包命名规范

- 基础包路径：`com.iot.{module}`
- 实体类：`com.iot.core.entity`
- Mapper：`com.iot.core.mapper`
- Service接口：`com.iot.core.service`
- Service实现：`com.iot.core.service.impl`
- Controller：`com.iot.api.controller.{user|admin}`
- 配置类：`com.iot.{module}.config`
- 工具类：`com.iot.common.util`

---

## 3. 数据库设计

> **说明**：表需要建在 MySQL `iot_charge_platform` 库中。建表操作由用户亲自执行。

### 3.1 数据库创建

```sql
CREATE DATABASE IF NOT EXISTS iot_charge_platform
    DEFAULT CHARACTER SET utf8mb4
    DEFAULT COLLATE utf8mb4_general_ci;
```

### 3.2 充电站表 (station)

```sql
CREATE TABLE station (
    id            BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    name          VARCHAR(100)  NOT NULL                COMMENT '充电站名称',
    address       VARCHAR(255)  DEFAULT NULL            COMMENT '详细地址',
    longitude     DECIMAL(10,7) DEFAULT NULL            COMMENT '经度',
    latitude      DECIMAL(10,7) DEFAULT NULL            COMMENT '纬度',
    business_hours VARCHAR(50)  DEFAULT '00:00-24:00'   COMMENT '营业时间',
    contact       VARCHAR(20)   DEFAULT NULL            COMMENT '联系电话',
    status        TINYINT       NOT NULL DEFAULT 1      COMMENT '状态：0-暂停营业，1-营业中，2-维护中',
    create_time   DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time   DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    INDEX idx_station_status (status),
    INDEX idx_station_location (longitude, latitude)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='充电站表';
```

### 3.3 充电桩表 (charger)

```sql
CREATE TABLE charger (
    id               BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    sn               VARCHAR(50)   NOT NULL                COMMENT '设备唯一序列号',
    name             VARCHAR(50)   DEFAULT NULL            COMMENT '充电桩名称',
    station_id       BIGINT        NOT NULL                COMMENT '所属充电站ID',
    power            DECIMAL(5,2)  NOT NULL DEFAULT 7.00   COMMENT '额定功率(kW)',
    status           TINYINT       NOT NULL DEFAULT 0      COMMENT '状态：0-离线，1-空闲，2-充电中，3-故障，4-锁定',
    current_voltage  DECIMAL(5,2)  DEFAULT NULL            COMMENT '当前电压(V)',
    current_current  DECIMAL(5,2)  DEFAULT NULL            COMMENT '当前电流(A)',
    current_power    DECIMAL(5,2)  DEFAULT NULL            COMMENT '当前功率(kW)',
    charged_energy   DECIMAL(8,2)  DEFAULT 0.00            COMMENT '已充电量(kWh)',
    temperature      DECIMAL(5,2)  DEFAULT NULL            COMMENT '设备温度(℃)',
    last_online_time DATETIME      DEFAULT NULL            COMMENT '最后上线时间',
    create_time      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE INDEX uk_sn (sn),
    INDEX idx_station_id (station_id),
    INDEX idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='充电桩表';
```

### 3.4 用户表 (user)

```sql
CREATE TABLE user (
    id          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    phone       VARCHAR(20)  NOT NULL                COMMENT '手机号',
    password    VARCHAR(128) NOT NULL                COMMENT '密码(BCrypt加密)',
    nickname    VARCHAR(50)  DEFAULT NULL            COMMENT '昵称',
    avatar      VARCHAR(255) DEFAULT NULL            COMMENT '头像URL',
    plate_no    VARCHAR(20)  DEFAULT NULL            COMMENT '车牌号',
    car_model   VARCHAR(50)  DEFAULT NULL            COMMENT '车型',
    status      TINYINT      NOT NULL DEFAULT 1      COMMENT '状态：0-禁用，1-正常',
    last_login  DATETIME     DEFAULT NULL            COMMENT '最后登录时间',
    create_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE INDEX uk_phone (phone)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户表';
```

### 3.5 充电订单表 (charge_order)

```sql
CREATE TABLE charge_order (
    id               BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    order_no         VARCHAR(32)   NOT NULL                COMMENT '订单编号',
    user_id          BIGINT        NOT NULL                COMMENT '用户ID',
    charger_id       BIGINT        NOT NULL                COMMENT '充电桩ID',
    station_id       BIGINT        NOT NULL                COMMENT '充电站ID',
    start_time       DATETIME      DEFAULT NULL            COMMENT '开始充电时间',
    end_time         DATETIME      DEFAULT NULL            COMMENT '结束充电时间',
    charged_energy   DECIMAL(8,2)  DEFAULT 0.00            COMMENT '充电总量(kWh)',
    total_amount     DECIMAL(10,2) DEFAULT 0.00            COMMENT '总金额(元)',
    electricity_fee  DECIMAL(10,2) DEFAULT 0.00            COMMENT '电费(元)',
    service_fee      DECIMAL(10,2) DEFAULT 0.00            COMMENT '服务费(元)',
    pay_status       TINYINT       NOT NULL DEFAULT 0      COMMENT '支付状态：0-未支付，1-已支付，2-已退款',
    order_status     TINYINT       NOT NULL DEFAULT 1      COMMENT '订单状态：0-待支付，1-充电中，2-已完成，3-已取消，4-异常',
    pay_time         DATETIME      DEFAULT NULL            COMMENT '支付时间',
    cancel_reason    VARCHAR(255)  DEFAULT NULL            COMMENT '取消原因',
    version          INT           NOT NULL DEFAULT 0      COMMENT '乐观锁版本号',
    create_time      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE INDEX uk_order_no (order_no),
    INDEX idx_user_id (user_id),
    INDEX idx_charger_id (charger_id),
    INDEX idx_order_status (order_status),
    INDEX idx_create_time (create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='充电订单表';
```

### 3.6 预约订单表 (reservation_order)

```sql
CREATE TABLE reservation_order (
    id              BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    order_no        VARCHAR(32)   NOT NULL                COMMENT '预约编号',
    user_id         BIGINT        NOT NULL                COMMENT '用户ID',
    charger_id      BIGINT        NOT NULL                COMMENT '充电桩ID',
    station_id      BIGINT        NOT NULL                COMMENT '充电站ID',
    reserve_date    DATE          NOT NULL                COMMENT '预约日期',
    start_time      TIME          NOT NULL                COMMENT '预约开始时间',
    end_time        TIME          NOT NULL                COMMENT '预约结束时间',
    deposit         DECIMAL(10,2) NOT NULL DEFAULT 30.00  COMMENT '预约押金(元)',
    penalty         DECIMAL(10,2) NOT NULL DEFAULT 10.00  COMMENT '违约金(元)',
    status          TINYINT       NOT NULL DEFAULT 0      COMMENT '状态：0-待使用，1-已使用，2-已取消，3-已超时',
    pay_status      TINYINT       NOT NULL DEFAULT 0      COMMENT '支付状态：0-未支付，1-已支付，2-已退款',
    reminder_sent   TINYINT       NOT NULL DEFAULT 0      COMMENT '是否已发送提醒：0-否，1-是',
    create_time     DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time     DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE INDEX uk_reserve_no (order_no),
    INDEX idx_user_id (user_id),
    INDEX idx_charger_date (charger_id, reserve_date),
    INDEX idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='预约订单表';
```

### 3.7 计费规则表 (pricing_rule)

```sql
CREATE TABLE pricing_rule (
    id                 BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    name               VARCHAR(100)  NOT NULL                COMMENT '规则名称',
    station_id         BIGINT        NOT NULL DEFAULT 0      COMMENT '所属充电站ID(0表示全局规则)',
    rule_type          TINYINT       NOT NULL                COMMENT '规则类型：1-基础电价，2-峰谷电价，3-阶梯电价',
    start_time         TIME          DEFAULT NULL            COMMENT '时段开始时间(峰谷电价使用)',
    end_time           TIME          DEFAULT NULL            COMMENT '时段结束时间(峰谷电价使用)',
    electricity_price  DECIMAL(8,4)  NOT NULL                COMMENT '电价(元/kWh)',
    service_price      DECIMAL(8,4)  NOT NULL                COMMENT '服务费(元/kWh)',
    priority           INT           NOT NULL DEFAULT 0      COMMENT '优先级(数字越大越优先匹配)',
    status             TINYINT       NOT NULL DEFAULT 1      COMMENT '状态：0-禁用，1-启用',
    create_time        DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time        DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    INDEX idx_station_type (station_id, rule_type),
    INDEX idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='计费规则表';
```

### 3.8 设备日志表 (device_log)

```sql
CREATE TABLE device_log (
    id          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    charger_id  BIGINT       NOT NULL                COMMENT '充电桩ID',
    sn          VARCHAR(50)  NOT NULL                COMMENT '设备SN',
    event_type  VARCHAR(50)  NOT NULL                COMMENT '事件类型：ONLINE/OFFLINE/STATUS_CHANGE/COMMAND/FAULT',
    content     TEXT         DEFAULT NULL            COMMENT '事件内容(JSON格式)',
    create_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (id),
    INDEX idx_charger_id (charger_id),
    INDEX idx_event_type (event_type),
    INDEX idx_create_time (create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='设备日志表';
```

### 3.9 告警记录表 (alarm)

```sql
CREATE TABLE alarm (
    id          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    charger_id  BIGINT       NOT NULL                COMMENT '充电桩ID',
    station_id  BIGINT       NOT NULL                COMMENT '充电站ID',
    alarm_type  VARCHAR(50)  NOT NULL                COMMENT '告警类型：OVER_TEMP/OVER_VOLT/UNDER_VOLT/SHORT_CIRCUIT/LEAKAGE/OFFLINE/COMM_ERROR',
    alarm_level TINYINT      NOT NULL                COMMENT '告警级别：1-一般，2-重要，3-紧急',
    content     TEXT         DEFAULT NULL            COMMENT '告警内容',
    status      TINYINT      NOT NULL DEFAULT 0      COMMENT '状态：0-未处理，1-已处理',
    handler_id  BIGINT       DEFAULT NULL            COMMENT '处理人ID',
    handle_time DATETIME     DEFAULT NULL            COMMENT '处理时间',
    handle_note TEXT         DEFAULT NULL            COMMENT '处理备注',
    create_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (id),
    INDEX idx_charger_id (charger_id),
    INDEX idx_alarm_level (alarm_level),
    INDEX idx_status (status),
    INDEX idx_create_time (create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='告警记录表';
```

### 3.10 角色表 (role)

```sql
CREATE TABLE role (
    id          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    name        VARCHAR(50)  NOT NULL                COMMENT '角色名称',
    code        VARCHAR(50)  NOT NULL                COMMENT '角色编码',
    description VARCHAR(200) DEFAULT NULL            COMMENT '角色描述',
    status      TINYINT      NOT NULL DEFAULT 1      COMMENT '状态：0-禁用，1-启用',
    create_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE INDEX uk_code (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='角色表';
```

### 3.11 权限表 (permission)

```sql
CREATE TABLE permission (
    id          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    name        VARCHAR(50)  NOT NULL                COMMENT '权限名称',
    code        VARCHAR(100) NOT NULL                COMMENT '权限编码',
    type        TINYINT      NOT NULL DEFAULT 1      COMMENT '类型：1-菜单，2-按钮，3-接口',
    parent_id   BIGINT       NOT NULL DEFAULT 0      COMMENT '父权限ID',
    path        VARCHAR(200) DEFAULT NULL            COMMENT '路由路径/接口路径',
    sort        INT          NOT NULL DEFAULT 0      COMMENT '排序',
    create_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (id),
    UNIQUE INDEX uk_code (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='权限表';
```

### 3.12 关联表

```sql
-- 用户角色关联表
CREATE TABLE user_role (
    id      BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    user_id BIGINT NOT NULL COMMENT '用户ID',
    role_id BIGINT NOT NULL COMMENT '角色ID',
    PRIMARY KEY (id),
    UNIQUE INDEX uk_user_role (user_id, role_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户角色关联表';

-- 角色权限关联表
CREATE TABLE role_permission (
    id            BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    role_id       BIGINT NOT NULL COMMENT '角色ID',
    permission_id BIGINT NOT NULL COMMENT '权限ID',
    PRIMARY KEY (id),
    UNIQUE INDEX uk_role_permission (role_id, permission_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='角色权限关联表';
```

### 3.13 操作日志表 (operation_log)

```sql
CREATE TABLE operation_log (
    id          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    user_id     BIGINT       DEFAULT NULL            COMMENT '操作用户ID',
    username    VARCHAR(50)  DEFAULT NULL            COMMENT '操作用户名',
    module      VARCHAR(50)  NOT NULL                COMMENT '操作模块',
    operation   VARCHAR(50)  NOT NULL                COMMENT '操作类型',
    method      VARCHAR(200) NOT NULL                COMMENT '请求方法',
    params      TEXT         DEFAULT NULL            COMMENT '请求参数',
    ip          VARCHAR(50)  DEFAULT NULL            COMMENT 'IP地址',
    status      TINYINT      NOT NULL DEFAULT 1      COMMENT '操作状态：0-失败，1-成功',
    error_msg   TEXT         DEFAULT NULL            COMMENT '错误信息',
    cost_time   BIGINT       DEFAULT NULL            COMMENT '耗时(ms)',
    create_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (id),
    INDEX idx_user_id (user_id),
    INDEX idx_create_time (create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='操作日志表';
```

### 3.14 数据库设计说明

**索引策略**：
- 高频查询字段（status、user_id、charger_id）建立普通索引
- 唯一约束字段（sn、phone、order_no）建立唯一索引
- 地理位置字段（longitude, latitude）建立复合索引用于附近搜索
- 时间字段（create_time）建立索引用于范围查询

**并发控制**：
- 充电订单表使用 `version` 字段实现乐观锁，防止并发更新冲突
- MyBatis-Plus 的 `@Version` 注解自动处理版本号递增

**数据归档**：
- 设备日志表（device_log）数据量增长快，建议按月归档或使用分区表
- 操作日志表（operation_log）同样建议按月归档

---

## 4. Redis 数据结构设计

### 4.1 Key 命名规范

采用 `{业务域}:{子域}:{标识}` 的命名格式，使用冒号分隔，便于 Redis 可视化工具分组查看。

### 4.2 详细结构设计

| Key Pattern | 类型 | 说明 | 示例值 |
|-------------|------|------|--------|
| `device:status:{sn}` | Hash | 设备在线状态 | `{online:true, status:1, lastHeartbeat:1717238400}` |
| `device:data:{sn}` | Hash | 设备实时数据 | `{voltage:220.5, current:16.2, power:3.56, energy:12.5, temp:42}` |
| `device:lock:{chargerId}` | String | 充电桩分布式锁 | Redisson RLock，自动续期 |
| `reservation:lock:{chargerId}:{date}:{startTime}` | String | 预约时段锁 | Redisson RLock |
| `charger:status:{id}` | String | 充电桩状态缓存 | `"1"` (空闲) |
| `station:info:{id}` | Hash | 站点信息缓存 | `{name, address, status, availableCount}` |
| `user:token:blacklist:{userId}` | Set | 用户Token黑名单 | 退出登录时将JTI加入 |
| `rate:limit:{ip}:{api}` | String | 接口限流计数器 | 固定窗口/滑动窗口计数 |
| `pricing:rules:{stationId}` | List | 计费规则缓存 | JSON序列化的规则列表 |
| `online:count` | String | 在线设备总数 | `"85"` |

### 4.3 设备状态存储设计

```
# 设备上线时写入
HSET device:status:CHARGER-001 online 1 status 1 lastHeartbeat 1717238400000

# 心跳更新（只更新心跳时间）
HINCRBY device:status:CHARGER-001 lastHeartbeat 1717238430000

# 设备实时数据更新
HMSET device:data:CHARGER-001 voltage 220.5 current 16.2 power 3.56 energy 12.5 temp 42

# 设备离线时更新
HSET device:status:CHARGER-001 online 0 status 0
```

### 4.4 分布式锁设计

使用 Redisson 实现，无需手动续期（看门狗机制）：

```java
// 充电桩锁定（防止并发抢桩）
RLock lock = redissonClient.getLock("device:lock:" + chargerId);
if (lock.tryLock(0, 30, TimeUnit.SECONDS)) {
    try {
        // 执行启桩逻辑
    } finally {
        lock.unlock();
    }
}

// 预约时段锁定
RLock reserveLock = redissonClient.getLock(
    "reservation:lock:" + chargerId + ":" + date + ":" + startTime
);
```

### 4.5 缓存策略

| 数据类型 | 缓存策略 | 过期时间 | 更新机制 |
|----------|----------|----------|----------|
| 站点信息 | 主动加载+被动过期 | 30分钟 | 运营端修改时主动刷新 |
| 充电桩状态 | 实时写入 | 无过期 | 设备上报/MQTT消息触发更新 |
| 计费规则 | 主动加载+被动过期 | 1小时 | 运营端修改时主动刷新 |
| Token黑名单 | 写入时设置 | Token剩余有效期 | 退出登录/修改密码时加入 |
| 限流计数 | 滑动窗口 | 窗口大小 | 每次请求递增 |

---

## 5. RocketMQ 消息队列设计

### 5.1 Topic 与 Consumer Group

| Topic | Consumer Group | 消息类型 | 说明 |
|-------|---------------|----------|------|
| `device_event` | `device_event_consumer` | 设备事件 | 设备上下线、状态变更、故障上报 |
| `order_event` | `order_event_consumer` | 订单事件 | 订单创建、充电开始/结束、支付完成 |
| `alarm_event` | `alarm_event_consumer` | 告警事件 | 故障告警创建、处理通知 |
| `delay_message` | `delay_message_consumer` | 延时消息 | 预约超时取消(延时到预约结束时间)、充电超时断电 |

### 5.2 消息体设计

```json
// 设备事件消息
{
  "eventType": "ONLINE|OFFLINE|STATUS_CHANGE|FAULT",
  "sn": "CHARGER-001",
  "chargerId": 1,
  "stationId": 1,
  "data": {
    "status": 1,
    "voltage": 220.5,
    "current": 16.2,
    "power": 3.56,
    "energy": 12.5,
    "temperature": 42.0
  },
  "timestamp": 1717238400000
}

// 订单事件消息
{
  "eventType": "ORDER_CREATED|CHARGE_STARTED|CHARGE_ENDED|PAY_COMPLETED|REFUND",
  "orderNo": "CD20260601120000001",
  "orderId": 1,
  "userId": 1,
  "chargerId": 1,
  "data": {
    "totalAmount": 25.50,
    "chargedEnergy": 18.5
  },
  "timestamp": 1717238400000
}

// 告警事件消息
{
  "eventType": "ALARM_CREATED|ALARM_HANDLED",
  "alarmId": 1,
  "chargerId": 1,
  "stationId": 1,
  "alarmType": "OVER_TEMP",
  "alarmLevel": 2,
  "content": "设备温度超过安全阈值，当前温度85℃",
  "timestamp": 1717238400000
}
```

### 5.3 消费策略

- **集群消费**：同一条消息只被一个消费者消费（默认）
- **顺序消费**：设备事件按SN分区，保证同一设备的事件顺序处理
- **延时消息**：预约超时、充电超时场景使用 RocketMQ 延时级别
  - 预约超时：delayLevel = 预约时段时长对应的延时级别
  - 充电超时断电：delayLevel = `18`（30分钟）

### 5.4 消息幂等性

- 每条消息携带唯一 `messageId`
- 消费者处理前先检查 Redis 中是否已处理过该消息
- Redis Key：`mq:consumed:{messageId}`，过期时间 24 小时
- 已处理则直接返回 ACK，避免重复消费

---

## 6. 核心模块详细设计

### 6.1 IoT 设备接入模块

#### 6.1.1 设备状态机

设备状态严格遵守以下状态机流转，非法状态变更将被拒绝：

```
                    ┌──────────┐
         注册成功    │  离线    │  心跳超时(90s)
       ┌──────────► │ OFFLINE  │ ◄──────────┐
       │            └────┬─────┘            │
       │                 │ 心跳上报          │
       │            ┌────▼─────┐            │
       │            │  空闲    │            │
       │            │  IDLE    │◄───────────┤
       │            └────┬─────┘            │
       │     平台锁定     │ 启桩指令         │
       │            ┌────▼─────┐   故障上报  │
       │            │  锁定    │            │
       │            │ LOCKED   │            │
       │            └────┬─────┘            │
       │            启桩成功│               │
       │            ┌────▼─────┐            │
       │            │  充电中   │            │
       │            │ CHARGING │            │
       │            └────┬─────┘            │
       │       停桩/结束  │  故障上报       │
       │            ┌────▼─────┐            │
       └────────────│  故障    │────────────┘
                    │  FAULT   │
                    └─────────┘
```

**状态值枚举**：

| 状态值 | 枚举名 | 说明 | 允许的下一状态 |
|--------|--------|------|---------------|
| 0 | OFFLINE | 离线 | IDLE |
| 1 | IDLE | 空闲 | LOCKED, FAULT, OFFLINE |
| 2 | CHARGING | 充电中 | IDLE, FAULT, OFFLINE |
| 3 | FAULT | 故障 | IDLE, OFFLINE |
| 4 | LOCKED | 锁定(预约/扫码锁定) | CHARGING, IDLE, FAULT |

**状态变更验证**：设备服务在每次状态更新时，先检查当前状态是否允许变更为目标状态，不合法则抛出 `BusinessException`。

#### 6.1.2 设备注册与鉴权流程

```
虚拟充电桩                    平台(Netty MQTT)              数据库/Redis
    │                              │                          │
    │  1. CONNECT(sn, secret)      │                          │
    │─────────────────────────────►│                          │
    │                              │ 2. 验证SN+密钥           │
    │                              │─────────────────────────►│ 查询charger表
    │                              │◄─────────────────────────│ 验证通过
    │  3. CONNACK(OK + token)      │                          │
    │◄─────────────────────────────│                          │
    │                              │ 4. 更新设备在线状态      │
    │                              │─────────────────────────►│ Redis: device:status
    │                              │                          │ device_online事件
    │                              │─────────────────────────►│ RocketMQ
    │                              │                          │
    │  5. PUBLISH heartbeat        │                          │
    │─────────────────────────────►│                          │
    │                              │ 6. 更新心跳时间          │
    │                              │─────────────────────────►│ Redis: lastHeartbeat
```

**鉴权验证逻辑**：
1. 从 CONNECT 报文的 username/password 提取 SN 和密钥
2. 查询 `charger` 表验证 SN 是否存在且设备未被禁用
3. 验证密钥是否匹配（HMAC-SHA256）
4. 验证通过后在 Redis `device:status:{sn}` 标记在线状态
5. 发送 RocketMQ 设备上线事件

#### 6.1.3 心跳保活机制

```
定时检查任务 (每30秒执行一次)
    │
    ▼
遍历 Redis device:status:* 所有设备的 lastHeartbeat
    │
    ▼
当前时间 - lastHeartbeat > 90秒？
    │
    ├── 是 ──► 标记设备离线
    │          - HSET device:status:{sn} online 0 status 0
    │          - UPDATE charger SET status=0 WHERE sn=?
    │          - INSERT INTO device_log (event_type='OFFLINE')
    │          - 发送 RocketMQ 设备离线事件
    │
    └── 否 ──► 跳过，设备正常
```

**设计要点**：
- 心跳检查由 `@Scheduled` 定时任务驱动，每 30 秒执行
- 心跳间隔 30 秒是设备端行为，超时 90 秒是平台端判定（容忍 2 个心跳周期丢失）
- 使用 Redis 存储心跳时间而非数据库，避免高频写入

#### 6.1.4 远程指令下发与确认

```
平台                           Netty MQTT Broker              虚拟充电桩
 │                                    │                          │
 │ 1. 业务触发下发指令               │                          │
 │    (启桩/停桩/重启)               │                          │
 │                                    │                          │
 │ 2. PUBLISH device/command/{sn}      │                          │
 │───────────────────────────────────►│                          │
 │                                    │ 3. 推送指令              │
 │                                    │─────────────────────────►│
 │                                    │                          │ 4. 执行指令
 │                                    │                          │
 │                                    │ 5. PUBLISH response       │
 │                                    │◄─────────────────────────│
 │ 6. 收到确认                       │                          │
 │◄───────────────────────────────────│                          │
 │                                    │                          │
 │ [超时未收到确认]                   │                          │
 │ 7. 重试(最多3次，指数退避)         │                          │
 │───────────────────────────────────►│                          │
```

**指令超时重试策略**：
- 平台下发指令后，启动超时计时器（10秒）
- 超时未收到确认：重试（最多3次）
- 重试间隔：1s → 2s → 4s（指数退避）
- 3次重试后仍未确认：标记指令失败，生成告警
- 使用 `CompletableFuture` + `orTimeout()` 实现异步等待

#### 6.1.5 连接治理

| 治理策略 | 实现方式 |
|----------|----------|
| 最大连接数 | Netty ServerBootstrap 设置 `SO_BACKLOG` 和连接计数 |
| 空闲检测 | Netty `IdleStateHandler` 检测读空闲 90s → 主动断连 |
| 资源隔离 | MQTT 连接使用独立的 EventLoopGroup |
| 连接数限制 | 通过 Redis 计数器限制单 IP 最大连接数 |
| 优雅关闭 | JVM ShutdownHook 中先停止接收新连接，再等待现有连接完成 |

### 6.2 用户端模块

#### 6.2.1 认证鉴权流程（JWT）

```
用户端                          iot-api                       Redis
 │                               │                              │
 │ 1. POST /api/user/login       │                              │
 │    {phone, password}          │                              │
 │──────────────────────────────►│                              │
 │                               │ 2. 验证手机号+密码          │
 │                               │    (BCrypt匹配)             │
 │                               │                              │
 │                               │ 3. 生成JWT Token            │
 │                               │    (userId, roles, 2h过期)  │
 │                               │                              │
 │ 4. 返回Token + 用户信息        │                              │
 │◄──────────────────────────────│                              │
 │                               │                              │
 │ 5. 后续请求携带Token           │                              │
 │    Authorization: Bearer xxx  │                              │
 │──────────────────────────────►│                              │
 │                               │ 6. JwtAuthFilter验证Token   │
 │                               │    解析→查黑名单→放行       │
 │                               │─────────────────────────────►│ 检查token:blacklist
 │                               │◄─────────────────────────────│
 │                               │                              │
 │ 7. 正常响应 / 401未授权        │                              │
 │◄──────────────────────────────│                              │
```

**JWT Token 设计**：
- Header：`{"alg": "HS256", "typ": "JWT"}`
- Payload：`{"sub": userId, "roles": ["user"], "iat": 时间戳, "exp": 过期时间, "jti": 唯一ID}`
- 签名密钥：配置文件中的 `jwt.secret`
- 有效期：2小时（Access Token），支持通过 Refresh Token 续期（可选）

#### 6.2.2 扫码启桩流程（核心流程）

```
用户端                     iot-api            iot-core          Redis           Netty MQTT       充电桩
 │                          │                   │                │                 │                │
 │ 1. POST /charge/start    │                   │                │                 │                │
 │    {chargerId}           │                   │                │                 │                │
 │─────────────────────────►│                   │                │                 │                │
 │                          │ 2. 验证用户认证    │                │                 │                │
 │                          │ 3. 校验充电桩状态  │                │                 │                │
 │                          │───────────────────►│ 查询充电桩状态  │                 │                │
 │                          │                   │───────────────►│ charger:status  │                │
 │                          │                   │◄───────────────│ status=1(空闲)  │                │
 │                          │                   │                │                 │                │
 │                          │ 4. 获取分布式锁    │                │                 │                │
 │                          │───────────────────►│ device:lock:{id}               │                │
 │                          │                   │◄───────────────│ 获取锁成功      │                │
 │                          │                   │                │                 │                │
 │                          │ 5. 更新充电桩状态  │                │                 │                │
 │                          │───────────────────►│ SET charger:status:1 = 4       │                │
 │                          │                   │ (LOCKED)       │                 │                │
 │                          │                   │                │                 │                │
 │                          │ 6. 创建充电订单    │                │                 │                │
 │                          │───────────────────►│ INSERT charge_order(充电中)     │                │
 │                          │                   │                │                 │                │
 │                          │ 7. 下发启桩指令    │                │                 │                │
 │                          │───────────────────►│                                │ PUBLISH cmd   │
 │                          │                   │────────────────────────────────►│ 启桩指令       │
 │                          │                   │                                │                │
 │                          │                   │◄────────────────────────────────│ CONNACK(确认) │
 │                          │                   │                │                 │                │
 │                          │ 8. 返回充电开始    │                │                 │                │
 │◄─────────────────────────│                   │                │                 │                │
 │                          │                   │                │                 │                │
 │ 9. WebSocket连接          │                   │                │                 │                │
 │    ws://host:9090/charge/{orderId}            │                │                 │                │
 │◄─────────────────────────│ ...实时推送充电进度...              │                 │                │
```

**关键设计要点**：
1. **先锁后操作**：先获取 Redis 分布式锁，再更新状态和下发指令
2. **锁超时处理**：30 秒锁超时，防止死锁；若指令下发超时，主动释放锁
3. **状态一致性**：任何一步失败，执行补偿逻辑——释放锁、恢复充电桩状态为 IDLE
4. **乐观锁兜底**：数据库 `charger` 更新时使用 `version` 字段，防止并发绕过 Redis 锁

#### 6.2.3 WebSocket 实时推送

```
WebSocket 连接建立
    │
    ▼
ws://host:9090/charge/{orderId}
    │
    ▼
WebSocketSessionManager 管理会话
    │
    ▼
设备上报数据 → MQTT Handler → DeviceService → WebSocketSessionManager.sendToOrder(orderId, data)
    │
    ▼
用户端实时接收：{voltage, current, power, energy, cost, duration}
```

**推送数据结构**：
```json
{
  "type": "CHARGE_PROGRESS",
  "data": {
    "orderId": 1,
    "orderNo": "CD20260601120000001",
    "voltage": 220.5,
    "current": 16.2,
    "power": 3.56,
    "chargedEnergy": 12.5,
    "currentCost": 15.80,
    "duration": 1200,
    "chargerStatus": 2
  },
  "timestamp": 1717238400000
}
```

### 6.3 订单与计费模块

#### 6.3.1 订单状态机

```
        创建订单
    ┌──────────────┐
    │   待支付     │
    │  PAY_PENDING │──── 支付超时 ────► 已取消 (CANCELLED)
    └──────┬───────┘
           │ 支付成功(预约场景)
    ┌──────▼───────┐
    │   充电中     │
    │  CHARGING    │──── 异常中断 ────► 异常 (ABNORMAL)
    └──────┬───────┘
           │ 充电结束 + 计费完成
    ┌──────▼───────┐
    │   已完成     │
    │  COMPLETED   │──── 退款申请 ────► 已退款 (REFUNDED)
    └──────────────┘
```

| 状态值 | 枚举名 | 说明 |
|--------|--------|------|
| 0 | PAY_PENDING | 待支付（预约场景先创建待支付订单） |
| 1 | CHARGING | 充电中 |
| 2 | COMPLETED | 已完成 |
| 3 | CANCELLED | 已取消 |
| 4 | ABNORMAL | 异常（需人工处理） |

#### 6.3.2 计费引擎设计（策略模式）

```
                    ┌──────────────────┐
                    │ PricingStrategy  │  计费策略接口
                    │ +calculate()      │
                    └────────┬─────────┘
                             │
          ┌──────────────────┼──────────────────┐
          │                  │                  │
┌─────────▼────────┐ ┌──────▼───────┐ ┌────────▼────────┐
│ BasePricing      │ │ PeakValley   │ │ TieredPricing   │
│ Strategy         │ │ Pricing      │ │ Strategy         │
│ (基础电价)       │ │ Strategy     │ │ (阶梯电价)       │
│                  │ │ (峰谷电价)   │ │                  │
│ price=kWh×电价   │ │ 按时段匹配   │ │ 按用量阶梯匹配  │
└──────────────────┘ └──────────────┘ └──────────────────┘

                    ┌──────────────────┐
                    │ PricingContext   │  计费上下文
                    │ +getStrategy()   │  根据规则匹配策略
                    │ +calculateFee()  │
                    └──────────────────┘
```

**计费计算流程**：

```
1. 充电结束，获取充电时间段和充电量
2. 查询该充电站的计费规则（PricingRule），按优先级排序
3. 对于峰谷电价，将充电时间按规则时段切分：
   - 如充电 14:00-15:30，跨越平段(14:00-17:00)和高峰(17:00-22:00)
   - 分别计算平段电量(3h × 功率)和高峰电量(0.5h × 功率)
4. 电费 = Σ(各时段电量 × 时段电价)
5. 服务费 = 总充电量 × 服务费单价
6. 总金额 = 电费 + 服务费
```

#### 6.3.3 支付流程（模拟支付）

```
用户端                      iot-api                    iot-core                     RocketMQ
 │                           │                           │                             │
 │ 1. POST /order/pay        │                           │                             │
 │    {orderNo}              │                           │                             │
 │──────────────────────────►│                           │                             │
 │                           │ 2. 验证订单状态           │                             │
 │                           │──────────────────────────►│                             │
 │                           │◄──────────────────────────│ status=COMPLETED             │
 │                           │                           │                             │
 │                           │ 3. 模拟支付处理           │                             │
 │                           │    (直接支付成功)         │                             │
 │                           │                           │                             │
 │                           │ 4. 更新订单支付状态       │                             │
 │                           │──────────────────────────►│ UPDATE pay_status=1         │
 │                           │                           │                             │
 │                           │ 5. 发送支付完成事件       │                             │
 │                           │──────────────────────────►│ send order_event            │
 │                           │                           │─────────────────────────────►│
 │ 6. 返回支付成功            │                           │                             │
 │◄──────────────────────────│                           │                             │
```

### 6.4 运营后台模块

运营后台是标准的 CRUD 管理模块，核心设计要点：

- **分页查询**：使用 MyBatis-Plus 的 `Page<T>` 实现分页，支持多条件筛选
- **数据导出**：使用 EasyExcel 或 POI 导出 Excel
- **权限控制**：通过 `@PreAuthorize` 注解控制操作权限

### 6.5 告警模块

#### 6.5.1 告警处理流程

```
充电桩                     Netty MQTT               RocketMQ                  运营后台(WebSocket)
 │                           │                        │                           │
 │ 1. 上报故障               │                        │                           │
 │    PUB device/alarm       │                        │                           │
 │──────────────────────────►│                        │                           │
 │                           │ 2. 解析故障报文         │                           │
 │                           │ 3. 发送告警事件         │                           │
 │                           │───────────────────────►│ alarm_event                │
 │                           │                        │                           │
 │                           │                        │ 4. Consumer处理:          │
 │                           │                        │    - INSERT alarm          │
 │                           │                        │    - UPDATE charger=FAULT  │
 │                           │                        │    - PUSH WebSocket        │
 │                           │                        │───────────────────────────►│ 弹窗+声音
 │                           │                        │                           │
 │                           │                        │                           │ 5. 管理员处理告警
 │                           │                        │◄───────────────────────────│ POST /admin/alarm/handle
 │                           │                        │                           │
 │                           │                        │ 6. UPDATE alarm=已处理    │
 │                           │                        │                           │
 │ 7. 故障恢复上报            │                        │                           │
 │──────────────────────────►│                        │                           │
 │                           │ 8. 更新设备状态为IDLE   │                           │
```

#### 6.5.2 告警级别与类型

**告警级别**：

| 级别 | 编码 | 说明 | 通知方式 |
|------|------|------|----------|
| 一般 | 1 | 设备温度偏高、电压波动 | 运营后台记录 |
| 重要 | 2 | 设备离线、通信异常 | 运营后台弹窗提醒 |
| 紧急 | 3 | 短路、漏电、过压 | 运营后台弹窗 + 声音报警 |

**告警类型**：OVER_TEMP（过温）、OVER_VOLT（过压）、UNDER_VOLT（欠压）、SHORT_CIRCUIT（短路）、LEAKAGE（漏电）、OFFLINE（离线）、COMM_ERROR（通信异常）

---

## 7. 接口设计

### 7.1 RESTful API 规范

**URL 规范**：
- 用户端：`/api/{resource}/{action}`
- 运营后台：`/api/admin/{resource}/{action}`
- HTTP 方法：GET（查询）、POST（新增/操作）、PUT（修改）、DELETE（删除）
- 路径使用小写+短横线，如 `/api/charge/start`

### 7.2 统一响应格式

```json
{
  "code": 200,
  "message": "操作成功",
  "data": {},
  "timestamp": 1717238400000
}
```

**响应码规范**：

| Code | 说明 |
|------|------|
| 200 | 成功 |
| 400 | 参数错误 |
| 401 | 未认证 |
| 403 | 无权限 |
| 404 | 资源不存在 |
| 409 | 资源冲突（如充电桩已被占用） |
| 429 | 请求过于频繁 |
| 500 | 服务器内部错误 |

### 7.3 分页请求/响应

**请求参数**：`?page=1&size=20&sort=create_time&order=desc`

**响应格式**：
```json
{
  "code": 200,
  "message": "success",
  "data": {
    "records": [],
    "total": 100,
    "page": 1,
    "size": 20,
    "pages": 5
  },
  "timestamp": 1717238400000
}
```

### 7.4 全局异常处理

```java
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public Result<Void> handleBusinessException(BusinessException e) {
        return Result.error(e.getCode(), e.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public Result<Void> handleValidation(MethodArgumentNotValidException e) {
        String msg = e.getBindingResult().getFieldErrors()
            .stream().map(FieldError::getDefaultMessage)
            .collect(Collectors.joining(", "));
        return Result.error(400, msg);
    }

    @ExceptionHandler(Exception.class)
    public Result<Void> handleException(Exception e) {
        log.error("系统异常", e);
        return Result.error(500, "服务器内部错误");
    }
}
```

### 7.5 幂等性设计

对于支付、启桩、停桩等关键操作，通过以下方式保证幂等：

1. **请求级幂等**：前端生成 `idempotent-key`（UUID），后端在 Redis 中记录：
   - Key：`idempotent:{userId}:{idempotentKey}`
   - 过期时间：5 分钟
   - 相同 key 的重复请求直接返回首次结果
2. **业务级幂等**：如启桩时检测该充电桩是否已处于充电中状态

---

## 8. 安全设计

### 8.1 多层防护体系

```
用户请求
    │
    ▼
┌─────────────┐
│  CORS 过滤器 │  跨域白名单控制
└──────┬──────┘
       ▼
┌─────────────┐
│  XSS 过滤器  │  输入过滤，防止XSS攻击
└──────┬──────┘
       ▼
┌─────────────┐
│  限流过滤器  │  IP+API粒度限流
└──────┬──────┘
       ▼
┌─────────────┐
│ JWT认证过滤  │  身份验证
└──────┬──────┘
       ▼
┌─────────────┐
│ 权限验证     │  @PreAuthorize角色/权限检查
└──────┬──────┘
       ▼
  业务Controller
```

### 8.2 关键安全措施

| 安全措施 | 实现方式 |
|----------|----------|
| 密码加密 | BCryptPasswordEncoder（Spring Security 默认） |
| SQL注入防护 | MyBatis-Plus 参数化查询，`#{}` 占位符 |
| XSS防护 | 自定义 XssFilter，对输入进行 HTML 转义 |
| CSRF防护 | 前后端分离 + JWT 无状态，天然免疫 CSRF |
| 接口限流 | Redis 滑动窗口限流 + 自定义 `@RateLimit` 注解 |
| CORS | 配置白名单域名，限制跨域来源 |
| JWT安全 | 短有效期(2h)、JTI 唯一标识支持黑名单、密钥外部化配置 |
| 敏感数据 | 手机号脱敏显示（138****8888）、日志中脱敏 |

### 8.3 设备端安全

- MQTT 连接使用 SN + 密钥鉴权（username/password 模式）
- 设备密钥使用 HMAC-SHA256 加密存储
- 设备只能发布自己的主题，不能订阅其他设备的指令主题
- 连接频率限制：单个设备重连间隔不小于 5 秒

---

## 9. 虚拟充电桩模拟器设计

### 9.1 模拟器架构

虚拟充电桩模拟器是一个独立的 Java 应用程序，通过 Eclipse Paho MQTT 客户端连接平台，模拟真实充电桩的行为。

```
┌──────────────────────────────────────────────────────────┐
│                iot-simulator (独立JAR)                     │
│                                                           │
│  ┌─────────────────┐         ┌─────────────────────────┐  │
│  │ SimulatorApp    │  启动    │ SimulatorController     │  │
│  │ (启动类)        │────────►│ (命令行交互控制台)       │  │
│  └─────────────────┘         └───────────┬─────────────┘  │
│                                          │                 │
│                     ┌────────────────────┼────────────┐    │
│                     │                    │            │    │
│               ┌─────▼─────┐    ┌────────▼──────┐    │    │
│               │ Virtual   │    │ Virtual       │    │    │
│               │ Charger 1 │    │ Charger N     │    │    │
│               │ (SN-001)  │... │ (SN-100)      │    │    │
│               └─────┬─────┘    └────────┬──────┘    │    │
│                     │                   │            │    │
│               ┌─────▼───────────────────▼──────┐     │    │
│               │    MqttClientManager           │     │    │
│               │    (统一MQTT客户端管理)          │     │    │
│               │    - 连接池管理                 │     │    │
│               │    - 消息路由                   │     │    │
│               └──────────────┬────────────────┘     │    │
│                              │                      │    │
└──────────────────────────────┼──────────────────────┘    │
                               │ MQTT (TCP:1883)
                               ▼
                        充电桩运营平台
```

### 9.2 模拟行为清单

| 行为 | 频率/触发方式 | 说明 |
|------|--------------|------|
| 设备注册 | 启动时执行一次 | 发送设备注册请求 |
| 心跳上报 | 每30秒 | 向 `device/heartbeat` 发送 |
| 状态上报 | 状态变更时立即发送 | 向 `device/status` 上报当前状态 |
| 数据上报 | 充电中每5秒 | 向 `device/data` 上报电压/电流/功率/电量 |
| 指令响应 | 收到指令后 | 向 `device/command/response` 发送确认 |
| 故障触发 | 手动/随机 | 模拟过温、过压、短路等故障 |
| 充电模拟 | 充电开始后 | 电量递增、功率波动模拟 |

### 9.3 虚拟充电桩实体

```java
public class VirtualCharger {
    private String sn;              // 设备唯一标识
    private String secret;          // 设备密钥
    private Long chargerId;         // 平台分配的ID
    private DeviceStatus status;    // 当前状态
    private double voltage;         // 当前电压(V)
    private double current;         // 当前电流(A)
    private double power;           // 当前功率(kW)
    private double chargedEnergy;   // 已充电量(kWh)
    private double temperature;     // 温度(℃)
    private ScheduledExecutorService scheduler;  // 定时任务调度
}
```

### 9.4 命令行交互

模拟器启动后提供命令行交互界面：

```
=== IoT充电桩虚拟模拟器 ===
命令列表：
  start [count]     - 启动指定数量的虚拟充电桩(默认10台)
  stop [sn]         - 停止指定的充电桩(不指定则停止全部)
  status            - 查看所有虚拟充电桩状态
  fault [sn] [type] - 触发指定充电桩的故障(type: TEMP/VOLT/SHORT)
  recover [sn]      - 恢复指定充电桩为正常状态
  exit              - 退出模拟器
>
```

---

## 10. 项目配置与部署

### 10.1 主应用配置 (application.yml)

```yaml
server:
  port: 8080

spring:
  application:
    name: iot-charge-platform
  datasource:
    url: jdbc:mysql://localhost:3306/iot_charge_platform?useUnicode=true&characterEncoding=utf8mb4&serverTimezone=Asia/Shanghai
    username: root
    password: ${DB_PASSWORD:root}
    driver-class-name: com.mysql.cj.jdbc.Driver
    hikari:
      maximum-pool-size: 20
      minimum-idle: 5
      idle-timeout: 300000
      connection-timeout: 20000
  data:
    redis:
      host: localhost
      port: 6379
      password: ${REDIS_PASSWORD:}
      database: 0
      lettuce:
        pool:
          max-active: 20
          max-idle: 10
          min-idle: 5
  threads:
    virtual:
      enabled: true  # 启用Java21虚拟线程

mybatis-plus:
  global-config:
    db-config:
      id-type: assign_id  # 雪花ID
      logic-delete-field: deleted
      logic-delete-value: 1
      logic-not-delete-value: 0
  configuration:
    log-impl: org.apache.ibatis.logging.stdout.StdOutImpl

rocketmq:
  name-server: localhost:9876
  producer:
    group: iot-platform-producer
    send-message-timeout: 3000
    retry-times-when-send-failed: 2

jwt:
  secret: ${JWT_SECRET:your-base64-encoded-secret-key-at-least-256-bits}
  expiration: 7200  # 2小时

mqtt:
  server:
    port: 1883
    boss-thread-count: 2
    worker-thread-count: 8
    max-connections: 200
    idle-timeout: 90  # 空闲超时(秒)

websocket:
  port: 9090

# 日志配置
logging:
  level:
    com.iot: DEBUG
    io.netty: INFO
    org.springframework: INFO
  file:
    path: ./logs
```

### 10.2 主启动类

```java
@SpringBootApplication
@MapperScan("com.iot.core.mapper")
@EnableScheduling
@EnableAsync
public class IotApplication {
    public static void main(String[] args) {
        SpringApplication.run(IotApplication.class, args);
    }
}
```

### 10.3 中间件启动顺序

```
1. MySQL 8.0      (端口 3306) — 必须最先启动
2. Redis          (端口 6379) — 必须
3. RocketMQ       (端口 9876/10911) — 必须
4. IoT平台        (端口 8080/1883/9090) — 依赖 1-3
5. 充电桩模拟器    (内部) — 依赖 4
```

### 10.4 部署架构

```
┌─────────────────────────────────────────────────┐
│              单机部署 (开发/演示环境)              │
│                                                  │
│  ┌──────────┐  ┌──────────┐  ┌───────────────┐  │
│  │ MySQL    │  │ Redis    │  │ RocketMQ      │  │
│  │ :3306    │  │ :6379    │  │ :9876/:10911  │  │
│  └────┬─────┘  └────┬─────┘  └───────┬───────┘  │
│       └──────────────┼───────────────┘          │
│                      │                          │
│              ┌───────▼───────┐                  │
│              │  IoT Platform │                  │
│              │  :8080(HTTP)  │                  │
│              │  :1883(MQTT)  │                  │
│              │  :9090(WS)    │                  │
│              └───────┬───────┘                  │
│                      │                          │
│              ┌───────▼───────┐                  │
│              │   Simulator   │                  │
│              │   (独立JAR)    │                  │
│              └───────────────┘                  │
└─────────────────────────────────────────────────┘
```

---

## 附录 A：项目里程碑与模块开发顺序

| 阶段 | 模块 | 依赖 | 说明 |
|------|------|------|------|
| 1 | iot-common | 无 | 基础工具、枚举、异常、响应模型 |
| 2 | iot-core (entity/mapper) | iot-common | 数据库实体和Mapper |
| 3 | iot-core (config) | iot-common | Redis、RocketMQ、MyBatis-Plus配置 |
| 4 | iot-core (service) | 2,3 | 核心业务服务实现 |
| 5 | iot-access (mqtt) | iot-common, iot-core | Netty MQTT 服务端 |
| 6 | iot-access (websocket) | iot-common, iot-core | WebSocket 推送服务 |
| 7 | iot-api (security) | iot-common, iot-core | Spring Security + JWT |
| 8 | iot-api (controller) | 4,7 | HTTP 接口层 |
| 9 | iot-simulator | iot-common | 虚拟充电桩模拟器 |

---

## 附录 B：技术风险与应对

| 风险 | 应对措施 |
|------|----------|
| Netty MQTT 编解码复杂 | 优先参考 netty-mqtt 开源实现，仅实现 MQTT 3.1.1 必要报文类型 |
| 状态一致性问题 | Redis 状态 + MySQL 数据双写，定时任务巡检修正不一致状态 |
| 并发抢桩超卖 | Redis 分布式锁（主防）+ 数据库乐观锁（兜底）双重保障 |
| 消息重复消费 | 消费端幂等处理（messageId + Redis 去重） |
| 长连接资源泄漏 | Netty IdleStateHandler + 定时巡检 + JVM ShutdownHook 优雅关闭 |
