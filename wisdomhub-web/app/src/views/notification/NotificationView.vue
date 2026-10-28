<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'

import { isApiError } from '@/api/error'
import WhButton from '@/components/base/WhButton.vue'
import WhPagination from '@/components/base/WhPagination.vue'
import { toast } from '@/components/base/toast'
import PageContainer from '@/components/layout/PageContainer.vue'
import { useAuthorBrief } from '@/composables/useAuthorBrief'
import { useSeo } from '@/composables/useSeo'
import { useNotificationStore } from '@/stores'
import type { NotificationVO } from '@/types/notification'
import { formatDateTime } from '@/utils'

const TYPE_LABELS: Record<number, string> = {
  1: '点赞',
  2: '评论',
  3: '回答',
  4: '关注',
  5: '专栏购买',
  6: 'AI 分析',
  7: '系统',
}

const TARGET_LABELS: Record<number, string> = {
  1: '文章',
  2: '回答',
  3: '问题',
  4: '专栏',
  5: '用户',
}

const notification = useNotificationStore()
const router = useRouter()
const { authors, ensureAuthors } = useAuthorBrief()

const error = ref('')
const markingAll = ref(false)
const markingId = ref('')

function targetLabel(item: NotificationVO): string {
  return item.type === 7 ? '系统' : (TARGET_LABELS[item.targetType] ?? '内容')
}

function targetRoute(item: NotificationVO): Record<string, unknown> | null {
  // 系统通知不跳转；targetType=2（回答）没有 questionId，暂不可深链
  if (item.type === 7) return null

  if (item.targetType === 1) {
    return { name: 'article-detail', params: { articleId: item.targetId } }
  }
  if (item.targetType === 3) {
    return { name: 'question-detail', params: { questionId: item.targetId } }
  }
  if (item.targetType === 4) {
    return { name: 'column-detail', params: { columnId: item.targetId } }
  }
  if (item.targetType === 5) {
    const userId = item.senderId ?? item.targetId
    return userId ? { name: 'user-profile', params: { userId } } : null
  }
  return null
}

function notificationText(item: NotificationVO): string {
  if (item.type === 7) return item.content || '系统通知'
  if (item.type === 6) return '你的文章分析已完成'

  const sender = item.senderId ? (authors[item.senderId]?.username ?? '有人') : '有人'
  if (item.type === 4) return `${sender} 关注了你`

  const actions: Record<number, string> = {
    1: '赞了你的',
    2: '评论了你的',
    3: '回答了你的',
    5: '购买了你的',
  }
  return `${sender} ${actions[item.type] ?? '有新的'}${targetLabel(item)}`
}

async function loadPage(page: number): Promise<void> {
  error.value = ''
  try {
    await notification.loadList(page)
    const ids = new Set(
      notification.items.map((item) => item.senderId).filter((id): id is string => Boolean(id)),
    )
    if (ids.size) void ensureAuthors([...ids])
  } catch (err) {
    error.value = isApiError(err) ? err.message : '通知加载失败，请稍后重试。'
  }
}

async function markAll(): Promise<void> {
  if (!notification.unreadCount || markingAll.value) return

  markingAll.value = true
  try {
    await notification.markAllRead()
    toast.success('已全部标为已读')
  } catch (err) {
    toast.error(isApiError(err) ? err.message : '操作失败，请稍后重试。')
  } finally {
    markingAll.value = false
  }
}

async function openNotification(item: NotificationVO): Promise<void> {
  if (markingId.value) return

  markingId.value = item.notificationId
  try {
    await notification.markRead(item.notificationId)
    const target = targetRoute(item)
    if (target) await router.push(target)
  } catch (err) {
    toast.error(isApiError(err) ? err.message : '标记已读失败，请稍后重试。')
  } finally {
    markingId.value = ''
  }
}

onMounted(() => {
  void notification.loadUnreadCount()
  void loadPage(1)
})

useSeo({
  title: '通知中心',
  description: '点赞、评论、回答、关注与系统通知。',
})
</script>

<template>
  <PageContainer tag="section">
    <div class="notices-page">
      <header class="notices-head">
        <div class="notices-head__title">
          <p class="notices-eyebrow">[ NOTIFICATIONS ]</p>
          <h1>通知中心</h1>
          <p class="notices-lede">
            {{ notification.unreadCount }} 条未读 · 共 {{ notification.total }} 条通知
          </p>
        </div>
        <WhButton
          size="sm"
          variant="ghost"
          :disabled="notification.unreadCount === 0 || markingAll"
          :loading="markingAll"
          @click="markAll"
        >
          全部已读
        </WhButton>
      </header>

      <div v-if="notification.loading" class="notices-state" aria-live="polite">正在加载通知…</div>

      <div v-else-if="error" class="notices-state notices-state--error" role="alert">
        <p>{{ error }}</p>
        <WhButton size="sm" variant="ghost" @click="loadPage(notification.page)"> 重试 </WhButton>
      </div>

      <div v-else-if="!notification.items.length" class="notices-empty">
        <p>还没有通知。</p>
        <p class="notices-empty__hint">点赞、评论、回答与关注都会出现在这里。</p>
      </div>

      <div v-else class="notices-list">
        <article
          v-for="item in notification.items"
          :key="item.notificationId"
          class="notice-item"
          :class="{ 'is-unread': !item.read }"
        >
          <button
            class="notice-item__main"
            type="button"
            :aria-label="`${item.read ? '已读' : '未读'}：${notificationText(item)}`"
            @click="openNotification(item)"
          >
            <span class="notice-item__type">{{ TYPE_LABELS[item.type] }}</span>
            <span v-if="!item.read" class="notice-item__dot" aria-label="未读"></span>
            <span class="notice-item__body">
              <span class="notice-item__text">{{ notificationText(item) }}</span>
              <span class="notice-item__meta">
                <span>{{ targetLabel(item) }}</span>
                <time :datetime="item.createTime">
                  {{ formatDateTime(item.createTime, 'YYYY-MM-DD HH:mm') }}
                </time>
              </span>
            </span>
          </button>
        </article>
      </div>

      <div v-if="notification.total > notification.pageSize" class="notices-pagination">
        <WhPagination
          :page="notification.page"
          :page-size="notification.pageSize"
          :total="notification.total"
          @update:page="loadPage"
        />
      </div>
    </div>
  </PageContainer>
</template>

<style scoped>
.notices-page {
  display: grid;
  gap: var(--space-4);
  max-width: 880px;
  margin: 0 auto;
}

.notices-head {
  display: flex;
  flex-wrap: wrap;
  align-items: flex-end;
  justify-content: space-between;
  gap: var(--space-4);
  padding-bottom: var(--space-4);
  border-bottom: 1px solid var(--ink);
}

.notices-head__title {
  display: grid;
  gap: var(--space-2);
}

.notices-eyebrow {
  color: var(--verm-deep);
  font-family: var(--mono);
  font-size: var(--text-2xs);
  letter-spacing: var(--tracking-wider);
}

.notices-head h1 {
  font-family: var(--serif);
  font-size: clamp(2rem, 4vw, 3rem);
  font-weight: var(--weight-black);
  line-height: var(--leading-tight);
  letter-spacing: var(--tracking-tight);
}

.notices-lede {
  color: var(--text-muted);
  font-size: var(--text-sm);
}

.notices-list {
  display: grid;
}

.notice-item {
  border-bottom: var(--border);
}

.notice-item.is-unread {
  background: color-mix(in srgb, var(--acid) 12%, transparent);
}

.notice-item__main {
  display: grid;
  grid-template-columns: auto auto minmax(0, 1fr);
  align-items: start;
  gap: var(--space-2);
  width: 100%;
  padding: var(--space-3);
  border: 0;
  background: transparent;
  color: var(--ink);
  text-align: left;
  cursor: pointer;
}

.notice-item__main:hover {
  background: color-mix(in srgb, var(--acid) 22%, transparent);
}

.notice-item__type {
  padding: 2px 7px;
  border: 1px solid var(--line-strong);
  color: var(--text-muted);
  font-family: var(--mono);
  font-size: var(--text-2xs);
  white-space: nowrap;
}

.notice-item__dot {
  width: 8px;
  height: 8px;
  margin-top: 5px;
  border-radius: 50%;
  background: var(--verm);
}

.notice-item__body {
  display: grid;
  gap: var(--space-1);
}

.notice-item__text {
  font-size: var(--text-sm);
  line-height: var(--leading-relaxed);
}

.notice-item__meta {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-3);
  color: var(--text-muted);
  font-family: var(--mono);
  font-size: var(--text-2xs);
}

.notices-state {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: var(--space-3);
  padding: var(--space-6);
  border: 1px dashed var(--line-strong);
  color: var(--text-muted);
  font-size: var(--text-sm);
}

.notices-state--error {
  border-color: var(--verm);
  color: var(--verm-deep);
}

.notices-empty {
  display: grid;
  gap: var(--space-2);
  padding: var(--space-8) 0;
  color: var(--text-muted);
  font-size: var(--text-sm);
}

.notices-empty__hint {
  font-family: var(--mono);
  font-size: var(--text-2xs);
}

.notices-pagination {
  padding-top: var(--space-3);
  border-top: 1px solid var(--ink);
}

@media (max-width: 640px) {
  .notices-head {
    align-items: flex-start;
  }

  .notice-item__main {
    grid-template-columns: auto minmax(0, 1fr);
    gap: var(--space-1) var(--space-2);
  }

  .notice-item__type {
    grid-row: span 2;
  }
}
</style>
