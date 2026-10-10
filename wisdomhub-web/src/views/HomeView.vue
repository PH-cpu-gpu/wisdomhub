<script setup lang="ts">
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
    <ArticleList>
      <template #heading>
        <h1 class="home__title">文章</h1>
        <span class="home__eyebrow wh-mono">最新动态</span>
      </template>
    </ArticleList>
  </div>
</template>

<style scoped>
/* 列表页铺满主列，宽度上限只有阅读页才需要 —— 那是给长文阅读留的 */
.home {
  /* 占满首屏，把页脚介绍压到首屏之外，进入后往下划一下才看到 */
  min-height: calc(100vh - var(--wh-nav-height));
  padding-block: var(--wh-space-4);
}

.home__title {
  font-size: var(--wh-text-xl);
}

.home__eyebrow {
  font-size: var(--wh-text-xs);
  letter-spacing: var(--wh-tracking-wide);
  color: var(--wh-accent);
}
</style>
