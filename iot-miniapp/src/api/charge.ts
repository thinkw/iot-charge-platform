import { http } from './request'

/** 扫码启桩 */
export function startCharge(chargerId: number) {
  return http.post<any>('/charge/start', { chargerId })
}

/** 结束充电 */
export function stopCharge(orderNo: string) {
  return http.post<any>('/charge/stop', { orderNo })
}

/** 获取充电实时状态 */
export function getChargeStatus(orderNo: string) {
  return http.get<any>(`/charge/status/${orderNo}`)
}
