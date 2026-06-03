<template>
  <view>
    <!-- 页面路由出口 -->
  </view>
</template>

<script setup lang="ts">
import { onLaunch, onShow, onHide } from '@dcloudio/uni-app'

onLaunch(() => {
  console.log('[App] 小程序启动')
})

onShow(() => {
  console.log('[App] 小程序显示')
})

onHide(() => {
  console.log('[App] 小程序隐藏')
})
</script>

<style lang="scss">
/* 全局样式 */
page {
  background-color: #f5f7fa;
  font-family: -apple-system, BlinkMacSystemFont, 'Helvetica Neue', 'PingFang SC', sans-serif;
  font-size: 28rpx;
  color: #303133;
}
</style>
