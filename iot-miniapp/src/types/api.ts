/**
 * 通用 API 类型定义
 * <p>
 * 与后端 iot-api 模块的 VO/BO 字段对齐。
 * 命名规则：xxxVO 对应后端返回 VO，xxxBO 对应后端接收参数。
 * 字段类型按 JSON 序列化后的实际值定义。
 * </p>
 */

/** ========== 通用 ========== */

/** 统一响应包装 */
export interface Result<T> {
  code: number
  message: string
  data: T
}

/** ========== 用户模块 ========== */

export interface UserVO {
  userId: number
  phone: string
  nickname?: string
  avatar?: string
  balance?: number
}

export interface LoginBO {
  phone: string
  password: string
}

export interface LoginVO {
  token: string
  user: UserVO
}

/** ========== 充电站/桩 ========== */

export interface StationVO {
  id: number
  name: string
  address: string
  longitude?: number
  latitude?: number
  distance?: number
  totalChargers: number
  availableChargers: number
  status?: number
}

export interface ChargerVO {
  id: number
  stationId: number
  stationName?: string
  name: string
  power: number          // 额定功率 kW
  status: number         // 见 DEVICE_STATUS_MAP
  pricePerKwh?: number
}

/** ========== 订单/充电 ========== */

export interface OrderVO {
  orderNo: string
  userId: number
  chargerId: number
  chargerName?: string
  stationId?: number
  stationName?: string
  orderStatus: number    // 见 ORDER_STATUS_MAP
  payStatus: number      // 见 PAY_STATUS_MAP
  chargedEnergy: number  // kWh
  totalAmount: number    // 元
  startTime: string
  endTime?: string
  duration?: number
}

/** 后端 CHARGE_PROGRESS WebSocket 推送 data 字段 */
export interface ChargeProgressWsData {
  orderNo: string
  chargedEnergy: number
  currentPower: number
  estimatedAmount: number
  durationSeconds: number
  voltage?: number
  current?: number
}

/** 后端 COMMAND_STATUS WebSocket 推送 data 字段 */
export interface CommandStatusWsData {
  orderNo: string
  status: 'PENDING' | 'SUCCESS' | 'FAILED' | 'TIMEOUT' | 'CANCELLED'
  message: string
}

/** 后端 CHARGE_START / CHARGE_STOP 推送 data 字段 */
export interface ChargeStartWsData {
  orderNo: string
  chargerId?: number
  message: string
}
export interface ChargeStopWsData {
  orderNo: string
  totalAmount: number
  message: string
}

/** 充电 REST 兜底（与 CHARGE_PROGRESS schema 一致） */
export interface ChargeStatusVO {
  orderNo: string
  chargedEnergy: number
  currentPower: number
  estimatedAmount: number
  durationSeconds: number
}

/** ========== 预约 ========== */

export interface ReservationBO {
  chargerId: number
  reserveTime: string  // ISO 8601
  duration: number     // 分钟
}

export interface ReservationVO {
  id: number
  orderNo: string
  chargerId: number
  chargerName?: string
  reserveTime: string
  duration: number
  status: number
}
