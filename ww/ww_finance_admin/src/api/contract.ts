// 后台合同台账 / 合同模板管理 API
// 对应后端 AdminContractController(/admin/core/contract) 与 AdminContractTemplateController(/admin/core/contract/template)
import { request } from '@/utils/request'

export interface PageResult<T> {
  list: T[]
  total: number
}

/** 合同实例（与后端 Contract 实体一一对应） */
export interface Contract {
  id: number
  contractNo: string
  contractType: string // LOAN / INVEST / PAYMENT
  bizType: string // LEND / BORROW_INFO / LEND_ITEM
  bizId: number
  templateId: number
  templateVersion: number
  partyAId: number
  partyAName: string
  partyBId: number | null
  partyBName: string | null
  amount: number | null
  lendYearRate: number | null
  period: number | null
  startDate: string | null
  endDate: string | null
  contentSnapshot: string
  contentHash: string
  status: number // 0待签署 1已签署 2已作废
  signDeadline: string | null
  revokeReason: string | null
  createTime: string
  updateTime: string
}

/** 签署存证（一次签名一条） */
export interface ContractSignRecord {
  id: number
  contractId: number
  signerId: number
  signerRole: string
  signerName: string
  signerIdCard: string | null
  signImgUrl: string
  contentHash: string
  signIp: string | null
  userAgent: string | null
  signTime: string
  createTime: string
}

/** 逐条存证的哈希复核结论（后台按当前正文重算 SHA-256 与签署时比对） */
export interface SignAudit {
  signerId: number
  signerName: string
  signTime: string
  valid: boolean
}

export interface ContractDetail {
  contract: Contract
  /** 正文快照（渲染后冻结） */
  content: string
  /** 最近一条存证 */
  signRecord: ContractSignRecord | null
  /** 单条哈希自检（无存证时为 true） */
  hashValid: boolean
  /** 全部存证 */
  signRecords: ContractSignRecord[]
  /** 逐条复核结论 */
  signAudits: SignAudit[]
  /** 所有存证哈希均通过 = 正文未被事后改动 */
  tamperFree: boolean
}

/** 合同模板 */
export interface ContractTemplate {
  id: number
  templateNo: string
  contractType: string
  title: string
  contentTpl: string
  variables: string
  version: number
  status: number // 1启用 0停用
  createTime: string
  updateTime: string
}

export interface ContractTypeOption {
  code: string
  desc: string
  defaultTemplateNo: string
}

export const CONTRACT_TYPE_META: Record<string, { label: string; tag: string }> = {
  LOAN: { label: '借款合同', tag: 'primary' },
  INVEST: { label: '出借协议', tag: 'success' },
  PAYMENT: { label: '放款确认书', tag: 'warning' },
}

export const CONTRACT_STATUS_META: Record<number, { label: string; tag: string }> = {
  0: { label: '待签署', tag: 'warning' },
  1: { label: '已签署', tag: 'success' },
  2: { label: '已作废', tag: 'info' },
}

export const BIZ_TYPE_META: Record<string, string> = {
  LEND: '标的',
  BORROW_INFO: '借款申请',
  LEND_ITEM: '投资记录',
}

export interface ContractQuery {
  pageNum?: number
  pageSize?: number
  contractType?: string
  bizType?: string
  status?: number
  keyword?: string
}

// ---------- 合同台账 ----------

// GET /admin/core/contract/page
export function getContractPage(params: ContractQuery = {}): Promise<PageResult<Contract>> {
  return request<PageResult<Contract>>({
    url: '/contract/page',
    method: 'get',
    params: {
      pageNum: params.pageNum ?? 1,
      pageSize: params.pageSize ?? 10,
      contractType: params.contractType || undefined,
      bizType: params.bizType || undefined,
      status: params.status ?? undefined,
      keyword: params.keyword?.trim() || undefined,
    },
  })
}

// GET /admin/core/contract/{id} —— 详情 + 全部签署存证 + 逐条哈希校验 + tamperFree 总判定
export function getContractDetail(id: number): Promise<ContractDetail> {
  return request<ContractDetail>({ url: `/contract/${id}`, method: 'get' })
}

// POST /admin/core/contract/{id}/revoke —— 仅"待签署"可作废；作废原因必填
export function revokeContract(id: number, reason: string): Promise<null> {
  return request<null>({
    url: `/contract/${id}/revoke`,
    method: 'post',
    data: { reason },
  })
}

// ---------- 合同模板 ----------

export interface TemplateQuery {
  pageNum?: number
  pageSize?: number
  contractType?: string
  status?: number
}

// GET /admin/core/contract/template/page
export function getTemplatePage(params: TemplateQuery = {}): Promise<PageResult<ContractTemplate>> {
  return request<PageResult<ContractTemplate>>({
    url: '/contract/template/page',
    method: 'get',
    params: {
      pageNum: params.pageNum ?? 1,
      pageSize: params.pageSize ?? 10,
      contractType: params.contractType || undefined,
      status: params.status ?? undefined,
    },
  })
}

// GET /admin/core/contract/template/types —— 合同类型字典（供下拉）
export function getContractTypes(): Promise<ContractTypeOption[]> {
  return request<ContractTypeOption[]>({ url: '/contract/template/types', method: 'get' })
}

// POST /admin/core/contract/template —— 新增（version 由后端取该 templateNo 的下一版）
export function createTemplate(data: Partial<ContractTemplate>): Promise<ContractTemplate> {
  return request<ContractTemplate>({ url: '/contract/template', method: 'post', data })
}

// PUT /admin/core/contract/template/{id} —— 改模板生成新版本，不覆盖历史
export function updateTemplate(id: number, data: Partial<ContractTemplate>): Promise<ContractTemplate> {
  return request<ContractTemplate>({ url: `/contract/template/${id}`, method: 'put', data })
}

// PUT /admin/core/contract/template/{id}/status —— 1启用 0停用
export function toggleTemplateStatus(id: number, status: number): Promise<null> {
  return request<null>({
    url: `/contract/template/${id}/status`,
    method: 'put',
    params: { status },
  })
}
