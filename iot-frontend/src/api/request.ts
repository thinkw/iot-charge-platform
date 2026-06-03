import axios from 'axios'
import { getToken, clearAuth } from '@/utils/auth'
import { ElMessage } from 'element-plus'

/** 延迟加载 router 以避免循环依赖（request.ts → router → guards → authStore → request.ts） */
function navigateToLogin() {
  import('@/router').then(m => m.default.push('/login'))
}

/**
 * 共享 axios 实例
 * <p>
 * 所有 API 模块通过此实例发送请求，统一处理：
 * - 请求拦截：自动注入 JWT Token
 * - 响应拦截：自动解包 Result.data + 401 跳转登录 + 统一错误提示
 * </p>
 */
const http = axios.create({
  baseURL: '/api',
  timeout: 15000,
  headers: { 'Content-Type': 'application/json' }
})

/** 请求拦截器：注入 Token */
http.interceptors.request.use(
  (config) => {
    const token = getToken()
    if (token) {
      config.headers.Authorization = `Bearer ${token}`
    }
    return config
  },
  (error) => Promise.reject(error)
)

/** 响应拦截器：解包 Result + 统一错误处理 */
http.interceptors.response.use(
  (response) => {
    const body = response.data
    // 如果响应体符合 Result 格式（有 code 字段），则解包
    if (body && typeof body.code === 'number') {
      if (body.code === 200) {
        return body.data
      }
      // 401 未认证 → 跳转登录
      if (body.code === 401) {
        clearAuth()
        navigateToLogin()
        return Promise.reject(new Error('登录已过期，请重新登录'))
      }
      // 其他业务错误
      ElMessage.error(body.message || '请求失败')
      return Promise.reject(new Error(body.message || '请求失败'))
    }
    // 非 Result 格式（如下载文件等），直接返回
    return body
  },
  (error) => {
    if (error.response?.status === 401) {
      clearAuth()
      navigateToLogin()
    } else if (error.response?.data?.message) {
      ElMessage.error(error.response.data.message)
    } else if (error.message) {
      ElMessage.error(error.message)
    }
    return Promise.reject(error)
  }
)

export default http
