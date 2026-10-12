import axios from 'axios'

/**
 * 平台运营统计（登录 / 注册页展示）。
 *
 * ⚠️ 这里<b>刻意不用</b> `@/utils/request`：
 * 公共请求器在失败时会弹 ElMessage 错误、并在 401/403 时跳转登录页。
 * 登录页本来就未登录，这两个副作用会造成「未登录 → 弹错误 → 跳登录页」的假故障。
 * 统计只是装饰性信息，取不到就静默隐藏，因此单独用裸 axios。
 */
export interface PlatformStatistics {
  /* 用户 */
  userCount: number
  borrowerCount: number
  investorCount: number
  /* 标的 */
  lendCount: number
  publishingCount: number
  publishAmount: number
  matchedAmount: number
  /* 利率与期限 */
  avgYearRate: number
  minYearRate: number
  maxYearRate: number
  avgPeriod: number
  /* 履约 */
  returnPlanCount: number
  returnedCount: number
  overdueCount: number
  onTimeRate: number | null
}

/** 拉取平台统计；任何异常（后端未起 / 未放行 / 超时）一律返回 null，由调用方决定隐藏该模块 */
export async function getPlatformStatistics(): Promise<PlatformStatistics | null> {
  try {
    const { data } = await axios.get('/api/core/index/statistics', { timeout: 6000 })
    if (data && data.code === 200 && data.data) {
      return data.data as PlatformStatistics
    }
    return null
  } catch {
    return null
  }
}
