/**
 * uni-app 请求封装
 * <p>
 * 封装 uni.request，统一处理：
 * - JWT Token 注入（从 storage 读取）
 * - Result.data 解包
 * - 401 自动跳转登录页
 * - 统一错误提示
 * </p>
 */

const BASE_URL = 'http://localhost:8080/api'

/** 通用请求方法 */
function request<T = any>(method: 'GET' | 'POST' | 'PUT' | 'DELETE', path: string, data?: any): Promise<T> {
  return new Promise((resolve, reject) => {
    const token = uni.getStorageSync('token') || ''

    uni.request({
      url: BASE_URL + path,
      method,
      data,
      header: {
        'Content-Type': 'application/json',
        'Authorization': token ? `Bearer ${token}` : ''
      },
      timeout: 5000,
      success: (res: any) => {
        const body = res.data
        if (res.statusCode === 401) {
          uni.removeStorageSync('token')
          uni.removeStorageSync('user')
          uni.reLaunch({ url: '/pages/login/index' })
          reject(new Error('登录已过期'))
          return
        }
        if (body && typeof body.code === 'number') {
          if (body.code === 200) {
            resolve(body.data as T)
            return
          }
          // GET 请求不弹 toast（页面自动加载，已由页面 catch 处理）
          if (method !== 'GET') {
            uni.showToast({ title: body.message || '请求失败', icon: 'none' })
          }
          reject(new Error(body.message || '请求失败'))
          return
        }
        resolve(body as T)
      },
      fail: (err) => {
        console.warn('[API] 请求失败:', path, err)
        // 仅在非 GET 请求时显示 toast，避免页面自动加载时频繁弹提示
        if (method !== 'GET') {
          uni.showToast({ title: '网络连接失败', icon: 'none' })
        }
        reject(err)
      }
    })
  })
}

export const http = {
  get<T = any>(path: string, params?: Record<string, any>): Promise<T> {
    const query = params ? '?' + Object.entries(params)
      .filter(([, v]) => v !== undefined && v !== null && v !== '')
      .map(([k, v]) => `${k}=${encodeURIComponent(v as string)}`)
      .join('&') : ''
    return request('GET', path + query)
  },
  post<T = any>(path: string, data?: any): Promise<T> {
    return request('POST', path, data)
  },
  put<T = any>(path: string, data?: any): Promise<T> {
    return request('PUT', path, data)
  },
  del<T = any>(path: string): Promise<T> {
    return request('DELETE', path)
  }
}
