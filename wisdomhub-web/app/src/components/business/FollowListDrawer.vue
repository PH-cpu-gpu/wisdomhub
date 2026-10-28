<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import { RouterLink } from 'vue-router'

import {
  follow as followUser,
  isFollowing as fetchFollowing,
  listFans,
  listFollowing,
  unfollow as unfollowUser,
} from '@/api/modules/user'
import WhAvatar from '@/components/base/WhAvatar.vue'
import WhButton from '@/components/base/WhButton.vue'
import WhDrawer from '@/components/base/WhDrawer.vue'
import WhPagination from '@/components/base/WhPagination.vue'
import WhTabs from '@/components/base/WhTabs.vue'
import { toast } from '@/components/base/toast'
import { usePagination } from '@/composables/usePagination'
import { useAuthStore } from '@/stores'
import type { ID } from '@/types/common'
import type { UserBriefVO } from '@/types/user'

type SocialTab = 'following' | 'fans'

const props = withDefaults(
  defineProps<{
    modelValue: boolean
    userId: ID
    initialTab?: SocialTab
    followCount?: number
    fansCount?: number
  }>(),
  {
    initialTab: 'following',
    followCount: 0,
    fansCount: 0,
  },
)

const emit = defineEmits<{
  'update:modelValue': [value: boolean]
  followChanged: [payload: { following: boolean; targetId: ID }]
}>()

const auth = useAuthStore()
const activeTab = ref<SocialTab>(props.initialTab)
const open = computed({
  get: () => props.modelValue,
  set: (value: boolean) => emit('update:modelValue', value),
})

const { page, pageSize, total, setTotal, reset } = usePagination({ pageSize: 10 })

const users = ref<UserBriefVO[]>([])
const loading = ref(false)
const error = ref('')
const followingMap = reactive<Record<ID, boolean>>({})
const checkingMap = reactive<Record<ID, boolean>>({})
const pendingMap = reactive<Record<ID, boolean>>({})

let listRequest = 0

const tabItems = computed(() => [
  { label: '关注', value: 'following', badge: props.followCount },
  { label: '粉丝', value: 'fans', badge: props.fansCount },
])

const tabModel = computed({
  get: () => activeTab.value,
  set: (value: string) => {
    const next: SocialTab = value === 'fans' ? 'fans' : 'following'
    if (next === activeTab.value) return
    activeTab.value = next
    reset()
    setTotal(0)
  },
})

const emptyText = computed(() => {
  const self = props.userId === auth.userId
  if (activeTab.value === 'following') {
    return self ? '你还没有关注任何人。' : 'TA 还没有关注任何人。'
  }
  return self ? '还没有粉丝。' : 'TA 还没有粉丝。'
})

function clearRecord(record: Record<ID, boolean>): void {
  for (const key of Object.keys(record)) delete record[key]
}

function canFollow(user: UserBriefVO): boolean {
  return auth.isLoggedIn() && user.userId !== auth.userId
}

async function loadFollowStates(items: UserBriefVO[], request: number): Promise<void> {
  clearRecord(followingMap)
  clearRecord(checkingMap)

  if (!auth.isLoggedIn()) return

  // 自己的关注列表：列表本身就是「我关注的人」，无需逐行请求
  if (activeTab.value === 'following' && props.userId === auth.userId) {
    items.forEach((user) => {
      followingMap[user.userId] = true
    })
    return
  }

  const targets = items.filter((user) => user.userId !== auth.userId)
  targets.forEach((user) => {
    checkingMap[user.userId] = true
  })

  await Promise.all(
    targets.map(async (user) => {
      try {
        const value = await fetchFollowing(user.userId, { skipErrorToast: true })
        if (request === listRequest) followingMap[user.userId] = value
      } catch {
        if (request === listRequest) followingMap[user.userId] = false
      } finally {
        if (request === listRequest) checkingMap[user.userId] = false
      }
    }),
  )
}

async function load(): Promise<void> {
  if (!props.modelValue || !props.userId) return

  const request = (listRequest += 1)
  loading.value = true
  error.value = ''
  clearRecord(followingMap)
  clearRecord(checkingMap)
  clearRecord(pendingMap)

  try {
    const params = { userId: props.userId, page: page.value, pageSize: pageSize.value }
    const result =
      activeTab.value === 'following'
        ? await listFollowing(params, { skipErrorToast: true })
        : await listFans(params, { skipErrorToast: true })

    if (request !== listRequest) return

    users.value = result.items
    setTotal(result.total)
    void loadFollowStates(result.items, request)
  } catch {
    if (request !== listRequest) return
    users.value = []
    setTotal(0)
    error.value = '列表加载失败，请稍后重试。'
  } finally {
    if (request === listRequest) loading.value = false
  }
}

async function toggleFollow(user: UserBriefVO): Promise<void> {
  if (!canFollow(user) || checkingMap[user.userId] || pendingMap[user.userId]) return

  pendingMap[user.userId] = true

  try {
    const next = !followingMap[user.userId]

    if (next) {
      await followUser(user.userId)
      followingMap[user.userId] = true
      toast.success('已关注')
    } else {
      await unfollowUser(user.userId)
      followingMap[user.userId] = false
      toast.info('已取消关注')
    }

    emit('followChanged', { following: next, targetId: user.userId })

    // 在自己的关注列表中取关后，行应从列表消失
    if (!next && activeTab.value === 'following' && props.userId === auth.userId) {
      users.value = users.value.filter((item) => item.userId !== user.userId)
      setTotal(Math.max(0, total.value - 1))
      if (!users.value.length && page.value > 1) page.value -= 1
    }
  } catch {
    // 请求层已 toast
  } finally {
    pendingMap[user.userId] = false
  }
}

function close(): void {
  emit('update:modelValue', false)
}

watch(
  () => props.modelValue,
  (value) => {
    if (!value) return
    activeTab.value = props.initialTab
    reset()
    setTotal(0)
  },
)

watch(
  () => [props.modelValue, activeTab.value, page.value, pageSize.value] as const,
  ([value]) => {
    if (value) void load()
  },
  { immediate: true },
)
</script>

<template>
  <WhDrawer v-model="open" title="关注与粉丝" width="min(460px, 100vw)">
    <div class="follow-drawer">
      <WhTabs v-model="tabModel" :items="tabItems" />

      <div v-if="loading" class="follow-state" aria-live="polite">正在加载…</div>

      <div v-else-if="error" class="follow-state follow-state--error" role="alert">
        <p>{{ error }}</p>
        <WhButton size="sm" variant="ghost" @click="load">重试</WhButton>
      </div>

      <div v-else-if="!users.length" class="follow-state">{{ emptyText }}</div>

      <div v-else class="follow-list">
        <div v-for="user in users" :key="user.userId" class="follow-row">
          <RouterLink
            class="follow-row__main"
            :to="{ name: 'user-profile', params: { userId: user.userId } }"
            @click="close"
          >
            <WhAvatar
              :src="user.avatar ?? undefined"
              :name="user.username"
              size="sm"
              shape="circle"
            />
            <span class="follow-row__text">
              <strong>{{ user.username }}</strong>
              <small>{{ user.bio || '这个人还没有写简介。' }}</small>
            </span>
          </RouterLink>

          <WhButton
            v-if="canFollow(user)"
            size="sm"
            :variant="followingMap[user.userId] ? 'ghost' : 'primary'"
            :loading="Boolean(checkingMap[user.userId] || pendingMap[user.userId])"
            :disabled="Boolean(checkingMap[user.userId] || pendingMap[user.userId])"
            @click="toggleFollow(user)"
          >
            {{ followingMap[user.userId] ? '已关注' : '关注' }}
          </WhButton>
        </div>
      </div>

      <div v-if="total > 0" class="follow-pagination">
        <WhPagination
          v-model:page="page"
          v-model:page-size="pageSize"
          :total="total"
          show-size-changer
        />
      </div>
    </div>
  </WhDrawer>
</template>

<style scoped>
.follow-drawer {
  display: grid;
  gap: var(--space-4);
}

.follow-state {
  display: grid;
  justify-items: center;
  gap: var(--space-3);
  padding: var(--space-8) var(--space-4);
  border: 1px dashed var(--line-strong);
  color: var(--text-muted);
  font-size: var(--text-sm);
  text-align: center;
}

.follow-state--error {
  border-color: var(--verm);
  color: var(--verm-deep);
}

.follow-list {
  display: grid;
}

.follow-row {
  display: flex;
  align-items: center;
  gap: var(--space-3);
  padding-block: var(--space-3);
  border-top: var(--border);
}

.follow-row:first-child {
  border-top: 0;
}

.follow-row__main {
  display: flex;
  flex: 1;
  align-items: center;
  gap: var(--space-2);
  min-width: 0;
}

.follow-row__text {
  display: grid;
  gap: 2px;
  min-width: 0;
}

.follow-row__text strong {
  color: var(--ink);
  font-size: var(--text-sm);
  font-weight: var(--weight-semibold);
}

.follow-row__text small {
  overflow: hidden;
  color: var(--text-muted);
  font-size: var(--text-xs);
  text-overflow: ellipsis;
  white-space: nowrap;
}

.follow-row__text strong,
.follow-row__text small {
  display: block;
}

.follow-pagination {
  padding-top: var(--space-4);
  border-top: var(--border);
}
</style>
