<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import { RouterLink } from 'vue-router'

import { isApiError } from '@/api/error'
import {
  addAlias,
  createTag,
  listAliases,
  listAll,
  removeAlias,
  removeTag,
  updateTag,
} from '@/api/modules/tag'
import WhButton from '@/components/base/WhButton.vue'
import WhDrawer from '@/components/base/WhDrawer.vue'
import WhEmpty from '@/components/base/WhEmpty.vue'
import WhInput from '@/components/base/WhInput.vue'
import WhModal from '@/components/base/WhModal.vue'
import WhPagination from '@/components/base/WhPagination.vue'
import WhSelect from '@/components/base/WhSelect.vue'
import WhSkeleton from '@/components/base/WhSkeleton.vue'
import WhTextarea from '@/components/base/WhTextarea.vue'
import { confirm } from '@/components/base/confirm'
import { toast } from '@/components/base/toast'
import PageContainer from '@/components/layout/PageContainer.vue'
import { usePagination } from '@/composables/usePagination'
import { useSeo } from '@/composables/useSeo'
import { useDictStore } from '@/stores'
import type { ID } from '@/types/common'
import type { TagAliasVO, TagVO } from '@/types/content'

interface FormErrors {
  name?: string
  category?: string
  description?: string
}

const dict = useDictStore()

const tags = ref<TagVO[]>([])
const loading = ref(false)
const error = ref('')
const keyword = ref('')
const category = ref<string>('all')

const { page, pageSize, offset } = usePagination({ pageSize: 20 })

const categories = computed(() => {
  const values = [...new Set(tags.value.map((tag) => tag.category).filter(Boolean))]
  return values.sort((left, right) => left.localeCompare(right, 'zh-Hans-CN'))
})

const categoryOptions = computed(() => [
  { label: '全部分组', value: 'all' },
  ...categories.value.map((value) => ({ label: value, value })),
])

const filtered = computed(() => {
  const text = keyword.value.trim().toLowerCase()
  return tags.value.filter((tag) => {
    if (category.value !== 'all' && tag.category !== category.value) return false
    if (!text) return true
    return [tag.name, tag.category, tag.description].some((field) =>
      (field ?? '').toLowerCase().includes(text),
    )
  })
})

const paged = computed(() => filtered.value.slice(offset.value, offset.value + pageSize.value))

// 搜索 / 分组变化后回到第一页
watch([keyword, category], () => {
  page.value = 1
})

let requestId = 0

async function loadTags(): Promise<void> {
  const request = (requestId += 1)
  loading.value = true
  error.value = ''

  try {
    const result = await listAll({ skipErrorToast: true })
    if (request !== requestId) return
    tags.value = result
  } catch (err) {
    if (request !== requestId) return
    tags.value = []
    error.value = isApiError(err) ? err.message : '标签加载失败，请稍后重试。'
  } finally {
    if (request === requestId) loading.value = false
  }
}

/** 写操作后同时刷页面数据和全局标签字典缓存 */
async function refresh(): Promise<void> {
  await loadTags()
  const maxPage = Math.max(1, Math.ceil(filtered.value.length / pageSize.value))
  if (page.value > maxPage) page.value = maxPage
  void dict.loadTags(true)
}

// ---------------- 新建 / 编辑 ----------------

const editorOpen = ref(false)
const editingId = ref<ID | null>(null)
const saving = ref(false)
const formErrors = ref<FormErrors>({})
const form = reactive({ name: '', category: '', description: '' })

function resetForm(): void {
  form.name = ''
  form.category = ''
  form.description = ''
  formErrors.value = {}
}

function openCreate(): void {
  editingId.value = null
  resetForm()
  editorOpen.value = true
}

function openEdit(tag: TagVO): void {
  editingId.value = tag.tagId
  formErrors.value = {}
  form.name = tag.name
  form.category = tag.category ?? ''
  form.description = tag.description ?? ''
  editorOpen.value = true
}

function validate(): boolean {
  const errors: FormErrors = {}
  const name = form.name.trim()
  if (!name) errors.name = '标签名不能为空'
  else if (name.length > 50) errors.name = '标签名不能超过 50 字'
  if (form.category.trim().length > 20) errors.category = '分组不能超过 20 字'
  if (form.description.trim().length > 255) errors.description = '描述不能超过 255 字'
  formErrors.value = errors
  return Object.keys(errors).length === 0
}

async function submitEditor(): Promise<void> {
  if (saving.value || !validate()) return

  const editing = editingId.value
  const payload = {
    name: form.name.trim(),
    category: form.category.trim() || undefined,
    description: form.description.trim() || undefined,
  }

  saving.value = true
  try {
    if (editing) await updateTag(editing, payload, { skipErrorToast: true })
    else await createTag(payload, { skipErrorToast: true })
    toast.success(editing ? '标签已更新' : '标签已创建')
    editorOpen.value = false
    await refresh()
  } catch (err) {
    toast.error(isApiError(err) ? err.message : '保存失败，请稍后重试。')
  } finally {
    saving.value = false
  }
}

// ---------------- 删除 ----------------

const removingId = ref<ID | null>(null)

async function deleteTag(tag: TagVO): Promise<void> {
  if (removingId.value) return

  const ok = await confirm({
    title: '删除标签',
    message: `「${tag.name}」的别名、与其他标签的关系、以及文章上的关联会一起删除并同步到图谱，操作不可恢复。`,
    confirmText: '删除',
    danger: true,
  })
  if (!ok) return

  removingId.value = tag.tagId
  try {
    await removeTag(tag.tagId, { skipErrorToast: true })
    toast.success('标签已删除')
    if (aliasTag.value?.tagId === tag.tagId) aliasOpen.value = false
    await refresh()
  } catch (err) {
    toast.error(isApiError(err) ? err.message : '删除失败，请稍后重试。')
  } finally {
    removingId.value = null
  }
}

// ---------------- 别名 ----------------

const aliasOpen = ref(false)
const aliasTag = ref<TagVO | null>(null)
const aliases = ref<TagAliasVO[]>([])
const aliasLoading = ref(false)
const aliasError = ref('')
const newAlias = ref('')
const aliasFormError = ref('')
const aliasSaving = ref(false)
const aliasRemovingId = ref<ID | null>(null)

const aliasTitle = computed(() => (aliasTag.value ? `别名 · ${aliasTag.value.name}` : '别名'))

function openAliases(tag: TagVO): void {
  aliasTag.value = tag
  aliasOpen.value = true
  aliases.value = []
  aliasError.value = ''
  newAlias.value = ''
  aliasFormError.value = ''
  void loadAliases()
}

async function loadAliases(): Promise<void> {
  const tag = aliasTag.value
  if (!tag) return

  aliasLoading.value = true
  aliasError.value = ''
  try {
    aliases.value = await listAliases(tag.tagId, { skipErrorToast: true })
  } catch (err) {
    aliases.value = []
    aliasError.value = isApiError(err) ? err.message : '别名加载失败，请稍后重试。'
  } finally {
    aliasLoading.value = false
  }
}

async function submitAlias(): Promise<void> {
  if (aliasSaving.value || !aliasTag.value) return

  const alias = newAlias.value.trim()
  if (!alias) {
    aliasFormError.value = '别名不能为空'
    return
  }
  if (alias.length > 50) {
    aliasFormError.value = '别名不能超过 50 字'
    return
  }

  aliasFormError.value = ''
  aliasSaving.value = true
  try {
    await addAlias(aliasTag.value.tagId, alias, { skipErrorToast: true })
    newAlias.value = ''
    toast.success('别名已添加')
    await loadAliases()
    void dict.loadTags(true)
  } catch (err) {
    aliasFormError.value = isApiError(err) ? err.message : '添加失败，请稍后重试。'
  } finally {
    aliasSaving.value = false
  }
}

async function deleteAlias(alias: TagAliasVO): Promise<void> {
  const tag = aliasTag.value
  if (!tag || aliasRemovingId.value) return

  aliasRemovingId.value = alias.id
  try {
    await removeAlias(tag.tagId, alias.id, { skipErrorToast: true })
    aliases.value = aliases.value.filter((item) => item.id !== alias.id)
    toast.success('别名已删除')
  } catch (err) {
    toast.error(isApiError(err) ? err.message : '删除失败，请稍后重试。')
  } finally {
    aliasRemovingId.value = null
  }
}

void loadTags()

useSeo({
  title: '标签管理',
  description: '标签 CRUD 与别名维护；删除会级联清关系并同步到图谱。',
})
</script>

<template>
  <PageContainer tag="section">
    <div class="tag-admin">
      <header class="tag-admin__head">
        <div>
          <p class="tag-admin__eyebrow">[ TAG GOVERNANCE / ADMIN ]</p>
          <h1>标签管理</h1>
          <p class="tag-admin__lede">
            标签与别名是图谱的真值来源；改完由 MQ 事件异步投影，图谱里会稍晚一步。
            <span v-if="!error">
              {{
                keyword.trim() || category !== 'all'
                  ? `筛选出 ${filtered.length} 个，共 ${tags.length} 个。`
                  : `当前共 ${tags.length} 个标签。`
              }}
            </span>
          </p>
        </div>
        <div class="tag-admin__head-actions">
          <RouterLink class="tag-admin__link" :to="{ name: 'tag-proposal-inbox' }">
            提案收件箱 →
          </RouterLink>
          <WhButton size="sm" @click="openCreate">新建标签</WhButton>
        </div>
      </header>

      <div class="tag-admin__toolbar">
        <div class="tag-admin__search">
          <label class="wh-sr-only" for="tag-search">搜索标签</label>
          <WhInput
            id="tag-search"
            v-model="keyword"
            placeholder="搜索标签名 / 分组 / 描述"
            clearable
          />
        </div>
        <div class="tag-admin__filter">
          <label class="wh-sr-only" for="tag-category-filter">按分组筛选</label>
          <WhSelect
            id="tag-category-filter"
            v-model="category"
            :options="categoryOptions"
          />
        </div>
      </div>

      <div v-if="loading" class="tag-admin__skeletons" aria-live="polite">
        <div v-for="index in 4" :key="index" class="tag-admin__skeleton">
          <WhSkeleton :lines="2" />
        </div>
        <span class="wh-sr-only">正在加载标签…</span>
      </div>

      <div v-else-if="error" class="tag-admin__state tag-admin__state--error" role="alert">
        <p>{{ error }}</p>
        <WhButton size="sm" variant="ghost" @click="loadTags">重试</WhButton>
      </div>

      <WhEmpty
        v-else-if="!filtered.length"
        title="没有匹配的标签"
        :description="tags.length ? '换个关键词或分组试试。' : '标签库还是空的，先建一个。'"
      />

      <div v-else class="tag-list">
        <article v-for="(tag, index) in paged" :key="tag.tagId" class="tag-row">
          <span class="tag-row__index">
            TAG / {{ String(offset + index + 1).padStart(3, '0') }}
          </span>

          <div class="tag-row__body">
            <div class="tag-row__top">
              <h2 class="tag-row__name">{{ tag.name }}</h2>
              <span class="tag-row__category">{{ tag.category || '未分组' }}</span>
            </div>
            <p class="tag-row__desc">{{ tag.description || '暂无描述' }}</p>
          </div>

          <div class="tag-row__actions">
            <WhButton size="sm" variant="text" @click="openAliases(tag)">别名</WhButton>
            <WhButton size="sm" variant="text" @click="openEdit(tag)">编辑</WhButton>
            <WhButton
              size="sm"
              variant="text"
              class="tag-row__danger"
              :disabled="removingId === tag.tagId"
              :loading="removingId === tag.tagId"
              @click="deleteTag(tag)"
            >
              删除
            </WhButton>
          </div>
        </article>
      </div>

      <div v-if="filtered.length > pageSize" class="tag-admin__pagination">
        <WhPagination v-model:page="page" :page-size="pageSize" :total="filtered.length" />
      </div>
    </div>

    <WhModal
      v-model="editorOpen"
      :title="editingId ? '编辑标签' : '新建标签'"
      size="sm"
      @close="formErrors = {}"
    >
      <form class="tag-form" @submit.prevent="submitEditor">
        <div class="tag-field">
          <label class="tag-field__label" for="tag-name">标签名</label>
          <WhInput
            id="tag-name"
            v-model="form.name"
            :maxlength="50"
            :error="formErrors.name"
            placeholder="保留原文大小写，如 Argo CD"
          />
        </div>

        <div class="tag-field">
          <label class="tag-field__label" for="tag-category">分组</label>
          <WhInput
            id="tag-category"
            v-model="form.category"
            :maxlength="20"
            :error="formErrors.category"
            placeholder="如 数据库 / 中间件"
            hint="留空归入「其他」"
          />
        </div>

        <div class="tag-field">
          <label class="tag-field__label" for="tag-description">描述</label>
          <WhTextarea
            id="tag-description"
            v-model="form.description"
            :rows="3"
            :maxlength="255"
            show-count
            :error="formErrors.description"
            placeholder="一句话说明这个技术是什么"
          />
        </div>

        <p class="tag-form__hint">
          标签名与别名都按归一化规则查重：大小写、空格与 - _ . 会被忽略。
        </p>
      </form>

      <template #footer>
        <WhButton variant="ghost" :disabled="saving" @click="editorOpen = false">取消</WhButton>
        <WhButton :loading="saving" :disabled="saving" @click="submitEditor">
          {{ editingId ? '保存' : '创建' }}
        </WhButton>
      </template>
    </WhModal>

    <WhDrawer v-model="aliasOpen" :title="aliasTitle" width="440px">
      <div class="alias-panel">
        <form class="alias-form" @submit.prevent="submitAlias">
          <div class="alias-form__input">
            <WhInput
              v-model="newAlias"
              :maxlength="50"
              :error="aliasFormError"
              placeholder="新增别名，如 k8s"
            />
          </div>
          <WhButton size="sm" :loading="aliasSaving" :disabled="aliasSaving" @click="submitAlias">
            添加
          </WhButton>
        </form>

        <p class="alias-hint">别名全局唯一，且不能与任何标签名重复。</p>

        <div v-if="aliasLoading" class="alias-state" aria-live="polite">正在加载别名…</div>

        <div v-else-if="aliasError" class="alias-state alias-state--error" role="alert">
          <span>{{ aliasError }}</span>
          <WhButton size="sm" variant="ghost" @click="loadAliases">重试</WhButton>
        </div>

        <p v-else-if="!aliases.length" class="alias-state">还没有别名。</p>

        <ul v-else class="alias-list">
          <li v-for="item in aliases" :key="item.id" class="alias-item">
            <span class="alias-item__name">{{ item.alias }}</span>
            <WhButton
              size="sm"
              variant="text"
              :disabled="aliasRemovingId === item.id"
              :loading="aliasRemovingId === item.id"
              @click="deleteAlias(item)"
            >
              删除
            </WhButton>
          </li>
        </ul>
      </div>
    </WhDrawer>
  </PageContainer>
</template>

<style scoped>
.tag-admin {
  display: grid;
  gap: var(--space-4);
  max-width: 1080px;
  margin: 0 auto;
}

.tag-admin__head {
  display: flex;
  flex-wrap: wrap;
  align-items: flex-end;
  justify-content: space-between;
  gap: var(--space-3);
}

.tag-admin__eyebrow {
  color: var(--verm-deep);
  font-family: var(--mono);
  font-size: var(--text-2xs);
  letter-spacing: var(--tracking-wider);
}

.tag-admin__head h1 {
  margin-top: var(--space-1);
  font-family: var(--serif);
  font-size: clamp(2rem, 4vw, 3rem);
  font-weight: var(--weight-black);
  line-height: var(--leading-tight);
  letter-spacing: var(--tracking-tight);
}

.tag-admin__lede {
  max-width: 66ch;
  margin-top: var(--space-2);
  color: var(--text-muted);
  font-size: var(--text-sm);
  line-height: var(--leading-relaxed);
}

.tag-admin__head-actions {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: var(--space-3);
}

.tag-admin__link {
  padding-bottom: 2px;
  border-bottom: 1px solid var(--ink);
  font-family: var(--mono);
  font-size: var(--text-2xs);
  letter-spacing: var(--tracking-wide);
  white-space: nowrap;
}

.tag-admin__link:hover {
  color: var(--verm-deep);
  border-color: var(--verm-deep);
}

.tag-admin__toolbar {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 200px;
  gap: var(--space-3);
  align-items: start;
}

.tag-admin__skeletons {
  display: grid;
  gap: var(--space-4);
  padding-block: var(--space-4);
  border-top: 1px solid var(--line);
}

.tag-admin__skeleton {
  padding-bottom: var(--space-4);
  border-bottom: 1px solid var(--line);
}

.tag-admin__state {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: var(--space-3);
  padding: var(--space-8) 0;
  color: var(--text-muted);
  font-size: var(--text-sm);
}

.tag-admin__state--error {
  color: var(--verm-deep);
}

.tag-list {
  display: grid;
  border-top: 1px solid var(--ink);
}

.tag-row {
  display: grid;
  grid-template-columns: 92px minmax(0, 1fr) auto;
  align-items: start;
  gap: var(--space-4);
  padding: var(--space-4) 0;
  border-bottom: 1px solid var(--line);
}

.tag-row__index {
  padding-top: 4px;
  color: var(--text-muted);
  font-family: var(--mono);
  font-size: var(--text-2xs);
  letter-spacing: var(--tracking-wide);
}

.tag-row__body {
  display: grid;
  gap: var(--space-1);
  min-width: 0;
}

.tag-row__top {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: var(--space-2);
}

.tag-row__name {
  font-family: var(--serif);
  font-size: var(--text-lg);
  font-weight: var(--weight-black);
  letter-spacing: var(--tracking-tight);
  overflow-wrap: anywhere;
}

.tag-row__category {
  padding: 1px 6px;
  border: 1px solid var(--line-strong);
  color: var(--text-muted);
  font-family: var(--mono);
  font-size: var(--text-2xs);
  white-space: nowrap;
}

.tag-row__desc {
  color: var(--text-muted);
  font-size: var(--text-sm);
  line-height: var(--leading-relaxed);
  overflow-wrap: anywhere;
}

.tag-row__actions {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: var(--space-1);
}

.tag-row__danger {
  color: var(--verm-deep);
}

.tag-admin__pagination {
  padding-top: var(--space-4);
  border-top: 1px solid var(--ink);
}

.tag-form {
  display: grid;
  gap: var(--space-4);
}

.tag-field {
  display: grid;
  gap: var(--space-2);
}

.tag-field__label {
  color: var(--text-muted);
  font-family: var(--mono);
  font-size: var(--text-2xs);
  letter-spacing: var(--tracking-wider);
  text-transform: uppercase;
}

.tag-form__hint {
  color: var(--text-muted);
  font-size: var(--text-xs);
  line-height: var(--leading-relaxed);
}

.alias-panel {
  display: grid;
  gap: var(--space-4);
}

.alias-form {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  gap: var(--space-2);
  align-items: start;
}

.alias-form__input {
  min-width: 0;
}

.alias-hint {
  color: var(--text-muted);
  font-size: var(--text-xs);
  line-height: var(--leading-relaxed);
}

.alias-state {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: var(--space-3);
  padding-block: var(--space-4);
  border-top: 1px solid var(--line);
  color: var(--text-muted);
  font-size: var(--text-sm);
}

.alias-state--error {
  color: var(--verm-deep);
}

.alias-list {
  display: grid;
  border-top: 1px solid var(--line);
  margin: 0;
  padding: 0;
  list-style: none;
}

.alias-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-3);
  padding: var(--space-2) 0;
  border-bottom: 1px solid var(--line);
}

.alias-item__name {
  font-family: var(--mono);
  font-size: var(--text-sm);
  overflow-wrap: anywhere;
}

@media (max-width: 720px) {
  .tag-admin__toolbar {
    grid-template-columns: minmax(0, 1fr);
  }

  .tag-row {
    grid-template-columns: minmax(0, 1fr);
  }

  .tag-row__index {
    display: none;
  }

  .alias-form {
    grid-template-columns: minmax(0, 1fr);
  }
}
</style>
