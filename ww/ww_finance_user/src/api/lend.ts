import { request } from '@/utils/request'

export interface LendItem {
  id: number
  title: string
  amount: number
  lendYearRate: number
  period: number
  investAmount: number
  investNum: number
  status: number
  returnMethod: number
  createTime: string
}

export interface LendListResult {
  list: LendItem[]
  total: number
}

export function getLendList(params?: { page?: number; limit?: number }): Promise<LendListResult> {
  return request<LendListResult>({
    url: '/core/lend/list',
    method: 'get',
    params: { page: params?.page ?? 1, limit: params?.limit ?? 10 },
  })
}

export interface LendDetail {
  id: number
  title: string
  amount: number
  lendYearRate: number
  period: number
  investAmount: number
  status: number
  returnMethod: number
  lendStartDate: string
  lendEndDate: string
  lendInfo: string
}

export function getLendDetail(id: number): Promise<LendDetail> {
  return request<LendDetail>({
    url: `/core/lend/${id}`,
    method: 'get',
  })
}

export function investLend(lendId: number, data: { amount: number }): Promise<null> {
  return request<null>({
    url: `/core/lend/invest/${lendId}`,
    method: 'post',
    data,
  })
}

export interface MyInvestment {
  id: number
  lendId: number
  investAmount: number
  lendYearRate: number
  expectAmount: number
  status: number
  createTime: string
}

export function getMyInvestments(): Promise<{ list: MyInvestment[]; total: number }> {
  return request<{ list: MyInvestment[]; total: number }>({
    url: '/core/lend/myInvestments',
    method: 'get',
  })
}