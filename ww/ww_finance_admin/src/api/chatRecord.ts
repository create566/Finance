// 智能客服咨询记录 API（AI模块）
import { aiRequest } from '../utils/request'

export interface ChatMessage {
  role: string
  content: string
  /** 后端返回 LocalDateTime，可能是 ISO 字符串或 Jackson 时间戳数组 */
  createTime?: string | number[]
  timestamp?: string
}

export interface ChatSession {
  // 列表/详情接口都返回 id，声明为必填避免调用处做 undefined 收窄
  id: number
  userId?: number
  userPhone?: string
  userName?: string
  title?: string
  messageCount?: number
  modelName?: string
  kbName?: string
  status: number
  createTime?: string
  updateTime?: string
  messages?: ChatMessage[]
}

export interface PageResult<T> {
  list: T[]
  total: number
  size: number
  current: number
}

export interface ChatSessionQuery {
  keyword?: string
  status?: number
  pageNum?: number
  pageSize?: number
}

// GET /ai/admin/chat/session/list
export function getChatSessionList(params: ChatSessionQuery = {}): Promise<PageResult<ChatSession>> {
  return aiRequest({ url: '/chat/session/list', method: 'get', params })
}

// GET /ai/admin/chat/session/{id}  →  { session, messages }
// 注意：列表接口返回的会话不含 messages，详情必须走这个接口单独取
export function getChatSessionDetail(id: number): Promise<{ session: ChatSession; messages: ChatMessage[] }> {
  return aiRequest({ url: `/chat/session/${id}`, method: 'get' })
}

// PUT /ai/admin/chat/session/{id}/close
export function closeChatSession(id: number): Promise<null> {
  return aiRequest({ url: `/chat/session/${id}/close`, method: 'put' })
}

// DELETE /ai/admin/chat/session/{id}
export function deleteChatSession(id: number): Promise<null> {
  return aiRequest({ url: `/chat/session/${id}`, method: 'delete' })
}