/**
 * JWT Token 管理工具
 * <p>
 * 负责 Token 的本地存储（localStorage）、读取和清除。
 * 登录后保存 token + userId + roles，退出时清除。
 * </p>
 */

const TOKEN_KEY = 'iot_admin_token'
const USER_KEY = 'iot_admin_user'

/** 用户信息 */
export interface AuthUser {
  userId: number
  phone: string
  roles: string[]
}

/** 保存登录凭证 */
export function saveAuth(token: string, user: AuthUser): void {
  localStorage.setItem(TOKEN_KEY, token)
  localStorage.setItem(USER_KEY, JSON.stringify(user))
}

/** 获取 Token */
export function getToken(): string | null {
  return localStorage.getItem(TOKEN_KEY)
}

/** 获取当前用户信息 */
export function getCurrentUser(): AuthUser | null {
  const raw = localStorage.getItem(USER_KEY)
  if (!raw) return null
  try {
    return JSON.parse(raw) as AuthUser
  } catch {
    return null
  }
}

/** 是否已登录 */
export function isLoggedIn(): boolean {
  return getToken() !== null
}

/** 清除登录凭证（退出登录） */
export function clearAuth(): void {
  localStorage.removeItem(TOKEN_KEY)
  localStorage.removeItem(USER_KEY)
}
