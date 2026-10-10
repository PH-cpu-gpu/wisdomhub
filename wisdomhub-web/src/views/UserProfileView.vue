<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { RouterLink, useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import 'element-plus/es/components/message/style/css'

import { ApiError } from '@/api/http'
import { ApiCode } from '@/api/types'
import {
  followUser,
  getUserProfile,
  isFollowed,
  listFans,
  listFollowing,
  unfollowUser,
  type UserBrief,
  type UserProfile,
} from '@/api/user'
import ArticleList from '@/components/article/ArticleList.vue'
import UserAvatar from '@/components/UserAvatar.vue'
import { useUserStore } from '@/stores/user'

/**
 * F1.6 个人主页：资料 + 文章列表 + 关注/粉丝 + 关注按钮。
 *
 * 两组数据的**可见性不同**（B1.11 / B1.12），这一页必须分开处理：
 *   - 资料聚合在网关的**免登录名单**里 → 游客也能看（含文章数/粉丝数/关注数）
 *   - 关注列表、粉丝列表、是否已关注都要登录 → 游客拿这些会 401。
 *     所以这些请求一律 **silent + 降级**（显示「登录后可见」），
 *     绝不能让它走 401 那套「清 token + 跳登录」—— 游客逛主页被弹「登录已过期」很莫名其妙。
 */
const route = useRoute()
const router = useRouter()
const user = useUserStore()

const profile = ref<UserProfile | null>(null)
const loading = ref(true)
const notFound = ref(false)

const following = ref(false)
const acting = ref(false)

const userId = computed(() => String(route.params.id ?? ''))
const isSelf = computed(() => user.userId !== null && user.userId === userId.value)

type Tab = 'articles' | 'following' | 'fans'
const tab = ref<Tab>('articles')

const people = ref<UserBrief[]>([])
const peopleTotal = ref(0)
const peoplePage = ref(1)
const peopleLoading = ref(false)
/** 拿不到时的原因：没登录 vs 别的错误 —— 文案不一样 */
const peopleIssue = ref('')

const PEOPLE_PAGE_SIZE = 10

// ---------------------------------------------------------------------------
// 资料
// ---------------------------------------------------------------------------

async function loadProfile() {
  loading.value = true
  notFound.value = false
  profile.value = null
  following.value = false

  try {
    profile.value = await getUserProfile(userId.value)
    // 关注状态只有登录且不是自己时才问；失败就当「没关注」显示，点了以后端为准
    if (user.isLoggedIn && !isSelf.value) {
      await loadFollowState()
    }
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

async function loadFollowState() {
  try {
    following.value = await isFollowed(userId.value, { silent: true })
  } catch {
    following.value = false
  }
}

async function onToggleFollow() {
  if (!profile.value) return

  if (!user.isLoggedIn) {
    ElMessage.info('登录后才能关注')
    void router.push({ path: '/login', query: { redirect: route.fullPath } })
    return
  }

  acting.value = true
  try {
    if (following.value) {
      await unfollowUser(profile.value.userId)
      following.value = false
      // 本地乐观更新粉丝数：接口只返回 void，而 fansCount 的读源是 Redis（后台每 2 分钟对账）
      profile.value.fansCount = Math.max(0, profile.value.fansCount - 1)
    } else {
      await followUser(profile.value.userId)
      following.value = true
      profile.value.fansCount += 1
    }
  } catch (e) {
    ElMessage.error(e instanceof ApiError ? e.message : '操作失败')
  } finally {
    acting.value = false
  }
}

// ---------------------------------------------------------------------------
// 关注 / 粉丝列表
// ---------------------------------------------------------------------------

async function loadPeople() {
  if (tab.value === 'articles') return

  peopleLoading.value = true
  peopleIssue.value = ''
  try {
    // silent：401 要在这里降级成提示，而不是触发全局的「清 token + 跳登录」
    const result =
      tab.value === 'fans'
        ? await listFans(userId.value, peoplePage.value, PEOPLE_PAGE_SIZE, { silent: true })
        : await listFollowing(userId.value, peoplePage.value, PEOPLE_PAGE_SIZE, { silent: true })
    people.value = result.items
    peopleTotal.value = result.total
  } catch (e) {
    people.value = []
    peopleTotal.value = 0
    if (e instanceof ApiError && e.code === ApiCode.UNAUTHORIZED) {
      peopleIssue.value = '登录后才能查看'
    } else {
      peopleIssue.value = e instanceof ApiError ? e.message : '加载失败'
    }
  } finally {
    peopleLoading.value = false
  }
}

function onTabChange(next: string | number | boolean | undefined) {
  peoplePage.value = 1
  if (next !== 'articles') void loadPeople()
}

watch(peoplePage, () => void loadPeople())

watch(
  () => route.params.id,
  () => {
    tab.value = 'articles'
    peoplePage.value = 1
    people.value = []
    peopleTotal.value = 0
    void loadProfile()
  },
)

onMounted(() => void loadProfile())
</script>

<template>
  <div class="prof">
    <el-skeleton v-if="loading" :rows="6" animated />

    <el-empty v-else-if="notFound" description="用户不存在">
      <el-button type="primary" @click="router.push('/')">回首页</el-button>
    </el-empty>

    <template v-else-if="profile">
      <header class="prof__head">
        <UserAvatar :src="profile.avatar" :name="profile.username" :size="72" />

        <div class="prof__info">
          <h1 class="prof__name">{{ profile.username }}</h1>
          <p v-if="profile.bio" class="prof__bio">{{ profile.bio }}</p>

          <dl class="prof__stats wh-mono">
            <div><dt>文章</dt><dd>{{ profile.articleCount }}</dd></div>
            <div><dt>粉丝</dt><dd>{{ profile.fansCount }}</dd></div>
            <div><dt>关注</dt><dd>{{ profile.followCount }}</dd></div>
          </dl>
        </div>

        <!-- 自己的主页不给关注按钮（后端也会把自己的关注挡掉） -->
        <el-button
          v-if="!isSelf"
          class="prof__follow"
          :type="following ? 'default' : 'primary'"
          :loading="acting"
          @click="onToggleFollow"
        >
          {{ following ? '已关注' : '关注' }}
        </el-button>
      </header>

      <div class="prof__tabs">
        <el-radio-group v-model="tab" size="small" @change="onTabChange">
          <el-radio-button value="articles">文章</el-radio-button>
          <el-radio-button value="following">关注</el-radio-button>
          <el-radio-button value="fans">粉丝</el-radio-button>
        </el-radio-group>
      </div>

      <ArticleList
        v-if="tab === 'articles'"
        :author-id="profile.userId"
        hide-author
        :empty-text="`${profile.username} 还没有已发布的文章`"
      />

      <template v-else>
        <el-skeleton v-if="peopleLoading" :rows="4" animated />

        <el-empty v-else-if="peopleIssue" :description="peopleIssue">
          <el-button
            v-if="peopleIssue === '登录后才能查看'"
            type="primary"
            @click="router.push('/login')"
          >
            去登录
          </el-button>
        </el-empty>

        <el-empty
          v-else-if="people.length === 0"
          :description="tab === 'fans' ? '还没有粉丝' : '还没有关注任何人'"
        />

        <ul v-else class="prof__people">
          <li v-for="person in people" :key="person.userId" class="prof__person">
            <RouterLink class="prof__person-name" :to="`/user/${person.userId}`">
              {{ person.username }}
            </RouterLink>
            <span v-if="person.bio" class="prof__person-bio">{{ person.bio }}</span>
          </li>
        </ul>

        <div v-if="peopleTotal > PEOPLE_PAGE_SIZE" class="prof__pager">
          <el-pagination
            v-model:current-page="peoplePage"
            :page-size="PEOPLE_PAGE_SIZE"
            :total="peopleTotal"
            layout="prev, pager, next"
            background
          />
        </div>
      </template>
    </template>
  </div>
</template>

<style scoped>
.prof {
  padding-block: var(--wh-space-4);
}

.prof__head {
  display: flex;
  flex-wrap: wrap;
  gap: var(--wh-space-5);
  align-items: flex-start;

  padding-bottom: var(--wh-space-5);
  border-bottom: var(--wh-border-width) solid var(--wh-border-strong);
}

.prof__info {
  flex: 1 1 12rem;
  min-width: 0;
}

.prof__name {
  font-size: var(--wh-text-xl);
  font-weight: var(--wh-weight-semibold);
}

.prof__bio {
  margin-top: var(--wh-space-2);

  font-size: var(--wh-text-sm);
  line-height: var(--wh-leading-normal);
  color: var(--wh-text-2);
}

.prof__stats {
  display: flex;
  gap: var(--wh-space-5);
  margin-top: var(--wh-space-3);

  font-size: var(--wh-text-sm);
}

.prof__stats div {
  display: flex;
  gap: var(--wh-space-1);
  align-items: baseline;
}

.prof__stats dt {
  color: var(--wh-text-3);
}

.prof__stats dd {
  color: var(--wh-text-1);
}

.prof__follow {
  align-self: center;
}

.prof__tabs {
  margin-top: var(--wh-space-4);
  margin-bottom: var(--wh-space-3);
}

.prof__people {
  display: flex;
  flex-direction: column;
  gap: var(--wh-space-3);
  padding-top: var(--wh-space-2);
  list-style: none;
}

.prof__person {
  display: flex;
  gap: var(--wh-space-3);
  align-items: baseline;

  padding-bottom: var(--wh-space-3);
  border-bottom: var(--wh-border-width) solid var(--wh-border);
}

.prof__person-name {
  flex: none;

  font-size: var(--wh-text-base);
  color: var(--wh-text-1);
  text-decoration: none;
}

.prof__person-name:hover {
  color: var(--wh-accent);
}

.prof__person-bio {
  overflow: hidden;

  font-size: var(--wh-text-xs);
  color: var(--wh-text-3);
  text-overflow: ellipsis;
  white-space: nowrap;
}

.prof__pager {
  display: flex;
  justify-content: center;
  padding-top: var(--wh-space-5);
}
</style>
