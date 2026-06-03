import http from '@/api/request'

export function getOrderList(params: Record<string, any>) {
  return http.get('/admin/order/list', { params })
}

export function getOrderDetail(id: number) {
  return http.get(`/admin/order/${id}`)
}

export function forceEndOrder(orderNo: string, reason: string) {
  return http.post('/admin/order/end', { orderNo, reason })
}

export function adminRefund(orderNo: string, reason: string) {
  return http.post('/admin/order/refund', { orderNo, reason })
}
