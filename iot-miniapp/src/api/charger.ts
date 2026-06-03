import { http } from './request'

/** 充电桩详情 */
export function getChargerDetail(id: number) {
  return http.get<any>(`/charger/${id}`)
}
