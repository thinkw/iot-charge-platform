<template>
  <div class="login-page">
    <div class="login-card">
      <!-- 头部 -->
      <div class="login-header">
        <el-icon :size="40" color="#409EFF"><Monitor /></el-icon>
        <h2>IoT充电桩智能运营平台</h2>
        <p class="login-subtitle">运营数据大屏 - 管理员登录</p>
      </div>

      <!-- 表单 -->
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
            placeholder="请输入管理员手机号"
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
          <el-button
            type="primary"
            native-type="submit"
            :loading="loading"
            class="login-btn"
          >
            {{ loading ? '登录中...' : '登 录' }}
          </el-button>
        </el-form-item>
      </el-form>

      <!-- 错误提示 -->
      <el-alert
        v-if="errorMsg"
        :title="errorMsg"
        type="error"
        show-icon
        :closable="true"
        @close="errorMsg = ''"
        class="login-error"
      />

      <!-- 测试账号提示 -->
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
  </div>
</template>

<script setup lang="ts">
import { ref, reactive } from 'vue'
import { useRouter } from 'vue-router'
import { Monitor, Phone, Lock } from '@element-plus/icons-vue'
import type { FormInstance, FormRules } from 'element-plus'
import axios from 'axios'
import { saveAuth, type AuthUser } from '@/utils/auth'

const router = useRouter()
const formRef = ref<FormInstance>()
const loading = ref(false)
const errorMsg = ref('')

/** 登录表单 */
const form = reactive({
  phone: '',
  password: ''
})

/** 表单校验规则 */
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

/** 处理登录 */
async function handleLogin() {
  // 校验表单
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return

  loading.value = true
  errorMsg.value = ''

  try {
    // 调用后端登录接口
    const response = await axios.post('/api/user/login', {
      phone: form.phone,
      password: form.password
    })

    // 后端返回 Result<LoginResponse>：{ code: 200, data: { token, userId, phone, roles } }
    const body = response.data
    if (body.code !== 200) {
      errorMsg.value = body.message || '登录失败'
      return
    }

    const loginData = body.data
    const user: AuthUser = {
      userId: loginData.userId,
      phone: loginData.phone,
      roles: loginData.roles || []
    }

    // 检查是否为管理员
    if (!user.roles.includes('ROLE_ADMIN') && !user.roles.includes('ADMIN')) {
      errorMsg.value = '此账号无管理员权限，请使用管理员账号登录'
      return
    }

    // 保存登录凭证
    saveAuth(loginData.token, user)

    // 跳转到大屏
    router.push('/dashboard')
  } catch (e: any) {
    if (e.response?.data?.message) {
      errorMsg.value = e.response.data.message
    } else if (e.message) {
      errorMsg.value = '网络错误：' + e.message
    } else {
      errorMsg.value = '登录请求失败，请检查网络连接'
    }
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.login-page {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, #e8f4fd 0%, #f0f5ff 50%, #f5f7fa 100%);
}

.login-card {
  width: 420px;
  padding: 40px;
  background: #ffffff;
  border-radius: 12px;
  box-shadow: 0 8px 40px rgba(0, 0, 0, 0.08);
}

.login-header {
  text-align: center;
  margin-bottom: 32px;
}

.login-header h2 {
  margin: 12px 0 4px;
  font-size: 20px;
  font-weight: 600;
  color: #303133;
}

.login-subtitle {
  font-size: 13px;
  color: #909399;
}

.login-btn {
  width: 100%;
}

.login-error {
  margin-bottom: 16px;
}

.test-hint {
  margin-top: 24px;
}
</style>
