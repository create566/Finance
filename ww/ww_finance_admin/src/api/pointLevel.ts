// 积分等级管理 API
import { request } from '@/utils/request'

/**
 * 积分等级（后端 /admin/core/integralGrade/page 的列表项）。
 * 类型原先定义在 api/mock.ts，已随 mock 数据清理归位到本模块。
 */
export interface PointLevel {
  id: number
  levelName: string
  minScore: number
  maxScore: number
  borrowLimit: number
  createTime: string
}

export interface PointLevelQuery {
  pageNum?: number
  pageSize?: number
}

export interface PageResult<T> {
  list: T[]
  total: number
}

// 获取积分等级列表
// GET /admin/core/integralGrade/page
export function getPointLevelList(params: PointLevelQuery = {}): Promise<PageResult<PointLevel>> {
  return request<PageResult<PointLevel>>({
    url: '/integralGrade/page',
    method: 'get',
    params: {
      pageNum: params.pageNum ?? 1,
      pageSize: params.pageSize ?? 10,
    },
  })
}

// 新增积分等级
// POST /admin/core/integralGrade/save
export function addPointLevel(data: Omit<PointLevel, 'id' | 'createTime'>): Promise<null> {
  return request<null>({ url: '/integralGrade/save', method: 'post', data })
}

// 修改积分等级
// PUT /admin/core/integralGrade/update
export function updatePointLevel(data: PointLevel): Promise<null> {
  return request<null>({ url: '/integralGrade/update', method: 'put', data })
}

// 删除积分等级
// DELETE /admin/core/integralGrade/remove/{id}
export function deletePointLevel(id: number): Promise<null> {
  return request<null>({ url: `/integralGrade/remove/${id}`, method: 'delete' })
}
