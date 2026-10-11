<script setup lang="ts">
import { onMounted, ref, watch } from 'vue'
import { RouterLink } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import 'element-plus/es/components/message/style/css'
import 'element-plus/es/components/message-box/style/css'

import {
  CommentTargetType,
  createComment,
  deleteComment,
  pageComments,
  type CommentItem,
} from '@/api/comment'
import { ApiError } from '@/api/http'
import { loadUserNames, userName } from '@/composables/useUserNames'
import { useUserStore } from '@/stores/user'
import { formatDateTime, formatRelativeTime } from '@/utils/format'

/**
 * 评论组件（F1.4）。文章与回答共用 —— 靠 `targetType` 区分。
 *
 * ⚠️ 两个「数」不是一回事，别写成同一个：
 *   - 标题上的「N 条评论」用文章/回答上的 `commentCount`，**含二级回复**
 *   - 分页器用接口返回的 `total`，那是**一级评论数**
 * 两者刻意不等（B1.25 定案：分页单位 ≠ 计数单位），看起来像 bug 但不是。
 */
const props = withDefaults(
  defineProps<{
    targetId: string
    targetType?: number
    /** 目标上的评论数（含回复），仅用于标题 */
    commentCount?: number
  }>(),
  { targetType: CommentTargetType.ARTICLE, commentCount: 0 },
)

const emit = defineEmits<{
  /** 评论增删后通知外层：目标上的 commentCount 变了，外层自己决定要不要刷新 */
  (e: 'changed'): void
}>()

const user = useUserStore()

const items = ref<CommentItem[]>([])
const total = ref(0)
const page = ref(1)
const pageSize = ref(10)
const loading = ref(false)

const content = ref('')
const submitting = ref(false)

/** 正在回复哪条（null = 没在回复）。传一级或二级评论的 id 都行，服务端会归一到一级父评论 */
const replyTo = ref<string | null>(null)
const replyContent = ref('')
const replySubmitting = ref(false)

async function load() {
  loading.value = true
  try {
    const result = await pageComments(props.targetId, props.targetType, page.value, pageSize.value)
    items.value = result.items
    total.value = result.total
    // 评论只给 userId，用户名走共享缓存批量取，一级和二级一起（见 useUserNames）
    void loadUserNames([
      ...result.items.map((item) => item.userId),
      ...result.items.flatMap((item) => (item.replies ?? []).map((reply) => reply.userId)),
    ])
  } catch (e) {
    // 读路径不校验目标：目标不存在时后端返回空列表而不是 404，所以这里基本都是网络/服务问题
    ElMessage.error(e instanceof ApiError ? e.message : '评论加载失败')
    items.value = []
    total.value = 0
  } finally {
    loading.value = false
  }
}

async function onSubmitRoot() {
  const text = content.value.trim()
  if (!text) return

  submitting.value = true
  try {
    await createComment({ targetType: props.targetType, targetId: props.targetId, content: text })
    content.value = ''
    ElMessage.success('评论已发布')
    // 回到第一页看自己的评论：按时间正序，新评论在最后一页，所以这里直接刷新当前页更实际
    await load()
    emit('changed')
  } catch (e) {
    ElMessage.error(e instanceof ApiError ? e.message : '评论失败')
  } finally {
    submitting.value = false
  }
}

function startReply(comment: CommentItem) {
  replyTo.value = comment.commentId
  replyContent.value = ''
}

function cancelReply() {
  replyTo.value = null
  replyContent.value = ''
}

async function onSubmitReply(parent: CommentItem) {
  const text = replyContent.value.trim()
  if (!text) return

  replySubmitting.value = true
  try {
    await createComment({
      targetType: props.targetType,
      targetId: props.targetId,
      parentId: parent.commentId,
      content: text,
    })
    cancelReply()
    ElMessage.success('回复已发布')
    await load()
  } catch (e) {
    ElMessage.error(e instanceof ApiError ? e.message : '回复失败')
  } finally {
    replySubmitting.value = false
  }
}

async function onDelete(comment: CommentItem) {
  try {
    await ElMessageBox.confirm(
      comment.parentId === null ? '删除这条评论会连同它的回复一起删掉。' : '确定删除这条回复吗？',
      '删除评论',
      { type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消' },
    )
  } catch {
    return // 用户取消
  }

  try {
    await deleteComment(comment.commentId)
    ElMessage.success('已删除')
    await load()
    emit('changed')
  } catch (e) {
    ElMessage.error(e instanceof ApiError ? e.message : '删除失败')
  }
}

/** 只有自己能删（后端也会拦，这里只是不显示按钮） */
function isMine(comment: CommentItem): boolean {
  return user.userId !== null && String(comment.userId) === user.userId
}

watch(() => props.targetId, () => {
  page.value = 1
  void load()
})

watch(page, () => void load())

onMounted(() => void load())
</script>

<template>
  <section class="cmt">
    <h2 class="cmt__heading">
      评论
      <span class="cmt__count wh-mono">{{ commentCount }}</span>
    </h2>

    <!-- 发表一级评论 -->
    <div class="cmt__composer">
      <textarea
        v-model="content"
        class="cmt__input"
        rows="3"
        maxlength="1000"
        placeholder="说点什么……（游客请先登录）"
      />
      <div class="cmt__composer-foot">
        <span class="wh-mono cmt__len">{{ content.length }}/1000</span>
        <el-button type="primary" :loading="submitting" :disabled="!content.trim()" @click="onSubmitRoot">
          发表评论
        </el-button>
      </div>
    </div>

    <el-skeleton v-if="loading && items.length === 0" :rows="4" animated />

    <el-empty v-else-if="items.length === 0" description="还没有评论" />

    <ul v-else class="cmt__list">
      <li v-for="root in items" :key="root.commentId" class="cmt__item">
        <div class="cmt__meta">
          <RouterLink class="cmt__author" :to="`/user/${root.userId}`">{{ userName(root.userId) }}</RouterLink>
          <span :title="formatDateTime(root.createTime)">{{ formatRelativeTime(root.createTime) }}</span>
        </div>
        <p class="cmt__body">{{ root.content }}</p>

        <div class="cmt__ops">
          <button class="cmt__op" type="button" @click="startReply(root)">回复</button>
          <button v-if="isMine(root)" class="cmt__op cmt__op--danger" type="button" @click="onDelete(root)">
            删除
          </button>
        </div>

        <!-- 二级回复：平铺在根评论下（服务端已归一化，所以这里只有一层） -->
        <ul v-if="root.replies?.length" class="cmt__replies">
          <li v-for="reply in root.replies" :key="reply.commentId" class="cmt__reply">
            <div class="cmt__meta">
              <RouterLink class="cmt__author" :to="`/user/${reply.userId}`">
                {{ userName(reply.userId) }}
              </RouterLink>
              <span :title="formatDateTime(reply.createTime)">{{ formatRelativeTime(reply.createTime) }}</span>
            </div>
            <p class="cmt__body">{{ reply.content }}</p>
            <div class="cmt__ops">
              <button class="cmt__op" type="button" @click="startReply(reply)">回复</button>
              <button
                v-if="isMine(reply)"
                class="cmt__op cmt__op--danger"
                type="button"
                @click="onDelete(reply)"
              >
                删除
              </button>
            </div>
          </li>
        </ul>

        <!-- 回复框：跟着被回复的那条走 -->
        <div v-if="replyTo === root.commentId" class="cmt__reply-box">
          <textarea v-model="replyContent" class="cmt__input" rows="2" maxlength="1000" placeholder="回复……" />
          <div class="cmt__composer-foot">
            <el-button size="small" @click="cancelReply">取消</el-button>
            <el-button
              size="small"
              type="primary"
              :loading="replySubmitting"
              :disabled="!replyContent.trim()"
              @click="onSubmitReply(root)"
            >
              回复
            </el-button>
          </div>
        </div>
      </li>
    </ul>

    <div v-if="total > pageSize" class="cmt__pager">
      <el-pagination
        v-model:current-page="page"
        :page-size="pageSize"
        :total="total"
        layout="prev, pager, next"
        background
      />
    </div>
  </section>
</template>

<style scoped>
.cmt {
  margin-top: var(--wh-space-8);
  padding-top: var(--wh-space-6);
  border-top: var(--wh-border-width) solid var(--wh-border-strong);
}

.cmt__heading {
  display: flex;
  gap: var(--wh-space-2);
  align-items: baseline;

  font-size: var(--wh-text-xl);
}

.cmt__count {
  font-size: var(--wh-text-sm);
  color: var(--wh-text-3);
}

.cmt__composer {
  display: flex;
  flex-direction: column;
  gap: var(--wh-space-2);
  margin: var(--wh-space-4) 0 var(--wh-space-6);
}

.cmt__input {
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

.cmt__input:focus {
  border-color: var(--wh-accent);
}

.cmt__composer-foot {
  display: flex;
  gap: var(--wh-space-2);
  align-items: center;
  justify-content: flex-end;
}

.cmt__len {
  margin-right: auto;
  font-size: var(--wh-text-xs);
  color: var(--wh-text-3);
}

.cmt__list {
  display: flex;
  flex-direction: column;
  gap: var(--wh-space-5);
  list-style: none;
}

.cmt__item,
.cmt__reply {
  padding-bottom: var(--wh-space-4);
  border-bottom: var(--wh-border-width) solid var(--wh-border);
}

.cmt__replies {
  display: flex;
  flex-direction: column;
  gap: var(--wh-space-4);

  margin-top: var(--wh-space-4);
  padding-left: var(--wh-space-4);

  list-style: none;
  border-left: 2px solid var(--wh-border);
}

.cmt__replies .cmt__reply:last-child {
  padding-bottom: 0;
  border-bottom: none;
}

.cmt__meta {
  display: flex;
  gap: var(--wh-space-3);
  align-items: center;

  font-size: var(--wh-text-xs);
  color: var(--wh-text-3);
}

.cmt__author {
  color: var(--wh-text-2);
  text-decoration: none;
}

.cmt__author:hover {
  color: var(--wh-accent);
}

.cmt__body {
  margin-top: var(--wh-space-2);

  font-size: var(--wh-text-sm);
  line-height: var(--wh-leading-relaxed);
  color: var(--wh-text-1);
  white-space: pre-wrap;
}

.cmt__ops {
  display: flex;
  gap: var(--wh-space-3);
  margin-top: var(--wh-space-2);
}

.cmt__op {
  padding: 0;

  font-size: var(--wh-text-xs);
  color: var(--wh-text-3);
  background: none;
  border: none;
  cursor: pointer;
}

.cmt__op:hover {
  color: var(--wh-accent);
  text-decoration: underline;
}

.cmt__op--danger:hover {
  color: var(--wh-danger);
}

.cmt__reply-box {
  display: flex;
  flex-direction: column;
  gap: var(--wh-space-2);
  margin-top: var(--wh-space-3);
}

.cmt__pager {
  display: flex;
  justify-content: center;
  margin-top: var(--wh-space-6);
}
</style>
