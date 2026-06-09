/**
 * 大屏 WebSocket 共享状态 Store
 * <p>
 * useWebSocket 内部管理 WS 连接生命周期，但 wsConnected 需要跨组件共享
 * （DashboardView 内部使用 + AdminLayout 顶栏展示），故提到 Pinia store。
 * </p>
 */
import { defineStore } from 'pinia'
import { ref, watch } from 'vue'
import { useWebSocket, type WsMessage } from '@/composables/useWebSocket'
import { useAuthStore } from '@/stores/authStore'
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

/**
 * 拼装大屏 WS URL。
 * - 优先读取 VITE_WS_DASHBOARD_URL；若包含 {{userId}} 占位则替换，否则追加 ?userId=xxx
 * - 未配置时按 VITE_API_BASE_URL 推断 ws://host:port/ws/charge
 */
function buildDashboardWsUrl(userId: number | string): string {
  const tmpl = import.meta.env.VITE_WS_DASHBOARD_URL as string | undefined
  let base: string
  if (tmpl) {
    base = tmpl
  } else {
    const apiBase = (import.meta.env.VITE_API_BASE_URL as string | undefined) || ''
    const protocol = apiBase.startsWith('https') ? 'wss:' : 'ws:'
    const host = apiBase.replace(/^https?:\/\//, '').replace(/\/.*$/, '')
    // 兜底：环境变量缺失时使用默认 localhost 地址
    base = host ? `${protocol}//${host}/ws/charge` : 'ws://localhost:8080/ws/charge'
  }
  if (base.includes('{{userId}}')) {
    return base.replace(/\{\{userId\}\}/g, String(userId))
  }
  return base.includes('userId=') ? base : `${base}${base.includes('?') ? '&' : '?'}userId=${userId}`
}

export const useDashboardStore = defineStore('dashboard', () => {
  // ==================== 指标数据 ====================
  const dashboard = ref<DashboardData>({
    onlineDeviceCount: 0,
    totalDeviceCount: 0,
    onlineRate: 0,
    chargingCount: 0,
    todayOrderCount: 0,
    todayRevenue: 0,
    unhandledAlarmCount: 0
  })

  const trend = ref<TrendData | null>(null)
  const trendLoading = ref(false)
  const stationRanks = ref<StationRank[]>([])
  const ranksLoading = ref(false)
  const faultStats = ref<FaultStatistics | null>(null)
  const faultLoading = ref(false)

  // ==================== WebSocket ====================
  const wsConnected = ref(false)
  let ws: ReturnType<typeof useWebSocket> | null = null

  // ==================== 数据加载 ====================
  async function loadDashboard() {
    try {
      dashboard.value = await fetchDashboard()
    } catch (e) {
      console.error('[Dashboard] 加载仪表盘数据失败:', e)
    }
  }

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

  async function loadStationRank(type: 'order' | 'energy' | 'revenue' = 'order', topN = 10) {
    ranksLoading.value = true
    try {
      stationRanks.value = await fetchStationRank(type, topN)
    } catch (e) {
      console.error('[Dashboard] 加载站点排名失败:', e)
    } finally {
      ranksLoading.value = false
    }
  }

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

  // ==================== 初始化（幂等，重复调用安全） ====================
  let initialized = false

  function init() {
    if (initialized) return

    const authStore = useAuthStore()
    if (!authStore.isAdmin) {
      console.info('[Dashboard] 当前非管理员，跳过大屏 WS 连接')
      return
    }
    const userId = authStore.user?.userId
    if (!userId) {
      console.warn('[Dashboard] 管理员 userId 缺失，跳过 WS 连接')
      return
    }

    const url = buildDashboardWsUrl(userId)
    ws = useWebSocket(url)

    watch(ws.connected, (v) => { wsConnected.value = v }, { immediate: true })

    ws.onMessage('DASHBOARD_UPDATE', (msg: WsMessage) => {
      if (msg.data) dashboard.value = { ...dashboard.value, ...msg.data }
    })
    ws.onMessage('ALARM', () => loadFaultStats())

    // 重连后 HTTP 兜底：跳过首次连接事件，仅在断线重连时触发全量拉取，
    // 消除 WS 断线期间因错过 DASHBOARD_UPDATE 推送造成的数据空窗
    let initialWsConnection = true
    watch(ws.connected, (v) => {
      if (v) {
        if (initialWsConnection) {
          initialWsConnection = false
          return
        }
        console.log('[Dashboard] WS 重连成功，HTTP 兜底刷新仪表盘')
        loadDashboard()
      }
    })

    initialized = true

    // 首次加载
    loadDashboard()
    loadTrend('day')
    loadStationRank('order', 10)
    loadFaultStats()

    // 建立 WS
    ws.connect()
  }

  function destroy() {
    if (!initialized) return
    initialized = false
    ws?.disconnect()
    ws = null
    wsConnected.value = false
  }

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
    init,
    destroy,
    loadDashboard,
    loadTrend,
    loadStationRank,
    loadFaultStats
  }
})
