<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { onBeforeRouteLeave, useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import 'element-plus/es/components/message/style/css'

import { getArticleSource, publishArticle } from '@/api/article'
import { createDraft, updateDraft } from '@/api/draft'
import { ApiError } from '@/api/http'
import { listTags, type Tag } from '@/api/tag'
import { renderMarkdown } from '@/composables/useMarkdown'
import { useUserStore } from '@/stores/user'
import { formatDateTime } from '@/utils/format'

/**
 * F1.3 文章编辑器（新建 + 编辑）。
 *
 * 两条来自后端的硬约束决定了这一页的形态：
 *
 * 1. **自动保存只能存草稿**（`PUT /content/draft/{id}` 只接受 `state=1`，已发布的会给 409）。
 *    所以**已发布文章的改动不自动保存**，只在点「发布」时走覆盖发布（B1.18 的乐观锁 CAS）——
 *    页面上必须把这一点说出来，否则用户会以为一直在存。
 * 2. **草稿就是文章本身**（B1.20：同一行 `state=1` + 同一份正文，没有独立的草稿集合）。
 *    所以新建草稿返回的 ID 就是这篇文章的 ID，发布时带回去原地转发布，正文不用搬。
 */

const route = useRoute()
const router = useRouter()
const user = useUserStore()

/** 自动保存的静默窗口：停手 2.5 秒才写，边打字边发请求没必要 */
const AUTOSAVE_DELAY = 2500

const STATE_DRAFT = 1
const STATE_PUBLISHED = 2

const articleId = ref<string>(typeof route.params.id === 'string' ? route.params.id : '')
const title = ref('')
const markdown = ref('')
const tagIds = ref<string[]>([])
const articleState = ref<number>(STATE_DRAFT)

const tags = ref<Tag[]>([])
const loading = ref(Boolean(articleId.value))
const saving = ref(false)
const publishing = ref(false)
const savedAt = ref<Date | null>(null)
const saveError = ref('')
const dirty = ref(false)

const isPublished = computed(() => articleState.value === STATE_PUBLISHED)
const previewHtml = computed(() => renderMarkdown(markdown.value))
const charCount = computed(() => markdown.value.length)

const saveStatus = computed(() => {
  if (saveError.value) return saveError.value
  if (isPublished.value) return '已发布文章：改动只在「发布」时保存'
  if (saving.value) return '保存中…'
  if (savedAt.value) return `草稿已保存 · ${formatDateTime(savedAt.value.toISOString()).slice(11)}`
  if (dirty.value) return '未保存'
  return articleId.value ? '已是最新' : '还没开始写'
})

// ---------------------------------------------------------------------------
// 载入
// ---------------------------------------------------------------------------

async function load() {
  if (!articleId.value) {
    await loadTags()
    return
  }

  loading.value = true
  try {
    // 源数据：属主本人、草稿/已发布/下架都能取（后端不按状态拦）
    const source = await getArticleSource(articleId.value)
    title.value = source.title
    markdown.value = source.markdown
    tagIds.value = source.tagIds ?? []
    articleState.value = source.state
    savedAt.value = source.updateTime ? new Date(source.updateTime) : null
    dirty.value = false
  } catch (e) {
    ElMessage.error(e instanceof ApiError ? e.message : '加载失败')
    void router.replace('/editor')
  } finally {
    loading.value = false
  }
  await loadTags()
}

async function loadTags() {
  try {
    tags.value = await listTags()
  } catch {
    /* 标签拉不到不该挡住写作：发布时不带标签即可 */
  }
}

// ---------------------------------------------------------------------------
// 自动保存（只对草稿）
// ---------------------------------------------------------------------------

let saveTimer: number | undefined

function scheduleSave() {
  if (saveTimer !== undefined) window.clearTimeout(saveTimer)
  saveTimer = window.setTimeout(() => {
    saveTimer = undefined
    void saveDraft()
  }, AUTOSAVE_DELAY)
}

async function saveDraft(): Promise<boolean> {
  if (isPublished.value) return false
  if (saving.value) return false
  // 空白的编辑器不建草稿：不然点进来又退出，草稿箱里就多一条「未命名」
  if (!title.value.trim() && !markdown.value.trim()) return false

  saving.value = true
  saveError.value = ''
  try {
    const payload = { title: title.value.trim() || '未命名草稿', markdown: markdown.value }
    if (!articleId.value) {
      articleId.value = await createDraft(payload)
      // 换成具体草稿的地址：刷新后还能接着编辑，而不是又开一篇新的
      void router.replace({ name: 'article-editor-edit', params: { id: articleId.value } })
    } else {
      await updateDraft(articleId.value, payload)
    }
    dirty.value = false
    savedAt.value = new Date()
    return true
  } catch (e) {
    saveError.value = e instanceof ApiError ? e.message : '保存失败'
    return false
  } finally {
    saving.value = false
  }
}

watch([title, markdown, tagIds], () => {
  dirty.value = true
  if (!isPublished.value) scheduleSave()
})

// ---------------------------------------------------------------------------
// 发布
// ---------------------------------------------------------------------------

async function onPublish() {
  if (!title.value.trim()) {
    ElMessage.warning('先给文章起个标题')
    return
  }
  if (!markdown.value.trim()) {
    ElMessage.warning('正文还是空的')
    return
  }

  publishing.value = true
  try {
    // 带上 articleId 就是覆盖发布：草稿转发布、或改已发布的文章都走这条
    const id = await publishArticle({
      articleId: articleId.value || undefined,
      title: title.value.trim(),
      markdown: markdown.value,
      tagIds: tagIds.value,
    })
    dirty.value = false
    ElMessage.success(isPublished.value ? '已更新发布' : '发布成功')
    void router.replace(`/article/${id}`)
  } catch (e) {
    // 覆盖发布撞上乐观锁时后端给 409「文章已被修改，发布未生效」—— 原文照弹，别自己编文案
    ElMessage.error(e instanceof ApiError ? e.message : '发布失败')
  } finally {
    publishing.value = false
  }
}

function onKeydown(event: KeyboardEvent) {
  if ((event.metaKey || event.ctrlKey) && event.key.toLowerCase() === 's') {
    event.preventDefault()
    void saveDraft()
  }
}

function onBeforeUnload(event: BeforeUnloadEvent) {
  if (!dirty.value) return
  // 自动保存通常已经落盘；dirty 只会在「已发布文章」或保存失败时长期为真
  event.preventDefault()
}

onMounted(() => {
  void load()
  window.addEventListener('keydown', onKeydown)
  window.addEventListener('beforeunload', onBeforeUnload)
})

onBeforeUnmount(() => {
  if (saveTimer !== undefined) window.clearTimeout(saveTimer)
  window.removeEventListener('keydown', onKeydown)
  window.removeEventListener('beforeunload', onBeforeUnload)
})

onBeforeRouteLeave(async () => {
  if (!dirty.value || isPublished.value) return true
  // 离开前把最后一次改动落盘（草稿才有意义）
  const ok = await saveDraft()
  return ok || window.confirm('还有改动没保存成功，确定离开吗？')
})
</script>

<template>
  <div class="editor">
    <header class="editor__bar">
      <input
        v-model="title"
        class="editor__title"
        type="text"
        maxlength="100"
        placeholder="文章标题"
        aria-label="文章标题"
      />

      <div class="editor__actions">
        <span class="editor__status wh-mono" :class="{ 'is-error': Boolean(saveError) }">
          {{ saveStatus }}
        </span>

        <el-select
          v-model="tagIds"
          class="editor__tags"
          multiple
          collapse-tags
          collapse-tags-tooltip
          :multiple-limit="10"
          placeholder="选标签（最多 10 个）"
          filterable
        >
          <el-option v-for="tag in tags" :key="tag.tagId" :label="tag.name" :value="tag.tagId" />
        </el-select>

        <el-button :loading="saving" :disabled="isPublished" @click="saveDraft()">
          存草稿
        </el-button>
        <el-button type="primary" :loading="publishing" @click="onPublish">
          {{ isPublished ? '更新发布' : '发布' }}
        </el-button>
      </div>
    </header>

    <p v-if="!user.isLoggedIn" class="editor__notice">
      没有登录态，发布与保存都会被拦成 401。
    </p>

    <el-skeleton v-if="loading" :rows="8" animated />

    <div v-else class="editor__panes">
      <section class="editor__pane">
        <p class="editor__pane-label wh-mono">MARKDOWN</p>
        <textarea
          v-model="markdown"
          class="editor__input"
          placeholder="写点什么……&#10;&#10;支持表格、删除线、裸链接自动成链；代码块用 ``` 包起来并标语言，预览与发布都会高亮。"
          spellcheck="false"
        />
        <p class="editor__hint wh-mono">{{ charCount }} 字 · Ctrl/Cmd+S 存草稿</p>
      </section>

      <section class="editor__pane">
        <p class="editor__pane-label wh-mono">PREVIEW</p>
        <div class="editor__preview wh-prose" v-html="previewHtml" />
      </section>
    </div>
  </div>
</template>

<style scoped>
.editor {
  display: flex;
  flex-direction: column;
  gap: var(--wh-space-4);

  height: calc(100dvh - var(--wh-nav-height));
  padding: var(--wh-space-4);
}

.editor__bar {
  display: flex;
  flex-wrap: wrap;
  gap: var(--wh-space-3);
  align-items: center;
}

.editor__title {
  flex: 1 1 16rem;

  padding: var(--wh-space-2) var(--wh-space-3);

  font-family: inherit;
  font-size: var(--wh-text-lg);
  font-weight: var(--wh-weight-semibold);
  color: var(--wh-text-1);
  background-color: var(--wh-bg-surface);
  border: var(--wh-border-width) solid var(--wh-border);
  border-radius: var(--wh-radius-sm);
}

.editor__title:focus {
  outline: none;
  border-color: var(--wh-accent);
  box-shadow: var(--wh-focus-ring);
}

.editor__actions {
  display: flex;
  gap: var(--wh-space-2);
  align-items: center;
  margin-left: auto;
}

.editor__status {
  font-size: var(--wh-text-xs);
  color: var(--wh-text-3);
  white-space: nowrap;
}

.editor__status.is-error {
  color: var(--wh-danger);
}

.editor__tags {
  width: 14rem;
}

.editor__notice {
  padding: var(--wh-space-2) var(--wh-space-3);

  font-size: var(--wh-text-xs);
  color: var(--wh-danger);
  background-color: var(--wh-danger-soft);
  border-radius: var(--wh-radius-xs);
}

/* 分栏：左写右看。窄屏堆成一列（此时预览在下，滚动查看） */
.editor__panes {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: var(--wh-space-4);

  flex: 1;
  min-height: 0;
}

.editor__pane {
  display: flex;
  flex-direction: column;
  gap: var(--wh-space-2);

  min-height: 0;
}

.editor__pane-label {
  font-size: var(--wh-text-xs);
  letter-spacing: var(--wh-tracking-wide);
  color: var(--wh-text-3);
}

.editor__input {
  flex: 1;
  resize: none;

  padding: var(--wh-space-4);

  font-family: var(--wh-font-mono);
  font-size: var(--wh-text-sm);
  line-height: var(--wh-leading-relaxed);
  color: var(--wh-text-1);
  background-color: var(--wh-bg-surface);
  border: var(--wh-border-width) solid var(--wh-border);
  border-radius: var(--wh-radius-md);
  outline: none;
}

.editor__input:focus {
  border-color: var(--wh-accent);
}

.editor__preview {
  overflow-y: auto;

  flex: 1;
  padding: var(--wh-space-4);

  background-color: var(--wh-bg-surface);
  border: var(--wh-border-width) solid var(--wh-border);
  border-radius: var(--wh-radius-md);
}

.editor__preview:empty::before {
  content: '预览会出现在这里';
  color: var(--wh-text-3);
}

.editor__hint {
  font-size: var(--wh-text-xs);
  color: var(--wh-text-3);
}

@media (max-width: 900px) {
  .editor {
    height: auto;
  }

  .editor__panes {
    grid-template-columns: 1fr;
  }

  .editor__input {
    min-height: 20rem;
  }
}
</style>
