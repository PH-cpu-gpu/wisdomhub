<script setup lang="ts">
import { RouterLink } from 'vue-router'

import ArticleList from '@/components/article/ArticleList.vue'

/**
 * F1.2 首页文章列表。
 *
 * 页面本身只剩标题 —— 分页、排序、骨架屏、URL 同步都在 `ArticleList` 里，
 * 标签详情（F1.5）、个人主页（F1.6）、我的收藏（F1.8）复用同一个组件。
 */
</script>

<template>
  <div class="home">
    <section class="home__intro" aria-labelledby="home-statement">
      <h1 id="home-statement" class="home__statement">
        技术知识，连成<span class="home__statement-accent">一张图</span>。
      </h1>
      <p class="home__lede">
        写文章、提问题，内容背后的<span class="home__nowrap">技术实体</span>自动<span class="home__nowrap">连成知识图谱；</span><span class="home__nowrap">AI 导师</span><span class="home__nowrap">沿着图谱</span>回答问题、给出<span class="home__nowrap">学习路径。</span>
      </p>
      <div class="home__links">
        <RouterLink class="home__link" to="/graph">
          探索知识图谱
          <svg class="home__arrow" viewBox="0 0 16 16" aria-hidden="true">
            <path
              d="M3 8h9M8.5 4.5 12 8l-3.5 3.5"
              fill="none"
              stroke="currentColor"
              stroke-width="1.4"
              stroke-linecap="round"
              stroke-linejoin="round"
            />
          </svg>
        </RouterLink>
        <RouterLink class="home__link" to="/ai">
          与 AI 导师聊聊
          <svg class="home__arrow" viewBox="0 0 16 16" aria-hidden="true">
            <path
              d="M3 8h9M8.5 4.5 12 8l-3.5 3.5"
              fill="none"
              stroke="currentColor"
              stroke-width="1.4"
              stroke-linecap="round"
              stroke-linejoin="round"
            />
          </svg>
        </RouterLink>
      </div>
    </section>

    <ArticleList>
      <template #heading>
        <h2 class="home__list-title">最新文章</h2>
      </template>
    </ArticleList>
  </div>
</template>

<style scoped>
/* 列表页铺满主列，宽度上限只有阅读页才需要 —— 那是给长文阅读留的 */
.home {
  /* 占满首屏，把页脚介绍压到首屏之外，进入后往下划一下才看到 */
  min-height: calc(100vh - var(--wh-nav-height));
  padding-block: var(--wh-space-6) var(--wh-space-8);
}

.home__intro {
  max-width: 48rem;
  padding-bottom: var(--wh-space-6);
}

.home__statement {
  font-size: var(--wh-text-3xl);
  font-weight: var(--wh-weight-semibold);
  line-height: var(--wh-leading-tight);
  letter-spacing: var(--wh-tracking-tight);
}

.home__statement-accent {
  color: var(--wh-accent);
}

.home__lede {
  max-width: 48rem;
  margin-top: var(--wh-space-3);

  font-size: var(--wh-text-reading);
  line-height: var(--wh-leading-reading);
  color: var(--wh-text-2);
  text-wrap: balance;
}

.home__nowrap {
  white-space: nowrap;
}

.home__links {
  display: flex;
  flex-wrap: wrap;
  gap: var(--wh-space-3) var(--wh-space-5);
  margin-top: var(--wh-space-5);
}

.home__link {
  display: inline-flex;
  gap: var(--wh-space-2);
  align-items: center;

  font-size: var(--wh-text-sm);
  font-weight: var(--wh-weight-medium);
  color: var(--wh-accent);
  text-decoration: none;
}

.home__link:hover {
  color: var(--wh-accent-hover);
  text-decoration: none;
}

.home__arrow {
  width: 14px;
  height: 14px;
  transition: transform var(--wh-duration-fast) var(--wh-ease);
}

.home__link:hover .home__arrow {
  transform: translateX(2px);
}

.home__list-title {
  font-size: var(--wh-text-xl);
}

@media (max-width: 600px) {
  .home__statement {
    font-size: var(--wh-text-page);
  }

  .home__intro {
    padding-bottom: var(--wh-space-5);
  }
}
</style>
