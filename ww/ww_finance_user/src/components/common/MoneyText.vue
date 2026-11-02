<script setup lang="ts">
import { computed } from 'vue'

/**
 * 金额统一展示：千分位 + 等宽数字 + 可选正负着色。
 *
 * 改造前全站有 4 处自定义 `.money` 样式 + 30 余处内联 `toFixed(2)`，
 * 导致同一笔钱在不同页面精度、分隔符、对齐方式都不一致。
 *
 * 着色遵循国内习惯：**正数（收入/收益）为红，负数（支出）为绿**。
 */
const props = withDefaults(
  defineProps<{
    value?: number | string | null
    /** 货币符号，默认 ¥ */
    prefix?: string
    /** 小数位，默认 2 */
    precision?: number
    /** 是否按正负着色（红收绿支），默认不着色 */
    colored?: boolean
    size?: 'sm' | 'base' | 'lg' | 'xl'
    strong?: boolean
  }>(),
  { prefix: '¥', precision: 2, colored: false, size: 'base', strong: false },
)

const numeric = computed<number>(() => {
  const raw = typeof props.value === 'string' ? Number(props.value) : props.value
  return typeof raw === 'number' && Number.isFinite(raw) ? raw : 0
})

const text = computed<string>(() => {
  const sign = numeric.value < 0 ? '-' : ''
  const body = Math.abs(numeric.value).toLocaleString('zh-CN', {
    minimumFractionDigits: props.precision,
    maximumFractionDigits: props.precision,
  })
  return `${sign}${props.prefix}${body}`
})

const tone = computed<string>(() => {
  if (!props.colored) return 'inherit'
  if (numeric.value > 0) return 'var(--color-danger)'
  if (numeric.value < 0) return 'var(--color-success)'
  return 'inherit'
})
</script>

<template>
  <span
    class="money-text"
    :class="`money-text--${size}`"
    :style="{ color: tone, fontWeight: strong ? 600 : undefined }"
  >{{ text }}</span>
</template>

<style scoped>
.money-text {
  font-variant-numeric: tabular-nums;
  font-feature-settings: 'tnum';
}
.money-text--sm {
  font-size: var(--font-xs);
}
.money-text--base {
  font-size: var(--font-base);
}
.money-text--lg {
  font-size: var(--font-lg);
}
.money-text--xl {
  font-size: var(--font-2xl);
  letter-spacing: 0.3px;
}
</style>
