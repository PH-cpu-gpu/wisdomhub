<script setup lang="ts">
import { RouterLink } from 'vue-router'

import AuthorChip from '@/components/business/AuthorChip.vue'
import type { ID } from '@/types/common'
import type { ArticleListItemVO } from '@/types/content'
import type { UserBriefVO } from '@/types/user'
import { formatDateTime } from '@/utils'

withDefaults(
  defineProps<{
    article: ArticleListItemVO
    author?: UserBriefVO | null
    tags?: { tagId: ID; name: string }[]
    compact?: boolean
  }>(),
  {
    compact: false,
  },
)
</script>

<template>
  <article class="article-card" :class="{ 'is-compact': compact }">
    <div class="article-card__author">
      <AuthorChip :author="author" />
      <span class="article-card__dot" aria-hidden="true">·</span>
      <time class="article-card__time" :datetime="article.publishTime">
        {{ formatDateTime(article.publishTime, 'YYYY-MM-DD') }}
      </time>
    </div>

    <h3 class="article-card__title">
      <RouterLink :to="`/article/${article.articleId}`">{{ article.title }}</RouterLink>
    </h3>
    <p class="article-card__summary">{{ article.summary }}</p>

    <div v-if="tags?.length" class="article-card__tags">
      <RouterLink
        v-for="tag in tags"
        :key="tag.tagId"
        class="article-card__tag"
        :to="{ name: 'article-list', query: { tagId: tag.tagId } }"
      >
        {{ tag.name }}
      </RouterLink>
    </div>

    <div class="article-card__meta">
      <span
        >阅读 <b>{{ article.viewCount }}</b></span
      >
      <span
        >赞 <b>{{ article.likeCount }}</b></span
      >
      <span
        >评论 <b>{{ article.commentCount }}</b></span
      >
      <span
        >收藏 <b>{{ article.favoriteCount }}</b></span
      >
    </div>
  </article>
</template>

<style scoped>
.article-card {
  display: grid;
  gap: var(--space-2);
  padding-block: var(--space-5);
  border-top: var(--border);
}

.article-card__author {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  min-width: 0;
  color: var(--text-muted);
  font-family: var(--mono);
  font-size: var(--text-2xs);
}

.article-card__time {
  flex-shrink: 0;
  margin-left: auto;
}

.article-card__title {
  font-family: var(--serif);
  font-size: var(--text-xl);
  font-weight: var(--weight-bold);
  line-height: var(--leading-snug);
  letter-spacing: var(--tracking-tight);
}

.article-card.is-compact {
  padding-block: var(--space-4);
}

.article-card.is-compact .article-card__title {
  display: -webkit-box;
  overflow: hidden;
  font-size: clamp(1.15rem, 1.5vw, 1.4rem);
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
}

.article-card.is-compact .article-card__summary {
  display: -webkit-box;
  overflow: hidden;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
}

.article-card__title a {
  text-decoration: none;
  transition: color var(--dur) var(--ease);
}

.article-card__title a:hover {
  color: var(--verm-deep);
}

.article-card__summary {
  max-width: 72ch;
  color: var(--text-muted);
  font-size: var(--text-sm);
  line-height: var(--leading-relaxed);
}

.article-card__tags {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-2);
}

.article-card__tag {
  padding: 2px 7px;
  border: 1px solid var(--line-strong);
  color: var(--text-muted);
  font-family: var(--mono);
  font-size: var(--text-2xs);
  text-decoration: none;
  transition:
    border-color var(--dur) var(--ease),
    color var(--dur) var(--ease);
}

.article-card__tag:hover {
  border-color: var(--verm);
  color: var(--verm-deep);
}

.article-card__meta {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-2) var(--space-4);
  color: var(--text-muted);
  font-family: var(--mono);
  font-size: var(--text-2xs);
}

.article-card__meta b {
  color: var(--ink);
  font-weight: var(--weight-semibold);
}
</style>
