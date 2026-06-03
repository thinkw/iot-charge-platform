import http from '@/api/request'

/**
 * 大屏数据 API
 * <p>
 * 复用共享 http 实例，统一 JWT 注入和错误处理。
 * 路径前缀 /admin/statistics 匹配后端运营统计接口。
 * </p>
 */

/** 仪表盘数据 */
export interface DashboardData {
  onlineCount: number
  totalCount: number
  onlineRate: number
  chargingCount: number
  todayOrderCount: number
  todayRevenue: number
  unhandledAlarmCount: number
}

/** 趋势数据 */
export interface TrendData {
  orderTrend: { date: string; count: number }[]
  energyTrend: { date: string; energy: number }[]
  revenueTrend: { date: string; revenue: number }[]
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
  faultCountByType: { type: string; count: number }[]
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
  return http.get('/admin/statistics/trend', params)
}

export function fetchStationRank(type: string = 'order', topN: number = 10): Promise<StationRank[]> {
  return http.get('/admin/statistics/station-rank', { type, topN })
}

export function fetchFaultStatistics(): Promise<FaultStatistics> {
  return http.get('/admin/statistics/fault')
}
