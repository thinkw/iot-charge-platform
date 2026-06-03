import { http } from './request'

/** 创建预约 */
export function createReservation(data: {
  chargerId: number
  reserveDate: string
  startTime: string
  endTime: string
}) {
  return http.post<any>('/reservation/create', data)
}

/** 取消预约 */
export function cancelReservation(orderNo: string) {
  return http.post<any>('/reservation/cancel', { orderNo })
}
