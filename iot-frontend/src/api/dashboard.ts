import http from '@/api/request'

/**
 * 大屏数据 API
 * <p>
 * 复用共享 http 实例，统一 JWT 注入和错误处理。
 * 路径前缀 /admin/statistics 匹配后端运营统计接口。
 * 字段名与 iot-core dto/response 严格对齐。
 * </p>
 */

/** 仪表盘数据 — 对应后端 DashboardVO */
export interface DashboardData {
  /** 当前在线设备数 */
  onlineDeviceCount: number
  /** 总设备数 */
  totalDeviceCount: number
  /** 设备在线率（百分比，如 85.5） */
  onlineRate: number
  /** 当前充电中的设备数 */
  chargingCount: number
  /** 今日订单总数 */
  todayOrderCount: number
  /** 今日营收总额（元） */
  todayRevenue: number
  /** 未处理告警数 */
  unhandledAlarmCount: number
}

/** 趋势点 — 对应后端 TrendVO.TrendPoint */
export interface TrendPoint {
  /** 日期（格式：yyyy-MM-dd） */
  date: string
  /** 整数值（订单数） */
  value: number
  /** 小数值（充电量、营收） */
  decimalValue: number
}

/** 趋势数据 — 对应后端 TrendVO */
export interface TrendData {
  orderTrend: TrendPoint[]
  energyTrend: TrendPoint[]
  revenueTrend: TrendPoint[]
}

/** 站点排名 — 对应后端 StationRankVO */
export interface StationRank {
  stationId: number
  stationName: string
  orderCount: number
  totalEnergy: number
  totalRevenue: number
}

/** 故障统计 — 对应后端 FaultStatisticsVO（faultCountByType 为 Map） */
export interface FaultStatistics {
  faultCountByType: Record<string, number>
  faultRate: number
  totalFaultCount: number
}

export function fetchDashboard(): Promise<DashboardData> {
  return http.get('/admin/statistics/dashboard')
}

export function fetchTrend(period: string, startDate?: string, endDate?: string): Promise<TrendData> {
  const params: Record<string, any> = { period }
  if (startDate) params.startDate = startDate
  if (endDate) params.endDate = endDate
  return http.get('/admin/statistics/trend', { params })
}

export function fetchStationRank(type: string = 'order', topN: number = 10): Promise<StationRank[]> {
  return http.get('/admin/statistics/station-rank', { params: { type, topN } })
}

export function fetchFaultStatistics(): Promise<FaultStatistics> {
  return http.get('/admin/statistics/fault')
}
