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
