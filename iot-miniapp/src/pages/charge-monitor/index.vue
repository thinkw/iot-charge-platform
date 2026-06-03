<template>
  <view class="page">
    <!-- 充电进度仪表盘 -->
    <view class="gauge-card">
      <view class="gauge-circle">
        <text class="gauge-energy">{{ (data.chargedEnergy || 0).toFixed(2) }}</text>
        <text class="gauge-unit">kWh</text>
      </view>
      <text class="gauge-status">⚡ 充电中</text>
    </view>

    <!-- 实时数据 -->
    <view class="data-grid">
      <view class="data-item">
        <text class="data-label">电压</text>
        <text class="data-value">{{ data.voltage || '-' }}<text class="unit">V</text></text>
      </view>
      <view class="data-item">
        <text class="data-label">电流</text>
        <text class="data-value">{{ data.current || '-' }}<text class="unit">A</text></text>
      </view>
      <view class="data-item">
        <text class="data-label">功率</text>
        <text class="data-value">{{ data.power || '-' }}<text class="unit">kW</text></text>
      </view>
      <view class="data-item">
        <text class="data-label">预估费用</text>
        <text class="data-value">{{ (data.currentCost || 0).toFixed(2) }}<text class="unit">元</text></text>
      </view>
    </view>

    <!-- 时长 -->
    <view class="info-row">
      <text>已充时长: {{ formatDuration(data.duration || 0) }}</text>
    </view>

    <!-- 停止充电按钮 -->
    <button class="stop-btn" :loading="stopping" @tap="handleStop">
      结束充电
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

let orderNo = ''
const data = reactive({ voltage: '', current: '', power: '', chargedEnergy: 0, currentCost: 0, duration: 0 })
const stopping = ref(false)
const authStore = useAuthStore()

const { connected: wsConnected, connect, disconnect, onMessage } = useWebSocket(`/ws/charge?userId=${authStore.user?.userId || ''}`)

onLoad((options: any) => { orderNo = options.orderNo })

onMounted(() => {
  // 注册充电进度消息监听
  onMessage('CHARGE_PROGRESS', (msg: WsMessage) => {
    Object.assign(data, msg.data)
  })
  // 建立 WebSocket 连接
  connect()
})

function handleStop() {
  uni.showModal({
    title: '确认结束充电',
    content: '结束后将自动计算费用，确定要结束吗？',
    success: async (res) => {
      if (res.confirm) {
        stopping.value = true
        try {
          await stopCharge(orderNo)
          uni.showToast({ title: '充电已结束', icon: 'success' })
          setTimeout(() => { uni.switchTab({ url: '/pages/order-list/index' }) }, 1000)
        } catch {
          uni.showToast({ title: '操作失败', icon: 'none' })
        } finally { stopping.value = false }
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

onUnmounted(() => { disconnect() })
</script>

<style scoped>
.page { padding: 20rpx; }
.gauge-card { text-align:center; background:#fff; padding:48rpx; border-radius:16rpx; margin-bottom:24rpx; }
.gauge-circle { width:260rpx; height:260rpx; border-radius:50%; border:10rpx solid #409EFF; margin:0 auto 24rpx; display:flex; flex-direction:column; align-items:center; justify-content:center; }
.gauge-energy { font-size:56rpx; font-weight:700; color:#409EFF; }
.gauge-unit { font-size:28rpx; color:#909399; }
.gauge-status { font-size:30rpx; color:#409EFF; }
.data-grid { display:flex; flex-wrap:wrap; gap:16rpx; margin-bottom:24rpx; }
.data-item { flex:1 1 45%; background:#fff; padding:28rpx; border-radius:12rpx; text-align:center; min-width:300rpx; }
.data-label { display:block; font-size:24rpx; color:#909399; margin-bottom:8rpx; }
.data-value { font-size:40rpx; font-weight:600; color:#303133; }
.unit { font-size:24rpx; color:#909399; font-weight:400; }
.info-row { background:#fff; padding:24rpx; border-radius:12rpx; text-align:center; font-size:28rpx; color:#606266; margin-bottom:32rpx; }
.stop-btn { width:100%; height:96rpx; background:#F56C6C; color:#fff; border:none; font-size:32rpx; border-radius:12rpx; }
.ws-status { text-align:center; margin-top:24rpx; font-size:24rpx; }
</style>
