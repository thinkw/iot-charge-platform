<template>
  <view class="page">
    <!-- 搜索栏 -->
    <view class="search-bar">
      <input v-model="searchName" placeholder="搜索充电站" class="search-input" @confirm="doSearch" />
      <picker :range="sortOptions" :value="sortIndex" @change="onSortChange" class="sort-picker">
        <text class="sort-text">{{ sortOptions[sortIndex] }} ▾</text>
      </picker>
    </view>

    <!-- 充电站列表（下拉刷新） -->
    <scroll-view
      scroll-y
      refresher-enabled
      :refresher-triggered="refreshing"
      @refresherrefresh="onRefresh"
      class="scroll-list"
    >
      <view v-if="firstLoad" class="loading">
        <text class="loading-icon">⚡</text>
        <text>正在查找附近的充电站...</text>
      </view>
      <view v-else>
        <view v-for="station in stations" :key="station.id" class="station-card" @tap="goDetail(station.id)">
          <view class="card-header">
            <text class="station-name">{{ station.name }}</text>
            <text class="station-status" :class="station.status===1?'open':'closed'">
              {{ station.status===1?'营业中':station.status===2?'维护中':'暂停' }}
            </text>
          </view>
          <view class="card-body">
            <text class="card-text">📍 {{ station.address }}</text>
            <text class="card-text">🅿️ 可用 {{ station.availableCount || 0 }}/{{ station.totalCount || 0 }} 桩</text>
            <text v-if="station.minPrice" class="card-text">💰 最低 {{ station.minPrice }} 元/kWh</text>
          </view>
        </view>

        <view v-if="stations.length === 0 && !loading" class="empty">
          <text class="empty-icon">🔌</text>
          <text>暂无可用的充电站</text>
          <text class="empty-hint">试试调整搜索条件或稍后再来</text>
        </view>
      </view>
    </scroll-view>
  </view>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { getStationList } from '@/api/station'
import { useLocation } from '@/hooks/useLocation'

const stations = ref<any[]>([])
const loading = ref(false)
const firstLoad = ref(true)
const refreshing = ref(false)
const searchName = ref('')
const sortIndex = ref(0)
const sortOptions = ['综合排序', '距离最近', '价格最低', '可用最多']
const { getLocation } = useLocation()

async function fetchStations() {
  loading.value = true
  try {
    const sortMap = ['', 'distance', 'price', 'available']
    const params: Record<string, any> = { page: 1, size: 50, name: searchName.value, sortBy: sortMap[sortIndex.value] }
    // 按距离排序时获取用户位置
    if (sortMap[sortIndex.value] === 'distance') {
      try {
        const loc = await getLocation()
        params.latitude = loc.latitude
        params.longitude = loc.longitude
      } catch { /* 用户拒绝定位，回退综合排序 */ }
    }
    const res: any = await getStationList(params)
    stations.value = res.records || []
  } catch {
    // 后端未启动或网络异常，忽略即可
  } finally {
    loading.value = false
    firstLoad.value = false
  }
}

async function onRefresh() {
  refreshing.value = true
  await fetchStations()
  refreshing.value = false
}

function doSearch() { fetchStations() }
function onSortChange(e: any) { sortIndex.value = e.detail.value; fetchStations() }
function goDetail(id: number) { uni.navigateTo({ url: `/pages/station-detail/index?id=${id}` }) }

onMounted(fetchStations)
</script>

<style scoped>
.page { padding: 20rpx; height: 100vh; display: flex; flex-direction: column; }
.search-bar { display:flex; gap:16rpx; margin-bottom:20rpx; align-items:center; flex-shrink: 0; }
.search-input { flex:1; height:72rpx; background:#fff; border-radius:8rpx; padding:0 20rpx; font-size:28rpx; }
.sort-picker { flex-shrink:0; background:#fff; padding:18rpx 20rpx; border-radius:8rpx; }
.sort-text { font-size:26rpx; color:#606266; }
.scroll-list { flex: 1; }
.station-card { background:#fff; margin-bottom:16rpx; padding:28rpx; border-radius:12rpx; box-shadow:0 2rpx 8rpx rgba(0,0,0,0.04); }
.card-header { display:flex; justify-content:space-between; align-items:center; margin-bottom:16rpx; }
.station-name { font-size:32rpx; font-weight:600; color:#303133; }
.station-status { font-size:24rpx; padding:4rpx 12rpx; border-radius:4rpx; }
.station-status.open { background:#e7f7e7; color:#67C23A; }
.station-status.closed { background:#f4f4f5; color:#909399; }
.card-text { display:block; font-size:26rpx; color:#606266; line-height:40rpx; }
.loading { text-align:center; padding:120rpx 0; color:#909399; }
.loading-icon { font-size:60rpx; display:block; margin-bottom:16rpx; }
.empty { text-align:center; padding:120rpx 20rpx; color:#909399; }
.empty-icon { font-size:80rpx; display:block; margin-bottom:16rpx; }
.empty-hint { display:block; font-size:24rpx; color:#c0c4cc; margin-top:8rpx; }
</style>
