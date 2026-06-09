/**
 * WebSocket 连接管理 composable
 * <p>
 * 管理与后端 WebSocket 服务的连接，自动重连，提供消息分发能力。
 * WebSocket 地址：ws://localhost:8080/ws/charge?userId={userId}
 * </p>
 */
import { ref } from 'vue'

/** WebSocket 推送消息结构 */
export interface WsMessage {
  type: string
  data: any
  timestamp: number
}

/** 消息处理器 */
type MessageHandler = (msg: WsMessage) => void

export function useWebSocket(userIdOrUrl: number | string = 0) {
  const connected = ref(false)
  const lastMessage = ref<WsMessage | null>(null)

  let ws: WebSocket | null = null
  let reconnectTimer: ReturnType<typeof setTimeout> | null = null
  /** 重连尝试次数，每次成功连接后重置 */
  let reconnectAttempt = 0
  let heartbeatTimer: ReturnType<typeof setInterval> | null = null
  const handlers = new Map<string, Set<MessageHandler>>()

  /** 建立 WebSocket 连接，直连后端 8080 端口（不经过 Vite 代理） */
  function connect() {
    if (ws && (ws.readyState === WebSocket.OPEN || ws.readyState === WebSocket.CONNECTING)) {
      return
    }

    const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:'
    // userIdOrUrl 为字符串时作为完整 WebSocket URL，为数字时使用默认充电推送地址
    const url = typeof userIdOrUrl === 'string'
      ? userIdOrUrl
      : `${protocol}//localhost:8080/ws/charge?userId=${userIdOrUrl}`

    try {
      ws = new WebSocket(url)

      ws.onopen = () => {
        reconnectAttempt = 0
        connected.value = true
        console.log('[WS] 连接成功')
        startHeartbeat()
      }

      ws.onmessage = (event) => {
        try {
          const msg: WsMessage = JSON.parse(event.data)
          lastMessage.value = msg
          // 分发给对应类型的处理器
          const typeHandlers = handlers.get(msg.type)
          if (typeHandlers) {
            typeHandlers.forEach((fn) => fn(msg))
          }
          // 也分发给 '*' 通配处理器
          const allHandlers = handlers.get('*')
          if (allHandlers) {
            allHandlers.forEach((fn) => fn(msg))
          }
        } catch (e) {
          console.warn('[WS] 消息解析失败:', event.data)
        }
      }

      ws.onclose = (event) => {
        connected.value = false
        console.log(`[WS] 连接关闭 (code: ${event.code})`)
        stopHeartbeat()
        scheduleReconnect()
      }

      ws.onerror = (error) => {
        console.error('[WS] 连接错误:', error)
        ws?.close()
      }
    } catch (e) {
      console.error('[WS] 创建连接失败:', e)
      scheduleReconnect()
    }
  }

  /** 断开连接 */
  function disconnect() {
    if (reconnectTimer) {
      clearTimeout(reconnectTimer)
      reconnectTimer = null
    }
    stopHeartbeat()
    if (ws) {
      ws.onclose = null // 阻止自动重连
      ws.close()
      ws = null
    }
    connected.value = false
  }

  /** 注册消息处理器 */
  function onMessage(type: string, handler: MessageHandler) {
    if (!handlers.has(type)) {
      handlers.set(type, new Set())
    }
    handlers.get(type)!.add(handler)
  }

  /** 移除消息处理器 */
  function offMessage(type: string, handler: MessageHandler) {
    const set = handlers.get(type)
    if (set) {
      set.delete(handler)
    }
  }

  /** 发送消息 */
  function send(data: string | object) {
    if (ws && ws.readyState === WebSocket.OPEN) {
      ws.send(typeof data === 'string' ? data : JSON.stringify(data))
    }
  }

  /** 心跳保活：每 30 秒发 JSON 格式 PING */
  function startHeartbeat() {
    heartbeatTimer = setInterval(() => {
      send({ type: 'PING' })
    }, 30000)
  }

  function stopHeartbeat() {
    if (heartbeatTimer) {
      clearInterval(heartbeatTimer)
      heartbeatTimer = null
    }
  }

  /**
   * 断线重连（指数退避 + 随机抖动）
   * <p>
   * 重连间隔：min(3000 × 2^attempt, 30000)ms，叠加 ±500ms 随机抖动。
   * 防止服务端重启时所有客户端同时重连导致惊群效应。
   * </p>
   */
  function scheduleReconnect() {
    if (reconnectTimer) return
    const base = 3000
    const max = 30000
    const delay = Math.min(base * Math.pow(2, reconnectAttempt), max)
    // 随机抖动 ±500ms，防止惊群效应
    const jitter = (Math.random() - 0.5) * 1000
    const actualDelay = Math.max(1000, Math.round(delay + jitter))

    reconnectTimer = setTimeout(() => {
      reconnectAttempt++
      reconnectTimer = null
      console.log(`[WS] 尝试重连...(第${reconnectAttempt}次, 间隔${Math.round(actualDelay / 1000)}s)`)
      connect()
    }, actualDelay)
  }

  // 组件卸载时自动断开 — 由调用方在 onUnmounted 中显式调用 disconnect()，
  // 避免 composable 内部生命周期钩子与调用方重复注册。

  return {
    connected,
    lastMessage,
    connect,
    disconnect,
    onMessage,
    offMessage,
    send
  }
}
