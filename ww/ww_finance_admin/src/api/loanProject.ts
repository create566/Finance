// 标的管理 API - 已对接后端（含字段映射）
import request from '@/utils/request'

export interface LoanProjectQuery {
  pageNum?: number
  pageSize?: number
  keyword?: string // 标的名称/借款人
  status?: number
}

export interface PageResult<T> {
  list: T[]
  total: number
}

/**
 * 标的（后端 Lend 实体映射后的前端视图模型）。
 * 类型原先定义在 api/mock.ts，已随 mock 数据清理归位到本模块。
 *
 * status 的真源是后端 `LendStatusEnum`（**8 个状态**，旧注释只写了 5 个）：
 * 0-待发布 1-募资中 2-已满标待放款 3-已逾期 4-已下架 5-还款中 6-已完结 7-已流标
 */
export interface LoanProject {
  id: number
  title: string
  borrowerName: string
  borrowerId: number
  amount: number
  rate: number
  term: number // 天数
  raisedAmount: number // 已募集金额
  progress: number // 募集进度 %
  status: number // 见上方 LendStatusEnum 说明
  purpose: string // 借款用途
  riskLevel: number // 风险等级 1-低 2-中 3-高
  publishTime: string
  endTime: string // 募集截止时间
  createTime: string
  remark: string
  // 上架审核 / 放款发起 / 下架处置 留痕（后端随列表返回；未发生时为空）
  checkAdminId?: number | null
  checkTime?: string | null
  checkAdminName?: string | null
  paymentAdminId?: number | null
  paymentAdminName?: string | null
  offlineAdminId?: number | null
  offlineTime?: string | null
  offlineAdminName?: string | null
}

/** 标的投资记录（标的管理详情抽屉里的投资列表）。 */
export interface Investment {
  id: number
  projectId: number // 标的ID
  investorName: string // 投资人姓名
  investorId: number // 投资人ID
  amount: number // 投资金额
  status: number // lend_item.status：0-募资中 1-已撤标 2-满标待放款
  investTime: string
  expectedReturn: number // 预期收益
  term: number // 投资期限（天）
}

// 后端Lend实体 → 前端LoanProject字段的映射函数
function mapLendToLoanProject(lend: any): any {
  const amount = Number(lend.amount) || 0
  const investAmount = Number(lend.investAmount) || 0
  const progress = amount > 0 ? Math.round((investAmount / amount) * 100) : 0

  return {
    id: lend.id,
    title: lend.title || '',
    borrowerName: `用户${lend.userId}`, // 后端只有userId，前端显示为"用户X"
    borrowerId: lend.userId,
    amount: amount,
    rate: Number(lend.lendYearRate) || 0, // 后端字段: lendYearRate → 前端: rate
    // ⚠️ 后端 period = 投资期数（单位：月/期），不是天数！展示时要拼"个月"。
    //    别和后端 Investment.term（= 按 lendStartDate/lendEndDate 算出的募资窗口"天数"）搞混——
    //    两者在各自接口里都叫 term，同名不同义。
    term: lend.period || 30,              // 后端字段: period → 前端: term（月）
    riskLevel: 1,                        // 后端无此字段，默认低风险
    progress: progress,                  // 计算得出：investAmount/amount*100
    raisedAmount: investAmount,
    status: lend.status,
    createTime: lend.createTime || '',
    publishTime: lend.publishDate || '',
    endTime: lend.lendEndDate || '',
    purpose: lend.lendInfo || '',
    remark: '',
    // 上架审核 / 放款发起 / 下架处置 留痕（由后端 admin 列表接口显式回填）
    checkAdminId: lend.checkAdminId ?? null,
    checkTime: lend.checkTime || '',
    checkAdminName: lend.checkAdminName || '',
    paymentAdminId: lend.paymentAdminId ?? null,
    paymentAdminName: lend.paymentAdminName || '',
    offlineAdminId: lend.offlineAdminId ?? null,
    offlineTime: lend.offlineTime || '',
    offlineAdminName: lend.offlineAdminName || '',
  }
}

// 获取标的列表（分页 + 搜索）- 已对接后端
// GET /admin/core/loan/project/page
export function getLoanProjectList(params: LoanProjectQuery = {}): Promise<PageResult<any>> {
  return request({
    url: '/loan/project/page',
    method: 'get',
    params: {
      pageNum: params.pageNum ?? 1,
      pageSize: params.pageSize ?? 10,
      keyword: params.keyword?.trim(),
      status: params.status,
    },
  }).then((data: any) => {
    // 拦截器已解包 code/data 信封，data = { list: [...], total: ... }
    const list = (data.list || []).map(mapLendToLoanProject)
    return { list, total: data.total || 0 }
  })
}

// 发布/创建标的 - 已对接后端
// POST /admin/core/loan/project
export function publishLoanProject(data: any): Promise<null> {
  return request({
    url: '/loan/project',
    method: 'post',
    data: {
      title: data.title,
      borrowerName: data.borrowerName,
      borrowerId: data.borrowerId,
      amount: data.amount,
      rate: data.rate,
      term: data.term,
      purpose: data.purpose,
      riskLevel: data.riskLevel || 1,
    },
  }).then(() => null as unknown as Promise<null>)
}

// 下架标的 - 已对接后端
// PUT /admin/core/loan/project/{id}/offline
export function offlineLoanProject(id: number): Promise<null> {
  return request({
    url: `/loan/project/${id}/offline`,
    method: 'put',
  }).then(() => null as unknown as Promise<null>)
}

// 发布标的（待发布→募资中）- 已对接后端
// PUT /admin/core/loan/project/{id}/publish
export function publishLoan(id: number): Promise<null> {
  return request({
    url: `/loan/project/${id}/publish`,
    method: 'put',
  }).then(() => null as unknown as Promise<null>)
}

// 满标放款 - 已对接后端
// PUT /admin/core/loan/project/{id}/makeLoan
// 服务端直连银行同步放款：返回 { success, message, agentBillNo, resultCode, bankResponse }
// 注：银行的放款接口是同步 JSON，不像充值/投标那样有异步回调，
//     所以这里不再返回 formHtml 让浏览器跳银行（那样平台永远收不到结果）。
export interface MakeLoanResult {
  lendId: number
  agentBillNo: string
  resultCode: string
  resultMsg: string
  success: boolean
  message: string
  bankResponse: Record<string, any> | null
}

export function makeLoan(id: number): Promise<MakeLoanResult> {
  return request({
    url: `/loan/project/${id}/makeLoan`,
    method: 'put',
  }).then((data: any) => data as MakeLoanResult)
}

// 获取标的的投资列表 - 已对接后端
// GET /admin/core/loan/project/{projectId}/investments
export function getInvestmentList(projectId: number): Promise<any[]> {
  return request({
    url: `/loan/project/${projectId}/investments`,
    method: 'get',
  }).then((data: any) => {
    return (data.list || []) as any[]
  })
}