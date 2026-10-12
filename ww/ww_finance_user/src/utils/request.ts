import axios, { type AxiosInstance, type AxiosRequestConfig, type InternalAxiosRequestConfig } from 'axios'
import { ElMessage } from 'element-plus'
import { revokeEntry } from '@/utils/entryGuard'

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

/** 登录态失效：清 cookie、吊销入口通行证，并跳登录页 */
function redirectToLogin() {
  document.cookie = 'user_token=; expires=Thu, 01 Jan 1970 00:00:00 GMT; path=/'
  // 通行证必须一起吊销：否则 token 过期后用户仍能凭旧通行证在页面间游走
  revokeEntry()
  if (!window.location.pathname.startsWith('/login')) {
    window.location.href = '/login'
  }
}

/**
 * 从响应体里识别后端的「业务失败信封」。
 *
 * 背景：充值 / 提现 / 还款 / 投标 这几个接口用的是 responseType: 'text'，
 * 因为成功时后端返回的是要自动提交到银行的 HTML 表单。
 * 但业务校验失败时（例如余额不足），后端走 @RestControllerAdvice 返回的是
 * `{"code":400,"msg":"可用余额不足…"}` —— **HTTP 状态码依然是 200**。
 * 如果不在这里识别出来，调用方只会得到一个普通字符串，
 * 判断「不含 <form>」后就会走到"提交成功"的分支：余额不足被静默吞成成功提示，
 * 这是"金额不够也能支付/还款"能骗过测试的根因。
 */
function parseErrorEnvelope(payload: unknown): ApiResult | null {
  let obj: any = payload
  if (typeof payload === 'string') {
    const text = payload.trim()
    // 银行表单是 HTML，不可能以 { 或 [ 开头，先做廉价判断避免无谓的 JSON.parse
    if (!text.startsWith('{') && !text.startsWith('[')) {
      return null
    }
    try {
      obj = JSON.parse(text)
    } catch {
      return null
    }
  }
  if (obj && typeof obj === 'object' && typeof obj.code === 'number' && obj.code !== 200) {
    return obj as ApiResult
  }
  return null
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
    const isText = response.config?.responseType === 'text'
    const contentType = response.headers?.['content-type'] || ''
    const isHtmlOrText =
      isText || contentType.includes('text/html') || typeof response.data === 'string'

    if (isHtmlOrText) {
      const envelope = parseErrorEnvelope(response.data)
      if (envelope) {
        ElMessage.error(envelope.msg || envelope.message || '请求失败')
        if (envelope.code === 401 || envelope.code === 403) {
          redirectToLogin()
        }
        return Promise.reject(new Error(envelope.msg || envelope.message || 'Error'))
      }
      return response.data
    }

    const res = response.data as ApiResult
    if (res && res.code === 200) {
      return res.data as unknown
    }
    ElMessage.error(res?.msg || res?.message || '请求失败')
    if (res?.code === 401 || res?.code === 403) {
      redirectToLogin()
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