<script setup lang="ts">
import { RouterLink } from 'vue-router'

import type { ArticleListItem } from '@/api/article'
import { userAvatar, userName } from '@/composables/useUserNames'
import UserAvatar from '@/components/UserAvatar.vue'
import { formatCount, formatDateTime, formatRelativeTime } from '@/utils/format'

/**
 * 文章列表的一行。首页、标签页、个人主页、我的收藏都复用。
 *
 * 作者名走 `useUserNames`：列表接口只给 `authorId`，那一层会批量把名字取回来并缓存，
 * 取不到时退化成「用户 1234」。
 */
withDefaults(
  defineProps<{
    article: ArticleListItem
    /** 不显示作者：个人主页里每条都是同一个人，重复显示只是噪音 */
    hideAuthor?: boolean
  }>(),
  { hideAuthor: false },
)
</script>

<template>
  <article class="item">
    <h2 class="item__title">
      <RouterLink :to="`/article/${article.articleId}`">{{ article.title }}</RouterLink>
    </h2>

    <p v-if="article.summary" class="item__summary">{{ article.summary }}</p>

    <div class="item__meta">
      <RouterLink v-if="!hideAuthor" class="item__author" :to="`/user/${article.authorId}`">
        <span aria-hidden="true">
          <UserAvatar
            :src="userAvatar(article.authorId)"
            :name="userName(article.authorId)"
            :size="20"
          />
        </span>
        {{ userName(article.authorId) }}
      </RouterLink>

      <span
        class="item__time"
        :title="formatDateTime(article.publishTime)"
      >{{ formatRelativeTime(article.publishTime) }}</span>

      <span class="item__stats">
        <span :title="`阅读 ${article.viewCount}`">阅读 <span class="wh-mono">{{ formatCount(article.viewCount) }}</span></span>
        <span :title="`点赞 ${article.likeCount}`">赞 <span class="wh-mono">{{ formatCount(article.likeCount) }}</span></span>
        <span :title="`评论 ${article.commentCount}`">评 <span class="wh-mono">{{ formatCount(article.commentCount) }}</span></span>
        <span :title="`收藏 ${article.favoriteCount}`">藏 <span class="wh-mono">{{ formatCount(article.favoriteCount) }}</span></span>
      </span>

      <!-- 每行自己的操作（「我的收藏」里的取消收藏等）；不传就是纯展示 -->
      <span class="item__actions">
        <slot name="actions" />
      </span>
    </div>
  </article>
</template>

<style scoped>
.item {
  padding: var(--wh-space-5) 0;
  border-bottom: var(--wh-border-width) solid var(--wh-border);
  transition: background-color var(--wh-duration-fast) var(--wh-ease);
}

.item:hover {
  background-color: color-mix(in srgb, var(--wh-bg-surface) 55%, transparent);
}

.item:last-child {
  border-bottom: none;
}

.item__title {
  font-size: var(--wh-text-lg);
  font-weight: var(--wh-weight-semibold);
  line-height: var(--wh-leading-snug);
}

.item__title a {
  color: var(--wh-text-1);
  text-decoration: none;
}

.item__title a:hover {
  color: var(--wh-accent);
}

.item__summary {
  display: -webkit-box;
  overflow: hidden;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;

  margin-top: var(--wh-space-2);

  font-size: var(--wh-text-sm);
  line-height: var(--wh-leading-relaxed);
  color: var(--wh-text-2);
}

.item__meta {
  display: flex;
  flex-wrap: wrap;
  gap: var(--wh-space-3);
  align-items: center;

  margin-top: var(--wh-space-3);

  font-size: var(--wh-text-sm);
  color: var(--wh-text-3);
}

.item__author {
  display: inline-flex;
  gap: var(--wh-space-2);
  align-items: center;

  color: var(--wh-text-2);
  font-weight: var(--wh-weight-medium);
  text-decoration: none;

  --wh-avatar-bg: var(--wh-bg-raised);
  --wh-avatar-text: var(--wh-text-2);
}

.item__author:hover {
  color: var(--wh-accent);
}

.item__stats {
  display: flex;
  gap: var(--wh-space-3);
  margin-left: auto;
}

.item__actions:empty {
  display: none;
}
</style>
