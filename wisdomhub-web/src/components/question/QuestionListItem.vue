<script setup lang="ts">
import { RouterLink } from 'vue-router'

import type { QuestionListItem } from '@/api/question'
import { userName } from '@/composables/useUserNames'
import { formatCount, formatDateTime, formatRelativeTime } from '@/utils/format'

/**
 * 问答列表的一行。问题列表页与「我的收藏 → 问答」复用。
 *
 * 作者名走 `useUserNames`：列表接口只给 `userId`，那一层会批量把名字取回来并缓存，
 * 取不到时退化成「用户 1234」。
 */
defineProps<{ question: QuestionListItem }>()
</script>

<template>
  <li class="qitem">
    <h2 class="qitem__title">
      <RouterLink :to="`/question/${question.questionId}`">{{ question.title }}</RouterLink>
      <span v-if="question.solved" class="qitem__solved wh-mono">已解决</span>
    </h2>

    <div class="qitem__meta wh-mono">
      <RouterLink class="qitem__author" :to="`/user/${question.userId}`">
        {{ userName(question.userId) }}
      </RouterLink>
      <span :title="formatDateTime(question.createTime)">
        {{ formatRelativeTime(question.createTime) }}
      </span>

      <span class="qitem__stats">
        <span>回答 {{ formatCount(question.answerCount) }}</span>
        <span>收藏 {{ formatCount(question.favoriteCount) }}</span>
      </span>

      <!-- 每行自己的操作（收藏页的「取消收藏」）；不传就是纯展示 -->
      <span class="qitem__actions">
        <slot name="actions" />
      </span>
    </div>
  </li>
</template>

<style scoped>
.qitem {
  padding: var(--wh-space-5) 0;
  border-bottom: var(--wh-border-width) solid var(--wh-border);
}

.qitem__title {
  display: flex;
  flex-wrap: wrap;
  gap: var(--wh-space-2);
  align-items: center;

  font-size: var(--wh-text-lg);
  font-weight: var(--wh-weight-semibold);
  line-height: var(--wh-leading-snug);
}

.qitem__title a {
  color: var(--wh-text-1);
  text-decoration: none;
}

.qitem__title a:hover {
  color: var(--wh-accent);
}

.qitem__solved {
  flex: none;
  padding: 0 var(--wh-space-2);

  font-size: var(--wh-text-xs);
  color: var(--wh-accent);
  background-color: var(--wh-accent-soft);
  border-radius: var(--wh-radius-full);
}

.qitem__meta {
  display: flex;
  flex-wrap: wrap;
  gap: var(--wh-space-3);
  align-items: center;

  margin-top: var(--wh-space-3);

  font-size: var(--wh-text-xs);
  color: var(--wh-text-3);
}

.qitem__author {
  color: var(--wh-text-2);
  text-decoration: none;
}

.qitem__author:hover {
  color: var(--wh-accent);
}

.qitem__stats {
  display: flex;
  gap: var(--wh-space-3);
  margin-left: auto;
}

.qitem__actions:empty {
  display: none;
}
</style>
