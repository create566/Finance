// 模型配置管理 API（AI模块）
import { aiRequest } from '../utils/request'

export interface ModelQuery {
  keyword?: string
  modelType?: string
  provider?: string
}

export interface ModelConfig {
  id?: number
  name: string
  provider: string
  modelType: string
  modelName: string
  apiUrl: string
  apiKey: string
  maxTokens: number
  temperature: number
  isDefault: number
  status: number
  remark: string
  /** 连通性测试结果：0-未测试/未通过 1-已通过（决定能否被「对话模型选择」绑定） */
  testStatus?: number
  testTime?: string
  testMessage?: string
  /** 正在引用该模型的对话场景（逗号分隔）；非空则不可删除 */
  usedBy?: string
  /** 其中启用中的引用场景；非空则不可停用 */
  activeUsedBy?: string
  createTime?: string
  updateTime?: string
}

export interface PageResult<T> {
  records: T[]
  total: number
  size: number
  current: number
}

// GET /ai/admin/model/config/list
export function getModelConfigList(params: ModelQuery = {}): Promise<ModelConfig[]> {
  return aiRequest({ url: '/model/config/list', method: 'get', params })
}

// POST /ai/admin/model/config
export function addModelConfig(data: Omit<ModelConfig, 'id' | 'createTime' | 'updateTime'>): Promise<number> {
  return aiRequest({ url: '/model/config', method: 'post', data })
}

// PUT /ai/admin/model/config
export function updateModelConfig(data: ModelConfig): Promise<null> {
  return aiRequest({ url: '/model/config', method: 'put', data })
}

// DELETE /ai/admin/model/config/{id}
export function deleteModelConfig(id: number): Promise<null> {
  return aiRequest({ url: `/model/config/${id}`, method: 'delete' })
}

// POST /ai/admin/model/config/test/{id}
export function testModelConfig(
  id: number,
): Promise<{ success: boolean; latency: number; message: string; httpStatus?: number }> {
  return aiRequest({ url: `/model/config/test/${id}`, method: 'post' })
}