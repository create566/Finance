// 借款人管理 API
import { request } from '@/utils/request'

/**
 * 借款人档案（后端 /admin/core/borrower/page 的列表项）。
 * 原先这个类型定义在 api/mock.ts 里 —— 该文件已随 mock 数据一起清理，
 * 类型按项目约定归位到本模块。
 */
export interface Borrower {
  id: number
  memberId: number
  realName: string
  idCard: string
  phone: string
  gender: number
  auditStatus: number // 0-待审核 1-审核通过 2-已拒绝
  creditLimit: number // 授信额度
  usedLimit: number // 已用额度
  bankCard: string
  employer: string // 工作单位
  monthlyIncome: number // 月收入
  createTime: string
  auditTime: string
  remark: string
}

export interface BorrowerQuery {
  pageNum?: number
  pageSize?: number
  keyword?: string
  auditStatus?: number
}

export interface PageResult<T> {
  list: T[]
  total: number
}

// 获取借款人列表（分页 + 搜索）
// GET /admin/core/borrower/page
export function getBorrowerList(params: BorrowerQuery = {}): Promise<PageResult<Borrower>> {
  return request<PageResult<Borrower>>({
    url: '/borrower/page',
    method: 'get',
    params: {
      pageNum: params.pageNum ?? 1,
      pageSize: params.pageSize ?? 10,
      keyword: params.keyword || undefined,
      auditStatus: params.auditStatus ?? undefined,
    },
  })
}

// 审核借款人（通过/拒绝）
// PUT /admin/core/borrower/{id}/audit
export function auditBorrower(id: number, auditStatus: number, remark?: string): Promise<null> {
  return request<null>({
    url: `/borrower/${id}/audit`,
    method: 'put',
    data: {
      status: auditStatus,
      isIdCardOk: true,
      isHouseOk: false,
      isCarOk: false,
      infoIntegral: 30,
      // 审核备注随请求提交（此前声明了参数却未使用，备注丢失）
      remark: remark ?? '',
    },
  })
}
