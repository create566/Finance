import axios, { type AxiosInstance, type AxiosRequestConfig, type InternalAxiosRequestConfig } from 'axios'
import { ElMessage } from 'element-plus'

// 后端统一响应结构：{ code: 200, data: ..., msg: ... }
export interface ApiResult<T = unknown> {
  code: number
  data: T
  msg?: string
  message?: string
}

// 从 cookie 读取 token（和前台 ww-front 保持一致）
function getToken(): string | null {
  const match = document.cookie.match(/(?:^|;\s*)admin_token=([^;]*)/)
  return match ? decodeURIComponent(match[1]) : null
}

// 创建 axios 实例
// 后台 API 规范：/admin/core/**  （/api/** 是前台用的）
const service: AxiosInstance = axios.create({
  baseURL: '/admin/core',
  timeout: 15000,
  withCredentials: true,
})

// 请求拦截器：自动带 token
service.interceptors.request.use(
  (config: InternalAxiosRequestConfig) => {
    const token = getToken()
    if (token) {
      config.headers.set('token', token)
    }
    return config
  },
  (error) => Promise.reject(error),
)

// 响应拦截器：解包 + 错误提示
service.interceptors.response.use(
  ((response: any) => {
    const res = response.data as ApiResult
    if (res && res.code === 200) {
      // Bug#3 修复：直接返回后端 data，让业务层泛型 request<T> 声明准确类型
      return res.data as unknown
    }
    // 业务错误
    ElMessage.error(res?.msg || res?.message || '请求失败')
    // 未登录或 token 失效
    if (res?.code === 401) {
      document.cookie = 'admin_token=; expires=Thu, 01 Jan 1970 00:00:00 GMT; path=/'
      if (!window.location.pathname.startsWith('/login')) {
        window.location.href = '/login'
      }
    }
    return Promise.reject(new Error(res?.msg || res?.message || 'Error'))
  }) as any,
  (error) => {
    if (error.response) {
      const data = error.response.data as ApiResult
      ElMessage.error(data?.msg || data?.message || `请求错误(${error.response.status})`)
    } else if (error.message?.includes('Network Error')) {
      ElMessage.error('无法连接到后端服务，请确认后端已启动')
    } else {
      ElMessage.error(error.message || '网络错误')
    }
    return Promise.reject(error)
  },
)

// 泛型请求封装（业务层拿到的就是 data 本身，不用再解包）
export function request<T = unknown>(config: AxiosRequestConfig): Promise<T> {
  return service.request(config) as unknown as Promise<T>
}

// AI模块请求实例（baseURL: /ai/admin）
const aiService: AxiosInstance = axios.create({
  baseURL: '/ai/admin',
  timeout: 15000,
  withCredentials: true,
})

aiService.interceptors.request.use(
  (config: InternalAxiosRequestConfig) => {
    const token = getToken()
    if (token) {
      config.headers.set('token', token)
    }
    return config
  },
  (error) => Promise.reject(error),
)

aiService.interceptors.response.use(
  ((response: any) => {
    const res = response.data as ApiResult
    if (res && res.code === 200) {
      return res.data as unknown
    }
    ElMessage.error(res?.msg || res?.message || '请求失败')
    if (res?.code === 401) {
      document.cookie = 'admin_token=; expires=Thu, 01 Jan 1970 00:00:00 GMT; path=/'
      if (!window.location.pathname.startsWith('/login')) {
        window.location.href = '/login'
      }
    }
    return Promise.reject(new Error(res?.msg || res?.message || 'Error'))
  }) as any,
  (error) => {
    if (error.response) {
      const data = error.response.data as ApiResult
      ElMessage.error(data?.msg || data?.message || `请求错误(${error.response.status})`)
    } else if (error.message?.includes('Network Error')) {
      ElMessage.error('无法连接到AI后端服务，请确认AI模块已启动')
    } else {
      ElMessage.error(error.message || '网络错误')
    }
    return Promise.reject(error)
  },
)

export function aiRequest<T = unknown>(config: AxiosRequestConfig): Promise<T> {
  return aiService.request(config) as unknown as Promise<T>
}

export default service