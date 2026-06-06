<template>
  <div class="login-card">
    <div class="login-header">
      <el-icon :size="40" color="#409EFF"><Monitor /></el-icon>
      <h2>IoT充电桩智能运营平台</h2>
      <p class="login-subtitle">运营后台 — 管理员登录</p>
    </div>

    <el-form
      ref="formRef"
      :model="form"
      :rules="rules"
      label-position="top"
      size="large"
      @submit.prevent="handleLogin"
    >
      <el-form-item label="手机号" prop="phone">
        <el-input
          v-model="form.phone"
          placeholder="请输入手机号"
          :prefix-icon="Phone"
          maxlength="11"
        />
      </el-form-item>
      <el-form-item label="密码" prop="password">
        <el-input
          v-model="form.password"
          type="password"
          placeholder="请输入密码"
          :prefix-icon="Lock"
          show-password
          @keyup.enter="handleLogin"
        />
      </el-form-item>
      <el-form-item>
        <el-button type="primary" native-type="submit" :loading="loading" class="login-btn">
          {{ loading ? '登录中...' : '登 录' }}
        </el-button>
      </el-form-item>
    </el-form>

    <el-alert
      v-if="errorMsg"
      :title="errorMsg"
      type="error"
      show-icon
      :closable="true"
      @close="errorMsg = ''"
      class="login-error"
    />

    <div class="test-hint">
      <el-divider content-position="center">测试账号</el-divider>
      <el-descriptions :column="2" size="small" border>
        <el-descriptions-item label="管理员">13800000003</el-descriptions-item>
        <el-descriptions-item label="密码">123456</el-descriptions-item>
        <el-descriptions-item label="普通用户">13800000001</el-descriptions-item>
        <el-descriptions-item label="密码">123456</el-descriptions-item>
      </el-descriptions>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { Monitor, Phone, Lock } from '@element-plus/icons-vue'
import type { FormInstance, FormRules } from 'element-plus'
import { useAuthStore } from '@/stores/authStore'

const router = useRouter()
const route = useRoute()
const authStore = useAuthStore()

const formRef = ref<FormInstance>()
const loading = ref(false)
const errorMsg = ref('')

const form = reactive({ phone: '', password: '' })

const rules: FormRules = {
  phone: [
    { required: true, message: '请输入手机号', trigger: 'blur' },
    { pattern: /^1\d{10}$/, message: '请输入有效的11位手机号', trigger: 'blur' }
  ],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { min: 6, message: '密码至少6位', trigger: 'blur' }
  ]
}

async function handleLogin() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return

  loading.value = true
  errorMsg.value = ''

  try {
    await authStore.login(form.phone, form.password)

    // 检查管理员权限（统一通过 authStore.isAdmin，避免角色字符串硬编码）
    if (!authStore.isAdmin) {
      authStore.logout()
      errorMsg.value = '此账号无管理员权限，请使用管理员账号登录'
      return
    }

    // 登录成功 → 跳转目标页或大屏（仅允许站内相对路径，防止开放重定向 OWASP A01）
    const rawRedirect = (route.query.redirect as string) || '/admin/dashboard'
    const redirect = isSafeRedirect(rawRedirect) ? rawRedirect : '/admin/dashboard'
    router.push(redirect)
  } catch (e: any) {
    errorMsg.value = e.message || '登录请求失败，请检查网络连接'
  } finally {
    loading.value = false
  }
}

/**
 * 校验 redirect 是否为站内相对路径
 * 仅允许以 "/" 开头的相对路径，禁止 // 开头（协议相对 URL 跳转外站）和绝对 URL
 */
function isSafeRedirect(path: string): boolean {
  if (typeof path !== 'string' || path.length === 0) return false
  if (path.startsWith('//')) return false              // //evil.com → 协议相对
  if (/^[a-zA-Z][a-zA-Z\d+\-.]*:/.test(path)) return false  // http:/https:/javascript: 等
  return path.startsWith('/')
}
</script>

<style scoped>
.login-card {
  width: 420px;
  padding: 40px;
  background: #ffffff;
  border-radius: 12px;
  box-shadow: 0 8px 40px rgba(0, 0, 0, 0.08);
}
.login-header { text-align: center; margin-bottom: 32px; }
.login-header h2 { margin: 12px 0 4px; font-size: 20px; font-weight: 600; color: #303133; }
.login-subtitle { font-size: 13px; color: #909399; }
.login-btn { width: 100%; }
.login-error { margin-bottom: 16px; }
.test-hint { margin-top: 24px; }
</style>
