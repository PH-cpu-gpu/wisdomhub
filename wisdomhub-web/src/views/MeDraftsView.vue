<script setup lang="ts">
import { onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import 'element-plus/es/components/message/style/css'
import 'element-plus/es/components/message-box/style/css'

import { deleteDraft, listDrafts, type DraftBrief } from '@/api/draft'
import { ApiError } from '@/api/http'
import { formatDateTime, formatRelativeTime } from '@/utils/format'

/**
 * F1.8 草稿箱。
 *
 * ⚠️ 草稿**就是文章**（B1.20：`article` 表里 `state=1` 的一行 + 同一份正文），
 * 所以「继续编辑」直接用 `/editor/{articleId}` —— 编辑页对草稿走自动保存、
 * 对已发布文章走「更新发布」，同一份源码两种行为。
 */
const router = useRouter()

const items = ref<DraftBrief[]>([])
const total = ref(0)
const page = ref(1)
const pageSize = ref(10)
const loading = ref(false)
const deletingId = ref<string | null>(null)

async function load(retryOnOutOfRange = true) {
  loading.value = true
  try {
    const result = await listDrafts(page.value, pageSize.value)
    items.value = result.items
    total.value = result.total

    if (retryOnOutOfRange && result.items.length === 0 && result.pages > 0 && page.value > result.pages) {
      page.value = result.pages
      await load(false)
    }
  } catch (e) {
    ElMessage.error(e instanceof ApiError ? e.message : '草稿加载失败')
    items.value = []
    total.value = 0
  } finally {
    loading.value = false
  }
}

async function onDelete(draft: DraftBrief) {
  try {
    await ElMessageBox.confirm(
      `「${draft.title || '未命名草稿'}」删除后无法恢复（项目没有回收站）。`,
      '丢弃草稿',
      { type: 'warning', confirmButtonText: '丢弃', cancelButtonText: '取消' },
    )
  } catch {
    return
  }

  deletingId.value = draft.articleId
  try {
    await deleteDraft(draft.articleId)
    ElMessage.success('草稿已丢弃')
    await load()
  } catch (e) {
    ElMessage.error(e instanceof ApiError ? e.message : '删除失败')
  } finally {
    deletingId.value = null
  }
}

watch(page, () => void load())
onMounted(() => void load())
</script>

<template>
  <div class="drafts">
    <header class="drafts__head">
      <div>
        <h1 class="drafts__title">草稿箱</h1>
        <p class="drafts__sub">编辑时自动保存的草稿，点标题继续编辑</p>
      </div>
      <el-button class="drafts__new" type="primary" size="small" @click="router.push('/editor')">
        新建草稿
      </el-button>
    </header>

    <el-skeleton v-if="loading && items.length === 0" :rows="5" animated />

    <el-empty v-else-if="items.length === 0" description="没有草稿">
      <el-button type="primary" @click="router.push('/editor')">去写一篇</el-button>
    </el-empty>

    <template v-else>
      <ul class="drafts__list" :class="{ 'is-loading': loading }">
        <li v-for="draft in items" :key="draft.articleId" class="drafts__item">
          <div class="drafts__main">
            <button class="drafts__title-btn" type="button" @click="router.push(`/editor/${draft.articleId}`)">
              {{ draft.title || '未命名草稿' }}
            </button>
            <span class="drafts__time wh-mono" :title="formatDateTime(draft.updateTime)">
              最后编辑 {{ formatRelativeTime(draft.updateTime) }}
            </span>
          </div>

          <div class="drafts__ops">
            <el-button size="small" @click="router.push(`/editor/${draft.articleId}`)">继续编辑</el-button>
            <el-button
              size="small"
              type="danger"
              plain
              :loading="deletingId === draft.articleId"
              @click="onDelete(draft)"
            >
              丢弃
            </el-button>
          </div>
        </li>
      </ul>

      <div class="drafts__pager">
        <el-pagination
          v-model:current-page="page"
          v-model:page-size="pageSize"
          :total="total"
          layout="prev, pager, next, total"
          background
        />
      </div>
    </template>
  </div>
</template>

<style scoped>
.drafts {
  padding-block: var(--wh-space-5) var(--wh-space-8);
}

/* 标题 + 说明 + 新建按钮，底下一条分隔线 */
.drafts__head {
  display: flex;
  flex-wrap: wrap;
  gap: var(--wh-space-4) var(--wh-space-6);
  align-items: flex-end;

  padding-bottom: var(--wh-space-5);
  border-bottom: var(--wh-border-width) solid var(--wh-border-strong);
}

.drafts__title {
  font-size: var(--wh-text-page);
}

.drafts__sub {
  max-width: 36rem;
  margin-top: var(--wh-space-2);

  font-size: var(--wh-text-base);
  color: var(--wh-text-2);
}

.drafts__new {
  margin-left: auto;
}

.drafts__list {
  list-style: none;
}

.drafts__list.is-loading {
  opacity: 0.55;
  transition: opacity var(--wh-duration-base) var(--wh-ease);
}

.drafts__item {
  display: flex;
  flex-wrap: wrap;
  gap: var(--wh-space-4);
  align-items: center;
  justify-content: space-between;

  padding: var(--wh-space-4) 0;
  border-bottom: var(--wh-border-width) solid var(--wh-border);
}

.drafts__main {
  display: flex;
  flex-direction: column;
  gap: var(--wh-space-1);

  min-width: 0;
}

.drafts__title-btn {
  overflow: hidden;

  padding: 0;

  font-family: inherit;
  font-size: var(--wh-text-base);
  font-weight: var(--wh-weight-medium);
  color: var(--wh-text-1);
  text-align: left;
  text-overflow: ellipsis;
  white-space: nowrap;
  background: none;
  border: none;
  cursor: pointer;
}

.drafts__title-btn:hover {
  color: var(--wh-accent);
}

.drafts__time {
  font-size: var(--wh-text-xs);
  color: var(--wh-text-3);
}

.drafts__ops {
  display: flex;
  flex: none;
  gap: var(--wh-space-2);
}

.drafts__pager {
  display: flex;
  justify-content: center;
  padding-top: var(--wh-space-6);
}
</style>
