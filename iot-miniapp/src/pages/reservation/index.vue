<template>
  <view class="page">
    <view class="form-card">
      <text class="form-title">📅 预约充电</text>

      <text class="form-label">充电桩 ID</text>
      <input v-model="form.chargerId" type="number" placeholder="请输入充电桩ID" class="form-input" />

      <text class="form-label">预约日期</text>
      <picker mode="date" :value="form.reserveDate" @change="onDateChange">
        <view class="picker-view">{{ form.reserveDate || '请选择日期' }}</view>
      </picker>

      <text class="form-label">开始时间</text>
      <picker mode="time" :value="form.startTime" @change="onStartTimeChange">
        <view class="picker-view">{{ form.startTime || '请选择开始时间' }}</view>
      </picker>

      <text class="form-label">结束时间</text>
      <picker mode="time" :value="form.endTime" @change="onEndTimeChange">
        <view class="picker-view">{{ form.endTime || '请选择结束时间' }}</view>
      </picker>

      <view class="info-note">
        <text>💡 押金: ¥30.00（按时到场使用后抵扣）</text>
        <text>⚠️ 超时未使用将扣除违约金 ¥10.00</text>
      </view>

      <button class="submit-btn" :loading="submitting" @tap="handleSubmit">
        确认预约
      </button>
      <button class="cancel-btn" @tap="handleCancel" :loading="cancelLoading" v-if="showCancel">
        取消已有预约
      </button>

      <view v-if="errorMsg" class="error">{{ errorMsg }}</view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { ref, reactive } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { createReservationApi, cancelReservationApi } from '@/api/reservation'

const form = reactive({ chargerId: 0, reserveDate: '', startTime: '', endTime: '' })
const submitting = ref(false)
const cancelLoading = ref(false)
const errorMsg = ref('')
const showCancel = ref(false)
const lastOrderNo = ref('')

onLoad((options: any) => {
  if (options.chargerId) form.chargerId = Number(options.chargerId)
})

function onDateChange(e: any) { form.reserveDate = e.detail.value }
function onStartTimeChange(e: any) { form.startTime = e.detail.value }
function onEndTimeChange(e: any) { form.endTime = e.detail.value }

async function handleSubmit() {
  if (!form.chargerId || !form.reserveDate || !form.startTime || !form.endTime) {
    errorMsg.value = '请填写完整的预约信息'
    return
  }
  if (form.startTime >= form.endTime) {
    errorMsg.value = '结束时间必须晚于开始时间'
    return
  }
  submitting.value = true
  errorMsg.value = ''
  try {
    const orderNo: any = await createReservationApi({
      chargerId: form.chargerId,
      reserveDate: form.reserveDate,
      startTime: form.startTime + ':00',
      endTime: form.endTime + ':00'
    })
    uni.showToast({ title: `预约成功`, icon: 'success' })
    lastOrderNo.value = orderNo
    showCancel.value = true
  } catch (e: any) {
    errorMsg.value = e.message || '预约失败'
  } finally { submitting.value = false }
}

async function handleCancel() {
  uni.showModal({
    title: '取消预约',
    content: '确定取消此预约吗？押金将退还。',
    success: async (res) => {
      if (res.confirm) {
        cancelLoading.value = true
        try {
          await cancelReservationApi(lastOrderNo.value)
          uni.showToast({ title: '已取消', icon: 'success' })
          showCancel.value = false
        } catch { /* 错误已在 request 层处理 */ }
        finally { cancelLoading.value = false }
      }
    }
  })
}
</script>

<style scoped>
.page { padding: 20rpx; }
.form-card { background:#fff; padding:36rpx; border-radius:12rpx; }
.form-title { font-size:34rpx; font-weight:600; display:block; margin-bottom:32rpx; text-align:center; }
.form-label { font-size:28rpx; color:#606266; display:block; margin-bottom:8rpx; margin-top:20rpx; }
.form-input { width:100%; height:88rpx; border:1rpx solid #dcdfe6; border-radius:8rpx; padding:0 20rpx; font-size:28rpx; box-sizing:border-box; }
.picker-view { height:88rpx; line-height:88rpx; border:1rpx solid #dcdfe6; border-radius:8rpx; padding:0 20rpx; font-size:28rpx; color:#303133; }
.info-note { margin-top:32rpx; padding:20rpx; background:#fdf6ec; border-radius:8rpx; }
.info-note text { display:block; font-size:24rpx; color:#E6A23C; line-height:36rpx; }
.submit-btn { width:100%; margin-top:48rpx; background:#409EFF; color:#fff; border:none; height:96rpx; line-height:96rpx; border-radius:12rpx; font-size:32rpx; }
.cancel-btn { width:100%; margin-top:20rpx; background:#fff; color:#909399; border:1rpx solid #dcdfe6; height:88rpx; line-height:88rpx; border-radius:12rpx; font-size:28rpx; }
.error { background:#fef0f0; color:#F56C6C; padding:20rpx; border-radius:8rpx; margin-top:24rpx; font-size:26rpx; }
</style>
