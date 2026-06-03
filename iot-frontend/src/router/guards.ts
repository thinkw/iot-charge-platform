import type { Router } from 'vue-router'
import { useAuthStore } from '@/stores/authStore'

/**
 * 注册导航守卫
 * <p>
 * 1. 页面标题设置
 * 2. 认证检查：需要认证的页面 → 未登录跳转 /login
 * 3. 角色检查：检查用户角色是否匹配路由 meta.roles
 * 4. 登录页防呆：已登录用户访问 /login → 按角色跳转
 * 5. 根路径重定向
 * </p>
 */
export function registerGuards(router: Router) {
  router.beforeEach((to, from, next) => {
    // 设置页面标题
    document.title = (to.meta.title as string) || 'IoT充电桩智能运营平台'

    // 需要从 Pinia 获取状态（放在 beforeEach 内确保已初始化）
    const authStore = useAuthStore()

    const requiresAuth = to.meta.roles && (to.meta.roles as string[]).length > 0
    const guestOnly = to.meta.guestOnly === true

    // 需要认证但未登录 → 跳转登录页
    if (requiresAuth && !authStore.isLoggedIn) {
      next({ name: 'Login', query: { redirect: to.fullPath } })
      return
    }

    // 需要认证但角色不匹配 → 跳转 Dashboard
    if (requiresAuth && authStore.isLoggedIn) {
      const allowedRoles = to.meta.roles as string[]
      const userRoles = authStore.user?.roles || []
      const hasRole = allowedRoles.some(r => userRoles.includes(r))
      if (!hasRole && allowedRoles.length > 0) {
        next({ name: 'AdminDashboard' })
        return
      }
    }

    // 仅游客页面（如登录页），已登录用户访问 → 跳转管理端
    if (guestOnly && authStore.isLoggedIn) {
      next({ name: 'AdminDashboard' })
      return
    }

    next()
  })
}
