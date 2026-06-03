/**
 * 大屏数据管理 composable
 * <p>
 * 封装大屏数据的获取和状态管理逻辑。
 * 指标卡片通过 WebSocket 实时更新，图表数据通过 HTTP 请求获取。
 * </p>
 */
import { ref, reactive, onMounted, watch } from 'vue'
import {
  fetchDashboard,
  fetchTrend,
  fetchStationRank,
  fetchFaultStatistics,
  type DashboardData,
  type TrendData,
  type StationRank,
  type FaultStatistics
} from '@/api/dashboard'
import { useWebSocket, type WsMessage } from './useWebSocket'

export function useDashboard() {
  // ==================== 状态 ====================

  /** 仪表盘指标数据 */
  const dashboard = reactive<DashboardData>({
    onlineDeviceCount: 0,
    totalDeviceCount: 0,
    onlineRate: 0,
    chargingCount: 0,
    todayOrderCount: 0,
    todayRevenue: 0,
    unhandledAlarmCount: 0
  })

  /** 趋势数据 */
  const trend = ref<TrendData | null>(null)
  const trendLoading = ref(false)

  /** 站点排名 */
  const stationRanks = ref<StationRank[]>([])
  const ranksLoading = ref(false)

  /** 故障统计 */
  const faultStats = ref<FaultStatistics | null>(null)
  const faultLoading = ref(false)

  /** WebSocket — 连接 Dashboard 推送（支持环境变量 VITE_WS_DASHBOARD_URL 配置） */
  const dashboardWsUrl = import.meta.env.VITE_WS_DASHBOARD_URL
    || `${window.location.protocol === 'https:' ? 'wss:' : 'ws:'}//localhost:9090/dashboard`
  const { connected: wsConnected, onMessage, connect: wsConnect } = useWebSocket(dashboardWsUrl)

  // ==================== 数据加载方法 ====================

  /** 加载仪表盘数据（HTTP），用于初始加载 */
  async function loadDashboard() {
    try {
      const data = await fetchDashboard()
      Object.assign(dashboard, data)
    } catch (e) {
      console.error('[Dashboard] 加载仪表盘数据失败:', e)
    }
  }

  /** 加载趋势数据 */
  async function loadTrend(period: 'day' | 'week' | 'month' = 'day') {
    trendLoading.value = true
    try {
      trend.value = await fetchTrend(period)
    } catch (e) {
      console.error('[Dashboard] 加载趋势数据失败:', e)
    } finally {
      trendLoading.value = false
    }
  }

  /** 加载站点排名 */
  async function loadStationRank(type: 'order' | 'energy' | 'revenue' = 'order', topN: number = 10) {
    ranksLoading.value = true
    try {
      stationRanks.value = await fetchStationRank(type, topN)
    } catch (e) {
      console.error('[Dashboard] 加载站点排名失败:', e)
    } finally {
      ranksLoading.value = false
    }
  }

  /** 加载故障统计 */
  async function loadFaultStats() {
    faultLoading.value = true
    try {
      faultStats.value = await fetchFaultStatistics()
    } catch (e) {
      console.error('[Dashboard] 加载故障统计失败:', e)
    } finally {
      faultLoading.value = false
    }
  }

  // ==================== WebSocket 实时更新 ====================

  /** 监听 DASHBOARD_UPDATE 推送，实时更新指标卡片 */
  onMessage('DASHBOARD_UPDATE', (msg: WsMessage) => {
    if (msg.data) {
      Object.assign(dashboard, msg.data)
    }
  })

  /** 监听 ALARM 推送，有新增告警时刷新故障统计 */
  onMessage('ALARM', () => {
    loadFaultStats()
  })

  // ==================== 初始化 ====================

  onMounted(() => {
    // 加载初始数据
    loadDashboard()
    loadTrend('day')
    loadStationRank('order', 10)
    loadFaultStats()
    // 建立 WebSocket 连接，接收实时推送
    wsConnect()
  })

  return {
    // 状态
    dashboard,
    trend,
    trendLoading,
    stationRanks,
    ranksLoading,
    faultStats,
    faultLoading,
    wsConnected,
    // 方法
    loadTrend,
    loadStationRank,
    loadFaultStats
  }
}
