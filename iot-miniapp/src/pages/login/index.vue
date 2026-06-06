<template>
  <view class="login-page">
    <view class="login-card">
      <view class="login-header">
        <text class="app-title">⚡ 充电桩智能运营平台</text>
        <text class="app-subtitle">用户端</text>
      </view>

      <view class="login-form">
        <text class="form-label">手机号</text>
        <input
          v-model="phone"
          type="text"
          inputmode="numeric"
          maxlength="11"
          placeholder="请输入手机号"
          class="login-input"
        />

        <text class="form-label">密码</text>
        <input
          v-model="password"
          type="password"
          placeholder="请输入密码"
          class="login-input"
          @confirm="handleLogin"
        />

        <button class="login-btn" :loading="loading" @tap="handleLogin">
          {{ loading ? '登录中...' : '登 录' }}
        </button>

        <view class="switch-mode" @tap="showRegister = !showRegister">
          {{ showRegister ? '已有账号？去登录' : '没有账号？去注册' }}
        </view>
      </view>

      <view v-if="errorMsg" class="error-msg">{{ errorMsg }}</view>

      <!-- 注册额外字段 -->
      <view v-if="showRegister" class="register-extra">
        <text class="form-label">昵称（选填）</text>
        <input v-model="nickname" placeholder="给自己起个昵称" class="login-input" />
      </view>

      <view class="test-hint">
        <text class="hint-title">测试账号</text>
        <text>普通用户: 13800000001 / 123456</text>
        <text>管理员: 13800000003 / 123456</text>
      </view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { useAuthStore } from '@/stores/authStore'

const authStore = useAuthStore()
const phone = ref('')
const password = ref('')
const nickname = ref('')
const loading = ref(false)
const errorMsg = ref('')
const showRegister = ref(false)

async function handleLogin() {
  if (!phone.value || !password.value) {
    errorMsg.value = '请输入手机号和密码'
    return
  }
  loading.value = true
  errorMsg.value = ''
  try {
    if (showRegister.value) {
      await authStore.register(phone.value, password.value, nickname.value || undefined)
    } else {
      await authStore.login(phone.value, password.value)
    }
    uni.switchTab({ url: '/pages/station-list/index' })
  } catch (e: any) {
    errorMsg.value = e.message || '操作失败'
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.login-page { min-height:100vh; display:flex; align-items:center; justify-content:center; background:linear-gradient(135deg,#e8f4fd,#f0f5ff); padding:40rpx; }
.login-card { width:100%; max-width:640rpx; background:#fff; border-radius:16rpx; padding:48rpx; box-shadow:0 8rpx 40rpx rgba(0,0,0,0.06); }
.login-header { text-align:center; margin-bottom:40rpx; }
.app-title { font-size:36rpx; font-weight:600; color:#303133; display:block; }
.app-subtitle { font-size:26rpx; color:#909399; margin-top:8rpx; }
.form-label { font-size:28rpx; color:#606266; display:block; margin-bottom:8rpx; margin-top:24rpx; }
.login-input { width:100%; height:88rpx; border:1rpx solid #dcdfe6; border-radius:8rpx; padding:0 24rpx; font-size:28rpx; box-sizing:border-box; }
.login-btn { width:100%; margin-top:40rpx; background:#409EFF; color:#fff; border:none; height:88rpx; line-height:88rpx; border-radius:8rpx; font-size:30rpx; }
.switch-mode { text-align:center; margin-top:20rpx; color:#409EFF; font-size:26rpx; }
.error-msg { background:#fef0f0; color:#F56C6C; padding:16rpx; border-radius:8rpx; margin-top:24rpx; font-size:26rpx; }
.register-extra { margin-top:8rpx; }
.test-hint { margin-top:40rpx; padding:24rpx; background:#f5f7fa; border-radius:8rpx; }
.hint-title { font-size:26rpx; color:#909399; display:block; margin-bottom:8rpx; }
.test-hint text { display:block; font-size:24rpx; color:#909399; line-height:36rpx; }
</style>
