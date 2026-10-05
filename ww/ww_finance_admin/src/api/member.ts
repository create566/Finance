// 会员管理 API
import { request } from '@/utils/request'

/**
 * 会员（后端 /admin/core/member/page 的列表项）。
 * 类型原先定义在 api/mock.ts，已随 mock 数据清理归位到本模块。
 */
export interface Member {
  id: number
  phone: string
  nickname: string
  realName: string
  idCard: string
  gender: number // 0-未知 1-男 2-女
  score: number
  levelName: string
  status: number // 1-正常 0-禁用
  registerTime: string
  lastLoginTime: string
  remark: string
}

export interface MemberQuery {
  pageNum?: number
  pageSize?: number
  keyword?: string
  status?: number
}

export interface PageResult<T> {
  list: T[]
  total: number
}

// 获取会员列表（分页 + 搜索）
// GET /admin/core/member/page
export function getMemberList(params: MemberQuery = {}): Promise<PageResult<Member>> {
  return request<PageResult<Member>>({
    url: '/member/page',
    method: 'get',
    params: {
      pageNum: params.pageNum ?? 1,
      pageSize: params.pageSize ?? 10,
      keyword: params.keyword || undefined,
      status: params.status ?? undefined,
    },
  })
}

// 新增会员
// POST /admin/core/member
export function addMember(data: Partial<Member>): Promise<null> {
  return request<null>({ url: '/member', method: 'post', data })
}

// 修改会员状态（启用/禁用）
// PUT /admin/core/member/{id}/status
export function updateMemberStatus(id: number, status: number): Promise<null> {
  return request<null>({ url: `/member/${id}/status`, method: 'put', data: { status } })
}
