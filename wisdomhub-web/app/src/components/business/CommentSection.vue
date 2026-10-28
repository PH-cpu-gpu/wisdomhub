<script setup lang="ts">
import { ref, watch } from 'vue'
import { RouterLink, useRoute, useRouter } from 'vue-router'

import { isApiError } from '@/api/error'
import {
  create as createComment,
  list as listComments,
  remove as removeComment,
} from '@/api/modules/comment'
import WhButton from '@/components/base/WhButton.vue'
import WhPagination from '@/components/base/WhPagination.vue'
import WhTextarea from '@/components/base/WhTextarea.vue'
import { confirm } from '@/components/base/confirm'
import { toast } from '@/components/base/toast'
import AuthorChip from '@/components/business/AuthorChip.vue'
import { useAuthorBrief } from '@/composables/useAuthorBrief'
import { useAuthStore } from '@/stores'
import type { ID } from '@/types/common'
import type { CommentVO } from '@/types/content'
import { formatDateTime } from '@/utils'

const props = withDefaults(
  defineProps<{
    targetId: ID
    targetType?: 1 | 2
  }>(),
  {
    targetType: 1,
  },
)

const emit = defineEmits<{
  totalChange: [total: number]
}>()

const PAGE_SIZE = 5

const auth = useAuthStore()
const route = useRoute()
const router = useRouter()
const { authors, ensureAuthors } = useAuthorBrief()

const comments = ref<CommentVO[]>([])
const total = ref(0)
const page = ref(1)
const loading = ref(false)
const error = ref('')

const newContent = ref('')
const newError = ref('')
const newSubmitting = ref(false)

const replyTarget = ref<CommentVO | null>(null)
const replyContent = ref('')
const replyError = ref('')
const replySubmitting = ref(false)

let requestId = 0

function ensureCommentAuthors(): void {
  const ids = new Set<ID>()
  for (const comment of comments.value) {
    ids.add(comment.userId)
    for (const reply of comment.replies) ids.add(reply.userId)
  }
  if (ids.size) void ensureAuthors([...ids])
}

async function loadComments(): Promise<void> {
  const request = (requestId += 1)
  loading.value = true
  error.value = ''

  try {
    const result = await listComments(
      {
        targetType: props.targetType,
        targetId: props.targetId,
        page: page.value,
        pageSize: PAGE_SIZE,
      },
      { skipErrorToast: true },
    )
    if (request !== requestId) return

    comments.value = result.items
    total.value = result.total
    emit('totalChange', result.total)
  } catch (err) {
    if (request !== requestId) return
    comments.value = []
    total.value = 0
    error.value = isApiError(err) ? err.message : '评论加载失败，请稍后重试。'
  } finally {
    if (request === requestId) loading.value = false
  }
}

function requireLogin(): boolean {
  if (auth.isLoggedIn()) return true
  void router.push({ name: 'login', query: { redirect: route.fullPath } })
  return false
}

async function submitComment(): Promise<void> {
  if (newSubmitting.value) return
  if (!requireLogin()) return

  const content = newContent.value.trim()
  newError.value = ''
  if (!content) {
    newError.value = '请输入评论内容'
    return
  }
  if (content.length > 1000) {
    newError.value = '评论内容长度不能超过 1000 字'
    return
  }

  newSubmitting.value = true
  try {
    await createComment(
      { targetType: props.targetType, targetId: props.targetId, content },
      { skipErrorToast: true },
    )
    newContent.value = ''
    toast.success('评论已发布')
    await loadComments()
  } catch (err) {
    newError.value = isApiError(err) ? err.message : '评论发布失败，请稍后重试。'
  } finally {
    newSubmitting.value = false
  }
}

function startReply(comment: CommentVO): void {
  if (!requireLogin()) return
  replyTarget.value = comment
  replyContent.value = ''
  replyError.value = ''
}

function cancelReply(): void {
  replyTarget.value = null
  replyContent.value = ''
  replyError.value = ''
}

async function submitReply(): Promise<void> {
  if (!replyTarget.value || replySubmitting.value) return
  if (!requireLogin()) return

  const content = replyContent.value.trim()
  replyError.value = ''
  if (!content) {
    replyError.value = '请输入回复内容'
    return
  }
  if (content.length > 1000) {
    replyError.value = '回复内容长度不能超过 1000 字'
    return
  }

  const root = replyTarget.value
  replySubmitting.value = true
  try {
    await createComment(
      {
        targetType: props.targetType,
        targetId: props.targetId,
        parentId: root.commentId,
        content,
      },
      { skipErrorToast: true },
    )
    cancelReply()
    toast.success('回复已发布')
    await loadComments()
  } catch (err) {
    replyError.value = isApiError(err) ? err.message : '回复发布失败，请稍后重试。'
  } finally {
    replySubmitting.value = false
  }
}

async function deleteComment(comment: CommentVO, isRoot: boolean): Promise<void> {
  if (!requireLogin() || auth.userId !== comment.userId) return

  const hasReplies = isRoot && comment.replies.length > 0
  const ok = await confirm({
    title: '删除评论',
    message: hasReplies ? '删除后其下的回复也会一起删除，确定继续？' : '确定删除这条评论？',
    confirmText: '删除',
    danger: true,
  })
  if (!ok) return

  try {
    await removeComment(comment.commentId, { skipErrorToast: true })
    toast.success('评论已删除')
    if (comments.value.length === 1 && page.value > 1) page.value -= 1
    else await loadComments()
  } catch (err) {
    toast.error(isApiError(err) ? err.message : '删除失败，请稍后重试。')
  }
}

function changePage(value: number): void {
  if (value === page.value) return
  page.value = value
}

watch(
  () => props.targetId,
  () => {
    page.value = 1
    replyTarget.value = null
    replyContent.value = ''
    newContent.value = ''
    newError.value = ''
    void loadComments()
  },
  { immediate: true },
)

watch(page, (value, previous) => {
  if (value !== previous) void loadComments()
})

watch(comments, ensureCommentAuthors, { immediate: true })
</script>

<template>
  <section class="comment-section">
    <header class="comment-head">
      <div>
        <p class="comment-eyebrow">[ COMMENTS ]</p>
        <h2>评论</h2>
      </div>
      <span class="comment-count">{{ total }} 条评论</span>
    </header>

    <div v-if="auth.isLoggedIn()" class="comment-compose">
      <WhTextarea
        v-model="newContent"
        :rows="3"
        :maxlength="1000"
        show-count
        auto-resize
        placeholder="写下你的想法…"
      />
      <div class="comment-compose__actions">
        <span class="comment-hint">按一级评论分页，回复会显示在对应评论下</span>
        <WhButton
          :disabled="!newContent.trim() || newSubmitting"
          :loading="newSubmitting"
          @click="submitComment"
        >
          发表评论
        </WhButton>
      </div>
      <p v-if="newError" class="comment-error" role="alert">{{ newError }}</p>
    </div>

    <div v-else class="comment-guest">
      <p>登录后参与讨论。</p>
      <RouterLink
        class="comment-guest__link"
        :to="{ name: 'login', query: { redirect: route.fullPath } }"
      >
        登录 / 注册
      </RouterLink>
    </div>

    <div v-if="loading" class="comment-state" aria-live="polite">正在加载评论…</div>

    <div v-else-if="error" class="comment-state comment-state--error" role="alert">
      <p>{{ error }}</p>
      <WhButton size="sm" variant="ghost" @click="loadComments">重试</WhButton>
    </div>

    <div v-else-if="!comments.length" class="comment-state">还没有评论，来写下第一条吧。</div>

    <div v-else class="comment-list">
      <article v-for="comment in comments" :key="comment.commentId" class="comment-item">
        <div class="comment-item__head">
          <AuthorChip :author="authors[comment.userId]" />
          <time class="comment-item__time" :datetime="comment.createTime">
            {{ formatDateTime(comment.createTime, 'YYYY-MM-DD HH:mm') }}
          </time>
          <div class="comment-item__actions">
            <button class="comment-action" type="button" @click="startReply(comment)">回复</button>
            <button
              v-if="auth.userId === comment.userId"
              class="comment-action comment-action--danger"
              type="button"
              @click="deleteComment(comment, true)"
            >
              删除
            </button>
          </div>
        </div>

        <p class="comment-item__content">{{ comment.content }}</p>

        <div v-if="comment.replies.length" class="comment-replies">
          <div v-for="reply in comment.replies" :key="reply.commentId" class="comment-reply">
            <div class="comment-reply__head">
              <AuthorChip :author="authors[reply.userId]" />
              <time class="comment-item__time" :datetime="reply.createTime">
                {{ formatDateTime(reply.createTime, 'YYYY-MM-DD HH:mm') }}
              </time>
              <button
                v-if="auth.userId === reply.userId"
                class="comment-action comment-action--danger"
                type="button"
                @click="deleteComment(reply, false)"
              >
                删除
              </button>
            </div>
            <p class="comment-reply__content">{{ reply.content }}</p>
          </div>
        </div>

        <div v-if="replyTarget?.commentId === comment.commentId" class="comment-reply-form">
          <WhTextarea
            v-model="replyContent"
            :rows="2"
            :maxlength="1000"
            show-count
            auto-resize
            :placeholder="`回复 @${authors[comment.userId]?.username ?? '用户'}`"
          />
          <div class="comment-reply-form__actions">
            <span class="comment-hint">回复将显示在该评论下</span>
            <div class="comment-reply-form__buttons">
              <WhButton size="sm" variant="ghost" @click="cancelReply">取消</WhButton>
              <WhButton
                size="sm"
                :disabled="!replyContent.trim() || replySubmitting"
                :loading="replySubmitting"
                @click="submitReply"
              >
                发布回复
              </WhButton>
            </div>
          </div>
          <p v-if="replyError" class="comment-error" role="alert">{{ replyError }}</p>
        </div>
      </article>
    </div>

    <div v-if="total > PAGE_SIZE" class="comment-pagination">
      <WhPagination :page="page" :page-size="PAGE_SIZE" :total="total" @update:page="changePage" />
    </div>
  </section>
</template>

<style scoped>
.comment-section {
  display: grid;
  gap: var(--space-5);
  padding-top: clamp(24px, 4vw, 40px);
  border-top: var(--border);
}

.comment-head {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: var(--space-3);
}

.comment-eyebrow {
  color: var(--verm-deep);
  font-family: var(--mono);
  font-size: var(--text-2xs);
  letter-spacing: var(--tracking-wider);
}

.comment-head h2 {
  margin-top: var(--space-1);
  font-family: var(--serif);
  font-size: var(--text-2xl);
  font-weight: var(--weight-black);
  letter-spacing: var(--tracking-tight);
}

.comment-count {
  color: var(--text-muted);
  font-family: var(--mono);
  font-size: var(--text-2xs);
}

.comment-compose {
  display: grid;
  gap: var(--space-3);
  padding: var(--space-4);
  border: var(--border);
  background: var(--surface);
}

.comment-compose__actions,
.comment-reply-form__actions {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-3);
}

.comment-hint {
  color: var(--text-muted);
  font-family: var(--mono);
  font-size: var(--text-2xs);
}

.comment-error {
  color: var(--verm-deep);
  font-size: var(--text-xs);
}

.comment-guest {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: var(--space-3);
  padding: var(--space-4);
  border: 1px dashed var(--line-strong);
  color: var(--text-muted);
  font-size: var(--text-sm);
}

.comment-guest__link {
  color: var(--verm-deep);
  font-family: var(--mono);
  font-size: var(--text-2xs);
  text-decoration: underline;
  text-underline-offset: 3px;
}

.comment-state {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: var(--space-3);
  padding: var(--space-6);
  border: 1px dashed var(--line-strong);
  color: var(--text-muted);
  font-size: var(--text-sm);
}

.comment-state--error {
  border-color: var(--verm);
  color: var(--verm-deep);
}

.comment-list {
  display: grid;
}

.comment-item {
  display: grid;
  gap: var(--space-2);
  padding-block: var(--space-5);
  border-top: var(--border);
}

.comment-item:first-child {
  border-top: 0;
}

.comment-item__head {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: var(--space-2);
  min-width: 0;
}

.comment-item__time {
  color: var(--text-muted);
  font-family: var(--mono);
  font-size: var(--text-2xs);
}

.comment-item__actions {
  display: flex;
  gap: var(--space-3);
  margin-left: auto;
}

.comment-action {
  padding: 0;
  border: 0;
  background: transparent;
  color: var(--text-muted);
  font-family: var(--mono);
  font-size: var(--text-2xs);
  transition: color var(--dur) var(--ease);
}

.comment-action:hover {
  color: var(--ink);
}

.comment-action--danger:hover {
  color: var(--verm-deep);
}

.comment-item__content,
.comment-reply__content {
  color: var(--text-body);
  font-size: var(--text-sm);
  line-height: var(--leading-relaxed);
  white-space: pre-wrap;
  overflow-wrap: anywhere;
}

.comment-replies {
  display: grid;
  gap: var(--space-3);
  margin-top: var(--space-2);
  margin-left: var(--space-5);
  padding-left: var(--space-4);
  border-left: 2px solid var(--line);
}

.comment-reply {
  display: grid;
  gap: 2px;
}

.comment-reply__head {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: var(--space-2);
  min-width: 0;
}

.comment-reply__head .comment-action {
  margin-left: auto;
}

.comment-reply__content {
  color: var(--text-muted);
  font-size: var(--text-sm);
}

.comment-reply-form {
  display: grid;
  gap: var(--space-3);
  margin-top: var(--space-3);
  padding: var(--space-3);
  border: 1px dashed var(--line-strong);
  background: var(--surface);
}

.comment-reply-form__buttons {
  display: flex;
  gap: var(--space-2);
}

.comment-pagination {
  padding-top: var(--space-4);
  border-top: var(--border);
}

@media (max-width: 640px) {
  .comment-compose__actions,
  .comment-reply-form__actions {
    align-items: flex-start;
    flex-direction: column;
  }

  .comment-compose__actions :deep(.wh-button) {
    width: 100%;
  }

  .comment-replies {
    margin-left: var(--space-2);
    padding-left: var(--space-3);
  }

  .comment-item__actions {
    width: 100%;
    margin-left: 0;
  }
}
</style>
