import { defineStore } from 'pinia'
import { ref, reactive } from 'vue'

/**
 * 充电状态管理
 * <p>
 * 管理当前充电会话的实时数据，与 WebSocket 推送同步。
 * 跨页面共享：charge-confirm → charge-monitor
 * </p>
 */
export const useChargingStore = defineStore('charging', () => {
  /** 当前充电订单号 */
  const orderNo = ref('')

  /** 充电实时数据 */
  const data = reactive({
    voltage: '', current: '', power: '',
    chargedEnergy: 0, currentCost: 0, duration: 0
  })

  /** 是否正在充电 */
  const isCharging = ref(false)

  /** WebSocket 连接状态 */
  const wsConnected = ref(false)

  /** 开始充电 */
  function startCharging(no: string) {
    orderNo.value = no
    isCharging.value = true
    // 重置数据
    Object.assign(data, { voltage: '', current: '', power: '', chargedEnergy: 0, currentCost: 0, duration: 0 })
  }

  /** 更新充电数据（由 WebSocket 推送触发） */
  function updateData(payload: Record<string, any>) {
    if (payload.voltage !== undefined) data.voltage = payload.voltage
    if (payload.current !== undefined) data.current = payload.current
    if (payload.power !== undefined) data.power = payload.power
    if (payload.chargedEnergy !== undefined) data.chargedEnergy = payload.chargedEnergy
    if (payload.currentCost !== undefined) data.currentCost = payload.currentCost
    if (payload.duration !== undefined) data.duration = payload.duration
  }

  /** 结束充电 */
  function endCharging() {
    orderNo.value = ''
    isCharging.value = false
    wsConnected.value = false
    Object.assign(data, { voltage: '', current: '', power: '', chargedEnergy: 0, currentCost: 0, duration: 0 })
  }

  return { orderNo, data, isCharging, wsConnected, startCharging, updateData, endCharging }
})
