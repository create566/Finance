// RAG 知识库管理 API（对接 AI 模块后端）
import { aiRequest } from '../utils/request'

export interface KnowledgeBase {
  id: number
  name: string
  description: string
  embeddingModel: string
  docCount: number
  chunkCount: number
  status: number
  createTime: string
  updateTime: string
}

export interface KnowledgeDoc {
  id: number
  kbId: number
  title: string
  source: string
  content: string
  fileName: string
  fileSize: number
  chunkCount: number
  status: number
  createTime: string
}

// ===== 知识库 CRUD =====

// GET /ai/admin/knowledge/base/list
export function getKnowledgeBaseList(keyword?: string, status?: number): Promise<KnowledgeBase[]> {
  return aiRequest({ url: '/knowledge/base/list', method: 'get', params: { keyword, status } })
}

// POST /ai/admin/knowledge/base
export function addKnowledgeBase(data: { name: string; description: string; embeddingModel: string }): Promise<number> {
  return aiRequest({ url: '/knowledge/base', method: 'post', data: { ...data, status: 1 } })
}

// PUT /ai/admin/knowledge/base
export function updateKnowledgeBase(data: KnowledgeBase): Promise<null> {
  return aiRequest({ url: '/knowledge/base', method: 'put', data })
}

// DELETE /ai/admin/knowledge/base/{id}
export function deleteKnowledgeBase(id: number): Promise<null> {
  return aiRequest({ url: `/knowledge/base/${id}`, method: 'delete' })
}

// ===== 知识文档 CRUD =====

// GET /ai/admin/knowledge/doc/list?kbId={kbId}
export function getKnowledgeDocList(kbId: number): Promise<KnowledgeDoc[]> {
  return aiRequest({ url: '/knowledge/doc/list', method: 'get', params: { kbId } })
}

// POST /ai/admin/knowledge/doc/text
export function addKnowledgeDocText(data: { kbId: number; title: string; content: string }): Promise<number> {
  return aiRequest({ url: '/knowledge/doc/text', method: 'post', data })
}

// POST /ai/admin/knowledge/doc/upload (multipart)
export function addKnowledgeDocPdf(kbId: number, title: string, file: File): Promise<number> {
  const formData = new FormData()
  formData.append('kbId', String(kbId))
  formData.append('title', title)
  formData.append('file', file)
  return aiRequest({
    url: '/knowledge/doc/upload',
    method: 'post',
    data: formData,
    headers: { 'Content-Type': 'multipart/form-data' },
  })
}

// DELETE /ai/admin/knowledge/doc/{id}
export function deleteKnowledgeDoc(id: number): Promise<null> {
  return aiRequest({ url: `/knowledge/doc/${id}`, method: 'delete' })
}