import { http } from './request'
import type { ChargerVO } from '@/types/api'

/** 充电站下的充电桩列表 */
export function listChargersApi(stationId: number) {
  return http.get<ChargerVO[]>('/charger/list', { stationId })
}

/** 充电桩详情 */
export function getChargerApi(chargerId: number) {
  return http.get<ChargerVO>(`/charger/${chargerId}`)
}
