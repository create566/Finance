import axios, { type AxiosInstance, type InternalAxiosRequestConfig } from 'axios'
import { revokeEntry } from '@/utils/entryGuard'

// 从 cookie 读取用户登录 token（与 src/utils/request.ts 同一份数据）
function getUserToken(): string | null {
  const match = document.cookie.match(/(?:^|;\s*)user_token=([^;]*)/)
  return match ? decodeURIComponent(match[1]) : null
}

const aiRequest: AxiosInstance = axios.create({
  baseURL: '/ai',
  timeout: 60000,
})

// AI 服务已启用真实登录校验：必须携带 user_token，
// 后端从 token 解出 userId，不再信任请求体里自报的 userId
aiRequest.interceptors.request.use((config: InternalAxiosRequestConfig) => {
  const token = getUserToken()
  if (token) {
    config.headers.set('token', token)
  }
  return config
})

aiRequest.interceptors.response.use(
  (response) => {
    const res = response.data
    if (res && res.code === 401) {
      // 未登录/登录过期：清 token、吊销入口通行证，回登录页（与 utils/request.ts 行为一致）
      document.cookie = 'user_token=; expires=Thu, 01 Jan 1970 00:00:00 GMT; path=/'
      revokeEntry()
      if (!window.location.pathname.startsWith('/login')) {
        window.location.href = '/login'
      }
    }
    return response
  },
  (error) => Promise.reject(error),
)

export interface ChatMessage {
  id?: number
  sessionId?: number
  userId?: number
  role: string
  content: string
  chunkIds?: string
  createTime?: string
}

export interface KnowledgeBase {
  id?: number
  name?: string
  description?: string
  docCount?: number
  chunkCount?: number
  status?: number
}

// ========== AI模块：会话/消息 ==========

/**
 * 解开 Result 信封：AI 模块的业务失败也是 **HTTP 200 + code!==200**，
 * 而 aiRequest 的响应拦截器只处理 401 —— 不在这里显式抛错的话，
 * 调用方的 try/catch 永远不触发，界面会显示"成功"或干脆毫无反应。
 */
function unwrap<T>(body: any): T {
  if (body && typeof body === 'object' && typeof body.code === 'number' && body.code !== 200) {
    throw new Error(body.msg || `请求失败(code ${body.code})`)
  }
  return (body?.data ?? null) as T
}

/**
 * 结束会话（status → 1）。
 *
 * <p>走 AI 模块 —— 会话数据在 Mongo，关闭必须落在这里。</p>
 *
 * <p><b>2026-10-08 修复</b>：原实现调 `/core/chat/session/{id}/close`
 * （api 的 MySQL 遗留链路），但传入的是 Mongo 的 sessionId，
 * 且该表 54 行全属 user_id=1 ⇒ 真实用户恒失败（实测 code 500），
 * 加上页面把错误当成"用户取消"吞掉，表现为「点了确定毫无反应」。</p>
 */
export async function closeSession(sessionId: number): Promise<null> {
  const res = await aiRequest.put(`/sessions/${sessionId}/close`)
  return unwrap<null>(res.data)
}

// ========== AI模块：同步问答 ==========

export function askAI(data: { question: string; sessionId?: number; userId?: number }): Promise<{ answer: string; sessionId: number }> {
  // aiRequest 是裸 axios（无拦截器），后端返回 Result 信封 {code,msg,data}，
  // 必须多解一层 res.data.data，否则 result.answer 永远是 undefined
  return aiRequest.post('/chat-sync', data).then(res => res.data.data)
}

// 知识库问答：后端 /chat-sync 的 ReAct 流程内部会检索知识库；
// knowledgeBaseId 透传给后端（当前后端忽略该参数，保留接口以备扩展）
export function askKnowledgeAI(data: { question: string; knowledgeBaseId: number; sessionId?: number }): Promise<{ answer: string; sessionId: number }> {
  return aiRequest.post('/chat-sync', data).then(res => res.data.data)
}

// ========== AI模块：知识库 ==========

export function getKnowledgeBases(): Promise<KnowledgeBase[]> {
  // 走公共只读接口 /ai/knowledge/base/list（用户端无管理端token，不能调 /ai/admin/ 鉴权接口）
  return aiRequest.get('/knowledge/base/list').then(res => {
    const d = res.data?.data
    // 兼容数组与 {list: [...]} 两种返回形态
    return Array.isArray(d) ? d : (d?.list ?? [])
  })
}

// ========== AI模块：聊天历史 ==========

export function getChatHistory(sessionId: number): Promise<{ sessionId: number; messages: ChatMessage[]; total: number }> {
  return aiRequest.get(`/chat-history/${sessionId}`).then(res => res.data.data)
}

export interface ChatSessionItem {
  id: number
  userId?: number
  title?: string
  messageCount?: number
  status?: number
  createTime?: string
  updateTime?: string
}

// 当前用户的历史会话列表（按 updateTime 倒序），供"历史会话"侧边栏使用。
// userId 由后端从登录 token 解出，前端不再传（此前写死 1 导致所有用户共享历史）
export function getAiSessions(): Promise<{ list: ChatSessionItem[]; total: number }> {
  return aiRequest.get('/sessions').then(res => res.data.data)
}

// 删除当前用户的一个历史会话（连同消息一起删除）
// 用 unwrap：业务失败必须抛错，否则调用方的 catch 不触发、界面会误报"已删除"
export async function deleteAiSession(sessionId: number): Promise<null> {
  const res = await aiRequest.delete(`/sessions/${sessionId}`)
  return unwrap<null>(res.data)
}

// ========== AI模块：SSE 流式问答 ==========

export interface StreamCallbacks {
  onToken: (token: string) => void
  onDone: (sessionId?: number) => void
  onError: (err: Error) => void
}

let streamAbortController: AbortController | null = null

export function abortStream() {
  if (streamAbortController) {
    streamAbortController.abort()
    streamAbortController = null
  }
}

export function askAIStream(
  params: { prompt: string; chatId?: string; sessionId?: number; knowledgeBaseId?: number },
  callbacks: StreamCallbacks,
): void {
  abortStream()
  streamAbortController = new AbortController()

  const url = new URL('/ai/chat-stream', window.location.origin)
  url.searchParams.set('prompt', params.prompt)
  if (params.chatId) {
    url.searchParams.set('chatId', params.chatId)
  }
  // 优先带 sessionId：后端据此复用既有会话（Mongo）与同名上下文记忆("session-{id}")，
  // 避免像之前那样每条消息都新建一个会话、记忆与历史列表对不上
  if (params.sessionId) {
    url.searchParams.set('sessionId', String(params.sessionId))
  }
  // 选中的知识库 id（可选）：后端检索时按 metadata knowledgeBaseId 过滤
  if (params.knowledgeBaseId) {
    url.searchParams.set('knowledgeBaseId', String(params.knowledgeBaseId))
  }

  const token = getUserToken()
  fetch(url.toString(), {
    signal: streamAbortController.signal,
    headers: {
      Accept: 'text/event-stream',
      // AI 服务校验登录态，SSE 走 fetch 不经过 axios 拦截器，需手动带上
      ...(token ? { token } : {}),
    },
  })
    .then(async (response) => {
      if (!response.ok) {
        throw new Error(`HTTP ${response.status}`)
      }
      const sessionId = response.headers.get('X-Session-Id')
      const reader = response.body?.getReader()
      if (!reader) {
        throw new Error('不支持流式读取')
      }
      const decoder = new TextDecoder()
      // 跨 chunk 缓冲：网络分片可能把一行 "data:xxx\n\n" 切成两半，
      // 直接 split('\n') 会把残片当成 token 推给 UI，出现乱码
      let buffer = ''

      function processBuffer() {
        // 只处理以 \n\n 结尾的完整 SSE 事件，剩余部分留到下一次
        let sepIdx: number
        while ((sepIdx = buffer.indexOf('\n\n')) !== -1) {
          const event = buffer.substring(0, sepIdx)
          buffer = buffer.substring(sepIdx + 2)
          for (const line of event.split('\n')) {
            if (line.startsWith('data:')) {
              // 不能 trim：token 内的首尾空格/空行是有意义的内容
              // （markdown 缩进 "  - xx"、段落间空行），trim 会把排版打碎
              const data = line.startsWith('data: ') ? line.slice(6) : line.slice(5)
              if (data !== '[DONE]') {
                callbacks.onToken(data)
              }
            }
          }
        }
      }

      function read() {
        reader!.read().then(({ done, value }) => {
          if (done) {
            // 流结束：处理最后残余的事件（服务端一般以 \n\n 结尾，这里兜底）
            if (buffer.trim()) {
              buffer += '\n\n'
              processBuffer()
            }
            callbacks.onDone(sessionId ? Number(sessionId) : undefined)
            return
          }
          buffer += decoder.decode(value, { stream: true })
          processBuffer()
          read()
        }).catch((err) => {
          if (err.name !== 'AbortError') {
            callbacks.onError(err)
          }
        })
      }
      read()
    })
    .catch((err) => {
      if (err.name !== 'AbortError') {
        callbacks.onError(err)
      }
    })
}