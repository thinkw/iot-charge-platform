<template>
  <div class="dashboard-layout">
    <!-- 顶部标题栏 -->
    <header class="layout-header">
      <div class="header-left">
        <el-icon :size="28" color="#409EFF"><Monitor /></el-icon>
        <h1 class="header-title">IoT充电桩智能运营平台</h1>
        <el-tag type="info" size="small" class="header-subtitle">实时数据大屏</el-tag>
      </div>
      <div class="header-right">
        <span class="header-time">{{ currentTime }}</span>
        <!-- 使用 prop 传入的 wsConnected，而非本地 ref -->
        <el-tag :type="wsConnected ? 'success' : 'danger'" size="small" effect="dark">
          {{ wsConnected ? 'WS 已连接' : 'WS 断开' }}
        </el-tag>
        <el-button
          type="danger"
          size="small"
          :icon="SwitchButton"
          plain
          @click="handleLogout"
        >
          退出登录
        </el-button>
      </div>
    </header>

    <!-- 内容区域 -->
    <main class="layout-content">
      <slot />
    </main>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, onUnmounted } from 'vue'
import { Monitor, SwitchButton } from '@element-plus/icons-vue'
import { ElMessageBox } from 'element-plus'
import { useAuthStore } from '@/stores/authStore'
import { useRouter } from 'vue-router'

/** 接收父组件传入的 WebSocket 连接状态 */
defineProps<{
  wsConnected: boolean
}>()

const emit = defineEmits<{
  (e: 'logout'): void
}>()

const router = useRouter()
const authStore = useAuthStore()

/** 当前时间（每秒刷新） */
const currentTime = ref('')
let timer: ReturnType<typeof setInterval> | null = null

onMounted(() => {
  updateTime()
  timer = setInterval(updateTime, 1000)
})

onUnmounted(() => {
  if (timer) clearInterval(timer)
})

function updateTime() {
  const now = new Date()
  currentTime.value = now.toLocaleString('zh-CN', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit'
  })
}

/** 退出登录确认 */
function handleLogout() {
  ElMessageBox.confirm(
    '确定要退出登录吗？',
    '退出确认',
    {
      confirmButtonText: '确定退出',
      cancelButtonText: '取消',
      type: 'warning'
    }
  ).then(() => {
    authStore.logout()
    router.replace('/login')
  }).catch(() => {
    // 用户取消，不做任何操作
  })
}
</script>

<style scoped>
.dashboard-layout {
  min-height: 100vh;
  display: flex;
  flex-direction: column;
  background: linear-gradient(135deg, #f0f5ff 0%, #f5f7fa 50%, #fafbfc 100%);
}

.layout-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 12px 24px;
  background: #ffffff;
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.06);
  z-index: 10;
}

.header-left {
  display: flex;
  align-items: center;
  gap: 12px;
}

.header-title {
  font-size: 20px;
  font-weight: 600;
  color: #303133;
  margin: 0;
}

.header-subtitle {
  margin-left: 4px;
}

.header-right {
  display: flex;
  align-items: center;
  gap: 16px;
}

.header-time {
  font-size: 14px;
  color: #606266;
  font-variant-numeric: tabular-nums;
}

.layout-content {
  flex: 1;
  padding: 20px;
  overflow-y: auto;
}
</style>
