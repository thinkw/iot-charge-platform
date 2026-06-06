import { http } from './request'
import type { ReservationBO, ReservationVO } from '@/types/api'

/** 创建预约 */
export function createReservationApi(payload: ReservationBO) {
  return http.post<ReservationVO>('/reservation/create', payload)
}

/** 取消预约 */
export function cancelReservationApi(orderNo: string) {
  return http.post<void>('/reservation/cancel', { orderNo })
}

/** 我的预约列表 */
export function listReservationsApi() {
  return http.get<ReservationVO[]>('/reservation/list')
}
