<script setup lang="ts">
import { computed } from 'vue'
import MoneyText from './MoneyText.vue'

/**
 * 指标卡：渐变图标 + 标签 + 数值 + 可选趋势。
 *
 * 改造前 `home/index.vue` 的 `.stat-card` 与 `account/overview.vue` 的
 * `.amount-card` 是同一种东西的两套实现。
 */
const props = withDefaults(
  defineProps<{
    label: string
    /** Element Plus 图标组件名（已全局注册），如 'Wallet' / 'TrendCharts' */
    icon?: string
    /** 数值（走金额格式化） */
    value?: number | string | null
    /** 非金额的纯文本值，如「4 笔」，与 value 二选一 */
    text?: string
    gradient?: 'brand' | 'accent' | 'teal' | 'warm'
    /** 趋势文案，如「+3.2% 较上月」 */
    trend?: string
    /** 趋势方向：true 上行（红），false 下行（绿） */
    trendUp?: boolean
  }>(),
  { gradient: 'brand', trendUp: true },
)

const GRADIENTS: Record<NonNullable<typeof props.gradient>, string> = {
  brand: 'var(--gradient-brand)',
  accent: 'var(--gradient-accent)',
  teal: 'linear-gradient(135deg, #36d1dc, #5b86e5)',
  warm: 'linear-gradient(135deg, #f093fb, #f5576c)',
}

const background = computed(() => GRADIENTS[props.gradient] ?? GRADIENTS.brand)
</script>

<template>
  <div class="stat-card app-card">
    <div class="stat-card__top">
      <span class="stat-card__icon" :style="{ background }">
        <component :is="icon" v-if="icon" class="stat-card__glyph" />
      </span>
      <span class="stat-card__label">{{ label }}</span>
    </div>
    <div class="stat-card__value">
      <MoneyText v-if="value !== undefined && value !== null" :value="value" size="lg" strong />
      <template v-else>{{ text ?? '-' }}</template>
    </div>
    <div v-if="trend" class="stat-card__trend" :style="{ color: trendUp ? 'var(--color-danger)' : 'var(--color-success)' }">
      {{ trend }}
    </div>
  </div>
</template>

<style scoped>
.stat-card {
  padding: var(--space-4);
}
.stat-card__top {
  display: flex;
  align-items: center;
  gap: 8px;
}
.stat-card__icon {
  width: 30px;
  height: 30px;
  border-radius: 9px;
  flex-shrink: 0;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  color: #fff;
}
.stat-card__glyph {
  width: 16px;
  height: 16px;
}
.stat-card__label {
  font-size: var(--font-xs);
  color: var(--text-secondary);
}
.stat-card__value {
  font-size: var(--font-lg);
  font-weight: 600;
  color: var(--text-primary);
  margin-top: var(--space-3);
}
.stat-card__trend {
  font-size: var(--font-xs);
  margin-top: 6px;
}
</style>
