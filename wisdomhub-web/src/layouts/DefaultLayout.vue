<script setup lang="ts">
import { computed, onBeforeUnmount, ref } from 'vue'
import { RouterView, useRoute } from 'vue-router'

import AppFooter from '@/components/layout/AppFooter.vue'
import AppHeader from '@/components/layout/AppHeader.vue'
import AppSidebar from '@/components/layout/AppSidebar.vue'

/**
 * 默认布局：顶部导航 + 侧边栏 + 内容 + 页脚。
 *
 * 侧边栏的显隐由路由的 `meta.sidebar` 决定（默认显示，阅读类页面显式关掉），
 * 见 src/router/index.ts。窄屏时它变成抽屉 —— 同一个 sidebarOpen 状态，
 * 两种形态由 CSS 分别解释，不需要两套代码。
 */
const route = useRoute()

/** 与 CSS 里的断点保持一致（见下方 @media） */
const NARROW = '(max-width: 900px)'
const mediaQuery = window.matchMedia(NARROW)

const isNarrow = ref(mediaQuery.matches)

// 宽屏默认展开、窄屏默认收起：窄屏若默认展开，一进页面就盖着内容。
const sidebarOpen = ref(!mediaQuery.matches)

function onBreakpointChange(e: MediaQueryListEvent) {
  isNarrow.value = e.matches
  // 跨越断点时回到该形态下的默认值。用户在某一形态下手动开合过，
  // 换形态后按新形态的默认值来更符合预期。
  sidebarOpen.value = !e.matches
}

mediaQuery.addEventListener('change', onBreakpointChange)
onBeforeUnmount(() => mediaQuery.removeEventListener('change', onBreakpointChange))

/** 这个页面要不要侧边栏（阅读/编辑器/图谱/AI 这类宽内容页不要） */
const hasSidebar = computed(() => route.meta.sidebar !== false)

/** 桌面端：侧边栏那一列是否占位 */
const columnVisible = computed(() => hasSidebar.value && sidebarOpen.value && !isNarrow.value)

/** 窄屏：抽屉是否打开 */
const drawerOpen = computed(() => hasSidebar.value && sidebarOpen.value && isNarrow.value)

function toggleSidebar() {
  sidebarOpen.value = !sidebarOpen.value
}

function closeDrawer() {
  sidebarOpen.value = false
}
</script>

<template>
  <div class="shell">
    <AppHeader :sidebar-open="sidebarOpen" @toggle-sidebar="toggleSidebar" />

    <div class="shell__body" :class="{ 'is-full': !columnVisible }">
      <div v-if="columnVisible" class="shell__aside">
        <AppSidebar />
      </div>

      <main class="shell__main">
        <RouterView />
      </main>
    </div>

    <AppFooter />

    <!-- 窄屏抽屉：同一份 AppSidebar，只是换了个位置 -->
    <template v-if="isNarrow">
      <div v-if="drawerOpen" class="shell__backdrop" @click="closeDrawer" />
      <div v-if="drawerOpen" class="shell__drawer">
        <AppSidebar />
      </div>
    </template>
  </div>
</template>

<style scoped>
.shell {
  display: flex;
  flex-direction: column;
  min-height: 100vh;
}

.shell__body {
  display: grid;
  /* minmax(0, 1fr) 而不是 1fr：否则长内容（表格、代码块）会把整列撑破 */
  grid-template-columns: 16rem minmax(0, 1fr);
  gap: var(--wh-space-6);

  flex: 1;

  width: 100%;
  max-width: var(--wh-container);
  margin-inline: auto;
  padding: var(--wh-space-6) var(--wh-space-4);
  align-items: start;
}

/* 侧边栏收起、或这个页面不要侧边栏时，内容占满。
   注意类名加在 .shell__body 自己身上 —— 把 grid-template-columns 写在
   网格子元素上是不生效的，它只对网格容器有效。 */
.shell__body.is-full {
  grid-template-columns: minmax(0, 1fr);
}

.shell__main {
  min-width: 0; /* 同上：允许收缩，否则子元素会溢出网格 */
}

/* ---------- 窄屏抽屉 ---------- */
.shell__backdrop {
  position: fixed;
  inset: 0;
  z-index: calc(var(--wh-z-nav) - 1);
  background-color: rgb(0 0 0 / 50%);
}

.shell__drawer {
  position: fixed;
  top: var(--wh-nav-height);
  bottom: 0;
  left: 0;
  z-index: var(--wh-z-nav);

  width: min(18rem, 82vw);
  padding: var(--wh-space-5) var(--wh-space-4);

  overflow-y: auto;
  background-color: var(--wh-bg-surface);
  border-right: var(--wh-border-width) solid var(--wh-border);
}

/* 窄屏下侧边栏不再是常驻列，内容占满宽度 */
@media (max-width: 900px) {
  .shell__body {
    grid-template-columns: minmax(0, 1fr);
    padding-inline: var(--wh-space-4);
  }
}
</style>
