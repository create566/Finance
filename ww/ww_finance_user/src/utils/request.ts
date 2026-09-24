import axios, { type AxiosInstance, type AxiosRequestConfig, type InternalAxiosRequestConfig } from 'axios'
import { ElMessage } from 'element-plus'

export interface ApiResult<T = unknown> {
  code: number
  data: T
  msg?: string
  message?: string
}

function getToken(): string | null {
  const match = document.cookie.match(/(?:^|;\s*)user_token=([^;]*)/)
  return match ? decodeURIComponent(match[1]) : null
}

const service: AxiosInstance = axios.create({
  baseURL: '/api',
  timeout: 15000,
  withCredentials: true,
})

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

service.interceptors.response.use(
  ((response: any) => {
    if (response.config?.responseType === 'text') {
      return response.data
    }
    const contentType = response.headers?.['content-type'] || ''
    if (contentType.includes('text/html') || typeof response.data === 'string') {
      return response.data
    }

    const res = response.data as ApiResult
    if (res && res.code === 200) {
      return res.data as unknown
    }
    ElMessage.error(res?.msg || res?.message || '请求失败')
    if (res?.code === 401 || res?.code === 403) {
      document.cookie = 'user_token=; expires=Thu, 01 Jan 1970 00:00:00 GMT; path=/'
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

export function request<T = unknown>(config: AxiosRequestConfig): Promise<T> {
  return service.request(config) as unknown as Promise<T>
}

export default service