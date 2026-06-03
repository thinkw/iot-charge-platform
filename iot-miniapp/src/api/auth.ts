import { http } from './request'

/** 登录 */
export function loginApi(phone: string, password: string) {
  return http.post<any>('/user/login', { phone, password })
}

/** 注册 */
export function registerApi(phone: string, password: string, nickname?: string) {
  return http.post<any>('/user/register', { phone, password, nickname })
}

/** 获取用户信息 */
export function getUserInfoApi() {
  return http.get<any>('/user/info')
}
