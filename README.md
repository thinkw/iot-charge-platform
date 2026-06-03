# 物联网充电桩智能运营平台

**项目代码名**：iot-charge-platform  
**技术栈**：Java 21 + Spring Boot 3.5.14 + MyBatis-Plus + MySQL 8.0 + Redis + RocketMQ + Netty + MQTT + WebSocket + Vue3 + Element Plus  
**架构形态**：单体架构 + 独立虚拟充电桩模拟器  
**定位**：聚焦 IoT 设备接入与高并发交易能力的纯软件云平台  

---

## 项目简介

本项目从零构建了一套轻量级 **物联网充电桩智能运营平台**，采用行业标准的 MQTT + Netty 技术栈实现设备通信，结合成熟的高并发交易系统架构，完整实现 **"设备-平台-用户-运营"全业务闭环**。

**核心亮点**：
- ⚡ 自研 Netty MQTT 服务端，单机支撑 100+ 设备并发长连接
- 🔐 设备鉴权 + 状态机管控 + 远程指令下发，完整 IoT 设备管理
- 💰 策略模式计费引擎，支持基础电价/峰谷电价/阶梯电价
- 🔒 Redisson 分布式锁防并发抢桩，乐观锁兜底
- 📡 WebSocket 实时推送充电进度、大屏数据、告警通知
- 🖥️ Vue3 + Element Plus 运营大屏（ECharts 图表）
- 🎮 独立虚拟充电桩模拟器，无需真实硬件即可全流程演示

---

## 快速开始

### 环境准备

| 组件 | 版本 | 端口 |
|------|------|------|
| JDK | 21 | - |
| MySQL | 8.0 | 3306 |
| Redis | 7.0+ | 6379 |
| RocketMQ | 5.5.0 | 9876/10911 |
| Node.js（前端） | 18+ | - |

### 5 分钟快速启动

```bash
# 1. 初始化数据库
mysql -u root -p123456 -e "CREATE DATABASE iot_charge_platform CHARACTER SET utf8mb4;"
mysql -u root -p123456 iot_charge_platform < init-data.sql

# 2. 启动中间件（MySQL + Redis + RocketMQ NameServer + Broker）

# 3. 构建并启动后端
mvn clean compile -f pom.xml
mvn clean package -pl iot-api -f pom.xml -DskipTests
java -jar iot-api/target/iot-api-1.0.0-SNAPSHOT.jar

# 4. 构建并启动模拟器（新开终端）
mvn clean package -pl iot-simulator -f pom.xml -DskipTests
java -jar iot-simulator/target/iot-simulator-1.0.0-SNAPSHOT.jar
# 模拟器命令行中输入: start 10

# 5. 启动前端（新开终端，可选）
cd iot-frontend
npm install && npm run dev
# 访问 http://localhost:5173
```

**测试账号**：

| 角色 | 手机号 | 密码 |
|------|--------|------|
| 普通用户 | 13800000001 | 123456 |
| 管理员 | 13800000003 | 123456 |

---

## 项目结构

```
iot-charge-platform/
├── iot-common/          # 公共模块：枚举、异常、统一响应、工具类
├── iot-core/            # 核心业务：Entity、Mapper、Service、计费策略、MQ
├── iot-access/          # 接入层：Netty MQTT 服务端(1883) + WebSocket(9090)
├── iot-api/             # HTTP 接口层：Controller、Spring Security、JWT
├── iot-simulator/       # 虚拟充电桩模拟器（独立启动）
├── iot-frontend/        # Vue3 管理端 Web 项目
├── iot-miniapp/         # uni-app 微信小程序用户端
├── PRD.md               # 产品需求文档
├── DESIGN.md            # 技术设计文档
├── API_DOC.md           # API 接口文档
├── DEPLOY.md            # 部署文档
├── init-data.sql        # 测试数据 SQL
├── CLAUDE.md            # AI 开发助手配置
└── pom.xml              # Maven 父 POM
```

---

## 核心功能

### 用户端（微信小程序 + Web）
- 📱 手机号注册/登录（JWT 认证）
- 📍 充电站列表查询（支持按距离/价格/可用桩数排序 + 下拉刷新）
- 🔍 充电站详情与充电桩实时状态
- ⚡ 扫码启桩 / 结束充电
- 📊 实时充电数据（电压、电流、功率、电量、费用）
- 📋 订单列表 / 支付（模拟）/ 退款
- 📅 充电桩预约（时段冲突检测 + 押金）

### 运营后台
- 🏪 充电站/充电桩 CRUD 管理
- 📦 全量订单查询与异常处理
- 💲 计费规则配置（基础电价 + 峰谷电价）
- 🚨 告警监控与处理
- 📈 数据统计大屏（在线率、订单量、营收、趋势图、站点排名、故障分布）

### 设备接入
- 🔌 MQTT 3.1.1 协议接入
- 🔐 SN + 密钥设备鉴权
- 💓 30 秒心跳保活 + 90 秒超时离线
- 🎛️ 远程指令下发（启桩/停桩/重启/设参）+ 3 次重试
- 📡 状态上报与实时数据上报

---

## 技术架构

```
充电桩模拟器 --MQTT(TCP:1883)--> Netty MQTT Server
                                      │
用户浏览器 --HTTP(8080)--> Spring MVC Controller
                                      │
运营后台 --WebSocket(9090)--> 实时数据推送
                                      │
                              ┌───────▼────────┐
                              │   业务服务层     │
                              │  设备/用户/订单   │
                              │  计费/告警/统计   │
                              └───────┬────────┘
                                      │
                         ┌────────────┼────────────┐
                         │            │            │
                        MySQL      Redis      RocketMQ
                      (业务数据)  (状态/缓存)  (事件队列)
```

**关键技术选型理由**：
| 技术 | 理由 |
|------|------|
| Java 21 虚拟线程 | 大幅提升 Netty 长连接场景的并发处理能力 |
| Netty 自研 MQTT | 完全可控的设备通信层，掌握 IoT 核心技术 |
| Redisson 分布式锁 | 看门狗自动续期，防止并发抢桩超卖 |
| RocketMQ 延时消息 | 预约超时取消、充电超时断电 |
| 策略模式计费 | 支持基础/峰谷/阶梯电价热插拔 |

---

## 文档导航

| 文档 | 说明 |
|------|------|
| [PRD.md](PRD.md) | 产品需求文档 — 功能需求、业务流程、里程碑 |
| [DESIGN.md](DESIGN.md) | 技术设计文档 — 架构、数据库 DDL、模块设计、接口规范 |
| [API_DOC.md](API_DOC.md) | API 接口文档 — 全部 REST API + WebSocket + MQTT 主题 |
| [DEPLOY.md](DEPLOY.md) | 部署文档 — 环境安装、中间件配置、启动运行 |
| [CLAUDE.md](CLAUDE.md) | AI 开发助手配置 — 构建命令、架构速查、开发约定 |

---

## 项目阶段

| 阶段 | 状态 | 内容 |
|------|------|------|
| 阶段1 | ✅ | 环境搭建与基础设计 |
| 阶段2 | ✅ | 设备接入与模拟器开发 |
| 阶段3 | ✅ | 用户端核心功能开发 |
| 阶段4 | ✅ | 订单与计费模块开发 |
| 阶段5 | ✅ | 运营后台与告警开发 |
| 阶段6 | ✅ | 实时推送与大屏开发 |
| 阶段7 | ✅ | 联调测试与优化 |
| 阶段8 | ✅ | 文档整理与演示准备 |

