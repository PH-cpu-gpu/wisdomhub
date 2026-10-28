<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { onBeforeRouteLeave, useRoute, useRouter } from 'vue-router'

import { isApiError } from '@/api/error'
import { publish as publishArticle, source as fetchSource } from '@/api/modules/article'
import { create as createDraft, update as updateDraft } from '@/api/modules/draft'
import WhButton from '@/components/base/WhButton.vue'
import WhInput from '@/components/base/WhInput.vue'
import WhTabs from '@/components/base/WhTabs.vue'
import WhTextarea from '@/components/base/WhTextarea.vue'
import { confirm } from '@/components/base/confirm'
import { toast } from '@/components/base/toast'
import PageContainer from '@/components/layout/PageContainer.vue'
import { useSeo } from '@/composables/useSeo'
import { useDictStore } from '@/stores'
import type { ID } from '@/types/common'
import { formatDateTime } from '@/utils'
import { renderMarkdown } from '@/utils/markdown'

type SaveStatus = 'idle' | 'unsaved' | 'saving' | 'saved' | 'error' | 'published' | 'loading'

const AUTO_SAVE_DELAY = 20_000

const route = useRoute()
const router = useRouter()
const dict = useDictStore()

const title = ref('')
const markdown = ref('')
const summary = ref('')
const sourceSummary = ref('')
const tagIds = ref<ID[]>([])
const tagsTouched = ref(false)
const publishing = ref(false)
const articleId = ref<ID | null>(null)
const articleState = ref<1 | 2 | 3>(1)
const loading = ref(false)
const loadError = ref('')
const saveStatus = ref<SaveStatus>('idle')
const saveError = ref('')
const lastSavedAt = ref<number | null>(null)
const textareaRef = ref<HTMLTextAreaElement | null>(null)
const mobileTab = ref<'edit' | 'preview'>('edit')

const mobileTabItems = [
  { label: '编辑', value: 'edit' },
  { label: '预览', value: 'preview' },
]

let hydrating = false
let sourceLoadedFor = ''
let bypassLeaveGuard = false
let idleTimer: ReturnType<typeof setTimeout> | null = null
let maxTimer: ReturnType<typeof setTimeout> | null = null
let saveRequest = 0
let revision = 0

const isPublished = computed(() => articleState.value !== 1)
const autoSaveEnabled = computed(() => !isPublished.value)
const tags = computed(() => dict.tags)
const summaryTouched = computed(() => summary.value !== sourceSummary.value)
const settingsDirty = computed(() => summaryTouched.value || tagsTouched.value)
const canPublish = computed(
  () => Boolean(title.value.trim() && markdown.value.trim()) && !publishing.value,
)
const previewHtml = computed(() => renderMarkdown(markdown.value))
const charCount = computed(() => markdown.value.length)
const wordCount = computed(() => {
  const text = markdown.value
    .replace(/```[\s\S]*?```/g, ' ')
    .replace(/`[^`]*`/g, ' ')
    .replace(/!\[[^\]]*\]\([^)]*\)/g, ' ')
    .replace(/\[([^\]]*)\]\([^)]*\)/g, '$1')
    .replace(/[#>*_~`\-[\]()!]/g, ' ')
    .trim()
  if (!text) return 0
  const chinese = (text.match(/[\u4e00-\u9fff]/g) ?? []).length
  const latin = (text.replace(/[\u4e00-\u9fff]/g, ' ').match(/[A-Za-z0-9]+/g) ?? []).length
  return chinese + latin
})
const hasUnsaved = computed(
  () => ['unsaved', 'saving', 'error'].includes(saveStatus.value) || settingsDirty.value,
)
const statusLabel = computed(() => {
  if (loading.value) return '加载中'
  if (isPublished.value) {
    return articleState.value === 3 ? '已下架 · 自动保存关闭' : '已发布 · 自动保存关闭'
  }
  if (saveStatus.value === 'saving') return '保存中…'
  if (saveStatus.value === 'saved') {
    if (settingsDirty.value) return '发布设置未保存'
    return lastSavedAt.value
      ? `已保存 ${formatDateTime(new Date(lastSavedAt.value).toISOString(), 'HH:mm')}`
      : '已保存'
  }
  if (saveStatus.value === 'error') return '保存失败'
  if (saveStatus.value === 'unsaved') return '未保存'
  return '开始创作'
})
const statusTone = computed(() => {
  if (isPublished.value) return 'published'
  if (saveStatus.value === 'saved') return settingsDirty.value ? 'dirty' : 'saved'
  if (saveStatus.value === 'error' || saveStatus.value === 'unsaved') return 'dirty'
  return ''
})

function clearTimers(): void {
  if (idleTimer) {
    clearTimeout(idleTimer)
    idleTimer = null
  }
  if (maxTimer) {
    clearTimeout(maxTimer)
    maxTimer = null
  }
}

function focusRange(start: number, end: number): void {
  void nextTick(() => {
    const textarea = textareaRef.value
    if (!textarea) return
    textarea.focus()
    textarea.setSelectionRange(start, end)
  })
}

function wrapSelection(before: string, after = before, placeholder = ''): void {
  const textarea = textareaRef.value
  if (!textarea) return

  const start = textarea.selectionStart
  const end = textarea.selectionEnd
  const selected = markdown.value.slice(start, end) || placeholder
  const inserted = `${before}${selected}${after}`
  markdown.value = markdown.value.slice(0, start) + inserted + markdown.value.slice(end)
  focusRange(start + before.length, start + before.length + selected.length)
}

function prefixLine(prefix: string): void {
  const textarea = textareaRef.value
  if (!textarea) return

  const start = textarea.selectionStart
  const lineStart = markdown.value.lastIndexOf('\n', start - 1) + 1
  const lineEnd = markdown.value.indexOf('\n', start)
  const end = lineEnd === -1 ? markdown.value.length : lineEnd
  const line = markdown.value.slice(lineStart, end)
  const cleaned = line.replace(/^(#{1,6}\s|>\s|[-*]\s|\d+\.\s)/, '')
  const nextLine = `${prefix}${cleaned}`

  markdown.value = markdown.value.slice(0, lineStart) + nextLine + markdown.value.slice(end)
  focusRange(lineStart + prefix.length, lineStart + nextLine.length)
}

function insertCodeBlock(): void {
  const textarea = textareaRef.value
  if (!textarea) return

  const start = textarea.selectionStart
  const end = textarea.selectionEnd
  const selected = markdown.value.slice(start, end) || '代码'
  const block = `\n\`\`\`\n${selected}\n\`\`\`\n`

  markdown.value = markdown.value.slice(0, start) + block + markdown.value.slice(end)
  focusRange(start + 5, start + 5 + selected.length)
}

function onKeydown(event: KeyboardEvent): void {
  if (!event.ctrlKey && !event.metaKey) return

  const key = event.key.toLowerCase()
  if (key === 'b') {
    event.preventDefault()
    wrapSelection('**', '**', '粗体')
  } else if (key === 'i') {
    event.preventDefault()
    wrapSelection('*', '*', '斜体')
  } else if (key === 'k') {
    event.preventDefault()
    wrapSelection('[', '](https://)', '链接文字')
  }
}

function scheduleAutoSave(): void {
  if (!autoSaveEnabled.value) return

  if (idleTimer) clearTimeout(idleTimer)
  idleTimer = setTimeout(() => {
    idleTimer = null
    void saveDraft()
  }, AUTO_SAVE_DELAY)

  if (!maxTimer) {
    maxTimer = setTimeout(() => {
      maxTimer = null
      void saveDraft()
    }, AUTO_SAVE_DELAY)
  }
}

async function saveDraft(): Promise<void> {
  if (!autoSaveEnabled.value || saveStatus.value === 'saving') return

  const trimmedTitle = title.value.trim()
  const trimmedMarkdown = markdown.value.trim()
  if (!trimmedTitle && !trimmedMarkdown) {
    clearTimers()
    saveStatus.value = 'idle'
    return
  }

  clearTimers()
  const request = (saveRequest += 1)
  const savingRevision = revision
  saveStatus.value = 'saving'
  saveError.value = ''

  try {
    if (!articleId.value) {
      const id = await createDraft(
        { title: title.value, markdown: markdown.value },
        { skipErrorToast: true },
      )
      articleId.value = id
      articleState.value = 1
      sourceLoadedFor = String(id)
      bypassLeaveGuard = true
      try {
        await router.replace({ name: 'editor-edit', params: { articleId: id } })
      } finally {
        bypassLeaveGuard = false
      }
    } else {
      await updateDraft(
        articleId.value,
        { title: title.value, markdown: markdown.value },
        { skipErrorToast: true },
      )
    }

    if (request !== saveRequest) return
    if (savingRevision !== revision) {
      saveStatus.value = 'unsaved'
      scheduleAutoSave()
      return
    }

    saveStatus.value = 'saved'
    lastSavedAt.value = Date.now()
  } catch (error) {
    if (request !== saveRequest) return
    saveStatus.value = 'error'
    saveError.value = isApiError(error) ? error.message : '保存失败，请稍后重试。'
  }
}

function retrySave(): void {
  void saveDraft()
}

function toggleTag(tagId: ID): void {
  if (publishing.value) return

  if (tagIds.value.includes(tagId)) {
    tagIds.value = tagIds.value.filter((id) => id !== tagId)
    tagsTouched.value = true
    return
  }

  if (tagIds.value.length >= 10) {
    toast.info('最多选择 10 个标签')
    return
  }

  tagIds.value = [...tagIds.value, tagId]
  tagsTouched.value = true
}

async function publishDraft(): Promise<void> {
  if (publishing.value) return
  if (!title.value.trim()) {
    toast.info('请填写标题')
    return
  }
  if (!markdown.value.trim()) {
    toast.info('请填写正文')
    return
  }
  if (summary.value.length > 255) {
    toast.info('摘要不能超过 255 字')
    return
  }
  if (tagIds.value.length > 10) {
    toast.info('最多选择 10 个标签')
    return
  }

  publishing.value = true
  clearTimers()
  const wasPublished = isPublished.value

  try {
    const id = await publishArticle(
      {
        articleId: articleId.value ?? undefined,
        title: title.value.trim(),
        summary: summary.value.trim() || undefined,
        markdown: markdown.value,
        tagIds: tagsTouched.value ? [...tagIds.value] : null,
      },
      { skipErrorToast: true },
    )

    articleId.value = id
    articleState.value = 2
    saveStatus.value = 'published'
    toast.success(wasPublished ? '文章已更新' : '文章已发布')

    bypassLeaveGuard = true
    try {
      await router.push({ name: 'article-detail', params: { articleId: id } })
    } finally {
      bypassLeaveGuard = false
    }
  } catch (error) {
    toast.error(isApiError(error) ? error.message : '发布失败，请稍后重试。')
  } finally {
    publishing.value = false
  }
}

async function loadFromRoute(): Promise<void> {
  const raw = route.params.articleId
  const id = raw ? String(raw) : ''

  if (!id) {
    sourceLoadedFor = ''
    articleId.value = null
    articleState.value = 1
    summary.value = ''
    sourceSummary.value = ''
    tagsTouched.value = false
    saveStatus.value = 'idle'
    loadError.value = ''
    return
  }
  if (sourceLoadedFor === id) return

  sourceLoadedFor = id
  loading.value = true
  loadError.value = ''
  saveStatus.value = 'loading'
  hydrating = true
  clearTimers()

  try {
    const data = await fetchSource(id, { skipErrorToast: true })
    articleId.value = data.articleId
    articleState.value = data.state
    title.value = data.title
    markdown.value = data.markdown
    summary.value = data.summary ?? ''
    sourceSummary.value = data.summary ?? ''
    tagIds.value = data.tagIds ?? []
    tagsTouched.value = false
    lastSavedAt.value = data.updateTime ? new Date(data.updateTime).getTime() : null
    saveStatus.value = data.state === 1 ? 'saved' : 'published'
    sourceLoadedFor = String(data.articleId)
  } catch (error) {
    loadError.value = isApiError(error) ? error.message : '文章加载失败，请稍后重试。'
  } finally {
    loading.value = false
    await nextTick()
    hydrating = false
  }
}

watch(
  () => route.params.articleId,
  () => {
    void loadFromRoute()
  },
  { immediate: true },
)

watch([title, markdown], () => {
  if (hydrating || loading.value || !autoSaveEnabled.value) return

  revision += 1
  if (!title.value.trim() && !markdown.value.trim()) {
    clearTimers()
    saveStatus.value = 'idle'
    return
  }

  saveStatus.value = 'unsaved'
  saveError.value = ''
  scheduleAutoSave()
})

function handleBeforeUnload(event: BeforeUnloadEvent): void {
  if (!hasUnsaved.value) return
  event.preventDefault()
  event.returnValue = ''
}

onMounted(() => {
  window.addEventListener('beforeunload', handleBeforeUnload)
  void dict.loadTags()
})

onBeforeUnmount(() => {
  clearTimers()
  window.removeEventListener('beforeunload', handleBeforeUnload)
})

onBeforeRouteLeave(async () => {
  if (bypassLeaveGuard) {
    bypassLeaveGuard = false
    return true
  }
  if (!hasUnsaved.value) return true
  return await confirm({
    title: '离开编辑器',
    message: '当前内容尚未保存，确定离开？',
    confirmText: '离开',
    danger: true,
  })
})

useSeo({
  title: '写文章',
  description: 'Markdown 分栏编辑、自动保存与实时预览。',
})
</script>

<template>
  <PageContainer tag="section">
    <div class="editor-page">
      <header class="editor-head">
        <div>
          <p class="editor-eyebrow">
            [ EDITOR / {{ isPublished ? 'PUBLISHED' : articleId ? 'DRAFT' : 'NEW' }} ]
          </p>
          <h1>{{ articleId ? '编辑文章' : '写文章' }}</h1>
        </div>
        <div class="editor-head__meta">
          <span class="editor-status" :class="statusTone ? `is-${statusTone}` : ''">
            {{ statusLabel }}
          </span>
          <button
            v-if="saveStatus === 'error'"
            class="editor-retry"
            type="button"
            @click="retrySave"
          >
            重试
          </button>
          <span v-if="saveStatus === 'error'" class="editor-save-error">{{ saveError }}</span>
          <span class="editor-head__hint">
            {{
              isPublished
                ? '当前为已发布文章，发布将覆盖更新；自动保存关闭'
                : '自动保存：停止输入 20s 或距上次改动 20s'
            }}
          </span>
        </div>
      </header>

      <div v-if="loading" class="editor-state" aria-live="polite">正在加载文章源数据…</div>
      <div v-else-if="loadError" class="editor-state editor-state--error" role="alert">
        <p>{{ loadError }}</p>
        <WhButton size="sm" variant="ghost" @click="loadFromRoute">重新加载</WhButton>
      </div>

      <template v-else>
        <WhInput
          v-model="title"
          size="lg"
          placeholder="给文章起一个标题"
          aria-label="文章标题"
          :maxlength="100"
          class="editor-title"
        />

        <div class="editor-mobile-tabs">
          <WhTabs v-model="mobileTab" :items="mobileTabItems" />
        </div>

        <div class="editor-workspace">
          <div class="editor-layout" :class="`is-mobile-${mobileTab}`">
            <section class="editor-pane editor-pane--edit">
              <div class="editor-toolbar" role="toolbar" aria-label="Markdown 工具栏">
                <button type="button" title="二级标题" @click="prefixLine('## ')">H2</button>
                <button type="button" title="三级标题" @click="prefixLine('### ')">H3</button>
                <button type="button" title="引用" @click="prefixLine('> ')">❝</button>
                <button type="button" title="无序列表" @click="prefixLine('- ')">•</button>
                <button type="button" title="有序列表" @click="prefixLine('1. ')">1.</button>
                <span class="editor-toolbar__divider" aria-hidden="true"></span>
                <button
                  type="button"
                  title="加粗（Ctrl+B）"
                  @click="wrapSelection('**', '**', '粗体')"
                >
                  B
                </button>
                <button
                  type="button"
                  title="斜体（Ctrl+I）"
                  @click="wrapSelection('*', '*', '斜体')"
                >
                  I
                </button>
                <button type="button" title="删除线" @click="wrapSelection('~~', '~~', '删除线')">
                  S
                </button>
                <button type="button" title="行内代码" @click="wrapSelection('`', '`', 'code')">
                  &lt;/&gt;
                </button>
                <button type="button" title="代码块" @click="insertCodeBlock">```</button>
                <span class="editor-toolbar__divider" aria-hidden="true"></span>
                <button
                  type="button"
                  title="链接（Ctrl+K）"
                  @click="wrapSelection('[', '](https://)', '链接文字')"
                >
                  Link
                </button>
                <button
                  type="button"
                  title="图片"
                  @click="wrapSelection('![', '](https://)', '图片描述')"
                >
                  Image
                </button>
              </div>

              <textarea
                ref="textareaRef"
                v-model="markdown"
                class="editor-textarea"
                placeholder="用 Markdown 开始写作…"
                spellcheck="false"
                aria-label="Markdown 正文"
                @keydown="onKeydown"
              ></textarea>
            </section>

            <section class="editor-pane editor-pane--preview">
              <div class="editor-preview-head">
                <span>[ PREVIEW ]</span>
                <span>{{ wordCount }} 字</span>
              </div>
              <div v-if="markdown.trim()" class="editor-preview" v-html="previewHtml"></div>
              <div v-else class="editor-preview-empty">预览会随着输入实时更新。</div>
            </section>
          </div>

          <aside class="editor-settings">
            <header class="editor-settings__head">
              <p class="editor-settings__eyebrow">[ PUBLISH ]</p>
              <h2>发布设置</h2>
            </header>

            <div class="editor-field">
              <div class="editor-field__label">
                <span>摘要</span>
                <span>{{ summary.length }}/255</span>
              </div>
              <WhTextarea
                v-model="summary"
                :maxlength="255"
                :rows="3"
                auto-resize
                placeholder="一句话介绍这篇文章（可选）"
              />
            </div>

            <div class="editor-field">
              <div class="editor-field__label">
                <span>标签</span>
                <span>{{ tagIds.length }}/10</span>
              </div>
              <div class="editor-tag-picker">
                <button
                  v-for="tag in tags"
                  :key="tag.tagId"
                  class="editor-tag"
                  :class="{ 'is-active': tagIds.includes(tag.tagId) }"
                  type="button"
                  :disabled="publishing || (tagIds.length >= 10 && !tagIds.includes(tag.tagId))"
                  @click="toggleTag(tag.tagId)"
                >
                  {{ tag.name }}
                </button>
              </div>
              <p class="editor-field__hint">
                不修改标签时覆盖发布会保持原标签；清空后发布会移除全部标签。
              </p>
            </div>

            <WhButton
              variant="primary"
              size="md"
              block
              :loading="publishing"
              :disabled="!canPublish"
              @click="publishDraft"
            >
              {{ isPublished ? '更新文章' : '发布文章' }}
            </WhButton>
            <p class="editor-settings__note">
              {{ isPublished ? '覆盖发布会保留原文章 ID' : '发布成功后跳转文章详情' }}
            </p>
          </aside>
        </div>

        <footer class="editor-foot">
          <span>{{ charCount }} 字符 · {{ wordCount }} 字</span>
          <span>{{ tagIds.length }} 个标签</span>
          <span>Markdown / 实时预览 / 代码高亮</span>
        </footer>
      </template>
    </div>
  </PageContainer>
</template>

<style scoped>
.editor-page {
  display: grid;
  gap: clamp(16px, 2.5vw, 24px);
  max-width: 1280px;
  margin: 0 auto;
}

.editor-head {
  display: flex;
  flex-wrap: wrap;
  align-items: flex-end;
  justify-content: space-between;
  gap: var(--space-3);
}

.editor-eyebrow {
  color: var(--verm-deep);
  font-family: var(--mono);
  font-size: var(--text-2xs);
  letter-spacing: var(--tracking-wider);
}

.editor-head h1 {
  margin-top: var(--space-1);
  font-family: var(--serif);
  font-size: clamp(1.8rem, 3.5vw, 2.6rem);
  font-weight: var(--weight-black);
  line-height: var(--leading-tight);
  letter-spacing: var(--tracking-tight);
}

.editor-head__meta {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: var(--space-2);
}

.editor-status {
  padding: 2px 7px;
  border: 1px solid var(--line-strong);
  color: var(--text-muted);
  font-family: var(--mono);
  font-size: var(--text-2xs);
}

.editor-status.is-dirty {
  border-color: var(--verm);
  color: var(--verm-deep);
}

.editor-status.is-saved {
  border-color: var(--acid);
  background: var(--acid);
  color: var(--ink);
}

.editor-status.is-published {
  border-color: var(--ink);
  background: var(--ink);
  color: var(--acid);
}

.editor-retry {
  padding: 0;
  border: 0;
  background: transparent;
  color: var(--verm-deep);
  font-family: var(--mono);
  font-size: var(--text-2xs);
  text-decoration: underline;
  text-underline-offset: 3px;
}

.editor-save-error {
  max-width: 28ch;
  color: var(--verm-deep);
  font-size: var(--text-xs);
}

.editor-state {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: var(--space-3);
  padding: var(--space-6);
  border: 1px dashed var(--line-strong);
  color: var(--text-muted);
  font-size: var(--text-sm);
}

.editor-state--error {
  border-color: var(--verm);
  color: var(--verm-deep);
}

.editor-head__hint {
  color: var(--text-muted);
  font-size: var(--text-xs);
}

.editor-title :deep(.wh-input) {
  min-height: 52px;
  font-family: var(--serif);
  font-size: var(--text-xl);
}

.editor-mobile-tabs {
  display: none;
}

.editor-workspace {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 300px;
  gap: var(--space-4);
  align-items: start;
}

.editor-settings {
  display: grid;
  gap: var(--space-4);
  padding: var(--space-4);
  border: var(--border);
  background: var(--surface);
}

.editor-settings__head {
  display: grid;
  gap: var(--space-1);
}

.editor-settings__eyebrow {
  color: var(--verm-deep);
  font-family: var(--mono);
  font-size: var(--text-2xs);
  letter-spacing: var(--tracking-wider);
}

.editor-settings h2 {
  font-family: var(--serif);
  font-size: var(--text-xl);
  font-weight: var(--weight-black);
  letter-spacing: var(--tracking-tight);
}

.editor-field {
  display: grid;
  gap: var(--space-2);
}

.editor-field__label {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-2);
  color: var(--text-muted);
  font-family: var(--mono);
  font-size: var(--text-2xs);
}

.editor-field :deep(.wh-textarea) {
  font-size: var(--text-sm);
}

.editor-tag-picker {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.editor-tag {
  padding: 4px 8px;
  border: 1px solid var(--line-strong);
  background: transparent;
  color: var(--text-muted);
  font-family: var(--mono);
  font-size: var(--text-2xs);
  transition:
    border-color var(--dur) var(--ease),
    background var(--dur) var(--ease),
    color var(--dur) var(--ease);
}

.editor-tag:hover:not(:disabled) {
  border-color: var(--ink);
  color: var(--ink);
}

.editor-tag.is-active {
  border-color: var(--ink);
  background: var(--acid);
  color: var(--ink);
}

.editor-tag:disabled {
  opacity: 0.45;
  cursor: not-allowed;
}

.editor-field__hint {
  color: var(--text-muted);
  font-size: var(--text-2xs);
  line-height: 1.5;
}

.editor-settings__note {
  color: var(--text-muted);
  font-family: var(--mono);
  font-size: var(--text-2xs);
  text-align: center;
}

.editor-layout {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(0, 1fr);
  min-height: min(64vh, 640px);
  border: var(--border);
  background: var(--surface);
}

.editor-pane {
  display: grid;
  grid-template-rows: auto minmax(0, 1fr);
  min-width: 0;
}

.editor-pane--edit {
  border-right: var(--border);
}

.editor-toolbar {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 4px;
  padding: var(--space-2);
  border-bottom: var(--border);
  background: var(--surface-strong);
}

.editor-toolbar button {
  min-width: 30px;
  min-height: 28px;
  padding-inline: 7px;
  border: 1px solid transparent;
  background: transparent;
  color: var(--text-muted);
  font-family: var(--mono);
  font-size: var(--text-2xs);
  transition:
    border-color var(--dur) var(--ease),
    background var(--dur) var(--ease),
    color var(--dur) var(--ease);
}

.editor-toolbar button:hover {
  border-color: var(--ink);
  background: var(--acid);
  color: var(--ink);
}

.editor-toolbar__divider {
  width: 1px;
  height: 18px;
  margin-inline: 4px;
  background: var(--line-strong);
}

.editor-textarea {
  width: 100%;
  min-height: 0;
  padding: var(--space-4);
  border: 0;
  background: var(--surface);
  color: var(--text-body);
  font-family: var(--mono);
  font-size: var(--text-sm);
  line-height: 1.75;
  resize: none;
  outline: none;
}

.editor-textarea:focus {
  box-shadow: inset 0 0 0 2px var(--focus);
}

.editor-textarea::placeholder {
  color: var(--text-muted);
}

.editor-preview-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-2);
  padding: var(--space-2) var(--space-4);
  border-bottom: var(--border);
  color: var(--text-muted);
  font-family: var(--mono);
  font-size: var(--text-2xs);
}

.editor-preview,
.editor-preview-empty {
  min-height: 0;
  padding: var(--space-4);
  overflow-y: auto;
}

.editor-preview-empty {
  color: var(--text-muted);
  font-size: var(--text-sm);
}

.editor-preview {
  color: var(--text-body);
  font-size: var(--text-sm);
  line-height: 1.8;
}

.editor-preview :deep(h1),
.editor-preview :deep(h2),
.editor-preview :deep(h3) {
  margin: 1.4em 0 0.6em;
  font-family: var(--serif);
  font-weight: var(--weight-bold);
  line-height: var(--leading-snug);
}

.editor-preview :deep(h2) {
  font-size: var(--text-xl);
}

.editor-preview :deep(h3) {
  font-size: var(--text-lg);
}

.editor-preview :deep(p) {
  margin: 0.8em 0;
}

.editor-preview :deep(a) {
  color: var(--verm-deep);
  text-decoration: underline;
  text-underline-offset: 3px;
}

.editor-preview :deep(ul),
.editor-preview :deep(ol) {
  margin: 0.8em 0;
  padding-left: 1.4em;
}

.editor-preview :deep(blockquote) {
  margin: 1em 0;
  padding: var(--space-2) var(--space-4);
  border-left: 3px solid var(--acid);
  background: var(--surface-strong);
}

.editor-preview :deep(pre) {
  margin: 1em 0;
  padding: var(--space-3);
  overflow-x: auto;
  background: var(--ink);
  color: var(--bone);
  font-family: var(--mono);
  font-size: var(--text-xs);
  line-height: 1.65;
}

.editor-preview :deep(code) {
  font-family: var(--mono);
}

.editor-preview :deep(pre code) {
  background: transparent;
  color: inherit;
}

.editor-preview :deep(table) {
  width: 100%;
  margin: 1em 0;
  border-collapse: collapse;
}

.editor-preview :deep(th),
.editor-preview :deep(td) {
  padding: var(--space-2);
  border: var(--border);
  text-align: left;
}

.editor-preview :deep(img) {
  max-width: 100%;
  height: auto;
}

.editor-foot {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-2);
  color: var(--text-muted);
  font-family: var(--mono);
  font-size: var(--text-2xs);
}

@media (max-width: 1100px) {
  .editor-workspace {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 960px) {
  .editor-mobile-tabs {
    display: block;
  }

  .editor-layout {
    grid-template-columns: 1fr;
    min-height: 60vh;
  }

  .editor-pane--edit {
    border-right: 0;
  }

  .editor-layout.is-mobile-edit .editor-pane--preview {
    display: none;
  }

  .editor-layout.is-mobile-preview .editor-pane--edit {
    display: none;
  }
}

@media (max-width: 640px) {
  .editor-head__meta {
    align-items: flex-start;
    flex-direction: column;
  }

  .editor-toolbar {
    overflow-x: auto;
    flex-wrap: nowrap;
  }
}
</style>
