<template>
  <view class="page">
    <!-- 状态栏：根据订单阶段展示不同文案 -->
    <view class="gauge-card">
      <view class="gauge-circle" :class="{ 'gauge-circle--pending': phase === 'pending' }">
        <text class="gauge-energy">
          {{ phase === 'pending' ? '--' : (Number(data.chargedEnergy) || 0).toFixed(2) }}
        </text>
        <text class="gauge-unit">kWh</text>
      </view>
      <text class="gauge-status">
        <text v-if="phase === 'pending'">⌛ 设备启动中</text>
        <text v-else-if="phase === 'charging'">⚡ 充电中</text>
        <text v-else-if="phase === 'finished'">✓ 充电已结束</text>
        <text v-else>⚡ 充电中</text>
      </text>
    </view>

    <!-- 实时数据 -->
    <view class="data-grid" :class="{ 'data-grid--disabled': phase === 'pending' }">
      <view class="data-item">
        <text class="data-label">电压</text>
        <text class="data-value">
          {{ phase === 'pending' ? '--' : (data.voltage ?? '-') }}<text class="unit">V</text>
        </text>
      </view>
      <view class="data-item">
        <text class="data-label">电流</text>
        <text class="data-value">
          {{ phase === 'pending' ? '--' : (data.current ?? '-') }}<text class="unit">A</text>
        </text>
      </view>
      <view class="data-item">
        <text class="data-label">功率</text>
        <text class="data-value">
          {{ phase === 'pending' ? '--' : (data.power ?? '-') }}<text class="unit">kW</text>
        </text>
      </view>
      <view class="data-item">
        <text class="data-label">预估费用</text>
        <text class="data-value">
          {{ phase === 'pending' ? '--' : (Number(data.currentCost) || 0).toFixed(2) }}<text class="unit">元</text>
        </text>
      </view>
    </view>

    <!-- 时长 -->
    <view class="info-row">
      <text>已充时长: {{ phase === 'pending' ? '--' : formatDuration(data.duration ?? 0) }}</text>
    </view>

    <!-- 停止充电按钮：pending 阶段禁用 -->
    <button
      class="stop-btn"
      :class="{ 'stop-btn--disabled': phase === 'pending' }"
      :loading="stopping"
      :disabled="phase === 'pending'"
      @tap="handleStop"
    >
      {{ phase === 'pending' ? '启动中...' : '结束充电' }}
    </button>

    <!-- WS 状态 -->
    <view class="ws-status">
      <text :style="{ color: wsConnected ? '#67C23A' : '#F56C6C' }">
        {{ wsConnected ? '● 实时连接中' : '○ 连接断开' }}
      </text>
    </view>
  </view>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted, onUnmounted } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { stopCharge } from '@/api/charge'
import { useAuthStore } from '@/stores/authStore'
import { useWebSocket, type WsMessage } from '@/hooks/useWebSocket'

const orderNo = ref('')
/** 充电阶段：pending(设备启动) / charging(正在充) / finished(已结束) */
const phase = ref<'pending' | 'charging' | 'finished'>('pending')

const data = reactive({
  voltage: '' as number | string,
  current: '' as number | string,
  power: '' as number | string,
  chargedEnergy: 0,
  currentCost: 0,
  duration: 0
})
const stopping = ref(false)
const authStore = useAuthStore()

const { connected: wsConnected, connect, disconnect, onMessage } = useWebSocket(
  `/ws/charge?userId=${authStore.user?.userId || ''}`
)

onLoad((options: any) => {
  orderNo.value = options.orderNo || ''
})

onMounted(() => {
  // 1) 登录守卫：未登录直接跳登录页
  if (!authStore.isLoggedIn || !authStore.user?.userId) {
    uni.showToast({ title: '请先登录', icon: 'none' })
    setTimeout(() => { uni.reLaunch({ url: '/pages/login/index' }) }, 600)
    return
  }

  // 2) 注册充电进度消息监听
  onMessage('CHARGE_PROGRESS', (msg: WsMessage) => {
    const d = msg.data || {}
    console.log('[CHARGE_PROGRESS] raw:', JSON.stringify(d))
    // 后端推送字段：orderNo, chargedEnergy, currentPower, estimatedAmount, durationSeconds, voltage, current
    // 前端展示字段：voltage, current, power, chargedEnergy, currentCost, duration
    if (d.chargedEnergy !== undefined && d.chargedEnergy !== null) {
      data.chargedEnergy = Number(d.chargedEnergy) || 0
    }
    if (d.currentPower !== undefined && d.currentPower !== null) {
      data.power = Number(d.currentPower)
    }
    if (d.estimatedAmount !== undefined && d.estimatedAmount !== null) {
      data.currentCost = Number(d.estimatedAmount)
    }
    if (d.durationSeconds !== undefined && d.durationSeconds !== null) {
      data.duration = Number(d.durationSeconds)
    }
    // 电压/电流由后端透传
    if (d.voltage !== undefined) data.voltage = d.voltage
    if (d.current !== undefined) data.current = d.current
    // 首次收到进度数据 → 切换到 charging 阶段
    phase.value = 'charging'
  })

  // 3) 注册充电开始通知
  onMessage('CHARGE_START', (msg: WsMessage) => {
    phase.value = 'charging'
    if (msg.data?.message) {
      uni.showToast({ title: msg.data.message, icon: 'success', duration: 1500 })
    }
  })

  // 4) 注册充电结束通知
  onMessage('CHARGE_STOP', (msg: WsMessage) => {
    phase.value = 'finished'
    disconnect()
    const amount = msg.data?.totalAmount !== undefined ? Number(msg.data.totalAmount).toFixed(2) : '--'
    uni.showModal({
      title: '充电已结束',
      content: `本次充电费用：${amount} 元`,
      showCancel: false,
      confirmText: '查看订单',
      success: () => {
        uni.switchTab({ url: '/pages/order-list/index' })
      }
    })
  })

  // 5) 注册指令状态消息监听（启桩确认/超时/取消等）
  onMessage('COMMAND_STATUS', (msg: WsMessage) => {
    const status = msg.data?.status
    const message = msg.data?.message || ''
    if (status === 'PENDING') {
      phase.value = 'pending'
      uni.showToast({ title: message || '设备正在启动中，请稍候...', icon: 'none', duration: 2000 })
    } else if (status === 'SUCCESS') {
      // 静默切换阶段，等待 CHARGE_PROGRESS 推送具体数据
      phase.value = 'charging'
    } else if (status === 'TIMEOUT' || status === 'CANCELLED' || status === 'FAILED') {
      disconnect()
      uni.showModal({
        title: status === 'TIMEOUT' ? '启动超时' : status === 'CANCELLED' ? '订单已取消' : '启动失败',
        content: message || '充电启动失败',
        showCancel: false,
        confirmText: '我知道了',
        success: () => { uni.switchTab({ url: '/pages/order-list/index' }) }
      })
    }
  })

  // 6) 建立 WebSocket 连接
  connect()
})

function handleStop() {
  if (phase.value === 'pending') {
    uni.showToast({ title: '设备启动中，请稍候', icon: 'none' })
    return
  }
  uni.showModal({
    title: '确认结束充电',
    content: '结束后将自动计算费用，确定要结束吗？',
    success: async (res) => {
      if (res.confirm) {
        stopping.value = true
        try {
          await stopCharge(orderNo.value)
          phase.value = 'finished'
          disconnect()
          uni.showToast({ title: '充电已结束', icon: 'success' })
          setTimeout(() => { uni.switchTab({ url: '/pages/order-list/index' }) }, 1000)
        } catch {
          uni.showToast({ title: '操作失败', icon: 'none' })
        } finally {
          stopping.value = false
        }
      }
    }
  })
}

function formatDuration(seconds: number) {
  const h = Math.floor(seconds / 3600)
  const m = Math.floor((seconds % 3600) / 60)
  const s = seconds % 60
  return `${h > 0 ? h + '时' : ''}${m}分${s}秒`
}

// 显式管理 WS 生命周期：useWebSocket hook 已不再自动 disconnect
onUnmounted(() => disconnect())
</script>

<style scoped>
.page { padding: 20rpx; }
.gauge-card { text-align:center; background:#fff; padding:48rpx; border-radius:16rpx; margin-bottom:24rpx; }
.gauge-circle {
  width:260rpx; height:260rpx; border-radius:50%;
  border:10rpx solid #409EFF; margin:0 auto 24rpx;
  display:flex; flex-direction:column; align-items:center; justify-content:center;
  transition: border-color .3s;
}
.gauge-circle--pending { border-color: #E6A23C; }
.gauge-energy { font-size:56rpx; font-weight:700; color:#409EFF; }
.gauge-circle--pending .gauge-energy { color: #E6A23C; }
.gauge-unit { font-size:28rpx; color:#909399; }
.gauge-status { font-size:30rpx; color:#409EFF; }
.gauge-circle--pending + .gauge-status { color: #E6A23C; }

.data-grid { display:flex; flex-wrap:wrap; gap:16rpx; margin-bottom:24rpx; transition: opacity .3s; }
.data-grid--disabled { opacity: .55; }
.data-item { flex:1 1 45%; background:#fff; padding:28rpx; border-radius:12rpx; text-align:center; min-width:300rpx; }
.data-label { display:block; font-size:24rpx; color:#909399; margin-bottom:8rpx; }
.data-value { font-size:40rpx; font-weight:600; color:#303133; }
.unit { font-size:24rpx; color:#909399; font-weight:400; }
.info-row { background:#fff; padding:24rpx; border-radius:12rpx; text-align:center; font-size:28rpx; color:#606266; margin-bottom:32rpx; }
.stop-btn { width:100%; height:96rpx; background:#F56C6C; color:#fff; border:none; font-size:32rpx; border-radius:12rpx; }
.stop-btn--disabled { background:#C0C4CC; }
.ws-status { text-align:center; margin-top:24rpx; font-size:24rpx; }
</style>
