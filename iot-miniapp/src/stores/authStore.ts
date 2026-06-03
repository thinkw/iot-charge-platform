import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { loginApi, registerApi, getUserInfoApi } from '@/api/auth'

/**
 * 认证状态管理
 * <p>
 * 管理用户登录状态和 Token 持久化。
 * Token 通过 uni.storage 存储，页面关闭后不丢失。
 * </p>
 */
export const useAuthStore = defineStore('auth', () => {
  const token = ref<string>(uni.getStorageSync('token') || '')
  const user = ref<any>(uni.getStorageSync('user') ? JSON.parse(uni.getStorageSync('user')) : null)
  const userInfo = ref<any>(null)

  const isLoggedIn = computed(() => !!token.value)

  /** 登录 */
  async function login(phone: string, password: string) {
    const data = await loginApi(phone, password)
    saveLoginState(data)
  }

  /** 注册 */
  async function register(phone: string, password: string, nickname?: string) {
    const data = await registerApi(phone, password, nickname)
    saveLoginState(data)
  }

  /** 保存登录状态 */
  function saveLoginState(data: any) {
    token.value = data.token
    user.value = { userId: data.userId, phone: data.phone, roles: data.roles || [] }
    uni.setStorageSync('token', data.token)
    uni.setStorageSync('user', JSON.stringify(user.value))
  }

  /** 退出登录 */
  function logout() {
    token.value = ''
    user.value = null
    userInfo.value = null
    uni.removeStorageSync('token')
    uni.removeStorageSync('user')
    uni.reLaunch({ url: '/pages/login/index' })
  }

  /** 获取用户信息 */
  async function fetchUserInfo() {
    if (!isLoggedIn.value) return
    try { userInfo.value = await getUserInfoApi() } catch { /* ignore */ }
  }

  return { token, user, userInfo, isLoggedIn, login, register, logout, fetchUserInfo }
})
