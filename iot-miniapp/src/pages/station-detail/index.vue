<template>
  <view class="page">
    <view v-if="loading" class="loading">加载中...</view>
    <view v-else-if="station">
      <!-- 站点信息 -->
      <view class="info-card">
        <text class="name">{{ station.name }}</text>
        <text class="address">📍 {{ station.address }}</text>
        <text class="hours">🕐 {{ station.businessHours || '00:00-24:00' }}</text>
        <text class="contact">📞 {{ station.contact || '暂无' }}</text>
      </view>

      <!-- 充电桩列表 -->
      <view class="section-title">充电桩列表</view>
      <view v-for="charger in station.chargers" :key="charger.id" class="charger-card"
        @tap="goCharger(charger.id)">
        <view class="charger-left">
          <text class="charger-name">{{ charger.name || charger.sn }}</text>
          <text class="charger-power">{{ charger.power }}kW</text>
        </view>
        <view class="charger-right">
          <view class="status-tag" :class="statusClass(charger.status)">
            {{ statusText(charger.status) }}
          </view>
          <text class="go-icon">›</text>
        </view>
      </view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { getStationApi } from '@/api/station'
import { DEVICE_STATUS_MAP } from '@/utils/constants'

const station = ref<any>(null)
const loading = ref(false)
let stationId = 0

onLoad((options: any) => { stationId = Number(options.id) })

function statusText(s: number) { return DEVICE_STATUS_MAP[s] || '未知' }
function statusClass(s: number) { return s === 1 ? 'idle' : s === 2 ? 'charging' : s === 3 ? 'fault' : 'offline' }
function goCharger(id: number) { uni.navigateTo({ url: `/pages/charger-detail/index?id=${id}` }) }

onMounted(async () => {
  loading.value = true
  try { station.value = await getStationApi(stationId) } catch { /* 后端不可用时忽略 */ } finally { loading.value = false }
})
</script>

<style scoped>
.page { padding: 20rpx; }
.info-card { background:#fff; padding:28rpx; border-radius:12rpx; margin-bottom:24rpx; }
.name { font-size:34rpx; font-weight:600; display:block; margin-bottom:12rpx; }
.address, .hours, .contact { display:block; font-size:26rpx; color:#606266; line-height:40rpx; }
.section-title { font-size:30rpx; font-weight:600; margin-bottom:16rpx; }
.charger-card { background:#fff; padding:24rpx; border-radius:10rpx; margin-bottom:12rpx; display:flex; justify-content:space-between; align-items:center; }
.charger-name { font-size:28rpx; font-weight:500; display:block; }
.charger-power { font-size:24rpx; color:#909399; }
.charger-right { display:flex; align-items:center; gap:12rpx; }
.status-tag { font-size:22rpx; padding:4rpx 12rpx; border-radius:4rpx; }
.status-tag.idle { background:#e7f7e7; color:#67C23A; }
.status-tag.charging { background:#fdf6ec; color:#E6A23C; }
.status-tag.fault { background:#fef0f0; color:#F56C6C; }
.status-tag.offline { background:#f4f4f5; color:#909399; }
.go-icon { font-size:36rpx; color:#c0c4cc; }
.loading { text-align:center; padding:120rpx; color:#909399; }
</style>
