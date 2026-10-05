// 资金流水监控 API（对齐管理端职能：核对 TransFlow 资金流水，确保账实一致）
import { request } from '@/utils/request'

export interface TransFlowRow {
  id: number
  userId: number
  userName: string
  transNo: string
  /** 1充值 2提现 3投标 4投资回款 5放款 6还款 */
  transType: number
  transTypeName: string
  /** 0处理中 1成功 2失败 */
  status: number
  transAmount: number
  memo: string
  createTime: string
}

export interface TransFlowQuery {
  pageNum?: number
  pageSize?: number
  userId?: number | null
  transType?: number | null
  userName?: string
}

export interface PageResult<T> {
  list: T[]
  total: number
}

// GET /admin/core/transFlow/page
export function getTransFlowPage(params: TransFlowQuery = {}): Promise<PageResult<TransFlowRow>> {
  return request<PageResult<TransFlowRow>>({
    url: '/transFlow/page',
    method: 'get',
    params: {
      pageNum: params.pageNum ?? 1,
      pageSize: params.pageSize ?? 10,
      userId: params.userId ?? undefined,
      transType: params.transType ?? undefined,
      userName: params.userName || undefined,
    },
  })
}

/** 交易类型元数据（与后端 TransTypeEnum 一一对应） */
export const TRANS_TYPE_META: Record<number, { text: string; tag: string; direction: 1 | -1 }> = {
  1: { text: '充值', tag: 'success', direction: 1 },
  2: { text: '提现', tag: 'warning', direction: -1 },
  3: { text: '投标', tag: 'primary', direction: -1 },
  4: { text: '投资回款', tag: 'success', direction: 1 },
  5: { text: '放款', tag: 'primary', direction: 1 },
  6: { text: '还款', tag: 'warning', direction: -1 },
}

/** 流水状态元数据（与后端 TransStatusEnum 一一对应） */
export const TRANS_STATUS_META: Record<number, { text: string; tag: string }> = {
  0: { text: '处理中', tag: 'info' },
  1: { text: '成功', tag: 'success' },
  2: { text: '失败', tag: 'danger' },
}
