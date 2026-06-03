# 物联网充电桩智能运营平台 — API 接口文档

**版本**：V1.0  
**基础 URL**：`http://localhost:8080`  
**认证方式**：JWT Token（Header: `Authorization: Bearer <token>`）  
**内容类型**：`application/json`

---

## 目录

1. [通用说明](#1-通用说明)
2. [用户端接口](#2-用户端接口)
   - 2.1 [用户认证](#21-用户认证)
   - 2.2 [站点与充电桩查询](#22-站点与充电桩查询)
   - 2.3 [充电控制](#23-充电控制)
   - 2.4 [订单管理](#24-订单管理)
   - 2.5 [预约管理](#25-预约管理)
3. [运营后台接口](#3-运营后台接口)
   - 3.1 [充电站管理](#31-充电站管理)
   - 3.2 [充电桩管理](#32-充电桩管理)
   - 3.3 [订单管理](#33-订单管理)
   - 3.4 [计费规则管理](#34-计费规则管理)
   - 3.5 [告警管理](#35-告警管理)
   - 3.6 [数据统计](#36-数据统计)
4. [WebSocket 推送](#4-websocket-推送)
5. [MQTT 设备端主题](#5-mqtt-设备端主题)
6. [错误码说明](#6-错误码说明)

---

## 1. 通用说明

### 1.1 认证机制

- 用户注册或登录成功后，服务端返回 JWT Token
- 后续请求需在 Header 中携带：`Authorization: Bearer <token>`
- Token 有效期：24 小时（`jwt.expiration=86400000`）
- 除 `/api/user/register` 和 `/api/user/login` 外，所有 `/api/**` 接口需要认证
- `/api/admin/**` 接口需要 ADMIN 角色

### 1.2 统一响应格式

所有接口返回统一的 JSON 格式：

```json
{
  "code": 200,
  "message": "操作成功",
  "data": {},
  "timestamp": 1717238400000
}
```

### 1.3 分页格式

分页请求参数：`?page=1&size=20`

分页响应格式：

```json
{
  "code": 200,
  "message": "操作成功",
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

---

## 2. 用户端接口

### 2.1 用户认证

#### 用户注册

```
POST /api/user/register
```

**请求体**：

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| phone | String | 是 | 手机号（11位） |
| password | String | 是 | 密码（6-20位） |
| nickname | String | 否 | 昵称 |

**请求示例**：
```json
{
  "phone": "13800138000",
  "password": "123456",
  "nickname": "测试用户"
}
```

**响应示例**：
```json
{
  "code": 200,
  "message": "操作成功",
  "data": {
    "userId": 4,
    "phone": "13800138000",
    "nickname": "测试用户",
    "roles": ["ROLE_USER"],
    "token": "eyJhbGciOiJIUzI1NiJ9..."
  }
}
```

**错误码**：400（手机号已注册）

---

#### 用户登录

```
POST /api/user/login
```

**请求体**：

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| phone | String | 是 | 手机号 |
| password | String | 是 | 密码 |

**请求示例**：
```json
{
  "phone": "13800000001",
  "password": "123456"
}
```

**响应示例**：
```json
{
  "code": 200,
  "message": "操作成功",
  "data": {
    "userId": 1,
    "phone": "13800000001",
    "nickname": "测试用户A",
    "roles": ["ROLE_USER"],
    "token": "eyJhbGciOiJIUzI1NiJ9..."
  }
}
```

**错误码**：400（手机号或密码错误）

**测试账号**：

| 手机号 | 密码 | 角色 |
|--------|------|------|
| 13800000001 | 123456 | 普通用户 |
| 13800000002 | 123456 | 普通用户 |
| 13800000003 | 123456 | 管理员（普通用户 + 运营管理员） |

---

#### 获取当前用户信息

```
GET /api/user/info
```

**请求头**：`Authorization: Bearer <token>`

**响应示例**：
```json
{
  "code": 200,
  "data": {
    "id": 1,
    "phone": "13800000001",
    "nickname": "测试用户A",
    "avatar": null,
    "plateNo": "京A12345",
    "carModel": "特斯拉 Model 3",
    "lastLogin": "2026-06-01 10:00:00"
  }
}
```

---

### 2.2 站点与充电桩查询

#### 获取充电站列表（分页）

```
GET /api/station/list?page=1&size=20&name=&sortBy=&latitude=&longitude=
```

**请求参数**：

| 参数 | 类型 | 必填 | 默认值 | 说明 |
|------|------|------|--------|------|
| page | int | 否 | 1 | 页码 |
| size | int | 否 | 20 | 每页数量 |
| name | String | 否 | - | 站点名称（模糊搜索） |
| sortBy | String | 否 | - | 排序：distance/price/available |
| latitude | BigDecimal | 否 | - | 用户纬度（按距离排序时必传） |
| longitude | BigDecimal | 否 | - | 用户经度（按距离排序时必传） |

**响应示例**：
```json
{
  "code": 200,
  "data": {
    "records": [
      {
        "id": 1,
        "name": "朝阳区新能源充电站",
        "address": "北京市朝阳区建国路88号",
        "longitude": 116.4612345,
        "latitude": 39.9098765,
        "businessHours": "00:00-24:00",
        "contact": "010-88881001",
        "status": 1,
        "distance": null,
        "availableCount": 12,
        "totalCount": 20,
        "minPrice": 0.80
      }
    ],
    "total": 5,
    "page": 1,
    "size": 20
  }
}
```

---

#### 获取充电站详情（含充电桩列表）

```
GET /api/station/{id}
```

**路径参数**：

| 参数 | 类型 | 说明 |
|------|------|------|
| id | Long | 充电站ID |

**响应示例**：
```json
{
  "code": 200,
  "data": {
    "id": 1,
    "name": "朝阳区新能源充电站",
    "address": "北京市朝阳区建国路88号",
    "status": 1,
    "chargers": [
      {
        "id": 1,
        "sn": "CHARGER-001",
        "name": "朝阳-1号桩",
        "power": 7.00,
        "status": 1,
        "statusDesc": "空闲"
      }
    ]
  }
}
```

---

#### 获取充电桩详情

```
GET /api/charger/{id}
```

**路径参数**：

| 参数 | 类型 | 说明 |
|------|------|------|
| id | Long | 充电桩ID |

**响应示例**：
```json
{
  "code": 200,
  "data": {
    "id": 1,
    "sn": "CHARGER-001",
    "name": "朝阳-1号桩",
    "stationId": 1,
    "stationName": "朝阳区新能源充电站",
    "power": 7.00,
    "status": 1,
    "statusDesc": "空闲",
    "currentVoltage": null,
    "currentCurrent": null,
    "currentPower": null,
    "temperature": null
  }
}
```

---

### 2.3 充电控制

#### 扫码启桩

```
POST /api/charge/start
```

**请求体**：

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| chargerId | Long | 是 | 充电桩ID |

**请求示例**：
```json
{
  "chargerId": 1
}
```

**响应示例**：
```json
{
  "code": 200,
  "data": "ORD1734567890123"
}
```

**业务流程**：
1. 验证充电桩状态为 IDLE（空闲）
2. 获取 Redis 分布式锁 `charge:lock:{chargerId}`
3. 更新充电桩状态为 LOCKED（锁定）
4. 创建充电订单（状态：充电中）
5. 通过 MQTT 下发启动充电指令
6. 返回订单编号

**错误码**：409（充电桩非空闲状态）、404（充电桩不存在）

---

#### 结束充电

```
POST /api/charge/stop
```

**请求体**：

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| orderNo | String | 是 | 充电订单编号 |

**请求示例**：
```json
{
  "orderNo": "ORD1734567890123"
}
```

**响应示例**：
```json
{
  "code": 200,
  "data": "充电已结束，总费用: 25.50 元"
}
```

---

#### 获取充电实时状态

```
GET /api/charge/status/{orderId}
```

**路径参数**：

| 参数 | 类型 | 说明 |
|------|------|------|
| orderId | String | 订单编号（orderNo） |

**响应示例**：
```json
{
  "code": 200,
  "data": {
    "orderNo": "ORD1734567890123",
    "chargerSn": "CHARGER-001",
    "status": 1,
    "voltage": 220.50,
    "current": 16.20,
    "power": 3.56,
    "chargedEnergy": 12.50,
    "estimatedCost": 15.80,
    "durationSeconds": 1200,
    "startTime": "2026-06-01 14:30:00"
  }
}
```

---

### 2.4 订单管理

#### 获取订单列表

```
GET /api/order/list?page=1&size=20&orderStatus=&startTime=&endTime=
```

**请求参数**：

| 参数 | 类型 | 必填 | 默认值 | 说明 |
|------|------|------|--------|------|
| page | int | 否 | 1 | 页码 |
| size | int | 否 | 20 | 每页数量 |
| orderStatus | Integer | 否 | - | 订单状态：1-充电中，2-已完成，3-已取消，4-异常 |
| startTime | String | 否 | - | 开始时间 (yyyy-MM-dd HH:mm:ss) |
| endTime | String | 否 | - | 结束时间 (yyyy-MM-dd HH:mm:ss) |

**响应示例**：
```json
{
  "code": 200,
  "data": {
    "records": [
      {
        "id": 1,
        "orderNo": "ORD1734567890001",
        "chargerId": 1,
        "chargerName": "朝阳-1号桩",
        "stationId": 1,
        "stationName": "朝阳区新能源充电站",
        "startTime": "2026-05-25 09:00:00",
        "endTime": "2026-05-25 10:30:00",
        "chargedEnergy": 15.50,
        "totalAmount": 20.15,
        "electricityFee": 12.40,
        "serviceFee": 7.75,
        "payStatus": 1,
        "orderStatus": 2
      }
    ],
    "total": 5,
    "page": 1,
    "size": 20
  }
}
```

---

#### 获取订单详情

```
GET /api/order/{id}
```

**路径参数**：

| 参数 | 类型 | 说明 |
|------|------|------|
| id | Long | 订单ID |

---

#### 支付订单（模拟）

```
POST /api/order/pay
```

**请求体**：

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| orderNo | String | 是 | 订单编号 |

**响应示例**：
```json
{
  "code": 200,
  "data": "支付成功"
}
```

**注意**：仅支持已完成（COMPLETED）且未支付（UNPAID）的订单。

---

#### 申请退款

```
POST /api/order/refund
```

**请求体**：

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| orderNo | String | 是 | 订单编号 |

**响应示例**：
```json
{
  "code": 200,
  "data": "退款成功"
}
```

**注意**：仅支持已支付（PAID）的订单。

---

### 2.5 预约管理

#### 创建预约订单

```
POST /api/reservation/create
```

**请求体**：

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| chargerId | Long | 是 | 充电桩ID |
| reserveDate | String | 是 | 预约日期 (yyyy-MM-dd) |
| startTime | String | 是 | 开始时间 (HH:mm) |
| endTime | String | 是 | 结束时间 (HH:mm) |

**请求示例**：
```json
{
  "chargerId": 1,
  "reserveDate": "2026-06-05",
  "startTime": "14:00",
  "endTime": "16:00"
}
```

**响应示例**：
```json
{
  "code": 200,
  "data": "RES1734567890456"
}
```

---

#### 取消预约

```
POST /api/reservation/cancel
```

**请求体**：

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| orderNo | String | 是 | 预约订单编号 |

**响应示例**：
```json
{
  "code": 200,
  "data": "预约已取消"
}
```

---

## 3. 运营后台接口

> **权限说明**：所有 `/api/admin/**` 接口需要 `ROLE_ADMIN` 角色。测试可用管理员账号 `13800000003/123456`。

### 3.1 充电站管理

#### 分页查询充电站列表

```
GET /api/admin/station/list?page=1&size=20&name=&status=
```

**请求参数**：

| 参数 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| page | int | 1 | 页码 |
| size | int | 20 | 每页数量 |
| name | String | - | 名称（模糊搜索） |
| status | Integer | - | 状态：0-暂停营业，1-营业中，2-维护中 |

---

#### 充电站 CRUD

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/admin/station/{id}` | 查看详情 |
| POST | `/api/admin/station` | 新增 |
| PUT | `/api/admin/station/{id}` | 修改 |
| DELETE | `/api/admin/station/{id}` | 删除（有关联充电桩时不允许删除） |
| PUT | `/api/admin/station/{id}/status?status=1` | 修改营业状态 |

**新增/修改充电站请求体**：
```json
{
  "name": "测试充电站",
  "address": "北京市海淀区测试路1号",
  "longitude": 116.3000000,
  "latitude": 39.9000000,
  "businessHours": "06:00-23:00",
  "contact": "010-12345678",
  "status": 1
}
```

---

### 3.2 充电桩管理

#### 分页查询充电桩列表

```
GET /api/admin/charger/list?page=1&size=20&stationId=&sn=&status=
```

**请求参数**：

| 参数 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| page | int | 1 | 页码 |
| size | int | 20 | 每页数量 |
| stationId | Long | - | 所属充电站ID |
| sn | String | - | 设备SN（模糊搜索） |
| status | Integer | - | 状态 |

---

#### 充电桩 CRUD

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/admin/charger/{id}` | 查看详情 |
| POST | `/api/admin/charger` | 新增 |
| PUT | `/api/admin/charger/{id}` | 修改（SN和所属充电站不可修改） |
| DELETE | `/api/admin/charger/{id}` | 删除（有进行中订单时不允许） |
| PUT | `/api/admin/charger/{id}/status?status=1` | 启用/禁用 |

**新增充电桩请求体**：
```json
{
  "sn": "CHARGER-101",
  "name": "测试-1号桩",
  "stationId": 1,
  "power": 7.00,
  "status": 1
}
```

---

### 3.3 订单管理

#### 全量订单查询

```
GET /api/admin/order/list?page=1&size=20&userId=&chargerId=&stationId=&orderStatus=&payStatus=&startTime=&endTime=
```

---

#### 订单操作

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/admin/order/{id}` | 查看订单详情（不受userId限制） |
| POST | `/api/admin/order/end` | 手动结束异常订单 |
| POST | `/api/admin/order/refund` | 管理员退款 |

**手动结束订单请求体**：
```json
{
  "orderNo": "ORD1734567890004",
  "reason": "设备离线导致订单异常，用户已通过客服确认"
}
```

**管理员退款请求体**：
```json
{
  "orderNo": "ORD1734567890005",
  "reason": "用户投诉，经核实后退款"
}
```

---

### 3.4 计费规则管理

#### 分页查询规则列表

```
GET /api/admin/pricing/list?page=1&size=20&stationId=&status=
```

---

#### 计费规则 CRUD

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/admin/pricing/{id}` | 查看规则详情 |
| POST | `/api/admin/pricing` | 新增规则 |
| PUT | `/api/admin/pricing/{id}` | 修改规则 |
| DELETE | `/api/admin/pricing/{id}` | 删除规则 |
| PUT | `/api/admin/pricing/{id}/status?status=1` | 启用/禁用 |
| GET | `/api/admin/pricing/station/{stationId}` | 获取某站点的所有启用规则 |

**新增计费规则请求体（基础电价）**：
```json
{
  "name": "测试-基础电价",
  "stationId": 1,
  "ruleType": 1,
  "electricityPrice": 0.80,
  "servicePrice": 0.50,
  "priority": 10,
  "status": 1
}
```

**新增计费规则请求体（峰谷电价）**：
```json
{
  "name": "测试-峰谷时段",
  "stationId": 1,
  "ruleType": 2,
  "startTime": "08:00:00",
  "endTime": "12:00:00",
  "electricityPrice": 1.00,
  "servicePrice": 0.60,
  "priority": 10,
  "status": 1
}
```

---

### 3.5 告警管理

#### 分页查询告警列表

```
GET /api/admin/alarm/list?page=1&size=20&chargerId=&stationId=&alarmType=&alarmLevel=&status=&startTime=&endTime=
```

**请求参数**：

| 参数 | 类型 | 说明 |
|------|------|------|
| chargerId | Long | 充电桩ID |
| stationId | Long | 充电站ID |
| alarmType | String | 告警类型：OVER_TEMP/OVER_VOLT/UNDER_VOLT/SHORT_CIRCUIT/LEAKAGE/OFFLINE/COMM_ERROR |
| alarmLevel | Integer | 告警级别：1-一般，2-重要，3-紧急 |
| status | Integer | 处理状态：0-未处理，1-已处理 |
| startTime | String | 开始时间 |
| endTime | String | 结束时间 |

---

#### 告警操作

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/admin/alarm/{id}` | 查看告警详情 |
| POST | `/api/admin/alarm/handle` | 处理告警 |
| GET | `/api/admin/alarm/statistics` | 告警统计（未处理数、类型分布、级别分布） |

**处理告警请求体**：
```json
{
  "alarmId": 2,
  "handleNote": "已派维修人员现场检修，更换散热风扇"
}
```

---

### 3.6 数据统计

| 方法 | 路径 | 参数 | 说明 |
|------|------|------|------|
| GET | `/api/admin/statistics/dashboard` | - | 实时大屏数据（在线率、充电中数、今日订单/营收、未处理告警） |
| GET | `/api/admin/statistics/trend` | `period=day&startDate=2026-05-28&endDate=2026-06-03` | 趋势统计（订单量、充电量、营收趋势） |
| GET | `/api/admin/statistics/station-rank` | `type=order&topN=10` | 站点排名（order/energy/revenue） |
| GET | `/api/admin/statistics/fault` | - | 故障统计（类型分布、故障率） |

**Dashboard 响应示例**：
```json
{
  "code": 200,
  "data": {
    "onlineCount": 75,
    "totalCount": 100,
    "onlineRate": 75.0,
    "chargingCount": 10,
    "todayOrderCount": 48,
    "todayRevenue": 1250.50,
    "unhandledAlarmCount": 2
  }
}
```

---

## 4. WebSocket 推送

### 4.1 充电进度推送

```
ws://localhost:9090/charge/{orderId}
```

**连接时机**：启桩成功后建立连接

**推送消息格式**：
```json
{
  "type": "CHARGE_PROGRESS",
  "data": {
    "orderId": 1,
    "orderNo": "ORD1734567890123",
    "voltage": 220.50,
    "current": 16.20,
    "power": 3.56,
    "chargedEnergy": 12.50,
    "currentCost": 15.80,
    "duration": 1200,
    "chargerStatus": 2
  },
  "timestamp": 1717238400000
}
```

**推送频率**：设备每 5 秒上报数据时触发推送

### 4.2 运营大屏推送

```
ws://localhost:9090/dashboard
```

**连接时机**：运营后台打开大屏页面时连接

**推送消息格式**：
```json
{
  "type": "DASHBOARD_UPDATE",
  "data": {
    "onlineCount": 75,
    "totalCount": 100,
    "onlineRate": 75.0,
    "chargingCount": 10,
    "todayOrderCount": 48,
    "todayRevenue": 1250.50,
    "unhandledAlarmCount": 2
  },
  "timestamp": 1717238400000
}
```

**推送频率**：后台每 10 秒定时推送（`DashboardPushScheduler`）

### 4.3 告警推送

运营后台连接 `/dashboard` WebSocket 后，新告警产生时实时推送：

```json
{
  "type": "ALARM_NOTIFY",
  "data": {
    "alarmId": 4,
    "chargerId": 16,
    "chargerName": "朝阳-16号桩",
    "alarmType": "OVER_TEMP",
    "alarmLevel": 3,
    "content": "设备温度超过安全阈值，当前温度85℃",
    "timestamp": 1717238400000
  }
}
```

---

## 5. MQTT 设备端主题

### 5.1 设备上报主题（设备 → 平台）

| 主题 | QoS | 频率 | 说明 | 报文格式 |
|------|-----|------|------|----------|
| `device/heartbeat` | 0 | 30秒 | 心跳上报 | 空 payload |
| `device/status` | 1 | 状态变更时 | 状态上报 | `{"status":1,"data":{...}}` |
| `device/data` | 0 | 充电中5秒 | 实时数据 | `{"voltage":220.5,"current":16.2,"power":3.56,"energy":12.5,"temperature":42}` |
| `device/alarm` | 1 | 故障触发时 | 故障上报 | `{"alarmType":"OVER_TEMP","alarmLevel":3,"content":"温度过高85℃"}` |
| `device/command/response` | 1 | 指令后 | 指令响应 | `{"command":"START_CHARGE","result":"SUCCESS","message":"已启动"}` |

### 5.2 平台下发主题（平台 → 设备）

| 主题 | QoS | 说明 |
|------|-----|------|
| `device/command/{sn}` | 1 | 远程控制指令下发 |

**指令报文格式**：
```json
{
  "command": "START_CHARGE",
  "params": {
    "maxDuration": 3600
  },
  "timestamp": 1717238400000
}
```

**指令类型**：`START_CHARGE`（启动充电）、`STOP_CHARGE`（停止充电）、`RESTART`（重启设备）、`SET_PARAM`（设置参数）

### 5.3 设备鉴权

- CONNECT 报文：username = 设备 SN，password = 设备密钥（SN 后 6 位）
- 示例：SN=CHARGER-001，密钥=R-001

---

## 6. 错误码说明

| Code | 说明 | 常见场景 |
|------|------|----------|
| 200 | 成功 | - |
| 400 | 请求参数错误 | 必填字段缺失、手机号格式错误、密码长度不足 |
| 401 | 未认证 | Token 缺失、过期、无效 |
| 403 | 无权限 | 普通用户访问管理端接口 |
| 404 | 资源不存在 | 充电站/充电桩/订单不存在 |
| 409 | 资源冲突 | 充电桩非空闲、预约时段冲突、告警已处理 |
| 429 | 请求过于频繁 | 触发接口限流 |
| 500 | 服务器内部错误 | 系统异常 |
