import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { getToken, saveAuth, clearAuth, getCurrentUser, type AuthUser } from '@/utils/auth'
import http from '@/api/request'

/**
 * 认证状态管理
 * <p>
 * 管理用户登录状态、角色信息和个人信息。
 * Token 通过 localStorage 持久化，页面刷新后自动恢复。
 * </p>
 */
export const useAuthStore = defineStore('auth', () => {
  const token = ref<string | null>(getToken())
  const user = ref<AuthUser | null>(getCurrentUser())
  const userInfo = ref<any>(null)

  /** 是否已登录 */
  const isLoggedIn = computed(() => !!token.value)

  /** 是否为管理员 */
  const isAdmin = computed(() => user.value?.roles?.some(r => r === 'ROLE_ADMIN' || r === 'ADMIN') ?? false)

  /** 是否为普通用户 */
  const isUser = computed(() => user.value?.roles?.some(r => r === 'ROLE_USER' || r === 'USER') ?? false)

  /** 登录 */
  async function login(phone: string, password: string): Promise<AuthUser> {
    const data: any = await http.post('/user/login', { phone, password })
    const authUser: AuthUser = {
      userId: data.userId,
      phone: data.phone,
      roles: data.roles || []
    }
    saveAuth(data.token, authUser)
    token.value = data.token
    user.value = authUser
    return authUser
  }

  /** 退出登录 */
  function logout() {
    clearAuth()
    token.value = null
    user.value = null
    userInfo.value = null
  }

  /** 获取用户个人信息 */
  async function fetchUserInfo() {
    try {
      userInfo.value = await http.get('/user/info')
    } catch {
      userInfo.value = null
    }
  }

  return { token, user, userInfo, isLoggedIn, isAdmin, isUser, login, logout, fetchUserInfo }
})
