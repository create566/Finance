// Prompt 指令配置 API（AI模块）
import { aiRequest } from '../utils/request'

export interface PromptQuery {
  keyword?: string
  scene?: string
  contentType?: string
}

export interface PromptConfig {
  id?: number
  promptKey: string
  name: string
  scene: string
  contentType: string
  systemPrompt: string
  temperature: number
  maxTokens: number
  topP: number
  model: string
  isDefault: number
  status: number
  remark: string
  /** 是否为其场景下运行时真正生效的指令（1=生效），由后端计算，只读 */
  effective?: number
  updateTime?: string
  createTime?: string
}

export interface PageResult<T> {
  records: T[]
  total: number
  size: number
  current: number
}

// GET /ai/admin/chat/prompt/list
export function getPromptList(params: PromptQuery = {}): Promise<PromptConfig[]> {
  return aiRequest({ url: '/chat/prompt/list', method: 'get', params })
}

// POST /ai/admin/chat/prompt
export function addPrompt(data: Omit<PromptConfig, 'id' | 'updateTime' | 'createTime'>): Promise<number> {
  return aiRequest({ url: '/chat/prompt', method: 'post', data })
}

// PUT /ai/admin/chat/prompt
export function updatePrompt(data: PromptConfig): Promise<null> {
  return aiRequest({ url: '/chat/prompt', method: 'put', data })
}

// DELETE /ai/admin/chat/prompt/{id}
export function deletePrompt(id: number): Promise<null> {
  return aiRequest({ url: `/chat/prompt/${id}`, method: 'delete' })
}