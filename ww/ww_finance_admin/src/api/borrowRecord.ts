// 借款记录管理 API
import { request } from '@/utils/request'

/**
 * 借款记录（后端 /admin/core/borrow/record/page 的列表项）。
 * 类型原先定义在 api/mock.ts，已随 mock 数据清理归位到本模块。
 */
export interface BorrowRecord {
  id: number
  borrowerName: string
  borrowerId: number
  amount: number
  term: number // 天数
  rate: number // 年化利率 %
  status: number // 0-待审核 1-审核通过 2-还款中 3-已结清 4-已拒绝
  purpose: string // 借款用途
  repayAmount: number // 已还金额
  applyTime: string
  auditTime: string
  repayEndTime: string // 应还清时间
  rejectReason: string // 拒绝原因
  // 审核留痕（后端随列表返回；未被审核时为 null）
  checkAdminId?: number | null
  checkTime?: string | null
  checkAdminName?: string | null
}

export interface BorrowRecordQuery {
  pageNum?: number
  pageSize?: number
  keyword?: string
  status?: number
}

export interface PageResult<T> {
  list: T[]
  total: number
}

// 获取借款记录列表（分页 + 搜索）
// GET /admin/core/borrow/record/page
export function getBorrowRecordList(params: BorrowRecordQuery = {}): Promise<PageResult<BorrowRecord>> {
  return request<PageResult<BorrowRecord>>({
    url: '/borrow/record/page',
    method: 'get',
    params: {
      pageNum: params.pageNum ?? 1,
      pageSize: params.pageSize ?? 10,
      keyword: params.keyword || undefined,
      status: params.status ?? undefined,
    },
  })
}

// 审核借款申请（通过/拒绝）
// PUT /admin/core/borrow/record/{id}/audit
export function auditBorrowRecord(id: number, status: number, rejectReason?: string): Promise<null> {
  return request<null>({
    url: `/borrow/record/${id}/audit`,
    method: 'put',
    data: { status, rejectReason },
  })
}
