import http from '@/api/request'

/** 查询充电站列表 */
export function getStationList(params: Record<string, any>) {
  return http.get('/admin/station/list', { params })
}

/** 获取充电站详情 */
export function getStationDetail(id: number) {
  return http.get(`/admin/station/${id}`)
}

/** 新增充电站 */
export function createStation(data: Record<string, any>) {
  return http.post('/admin/station', data)
}

/** 修改充电站 */
export function updateStation(id: number, data: Record<string, any>) {
  return http.put(`/admin/station/${id}`, data)
}

/** 删除充电站 */
export function deleteStation(id: number) {
  return http.delete(`/admin/station/${id}`)
}

/** 修改营业状态 */
export function updateStationStatus(id: number, status: number) {
  return http.put(`/admin/station/${id}/status?status=${status}`)
}
