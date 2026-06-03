<template>
  <view class="page">
    <view v-if="loading" class="loading">处理中...</view>
    <view v-else class="confirm-card">
      <view class="icon-row">⚡</view>
      <text class="confirm-title">确认开始充电？</text>
      <text class="confirm-desc">充电桩: #{{ chargerId }}</text>
      <text class="confirm-desc">充电将立即开始，请确认充电枪已连接</text>
      <button class="start-btn" :loading="submitting" @tap="handleStart">
        开始充电
      </button>
      <button class="cancel-btn" @tap="goBack">取消</button>
      <view v-if="errorMsg" class="error">{{ errorMsg }}</view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { startCharge } from '@/api/charge'

const chargerId = ref(0)
const loading = ref(false)
const submitting = ref(false)
const errorMsg = ref('')

onLoad((options: any) => { chargerId.value = Number(options.chargerId) })

async function handleStart() {
  submitting.value = true
  errorMsg.value = ''
  try {
    const orderNo: any = await startCharge(chargerId.value)
    uni.redirectTo({ url: `/pages/charge-monitor/index?orderNo=${orderNo}` })
  } catch (e: any) {
    errorMsg.value = e.message || '启桩失败'
  } finally { submitting.value = false }
}

function goBack() { uni.navigateBack() }
</script>

<style scoped>
.page { min-height:100vh; display:flex; align-items:center; justify-content:center; padding:40rpx; }
.confirm-card { background:#fff; width:100%; max-width:600rpx; padding:48rpx; border-radius:16rpx; text-align:center; }
.icon-row { font-size:80rpx; margin-bottom:24rpx; }
.confirm-title { font-size:34rpx; font-weight:600; display:block; margin-bottom:16rpx; }
.confirm-desc { font-size:28rpx; color:#606266; display:block; margin-bottom:8rpx; }
.start-btn { width:100%; margin-top:48rpx; background:#409EFF; color:#fff; border:none; height:96rpx; line-height:96rpx; border-radius:12rpx; font-size:32rpx; }
.cancel-btn { width:100%; margin-top:20rpx; background:#f5f7fa; color:#606266; border:none; height:88rpx; line-height:88rpx; border-radius:12rpx; font-size:28rpx; }
.error { background:#fef0f0; color:#F56C6C; padding:20rpx; border-radius:8rpx; margin-top:24rpx; font-size:26rpx; }
.loading { text-align:center; color:#909399; padding-top:200rpx; }
</style>
