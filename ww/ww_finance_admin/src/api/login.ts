import { request } from '@/utils/request'

// 管理员登录参数
export interface LoginParams {
  username: string
  password: string
}

/**
 * 管理员登录
 * 后端：POST /admin/core/login
 * 请求：{ "username": "admin", "password": "123456" }
 * 响应：{ "code": 200, "data": "token字符串", "msg": "登录成功" }
 */
export function login(data: LoginParams): Promise<string> {
  return request<string>({
    url: '/login',
    method: 'post',
    data,
  })
}

/**
 * 退出登录
 * 后端：GET /admin/core/logout
 */
export function logout(): Promise<unknown> {
  return request({ url: '/logout', method: 'get' })
}

/**
 * 获取当前管理员信息
 * 后端：GET /admin/core/userInfo
 */
export interface AdminUserInfo {
  id: number
  username: string
  nickname?: string
  avatar?: string
}

export function getUserInfo(): Promise<AdminUserInfo> {
  return request<AdminUserInfo>({ url: '/userInfo', method: 'get' })
}
