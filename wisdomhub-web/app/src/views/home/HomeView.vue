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
import AnimatedCounter from '@/components/effects/AnimatedCounter.vue'
import FluidBackground from '@/components/effects/FluidBackground.vue'
import GlowBorder from '@/components/effects/GlowBorder.vue'
import ParticleBackground from '@/components/effects/ParticleBackground.vue'
import TiltCard from '@/components/effects/TiltCard.vue'
import TypewriterText from '@/components/effects/TypewriterText.vue'
import HomeSection from '@/components/home/HomeSection.vue'
import PageContainer from '@/components/layout/PageContainer.vue'
import VideoCard from '@/components/video/VideoCard.vue'
import { useAsyncData } from '@/composables/useAsyncData'
import { useArticleAuthors } from '@/composables/useArticleAuthors'
import { useInteractionCounts } from '@/composables/useInteractionCounts'
import { useSeo } from '@/composables/useSeo'
import { useAuthStore } from '@/stores'
import { CONTENT_TARGET_TYPE } from '@/types/interaction'
import type { RecommendationItemVO } from '@/types/recommendation'
import type { VideoListItemVO } from '@/types/video'
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
} = useAsyncData(() => fetchHomeRecommendations(20, { skipErrorToast: true }), {
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

function isVideo(item: RecommendationItemVO): boolean {
  return item.contentType === CONTENT_TARGET_TYPE.VIDEO
}

/** 推荐条目 → 视频卡片所需的扁平结构 */
function toVideoItem(item: RecommendationItemVO): VideoListItemVO {
  return {
    videoId: item.videoId ?? item.articleId,
    authorId: item.authorId,
    title: item.title,
    summary: item.summary ?? null,
    coverKey: item.coverKey,
    coverUrl: item.coverUrl ?? '',
    durationMs: item.durationMs,
    viewCount: item.viewCount,
    publishTime: item.publishTime,
    tags: item.tags,
  }
}

const recommendedArticles = computed(() =>
  (recommendations.value ?? []).filter((item) => !isVideo(item)).slice(0, 6),
)
const recommendedVideos = computed(() =>
  (recommendations.value ?? []).filter(isVideo).slice(0, 6),
)

/** 三个文章区块 + 推荐视频 + 热门问题共用一个批取：重复 id 在 composable 里去重 */
const homeInteractionTargets = computed(() => [
  ...recommendedArticles.value.map((item) => ({
    targetType: CONTENT_TARGET_TYPE.ARTICLE,
    targetId: item.articleId,
  })),
  ...recommendedVideos.value.map((item) => ({
    targetType: CONTENT_TARGET_TYPE.VIDEO,
    targetId: item.videoId ?? item.articleId,
  })),
  ...(hotArticles.value ?? []).map((article) => ({
    targetType: CONTENT_TARGET_TYPE.ARTICLE,
    targetId: article.articleId,
  })),
  ...(latestArticles.value?.items ?? []).map((article) => ({
    targetType: CONTENT_TARGET_TYPE.ARTICLE,
    targetId: article.articleId,
  })),
  ...(hotQuestions.value?.items ?? []).map((question) => ({
    targetType: CONTENT_TARGET_TYPE.QUESTION,
    targetId: question.questionId,
  })),
])
const { countsFor } = useInteractionCounts(homeInteractionTargets)

const recommendedArticlesEmpty = computed(
  () =>
    !recommendationsLoading.value &&
    !recommendationsError.value &&
    recommendedArticles.value.length === 0,
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

const totalArticleCount = computed(() =>
  (hotArticles.value ?? []).reduce((sum, a) => sum + (a.viewCount || 0), 0),
)
const totalTagCount = computed(() => hotTags.value?.length ?? 0)
const totalQuestionCount = computed(() => hotQuestions.value?.total ?? 0)

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
        <FluidBackground :colors="['#c9ff2e', '#ff3b1f', '#3b1dff']" :blur="100" :opacity="0.15" />
        <ParticleBackground
          :particle-count="50"
          :connection-distance="120"
          particle-color="#c9ff2e"
          line-color="#c9ff2e"
          :speed="0.3"
        />

        <div class="home-hero__content">
          <div class="home-hero__main" v-reveal="{ animation: 'left', delay: 100 }">
            <p class="home-hero__eyebrow">
              <span class="home-hero__eyebrow-dot" />
              [ WISDOMHUB / HOME ]
            </p>
            <h1 class="home-hero__title">
              <TypewriterText
                v-if="loggedIn"
                :text="`${greeting}，\n欢迎回来。`"
                :speed="50"
                :delay="500"
              />
              <TypewriterText v-else :text="'把散落的知识，\n连成一张图。'" :speed="60" :delay="300" />
            </h1>
          <p v-if="!loggedIn" class="home-hero__lede" v-reveal="{ animation: 'up', delay: 400 }">
            文章、问答、搜索与 AI，共用同一张知识网络。
          </p>

          <form class="home-search" @submit.prevent="submitSearch" v-reveal="{ animation: 'up', delay: 600 }">
            <WhInput
              v-model="keyword"
              size="lg"
              placeholder="搜索文章、视频、问题或标签"
              aria-label="搜索文章、视频、问题或标签"
            />
            <WhButton type="submit" size="lg">搜索</WhButton>
          </form>

          <div class="home-hero__actions" v-reveal="{ animation: 'up', delay: 800 }">
            <template v-if="loggedIn">
              <RouterLink class="home-hero__cta" to="/editor/new">写一篇文章</RouterLink>
              <RouterLink class="home-hero__cta" to="/videos/upload">发一段视频</RouterLink>
            </template>
            <template v-else>
              <RouterLink class="home-hero__cta" :to="{ path: '/login', query: { redirect: '/' } }">
                登录
              </RouterLink>
              <RouterLink class="home-hero__ghost" to="/register">注册</RouterLink>
            </template>
          </div>

          <div class="home-stats" v-reveal="{ animation: 'up', delay: 1000 }">
            <div class="home-stat">
              <AnimatedCounter :value="totalArticleCount" class="home-stat__value" />
              <span class="home-stat__label">次浏览</span>
            </div>
            <div class="home-stat-divider" />
            <div class="home-stat">
              <AnimatedCounter :value="totalTagCount" class="home-stat__value" />
              <span class="home-stat__label">个标签</span>
            </div>
            <div class="home-stat-divider" />
            <div class="home-stat">
              <AnimatedCounter :value="totalQuestionCount" class="home-stat__value" />
              <span class="home-stat__label">个问题</span>
            </div>
          </div>
        </div>

        <div class="home-hero__graphic" aria-hidden="true" v-reveal="{ animation: 'scale', delay: 300 }">
          <svg class="home-graph" viewBox="0 0 340 340" fill="none">
            <defs>
              <filter id="glow">
                <feGaussianBlur stdDeviation="3" result="coloredBlur" />
                <feMerge>
                  <feMergeNode in="coloredBlur" />
                  <feMergeNode in="SourceGraphic" />
                </feMerge>
              </filter>
              <linearGradient id="lineGradient" x1="0%" y1="0%" x2="100%" y2="100%">
                <stop offset="0%" stop-color="#c9ff2e" stop-opacity="0.8" />
                <stop offset="100%" stop-color="#ff3b1f" stop-opacity="0.4" />
              </linearGradient>
            </defs>

            <g class="home-graph__lines">
              <path d="M65 82 L170 170" stroke="url(#lineGradient)" stroke-width="1.5" class="graph-line" />
              <path d="M275 72 L170 170" stroke="url(#lineGradient)" stroke-width="1.5" class="graph-line" style="animation-delay: 0.2s" />
              <path d="M72 272 L170 170" stroke="url(#lineGradient)" stroke-width="1.5" class="graph-line" style="animation-delay: 0.4s" />
              <path d="M286 258 L170 170" stroke="url(#lineGradient)" stroke-width="1.5" class="graph-line" style="animation-delay: 0.6s" />
              <path d="M65 82 L275 72" stroke="url(#lineGradient)" stroke-width="1" opacity=".3" class="graph-line" style="animation-delay: 0.8s" />
              <path d="M72 272 L286 258" stroke="url(#lineGradient)" stroke-width="1" opacity=".3" class="graph-line" style="animation-delay: 1s" />
            </g>
            <g class="home-graph__nodes">
              <rect x="20" y="60" width="90" height="34" class="graph-node" style="animation-delay: 0.3s" />
              <rect x="240" y="52" width="78" height="34" class="graph-node" style="animation-delay: 0.5s" />
              <rect x="30" y="255" width="84" height="34" class="graph-node" style="animation-delay: 0.7s" />
              <rect x="252" y="242" width="68" height="34" class="graph-node" style="animation-delay: 0.9s" />
            </g>
            <circle cx="170" cy="170" r="46" class="home-graph__core" filter="url(#glow)" />
            <circle cx="170" cy="170" r="46" class="home-graph__core-pulse" />
            <text x="170" y="177" text-anchor="middle" class="home-graph__core-text">WH</text>
            <g class="home-graph__labels">
              <text x="65" y="81">文章</text>
              <text x="279" y="73">问答</text>
              <text x="72" y="276">视频</text>
              <text x="286" y="263">AI</text>
            </g>
            <circle class="graph-particle" cx="100" cy="120" r="2" />
            <circle class="graph-particle" cx="240" cy="150" r="2" style="animation-delay: 1s" />
            <circle class="graph-particle" cx="150" cy="250" r="2" style="animation-delay: 2s" />
            <circle class="graph-particle" cx="220" cy="100" r="2" style="animation-delay: 3s" />
          </svg>
        </div>
      </div>
    </section>

      <div class="home-sections">
        <HomeSection
          v-if="loggedIn"
          eyebrow="[ FOR YOU / ARTICLES ]"
          title="推荐文章"
          action-label="全部文章"
          :action-to="{ name: 'article-list' }"
          :loading="recommendationsLoading"
          :error="recommendationsError"
          :empty="recommendedArticlesEmpty"
          empty-text="暂时没有推荐文章，稍后再来看看。"
          skeleton="article"
          @retry="executeRecommendations"
        >
          <div class="home-article-grid">
            <TiltCard
              v-for="(item, index) in recommendedArticles"
              :key="item.articleId"
              :max-tilt="8"
              :scale="1.02"
              v-reveal="{ animation: 'up', delay: index * 100 }"
            >
              <ArticleCard
                :article="item"
                :author="authors[item.authorId]"
                :counts="countsFor(CONTENT_TARGET_TYPE.ARTICLE, item.articleId)"
                compact
                :tags="item.tags"
              />
            </TiltCard>
          </div>
        </HomeSection>

        <HomeSection
          v-if="loggedIn && recommendedVideos.length"
          eyebrow="[ FOR YOU / VIDEOS ]"
          title="推荐视频"
          action-label="全部视频"
          :action-to="{ name: 'video-list' }"
          skeleton="article"
        >
          <div class="home-video-grid">
            <TiltCard
              v-for="(item, index) in recommendedVideos"
              :key="item.videoId ?? item.articleId"
              :max-tilt="10"
              :scale="1.03"
              v-reveal="{ animation: 'scale', delay: index * 100 }"
            >
              <VideoCard
                :video="toVideoItem(item)"
                :author="authors[item.authorId]"
                :counts="countsFor(CONTENT_TARGET_TYPE.VIDEO, item.videoId ?? item.articleId)"
              />
            </TiltCard>
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
            <GlowBorder
              v-for="(tag, index) in hotTags"
              :key="tag.tagId"
              color="var(--acid)"
              :animated="false"
              v-reveal="{ animation: 'left', delay: index * 80 }"
            >
              <RouterLink
                class="home-tag"
                :to="{ name: 'tech-detail', params: { tagId: tag.tagId } }"
              >
                <span class="home-tag__index">{{ padIndex(index) }}</span>
                <span class="home-tag__name">{{ tag.name }}</span>
                <span class="home-tag__foot">
                  <span>{{ tag.articleCount }} 篇</span>
                  <span class="home-tag__arrow" aria-hidden="true">&#8599;</span>
                </span>
              </RouterLink>
            </GlowBorder>
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
            <TiltCard
              v-for="(article, index) in hotArticles"
              :key="article.articleId"
              :max-tilt="8"
              :scale="1.02"
              v-reveal="{ animation: 'up', delay: index * 100 }"
            >
              <ArticleCard
                :article="article"
                :author="authors[article.authorId]"
                :counts="countsFor(CONTENT_TARGET_TYPE.ARTICLE, article.articleId)"
                compact
                :tags="article.tags"
              />
            </TiltCard>
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
              v-reveal="{ animation: 'right', delay: index * 100 }"
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
                  <span
                    >{{ countsFor(CONTENT_TARGET_TYPE.QUESTION, question.questionId)?.favoriteCount ?? '—' }}
                    收藏</span
                  >
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
            <TiltCard
              v-for="(article, index) in latestArticles?.items ?? []"
              :key="article.articleId"
              :max-tilt="8"
              :scale="1.02"
              v-reveal="{ animation: 'up', delay: index * 100 }"
            >
              <ArticleCard
                :article="article"
                :author="authors[article.authorId]"
                :counts="countsFor(CONTENT_TARGET_TYPE.ARTICLE, article.articleId)"
                compact
              />
            </TiltCard>
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

.home-hero {
  position: relative;
  padding: clamp(28px, 5vw, 56px);
  overflow: hidden;
  background: var(--ink);
  color: var(--bone);
  box-shadow: var(--shadow-hard);
  min-height: 500px;
  display: flex;
  align-items: center;
}

.home-hero__content {
  position: relative;
  z-index: 2;
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  gap: clamp(24px, 4vw, 56px);
  align-items: center;
  width: 100%;
}

.home-hero__main {
  display: grid;
  align-content: center;
  gap: var(--space-4);
  min-width: 0;
}

.home-hero__eyebrow {
  display: inline-flex;
  align-items: center;
  gap: var(--space-2);
  color: var(--verm);
  font-family: var(--mono);
  font-size: var(--text-2xs);
  letter-spacing: var(--tracking-wider);
}

.home-hero__eyebrow-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: var(--verm);
  animation: pulse-dot 2s ease-in-out infinite;
}

@keyframes pulse-dot {
  0%, 100% { opacity: 1; transform: scale(1); }
  50% { opacity: 0.5; transform: scale(1.5); }
}

.home-hero__title {
  font-family: var(--serif);
  font-size: clamp(2.25rem, 4.2vw, 4rem);
  font-weight: var(--weight-black);
  line-height: 1.05;
  letter-spacing: var(--tracking-tight);
  color: var(--bone);
  min-height: 2.2em;
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

.home-search :deep(.wh-field) { flex: 1; }
.home-search :deep(.wh-input) { min-height: 48px; background: var(--bone); }
.home-search :deep(.wh-button) { min-height: 48px; }

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
  padding-inline: 20px;
  border: 1px solid var(--acid);
  font-family: var(--mono);
  font-size: var(--text-sm);
  font-weight: var(--weight-semibold);
  text-decoration: none;
  transition: all var(--dur) var(--ease);
  position: relative;
  overflow: hidden;
}

.home-hero__cta::before {
  content: '';
  position: absolute;
  inset: 0;
  background: var(--acid);
  transform: translateX(-100%);
  transition: transform var(--dur-slow) var(--ease);
  z-index: -1;
}

.home-hero__cta:hover::before { transform: translateX(0); }
.home-hero__cta { background: var(--acid); color: var(--ink); }

.home-hero__cta:hover {
  color: var(--ink);
  transform: translateY(-2px);
  box-shadow: 0 4px 20px rgb(201 255 46 / 40%);
}

.home-hero__ghost {
  background: transparent;
  border-color: color-mix(in srgb, var(--bone) 34%, transparent);
  color: var(--bone);
}

.home-hero__ghost:hover {
  border-color: var(--acid);
  color: var(--acid);
  transform: translateY(-2px);
}

.home-stats {
  display: flex;
  align-items: center;
  gap: var(--space-4);
  padding-top: var(--space-4);
  margin-top: var(--space-2);
}

.home-stat { display: flex; flex-direction: column; gap: 2px; }

.home-stat__value {
  font-family: var(--mono);
  font-size: var(--text-xl);
  font-weight: var(--weight-black);
  color: var(--acid);
}

.home-stat__label {
  font-family: var(--mono);
  font-size: var(--text-2xs);
  color: color-mix(in srgb, var(--bone) 60%, transparent);
  letter-spacing: var(--tracking-wide);
}

.home-stat-divider {
  width: 1px;
  height: 32px;
  background: color-mix(in srgb, var(--bone) 20%, transparent);
}

.home-hero__graphic {
  position: relative;
  z-index: 1;
  display: flex;
  justify-content: center;
}

.home-graph { width: min(340px, 30vw); height: auto; overflow: visible; }

.home-graph__lines path {
  stroke-dasharray: 300;
  stroke-dashoffset: 300;
  animation: draw-line 1.5s ease-out forwards;
}

@keyframes draw-line { to { stroke-dashoffset: 0; } }

.graph-node {
  fill: var(--ink);
  stroke: var(--acid);
  stroke-width: 1.5;
  opacity: 0;
  animation: node-appear 0.5s ease-out forwards;
}

@keyframes node-appear {
  from { opacity: 0; transform: scale(0.8); }
  to { opacity: 1; transform: scale(1); }
}

.home-graph__core {
  fill: var(--acid);
  animation: core-pulse 3s ease-in-out infinite;
}

@keyframes core-pulse {
  0%, 100% { filter: drop-shadow(0 0 10px var(--acid)); }
  50% { filter: drop-shadow(0 0 25px var(--acid)); }
}

.home-graph__core-pulse {
  fill: none;
  stroke: var(--acid);
  stroke-width: 2;
  animation: ring-expand 2s ease-out infinite;
}

@keyframes ring-expand {
  0% { r: 46; opacity: 1; }
  100% { r: 70; opacity: 0; }
}

.home-graph__core-text { fill: var(--ink); font-family: var(--serif); font-size: 22px; font-weight: 900; }

.home-graph__labels text {
  fill: var(--acid);
  font-family: var(--mono);
  font-size: 11px;
  letter-spacing: 0.08em;
  text-anchor: middle;
  dominant-baseline: middle;
}

.graph-particle {
  fill: var(--acid);
  opacity: 0.6;
  animation: float-particle 4s ease-in-out infinite;
}

@keyframes float-particle {
  0%, 100% { transform: translate(0, 0); opacity: 0.6; }
  25% { transform: translate(10px, -15px); opacity: 1; }
  50% { transform: translate(-5px, -30px); opacity: 0.4; }
  75% { transform: translate(-15px, -15px); opacity: 0.8; }
}

.home-sections { display: grid; gap: clamp(36px, 5vw, 56px); }

.home-article-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  column-gap: clamp(24px, 4vw, 56px);
}

.home-article-grid :deep(.article-card) { min-width: 0; }
.home-article-grid :deep(.tilt-card) { border-radius: var(--radius-sm); }
.home-article-grid :deep(.tilt-card__glare) { border-radius: var(--radius-sm); }

.home-video-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  column-gap: clamp(20px, 2.5vw, 32px);
  row-gap: clamp(24px, 3vw, 36px);
}

@media (max-width: 1100px) {
  .home-video-grid { grid-template-columns: repeat(2, minmax(0, 1fr)); }
}

.home-tags {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(160px, 1fr));
  gap: var(--space-3);
}

.home-tag {
  display: grid;
  gap: var(--space-3);
  padding: var(--space-4);
  text-decoration: none;
  transition: all var(--dur) var(--ease);
  position: relative;
  z-index: 1;
}

.home-tag:hover { transform: translateY(-2px); }
.home-tag:hover .home-tag__arrow { transform: translate(2px, -2px); }

.home-tag__index { color: var(--text-muted); font-family: var(--mono); font-size: var(--text-2xs); }

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

.home-tag__arrow { transition: transform var(--dur) var(--ease); color: var(--verm); }

.home-tag:hover .home-tag__index,
.home-tag:hover .home-tag__foot { color: var(--ink); }

.home-questions { display: grid; }

.home-question {
  display: grid;
  grid-template-columns: 44px minmax(0, 1fr) auto;
  align-items: center;
  gap: var(--space-4);
  padding-block: var(--space-4);
  border-top: var(--border);
  transition: all var(--dur) var(--ease);
}

.home-question:hover { padding-left: var(--space-2); background: var(--surface); }
.home-question:first-child { border-top: 0; }

.home-question__index { color: var(--text-muted); font-family: var(--mono); font-size: var(--text-2xs); }

.home-question__main { display: grid; gap: var(--space-2); min-width: 0; }

.home-question__title {
  color: var(--ink);
  font-family: var(--serif);
  font-size: var(--text-lg);
  font-weight: var(--weight-semibold);
  line-height: var(--leading-snug);
  text-decoration: none;
  transition: color var(--dur) var(--ease);
}

.home-question__title:hover { color: var(--verm-deep); }

.home-question__meta {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-2) var(--space-4);
  color: var(--text-muted);
  font-family: var(--mono);
  font-size: var(--text-2xs);
}

.home-question__state {
  padding: 4px 10px;
  border: 1px solid var(--line-strong);
  color: var(--text-muted);
  font-family: var(--mono);
  font-size: var(--text-2xs);
  white-space: nowrap;
  transition: all var(--dur) var(--ease);
}

.home-question__state.is-solved {
  border-color: var(--acid);
  background: var(--acid);
  color: var(--ink);
  box-shadow: 0 0 10px rgb(201 255 46 / 30%);
}

@media (max-width: 900px) {
  .home-hero__content { grid-template-columns: 1fr; }
  .home-hero__graphic { display: none; }
  .home-stats { justify-content: flex-start; }
}

@media (max-width: 720px) {
  .home-article-grid,
  .home-video-grid { grid-template-columns: 1fr; }

  .home-question {
    grid-template-columns: 32px minmax(0, 1fr);
    align-items: start;
  }

  .home-question__state { grid-column: 2; justify-self: start; }
  .home-stats { flex-wrap: wrap; gap: var(--space-3); }
}

@media (max-width: 640px) {
  .home-hero { padding: var(--space-5); box-shadow: var(--shadow-hard-sm); min-height: auto; }
  .home-search { flex-direction: column; }
  .home-search :deep(.wh-button) { width: 100%; }
  .home-tags { grid-template-columns: repeat(2, minmax(0, 1fr)); }
}
</style>
