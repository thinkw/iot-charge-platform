import { createRouter, createWebHistory, RouteRecordRaw } from 'vue-router'
import { isLoggedIn } from '@/utils/auth'

const routes: RouteRecordRaw[] = [
  {
    path: '/',
    redirect: '/dashboard'
  },
  {
    path: '/login',
    name: 'Login',
    component: () => import('@/views/login/LoginView.vue'),
    meta: { title: '管理员登录' }
  },
  {
    path: '/dashboard',
    name: 'Dashboard',
    component: () => import('@/views/dashboard/DashboardView.vue'),
    meta: { title: '实时数据大屏', requiresAuth: true }
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

// 路由守卫
router.beforeEach((to, from, next) => {
  // 设置页面标题
  document.title = (to.meta.title as string) || 'IoT充电桩智能运营平台'

  // 需要认证的页面 → 检查是否已登录
  if (to.meta.requiresAuth && !isLoggedIn()) {
    next({ name: 'Login', query: { redirect: to.fullPath } })
    return
  }

  // 已登录用户访问登录页 → 直接跳转大屏
  if (to.name === 'Login' && isLoggedIn()) {
    next({ name: 'Dashboard' })
    return
  }

  next()
})

export default router
