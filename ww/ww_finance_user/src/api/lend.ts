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

export function investLend(lendId: number, data: { amount: number }): Promise<string> {
  return request<string>({
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

/** 推荐结果：标的本体 + 得分 + 可解释的推荐理由 */
export interface RecommendLend {
  lend: LendItem
  score: number
  scoreDetail?: Record<string, number>
  reason: string
  tags: string[]
  remainAmount: number
  progress: number
  /** RULE / ITEM_CF / FUSION / FALLBACK_HOT —— 便于埋点与调参排查 */
  source: string
}

export interface RecommendResult {
  list: RecommendLend[]
  total: number
}

/** 为你推荐（规则打分 + Item-CF 融合；后端已保证永不空返回） */
export function getRecommendLends(topN = 5): Promise<RecommendResult> {
  return request<RecommendResult>({
    url: '/core/lend/auth/recommend',
    method: 'get',
    params: { topN },
  })
}

/** 相似标的（详情页底部） */
export function getSimilarLends(id: number, topN = 5): Promise<RecommendResult> {
  return request<RecommendResult>({
    url: `/core/lend/${id}/similar`,
    method: 'get',
    params: { topN },
  })
}