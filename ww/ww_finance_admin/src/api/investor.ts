// 投资人管理 API
import { request } from '@/utils/request'

export interface InvestorQuery {
  pageNum?: number
  pageSize?: number
  keyword?: string
  auditStatus?: number
}

export interface Investor {
  id: number
  userId: number
  name: string
  idCard: string
  mobile: string
  sex?: number
  age?: number
  status: number
  createTime: string
  updateTime: string
}

export interface PageResult<T> {
  list: T[]
  total: number
}

// 获取投资人列表（分页 + 搜索）
// GET /admin/core/investor/page
export function getInvestorList(params: InvestorQuery = {}): Promise<PageResult<Investor>> {
  return request<PageResult<Investor>>({
    url: '/investor/page',
    method: 'get',
    params: {
      pageNum: params.pageNum ?? 1,
      pageSize: params.pageSize ?? 10,
      keyword: params.keyword || undefined,
      auditStatus: params.auditStatus ?? undefined,
    },
  })
}

// 审核投资人（通过/拒绝）
// PUT /admin/core/investor/{id}/audit
export function auditInvestor(id: number, auditStatus: number): Promise<null> {
  return request<null>({
    url: `/investor/${id}/audit`,
    method: 'put',
    data: {
      status: auditStatus,
      isIdCardOk: true,
    },
  })
}
