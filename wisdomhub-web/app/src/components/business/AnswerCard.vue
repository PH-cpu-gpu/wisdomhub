<script setup lang="ts">
import AuthorChip from '@/components/business/AuthorChip.vue'
import type { InteractionCountVO } from '@/types/interaction'
import type { AnswerVO } from '@/types/qa'
import type { UserBriefVO } from '@/types/user'
import { formatDateTime } from '@/utils'

withDefaults(
  defineProps<{
    answer: AnswerVO
    author?: UserBriefVO | null
    /** 点赞数/评论数由问题详情页批量取互动计数后传入 */
    counts?: InteractionCountVO | null
    accepted?: boolean
    liked?: boolean
    likePending?: boolean
    canDelete?: boolean
    canAccept?: boolean
    /** 刚被采纳时播放一次性庆祝反馈 */
    celebrating?: boolean
  }>(),
  {
    accepted: false,
    liked: false,
    likePending: false,
    canDelete: false,
    canAccept: false,
    celebrating: false,
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
  <article
    class="answer-card"
    :class="{ 'is-accepted': accepted, 'is-celebrating': celebrating }"
  >
    <span v-if="celebrating" class="answer-card__burst" aria-hidden="true"></span>
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
      <span v-if="counts?.commentCount">{{ counts.commentCount }} 评论</span>
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
        <span>{{ liked ? '已赞' : '点赞' }} {{ counts?.likeCount ?? '—' }}</span>
      </button>
      <button class="answer-action" type="button" @click="emit('comment', answer)">
        {{ counts?.commentCount ?? '—' }} 评论
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
@keyframes accept-burst {
  0% {
    opacity: 0.9;
    transform: scale(0.97);
  }
  100% {
    opacity: 0;
    transform: scale(1.04);
  }
}

@keyframes accept-stamp {
  0% {
    opacity: 0;
    transform: scale(1.5);
  }
  60% {
    opacity: 1;
    transform: scale(0.94);
  }
  100% {
    opacity: 1;
    transform: scale(1);
  }
}

@keyframes accept-lift {
  0% {
    transform: translateY(6px);
  }
  55% {
    transform: translateY(-2px);
  }
  100% {
    transform: translateY(0);
  }
}

.answer-card {
  position: relative;
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

.answer-card.is-celebrating {
  animation: accept-lift 620ms var(--anim-ease-out) both;
}

.answer-card__burst {
  position: absolute;
  inset: -1px;
  border: 2px solid var(--acid);
  animation: accept-burst 900ms var(--anim-ease-out) both;
  pointer-events: none;
}

.answer-card.is-celebrating .answer-card__accepted {
  animation: accept-stamp 600ms var(--anim-bounce) both;
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
  display: inline-block;
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

@media (prefers-reduced-motion: reduce) {
  .answer-card.is-celebrating,
  .answer-card__burst,
  .answer-card.is-celebrating .answer-card__accepted {
    animation: none;
  }

  .answer-card__burst {
    display: none;
  }
}
</style>
