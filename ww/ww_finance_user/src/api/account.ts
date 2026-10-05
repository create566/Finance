import { request } from '@/utils/request'
import service from '@/utils/request'

export interface UserAccount {
  id: number
  userId: number
  amount: number
  freezeAmount: number
  bindStatus?: number
  bindCode?: string | null
  userType?: number
  borrowAuthStatus?: number
}

export function getAccount(): Promise<UserAccount> {
  return request<UserAccount>({
    url: '/user/account/auth/getAccount',
    method: 'get',
  })
}

export function recharge(data: { amount: number }): Promise<string> {
  return service({
    url: '/user/account/auth/recharge',
    method: 'post',
    data,
    responseType: 'text',
  }) as unknown as Promise<string>
}

export function withdraw(data: { amount: number }): Promise<string> {
  return service({
    url: '/user/account/auth/withdraw',
    method: 'post',
    data,
    responseType: 'text',
  }) as unknown as Promise<string>
}

export interface TransFlowItem {
  id: number
  userId: number
  userName: string
  transNo: string
  /** 1充值 2提现 3投标 4投资回款 5放款 6还款 */
  transType: number
  transTypeName: string
  /** 0处理中 1成功 2失败。只有充值/提现会出现"处理中"（银行在途） */
  status: number
  transAmount: number
  memo: string
  createTime: string
}

export interface TransFlowResult {
  list: TransFlowItem[]
  total: number
}

/**
 * 交易流水类型元数据。
 * direction 只用于展示正负号：流水表里 trans_amount 一律存正数，收/支靠类型区分。
 */
export const TRANS_TYPE_META: Record<number, { text: string; tag: string; direction: 1 | -1 }> = {
  1: { text: '充值', tag: 'success', direction: 1 },
  2: { text: '提现', tag: 'warning', direction: -1 },
  3: { text: '投标', tag: 'primary', direction: -1 },
  4: { text: '投资回款', tag: 'success', direction: 1 },
  5: { text: '放款', tag: 'primary', direction: 1 },
  6: { text: '还款', tag: 'warning', direction: -1 },
}

export const TRANS_STATUS_META: Record<number, { text: string; tag: string }> = {
  0: { text: '处理中', tag: 'info' },
  1: { text: '成功', tag: 'success' },
  2: { text: '失败', tag: 'danger' },
}

export function transTypeText(row: TransFlowItem): string {
  return row.transTypeName || TRANS_TYPE_META[row.transType]?.text || '未知'
}

export function getTransFlowList(params: {
  page: number
  limit: number
  /** 可选：按类型过滤（1充值2提现…），不传=全部 */
  transType?: number
}): Promise<TransFlowResult> {
  return request({
    url: `/core/transFlow/auth/list/${params.page}/${params.limit}`,
    method: 'get',
    params: params.transType ? { transType: params.transType } : undefined,
  }).then((data: any) => {
    // 后端返回 IPage：{ records, total, size, current }
    return {
      list: data?.records || [],
      total: data?.total || 0,
    }
  })
}
