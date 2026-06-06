import { http } from './request'
import type { OrderVO } from '@/types/api'

/**
 * 订单列表查询
 * @param params.status 订单状态（可选）：1 充电中 2 已完成 3 已取消 4 异常 5 待确认
 * @param params.page   页码（可选，默认 1）
 * @param params.size   每页大小（可选，默认 10）
 */
export function listOrdersApi(params: {
  status?: number
  page?: number
  size?: number
} = {}) {
  return http.get<OrderVO[]>('/order/list', params)
}

/** 订单详情（通过订单编号查询） */
export function getOrderApi(orderNo: string) {
  return http.get<OrderVO>(`/order/by-no/${orderNo}`)
}

/** 支付订单 */
export function payOrder(orderNo: string) {
  return http.post<void>('/order/pay', { orderNo })
}

/** 申请退款 */
export function refundOrder(orderNo: string) {
  return http.post<void>('/order/refund', { orderNo })
}
