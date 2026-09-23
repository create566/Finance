import { request } from '@/utils/request'

export interface BorrowInfoSubmitParams {
  amount: number
  period: number
  returnMethod: number
  moneyUse: number
  isDraft?: boolean
}

export function submitBorrowInfo(data: BorrowInfoSubmitParams): Promise<null> {
  return request<null>({
    url: '/core/borrowInfo/auth/submit',
    method: 'post',
    data,
  })
}

export interface BorrowInfoItem {
  id: number
  amount: number
  period: number
  returnMethod: number
  moneyUse: number
  status: number
  createTime: string
}

export function getBorrowInfoList(): Promise<BorrowInfoItem[]> {
  return request<BorrowInfoItem[]>({
    url: '/core/borrowInfo/auth/list',
    method: 'get',
  })
}

export interface BorrowInfoStatus {
  borrowInfoStatus: number
}

export function getBorrowInfoStatus(): Promise<BorrowInfoStatus> {
  return request<BorrowInfoStatus>({
    url: '/core/borrowInfo/auth/status',
    method: 'get',
  })
}