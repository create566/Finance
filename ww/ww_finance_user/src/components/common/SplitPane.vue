<script setup lang="ts">
/**
 * 两栏骨架：左侧内容 + 右侧常驻操作卡。
 *
 * 「内容与操作分栏」是本轮布局重构的核心手段 —— 改造前标的详情的
 * 投标金额输入被埋在页面第 3 屏，用户要滚 2 屏才能投标。
 *
 * 窄屏（≤1280px）自动堆叠为单列，操作卡取消 sticky。
 */
withDefaults(
  defineProps<{
    /** 右栏宽度档位 */
    aside?: 'narrow' | 'normal' | 'wide'
  }>(),
  { aside: 'normal' },
)
</script>

<template>
  <div class="split-pane" :class="`split-pane--${aside}`">
    <div class="split-pane__main">
      <slot />
    </div>
    <aside v-if="$slots.aside" class="split-pane__aside">
      <slot name="aside" />
    </aside>
  </div>
</template>

<style scoped>
.split-pane {
  display: grid;
  grid-template-columns: minmax(0, 1fr) var(--aside-w, 220px);
  gap: var(--space-4);
  align-items: start;
}
.split-pane--narrow {
  --aside-w: 200px;
}
.split-pane--normal {
  --aside-w: 220px;
}
.split-pane--wide {
  --aside-w: 280px;
}
.split-pane__main {
  min-width: 0;
}
.split-pane__aside {
  position: sticky;
  top: var(--space-4);
  min-width: 0;
}
@media (max-width: 1280px) {
  .split-pane {
    grid-template-columns: minmax(0, 1fr);
  }
  .split-pane__aside {
    position: static;
  }
}
</style>
