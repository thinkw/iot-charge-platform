import http from '@/api/request'

export function getChargerList(params: Record<string, any>) {
  return http.get('/admin/charger/list', { params })
}

export function getChargerDetail(id: number) {
  return http.get(`/admin/charger/${id}`)
}

export function createCharger(data: Record<string, any>) {
  return http.post('/admin/charger', data)
}

export function updateCharger(id: number, data: Record<string, any>) {
  return http.put(`/admin/charger/${id}`, data)
}

export function deleteCharger(id: number) {
  return http.delete(`/admin/charger/${id}`)
}

export function updateChargerStatus(id: number, status: number) {
  return http.put(`/admin/charger/${id}/status?status=${status}`)
}
