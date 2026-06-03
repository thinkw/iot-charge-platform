import http from '@/api/request'

export function getRoleList(params: Record<string, any>) {
  return http.get('/admin/role/list', { params })
}

export function getRoleDetail(id: number) {
  return http.get(`/admin/role/${id}`)
}

export function createRole(data: Record<string, any>) {
  return http.post('/admin/role', data)
}

export function updateRole(id: number, data: Record<string, any>) {
  return http.put(`/admin/role/${id}`, data)
}

export function deleteRole(id: number) {
  return http.delete(`/admin/role/${id}`)
}

export function updateRoleStatus(id: number, status: number) {
  return http.put(`/admin/role/${id}/status?status=${status}`)
}

export function getPermissionTree() {
  return http.get('/admin/role/permission/tree')
}
