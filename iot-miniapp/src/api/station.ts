import { http } from './request'

/** 充电站列表 */
export function getStationList(params: Record<string, any>) {
  return http.get<any>('/station/list', params)
}

/** 充电站详情 */
export function getStationDetail(id: number) {
  return http.get<any>(`/station/${id}`)
}
