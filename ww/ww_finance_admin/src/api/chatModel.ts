// 智能客服对话模型配置 API（AI模块）
import { aiRequest } from '../utils/request'
import { getModelConfigList } from './modelConfig'
import { getPromptList } from './promptConfig'
import { getKnowledgeBaseList } from './knowledge'

export interface ChatModelBinding {
  id?: number
  /** 对话场景标识，与运行时读取的场景一致：intent 意图路由 / chat 闲聊应答 / rag 知识库问答 */
  scene: string
  sceneName?: string
  /** 模型配置 ID（ai_model_config.id） */
  modelId?: number | null
  /** 模型标识，例如 qwen3.8-max —— 会被当作真实模型名传给大模型接口 */
  modelName?: string
  /** Prompt 配置 ID（prompt_config.id），0 表示不绑定（回退该场景默认指令） */
  promptId?: number | null
  promptKey?: string
  /** 知识库 ID（knowledge_base.id），null 表示不检索 */
  kbId?: number | null
  kbName?: string
  /** 启用状态 0/1（后端实体字段是 enabled，不是 status） */
  enabled: number
  remark?: string
  updateTime?: string
  /** 绑定的模型当前是否测试通过（1=通过），未通过说明是门禁之前的历史绑定 */
  modelTestStatus?: number | null
  modelTestMessage?: string | null
}

export interface SceneOption {
  scene: string
  sceneName: string
  /** 是否已配置绑定 */
  bound: boolean
}

/** 某对话场景当前实际生效的模型及来源 */
export interface EffectiveConfig {
  scene: string
  sceneName: string
  bound: boolean
  /** binding | prompt | default */
  source: string
  sourceText: string
  modelId?: number | null
  modelName: string
  promptKey?: string | null
  kbId?: number | null
  kbName?: string
  modelTestStatus?: number | null
  modelTestMessage?: string | null
}

export interface SelectOptions {
  models: { id: number; name: string; modelName: string }[]
  prompts: { id: number; promptKey: string; name: string; scene: string }[]
  knowledgeBases: { id: number; name: string }[]
}

// GET /ai/admin/chat/model/list —— 返回全部未删除绑定（含禁用）
export function getChatModelBindings(): Promise<ChatModelBinding[]> {
  return aiRequest({ url: '/chat/model/list', method: 'get' })
}

// GET /ai/admin/chat/model/scenes —— 运行时可配置的处理环节
export function getChatModelScenes(): Promise<SceneOption[]> {
  return aiRequest({ url: '/chat/model/scenes', method: 'get' })
}

// GET /ai/admin/chat/model/effective —— 各环节当前实际生效的模型及来源
export function getEffectiveConfigs(): Promise<EffectiveConfig[]> {
  return aiRequest({ url: '/chat/model/effective', method: 'get' })
}

// POST /ai/admin/chat/model
export function addChatModelBinding(data: ChatModelBinding): Promise<number> {
  return aiRequest({ url: '/chat/model', method: 'post', data })
}

// PUT /ai/admin/chat/model
export function updateChatModelBinding(data: ChatModelBinding): Promise<null> {
  return aiRequest({ url: '/chat/model', method: 'put', data })
}

// PUT /ai/admin/chat/model/toggle/{id}
export function toggleChatModelBinding(id: number): Promise<null> {
  return aiRequest({ url: `/chat/model/toggle/${id}`, method: 'put' })
}

// DELETE /ai/admin/chat/model/{id}
export function deleteChatModelBinding(id: number): Promise<null> {
  return aiRequest({ url: `/chat/model/${id}`, method: 'delete' })
}

// 获取模型、Prompt、知识库下拉选项
export async function getSelectOptions(): Promise<SelectOptions> {
  const [modelsList, promptsList, kbList] = await Promise.all([
    getModelConfigList().catch(() => [] as any[]),
    getPromptList().catch(() => [] as any[]),
    getKnowledgeBaseList().catch(() => [] as any[]),
  ])
  return {
    // 对话场景只能选「对话类型 + 测试通过 + 已启用」的模型（后端 resolveNames 也会拦，前端先收敛选项）
    models: modelsList
      .filter((m: any) => m.status === 1 && m.modelType === 'chat' && m.testStatus === 1)
      .map((m: any) => ({ id: m.id, name: m.name, modelName: m.modelName })),
    // 关键词表不是系统指令，不能绑定到对话场景
    prompts: promptsList
      .filter((p: any) => p.contentType !== 'keywords')
      .map((p: any) => ({ id: p.id, promptKey: p.promptKey, name: p.name, scene: p.scene })),
    knowledgeBases: kbList.map((k: any) => ({ id: k.id, name: k.name })),
  }
}
