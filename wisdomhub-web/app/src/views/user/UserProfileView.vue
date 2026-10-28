<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { RouterLink, useRoute, useRouter } from 'vue-router'

import { isApiError } from '@/api/error'
import { list as listArticles } from '@/api/modules/article'
import {
  follow as followUser,
  getProfile as fetchProfile,
  isFollowing as fetchFollowing,
  unfollow as unfollowUser,
} from '@/api/modules/user'
import ArticleCard from '@/components/business/ArticleCard.vue'
import FollowListDrawer from '@/components/business/FollowListDrawer.vue'
import WhAvatar from '@/components/base/WhAvatar.vue'
import WhButton from '@/components/base/WhButton.vue'
import WhPagination from '@/components/base/WhPagination.vue'
import { toast } from '@/components/base/toast'
import PageContainer from '@/components/layout/PageContainer.vue'
import { useArticleAuthors } from '@/composables/useArticleAuthors'
import { usePagination } from '@/composables/usePagination'
import { useSeo } from '@/composables/useSeo'
import { useAuthStore } from '@/stores'
import type { ArticleListItemVO } from '@/types/content'
import type { UserProfileVO } from '@/types/user'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()

const userId = computed(() => String(route.params.userId ?? ''))
const isSelf = computed(() => Boolean(auth.userId) && auth.userId === userId.value)

const articlesSection = ref<HTMLElement | null>(null)
const followDrawerOpen = ref(false)
const followDrawerTab = ref<'following' | 'fans'>('following')

const { page, pageSize, total, query, setTotal, reset } = usePagination({ pageSize: 10 })

const profile = ref<UserProfileVO | null>(null)
const profileLoading = ref(true)
const profileError = ref('')
const notFound = ref(false)

const articles = ref<ArticleListItemVO[]>([])
const { authors } = useArticleAuthors(articles)
const articlesLoading = ref(false)
const articlesError = ref('')

const following = ref(false)
const followChecking = ref(false)
const followToggling = ref(false)
const followButtonText = computed(() => {
  if (followChecking.value || followToggling.value) return '处理中…'
  return following.value ? '已关注' : '关注'
})

let profileRequest = 0
let articleRequest = 0

async function loadFollowState(): Promise<void> {
  if (!auth.isLoggedIn() || isSelf.value) return

  const id = userId.value
  followChecking.value = true

  try {
    const value = await fetchFollowing(id, { skipErrorToast: true })
    if (userId.value === id) following.value = value
  } catch {
    if (userId.value === id) following.value = false
  } finally {
    if (userId.value === id) followChecking.value = false
  }
}

async function loadProfile(): Promise<void> {
  const id = userId.value
  const request = (profileRequest += 1)

  profileLoading.value = true
  profileError.value = ''
  notFound.value = false
  profile.value = null
  following.value = false
  followChecking.value = false

  try {
    const data = await fetchProfile(id, { skipErrorToast: true })
    if (request !== profileRequest) return

    profile.value = data
    void loadFollowState()
  } catch (error) {
    if (request !== profileRequest) return

    if (isApiError(error)) {
      profileError.value = error.message
      notFound.value = error.code === 404
    } else {
      profileError.value = '主页加载失败，请稍后重试。'
    }
  } finally {
    if (request === profileRequest) profileLoading.value = false
  }
}

async function loadArticles(): Promise<void> {
  const id = userId.value
  if (!id) return

  const request = (articleRequest += 1)
  articlesLoading.value = true
  articlesError.value = ''

  try {
    const result = await listArticles({ ...query.value, authorId: id }, { skipErrorToast: true })
    if (request !== articleRequest) return

    articles.value = result.items
    setTotal(result.total)
  } catch (error) {
    if (request !== articleRequest) return

    articles.value = []
    setTotal(0)
    articlesError.value = isApiError(error) ? error.message : '文章加载失败，请稍后重试。'
  } finally {
    if (request === articleRequest) articlesLoading.value = false
  }
}

async function toggleFollow(): Promise<void> {
  if (followChecking.value || followToggling.value) return

  if (!auth.isLoggedIn()) {
    await router.push({ name: 'login', query: { redirect: route.fullPath } })
    return
  }

  if (isSelf.value || !profile.value) return

  followToggling.value = true

  try {
    if (following.value) {
      await unfollowUser(userId.value)
      following.value = false
      profile.value = {
        ...profile.value,
        fansCount: Math.max(0, profile.value.fansCount - 1),
      }
      toast.info('已取消关注')
    } else {
      await followUser(userId.value)
      following.value = true
      profile.value = { ...profile.value, fansCount: profile.value.fansCount + 1 }
      toast.success('已关注')
    }
  } catch {
    // 请求层已 toast
  } finally {
    followToggling.value = false
  }
}

function openFollowList(tab: 'following' | 'fans'): void {
  if (!auth.isLoggedIn()) return
  followDrawerTab.value = tab
  followDrawerOpen.value = true
}

function scrollToArticles(): void {
  articlesSection.value?.scrollIntoView({ behavior: 'smooth', block: 'start' })
}

function onFollowChanged(payload: { following: boolean; targetId: string }): void {
  if (!isSelf.value || !profile.value) return
  const delta = payload.following ? 1 : -1
  profile.value = {
    ...profile.value,
    followCount: Math.max(0, profile.value.followCount + delta),
  }
}

watch(
  userId,
  () => {
    reset()
    setTotal(0)
    profile.value = null
    articles.value = []
    articlesError.value = ''
    following.value = false
    followChecking.value = false
    notFound.value = false
    void loadProfile()
  },
  { immediate: true },
)

watch(
  [userId, page, pageSize],
  () => {
    void loadArticles()
  },
  { immediate: true },
)

useSeo({
  title: computed(() => (profile.value ? `${profile.value.username} 的主页` : '个人主页')),
  description: computed(() => profile.value?.bio || 'WisdomHub 用户主页。'),
})
</script>

<template>
  <PageContainer tag="section">
    <div class="profile-page">
      <div v-if="profileLoading" class="profile-state" aria-live="polite">
        <span class="profile-state__mark">…</span>
        <p>正在加载个人主页</p>
      </div>

      <div v-else-if="notFound" class="profile-state profile-state--missing">
        <p class="profile-eyebrow">[ 404 / USER ]</p>
        <h1>用户不存在</h1>
        <p>这个主页可能已被删除，或者链接有误。</p>
        <RouterLink class="profile-action" :to="{ name: 'home' }">回到首页</RouterLink>
      </div>

      <div v-else-if="profileError" class="profile-state profile-state--error" role="alert">
        <span class="profile-state__mark">!</span>
        <p>{{ profileError }}</p>
        <WhButton size="sm" variant="ghost" @click="loadProfile">重试</WhButton>
      </div>

      <template v-else-if="profile">
        <section class="profile-hero">
          <div class="profile-hero__main">
            <div class="profile-avatar">
              <WhAvatar
                :src="profile.avatar ?? undefined"
                :name="profile.username"
                size="xl"
                shape="circle"
              />
            </div>
            <div class="profile-identity">
              <p class="profile-eyebrow">[ USER / PROFILE ]</p>
              <h1>{{ profile.username }}</h1>
              <p class="profile-bio">{{ profile.bio || '这个人还没有写简介。' }}</p>
              <p class="profile-id">ID {{ profile.userId }}</p>
            </div>
          </div>

          <div class="profile-hero__side">
            <div class="profile-stats">
              <button class="profile-stat" type="button" @click="scrollToArticles">
                <span class="profile-stat__label">文章</span>
                <span class="profile-stat__value">{{ profile.articleCount }}</span>
              </button>
              <button
                class="profile-stat"
                type="button"
                :disabled="!auth.isLoggedIn()"
                :title="auth.isLoggedIn() ? '查看粉丝列表' : '登录后可查看'"
                @click="openFollowList('fans')"
              >
                <span class="profile-stat__label">粉丝</span>
                <span class="profile-stat__value">{{ profile.fansCount }}</span>
              </button>
              <button
                class="profile-stat"
                type="button"
                :disabled="!auth.isLoggedIn()"
                :title="auth.isLoggedIn() ? '查看关注列表' : '登录后可查看'"
                @click="openFollowList('following')"
              >
                <span class="profile-stat__label">关注</span>
                <span class="profile-stat__value">{{ profile.followCount }}</span>
              </button>
            </div>

            <div class="profile-actions">
              <template v-if="isSelf">
                <RouterLink class="profile-action" :to="{ name: 'me-settings' }">
                  编辑资料
                </RouterLink>
                <RouterLink
                  class="profile-action profile-action--ghost"
                  :to="{ name: 'me-columns' }"
                >
                  我的专栏
                </RouterLink>
              </template>
              <button
                v-else
                class="profile-follow"
                :class="{ 'is-following': following }"
                type="button"
                :disabled="followChecking || followToggling"
                :aria-busy="followChecking || followToggling"
                @click="toggleFollow"
              >
                {{ followButtonText }}
              </button>
            </div>
          </div>
        </section>

        <section ref="articlesSection" class="profile-articles">
          <header class="profile-articles__head">
            <div>
              <p class="profile-eyebrow">[ ARTICLES ]</p>
              <h2>文章</h2>
            </div>
            <span class="profile-articles__count">{{ total }} 篇</span>
          </header>

          <div
            v-if="articlesLoading"
            class="profile-state profile-state--inline"
            aria-live="polite"
          >
            正在加载文章…
          </div>

          <div
            v-else-if="articlesError"
            class="profile-state profile-state--inline profile-state--error"
            role="alert"
          >
            <p>{{ articlesError }}</p>
            <WhButton size="sm" variant="ghost" @click="loadArticles">重试</WhButton>
          </div>

          <div v-else-if="!articles.length" class="profile-empty">
            <p>{{ isSelf ? '你还没有发布文章。' : '这位作者还没有发布文章。' }}</p>
            <RouterLink v-if="isSelf" class="profile-action" :to="{ name: 'editor-new' }">
              写第一篇
            </RouterLink>
          </div>

          <div v-else class="profile-article-list">
            <ArticleCard
              v-for="article in articles"
              :key="article.articleId"
              :article="article"
              :author="authors[article.authorId]"
            />
          </div>

          <div v-if="total > 0" class="profile-pagination">
            <WhPagination
              v-model:page="page"
              v-model:page-size="pageSize"
              :total="total"
              show-size-changer
            />
          </div>
        </section>

        <FollowListDrawer
          v-model="followDrawerOpen"
          :user-id="profile.userId"
          :initial-tab="followDrawerTab"
          :follow-count="profile.followCount"
          :fans-count="profile.fansCount"
          @follow-changed="onFollowChanged"
        />
      </template>
    </div>
  </PageContainer>
</template>

<style scoped>
.profile-page {
  display: grid;
  gap: clamp(28px, 4vw, 48px);
  max-width: 1080px;
  margin: 0 auto;
}

.profile-state {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: var(--space-3);
  padding: var(--space-6);
  border: var(--border);
  background: var(--surface);
  color: var(--text-muted);
  font-size: var(--text-sm);
}

.profile-state__mark {
  display: inline-grid;
  place-items: center;
  width: 28px;
  height: 28px;
  border: 1px solid var(--ink);
  background: var(--acid);
  color: var(--ink);
  font-family: var(--mono);
  font-size: var(--text-sm);
}

.profile-state--inline {
  padding: var(--space-4);
  border-style: dashed;
}

.profile-state--error {
  border-color: var(--verm);
  color: var(--verm-deep);
}

.profile-state--error .profile-state__mark {
  background: var(--verm);
  color: var(--bone);
}

.profile-state--missing {
  display: grid;
  justify-items: start;
  gap: var(--space-3);
  padding-block: clamp(32px, 8vw, 72px);
}

.profile-state--missing h1 {
  font-family: var(--serif);
  font-size: var(--text-3xl);
  font-weight: var(--weight-black);
}

.profile-eyebrow,
.profile-stat__label,
.profile-id {
  font-family: var(--mono);
  letter-spacing: var(--tracking-wide);
  text-transform: uppercase;
}

.profile-eyebrow {
  color: var(--verm-deep);
  font-size: var(--text-2xs);
  letter-spacing: var(--tracking-wider);
}

.profile-hero {
  position: relative;
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  gap: clamp(24px, 4vw, 48px);
  padding: clamp(28px, 4vw, 48px);
  overflow: hidden;
  background: var(--ink);
  color: var(--bone);
  box-shadow: var(--shadow-hard);
}

.profile-hero::before {
  content: '';
  position: absolute;
  inset: 0;
  background-image:
    linear-gradient(var(--line-inverse) 1px, transparent 1px),
    linear-gradient(90deg, var(--line-inverse) 1px, transparent 1px);
  background-size: 48px 48px;
  opacity: 0.22;
  pointer-events: none;
}

.profile-hero > * {
  position: relative;
  z-index: 1;
}

.profile-hero__main {
  display: flex;
  align-items: flex-start;
  gap: clamp(18px, 3vw, 28px);
}

.profile-avatar :deep(.wh-avatar) {
  border-color: var(--acid);
}

.profile-identity {
  display: grid;
  gap: var(--space-2);
}

.profile-identity h1 {
  color: var(--bone);
  font-family: var(--serif);
  font-size: var(--text-3xl);
  font-weight: var(--weight-black);
  line-height: var(--leading-tight);
  letter-spacing: var(--tracking-tight);
}

.profile-bio {
  max-width: 42ch;
  color: color-mix(in srgb, var(--bone) 76%, transparent);
  font-size: var(--text-sm);
  line-height: var(--leading-relaxed);
}

.profile-id {
  color: color-mix(in srgb, var(--bone) 48%, transparent);
  font-size: var(--text-2xs);
}

.profile-hero__side {
  display: grid;
  align-content: space-between;
  gap: var(--space-6);
  min-width: 220px;
}

.profile-stats {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: var(--space-3);
  padding-bottom: var(--space-4);
  border-bottom: 1px solid var(--line-inverse);
}

.profile-stat {
  display: grid;
  justify-items: start;
  gap: 2px;
  padding: 0;
  border: 0;
  background: transparent;
  color: inherit;
  font: inherit;
  text-align: left;
}

.profile-stat:not(:disabled) {
  cursor: pointer;
}

.profile-stat:disabled {
  cursor: default;
}

.profile-stat__label {
  color: color-mix(in srgb, var(--bone) 50%, transparent);
  font-size: var(--text-2xs);
}

.profile-stat__value {
  color: var(--acid);
  font-family: var(--serif);
  font-size: var(--text-2xl);
  font-weight: var(--weight-black);
  line-height: var(--leading-none);
}

.profile-stat:not(:disabled):hover .profile-stat__value {
  text-decoration: underline;
  text-underline-offset: 4px;
}

.profile-actions {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-2);
}

.profile-action,
.profile-follow {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-height: 40px;
  padding-inline: 16px;
  border: 1px solid var(--acid);
  background: var(--acid);
  color: var(--ink);
  font-family: var(--sans);
  font-size: var(--text-sm);
  font-weight: var(--weight-semibold);
  white-space: nowrap;
  transition:
    background var(--dur) var(--ease),
    color var(--dur) var(--ease);
}

.profile-action:hover,
.profile-follow:hover:not(:disabled) {
  background: transparent;
  color: var(--acid);
}

.profile-action--ghost,
.profile-follow.is-following {
  background: transparent;
  color: var(--acid);
}

.profile-action--ghost:hover,
.profile-follow.is-following:hover:not(:disabled) {
  background: var(--acid);
  color: var(--ink);
}

.profile-follow:disabled {
  opacity: 0.55;
  cursor: wait;
}

.profile-articles {
  display: grid;
  gap: var(--space-2);
  scroll-margin-top: 96px;
}

.profile-articles__head {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: var(--space-3);
  padding-bottom: var(--space-3);
  border-bottom: 1px solid var(--ink);
}

.profile-articles__head h2 {
  margin-top: var(--space-1);
  font-family: var(--serif);
  font-size: var(--text-2xl);
  font-weight: var(--weight-black);
  letter-spacing: var(--tracking-tight);
}

.profile-articles__count {
  color: var(--text-muted);
  font-family: var(--mono);
  font-size: var(--text-2xs);
}

.profile-empty {
  display: grid;
  justify-items: start;
  gap: var(--space-4);
  padding: var(--space-8) 0;
  color: var(--text-muted);
  font-size: var(--text-sm);
}

.profile-article-list {
  display: grid;
}

.profile-pagination {
  padding-top: var(--space-5);
}

@media (max-width: 860px) {
  .profile-hero {
    grid-template-columns: 1fr;
  }

  .profile-hero__side {
    min-width: 0;
  }

  .profile-hero__main {
    flex-direction: column;
  }
}

@media (max-width: 640px) {
  .profile-hero {
    box-shadow: var(--shadow-hard-sm);
  }

  .profile-stat__value {
    font-size: var(--text-xl);
  }

  .profile-actions {
    display: grid;
    grid-template-columns: 1fr 1fr;
  }

  .profile-follow {
    grid-column: 1 / -1;
  }

  .profile-articles__head {
    align-items: flex-start;
  }
}
</style>
