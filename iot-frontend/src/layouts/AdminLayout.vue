<template>
  <div class="admin-layout">
    <!-- 侧边栏 -->
    <el-menu
      :default-active="activeMenu"
      :collapse="isCollapse"
      :router="true"
      class="admin-sidebar"
      background-color="#304156"
      text-color="#bfcbd9"
      active-text-color="#409EFF"
    >
      <!-- Logo -->
      <div class="sidebar-logo">
        <el-icon :size="24" color="#409EFF"><Monitor /></el-icon>
        <span v-show="!isCollapse" class="logo-text">IoT充电运营平台</span>
      </div>

      <el-menu-item index="/admin/dashboard">
        <el-icon><DataAnalysis /></el-icon>
        <template #title>运营大屏</template>
      </el-menu-item>

      <el-sub-menu index="business">
        <template #title>
          <el-icon><OfficeBuilding /></el-icon>
          <span>业务管理</span>
        </template>
        <el-menu-item index="/admin/station">充电站管理</el-menu-item>
        <el-menu-item index="/admin/charger">充电桩管理</el-menu-item>
        <el-menu-item index="/admin/order">订单管理</el-menu-item>
        <el-menu-item index="/admin/pricing">计费规则</el-menu-item>
      </el-sub-menu>

      <el-menu-item index="/admin/alarm">
        <el-icon><Bell /></el-icon>
        <template #title>告警管理</template>
      </el-menu-item>

      <el-sub-menu index="system">
        <template #title>
          <el-icon><Setting /></el-icon>
          <span>系统管理</span>
        </template>
        <el-menu-item index="/admin/system/user">用户管理</el-menu-item>
        <el-menu-item index="/admin/system/role">角色管理</el-menu-item>
        <el-menu-item index="/admin/system/log">操作日志</el-menu-item>
      </el-sub-menu>
    </el-menu>

    <!-- 右侧区域 -->
    <div class="admin-main">
      <!-- 顶栏 -->
      <header class="admin-header">
        <div class="header-left">
          <el-icon
            :size="22"
            class="collapse-btn"
            @click="isCollapse = !isCollapse"
          >
            <Fold v-if="!isCollapse" />
            <Expand v-else />
          </el-icon>
          <el-breadcrumb separator="/">
            <el-breadcrumb-item :to="{ path: '/admin/dashboard' }">首页</el-breadcrumb-item>
            <el-breadcrumb-item v-for="item in breadcrumbs" :key="item.path" :to="item.path ? { path: item.path } : undefined">
              {{ item.title }}
            </el-breadcrumb-item>
          </el-breadcrumb>
        </div>
        <div class="header-right">
          <span class="header-time">{{ currentTime }}</span>
          <el-tag
            :type="wsConnected ? 'success' : 'danger'"
            size="small"
            effect="dark"
          >
            {{ wsConnected ? 'WS 已连接' : 'WS 断开' }}
          </el-tag>
          <el-dropdown trigger="click" @command="handleCommand">
            <span class="user-info">
              <el-icon><UserFilled /></el-icon>
              <span>{{ authStore.user?.phone || '管理员' }}</span>
              <el-icon><ArrowDown /></el-icon>
            </span>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item command="profile">个人信息</el-dropdown-item>
                <el-dropdown-item command="logout" divided>退出登录</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </header>

      <!-- 内容区域 -->
      <main class="admin-content">
        <slot />
      </main>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessageBox } from 'element-plus'
import {
  Monitor, DataAnalysis, OfficeBuilding, Bell, Setting,
  Fold, Expand, UserFilled, ArrowDown
} from '@element-plus/icons-vue'
import { useAuthStore } from '@/stores/authStore'

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()

const isCollapse = ref(false)
const wsConnected = ref(false) // 后续集成 WebSocket 时更新

/** 当前激活的菜单项（用于高亮） */
const activeMenu = computed(() => route.path)

/** 当前页面标题 */
const pageTitle = computed(() => route.meta.title as string || '')

/** 动态面包屑 */
const breadcrumbMap: Record<string, string> = {
  station: '充电站管理', charger: '充电桩管理', order: '订单管理',
  pricing: '计费规则', alarm: '告警管理', system: '系统管理',
  user: '用户管理', role: '角色管理', log: '操作日志'
}
const breadcrumbs = computed(() => {
  const crumbs: { title: string; path: string }[] = []
  const parts = route.path.split('/').filter(Boolean)
  let currentPath = ''
  for (const part of parts) {
    if (part === 'admin' || part === 'dashboard') continue
    currentPath += '/' + part
    const title = route.meta.title as string || breadcrumbMap[part] || part
    const isLast = part === parts[parts.length - 1]
    crumbs.push({ title, path: isLast ? '' : currentPath })
  }
  return crumbs.length > 0 ? crumbs : [{ title: pageTitle.value || '管理', path: '' }]
})

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
  currentTime.value = new Date().toLocaleString('zh-CN', {
    year: 'numeric', month: '2-digit', day: '2-digit',
    hour: '2-digit', minute: '2-digit', second: '2-digit'
  })
}

/** 下拉菜单命令 */
function handleCommand(command: string) {
  if (command === 'logout') {
    ElMessageBox.confirm('确定要退出登录吗？', '退出确认', {
      confirmButtonText: '确定', cancelButtonText: '取消', type: 'warning'
    }).then(() => {
      authStore.logout()
      router.replace('/login')
    }).catch(() => {})
  }
}
</script>

<style scoped>
.admin-layout {
  display: flex;
  min-height: 100vh;
}

.admin-sidebar {
  width: 220px;
  min-height: 100vh;
  overflow-y: auto;
  border-right: none;
  transition: width 0.3s;
}
.admin-sidebar.el-menu--collapse {
  width: 64px;
}

.sidebar-logo {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 16px 20px;
  border-bottom: 1px solid rgba(255,255,255,0.05);
}
.logo-text {
  font-size: 15px;
  font-weight: 600;
  color: #fff;
  white-space: nowrap;
}

.admin-main {
  flex: 1;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.admin-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 0 20px;
  height: 56px;
  background: #fff;
  box-shadow: 0 1px 4px rgba(0,0,0,0.06);
  z-index: 5;
}
.header-left {
  display: flex;
  align-items: center;
  gap: 16px;
}
.collapse-btn {
  cursor: pointer;
  color: #606266;
}
.header-right {
  display: flex;
  align-items: center;
  gap: 16px;
}
.header-time {
  font-size: 13px;
  color: #909399;
  font-variant-numeric: tabular-nums;
}
.user-info {
  display: flex;
  align-items: center;
  gap: 6px;
  cursor: pointer;
  font-size: 14px;
  color: #303133;
}

.admin-content {
  flex: 1;
  padding: 20px;
  overflow-y: auto;
  background: #f0f2f5;
}
</style>
