import { request } from '@/utils/request'

/** 合同类型：借款合同 / 出借协议 / 放款确认书 */
export type ContractType = 'LOAN' | 'INVEST' | 'PAYMENT'
/** 合同关联的业务类型 */
export type ContractBizType = 'LEND' | 'BORROW_INFO' | 'LEND_ITEM'

export interface Contract {
  id: number
  contractNo: string
  contractType: ContractType
  bizType: ContractBizType
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
  status: number
  signDeadline: string | null
  revokeReason: string | null
  createTime: string
  updateTime: string
}

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
}

export interface ContractDetail {
  contract: Contract
  content: string
  signRecord: ContractSignRecord | null
  /** 后端重算快照哈希与存证哈希比对结果，false 代表正文被改动过 */
  hashValid: boolean
}

export interface ContractPrepareResult {
  contract: Contract
  content: string
  signed: boolean
}

export interface ContractPage {
  list: Contract[]
  total: number
}

/** 我的合同列表 */
export function getMyContracts(params?: {
  contractType?: ContractType
  status?: number
  pageNum?: number
  pageSize?: number
}): Promise<ContractPage> {
  return request<ContractPage>({
    url: '/core/contract/auth/list',
    method: 'get',
    params: {
      contractType: params?.contractType,
      status: params?.status,
      pageNum: params?.pageNum ?? 1,
      pageSize: params?.pageSize ?? 10,
    },
  })
}

/** 获取或生成合同（投资前发现未签署时调用） */
export function prepareContract(data: {
  bizType: ContractBizType
  contractType: ContractType
  bizId: number
  amount?: number
}): Promise<ContractPrepareResult> {
  return request<ContractPrepareResult>({
    url: '/core/contract/auth/prepare',
    method: 'post',
    data,
  })
}

/** 合同详情（正文 + 签署存证 + 哈希校验） */
export function getContractDetail(contractId: number): Promise<ContractDetail> {
  return request<ContractDetail>({
    url: `/core/contract/auth/detail/${contractId}`,
    method: 'get',
  })
}

/** 提交手写签名（signImgBase64 支持 data:image/png;base64,... 或裸 base64） */
export function signContract(data: {
  contractId: number
  signImgBase64: string
}): Promise<{ contractNo: string; status: number }> {
  return request<{ contractNo: string; status: number }>({
    url: '/core/contract/auth/sign',
    method: 'post',
    data,
  })
}

/**
 * 补建历史缺失的待签合同。
 * 合同模块上线前的标的/投资记录没有合同，不补建的话关口会拦住业务却看不到该签哪一份。
 * created = 真正新建的份数，processed = 本次处理（含已存在的幂等命中）份数。
 */
export function syncMyContracts(): Promise<{
  created: number
  processed: number
  skipped: number
  notes: string[]
}> {
  return request<{ created: number; processed: number; skipped: number; notes: string[] }>({
    url: '/core/contract/auth/sync',
    method: 'post',
  })
}

/** 合同类型展示元数据 */
export const CONTRACT_TYPE_META: Record<ContractType, { text: string; tag: string; hint: string }> = {
  LOAN: { text: '借款合同', tag: 'warning', hint: '标的发布募资前须由借款人签署' },
  INVEST: { text: '出借协议', tag: 'primary', hint: '投资下单前须由投资人签署' },
  PAYMENT: { text: '放款确认书', tag: 'success', hint: '满标放款前须由借款人签署' },
}

/** 合同状态展示元数据 */
export const CONTRACT_STATUS_META: Record<number, { text: string; tag: string }> = {
  0: { text: '待签署', tag: 'warning' },
  1: { text: '已签署', tag: 'success' },
  2: { text: '已作废', tag: 'info' },
}

export function contractTypeText(t: string): string {
  return CONTRACT_TYPE_META[t as ContractType]?.text || t || '-'
}

export function contractStatusText(s: number | null | undefined): string {
  if (s === null || s === undefined) return '-'
  return CONTRACT_STATUS_META[s]?.text || '未知'
}
