import { http } from './request'

/** 订单列表 */
export function getOrderList(params: Record<string, any>) {
  return http.get<any>('/order/list', params)
}

/** 订单详情（通过订单号，避免雪花ID精度丢失） */
export function getOrderDetailByNo(orderNo: string) {
  return http.get<any>(`/order/by-no/${orderNo}`)
}

/** 订单详情（通过数字ID，仅内部使用） */
export function getOrderDetail(id: number) {
  return http.get<any>(`/order/${id}`)
}

/** 支付 */
export function payOrder(orderNo: string) {
  return http.post<any>('/order/pay', { orderNo })
}

/** 退款 */
export function refundOrder(orderNo: string) {
  return http.post<any>('/order/refund', { orderNo })
}
