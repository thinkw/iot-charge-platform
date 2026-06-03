import http from '@/api/request'

export function getUserList(params: Record<string, any>) {
  return http.get('/admin/user/list', { params })
}

export function updateUserStatus(id: number, status: number) {
  return http.put(`/admin/user/${id}/status?status=${status}`)
}
