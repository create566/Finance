import { request } from '@/utils/request'

export interface MyBorrowItem {
  id: number
  lendId: number
  currentPeriod: number
  amount: number
  lendYearRate: number
  returnMethod: number
  principal: number
  interest: number
  total: number
  returnDate: string
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

// 后端返回的是银行表单 HTML 字符串（前端自动提交跳转），不是 null
export function repay(id: number): Promise<string> {
  return request<string>({
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