import { http } from './request'
import type { ChargeStatusVO } from '@/types/api'

/** 扫码启桩（创建订单 + 下发启动指令） */
export function startCharge(chargerId: number) {
  return http.post<{ orderNo: string }>('/charge/start', { chargerId })
}

/** 结束充电（前端主动 / 后台异步补偿） */
export function stopCharge(orderNo: string) {
  return http.post<void>('/charge/stop', { orderNo })
}

/**
 * 获取充电实时状态（REST 兜底）
 * <p>
 * 当前主要依赖 WebSocket CHARGE_PROGRESS 推送实时数据。
 * 本接口保留作为 WS 断线重连时的数据补偿入口。
 * </p>
 */
export function getChargeStatus(orderNo: string) {
  return http.get<ChargeStatusVO>(`/charge/status/${orderNo}`)
}
