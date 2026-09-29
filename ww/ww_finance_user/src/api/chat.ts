import request from '../utils/request'
import axios, { type AxiosInstance } from 'axios'

const aiRequest: AxiosInstance = axios.create({
  baseURL: '/ai',
  timeout: 60000,
})

export interface ChatMessage {
  id?: number
  sessionId?: number
  userId?: number
  role: string
  content: string
  chunkIds?: string
  createTime?: string
}

export interface ChatSession {
  id?: number
  userId?: number
  userPhone?: string
  userName?: string
  title?: string
  modelName?: string
  kbName?: string
  status?: number
  createTime?: string
  updateTime?: string
  messages?: ChatMessage[]
}

export interface KnowledgeBase {
  id?: number
  name?: string
  description?: string
  docCount?: number
  chunkCount?: number
  status?: number
}

// ========== API模块：会话/消息 CRUD ==========

export function createSession(data: { userId?: number; title?: string }): Promise<ChatSession> {
  return request({ url: '/core/chat/session/create', method: 'post', data })
}

export function sendMessage(data: { sessionId: number; content: string; role?: string; userId?: number }): Promise<ChatMessage> {
  return request({ url: '/core/chat/message/send', method: 'post', data })
}

export function getMessages(sessionId: number): Promise<ChatMessage[]> {
  return request({ url: '/core/chat/message/list', method: 'get', params: { sessionId } })
}

export function getMySessions(userId: number): Promise<{ list: ChatSession[]; total: number }> {
  return request({ url: '/core/chat/session/list', method: 'get', params: { userId } })
}

export function closeSession(sessionId: number): Promise<null> {
  return request({ url: `/core/chat/session/${sessionId}/close`, method: 'put' })
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
  return aiRequest.get('/admin/knowledge/base/list').then(res => {
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

// 当前用户的历史会话列表（按 updateTime 倒序），供"历史会话"侧边栏使用
export function getAiSessions(userId = 1): Promise<{ list: ChatSessionItem[]; total: number }> {
  return aiRequest.get('/sessions', { params: { userId } }).then(res => res.data.data)
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
  params: { prompt: string; chatId?: string },
  callbacks: StreamCallbacks,
): void {
  abortStream()
  streamAbortController = new AbortController()

  const url = new URL('/ai/chat-stream', window.location.origin)
  url.searchParams.set('prompt', params.prompt)
  if (params.chatId) {
    url.searchParams.set('chatId', params.chatId)
  }

  fetch(url.toString(), {
    signal: streamAbortController.signal,
    headers: { Accept: 'text/event-stream' },
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
              const data = line.substring(5).trim()
              if (data && data !== '[DONE]') {
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