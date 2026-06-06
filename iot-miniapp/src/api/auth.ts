import { http } from './request'
import type { LoginBO, LoginVO, UserVO } from '@/types/api'

/** 登录 */
export function loginApi(payload: LoginBO) {
  return http.post<LoginVO>('/user/login', payload)
}

/** 注册 */
export function registerApi(payload: LoginBO & { nickname?: string }) {
  return http.post<LoginVO>('/user/register', payload)
}

/** 获取当前用户信息 */
export function fetchUserInfoApi() {
  return http.get<UserVO>('/user/info')
}

/** 退出登录（前端清除 token，后端无需感知） */
export function logoutApi() {
  return http.post<void>('/user/logout')
}
