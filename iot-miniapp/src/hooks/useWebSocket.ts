import { ref } from 'vue'
import { WS_BASE_URL } from '@/utils/constants'

export interface WsMessage {
  type: string
  data: any
  timestamp: number
}

type MessageHandler = (msg: WsMessage) => void

/**
 * WebSocket Hook（uni-app 适配版）
 * <p>
 * 使用 uni.connectSocket API，支持自动重连和消息分发。
 * 页面 onHide 时自动断开，onShow 时自动重连（由调用方管理）。
 * </p>
 *
 * @param path WebSocket 路径（如 /charge/ORD123）
 */
export function useWebSocket(path: string) {
  const connected = ref(false)
  const lastMessage = ref<WsMessage | null>(null)

  let socketTask: UniApp.SocketTask | null = null
  let reconnectTimer: ReturnType<typeof setTimeout> | null = null
  /** 重连尝试次数，每次成功连接后重置 */
  let reconnectAttempt = 0
  let manualDisconnect = false
  const handlers = new Map<string, Set<MessageHandler>>()

  /** 建立连接 */
  function connect() {
    if (socketTask || manualDisconnect) return

    const url = `${WS_BASE_URL}${path}`
    socketTask = uni.connectSocket({ url, complete: () => {} })

    socketTask.onOpen(() => {
      reconnectAttempt = 0
      connected.value = true
      console.log('[WS] 连接成功:', url)
    })

    socketTask.onMessage((res) => {
      try {
        const msg: WsMessage = JSON.parse(res.data as string)
        lastMessage.value = msg
        const typeHandlers = handlers.get(msg.type)
        if (typeHandlers) typeHandlers.forEach(fn => fn(msg))
        const allHandlers = handlers.get('*')
        if (allHandlers) allHandlers.forEach(fn => fn(msg))
      } catch { /* JSON 解析失败，忽略 */ }
    })

    socketTask.onClose(() => {
      connected.value = false
      socketTask = null
      // 非主动断开时自动重连（指数退避 + 随机抖动）
      if (!manualDisconnect) {
        scheduleReconnect()
      }
    })

    socketTask.onError((err) => {
      console.error('[WS] 连接错误:', err)
      // 先保存引用再置空，防止 onClose 回调中重复触发重连
      const task = socketTask
      socketTask = null
      // 静默关闭：传入 fail 回调避免 closeSocket:fail task not found 报错
      task?.close({
        fail: (e) => console.log('[WS] close silently:', e.errMsg)
      })
    })
  }

  /** 断开连接（主动调用，不自动重连） */
  function disconnect() {
    manualDisconnect = true
    if (reconnectTimer) {
      clearTimeout(reconnectTimer)
      reconnectTimer = null
    }
    if (socketTask) {
      const task = socketTask
      socketTask = null
      connected.value = false
      task.close({
        fail: (e) => console.log('[WS] close silently:', e.errMsg)
      })
    } else {
      connected.value = false
    }
  }

  /**
   * 断线重连调度（指数退避 + 随机抖动）
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

  /** 注册消息处理器 */
  function onMessage(type: string, handler: MessageHandler) {
    if (!handlers.has(type)) handlers.set(type, new Set())
    handlers.get(type)!.add(handler)
  }

  /** 发送消息 */
  function send(data: object) {
    if (socketTask) {
      socketTask.send({ data: JSON.stringify(data) })
    }
  }

  /**
   * 注意：不在 hook 内注册 onUnmounted(disconnect)。
   * 原因：hook 在 setup 阶段执行时注册 onUnmounted 生命周期，
   * 若调用方（如 charge-monitor）也手动管理 disconnect 会出现双重注册。
   * 改由调用方在合适时机显式调用 disconnect() 即可。
   * 典型场景：onUnmounted(() => disconnect()) / handleStop() 中 disconnect()。
   */

  return { connected, lastMessage, connect, disconnect, onMessage, send }
}
