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
  id: number
  name: string
  description: string
  docCount: number
  chunkCount: number
  status: number
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
  return aiRequest.post('/chat-sync', data).then(res => res.data)
}

// ========== AI模块：知识库列表 ==========

export function getKnowledgeBases(): Promise<KnowledgeBase[]> {
  return aiRequest.get('/admin/knowledge/base/list', { params: { status: 1 } }).then((res: any) => {
    return (res.list || res.data || res || [])
  })
}

// ========== AI模块：知识库 RAG 同步问答 ==========

export function askKnowledgeAI(data: {
  question: string
  knowledgeBaseId: number
  sessionId?: number
  userId?: number
}): Promise<{ answer: string; sessionId: number }> {
  return aiRequest.post('/knowledge-chat', data).then(res => res.data)
}

// ========== AI模块：SSE 流式问答 ==========

export interface StreamCallbacks {
  onToken: (token: string) => void
  onDone: () => void
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
      const reader = response.body?.getReader()
      if (!reader) {
        throw new Error('不支持流式读取')
      }
      const decoder = new TextDecoder()

      function read() {
        reader.read().then(({ done, value }) => {
          if (done) {
            callbacks.onDone()
            return
          }
          const chunk = decoder.decode(value, { stream: true })
          const lines = chunk.split('\n')
          for (const line of lines) {
            if (line.startsWith('data:')) {
              const data = line.substring(5).trim()
              if (data && data !== '[DONE]') {
                callbacks.onToken(data)
              }
            } else if (line.trim() && !line.startsWith(':')) {
              callbacks.onToken(line)
            }
          }
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

export function askKnowledgeAIStream(
  params: { prompt: string; knowledgeBaseId: number; chatId?: string },
  callbacks: StreamCallbacks,
): void {
  abortStream()
  streamAbortController = new AbortController()

  const url = new URL('/ai/knowledge-chat-stream', window.location.origin)
  url.searchParams.set('prompt', params.prompt)
  url.searchParams.set('knowledgeBaseId', String(params.knowledgeBaseId))
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
      const reader = response.body?.getReader()
      if (!reader) {
        throw new Error('不支持流式读取')
      }
      const decoder = new TextDecoder()

      function read() {
        reader.read().then(({ done, value }) => {
          if (done) {
            callbacks.onDone()
            return
          }
          const chunk = decoder.decode(value, { stream: true })
          const lines = chunk.split('\n')
          for (const line of lines) {
            if (line.startsWith('data:')) {
              const data = line.substring(5).trim()
              if (data && data !== '[DONE]') {
                callbacks.onToken(data)
              }
            } else if (line.trim() && !line.startsWith(':')) {
              callbacks.onToken(line)
            }
          }
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