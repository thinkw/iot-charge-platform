import { http } from './request'
import type { StationVO } from '@/types/api'

/**
 * 查询附近充电站
 * @param params.latitude  纬度（可选）
 * @param params.longitude 经度（可选）
 * @param params.keyword   关键字（可选）
 */
export function listStationsApi(params: {
  latitude?: number
  longitude?: number
  keyword?: string
} = {}) {
  return http.get<StationVO[]>('/station/list', params)
}

/** 充电站详情 */
export function getStationApi(stationId: number) {
  return http.get<StationVO>(`/station/${stationId}`)
}
