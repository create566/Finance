<script setup lang="ts">
import { computed } from 'vue'
import MoneyText from './MoneyText.vue'
import StatusTag from './StatusTag.vue'

/**
 * 标的卡片。
 *
 * 改造前 `invest/list.vue` 的 `.recommend-item` 与 `invest/detail.vue` 的
 * `.similar-item` 结构与样式几乎一致，却各写了一份（含约 60 行重复 CSS）。
 *
 * 字段以显式 props 传入，不绑定具体接口结构，便于两处复用。
 */
const props = withDefaults(
  defineProps<{
    name: string
    /** `lend.status` 0~7，走统一状态表 */
    status: number
    /** 年化利率（百分数，如 8.5） */
    rate: number
    /** 期限文案，如「12 个月」 */
    period?: string
    /** 起投金额 */
    minAmount?: number
    /** 剩余可投金额 */
    remainAmount?: number
    /** 募集进度 0~100 */
    progress?: number
    /** 进度下方左侧文案 */
    progressText?: string
    /** 进度下方右侧文案 */
    footText?: string
    /** 展示形态：网格卡 / 详情页相似标的 */
    mode?: 'grid' | 'similar'
    /** 名称右侧的小角标（如推荐来源「规则推荐」），与状态标签并列 */
    badge?: string
    /** 一句话推荐理由，橙色小字 */
    reason?: string
    /** 标签组（如「复投」「协同推荐」） */
    tags?: string[]
    /** 操作按钮文案 */
    actionText?: string
    /** 按钮是否为次要样式（不可投标时用） */
    actionPlain?: boolean
  }>(),
  {
    mode: 'grid',
    actionText: '立即投标',
    actionPlain: false,
  },
)

const emit = defineEmits<{
  (e: 'action'): void
  (e: 'select'): void
}>()

const clampedProgress = computed(() => {
  const p = props.progress ?? 0
  return Math.min(100, Math.max(0, p))
})

/** 满标后进度条转为完成色，逾期转警示色，避免「满标还显示蓝色进度」的误导 */
const barStyle = computed(() => {
  if (props.status === 3) return { background: 'var(--color-danger)' }
  if (props.status === 6 || props.status === 5) return { background: 'var(--color-warning)' }
  return { background: 'var(--gradient-brand)' }
})

function onAction() {
  emit('action')
}

function onSelect() {
  emit('select')
}
</script>

<template>
  <div
    class="lend-card app-card"
    :class="[`lend-card--${mode}`, { 'app-card--hover': mode === 'grid' }]"
    @click="onSelect"
  >
    <div class="lend-card__head">
      <div class="lend-card__name-wrap">
        <span class="lend-card__name">{{ name }}</span>
        <span v-if="badge" class="lend-card__badge">{{ badge }}</span>
      </div>
      <StatusTag :status="status" kind="lend" />
    </div>

    <div class="lend-card__rate">
      <span class="lend-card__rate-value">{{ rate.toFixed(2) }}</span>
      <span class="lend-card__rate-unit">%</span>
      <span class="lend-card__rate-label">年化利率</span>
    </div>

    <div v-if="reason" class="lend-card__reason">「{{ reason }}」</div>

    <div class="lend-card__meta">
      <div v-if="period" class="lend-card__meta-item">
        <span class="lend-card__meta-label">期限</span>
        <span class="lend-card__meta-value">{{ period }}</span>
      </div>
      <div v-if="minAmount !== undefined" class="lend-card__meta-item">
        <span class="lend-card__meta-label">起投</span>
        <span class="lend-card__meta-value"><MoneyText :value="minAmount" size="sm" /></span>
      </div>
      <div v-if="remainAmount !== undefined" class="lend-card__meta-item">
        <span class="lend-card__meta-label">剩余可投</span>
        <span class="lend-card__meta-value"><MoneyText :value="remainAmount" size="sm" /></span>
      </div>
    </div>

    <div v-if="progress !== undefined" class="lend-card__progress">
      <div class="lend-card__bar">
        <div class="lend-card__bar-inner" :style="{ width: `${clampedProgress}%`, ...barStyle }" />
      </div>
      <div class="lend-card__progress-foot">
        <span>{{ progressText ?? `已募 ${clampedProgress}%` }}</span>
        <span v-if="footText">{{ footText }}</span>
      </div>
    </div>

    <div v-if="tags && tags.length" class="lend-card__tags">
      <el-tag
        v-for="t in tags"
        :key="t"
        size="small"
        effect="light"
        :type="t === '复投' ? 'danger' : t === '协同推荐' ? 'primary' : 'warning'"
      >
        {{ t }}
      </el-tag>
    </div>

    <button
      class="lend-card__action"
      :class="{ 'lend-card__action--plain': actionPlain }"
      type="button"
      @click.stop="onAction"
    >{{ actionText }}</button>
  </div>
</template>

<style scoped>
.lend-card {
  display: flex;
  flex-direction: column;
  gap: var(--space-3);
  padding: var(--space-4);
  cursor: pointer;
}
.lend-card__head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: var(--space-2);
}
.lend-card__name-wrap {
  display: flex;
  align-items: center;
  gap: 6px;
  min-width: 0;
}
.lend-card__name {
  font-size: var(--font-sm);
  font-weight: 600;
  color: var(--text-primary);
  line-height: var(--lh-tight);
  display: -webkit-box;
  -webkit-line-clamp: 1;
  -webkit-box-orient: vertical;
  overflow: hidden;
}
.lend-card__badge {
  flex-shrink: 0;
  font-size: 11px;
  line-height: 1;
  padding: 3px 6px;
  border-radius: var(--radius-sm);
  color: var(--color-primary);
  background: var(--color-primary-bg);
  border: 1px solid var(--color-primary-light);
}
.lend-card__reason {
  font-size: var(--font-xs);
  color: var(--color-warning);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.lend-card__tags {
  display: flex;
  gap: 4px;
  flex-wrap: wrap;
}
.lend-card__rate {
  display: flex;
  align-items: baseline;
  gap: 3px;
}
.lend-card__rate-value {
  font-size: var(--font-xl);
  font-weight: 600;
  color: var(--color-danger);
  font-variant-numeric: tabular-nums;
}
.lend-card__rate-unit {
  font-size: var(--font-xs);
  color: var(--color-danger);
}
.lend-card__rate-label {
  font-size: var(--font-xs);
  color: var(--text-placeholder);
  margin-left: auto;
}
.lend-card__meta {
  display: flex;
  gap: var(--space-4);
}
.lend-card__meta-item {
  min-width: 0;
}
.lend-card__meta-label {
  display: block;
  font-size: var(--font-xs);
  color: var(--text-placeholder);
}
.lend-card__meta-value {
  display: block;
  font-size: var(--font-xs);
  color: var(--text-regular);
  margin-top: 2px;
}
.lend-card__bar {
  height: 6px;
  border-radius: var(--radius-full);
  background: var(--border-light);
  overflow: hidden;
}
.lend-card__bar-inner {
  height: 100%;
  border-radius: var(--radius-full);
  transition: width var(--transition-base);
}
.lend-card__progress-foot {
  display: flex;
  justify-content: space-between;
  font-size: var(--font-xs);
  color: var(--text-placeholder);
  margin-top: 6px;
}
.lend-card__action {
  margin-top: auto;
  height: 34px;
  border-radius: var(--radius-base);
  border: none;
  font-family: inherit;
  font-size: var(--font-sm);
  font-weight: 500;
  color: #fff;
  background: var(--gradient-brand);
  box-shadow: 0 5px 14px rgba(91, 134, 229, 0.3);
  cursor: pointer;
  transition: all var(--transition-base);
}
.lend-card__action:hover {
  background: var(--gradient-brand-hover);
  box-shadow: 0 8px 20px rgba(91, 134, 229, 0.4);
  transform: translateY(-1px);
}
.lend-card__action--plain {
  background: transparent;
  color: var(--text-secondary);
  border: 1px solid var(--border-base);
  box-shadow: none;
}
.lend-card__action--plain:hover {
  background: transparent;
  color: var(--color-primary);
  border-color: var(--color-primary-light);
  transform: none;
  box-shadow: none;
}
.lend-card--similar .lend-card__rate-value {
  font-size: var(--font-lg);
}
.lend-card--similar .lend-card__action {
  height: 30px;
  font-size: var(--font-xs);
}
</style>
