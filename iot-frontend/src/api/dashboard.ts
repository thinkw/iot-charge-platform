/**
 * 大屏数据 API 调用层
 * <p>
 * 封装对后端统计接口的 HTTP 请求，自动解包 Result.data。
 * 后端返回格式: { code: 200, message: "操作成功", data: {...}, timestamp: ... }
 * 自动注入 JWT Token（从 localStorage 读取）。
 * </p>
 */
import axios from 'axios'
import { getToken, clearAuth } from '@/utils/auth'

// 创建 axios 实例
const http = axios.create({
  baseURL: '/api/admin',
  timeout: 10000,
  headers: { 'Content-Type': 'application/json' }
})

// 请求拦截器：注入 JWT Token
http.interceptors.request.use(
  (config) => {
    const token = getToken()
    if (token) {
      config.headers.Authorization = `Bearer ${token}`
    }
    return config
  },
  (error) => Promise.reject(error)
)

// 响应拦截器：解包 Result.data + 处理 401 未授权
http.interceptors.response.use(
  (response) => {
    const body = response.data
    // 后端统一返回 Result<T> 格式
    if (body && typeof body.code === 'number') {
      if (body.code === 200) {
        return body.data
      }
      // 401 表示 Token 过期或无效，清除凭证
      if (body.code === 401) {
        clearAuth()
        window.location.href = '/login'
      }
      return Promise.reject(new Error(body.message || '请求失败'))
    }
    return body
  },
  (error) => {
    // HTTP 401 → Token 过期或无效
    if (error.response?.status === 401) {
      clearAuth()
      window.location.href = '/login'
    }
    return Promise.reject(error)
  }
)

// ==================== 类型定义 ====================

/** 大屏仪表盘数据 */
export interface DashboardData {
  onlineDeviceCount: number
  totalDeviceCount: number
  onlineRate: number
  chargingCount: number
  todayOrderCount: number
  todayRevenue: number
  unhandledAlarmCount: number
}

/** 趋势数据点 */
export interface TrendPoint {
  date: string
  value: number
  decimalValue: number
}

/** 趋势数据 */
export interface TrendData {
  orderTrend: TrendPoint[]
  energyTrend: TrendPoint[]
  revenueTrend: TrendPoint[]
}

/** 站点排名 */
export interface StationRank {
  stationId: number
  stationName: string
  orderCount: number
  totalEnergy: number
  totalRevenue: number
}

/** 故障统计 */
export interface FaultStatistics {
  faultCountByType: Record<string, number>
  faultRate: number
  totalFaultCount: number
}

// ==================== API 方法 ====================

/** 获取实时大屏仪表盘数据 */
export function fetchDashboard(): Promise<DashboardData> {
  return http.get('/statistics/dashboard')
}

/** 获取趋势统计数据 */
export function fetchTrend(
  period: 'day' | 'week' | 'month' = 'day',
  startDate?: string,
  endDate?: string
): Promise<TrendData> {
  return http.get('/statistics/trend', { params: { period, startDate, endDate } })
}

/** 获取站点排名 */
export function fetchStationRank(
  type: 'order' | 'energy' | 'revenue' = 'order',
  topN: number = 10
): Promise<StationRank[]> {
  return http.get('/statistics/station-rank', { params: { type, topN } })
}

/** 获取故障统计 */
export function fetchFaultStatistics(): Promise<FaultStatistics> {
  return http.get('/statistics/fault')
}
