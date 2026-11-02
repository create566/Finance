<script setup lang="ts">
import { computed } from 'vue'

/**
 * 统一空状态。
 *
 * 改造前 `el-empty`（4 个页面）与 `el-table` 的 `empty-text`（3 个页面）混用，
 * 空数据的观感与文案风格都不一致。
 */
const props = withDefaults(
  defineProps<{
    description?: string
    size?: 'sm' | 'base'
  }>(),
  { description: '暂无数据', size: 'base' },
)

const padding = computed(() => (props.size === 'sm' ? '18px 0' : '40px 0'))
const iconSize = computed(() => (props.size === 'sm' ? 34 : 46))
</script>

<template>
  <div class="app-empty" :style="{ padding }">
    <slot name="icon">
      <div class="app-empty__icon" :style="{ width: `${iconSize}px`, height: `${iconSize}px` }">
        <span class="app-empty__glyph" />
      </div>
    </slot>
    <p class="app-empty__desc">{{ description }}</p>
    <div v-if="$slots.action" class="app-empty__action">
      <slot name="action" />
    </div>
  </div>
</template>

<style scoped>
.app-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: var(--space-3);
}
.app-empty__icon {
  border-radius: var(--radius-full);
  background: var(--gradient-brand-soft);
  border: 1px solid var(--border-light);
  display: flex;
  align-items: center;
  justify-content: center;
}
.app-empty__glyph {
  width: 38%;
  height: 38%;
  border-radius: 5px;
  background: var(--color-primary-light);
  opacity: 0.7;
}
.app-empty__desc {
  font-size: var(--font-sm);
  color: var(--text-placeholder);
}
.app-empty__action {
  margin-top: var(--space-1);
}
</style>
