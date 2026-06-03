<template>
  <view class="page">
    <view v-if="loading" class="loading">加载中...</view>
    <view v-else-if="charger">
      <view class="info-card">
        <text class="name">{{ charger.name || charger.sn }}</text>
        <text class="detail">SN: {{ charger.sn }}</text>
        <text class="detail">额定功率: {{ charger.power }} kW</text>
        <view class="status-row">
          <text>当前状态:</text>
          <view class="status-tag" :class="statusClass(charger.status)">{{ statusText(charger.status) }}</view>
        </view>
      </view>

      <view class="action-area">
        <button v-if="charger.status === 1" class="charge-btn" @tap="startCharge">
          ⚡ 扫码启桩
        </button>
        <button v-else class="charge-btn disabled" disabled>
          {{ statusText(charger.status) }} - 不可用
        </button>

        <button class="reserve-btn" @tap="goReserve">📅 预约此桩</button>
      </view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { getChargerDetail } from '@/api/charger'
import { DEVICE_STATUS_MAP } from '@/utils/constants'
import { useScanCode } from '@/hooks/useScanCode'

const charger = ref<any>(null)
const loading = ref(false)
let chargerId = 0
const { scan } = useScanCode()

onLoad((options: any) => { chargerId = Number(options.id) })

function statusText(s: number) { return DEVICE_STATUS_MAP[s] || '未知' }
function statusClass(s: number) { return s === 1 ? 'idle' : s === 2 ? 'charging' : s === 3 ? 'fault' : 'offline' }

async function startCharge() {
  try {
    // 先尝试扫码获取 chargerId，用户也可以直接使用当前页面的 chargerId
    let id = chargerId
    try {
      id = await scan()
    } catch {
      // 扫码取消则使用当前 chargerId
    }
    uni.navigateTo({ url: `/pages/charge-confirm/index?chargerId=${id}` })
  } catch { /* 用户取消 */ }
}

function goReserve() {
  uni.navigateTo({ url: `/pages/reservation/index?chargerId=${chargerId}&stationId=${charger.value.stationId}` })
}

onMounted(async () => {
  loading.value = true
  try { charger.value = await getChargerDetail(chargerId) } catch { /* 后端不可用时忽略 */ } finally { loading.value = false }
})
</script>

<style scoped>
.page { padding: 20rpx; }
.info-card { background:#fff; padding:28rpx; border-radius:12rpx; margin-bottom:24rpx; }
.name { font-size:34rpx; font-weight:600; display:block; margin-bottom:12rpx; }
.detail { display:block; font-size:26rpx; color:#606266; line-height:40rpx; }
.status-row { display:flex; align-items:center; gap:12rpx; margin-top:12rpx; font-size:26rpx; }
.status-tag { font-size:22rpx; padding:4rpx 12rpx; border-radius:4rpx; }
.status-tag.idle { background:#e7f7e7; color:#67C23A; }
.status-tag.charging { background:#fdf6ec; color:#E6A23C; }
.status-tag.fault { background:#fef0f0; color:#F56C6C; }
.status-tag.offline { background:#f4f4f5; color:#909399; }
.action-area { margin-top:32rpx; }
.charge-btn { width:100%; height:96rpx; background:linear-gradient(135deg,#409EFF,#3a7fd8); color:#fff; border:none; font-size:32rpx; border-radius:12rpx; margin-bottom:20rpx; }
.charge-btn.disabled { background:#c0c4cc; }
.reserve-btn { width:100%; height:88rpx; background:#fff; color:#409EFF; border:2rpx solid #409EFF; font-size:30rpx; border-radius:12rpx; }
.loading { text-align:center; padding:120rpx; color:#909399; }
</style>
