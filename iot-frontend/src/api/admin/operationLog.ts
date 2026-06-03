import http from '@/api/request'

export function getOperationLogList(params: Record<string, any>) {
  return http.get('/admin/operation-log/list', { params })
}
