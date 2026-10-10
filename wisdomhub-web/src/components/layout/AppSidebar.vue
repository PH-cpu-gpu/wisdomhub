<script setup lang="ts">
import { EditPen } from '@element-plus/icons-vue'
import { RouterLink, useRoute } from 'vue-router'

import { accountNav } from '@/config/navigation'
import { useUserStore } from '@/stores/user'

/**
 * 侧边栏。
 *
 * 桌面端是常驻的一列，窄屏时由 DefaultLayout 把它变成抽屉 ——
 * 组件本身只负责内容，位置与开合都交给布局，
 * 这样同一份内容不需要为两种形态各写一遍。
 *
 * 里面的「热门技术」「我的」都是入口性质的内容，不放业务逻辑。
 */
const route = useRoute()
const user = useUserStore()

function isActive(to: string): boolean {
  return route.path === to || route.path.startsWith(`${to}/`)
}
</script>

<template>
  <aside class="side">
    <!-- 写文章：全站最主要的一个动作，放最上面 -->
    <RouterLink class="side__cta" to="/editor">
      <el-icon><EditPen /></el-icon>
      写文章
    </RouterLink>

    <!-- 我的：未登录时整组不出现，而不是显示一堆点了就跳登录的入口 -->
    <section v-if="user.isLoggedIn" class="side__group">
      <h2 class="side__title">我的</h2>
      <ul class="side__list">
        <li v-for="item in accountNav" :key="item.to">
          <RouterLink
            class="side__link"
            :class="{ 'is-active': isActive(item.to) }"
            :to="item.to"
          >
            <el-icon><component :is="item.icon" /></el-icon>
            {{ item.label }}
          </RouterLink>
        </li>
      </ul>
    </section>
  </aside>
</template>

<style scoped>
.side {
  display: flex;
  flex-direction: column;
  gap: var(--wh-space-6);

  /* 跟着页面滚动，长文页也能随时切走 */
  position: sticky;
  top: calc(var(--wh-nav-height) + var(--wh-space-4));
}

/* ---------- 写文章 ---------- */
.side__cta {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: var(--wh-space-2);

  padding: var(--wh-space-3) var(--wh-space-4);

  font-weight: var(--wh-weight-medium);
  color: var(--wh-text-on-fill);
  background-color: var(--wh-accent);
  border-radius: var(--wh-radius-md);
  text-decoration: none;

  transition: background-color var(--wh-duration-fast) var(--wh-ease);
}

.side__cta:hover {
  color: var(--wh-text-on-fill);
  background-color: var(--wh-accent-hover);
  text-decoration: none;
}

/* ---------- 分组 ---------- */
.side__group {
  display: flex;
  flex-direction: column;
  gap: var(--wh-space-2);
}

.side__title {
  font-size: var(--wh-text-xs);
  font-weight: var(--wh-weight-medium);
  letter-spacing: var(--wh-tracking-wide);
  color: var(--wh-text-3);
}

.side__list {
  display: flex;
  flex-direction: column;
  margin: 0;
  padding: 0;
  list-style: none;
}

.side__link {
  display: flex;
  align-items: center;
  gap: var(--wh-space-2);

  padding: var(--wh-space-2) var(--wh-space-3);

  font-size: var(--wh-text-sm);
  color: var(--wh-text-2);
  text-decoration: none;
  border-radius: var(--wh-radius-sm);

  transition:
    color var(--wh-duration-fast) var(--wh-ease),
    background-color var(--wh-duration-fast) var(--wh-ease);
}

.side__link:hover {
  color: var(--wh-text-1);
  background-color: var(--wh-bg-raised);
  text-decoration: none;
}

.side__link.is-active {
  color: var(--wh-accent);
  background-color: var(--wh-accent-soft);
}
</style>
