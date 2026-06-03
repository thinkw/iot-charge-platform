# 物联网充电桩智能运营平台 — 部署文档

**版本**：V1.0  
**目标环境**：Windows 10/11 或 Linux（开发/演示环境）  
**部署形态**：单机部署

---

## 目录

1. [环境要求](#1-环境要求)
2. [中间件安装](#2-中间件安装)
3. [数据库初始化](#3-数据库初始化)
4. [项目构建](#4-项目构建)
5. [启动运行](#5-启动运行)
6. [前端构建与部署](#6-前端构建与部署)
7. [验证测试](#7-验证测试)
8. [常见问题](#8-常见问题)

---

## 1. 环境要求

| 组件 | 版本要求 | 说明 |
|------|----------|------|
| JDK | 21 LTS | 推荐 Temurin 21 |
| Maven | 3.8+ | 构建工具 |
| MySQL | 8.0+ | 业务数据库 |
| Redis | 7.0+ | 缓存/状态存储/分布式锁 |
| RocketMQ | 5.5.0 | 消息队列 |
| Node.js | 18+ | 前端构建（可选，仅需构建时安装） |
| npm | 9+ | 前端包管理（可选） |

### 1.1 Windows 环境验证

```bash
java -version
# 输出应包含：openjdk version "21"

mvn -version
# 输出应包含：Apache Maven 3.9.x 和 Java 21

mysql --version
# 输出应包含：mysql 8.0.x

redis-cli --version
# 输出应包含：redis-cli 7.x
```

---

## 2. 中间件安装

### 2.1 MySQL 8.0

**Windows**：
1. 下载 MySQL 8.0 安装包：https://dev.mysql.com/downloads/mysql/8.0.html
2. 安装时设置 root 密码为 `123456`（或修改 `application.yml` 中的密码）
3. 确保服务已启动，默认端口 3306

**Linux**：
```bash
# Ubuntu/Debian
sudo apt install mysql-server-8.0

# 设置密码
sudo mysql -u root
ALTER USER 'root'@'localhost' IDENTIFIED WITH mysql_native_password BY '123456';
FLUSH PRIVILEGES;
```

**验证**：
```bash
mysql -u root -p123456 -e "SELECT VERSION();"
```

### 2.2 Redis

**Windows**：
1. 下载 Redis for Windows：https://github.com/tporadowski/redis/releases
2. 解压后运行 `redis-server.exe`
3. 默认端口 6379，无密码

**Linux**：
```bash
# 安装
sudo apt install redis-server

# 启动
sudo systemctl start redis
sudo systemctl enable redis
```

**验证**：
```bash
redis-cli ping
# 输出：PONG
```

### 2.3 RocketMQ

**Windows/Linux**：
1. 下载 RocketMQ 5.5.0：https://rocketmq.apache.org/download/
2. 解压到任意目录，如 `D:\rocketmq` 或 `/opt/rocketmq`

**启动 NameServer**（必须先启动）：
```bash
# Windows
set JAVA_HOME=C:\Program Files\Java\jdk-21
bin\mqnamesrv.cmd

# Linux
export JAVA_HOME=/usr/lib/jvm/java-21
nohup sh bin/mqnamesrv &
```

**启动 Broker**：
```bash
# Windows
bin\mqbroker.cmd -n localhost:9876

# Linux
nohup sh bin/mqbroker -n localhost:9876 &
```

**验证**：
```bash
# NameServer 端口 9876，Broker 端口 10911
netstat -an | findstr 9876   # Windows
netstat -an | grep 9876      # Linux
```

---

## 3. 数据库初始化

### 3.1 创建数据库

登录 MySQL 并执行：

```sql
CREATE DATABASE IF NOT EXISTS iot_charge_platform
    DEFAULT CHARACTER SET utf8mb4
    DEFAULT COLLATE utf8mb4_general_ci;
```

### 3.2 执行建表 DDL

从项目根目录的 `DESIGN.md` 第 3 章中提取并执行全部 DDL 语句。

### 3.3 导入测试数据

```bash
mysql -u root -p123456 iot_charge_platform < init-data.sql
```

**测试数据概览**：

| 数据项 | 数量 | 说明 |
|--------|------|------|
| 充电站 | 5 个 | 朝阳、海淀、西城、东城、丰台 |
| 充电桩 | 100 台 | SN: CHARGER-001 ~ CHARGER-100 |
| 计费规则 | 22 条 | 1 条全局 + 21 条站级 |
| 角色 | 3 个 | ROLE_USER / ROLE_ADMIN / ROLE_SYSTEM |
| 测试用户 | 3 个 | 密码均为 123456 |

---

## 4. 项目构建

### 4.1 编译全部模块

```bash
cd iot-charge-platform
mvn clean compile -f pom.xml
```

### 4.2 运行测试

```bash
mvn test -f pom.xml
```

预期结果：133 个测试全部通过。

### 4.3 打包可运行 JAR

```bash
# 打包主应用
mvn clean package -pl iot-api -f pom.xml -DskipTests

# 打包模拟器
mvn clean package -pl iot-simulator -f pom.xml -DskipTests
```

---

## 5. 启动运行

### 5.1 启动顺序

```
1. MySQL      (端口 3306) — 必须最先启动
2. Redis      (端口 6379) — 必须
3. RocketMQ   (端口 9876/10911) — 必须
4. 主应用      (端口 8080/1883/9090) — 依赖 1-3
5. 模拟器      (内部) — 依赖 4
```

### 5.2 启动主应用

```bash
java -jar iot-api/target/iot-api-1.0.0-SNAPSHOT.jar
```

**启动成功标志**：
```
==============================
MQTT Server 启动成功，监听端口: 1883
Boss线程数: 2, Worker线程数: 8
==============================
Started IotApiApplication in X.XXX seconds
```

**服务端口**：

| 端口 | 协议 | 用途 |
|------|------|------|
| 8080 | HTTP | REST API 接口 |
| 1883 | TCP/MQTT | 设备 MQTT 长连接 |
| 9090 | WebSocket | 实时数据推送 |

### 5.3 启动模拟器

```bash
java -jar iot-simulator/target/iot-simulator-1.0.0-SNAPSHOT.jar
```

模拟器启动后提供命令行交互界面：

```
=== IoT充电桩虚拟模拟器 ===
命令列表：
  start [count]     - 启动指定数量的虚拟充电桩（默认10台）
  stop [sn]         - 停止指定的充电桩
  status            - 查看所有虚拟充电桩状态
  fault [sn] [type] - 触发故障
  recover [sn]      - 恢复设备
  exit              - 退出
>
```

### 5.4 停止服务

按 `Ctrl+C` 停止应用。主应用会自动执行优雅关闭：
- 关闭 MQTT Server（停止接收新连接，等待现有连接完成）
- 释放 Netty EventLoopGroup 资源

---

## 6. 前端构建与部署

### 6.1 开发模式启动

```bash
cd iot-frontend
npm install
npm run dev
```

前端开发服务器：`http://localhost:5173`

Vite 配置了代理：
- `/api` → `http://localhost:8080`（后端 API）
- `/ws` → `ws://localhost:9090`（WebSocket）

### 6.2 生产构建

```bash
cd iot-frontend
npm run build
```

构建产物输出到 `iot-api/src/main/resources/static/`，打包在主应用 JAR 中。

---

## 7. 验证测试

### 7.1 后端接口测试

使用 Postman 导入项目根目录的 `iot-charge-platform.postman_collection.json`：

1. 导入 Collection
2. 先执行「用户端 → 用户认证 → 用户登录」
3. Token 自动存入 Collection 变量
4. 依次执行其他接口测试

### 7.2 核心流程验证

| 步骤 | 操作 | 预期结果 |
|------|------|----------|
| 1 | 启动模拟器 `start 10` | 10 台虚拟充电桩上线 |
| 2 | 登录普通用户 `13800000001/123456` | 获取 JWT Token |
| 3 | 查询充电站列表 `GET /api/station/list` | 返回 5 个充电站 |
| 4 | 查看充电站详情 `GET /api/station/1` | 包含充电桩列表（有空闲桩） |
| 5 | 扫码启桩 `POST /api/charge/start` | 返回订单编号 |
| 6 | 查询充电状态 `GET /api/charge/status/{orderNo}` | 返回实时数据 |
| 7 | 结束充电 `POST /api/charge/stop` | 返回总费用 |
| 8 | 支付 `POST /api/order/pay` | 支付成功 |
| 9 | 登录管理员 `13800000003/123456` | 获取 Admin Token |
| 10 | 查看大屏 `GET /api/admin/statistics/dashboard` | 返回大屏数据 |
| 11 | 触发故障（模拟器 `fault CHARGER-001 OVER_TEMP`） | 产生告警记录 |
| 12 | 查看告警 `GET /api/admin/alarm/list` | 可见新告警 |
| 13 | 处理告警 `POST /api/admin/alarm/handle` | 告警已处理 |

### 7.3 性能验证

```bash
# 启动 100 台虚拟充电桩
start 100

# 观察控制台：100 台设备心跳正常、无断连
# 观察管理端大屏：在线设备数 = 100
```

---

## 8. 常见问题

### 8.1 MQTT Server 启动失败

**现象**：启动日志显示 `MQTT Server 启动失败`

**原因**：端口 1883 被占用

**解决**：
```bash
# Windows 查看占用
netstat -ano | findstr 1883

# 或修改 application.yml 中的 mqtt.server.port
```

### 8.2 RocketMQ 连接超时

**现象**：日志显示 `connect to localhost:9876 failed`

**解决**：确保 NameServer 和 Broker 已启动
```bash
# 验证 NameServer
telnet localhost 9876

# 验证 Broker
telnet localhost 10911
```

### 8.3 前端代理不通

**现象**：前端页面加载但请求 404

**解决**：检查 Vite 代理配置是否正确
```typescript
// iot-frontend/vite.config.ts
proxy: {
  '/api': 'http://localhost:8080',
  '/ws': {
    target: 'ws://localhost:9090',
    ws: true
  }
}
```

### 8.4 Redis 连接失败

**现象**：应用启动报 Redis 连接错误

**解决**：确保 Redis 已启动，端口 6379 可访问
```bash
redis-cli -h localhost -p 6379 ping
```

### 8.5 模拟器设备全部离线

**现象**：模拟器启动后设备状态为离线

**解决**：
1. 检查主应用 MQTT Server 是否已启动（端口 1883）
2. 检查模拟器 `application.yml` 中 MQTT 连接地址是否正确
3. 查看主应用日志，确认设备鉴权是否通过
