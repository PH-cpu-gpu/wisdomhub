<script setup lang="ts">
import { onMounted, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import 'element-plus/es/components/message/style/css'

import { listMyFavorites, uncollectArticle, type ArticleListItem } from '@/api/article'
import { ApiError } from '@/api/http'
import { listMyFavoriteQuestions, unfavoriteQuestion, type QuestionListItem } from '@/api/question'
import ArticleRow from '@/components/article/ArticleListItem.vue'
import QuestionRow from '@/components/question/QuestionListItem.vue'
import { loadUserNames } from '@/composables/useUserNames'

/**
 * F1.8 我的收藏。文章与问答两个 Tab。
 *
 * 这页没法直接套 `ArticleList` / 问答列表页：它们各自带筛选与排序，
 * 而收藏走的是另外两条接口（都按**收藏时间**倒序）。列表项复用同一套行组件，
 * 排版与别处一致，只是多一个「取消收藏」的动作。
 *
 * ⚠️ 两条收藏接口**路径不同、名字也不同**，别混：
 *   文章 `GET /content/article/collect`
 *   问答 `GET /content/question/favorite`
 *
 * ⚠️ 两边都是 join 出来的：已删/草稿/下架的文章、已删的问题不会出现，
 * 所以 `total` 与「我总共收藏过多少」不是一个数。
 */
type Tab = 'article' | 'question'

const tab = ref<Tab>('article')

const items = ref<ArticleListItem[]>([])
const total = ref(0)
const page = ref(1)
const pageSize = ref(10)
const loading = ref(false)
const removingId = ref<string | null>(null)

const questions = ref<QuestionListItem[]>([])
const questionTotal = ref(0)
const questionPage = ref(1)
const questionPageSize = ref(10)
const questionLoading = ref(false)
const removingQuestionId = ref<string | null>(null)

async function load(retryOnOutOfRange = true) {
  loading.value = true
  try {
    const result = await listMyFavorites(page.value, pageSize.value)
    items.value = result.items
    total.value = result.total
    void loadUserNames(result.items.map((item) => item.authorId))

    if (retryOnOutOfRange && result.items.length === 0 && result.pages > 0 && page.value > result.pages) {
      page.value = result.pages
      await load(false)
    }
  } catch (e) {
    ElMessage.error(e instanceof ApiError ? e.message : '收藏加载失败')
    items.value = []
    total.value = 0
  } finally {
    loading.value = false
  }
}

async function loadQuestions(retryOnOutOfRange = true) {
  questionLoading.value = true
  try {
    const result = await listMyFavoriteQuestions(questionPage.value, questionPageSize.value)
    questions.value = result.items
    questionTotal.value = result.total
    void loadUserNames(result.items.map((item) => item.userId))

    if (
      retryOnOutOfRange &&
      result.items.length === 0 &&
      result.pages > 0 &&
      questionPage.value > result.pages
    ) {
      questionPage.value = result.pages
      await loadQuestions(false)
    }
  } catch (e) {
    ElMessage.error(e instanceof ApiError ? e.message : '收藏加载失败')
    questions.value = []
    questionTotal.value = 0
  } finally {
    questionLoading.value = false
  }
}

/** 取消收藏：接口幂等，返回最新计数 —— 这里直接重取即可（这一页就是靠它列出来的） */
async function onUncollect(article: ArticleListItem) {
  removingId.value = article.articleId
  try {
    await uncollectArticle(article.articleId)
    ElMessage.success('已取消收藏')
    await load()
  } catch (e) {
    ElMessage.error(e instanceof ApiError ? e.message : '取消失败')
  } finally {
    removingId.value = null
  }
}

async function onUnfavorite(question: QuestionListItem) {
  removingQuestionId.value = question.questionId
  try {
    await unfavoriteQuestion(question.questionId)
    ElMessage.success('已取消收藏')
    await loadQuestions()
  } catch (e) {
    ElMessage.error(e instanceof ApiError ? e.message : '取消失败')
  } finally {
    removingQuestionId.value = null
  }
}

watch(page, () => void load())
watch(questionPage, () => void loadQuestions())

// 两份都预拉：Tab 上写着条数，不拉就是个"问答 0"的假数字。
// 都是小分页请求，且各自 try/catch，一边挂了不影响另一边
onMounted(() => {
  void load()
  void loadQuestions()
})
</script>

<template>
  <div class="fav">
    <header class="fav__head">
      <div>
        <h1 class="fav__title">我的收藏</h1>
        <p class="fav__sub">按收藏时间倒序；已删除的内容不会出现在这里</p>
      </div>
    </header>

    <div class="fav__tabs" role="tablist">
      <button
        class="fav__tab"
        type="button"
        role="tab"
        :aria-selected="tab === 'article'"
        :class="{ 'is-on': tab === 'article' }"
        @click="tab = 'article'"
      >
        文章 <span class="wh-mono">{{ total }}</span>
      </button>
      <button
        class="fav__tab"
        type="button"
        role="tab"
        :aria-selected="tab === 'question'"
        :class="{ 'is-on': tab === 'question' }"
        @click="tab = 'question'"
      >
        问答 <span class="wh-mono">{{ questionTotal }}</span>
      </button>
    </div>

    <!-- ================= 文章 ================= -->
    <template v-if="tab === 'article'">
      <el-skeleton v-if="loading && items.length === 0" :rows="5" animated />

      <el-empty v-else-if="items.length === 0" description="还没有收藏任何文章">
        <el-button type="primary" @click="$router.push('/')">去逛逛</el-button>
      </el-empty>

      <template v-else>
        <div class="fav__list" :class="{ 'is-loading': loading }">
          <ArticleRow v-for="article in items" :key="article.articleId" :article="article">
            <template #actions>
              <button
                class="fav__op"
                type="button"
                :disabled="removingId === article.articleId"
                @click="onUncollect(article)"
              >
                取消收藏
              </button>
            </template>
          </ArticleRow>
        </div>

        <div class="fav__pager">
          <el-pagination
            v-model:current-page="page"
            v-model:page-size="pageSize"
            :total="total"
            layout="prev, pager, next, total"
            background
          />
        </div>
      </template>
    </template>

    <!-- ================= 问答 ================= -->
    <template v-else>
      <el-skeleton v-if="questionLoading && questions.length === 0" :rows="5" animated />

      <el-empty v-else-if="questions.length === 0" description="还没有收藏任何问题">
        <el-button type="primary" @click="$router.push('/questions')">去逛逛</el-button>
      </el-empty>

      <template v-else>
        <ul class="fav__list" :class="{ 'is-loading': questionLoading }">
          <QuestionRow
            v-for="question in questions"
            :key="question.questionId"
            :question="question"
          >
            <template #actions>
              <button
                class="fav__op"
                type="button"
                :disabled="removingQuestionId === question.questionId"
                @click="onUnfavorite(question)"
              >
                取消收藏
              </button>
            </template>
          </QuestionRow>
        </ul>

        <div class="fav__pager">
          <el-pagination
            v-model:current-page="questionPage"
            v-model:page-size="questionPageSize"
            :total="questionTotal"
            layout="prev, pager, next, total"
            background
          />
        </div>
      </template>
    </template>
  </div>
</template>

<style scoped>
.fav {
  padding-block: var(--wh-space-5) var(--wh-space-8);
}

/* 与 ArticleList 的工具栏同一套版式：标题与说明，底下一条分隔线 */
.fav__head {
  display: flex;
  flex-wrap: wrap;
  gap: var(--wh-space-4) var(--wh-space-6);
  align-items: flex-end;

  padding-bottom: var(--wh-space-5);
  border-bottom: var(--wh-border-width) solid var(--wh-border-strong);
}

.fav__title {
  font-size: var(--wh-text-page);
}

.fav__sub {
  max-width: 36rem;
  margin-top: var(--wh-space-2);

  font-size: var(--wh-text-base);
  color: var(--wh-text-2);
}

/* Tab：不用 el-tabs —— 那两个面板的骨架/空态/分页都得自己管，
   用按钮 + v-if 更直白，样式也和站内其它「下划线激活」保持一致 */
.fav__tabs {
  display: flex;
  gap: var(--wh-space-5);

  margin-top: var(--wh-space-4);
  border-bottom: var(--wh-border-width) solid var(--wh-border);
}

.fav__tab {
  display: inline-flex;
  gap: var(--wh-space-2);
  align-items: baseline;

  padding: 0 0 var(--wh-space-2);

  font-family: inherit;
  font-size: var(--wh-text-base);
  color: var(--wh-text-3);
  background: none;
  border: none;
  border-bottom: 2px solid transparent;
  cursor: pointer;

  transition: color var(--wh-duration-fast) var(--wh-ease);
}

.fav__tab:hover {
  color: var(--wh-text-1);
}

.fav__tab.is-on {
  color: var(--wh-text-1);
  border-bottom-color: var(--wh-accent);
}

.fav__tab .wh-mono {
  font-size: var(--wh-text-xs);
  color: var(--wh-text-3);
}

.fav__list {
  list-style: none;
}

.fav__list.is-loading {
  opacity: 0.55;
  transition: opacity var(--wh-duration-base) var(--wh-ease);
}

.fav__op {
  padding: 0;

  font-family: inherit;
  font-size: var(--wh-text-xs);
  color: var(--wh-text-3);
  background: none;
  border: none;
  cursor: pointer;
}

.fav__op:hover:not(:disabled) {
  color: var(--wh-danger);
  text-decoration: underline;
}

.fav__op:disabled {
  cursor: default;
  opacity: 0.6;
}

.fav__pager {
  display: flex;
  justify-content: center;
  padding-top: var(--wh-space-6);
}
</style>
