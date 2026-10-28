<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { RouterLink, useRouter } from 'vue-router'

import { list as listArticles } from '@/api/modules/article'
import { list as listQuestions } from '@/api/modules/question'
import {
  home as fetchHomeRecommendations,
  hot as fetchHotArticles,
} from '@/api/modules/recommendation'
import { listHot } from '@/api/modules/tag'
import WhButton from '@/components/base/WhButton.vue'
import WhInput from '@/components/base/WhInput.vue'
import ArticleCard from '@/components/business/ArticleCard.vue'
import HomeSection from '@/components/home/HomeSection.vue'
import PageContainer from '@/components/layout/PageContainer.vue'
import { useAsyncData } from '@/composables/useAsyncData'
import { useArticleAuthors } from '@/composables/useArticleAuthors'
import { useSeo } from '@/composables/useSeo'
import { useAuthStore } from '@/stores'
import { formatDateTime } from '@/utils'

const auth = useAuthStore()
const router = useRouter()

const keyword = ref('')
const loggedIn = computed(() => auth.isLoggedIn())
const greeting = computed(() => {
  const hour = new Date().getHours()
  if (hour < 6) return '夜深了'
  if (hour < 12) return '早上好'
  if (hour < 18) return '下午好'
  return '晚上好'
})

const {
  data: recommendations,
  loading: recommendationsLoading,
  error: recommendationsError,
  execute: executeRecommendations,
  reset: resetRecommendations,
} = useAsyncData(() => fetchHomeRecommendations(6, { skipErrorToast: true }), {
  immediate: false,
})

const {
  data: hotTags,
  loading: hotTagsLoading,
  error: hotTagsError,
  execute: executeHotTags,
} = useAsyncData(() => listHot(8, { skipErrorToast: true }), { immediate: false })

const {
  data: hotArticles,
  loading: hotArticlesLoading,
  error: hotArticlesError,
  execute: executeHotArticles,
} = useAsyncData(() => fetchHotArticles({ limit: 6 }, { skipErrorToast: true }), {
  immediate: false,
})

const {
  data: hotQuestions,
  loading: hotQuestionsLoading,
  error: hotQuestionsError,
  execute: executeHotQuestions,
} = useAsyncData(
  () => listQuestions({ sort: 'hot', page: 1, pageSize: 5 }, { skipErrorToast: true }),
  { immediate: false },
)

const {
  data: latestArticles,
  loading: latestArticlesLoading,
  error: latestArticlesError,
  execute: executeLatestArticles,
} = useAsyncData(
  () => listArticles({ sort: 'new', page: 1, pageSize: 6 }, { skipErrorToast: true }),
  { immediate: false },
)

const { authors } = useArticleAuthors(
  recommendations,
  hotArticles,
  () => latestArticles.value?.items,
)

const recommendationsEmpty = computed(
  () =>
    !recommendationsLoading.value && !recommendationsError.value && !recommendations.value?.length,
)
const hotTagsEmpty = computed(
  () => !hotTagsLoading.value && !hotTagsError.value && !hotTags.value?.length,
)
const hotArticlesEmpty = computed(
  () => !hotArticlesLoading.value && !hotArticlesError.value && !hotArticles.value?.length,
)
const hotQuestionsEmpty = computed(
  () => !hotQuestionsLoading.value && !hotQuestionsError.value && !hotQuestions.value?.items.length,
)
const latestArticlesEmpty = computed(
  () =>
    !latestArticlesLoading.value &&
    !latestArticlesError.value &&
    !latestArticles.value?.items.length,
)

function padIndex(index: number): string {
  return String(index + 1).padStart(2, '0')
}

async function loadHome(): Promise<void> {
  const tasks: Promise<unknown>[] = [
    executeHotTags(),
    executeHotArticles(),
    executeHotQuestions(),
    executeLatestArticles(),
  ]
  if (loggedIn.value) tasks.push(executeRecommendations())
  await Promise.allSettled(tasks)
}

function submitSearch(): void {
  const value = keyword.value.trim()
  if (!value) return
  void router.push({ name: 'search', query: { keyword: value } })
}

watch(loggedIn, (value) => {
  if (value) void executeRecommendations()
  else resetRecommendations()
})

useSeo({
  title: '首页',
  description: '把散落的知识，连成一张图。文章、问答、搜索与 AI，共用同一张知识网络。',
})

void loadHome()
</script>

<template>
  <PageContainer tag="div">
    <div class="home-page">
      <section class="home-hero">
        <div class="home-hero__main">
          <p class="home-hero__eyebrow">[ WISDOMHUB / HOME ]</p>
          <h1 v-if="loggedIn">{{ greeting }}，<br />欢迎回来。</h1>
          <h1 v-else>把散落的知识，<br />连成一张图。</h1>
          <p class="home-hero__lede">
            {{
              loggedIn
                ? '根据你的兴趣，挑了几篇最近值得读的文章。'
                : '文章、问答、搜索与 AI，共用同一张知识网络。'
            }}
          </p>

          <form class="home-search" @submit.prevent="submitSearch">
            <WhInput
              v-model="keyword"
              size="lg"
              placeholder="搜索文章、问题或标签"
              aria-label="搜索文章、问题或标签"
            />
            <WhButton type="submit" size="lg">搜索</WhButton>
          </form>

          <div class="home-hero__actions">
            <template v-if="loggedIn">
              <RouterLink class="home-hero__cta" to="/editor/new">写一篇文章</RouterLink>
              <RouterLink
                class="home-hero__ghost"
                :to="{ name: 'user-profile', params: { userId: auth.userId ?? '' } }"
              >
                我的主页
              </RouterLink>
            </template>
            <template v-else>
              <RouterLink class="home-hero__cta" :to="{ path: '/login', query: { redirect: '/' } }">
                登录
              </RouterLink>
              <RouterLink class="home-hero__ghost" to="/register">注册</RouterLink>
            </template>
          </div>
        </div>

        <div class="home-hero__graphic" aria-hidden="true">
          <svg class="home-graph" viewBox="0 0 340 340" fill="none">
            <g class="home-graph__lines">
              <path d="M65 82 L170 170" />
              <path d="M275 72 L170 170" />
              <path d="M72 272 L170 170" />
              <path d="M286 258 L170 170" />
              <path d="M65 82 L275 72" opacity=".3" />
              <path d="M72 272 L286 258" opacity=".3" />
            </g>
            <g class="home-graph__nodes">
              <rect x="20" y="60" width="90" height="34" />
              <rect x="240" y="52" width="78" height="34" />
              <rect x="30" y="255" width="84" height="34" />
              <rect x="252" y="242" width="68" height="34" />
            </g>
            <circle cx="170" cy="170" r="46" class="home-graph__core" />
            <text x="170" y="177" text-anchor="middle" class="home-graph__core-text">WH</text>
            <g class="home-graph__labels">
              <text x="65" y="81">文章</text>
              <text x="279" y="73">问答</text>
              <text x="72" y="276">标签</text>
              <text x="286" y="263">AI</text>
            </g>
          </svg>
        </div>
      </section>

      <div class="home-sections">
        <HomeSection
          v-if="loggedIn"
          eyebrow="[ FOR YOU ]"
          title="为你推荐"
          action-label="去发现"
          :action-to="{ name: 'article-list' }"
          :loading="recommendationsLoading"
          :error="recommendationsError"
          :empty="recommendationsEmpty"
          empty-text="暂时没有推荐，稍后再来看看。"
          skeleton="article"
          @retry="executeRecommendations"
        >
          <div class="home-article-grid">
            <ArticleCard
              v-for="article in recommendations"
              :key="article.articleId"
              :article="article"
              :author="authors[article.authorId]"
              compact
              :tags="article.tags"
            />
          </div>
        </HomeSection>

        <HomeSection
          eyebrow="[ HOT TAGS ]"
          title="热门技术"
          action-label="全部标签"
          :action-to="{ name: 'tag-list' }"
          :loading="hotTagsLoading"
          :error="hotTagsError"
          :empty="hotTagsEmpty"
          empty-text="暂无热门标签。"
          skeleton="tag"
          @retry="executeHotTags"
        >
          <div class="home-tags">
            <RouterLink
              v-for="(tag, index) in hotTags"
              :key="tag.tagId"
              class="home-tag"
              :to="{ name: 'tech-detail', params: { tagId: tag.tagId } }"
            >
              <span class="home-tag__index">{{ padIndex(index) }}</span>
              <span class="home-tag__name">{{ tag.name }}</span>
              <span class="home-tag__foot">
                <span>{{ tag.articleCount }} 篇</span>
                <span aria-hidden="true">↗</span>
              </span>
            </RouterLink>
          </div>
        </HomeSection>

        <HomeSection
          eyebrow="[ HOT ARTICLES ]"
          title="热门文章"
          action-label="查看热门"
          :action-to="{ name: 'article-list', query: { sort: 'hot' } }"
          :loading="hotArticlesLoading"
          :error="hotArticlesError"
          :empty="hotArticlesEmpty"
          empty-text="暂无热门文章。"
          skeleton="article"
          @retry="executeHotArticles"
        >
          <div class="home-article-grid">
            <ArticleCard
              v-for="article in hotArticles"
              :key="article.articleId"
              :article="article"
              :author="authors[article.authorId]"
              compact
              :tags="article.tags"
            />
          </div>
        </HomeSection>

        <HomeSection
          eyebrow="[ HOT QUESTIONS ]"
          title="热门问题"
          action-label="全部问答"
          :action-to="{ name: 'question-list', query: { sort: 'hot' } }"
          :loading="hotQuestionsLoading"
          :error="hotQuestionsError"
          :empty="hotQuestionsEmpty"
          empty-text="暂无热门问题。"
          skeleton="question"
          @retry="executeHotQuestions"
        >
          <ol class="home-questions">
            <li
              v-for="(question, index) in hotQuestions?.items ?? []"
              :key="question.questionId"
              class="home-question"
            >
              <span class="home-question__index">{{ padIndex(index) }}</span>
              <div class="home-question__main">
                <RouterLink
                  class="home-question__title"
                  :to="{ name: 'question-detail', params: { questionId: question.questionId } }"
                >
                  {{ question.title }}
                </RouterLink>
                <div class="home-question__meta">
                  <span>{{ question.answerCount }} 回答</span>
                  <span>{{ question.favoriteCount }} 收藏</span>
                  <time :datetime="question.createTime">
                    {{ formatDateTime(question.createTime, 'YYYY-MM-DD') }}
                  </time>
                </div>
              </div>
              <span class="home-question__state" :class="{ 'is-solved': question.solved }">
                {{ question.solved ? '已解决' : '待解决' }}
              </span>
            </li>
          </ol>
        </HomeSection>

        <HomeSection
          eyebrow="[ LATEST ]"
          title="最新文章"
          action-label="查看最新"
          :action-to="{ name: 'article-list', query: { sort: 'new' } }"
          :loading="latestArticlesLoading"
          :error="latestArticlesError"
          :empty="latestArticlesEmpty"
          empty-text="暂无最新文章。"
          skeleton="article"
          @retry="executeLatestArticles"
        >
          <div class="home-article-grid">
            <ArticleCard
              v-for="article in latestArticles?.items ?? []"
              :key="article.articleId"
              :article="article"
              :author="authors[article.authorId]"
              compact
            />
          </div>
        </HomeSection>
      </div>
    </div>
  </PageContainer>
</template>

<style scoped>
.home-page {
  display: grid;
  gap: clamp(32px, 5vw, 64px);
}

/* ---------- Hero ---------- */

.home-hero {
  position: relative;
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  gap: clamp(24px, 4vw, 56px);
  align-items: center;
  padding: clamp(28px, 5vw, 56px);
  overflow: hidden;
  background: var(--ink);
  color: var(--bone);
  box-shadow: var(--shadow-hard);
}

.home-hero::before {
  content: '';
  position: absolute;
  inset: 0;
  background-image:
    linear-gradient(var(--line-inverse) 1px, transparent 1px),
    linear-gradient(90deg, var(--line-inverse) 1px, transparent 1px);
  background-size: 48px 48px;
  opacity: 0.2;
  pointer-events: none;
}

.home-hero__main {
  position: relative;
  z-index: 1;
  display: grid;
  align-content: center;
  gap: var(--space-4);
  min-width: 0;
}

.home-hero__eyebrow {
  color: var(--verm);
  font-family: var(--mono);
  font-size: var(--text-2xs);
  letter-spacing: var(--tracking-wider);
}

.home-hero h1 {
  color: var(--bone);
  font-family: var(--serif);
  font-size: clamp(2.25rem, 4.2vw, 4rem);
  font-weight: var(--weight-black);
  line-height: 1.05;
  letter-spacing: var(--tracking-tight);
}

.home-hero__lede {
  max-width: 46ch;
  color: color-mix(in srgb, var(--bone) 72%, transparent);
  font-size: var(--text-sm);
  line-height: var(--leading-relaxed);
}

.home-search {
  display: flex;
  gap: var(--space-2);
  width: 100%;
  max-width: 560px;
  padding-top: var(--space-2);
}

.home-search :deep(.wh-field) {
  flex: 1;
}

.home-search :deep(.wh-input) {
  min-height: 48px;
  background: var(--bone);
}

.home-search :deep(.wh-button) {
  min-height: 48px;
}

.home-hero__actions {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-2);
  padding-top: var(--space-1);
}

.home-hero__cta,
.home-hero__ghost {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-height: 40px;
  padding-inline: 16px;
  border: 1px solid var(--acid);
  font-family: var(--sans);
  font-size: var(--text-sm);
  font-weight: var(--weight-semibold);
  text-decoration: none;
  transition:
    background var(--dur) var(--ease),
    color var(--dur) var(--ease);
}

.home-hero__cta {
  background: var(--acid);
  color: var(--ink);
}

.home-hero__cta:hover {
  background: transparent;
  color: var(--acid);
}

.home-hero__ghost {
  background: transparent;
  border-color: color-mix(in srgb, var(--bone) 34%, transparent);
  color: var(--bone);
}

.home-hero__ghost:hover {
  border-color: var(--acid);
  color: var(--acid);
}

.home-hero__graphic {
  position: relative;
  z-index: 1;
  display: flex;
  justify-content: center;
}

.home-graph {
  width: min(340px, 30vw);
  height: auto;
  overflow: visible;
}

.home-graph__lines {
  color: color-mix(in srgb, var(--bone) 28%, transparent);
}

.home-graph__lines path {
  stroke: currentColor;
  stroke-width: 1;
}

.home-graph__nodes rect {
  fill: var(--ink);
  stroke: var(--acid);
  stroke-width: 1;
}

.home-graph__core {
  fill: var(--acid);
}

.home-graph__core-text {
  fill: var(--ink);
  font-family: var(--serif);
  font-size: 22px;
  font-weight: 900;
}

.home-graph__labels text {
  fill: var(--acid);
  font-family: var(--mono);
  font-size: 11px;
  letter-spacing: 0.08em;
  text-anchor: middle;
  dominant-baseline: middle;
}

/* ---------- Sections ---------- */

.home-sections {
  display: grid;
  gap: clamp(36px, 5vw, 56px);
}

.home-article-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  column-gap: clamp(24px, 4vw, 56px);
}

.home-article-grid :deep(.article-card) {
  min-width: 0;
}

.home-tags {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(160px, 1fr));
  gap: 1px;
  border: 1px solid var(--line);
  background: var(--line);
}

.home-tag {
  display: grid;
  gap: var(--space-3);
  padding: var(--space-4);
  background: var(--surface);
  text-decoration: none;
  transition: background var(--dur) var(--ease);
}

.home-tag:hover {
  background: var(--acid);
}

.home-tag__index {
  color: var(--text-muted);
  font-family: var(--mono);
  font-size: var(--text-2xs);
}

.home-tag__name {
  color: var(--ink);
  font-family: var(--serif);
  font-size: var(--text-xl);
  font-weight: var(--weight-bold);
  line-height: var(--leading-snug);
}

.home-tag__foot {
  display: flex;
  align-items: center;
  justify-content: space-between;
  color: var(--text-muted);
  font-family: var(--mono);
  font-size: var(--text-2xs);
}

.home-tag:hover .home-tag__index,
.home-tag:hover .home-tag__foot {
  color: var(--ink);
}

.home-questions {
  display: grid;
}

.home-question {
  display: grid;
  grid-template-columns: 44px minmax(0, 1fr) auto;
  align-items: center;
  gap: var(--space-4);
  padding-block: var(--space-4);
  border-top: var(--border);
}

.home-question:first-child {
  border-top: 0;
}

.home-question__index {
  color: var(--text-muted);
  font-family: var(--mono);
  font-size: var(--text-2xs);
}

.home-question__main {
  display: grid;
  gap: var(--space-2);
  min-width: 0;
}

.home-question__title {
  color: var(--ink);
  font-family: var(--serif);
  font-size: var(--text-lg);
  font-weight: var(--weight-semibold);
  line-height: var(--leading-snug);
  text-decoration: none;
  transition: color var(--dur) var(--ease);
}

.home-question__title:hover {
  color: var(--verm-deep);
}

.home-question__meta {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-2) var(--space-4);
  color: var(--text-muted);
  font-family: var(--mono);
  font-size: var(--text-2xs);
}

.home-question__state {
  padding: 2px 7px;
  border: 1px solid var(--line-strong);
  color: var(--text-muted);
  font-family: var(--mono);
  font-size: var(--text-2xs);
  white-space: nowrap;
}

.home-question__state.is-solved {
  border-color: var(--acid);
  background: var(--acid);
  color: var(--ink);
}

/* ---------- Responsive ---------- */

@media (max-width: 900px) {
  .home-hero {
    grid-template-columns: 1fr;
  }

  .home-hero__graphic {
    display: none;
  }
}

@media (max-width: 720px) {
  .home-article-grid {
    grid-template-columns: 1fr;
  }

  .home-question {
    grid-template-columns: 32px minmax(0, 1fr);
    align-items: start;
  }

  .home-question__state {
    grid-column: 2;
    justify-self: start;
  }
}

@media (max-width: 640px) {
  .home-hero {
    padding: var(--space-5);
    box-shadow: var(--shadow-hard-sm);
  }

  .home-search {
    flex-direction: column;
  }

  .home-search :deep(.wh-button) {
    width: 100%;
  }

  .home-tags {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}
</style>
