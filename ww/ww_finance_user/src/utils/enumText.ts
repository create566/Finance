/**
 * 后端枚举 → 前端展示文案的**唯一真源**。
 *
 * 为什么集中到这里：原先 `invest/list.vue`、`invest/detail.vue`、`invest/my.vue`
 * 各自维护了一份状态映射表，很快就漂移了 —— 例如 `lend.status = 2` 在一处写成
 * 「已完成」（后端真源是「已满标待放款」），且三张表都缺 5/6/7，
 * 导致库里真实存在的「还款中」标的在列表里显示成「未知」。
 *
 * 真源（改后端枚举时必须同步改这里，两处是一对）：
 * - `api/.../enums/LendStatusEnum.java`   → `lend.status`
 * - `lend_item.status` **没有独立枚举类**，语义散落在代码里：
 *   `LendServiceImpl.withdrawAllInvestments` / `LendItemServiceImpl.cancelInvest` 置 1，
 *   `LendItemServiceImpl.handleInvestNotify` 满标时批量置 2。
 *
 * ⚠️ `lend.status` 与 `lend_item.status` 是**两套不同的编码，绝不能合并成一张表**：
 * `lend_item.status = 1` 是「已撤标」，而 `lend.status = 1` 是「募资中」。
 */

export type EnumTagType = 'primary' | 'success' | 'info' | 'warning' | 'danger'

export interface EnumTag {
  text: string
  type: EnumTagType
}

/** `lend.status` —— 对应后端 `LendStatusEnum`（0~7，共 8 态） */
export const LEND_STATUS_MAP: Record<number, EnumTag> = {
  0: { text: '待发布', type: 'info' },
  1: { text: '募资中', type: 'primary' },
  2: { text: '已满标待放款', type: 'success' },
  3: { text: '已逾期', type: 'danger' },
  4: { text: '已下架', type: 'info' },
  5: { text: '还款中', type: 'primary' },
  6: { text: '已完结', type: 'success' },
  7: { text: '已流标', type: 'warning' },
}

/**
 * `lend_item.status` —— 「我的投资」记录状态（3 态）。
 *
 * 0 只表示「这笔投资所在的标的还没满标」；标的被撤标/下架/流标时置 1；
 * 满标瞬间整标的投资记录统一置 2。**没有**「已回款」状态 ——
 * 回款进度看 `lend_return` / `lend_item_return`，不要在这里造状态。
 */
export const LEND_ITEM_STATUS_MAP: Record<number, EnumTag> = {
  0: { text: '募资中', type: 'warning' },
  1: { text: '已撤标', type: 'info' },
  2: { text: '满标待放款', type: 'success' },
}

/** 取标签；未知/空值统一回落「未知」，保证列表永不空白。 */
export function enumTag(map: Record<number, EnumTag>, code: number | null | undefined): EnumTag {
  if (code === null || code === undefined) {
    return { text: '未知', type: 'info' }
  }
  return map[code] || { text: '未知', type: 'info' }
}

/**
 * 把 `{ text, tag }` 形状的**既有**映射表适配为 `EnumTag`，供 `StatusTag` 统一渲染。
 *
 * 用途：`api/contract.ts` 的 `CONTRACT_TYPE_META` / `CONTRACT_STATUS_META` 用的是
 * `{ text, tag, hint? }` 形状（字段名是 `tag` 而非 `type`）。这些表归 api 层所有，
 * 此处只做形状转换，不复制其内容，也不改动 api 层 —— 从而在不制造第二份真源的前提下
 * 让合同页的状态标签与全站其它页面共用同一套观感。
 */
export function adaptTagMap<K extends string | number>(
  src: Record<K, { text: string; tag: string }>,
): Record<string, EnumTag> {
  const out: Record<string, EnumTag> = {}
  for (const key of Object.keys(src) as unknown as K[]) {
    const meta = src[key]
    out[String(key)] = { text: meta.text, type: (meta.tag || 'info') as EnumTagType }
  }
  return out
}

/* ==================================================================
 * 以下映射表由各页面内重复定义的版本收敛而来
 * （改造前：authStatusMap × 2、returnMethodMap × 3、statusMap × 2 等共 10 处）
 *
 * ⚠️ 相邻两表编码相似但**语义不同，不可合并**：
 *    INVEST_RETURN_STATUS_MAP（投资人视角：未回款 / 已回款 / 逾期）
 *    REPAY_STATUS_MAP（借款人视角：待还款 / 已还清 / 已逾期）
 * 这与 `lend.status` 与 `lend_item.status` 的关系同理。
 * ================================================================== */

/**
 * 实名认证状态：`borrow_auth_status` 与 `invest_auth_status` **共用同一编码**。
 * 真源：`views/auth/index.vue`（认证中心）读取的两个状态字段。
 */
export const AUTH_STATUS_MAP: Record<number, EnumTag> = {
  0: { text: '未认证', type: 'info' },
  1: { text: '认证中', type: 'warning' },
  2: { text: '已通过', type: 'success' },
  [-1]: { text: '认证失败', type: 'danger' },
}

/** 用户角色 `user_type`（0 未认证 / 1 投资人 / 2 借款人） */
export const USER_TYPE_MAP: Record<number, EnumTag> = {
  0: { text: '未认证', type: 'info' },
  1: { text: '投资人', type: 'primary' },
  2: { text: '借款人', type: 'warning' },
}

/** 还款方式 `return_method`（纯文案，无颜色语义） */
export const RETURN_METHOD_TEXT: Record<number, string> = {
  1: '等额本息',
  2: '等额本金',
  3: '每月还息一次还本',
  4: '一次还本付息',
}

/** 取还款方式文案；未知回落 `-`（与金额的展示习惯保持一致） */
export function returnMethodText(code: number | null | undefined): string {
  if (code === null || code === undefined) return '-'
  return RETURN_METHOD_TEXT[code] || '-'
}

/** 回款计划状态（**投资人**视角，`invest/return.vue`） */
export const INVEST_RETURN_STATUS_MAP: Record<number, EnumTag> = {
  0: { text: '未回款', type: 'info' },
  1: { text: '已回款', type: 'success' },
  2: { text: '逾期', type: 'danger' },
}

/** 还款计划状态（**借款人**视角，`borrow/repay.vue`）—— 勿与上表合并 */
export const REPAY_STATUS_MAP: Record<number, EnumTag> = {
  0: { text: '待还款', type: 'warning' },
  1: { text: '已还清', type: 'success' },
  2: { text: '已逾期', type: 'danger' },
}

/** 借款申请状态 `borrow_info.status`（`borrow/my.vue`） */
export const BORROW_APPLY_STATUS_MAP: Record<number, EnumTag> = {
  0: { text: '草稿', type: 'info' },
  1: { text: '审核中', type: 'warning' },
  2: { text: '审核通过', type: 'success' },
  [-1]: { text: '审核拒绝', type: 'danger' },
}

/** 借款分组状态（「我的借款」按标的聚合后的三个视图，字符串键） */
export const LOAN_GROUP_STATUS_MAP: Record<string, EnumTag> = {
  repaying: { text: '还款中', type: 'warning' },
  settled: { text: '已结清', type: 'success' },
  overdue: { text: '已逾期', type: 'danger' },
}

/** 站内信已读标记 `readFlag`（0 未读 / 1 已读） */
export const MESSAGE_READ_MAP: Record<number, EnumTag> = {
  0: { text: '未读', type: 'danger' },
  1: { text: '已读', type: 'info' },
}

/** 银行卡绑定状态 `bindStatus`（0 未绑卡 / 1 已绑卡；资金存管账户开立标志） */
export const BANK_BIND_STATUS_MAP: Record<number, EnumTag> = {
  0: { text: '未绑卡', type: 'info' },
  1: { text: '已绑卡', type: 'success' },
}
