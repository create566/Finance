<script lang="ts">
export interface MetricItem {
  label: string
  /** 数值（走金额格式化） */
  value?: number | string | null
  /** 非金额纯文本，如「4 笔」 */
  text?: string
  /** 纯文本后缀单位 */
  unit?: string
  /** 数值是否按正负着色（红收绿支） */
  colored?: boolean
}

export type MetricTone = 'default' | 'primary' | 'danger' | 'success'
</script>

<script setup lang="ts">
import { computed } from 'vue'
import MoneyText from './MoneyText.vue'

/**
 * 指标行：列表页顶部的汇总指标组（「指标前置」布局原则的载体）。
 *
 * 改造前「我的投资」「我的借款」「回款计划」「资金流水」等页面直接进入表格，
 * 用户看不出总量，只能逐行累加。
 */
const props = withDefaults(
  defineProps<{
    items: MetricItem[]
    /** 每行显示几个，默认 4 */
    cols?: number
    /** 数值强调色 */
    tone?: MetricTone
  }>(),
  { cols: 4, tone: 'default' },
)

const gridStyle = computed(() => ({
  gridTemplateColumns: `repeat(${props.cols}, minmax(0, 1fr))`,
}))

const toneColor = computed<string>(() => {
  switch (props.tone) {
    case 'primary':
      return 'var(--color-primary)'
    case 'danger':
      return 'var(--color-danger)'
    case 'success':
      return 'var(--color-success)'
    default:
      return 'var(--text-primary)'
  }
})
</script>

<template>
  <div class="metric-row" :style="gridStyle">
    <div v-for="item in items" :key="item.label" class="metric-item">
      <div class="metric-item__label">{{ item.label }}</div>
      <div class="metric-item__value" :style="{ color: toneColor }">
        <MoneyText
          v-if="item.value !== undefined && item.value !== null"
          :value="item.value"
          size="lg"
          strong
          :colored="item.colored"
        />
        <template v-else>
          {{ item.text ?? '-' }}<span v-if="item.unit" class="metric-item__unit">{{ item.unit }}</span>
        </template>
      </div>
    </div>
  </div>
</template>

<style scoped>
.metric-row {
  display: grid;
  gap: var(--space-3);
}
.metric-item {
  background: var(--bg-card);
  border: 1px solid var(--border-light);
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-base);
  padding: var(--space-4);
}
.metric-item__label {
  font-size: var(--font-xs);
  color: var(--text-placeholder);
}
.metric-item__value {
  font-size: var(--font-lg);
  font-weight: 600;
  margin-top: 8px;
}
.metric-item__unit {
  font-size: var(--font-xs);
  font-weight: 400;
  margin-left: 3px;
}
</style>
