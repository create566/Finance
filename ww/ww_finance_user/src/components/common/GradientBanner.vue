<script setup lang="ts">
import { computed } from 'vue'

/**
 * 渐变头：页面级品牌表达 + 关键信息承载。
 *
 * 改造前 `.page-banner`（`invest/return.vue`、`borrow/repay.vue`）与
 * `.auth-banner`（认证相关页）共有 5 处近似的渐变实现，配色各不相同
 * （`#11998e→#38ef7d`、`#667eea→#764ba2` 等）。
 */
const props = withDefaults(
  defineProps<{
    title: string
    desc?: string
    gradient?: 'brand' | 'accent'
    /** 紧凑模式：用于列表页顶部，不抢内容视觉 */
    compact?: boolean
  }>(),
  { gradient: 'brand', compact: false },
)

const background = computed(() =>
  props.gradient === 'accent' ? 'var(--gradient-accent)' : 'var(--gradient-brand)',
)
</script>

<template>
  <div
    class="gbanner"
    :class="{ 'gbanner--compact': compact }"
    :style="{ background }"
  >
    <div class="gbanner__main">
      <h2 class="gbanner__title">{{ title }}</h2>
      <p v-if="desc" class="gbanner__desc">{{ desc }}</p>
    </div>
    <div v-if="$slots.default" class="gbanner__extra">
      <slot />
    </div>
  </div>
</template>

<style scoped>
.gbanner {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-4);
  border-radius: var(--radius-xl);
  padding: var(--space-5) var(--space-6);
  box-shadow: var(--shadow-brand);
}
.gbanner--compact {
  padding: var(--space-4) var(--space-5);
}
.gbanner__main {
  min-width: 0;
}
.gbanner__title {
  font-size: var(--font-lg);
  font-weight: 600;
  color: #fff;
  letter-spacing: 0.3px;
}
.gbanner--compact .gbanner__title {
  font-size: var(--font-md);
}
.gbanner__desc {
  font-size: var(--font-xs);
  color: rgba(255, 255, 255, 0.85);
  margin-top: 5px;
}
.gbanner__extra {
  flex-shrink: 0;
}
</style>
