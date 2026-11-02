<script setup lang="ts">
/**
 * 页面外壳：统一标题区（标题 + 副标题 + 右侧操作槽）。
 *
 * 改造前 `page-container` / `page-header` 在 13 个页面的 `<style scoped>` 里
 * 各写一遍，字号、间距、颜色都不完全一致。
 */
defineProps<{
  title: string
  /** 标题下方的摘要，用于承载统计口径等辅助信息 */
  subtitle?: string
  /** 是否去掉底部间距（用于页面内还有自定义分区的场景） */
  flush?: boolean
}>()
</script>

<template>
  <div class="app-page">
    <div class="app-page__header" :class="{ 'app-page__header--flush': flush }">
      <div class="app-page__heading">
        <h2 class="app-page__title">{{ title }}</h2>
        <p v-if="subtitle" class="app-page__sub">{{ subtitle }}</p>
      </div>
      <div v-if="$slots.actions" class="app-page__actions">
        <slot name="actions" />
      </div>
    </div>
    <slot />
  </div>
</template>

<style scoped>
.app-page {
  width: 100%;
}
.app-page__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-4);
  margin-bottom: var(--space-4);
}
.app-page__header--flush {
  margin-bottom: 0;
}
.app-page__heading {
  min-width: 0;
}
.app-page__title {
  font-size: var(--font-lg);
  font-weight: 600;
  color: var(--text-primary);
  letter-spacing: 0.2px;
}
.app-page__sub {
  font-size: var(--font-xs);
  color: var(--text-placeholder);
  margin-top: 4px;
}
.app-page__actions {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  flex-shrink: 0;
}
</style>
