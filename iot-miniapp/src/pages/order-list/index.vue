<template>
  <view class="page">
    <!-- 状态筛选 Tab（4 Tab 等分 + nowrap，避免窄屏挤压） -->
    <view class="tabs">
      <view v-for="(t, i) in tabs" :key="i" class="tab" :class="{ active: activeTab === t.value }"
        @tap="activeTab = t.value; fetchOrders()">
        {{ t.label }}
      </view>
    </view>

    <view v-if="loading" class="loading">加载中...</view>
    <view v-else>
      <view v-for="order in orders" :key="order.orderNo" class="order-card" @tap="goDetail(order.orderNo)">
        <view class="card-top">
          <text class="order-no">订单号: {{ order.orderNo }}</text>
          <text class="order-status">{{ statusText(order.orderStatus) }}</text>
        </view>
        <view class="card-mid">
          <text>充电桩: {{ order.chargerName || order.chargerId }}</text>
          <text>充电量: {{ order.chargedEnergy }} kWh</text>
        </view>
        <view class="card-bottom">
          <text class="amount">¥{{ order.totalAmount }}</text>
          <text class="time">{{ order.startTime }}</text>
        </view>
        <!-- 充电中：提供进入监控的入口 -->
        <view v-if="order.orderStatus === 1" class="card-action" @tap.stop="goMonitor(order.orderNo)">
          <text class="monitor-link">⚡ 查看充电进度 →</text>
        </view>
      </view>

      <view v-if="orders.length === 0" class="empty">
        <text>暂无订单</text>
      </view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { listOrdersApi } from '@/api/order'
import { ORDER_STATUS_MAP } from '@/utils/constants'

const orders = ref<any[]>([])
const loading = ref(false)
const activeTab = ref<number | undefined>(undefined)
const tabs = [
  { label: '全部', value: undefined },
  { label: '充电中', value: 1 },
  { label: '待确认', value: 5 },
  { label: '已完成', value: 2 }
]

function statusText(s: number) { return ORDER_STATUS_MAP[s] || '未知' }

async function fetchOrders() {
  loading.value = true
  try {
    const params: any = { page: 1, size: 50 }
    if (activeTab.value !== undefined && activeTab.value !== null) params.orderStatus = activeTab.value
    const res: any = await listOrdersApi(params)
    orders.value = res.records || []
  } catch {
    // 后端未启动或网络异常，忽略即可
  } finally { loading.value = false }
}

function goDetail(orderNo: string) { uni.navigateTo({ url: `/pages/order-detail/index?orderNo=${encodeURIComponent(orderNo)}` }) }
function goMonitor(orderNo: string) { uni.navigateTo({ url: `/pages/charge-monitor/index?orderNo=${encodeURIComponent(orderNo)}` }) }

onMounted(fetchOrders)
</script>

<style scoped>
.page { padding: 20rpx; }
.tabs { display:flex; gap:0; margin-bottom:20rpx; background:#fff; border-radius:8rpx; overflow:hidden; }
.tab {
  flex:1; min-width:0;
  text-align:center; padding:20rpx 4rpx; font-size:26rpx; color:#606266;
  white-space:nowrap; overflow:hidden; text-overflow:ellipsis;
}
.tab.active { color:#409EFF; background:#ecf5ff; font-weight:600; }
.order-card { background:#fff; padding:24rpx; border-radius:12rpx; margin-bottom:16rpx; }
.card-top { display:flex; justify-content:space-between; margin-bottom:12rpx; }
.order-no { font-size:26rpx; color:#606266; }
.order-status { font-size:24rpx; color:#E6A23C; }
.card-mid text { display:block; font-size:26rpx; color:#909399; line-height:36rpx; }
.card-bottom { display:flex; justify-content:space-between; align-items:center; margin-top:12rpx; }
.amount { font-size:34rpx; font-weight:600; color:#F56C6C; }
.time { font-size:24rpx; color:#c0c4cc; }
.loading { text-align:center; padding:80rpx; color:#909399; }
.empty { text-align:center; padding:120rpx; color:#909399; }
.card-action { margin-top:16rpx; padding-top:16rpx; border-top:1rpx solid #f0f0f0; }
.monitor-link { font-size:26rpx; color:#409EFF; font-weight:500; }
</style>
