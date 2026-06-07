/** 设备状态映射 */
export const DEVICE_STATUS_MAP: Record<number, string> = {
  0: '离线', 1: '空闲', 2: '充电中', 3: '故障', 4: '锁定'
}

/** 订单状态映射 */
export const ORDER_STATUS_MAP: Record<number, string> = {
  0: '待支付', 1: '充电中', 2: '已完成', 3: '已取消', 4: '异常', 5: '待支付', 6: '等待设备'
}

/** 支付状态映射 */
export const PAY_STATUS_MAP: Record<number, string> = {
  0: '未支付', 1: '已支付', 2: '已退款'
}

/**
 * WebSocket 地址（由 Vite 编译时从 .env 注入，回退 localhost:8080）
 * 需与后端 HTTP 端口一致
 */
export const WS_BASE_URL = import.meta.env.VITE_WS_BASE_URL || 'ws://localhost:8080'
