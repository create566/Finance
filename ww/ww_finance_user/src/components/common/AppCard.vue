<script setup lang="ts">
/**
 * 统一内容卡片：圆角 / 阴影 / 卡头（标题 + 副标题 + 右侧操作槽）。
 *
 * 改造前各页直接裸用 `el-card`，圆角、内边距、卡头样式各不相同；
 * 「标题 + 副标题」这种卡头结构在 `invest/list.vue` 与 `invest/detail.vue`
 * 被各写一遍且视觉几乎相同。
 */
withDefaults(
  defineProps<{
    title?: string
    subtitle?: string
    /** 是否启用 hover 浮起（用于可点击卡片） */
    hoverable?: boolean
    /** 内容区是否无内边距（用于表格、列表等自带边距的内容） */
    bodyFlush?: boolean
  }>(),
  { hoverable: false, bodyFlush: false },
)
</script>

<template>
  <section class="app-card" :class="{ 'app-card--hover': hoverable }">
    <header v-if="title || $slots.extra" class="app-card__header">
      <div class="app-card__heading">
        <span class="section-bar" />
        <div class="app-card__heading-text">
          <div class="app-card__title">{{ title }}</div>
          <div v-if="subtitle" class="app-card__sub">{{ subtitle }}</div>
        </div>
      </div>
      <div v-if="$slots.extra" class="app-card__extra">
        <slot name="extra" />
      </div>
    </header>
    <div class="app-card__body" :class="{ 'app-card__body--flush': bodyFlush }">
      <slot />
    </div>
  </section>
</template>

<style scoped>
.app-card__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-3);
  padding: var(--space-4) var(--space-4) var(--space-3);
  border-bottom: 1px solid var(--border-light);
}
.app-card__heading {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
}
.app-card__heading-text {
  min-width: 0;
}
.app-card__title {
  font-size: var(--font-sm);
  font-weight: 600;
  color: var(--text-primary);
}
.app-card__sub {
  font-size: var(--font-xs);
  color: var(--text-placeholder);
  margin-top: 2px;
}
.app-card__extra {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  flex-shrink: 0;
}
.app-card__body {
  padding: var(--space-4);
}
.app-card__body--flush {
  padding: 0;
}
</style>
