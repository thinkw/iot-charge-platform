<template>
  <view>
    <!-- 页面路由出口 -->
  </view>
</template>

<script setup lang="ts">
import { onLaunch, onShow, onHide } from '@dcloudio/uni-app'

/**
 * 全局导航防抖守卫
 * <p>
 * 微信小程序限制同时只能有一个 navigateTo 在运行。
 * 用户快速双击时，第二次 navigateTo 因第一次未完成而报
 * "navigateTo:fail timeout"。
 * <p>
 * 通过在 App.onLaunch 中拦截 uni.navigateTo，添加 300ms
 * 最小调用间隔，从根本上防止双击导致的 navigateTo timeout。
 * 所有页面无需任何改动，现有 uni.navigateTo 调用自动受保护。
 * </p>
 */
function installNavigationGuard() {
  const originalNavigateTo = uni.navigateTo
  let lastCallTime = 0
  const MIN_GAP = 300

  uni.navigateTo = function (options: UniApp.NavigateToOptions) {
    const now = Date.now()
    if (now - lastCallTime < MIN_GAP) {
      console.log('[NavGuard] 拦截重复导航:', (options as any).url)
      return
    }
    lastCallTime = now
    // 调用失败时重置锁，确保不影响正常重试
    const originalFail = options.fail
    const wrappedOptions = {
      ...options,
      fail(err: any) {
        lastCallTime = 0
        console.error('[NavGuard] navigateTo 失败:', (options as any).url, err)
        if (originalFail) originalFail(err)
      }
    }
    originalNavigateTo(wrappedOptions)
  } as typeof uni.navigateTo
}

onLaunch(() => {
  console.log('[App] 小程序启动')
  installNavigationGuard()
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
