<script setup lang="ts">
import { computed } from 'vue'
import {
  AUTH_STATUS_MAP,
  BORROW_APPLY_STATUS_MAP,
  INVEST_RETURN_STATUS_MAP,
  LEND_ITEM_STATUS_MAP,
  LEND_STATUS_MAP,
  LOAN_GROUP_STATUS_MAP,
  REPAY_STATUS_MAP,
  USER_TYPE_MAP,
  type EnumTag,
} from '@/utils/enumText'

/**
 * 状态标签：把「编码 → 文案 + 配色」统一到一处。
 *
 * 改造前每个页面各写一份映射表（`authStatusMap` × 2、`returnMethodMap` × 3、
 * `statusMap` × 2、`loanStatusMap` / `applyStatusMap` …），很容易漂移。
 *
 * 语义色统一为：成功绿 / 进行中蓝 / 待处理橙 / 异常红 / 中性灰。
 * ⚠️ 所有状态都渲染文字，不单靠颜色区分（无障碍要求）。
 */
type PresetKind =
  | 'auth'
  | 'userType'
  | 'lend'
  | 'lendItem'
  | 'investReturn'
  | 'repay'
  | 'borrowApply'
  | 'loanGroup'

const PRESET: Record<PresetKind, Record<string | number, EnumTag>> = {
  auth: AUTH_STATUS_MAP,
  userType: USER_TYPE_MAP,
  lend: LEND_STATUS_MAP,
  lendItem: LEND_ITEM_STATUS_MAP,
  investReturn: INVEST_RETURN_STATUS_MAP,
  repay: REPAY_STATUS_MAP,
  borrowApply: BORROW_APPLY_STATUS_MAP,
  loanGroup: LOAN_GROUP_STATUS_MAP,
}

const props = withDefaults(
  defineProps<{
    status?: number | string | null
    /** 使用内置映射表 */
    kind?: PresetKind
    /** 传入自定义映射表时优先于 kind */
    map?: Record<string | number, EnumTag>
    size?: 'small' | 'default' | 'large'
  }>(),
  { size: 'small' },
)

const meta = computed<EnumTag>(() => {
  const table = props.map ?? (props.kind ? PRESET[props.kind] : undefined)
  if (!table || props.status === null || props.status === undefined) {
    return { text: '未知', type: 'info' }
  }
  return table[props.status] ?? { text: '未知', type: 'info' }
})
</script>

<template>
  <el-tag :type="meta.type" :size="size" effect="light" class="status-tag">{{ meta.text }}</el-tag>
</template>

<style scoped>
.status-tag {
  border: none;
  font-weight: 500;
}
</style>
