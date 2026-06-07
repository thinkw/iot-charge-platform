<template>
  <view class="page">
    <view v-if="loading" class="loading">加载中...</view>
    <view v-else-if="order" class="detail-card">
      <text class="section-title">订单信息</text>
      <view class="info-grid">
        <view class="info-item"><text class="label">订单编号</text><text class="value">{{ order.orderNo }}</text></view>
        <view class="info-item"><text class="label">充电桩</text><text class="value">{{ order.chargerName || order.chargerId }}</text></view>
        <view class="info-item"><text class="label">充电站</text><text class="value">{{ order.stationName || order.stationId }}</text></view>
        <view class="info-item"><text class="label">开始时间</text><text class="value">{{ order.startTime }}</text></view>
        <view class="info-item"><text class="label">结束时间</text><text class="value">{{ order.endTime || (order.orderStatus === 6 ? '等待设备确认' : '充电中') }}</text></view>
        <view class="info-item"><text class="label">充电量</text><text class="value">{{ order.chargedEnergy }} kWh</text></view>
        <view class="info-item"><text class="label">金额</text><text class="value amount">¥{{ order.totalAmount }}</text></view>
        <view class="info-item"><text class="label">订单状态</text><text class="value">{{ orderStatusText(order.orderStatus) }}</text></view>
        <view class="info-item"><text class="label">支付状态</text><text class="value">{{ payStatusText(order.payStatus) }}</text></view>
      </view>

      <!-- 操作按钮 -->
      <!-- 充电中：进入充电监控 -->
      <view class="actions" v-if="order.orderStatus === 1">
        <button class="monitor-btn" @tap="goMonitor">⚡ 进入充电监控</button>
      </view>
      <!-- 待支付：充电已结束，账单已生成 -->
      <view class="actions" v-if="order.orderStatus === 5">
        <button class="pay-btn" @tap="handlePay">去支付</button>
      </view>
      <!-- 等待设备：启桩指令已下发，设备正在响应 -->
      <view class="actions" v-if="order.orderStatus === 6">
        <view class="pending-hint">⏳ 设备正在响应中，请稍候...</view>
      </view>
      <view class="actions" v-if="order.orderStatus === 2 && order.payStatus === 0">
        <button class="pay-btn" @tap="handlePay">去支付</button>
      </view>
      <view class="actions" v-if="order.payStatus === 1">
        <button class="refund-btn" @tap="handleRefund">申请退款</button>
      </view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { ref, onUnmounted } from 'vue'
import { onLoad, onShow, onHide } from '@dcloudio/uni-app'
import { getOrderApi, payOrder, refundOrder } from '@/api/order'
import { ORDER_STATUS_MAP, PAY_STATUS_MAP } from '@/utils/constants'

const order = ref<any>(null)
const loading = ref(false)
let orderNoStr = ''
let pollTimer: ReturnType<typeof setInterval> | null = null
/** 是否已完成首次加载：防止 onLoad + onShow 双重请求 */
let initialLoaded = false

onLoad((options: any) => {
  orderNoStr = options.orderNo || ''
  fetchOrder()
})

// 页面显示时刷新（仅非首次加载时，避免与 onLoad 重复请求）
onShow(() => {
  if (!initialLoaded) {
    initialLoaded = true
    return
  }
  if (orderNoStr) fetchOrder()
})

// 页面隐藏/卸载时清除轮询
onHide(() => stopPolling())
onUnmounted(() => stopPolling())

function orderStatusText(s: number) { return ORDER_STATUS_MAP[s] || '未知' }
function payStatusText(s: number) { return PAY_STATUS_MAP[s] || '未知' }

async function fetchOrder() {
  if (!orderNoStr) return
  loading.value = true
  try {
    order.value = await getOrderApi(orderNoStr)
    // AWAITING_DEVICE（等待设备确认）开启 3 秒轮询
    if (order.value?.orderStatus === 6) {
      startPolling()
    } else {
      stopPolling()
    }
  } catch { /* 订单不存在时忽略 */ } finally { loading.value = false }
}

function startPolling() {
  if (pollTimer) return
  pollTimer = setInterval(async () => {
    try {
      const fresh = await getOrderApi(orderNoStr)
      order.value = fresh
      // AWAITING_DEVICE 状态变化 → 停止轮询
      if (fresh?.orderStatus !== 6) {
        stopPolling()
      }
    } catch { /* 静默 */ }
  }, 3000)
}

function stopPolling() {
  if (pollTimer) {
    clearInterval(pollTimer)
    pollTimer = null
  }
}

function goMonitor() {
  uni.navigateTo({ url: `/pages/charge-monitor/index?orderNo=${encodeURIComponent(orderNoStr)}` })
}

async function handlePay() {
  try {
    await payOrder(order.value!.orderNo)
    uni.showToast({ title: '支付成功', icon: 'success' })
    fetchOrder()
  } catch { /* 错误已在 request 层处理 */ }
}

async function handleRefund() {
  uni.showModal({
    title: '确认退款',
    content: '确定要申请退款吗？退款将原路返回。',
    success: async (res) => {
      if (res.confirm) {
        try {
          await refundOrder(order.value!.orderNo)
          uni.showToast({ title: '退款成功', icon: 'success' })
          fetchOrder()
        } catch { /* 错误已在 request 层处理 */ }
      }
    }
  })
}
</script>

<style scoped>
.page { padding: 20rpx; }
.detail-card { background:#fff; padding:28rpx; border-radius:12rpx; }
.section-title { font-size:32rpx; font-weight:600; display:block; margin-bottom:24rpx; }
.info-grid { display:flex; flex-direction:column; gap:16rpx; }
.info-item { display:flex; justify-content:space-between; align-items:center; padding:12rpx 0; border-bottom:1rpx solid #f0f0f0; }
.label { font-size:26rpx; color:#909399; }
.value { font-size:28rpx; color:#303133; text-align:right; max-width:60%; }
.amount { color:#F56C6C; font-weight:600; }
.pending-hint { width:100%; padding:32rpx 0; text-align:center; font-size:28rpx; color:#E6A23C; background:#fdf6ec; border-radius:12rpx; }
.actions { margin-top:40rpx; }
.pay-btn { width:100%; height:96rpx; background:#409EFF; color:#fff; border:none; border-radius:12rpx; font-size:32rpx; }
.monitor-btn { width:100%; height:96rpx; background:linear-gradient(135deg,#409EFF,#67C23A); color:#fff; border:none; border-radius:12rpx; font-size:32rpx; }
.refund-btn { width:100%; height:88rpx; background:#fff; color:#F56C6C; border:2rpx solid #F56C6C; border-radius:12rpx; font-size:30rpx; }
.loading { text-align:center; padding:120rpx; color:#909399; }
</style>
