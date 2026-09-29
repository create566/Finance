import { request } from '@/utils/request'
import service from '@/utils/request'

export interface UserAccount {
  id: number
  userId: number
  amount: number
  freezeAmount: number
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
  transNo: string
  transType: string
  transAmount: number
  status: number
  createTime: string
  remark: string
}

export interface TransFlowResult {
  list: TransFlowItem[]
  total: number
}

export function getTransFlowList(params: { page: number; limit: number }): Promise<TransFlowResult> {
  return request({
    url: `/core/transFlow/auth/list/${params.page}/${params.limit}`,
    method: 'get',
  }).then((data: any) => {
    // 后端返回 IPage：{ records, total, size, current }
    return {
      list: data.records || [],
      total: data.total || 0,
    }
  })
}