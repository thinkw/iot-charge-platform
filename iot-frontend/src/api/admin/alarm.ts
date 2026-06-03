import http from '@/api/request'

export function getAlarmList(params: Record<string, any>) {
  return http.get('/admin/alarm/list', { params })
}

export function getAlarmDetail(id: number) {
  return http.get(`/admin/alarm/${id}`)
}

export function handleAlarm(alarmId: number, handleNote: string) {
  return http.post('/admin/alarm/handle', { alarmId, handleNote })
}

export function getAlarmStatistics() {
  return http.get('/admin/alarm/statistics')
}
