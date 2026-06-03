import http from '@/api/request'

export function getPricingList(params: Record<string, any>) {
  return http.get('/admin/pricing/list', { params })
}

export function getPricingDetail(id: number) {
  return http.get(`/admin/pricing/${id}`)
}

export function createPricing(data: Record<string, any>) {
  return http.post('/admin/pricing', data)
}

export function updatePricing(id: number, data: Record<string, any>) {
  return http.put(`/admin/pricing/${id}`, data)
}

export function deletePricing(id: number) {
  return http.delete(`/admin/pricing/${id}`)
}

export function updatePricingStatus(id: number, status: number) {
  return http.put(`/admin/pricing/${id}/status?status=${status}`)
}

export function getStationPricing(stationId: number) {
  return http.get(`/admin/pricing/station/${stationId}`)
}
