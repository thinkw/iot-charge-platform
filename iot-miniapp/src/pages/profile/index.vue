<template>
  <view class="page">
    <!-- 用户信息卡片 -->
    <view class="user-card">
      <view class="avatar">👤</view>
      <view class="user-info">
        <text class="nickname">{{ userInfo?.nickname || '用户' }}</text>
        <text class="phone">{{ userInfo?.phone || authStore.user?.phone || '未登录' }}</text>
      </view>
    </view>

    <!-- 车辆信息 -->
    <view class="section">
      <text class="section-title">我的车辆</text>
      <view class="car-card">
        <text>车牌号: {{ userInfo?.plateNo || '未设置' }}</text>
        <text>车型: {{ userInfo?.carModel || '未设置' }}</text>
      </view>
    </view>

    <!-- 快捷入口 -->
    <view class="section">
      <text class="section-title">快捷功能</text>
      <view class="menu-list">
        <view class="menu-item" @tap="scanCharge">📷 扫码充电</view>
        <view class="menu-item" @tap="goOrders">📋 我的订单</view>
        <view class="menu-item" @tap="goReserve">📅 预约充电</view>
      </view>
    </view>

    <!-- 退出登录 -->
    <button class="logout-btn" @tap="handleLogout">退出登录</button>
  </view>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useAuthStore } from '@/stores/authStore'
import { useScanCode } from '@/hooks/useScanCode'

const authStore = useAuthStore()
const userInfo = ref<any>(null)
const { scan } = useScanCode()

onMounted(async () => {
  try {
    await authStore.fetchUserInfo()
    userInfo.value = authStore.userInfo
  } catch {
    // 未登录或后端不可用，忽略即可
  }
})

async function scanCharge() {
  try {
    const chargerId = await scan()
    uni.navigateTo({ url: `/pages/charge-confirm/index?chargerId=${chargerId}` })
  } catch { /* 用户取消或扫码失败 */ }
}

function goOrders() { uni.switchTab({ url: '/pages/order-list/index' }) }
function goReserve() { uni.navigateTo({ url: '/pages/reservation/index' }) }

function handleLogout() {
  uni.showModal({
    title: '退出登录',
    content: '确定要退出登录吗？',
    success: (res) => {
      if (res.confirm) authStore.logout()
    }
  })
}
</script>

<style scoped>
.page { padding: 20rpx; }
.user-card { background:linear-gradient(135deg,#409EFF,#3a7fd8); padding:40rpx; border-radius:16rpx; display:flex; align-items:center; gap:24rpx; margin-bottom:24rpx; }
.avatar { width:96rpx; height:96rpx; border-radius:50%; background:rgba(255,255,255,0.2); display:flex; align-items:center; justify-content:center; font-size:48rpx; }
.nickname { font-size:34rpx; color:#fff; font-weight:600; display:block; }
.phone { font-size:26rpx; color:rgba(255,255,255,0.8); display:block; }
.section { margin-bottom:24rpx; }
.section-title { font-size:30rpx; font-weight:600; display:block; margin-bottom:16rpx; }
.car-card { background:#fff; padding:24rpx; border-radius:12rpx; }
.car-card text { display:block; font-size:28rpx; color:#606266; line-height:40rpx; }
.menu-list { background:#fff; border-radius:12rpx; overflow:hidden; }
.menu-item { padding:28rpx; font-size:28rpx; color:#303133; border-bottom:1rpx solid #f5f7fa; }
.menu-item:last-child { border-bottom:none; }
.logout-btn { width:100%; margin-top:32rpx; background:#fff; color:#F56C6C; border:none; height:88rpx; line-height:88rpx; border-radius:12rpx; font-size:30rpx; }
</style>
