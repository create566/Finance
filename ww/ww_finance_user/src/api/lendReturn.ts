import { request } from '@/utils/request'

export interface MyBorrowItem {
  id: number
  lendId: number
  amount: number
  lendYearRate: number
  returnMethod: number
  status: number
  createTime: string
}

export function getMyBorrowList(): Promise<MyBorrowItem[]> {
  return request<MyBorrowItem[]>({
    url: '/core/lendReturn/myBorrowList',
    method: 'get',
  })
}

export function getMyInvestList(): Promise<any[]> {
  return request<any[]>({
    url: '/core/lendReturn/myInvestList',
    method: 'get',
  })
}

export function repay(id: number): Promise<null> {
  return request<null>({
    url: `/core/lendReturn/repay/${id}`,
    method: 'put',
  })
}

export interface LendItemReturn {
  id: number
  lendReturnId: number
  returnDate: string
  total: number
  principal: number
  interest: number
  status: number
}

export function getItemReturns(lendReturnId: number): Promise<LendItemReturn[]> {
  return request<LendItemReturn[]>({
    url: `/core/lendReturn/itemReturns/${lendReturnId}`,
    method: 'get',
  })
}