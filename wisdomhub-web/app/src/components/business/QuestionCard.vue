<script setup lang="ts">
import { RouterLink } from 'vue-router'

import AuthorChip from '@/components/business/AuthorChip.vue'
import type { QuestionListItemVO } from '@/types/qa'
import type { UserBriefVO } from '@/types/user'
import { formatDateTime } from '@/utils'

defineProps<{
  question: QuestionListItemVO
  author?: UserBriefVO | null
}>()
</script>

<template>
  <article class="question-card" :class="{ 'is-solved': question.solved }">
    <div class="question-card__status">
      <span class="question-card__badge" :class="question.solved ? 'is-solved' : 'is-open'">
        {{ question.solved ? '已解决' : '待解决' }}
      </span>
      <time :datetime="question.createTime">
        {{ formatDateTime(question.createTime, 'YYYY-MM-DD') }}
      </time>
    </div>

    <RouterLink
      class="question-card__title"
      :to="{ name: 'question-detail', params: { questionId: question.questionId } }"
    >
      {{ question.title }}
    </RouterLink>

    <div class="question-card__meta">
      <AuthorChip :author="author" size="xs" />
      <span class="question-card__stat">{{ question.answerCount }} 回答</span>
      <span class="question-card__stat">{{ question.favoriteCount }} 收藏</span>
    </div>
  </article>
</template>

<style scoped>
.question-card {
  display: grid;
  gap: var(--space-2);
  padding-block: var(--space-4);
  border-bottom: var(--border);
}

.question-card__status {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: var(--space-2);
  color: var(--text-muted);
  font-family: var(--mono);
  font-size: var(--text-2xs);
}

.question-card__badge {
  padding: 2px 7px;
  border: 1px solid var(--line-strong);
  color: var(--text-muted);
  font-family: var(--mono);
  font-size: var(--text-2xs);
}

.question-card__badge.is-solved {
  border-color: var(--acid);
  background: var(--acid);
  color: var(--ink);
}

.question-card__title {
  max-width: 78ch;
  color: var(--ink);
  font-family: var(--serif);
  font-size: var(--text-lg);
  font-weight: var(--weight-semibold);
  line-height: var(--leading-snug);
  text-decoration: none;
  transition: color var(--dur) var(--ease);
}

.question-card__title:hover {
  color: var(--verm-deep);
}

.question-card__meta {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: var(--space-3);
}

.question-card__stat {
  color: var(--text-muted);
  font-family: var(--mono);
  font-size: var(--text-2xs);
}

@media (max-width: 640px) {
  .question-card__title {
    font-size: var(--text-base);
  }
}
</style>
