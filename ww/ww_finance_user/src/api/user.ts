import { request } from '@/utils/request'

export interface SendCodeParams {
  mobile: string
}

export function sendCode(data: SendCodeParams): Promise<string> {
  return request<string>({
    url: '/user/sendCode',
    method: 'post',
    params: data,
  })
}

export interface RegisterParams {
  mobile: string
  code: string
  password: string
  passwordto: string
  userType: number
}

export function register(data: RegisterParams): Promise<null> {
  return request<null>({
    url: '/user/register',
    method: 'post',
    data,
  })
}

export interface LoginParams {
  mobile: string
  password: string
}

export function login(data: LoginParams): Promise<string> {
  return request<string>({
    url: '/user/login',
    method: 'post',
    data,
  })
}

export interface LoginByCodeParams {
  mobile: string
  code: string
}

export function loginByCode(data: LoginByCodeParams): Promise<string> {
  return request<string>({
    url: '/user/loginByCode',
    method: 'post',
    data,
  })
}

export function logout(): Promise<unknown> {
  return request({ url: '/user/logout', method: 'post' })
}

export interface UserInfo {
  id: number
  mobile: string
  name: string
  nickName: string
  headImg: string
  bindStatus: number
  borrowAuthStatus: number
  userType: number
  integral: number
  idCard: string
  email: string
}

export function getUserInfo(): Promise<UserInfo> {
  return request<UserInfo>({ url: '/user/info', method: 'get' })
}

export interface UpdateProfileParams {
  nickName?: string
  email?: string
  headImg?: string
}

export function updateProfile(data: UpdateProfileParams): Promise<null> {
  return request<null>({ url: '/user/profile', method: 'put', data })
}