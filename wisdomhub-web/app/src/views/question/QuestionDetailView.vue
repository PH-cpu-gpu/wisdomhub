<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { RouterLink, useRoute, useRouter } from 'vue-router'

import { isApiError } from '@/api/error'
import {
  create as createAnswer,
  like as likeAnswer,
  list as listAnswers,
  remove as removeAnswerApi,
  unlike as unlikeAnswer,
} from '@/api/modules/answer'
import { accept, cancelAccept, detail, favorite, unfavorite } from '@/api/modules/question'
import WhButton from '@/components/base/WhButton.vue'
import WhPagination from '@/components/base/WhPagination.vue'
import WhTextarea from '@/components/base/WhTextarea.vue'
import { confirm } from '@/components/base/confirm'
import { toast } from '@/components/base/toast'
import AnswerCard from '@/components/business/AnswerCard.vue'
import AuthorChip from '@/components/business/AuthorChip.vue'
import CommentSection from '@/components/business/CommentSection.vue'
import PageContainer from '@/components/layout/PageContainer.vue'
import { useAuthorBrief } from '@/composables/useAuthorBrief'
import { useSeo } from '@/composables/useSeo'
import { useAuthStore } from '@/stores'
import type { AnswerVO, QuestionDetailVO } from '@/types/qa'
import { formatDateTime } from '@/utils'

const ANSWER_PAGE_SIZE = 5

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()

const questionId = computed(() => String(route.params.questionId ?? ''))
const question = ref<QuestionDetailVO | null>(null)
const answers = ref<AnswerVO[]>([])
const answerTotal = ref(0)
const answerPage = ref(1)

const loading = ref(false)
const error = ref('')
const notFound = ref(false)
const answersLoading = ref(false)
const answersError = ref('')
const favoritePending = ref(false)
const answerContent = ref('')
const answerSubmitting = ref(false)
const likedAnswerIds = ref(new Set<string>())
const likePendingId = ref('')
const expandedAnswerId = ref('')

const { authors, ensureAuthors } = useAuthorBrief()

let questionRequest = 0
let answerRequest = 0

async function loadQuestion(): Promise<void> {
  const id = questionId.value
  if (!id) return

  const request = (questionRequest += 1)
  loading.value = true
  error.value = ''
  notFound.value = false

  try {
    const data = await detail(id, { skipErrorToast: true })
    if (request !== questionRequest) return

    question.value = data
    void ensureAuthors([data.userId])
  } catch (err) {
    if (request !== questionRequest) return
    question.value = null
    if (isApiError(err) && err.code === 404) notFound.value = true
    else error.value = isApiError(err) ? err.message : '问题加载失败，请稍后重试。'
  } finally {
    if (request === questionRequest) loading.value = false
  }
}

async function loadAnswers(): Promise<void> {
  const id = questionId.value
  if (!id) return

  const request = (answerRequest += 1)
  answersLoading.value = true
  answersError.value = ''

  try {
    const result = await listAnswers(
      id,
      { page: answerPage.value, pageSize: ANSWER_PAGE_SIZE },
      { skipErrorToast: true },
    )
    if (request !== answerRequest) return

    answers.value = result.items
    answerTotal.value = result.total
    if (result.items.length) {
      void ensureAuthors([...new Set(result.items.map((answer) => answer.userId))])
    }
  } catch (err) {
    if (request !== answerRequest) return
    answers.value = []
    answerTotal.value = 0
    answersError.value = isApiError(err) ? err.message : '回答加载失败，请稍后重试。'
  } finally {
    if (request === answerRequest) answersLoading.value = false
  }
}

async function toggleFavorite(): Promise<void> {
  const current = question.value
  if (!current || favoritePending.value) return

  if (!auth.isLoggedIn()) {
    void router.push({ name: 'login', query: { redirect: route.fullPath } })
    return
  }

  const previous = current.favorited
  const previousCount = current.favoriteCount
  question.value = {
    ...current,
    favorited: !previous,
    favoriteCount: Math.max(0, previousCount + (previous ? -1 : 1)),
  }
  favoritePending.value = true

  try {
    const count = previous
      ? await unfavorite(current.questionId, { skipErrorToast: true })
      : await favorite(current.questionId, { skipErrorToast: true })
    if (question.value?.questionId === current.questionId) {
      question.value = {
        ...question.value,
        favorited: !previous,
        favoriteCount: count ?? question.value.favoriteCount,
      }
    }
  } catch (err) {
    if (question.value?.questionId === current.questionId) {
      question.value = {
        ...question.value,
        favorited: previous,
        favoriteCount: previousCount,
      }
    }
    toast.error(isApiError(err) ? err.message : '收藏失败，请稍后重试。')
  } finally {
    favoritePending.value = false
  }
}

function setAnswerPage(value: number): void {
  answerPage.value = value
}

function toggleAnswerComments(answer: AnswerVO): void {
  expandedAnswerId.value = expandedAnswerId.value === answer.answerId ? '' : answer.answerId
}

function requireLogin(): boolean {
  if (auth.isLoggedIn()) return true
  void router.push({ name: 'login', query: { redirect: route.fullPath } })
  return false
}

async function submitAnswer(): Promise<void> {
  if (!requireLogin() || answerSubmitting.value) return

  const content = answerContent.value.trim()
  if (!content) {
    toast.info('请填写回答内容')
    return
  }
  if (content.length > 10000) {
    toast.info('回答不能超过 10000 字')
    return
  }

  answerSubmitting.value = true
  try {
    await createAnswer(questionId.value, { content }, { skipErrorToast: true })
    answerContent.value = ''
    toast.success('回答已发布')
    answerPage.value = 1
    await loadAnswers()
    await loadQuestion()
  } catch (err) {
    toast.error(isApiError(err) ? err.message : '回答失败，请稍后重试。')
  } finally {
    answerSubmitting.value = false
  }
}

async function toggleLike(answer: AnswerVO): Promise<void> {
  if (!requireLogin() || likePendingId.value) return

  const liked = likedAnswerIds.value.has(answer.answerId)
  likePendingId.value = answer.answerId

  try {
    const count = liked
      ? await unlikeAnswer(answer.answerId, { skipErrorToast: true })
      : await likeAnswer(answer.answerId, { skipErrorToast: true })

    const target = answers.value.find((item) => item.answerId === answer.answerId)
    if (target && typeof count === 'number') target.likeCount = count
    if (liked) likedAnswerIds.value.delete(answer.answerId)
    else likedAnswerIds.value.add(answer.answerId)
  } catch (err) {
    toast.error(isApiError(err) ? err.message : '点赞失败，请稍后重试。')
  } finally {
    likePendingId.value = ''
  }
}

async function removeAnswer(answer: AnswerVO): Promise<void> {
  if (answer.userId !== auth.userId) return

  const ok = await confirm({
    title: '删除回答',
    message: '删除后不可恢复，确定继续？',
    confirmText: '删除',
    danger: true,
  })
  if (!ok) return

  try {
    await removeAnswerApi(answer.answerId, { skipErrorToast: true })
    toast.success('回答已删除')
    if (answers.value.length === 1 && answerPage.value > 1) answerPage.value -= 1
    else {
      await loadAnswers()
      await loadQuestion()
    }
  } catch (err) {
    toast.error(isApiError(err) ? err.message : '删除失败，请稍后重试。')
  }
}

async function toggleAccept(answer: AnswerVO): Promise<void> {
  const current = question.value
  if (!current || current.userId !== auth.userId) return

  const accepted = current.acceptedAnswerId === answer.answerId
  if (!accepted) {
    const ok = await confirm({
      title: '采纳回答',
      message: '采纳后会将该回答置顶并标记问题为已解决，确定继续？',
      confirmText: '采纳',
    })
    if (!ok) return
  }

  try {
    if (accepted) {
      await cancelAccept(current.questionId, answer.answerId, { skipErrorToast: true })
    } else {
      await accept(current.questionId, answer.answerId, { skipErrorToast: true })
    }
    toast.success(accepted ? '已取消采纳' : '已采纳回答')
    await loadQuestion()
    await loadAnswers()
  } catch (err) {
    toast.error(isApiError(err) ? err.message : '采纳失败，请稍后重试。')
  }
}

watch(
  questionId,
  () => {
    answerPage.value = 1
    void loadQuestion()
    void loadAnswers()
  },
  { immediate: true },
)

watch(answerPage, () => {
  void loadAnswers()
})

useSeo({
  title: '问题详情',
  description: '问题正文、采纳答案与回答列表。',
})
</script>

<template>
  <PageContainer tag="section">
    <div v-if="loading" class="question-state" aria-live="polite">正在加载问题…</div>

    <div v-else-if="notFound" class="question-state">
      <p>问题不存在或已删除。</p>
      <RouterLink class="question-link" :to="{ name: 'question-list' }">返回问答</RouterLink>
    </div>

    <div v-else-if="error" class="question-state question-state--error" role="alert">
      <p>{{ error }}</p>
      <WhButton size="sm" variant="ghost" @click="loadQuestion">重试</WhButton>
    </div>

    <div v-else-if="question" class="question-detail">
      <header class="question-head">
        <RouterLink class="question-back" :to="{ name: 'question-list' }"> ← 全部问题 </RouterLink>
        <p class="question-eyebrow">[ Q&amp;A / QUESTION / {{ question.questionId }} ]</p>
        <div class="question-title-row">
          <h1>{{ question.title }}</h1>
          <span
            class="question-solved"
            :class="question.acceptedAnswerId ? 'is-solved' : 'is-open'"
          >
            {{ question.acceptedAnswerId ? '已解决' : '待解决' }}
          </span>
        </div>

        <div class="question-meta">
          <AuthorChip :author="authors[question.userId]" size="sm" />
          <span class="question-meta__dot" aria-hidden="true">·</span>
          <time :datetime="question.createTime">
            {{ formatDateTime(question.createTime, 'YYYY年MM月DD日') }}
          </time>
          <span class="question-meta__dot" aria-hidden="true">·</span>
          <span>{{ question.answerCount }} 回答</span>
          <span class="question-meta__dot" aria-hidden="true">·</span>
          <span>{{ question.favoriteCount }} 收藏</span>
        </div>

        <p class="question-content">{{ question.content }}</p>

        <div class="question-actions">
          <button
            class="question-favorite"
            :class="{ 'is-active': question.favorited }"
            type="button"
            :aria-pressed="question.favorited"
            :disabled="favoritePending"
            @click="toggleFavorite"
          >
            <span aria-hidden="true">☆</span>
            <span>{{ question.favorited ? '已收藏' : '收藏' }}</span>
            <span>{{ question.favoriteCount }}</span>
          </button>
        </div>
      </header>

      <section class="answer-section">
        <header class="answer-section__head">
          <div>
            <p class="question-eyebrow">[ ANSWERS ]</p>
            <h2>回答</h2>
          </div>
          <span>{{ question.answerCount }} 条</span>
        </header>

        <p v-if="!question.acceptedAnswerId" class="answer-unaccepted">该问题还没有采纳答案。</p>

        <div v-if="answersLoading" class="question-state" aria-live="polite">正在加载回答…</div>

        <div v-else-if="answersError" class="question-state question-state--error" role="alert">
          <p>{{ answersError }}</p>
          <WhButton size="sm" variant="ghost" @click="loadAnswers">重试</WhButton>
        </div>

        <div v-else-if="!answers.length" class="answer-empty">
          <p>还没有回答。</p>
        </div>

        <div v-else class="answer-list">
          <template v-for="answer in answers" :key="answer.answerId">
            <div class="answer-item">
              <AnswerCard
                :answer="answer"
                :author="authors[answer.userId]"
                :accepted="answer.answerId === question.acceptedAnswerId"
                :liked="likedAnswerIds.has(answer.answerId)"
                :like-pending="likePendingId === answer.answerId"
                :can-delete="answer.userId === auth.userId"
                :can-accept="question.userId === auth.userId"
                @like="toggleLike"
                @remove="removeAnswer"
                @accept="toggleAccept"
                @comment="toggleAnswerComments"
              />
              <CommentSection
                v-if="expandedAnswerId === answer.answerId"
                :target-id="answer.answerId"
                :target-type="2"
                @total-change="answer.commentCount = $event"
              />
            </div>
          </template>
        </div>

        <div v-if="answerTotal > ANSWER_PAGE_SIZE" class="answer-pagination">
          <WhPagination
            :page="answerPage"
            :page-size="ANSWER_PAGE_SIZE"
            :total="answerTotal"
            @update:page="setAnswerPage"
          />
        </div>

        <section class="answer-editor">
          <header class="answer-editor__head">
            <p class="question-eyebrow">[ YOUR ANSWER ]</p>
            <h2>写回答</h2>
          </header>

          <template v-if="auth.isLoggedIn()">
            <WhTextarea
              v-model="answerContent"
              :maxlength="10000"
              :rows="6"
              placeholder="分享你的思路、代码或排查过程…"
              aria-label="回答内容"
            />
            <div class="answer-editor__actions">
              <span>{{ answerContent.length }}/10000</span>
              <WhButton
                :loading="answerSubmitting"
                :disabled="!answerContent.trim()"
                @click="submitAnswer"
              >
                发布回答
              </WhButton>
            </div>
          </template>
          <p v-else class="answer-editor__guest">
            登录后参与回答。
            <button class="question-link" type="button" @click="requireLogin">登录 / 注册</button>
          </p>
        </section>
      </section>
    </div>
  </PageContainer>
</template>

<style scoped>
.question-detail {
  display: grid;
  gap: clamp(24px, 4vw, 40px);
  max-width: 960px;
  margin: 0 auto;
}

.question-back {
  color: var(--text-muted);
  font-family: var(--mono);
  font-size: var(--text-2xs);
  text-decoration: underline;
  text-underline-offset: 3px;
}

.question-back:hover {
  color: var(--verm-deep);
}

.question-eyebrow {
  color: var(--verm-deep);
  font-family: var(--mono);
  font-size: var(--text-2xs);
  letter-spacing: var(--tracking-wider);
}

.question-head {
  display: grid;
  gap: var(--space-3);
}

.question-title-row {
  display: flex;
  flex-wrap: wrap;
  align-items: flex-start;
  gap: var(--space-3);
}

.question-title-row h1 {
  flex: 1;
  min-width: 0;
  font-family: var(--serif);
  font-size: clamp(1.7rem, 3vw, 2.4rem);
  font-weight: var(--weight-black);
  line-height: var(--leading-tight);
  letter-spacing: var(--tracking-tight);
}

.question-solved {
  padding: 2px 7px;
  border: 1px solid var(--line-strong);
  color: var(--text-muted);
  font-family: var(--mono);
  font-size: var(--text-2xs);
  white-space: nowrap;
}

.question-solved.is-solved {
  border-color: var(--acid);
  background: var(--acid);
  color: var(--ink);
}

.question-meta {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: var(--space-2);
  color: var(--text-muted);
  font-family: var(--mono);
  font-size: var(--text-2xs);
}

.question-meta__dot {
  opacity: 0.5;
}

.question-content {
  padding-block: var(--space-4);
  border-top: var(--border);
  border-bottom: var(--border);
  color: var(--text-body);
  font-size: var(--text-base);
  line-height: var(--leading-relaxed);
  white-space: pre-wrap;
}

.question-actions {
  display: flex;
  justify-content: flex-end;
}

.question-favorite {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  min-height: 34px;
  padding-inline: 12px;
  border: 1px solid var(--line-strong);
  background: transparent;
  color: var(--text-muted);
  font-family: var(--mono);
  font-size: var(--text-2xs);
  transition:
    border-color var(--dur) var(--ease),
    background var(--dur) var(--ease),
    color var(--dur) var(--ease);
}

.question-favorite:hover:not(:disabled) {
  border-color: var(--ink);
  color: var(--ink);
}

.question-favorite.is-active {
  border-color: var(--ink);
  background: var(--acid);
  color: var(--ink);
}

.question-favorite:disabled {
  opacity: 0.6;
  cursor: wait;
}

.answer-section {
  display: grid;
  gap: var(--space-3);
}

.answer-section__head {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: var(--space-3);
  padding-bottom: var(--space-3);
  border-bottom: 1px solid var(--ink);
}

.answer-section__head h2 {
  margin-top: var(--space-1);
  font-family: var(--serif);
  font-size: var(--text-2xl);
  font-weight: var(--weight-black);
}

.answer-section__head > span {
  color: var(--text-muted);
  font-family: var(--mono);
  font-size: var(--text-2xs);
}

.answer-unaccepted {
  color: var(--text-muted);
  font-size: var(--text-xs);
}

.answer-list {
  display: grid;
  gap: var(--space-3);
}

.answer-item {
  display: grid;
  gap: var(--space-2);
}

.answer-empty,
.question-state {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: var(--space-3);
  padding: var(--space-6);
  border: 1px dashed var(--line-strong);
  color: var(--text-muted);
  font-size: var(--text-sm);
}

.question-state--error {
  border-color: var(--verm);
  color: var(--verm-deep);
}

.question-link {
  padding: 0;
  border: 0;
  background: transparent;
  color: var(--verm-deep);
  font-family: var(--mono);
  font-size: var(--text-2xs);
  text-decoration: underline;
  text-underline-offset: 3px;
  cursor: pointer;
}

.answer-pagination {
  padding-top: var(--space-3);
  border-top: var(--border);
}

.answer-editor {
  display: grid;
  gap: var(--space-3);
  margin-top: var(--space-4);
  padding-top: var(--space-4);
  border-top: 1px solid var(--ink);
}

.answer-editor__head h2 {
  margin-top: var(--space-1);
  font-family: var(--serif);
  font-size: var(--text-2xl);
  font-weight: var(--weight-black);
}

.answer-editor__actions {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-3);
  color: var(--text-muted);
  font-family: var(--mono);
  font-size: var(--text-2xs);
}

.answer-editor__guest {
  color: var(--text-muted);
  font-size: var(--text-sm);
}

@media (max-width: 640px) {
  .question-actions {
    justify-content: flex-start;
  }
}
</style>
