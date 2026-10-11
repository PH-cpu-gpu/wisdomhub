<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { RouterLink, useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import 'element-plus/es/components/message/style/css'
import 'element-plus/es/components/message-box/style/css'

import { ApiError } from '@/api/http'
import { ApiCode } from '@/api/types'
import {
  acceptAnswer,
  cancelAccept,
  createAnswer,
  deleteAnswer,
  deleteQuestion,
  favoriteQuestion,
  getQuestion,
  likeAnswer,
  listAnswers,
  unfavoriteQuestion,
  unlikeAnswer,
  type AnswerItem,
  type QuestionDetail,
} from '@/api/question'
import { loadUserNames, userName } from '@/composables/useUserNames'
import { useUserStore } from '@/stores/user'
import { formatDateTime, formatRelativeTime } from '@/utils/format'

/**
 * F1.7 问题详情：问题 + 回答列表 + 回答点赞 + 收藏 + 采纳。
 *
 * 三条形态要贴合后端（B1.28）：
 *   1. **回答是「采纳的置顶 + 其余按发表时间升序」** —— 所以被采纳的那条总在第一页第一条，
 *      `acceptedAnswerId` 只用来打「已采纳」标记，不再单独渲染一份（否则同一条出现两次）
 *   2. **采纳可改可撤**：同一个按钮按当前状态决定调 PUT 还是 DELETE；换采纳直接点另一条即可
 *   3. **问题正文是纯文本**（TEXT，没有 Markdown 渲染），按 `pre-wrap` 显示
 */
const route = useRoute()
const router = useRouter()
const user = useUserStore()

const question = ref<QuestionDetail | null>(null)
const loading = ref(true)
const notFound = ref(false)
const acting = ref(false)

const answers = ref<AnswerItem[]>([])
const answerTotal = ref(0)
const answerPage = ref(1)
const answerPageSize = ref(10)
const answersLoading = ref(false)

const newAnswer = ref('')
const answering = ref(false)

/** 本页会话内「我点过赞的回答」。后端不返回这个状态，所以只能自己记（见 onToggleAnswerLike） */
const likedAnswers = ref<Set<string>>(new Set())

const questionId = computed(() => String(route.params.id ?? ''))
const isAsker = computed(
  () => question.value !== null && user.userId !== null && String(question.value.userId) === user.userId,
)

function isMine(userId: string): boolean {
  return user.userId !== null && String(userId) === user.userId
}

// ---------------------------------------------------------------------------
// 加载
// ---------------------------------------------------------------------------

async function loadQuestion() {
  loading.value = true
  notFound.value = false
  question.value = null

  try {
    question.value = await getQuestion(questionId.value)
    void loadUserNames([question.value.userId])
  } catch (e) {
    if (e instanceof ApiError && e.code === ApiCode.NOT_FOUND) {
      notFound.value = true
      return
    }
    ElMessage.error(e instanceof ApiError ? e.message : '加载失败')
  } finally {
    loading.value = false
  }
}

async function loadAnswers() {
  answersLoading.value = true
  try {
    const result = await listAnswers(questionId.value, answerPage.value, answerPageSize.value)
    answers.value = result.items
    answerTotal.value = result.total
    // 回答只给 userId，用户名走共享缓存批量取（见 useUserNames）
    void loadUserNames(result.items.map((item) => item.userId))
  } catch (e) {
    answers.value = []
    answerTotal.value = 0
    ElMessage.error(e instanceof ApiError ? e.message : '回答加载失败')
  } finally {
    answersLoading.value = false
  }
}

/** 回答数变了就重取一次问题（只更新计数，不重渲染整页） */
async function refreshQuestion() {
  try {
    question.value = await getQuestion(questionId.value)
    void loadUserNames([question.value.userId])
  } catch {
    /* 计数刷新失败不影响阅读 */
  }
}

// ---------------------------------------------------------------------------
// 操作
// ---------------------------------------------------------------------------

function requireLogin(): boolean {
  if (user.isLoggedIn) return true
  ElMessage.info('登录后才能操作')
  void router.push({ path: '/login', query: { redirect: route.fullPath } })
  return false
}

async function onToggleFavorite() {
  if (!question.value || !requireLogin()) return

  acting.value = true
  try {
    const { questionId: id, favorited } = question.value
    question.value.favoriteCount = favorited ? await unfavoriteQuestion(id) : await favoriteQuestion(id)
    question.value.favorited = !favorited
  } catch (e) {
    ElMessage.error(e instanceof ApiError ? e.message : '操作失败')
  } finally {
    acting.value = false
  }
}

async function onToggleAnswerLike(answer: AnswerItem) {
  if (!requireLogin()) return

  try {
    // ⚠️ 后端**刻意不返回 liked**（回答是列表，一页 N 条不能 N 次查询），
    // 所以「我点没点过」只能在本页会话里记着。刷新后忘掉也没关系：
    // 点赞是幂等的「设置状态」，再点一次只会把计数设成同一个值。
    const liked = likedAnswers.value.has(answer.answerId)
    answer.likeCount = liked ? await unlikeAnswer(answer.answerId) : await likeAnswer(answer.answerId)

    const next = new Set(likedAnswers.value)
    if (liked) next.delete(answer.answerId)
    else next.add(answer.answerId)
    likedAnswers.value = next
  } catch (e) {
    ElMessage.error(e instanceof ApiError ? e.message : '操作失败')
  }
}

async function onToggleAccept(answer: AnswerItem) {
  if (!question.value) return

  try {
    const accepted = question.value.acceptedAnswerId === answer.answerId
    if (accepted) {
      await cancelAccept(question.value.questionId, answer.answerId)
    } else {
      await acceptAnswer(question.value.questionId, answer.answerId)
    }
    await refreshQuestion()
    await loadAnswers()
    ElMessage.success(accepted ? '已取消采纳' : '已采纳')
  } catch (e) {
    ElMessage.error(e instanceof ApiError ? e.message : '操作失败')
  }
}

async function onSubmitAnswer() {
  const text = newAnswer.value.trim()
  if (!text) return

  answering.value = true
  try {
    await createAnswer(questionId.value, { content: text })
    newAnswer.value = ''
    ElMessage.success('回答已发布')
    answerPage.value = 1
    await loadAnswers()
    await refreshQuestion()
  } catch (e) {
    ElMessage.error(e instanceof ApiError ? e.message : '回答失败')
  } finally {
    answering.value = false
  }
}

async function onDeleteAnswer(answer: AnswerItem) {
  try {
    await ElMessageBox.confirm('确定删除这条回答吗？', '删除回答', {
      type: 'warning',
      confirmButtonText: '删除',
      cancelButtonText: '取消',
    })
  } catch {
    return
  }

  try {
    await deleteAnswer(answer.answerId)
    ElMessage.success('已删除')
    await loadAnswers()
    await refreshQuestion()
  } catch (e) {
    ElMessage.error(e instanceof ApiError ? e.message : '删除失败')
  }
}

async function onDeleteQuestion() {
  if (!question.value) return

  try {
    await ElMessageBox.confirm('删除问题会连同它的全部回答一起删掉，确定吗？', '删除问题', {
      type: 'warning',
      confirmButtonText: '删除',
      cancelButtonText: '取消',
    })
  } catch {
    return
  }

  try {
    await deleteQuestion(question.value.questionId)
    ElMessage.success('问题已删除')
    void router.replace('/questions')
  } catch (e) {
    ElMessage.error(e instanceof ApiError ? e.message : '删除失败')
  }
}

watch(answerPage, () => void loadAnswers())

watch(
  () => route.params.id,
  () => {
    answerPage.value = 1
    answers.value = []
    void loadQuestion()
    void loadAnswers()
  },
)

onMounted(() => {
  void loadQuestion()
  void loadAnswers()
})
</script>

<template>
  <div class="qd">
    <el-skeleton v-if="loading" :rows="8" animated />

    <el-empty v-else-if="notFound" description="问题不存在或已删除">
      <el-button type="primary" @click="router.push('/questions')">回问答列表</el-button>
    </el-empty>

    <template v-else-if="question">
      <header class="qd__head">
        <h1 class="qd__title">
          {{ question.title }}
          <span v-if="question.acceptedAnswerId" class="qd__solved wh-mono">已解决</span>
        </h1>

        <div class="qd__meta wh-mono">
          <RouterLink class="qd__author" :to="`/user/${question.userId}`">
            {{ userName(question.userId) }}
          </RouterLink>
          <span :title="formatDateTime(question.createTime)">
            {{ formatRelativeTime(question.createTime) }}
          </span>
          <span>回答 {{ question.answerCount }}</span>
          <span>收藏 {{ question.favoriteCount }}</span>
        </div>
      </header>

      <!-- 问题正文是纯文本（TEXT，不走 Markdown），保留换行 -->
      <p class="qd__content">{{ question.content }}</p>

      <div class="qd__actions">
        <el-button
          :type="question.favorited ? 'default' : 'primary'"
          :loading="acting"
          @click="onToggleFavorite"
        >
          {{ question.favorited ? '已收藏' : '收藏' }}
        </el-button>

        <el-button v-if="isAsker" type="danger" plain @click="onDeleteQuestion">删除问题</el-button>
      </div>

      <!-- 回答 -->
      <section class="qd__answers">
        <h2 class="qd__answers-title">
          回答
          <span class="wh-mono qd__answers-count">{{ answerTotal }}</span>
        </h2>

        <div class="qd__composer">
          <textarea
            v-model="newAnswer"
            class="qd__input"
            rows="4"
            maxlength="10000"
            placeholder="写下你的回答……"
          />
          <div class="qd__composer-foot">
            <span class="wh-mono qd__len">{{ newAnswer.length }}/10000</span>
            <el-button
              type="primary"
              :loading="answering"
              :disabled="!newAnswer.trim()"
              @click="onSubmitAnswer"
            >
              发布回答
            </el-button>
          </div>
        </div>

        <el-skeleton v-if="answersLoading && answers.length === 0" :rows="4" animated />

        <el-empty v-else-if="answers.length === 0" description="还没有回答" />

        <ul v-else class="qd__list">
          <li
            v-for="answer in answers"
            :key="answer.answerId"
            class="qd__answer"
            :class="{ 'is-accepted': question.acceptedAnswerId === answer.answerId }"
          >
            <div class="qd__answer-meta wh-mono">
              <span
                v-if="question.acceptedAnswerId === answer.answerId"
                class="qd__accepted-badge"
              >
                已采纳
              </span>
              <RouterLink class="qd__author" :to="`/user/${answer.userId}`">
                {{ userName(answer.userId) }}
              </RouterLink>
              <span :title="formatDateTime(answer.createTime)">
                {{ formatRelativeTime(answer.createTime) }}
              </span>
            </div>

            <p class="qd__answer-body">{{ answer.content }}</p>

            <div class="qd__answer-ops">
              <button
                class="qd__op"
                :class="{ 'is-on': likedAnswers.has(answer.answerId) }"
                type="button"
                @click="onToggleAnswerLike(answer)"
              >
                {{ likedAnswers.has(answer.answerId) ? '♥' : '♡' }} 赞
                <span class="wh-mono">{{ answer.likeCount }}</span>
              </button>

              <!-- 采纳只有提问者本人能点（后端也会拦），同一个按钮按状态决定采纳还是取消 -->
              <button v-if="isAsker" class="qd__op" type="button" @click="onToggleAccept(answer)">
                {{ question.acceptedAnswerId === answer.answerId ? '取消采纳' : '采纳' }}
              </button>

              <button
                v-if="isMine(answer.userId)"
                class="qd__op qd__op--danger"
                type="button"
                @click="onDeleteAnswer(answer)"
              >
                删除
              </button>
            </div>
          </li>
        </ul>

        <div v-if="answerTotal > answerPageSize" class="qd__pager">
          <el-pagination
            v-model:current-page="answerPage"
            :page-size="answerPageSize"
            :total="answerTotal"
            layout="prev, pager, next"
            background
          />
        </div>
      </section>
    </template>
  </div>
</template>

<style scoped>
.qd {
  max-width: var(--wh-reading);
  padding-block: var(--wh-space-6);
}

.qd__head {
  padding-bottom: var(--wh-space-4);
  border-bottom: var(--wh-border-width) solid var(--wh-border-strong);
}

.qd__title {
  display: flex;
  flex-wrap: wrap;
  gap: var(--wh-space-3);
  align-items: center;

  font-size: var(--wh-text-2xl);
  font-weight: var(--wh-weight-semibold);
  line-height: var(--wh-leading-snug);
}

.qd__solved,
.qd__accepted-badge {
  padding: 0 var(--wh-space-2);

  font-size: var(--wh-text-xs);
  color: var(--wh-accent);
  background-color: var(--wh-accent-soft);
  border-radius: var(--wh-radius-full);
}

.qd__meta,
.qd__answer-meta {
  display: flex;
  flex-wrap: wrap;
  gap: var(--wh-space-3);
  align-items: center;

  margin-top: var(--wh-space-3);

  font-size: var(--wh-text-xs);
  color: var(--wh-text-3);
}

.qd__author {
  color: var(--wh-text-2);
  text-decoration: none;
}

.qd__author:hover {
  color: var(--wh-accent);
}

.qd__content {
  margin-top: var(--wh-space-5);

  font-size: var(--wh-text-base);
  line-height: var(--wh-leading-relaxed);
  color: var(--wh-text-1);
  white-space: pre-wrap;
}

.qd__actions {
  display: flex;
  gap: var(--wh-space-3);
  margin-top: var(--wh-space-5);
}

.qd__answers {
  margin-top: var(--wh-space-8);
  padding-top: var(--wh-space-6);
  border-top: var(--wh-border-width) solid var(--wh-border-strong);
}

.qd__answers-title {
  display: flex;
  gap: var(--wh-space-2);
  align-items: baseline;

  font-size: var(--wh-text-xl);
}

.qd__answers-count {
  font-size: var(--wh-text-sm);
  color: var(--wh-text-3);
}

.qd__composer {
  display: flex;
  flex-direction: column;
  gap: var(--wh-space-2);
  margin: var(--wh-space-4) 0 var(--wh-space-6);
}

.qd__input {
  width: 100%;
  padding: var(--wh-space-3);

  font-family: inherit;
  font-size: var(--wh-text-sm);
  line-height: var(--wh-leading-normal);
  color: var(--wh-text-1);
  background-color: var(--wh-bg-surface);
  border: var(--wh-border-width) solid var(--wh-border);
  border-radius: var(--wh-radius-md);
  outline: none;
  resize: vertical;
}

.qd__input:focus {
  border-color: var(--wh-accent);
}

.qd__composer-foot {
  display: flex;
  gap: var(--wh-space-2);
  align-items: center;
  justify-content: flex-end;
}

.qd__len {
  margin-right: auto;
  font-size: var(--wh-text-xs);
  color: var(--wh-text-3);
}

.qd__list {
  list-style: none;
}

.qd__answer {
  padding: var(--wh-space-5) var(--wh-space-4);

  border-bottom: var(--wh-border-width) solid var(--wh-border);
}

/* 被采纳的那条：左侧一条主色竖线 + 淡底，一眼能认出来 */
.qd__answer.is-accepted {
  background-color: var(--wh-accent-soft);
  border-left: 2px solid var(--wh-accent);
  border-radius: var(--wh-radius-sm);
}

.qd__answer-body {
  margin-top: var(--wh-space-3);

  font-size: var(--wh-text-sm);
  line-height: var(--wh-leading-relaxed);
  color: var(--wh-text-1);
  white-space: pre-wrap;
}

.qd__answer-ops {
  display: flex;
  gap: var(--wh-space-4);
  margin-top: var(--wh-space-3);
}

.qd__op {
  padding: 0;

  font-size: var(--wh-text-xs);
  color: var(--wh-text-3);
  background: none;
  border: none;
  cursor: pointer;
}

.qd__op:hover {
  color: var(--wh-accent);
  text-decoration: underline;
}

/* 已点赞的回答：主色字，与「♡/♥」的切换一起构成双重线索 */
.qd__op.is-on {
  color: var(--wh-accent);
}

.qd__op--danger:hover {
  color: var(--wh-danger);
}

.qd__pager {
  display: flex;
  justify-content: center;
  padding-top: var(--wh-space-5);
}
</style>
