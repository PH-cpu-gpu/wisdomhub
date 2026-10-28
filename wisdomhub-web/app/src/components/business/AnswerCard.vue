<script setup lang="ts">
import AuthorChip from '@/components/business/AuthorChip.vue'
import type { AnswerVO } from '@/types/qa'
import type { UserBriefVO } from '@/types/user'
import { formatDateTime } from '@/utils'

withDefaults(
  defineProps<{
    answer: AnswerVO
    author?: UserBriefVO | null
    accepted?: boolean
    liked?: boolean
    likePending?: boolean
    canDelete?: boolean
    canAccept?: boolean
  }>(),
  {
    accepted: false,
    liked: false,
    likePending: false,
    canDelete: false,
    canAccept: false,
  },
)

const emit = defineEmits<{
  like: [answer: AnswerVO]
  remove: [answer: AnswerVO]
  accept: [answer: AnswerVO]
  comment: [answer: AnswerVO]
}>()
</script>

<template>
  <article class="answer-card" :class="{ 'is-accepted': accepted }">
    <div class="answer-card__head">
      <span v-if="accepted" class="answer-card__accepted">已采纳答案</span>
      <span v-else class="answer-card__label">回答</span>
      <time :datetime="answer.createTime">
        {{ formatDateTime(answer.createTime, 'YYYY-MM-DD HH:mm') }}
      </time>
    </div>

    <p class="answer-card__content">{{ answer.content }}</p>

    <div class="answer-card__meta">
      <AuthorChip :author="author" size="xs" />
      <span v-if="answer.commentCount">{{ answer.commentCount }} 评论</span>
    </div>

    <div class="answer-card__actions">
      <button
        class="answer-action"
        :class="{ 'is-liked': liked }"
        type="button"
        :disabled="likePending"
        :aria-pressed="liked"
        @click="emit('like', answer)"
      >
        <span aria-hidden="true">♥</span>
        <span>{{ liked ? '已赞' : '点赞' }} {{ answer.likeCount }}</span>
      </button>
      <button class="answer-action" type="button" @click="emit('comment', answer)">
        {{ answer.commentCount }} 评论
      </button>
      <button
        v-if="canAccept"
        class="answer-action"
        :class="{ 'is-liked': accepted }"
        type="button"
        @click="emit('accept', answer)"
      >
        {{ accepted ? '取消采纳' : '采纳' }}
      </button>
      <button
        v-if="canDelete"
        class="answer-action answer-action--danger"
        type="button"
        @click="emit('remove', answer)"
      >
        删除
      </button>
    </div>
  </article>
</template>

<style scoped>
.answer-card {
  display: grid;
  gap: var(--space-2);
  padding: var(--space-4);
  border: var(--border);
  background: var(--surface);
}

.answer-card.is-accepted {
  border-color: var(--ink);
  box-shadow: inset 3px 0 0 var(--acid);
}

.answer-card__head {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-2);
  color: var(--text-muted);
  font-family: var(--mono);
  font-size: var(--text-2xs);
}

.answer-card__accepted {
  padding: 2px 7px;
  border: 1px solid var(--acid);
  background: var(--acid);
  color: var(--ink);
  font-family: var(--mono);
  font-size: var(--text-2xs);
}

.answer-card__label {
  letter-spacing: 0.08em;
}

.answer-card__content {
  color: var(--text-body);
  font-size: var(--text-sm);
  line-height: var(--leading-relaxed);
  white-space: pre-wrap;
}

.answer-card__meta {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: var(--space-3);
  padding-top: var(--space-2);
  border-top: var(--border);
  color: var(--text-muted);
  font-family: var(--mono);
  font-size: var(--text-2xs);
}

.answer-card__actions {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: var(--space-3);
}

.answer-action {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 0;
  border: 0;
  background: transparent;
  color: var(--text-muted);
  font-family: var(--mono);
  font-size: var(--text-2xs);
  text-decoration: underline;
  text-underline-offset: 3px;
  transition: color var(--dur) var(--ease);
}

.answer-action:hover:not(:disabled) {
  color: var(--verm-deep);
}

.answer-action.is-liked {
  color: var(--verm-deep);
  font-weight: var(--weight-semibold);
}

.answer-action--danger:hover {
  color: var(--verm-deep);
}

.answer-action:disabled {
  opacity: 0.55;
  cursor: wait;
}
</style>
