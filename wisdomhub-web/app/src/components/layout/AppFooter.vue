<script setup lang="ts">
import { RouterLink } from 'vue-router'

import BrandMark from './BrandMark.vue'

const groups = [
  {
    title: '内容',
    links: [
      { label: '文章', to: '/articles' },
      { label: '问答', to: '/questions' },
      { label: '提问', to: '/ask' },
      { label: '标签', to: '/tags' },
    ],
  },
  {
    title: '探索',
    links: [
      { label: '知识图谱', to: '/graph' },
      { label: 'AI 导师', to: '/ai' },
      { label: '搜索', to: '/search' },
      { label: '专栏', to: '/columns' },
    ],
  },
  {
    title: '我的',
    links: [
      { label: '收藏', to: '/me/favorites' },
      { label: '浏览历史', to: '/me/history' },
      { label: '我的文章', to: '/me/articles' },
      { label: '我的专栏', to: '/me/columns' },
      { label: '已购专栏', to: '/me/purchased' },
      { label: '订单', to: '/me/orders' },
      { label: '账号设置', to: '/me/settings' },
    ],
  },
]
</script>

<template>
  <footer class="app-footer">
    <div class="wh-container">
      <div class="app-footer__top">
        <div class="app-footer__brand">
          <BrandMark to="/home" tone="inverse" />
          <p>把散落的知识，连成一张图。文章、问答、搜索、图谱与 AI，共用同一张知识网络。</p>
        </div>

        <div v-for="group in groups" :key="group.title" class="app-footer__col">
          <h3>{{ group.title }}</h3>
          <RouterLink v-for="link in group.links" :key="link.to" :to="link.to">
            {{ link.label }}
          </RouterLink>
        </div>
      </div>

      <div class="app-footer__bottom">
        <span>WISDOMHUB · 2026</span>
        <span>内容 · 搜索 · 图谱 · 推荐 · AI</span>
      </div>
    </div>
  </footer>
</template>

<style scoped>
.app-footer {
  background: var(--ink);
  color: var(--bone);
}

.app-footer__top {
  display: grid;
  grid-template-columns: 2fr 1fr 1fr 1fr;
  gap: clamp(28px, 4vw, 56px);
  padding-block: clamp(40px, 7vw, 72px);
}

.app-footer__brand p {
  max-width: 30ch;
  margin-top: var(--space-4);
  color: rgb(242 237 223 / 52%);
  font-size: var(--text-sm);
  line-height: 1.85;
}

.app-footer__col {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 11px;
}

.app-footer__col h3 {
  margin: 0 0 8px;
  color: var(--acid);
  font-family: var(--mono);
  font-size: 9px;
  font-weight: var(--weight-regular);
  letter-spacing: 0.22em;
  text-transform: uppercase;
}

.app-footer__col a {
  color: rgb(242 237 223 / 62%);
  font-size: var(--text-sm);
  transition: color var(--dur) var(--ease);
}

.app-footer__col a:hover {
  color: var(--acid);
}

.app-footer__bottom {
  display: flex;
  flex-wrap: wrap;
  justify-content: space-between;
  gap: var(--space-2) var(--space-6);
  padding-block: var(--space-4);
  border-top: 1px solid var(--line-inverse);
  color: rgb(242 237 223 / 48%);
  font-family: var(--mono);
  font-size: var(--text-2xs);
  letter-spacing: 0.08em;
}

@media (max-width: 959px) {
  .app-footer__top {
    grid-template-columns: 1fr 1fr;
  }

  .app-footer__brand {
    grid-column: 1 / -1;
  }
}

@media (max-width: 639px) {
  .app-footer__top {
    grid-template-columns: 1fr;
  }

  .app-footer__brand {
    grid-column: auto;
  }
}
</style>
