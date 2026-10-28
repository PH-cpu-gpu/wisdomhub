<script setup lang="ts">
import { ref, watch } from 'vue'
import { RouterLink, useRouter } from 'vue-router'

import { isApiError } from '@/api/error'
import { create as createColumn, listMine } from '@/api/modules/column'
import WhButton from '@/components/base/WhButton.vue'
import WhInput from '@/components/base/WhInput.vue'
import WhModal from '@/components/base/WhModal.vue'
import WhPagination from '@/components/base/WhPagination.vue'
import WhTextarea from '@/components/base/WhTextarea.vue'
import { toast } from '@/components/base/toast'
import PageContainer from '@/components/layout/PageContainer.vue'
import { usePagination } from '@/composables/usePagination'
import { useSeo } from '@/composables/useSeo'
import type { ColumnVO } from '@/types/content'
import { formatDateTime, formatMoney, parseYuanToCents } from '@/utils'

const router = useRouter()

const columns = ref<ColumnVO[]>([])
const loading = ref(false)
const error = ref('')
const { page, pageSize, total, setTotal } = usePagination({ pageSize: 10 })

const createOpen = ref(false)
const saving = ref(false)
const formTitle = ref('')
const formDescription = ref('')
const formCover = ref('')
const formPrice = ref('')
const titleError = ref('')
const descriptionError = ref('')
const coverError = ref('')
const priceError = ref('')
const formError = ref('')

let requestId = 0

function priceLabel(price: number): string {
  return formatMoney(price, { freeLabel: '免费' })
}

async function loadColumns(): Promise<void> {
  const request = (requestId += 1)
  loading.value = true
  error.value = ''

  try {
    const result = await listMine(
      { page: page.value, pageSize: pageSize.value },
      { skipErrorToast: true },
    )
    if (request !== requestId) return

    columns.value = result.items
    setTotal(result.total)
  } catch (err) {
    if (request !== requestId) return
    columns.value = []
    setTotal(0)
    error.value = isApiError(err) ? err.message : '专栏加载失败，请稍后重试。'
  } finally {
    if (request === requestId) loading.value = false
  }
}

function resetForm(): void {
  formTitle.value = ''
  formDescription.value = ''
  formCover.value = ''
  formPrice.value = ''
  titleError.value = ''
  descriptionError.value = ''
  coverError.value = ''
  priceError.value = ''
  formError.value = ''
}

function openCreate(): void {
  resetForm()
  createOpen.value = true
}

function validateForm(): boolean {
  titleError.value = ''
  descriptionError.value = ''
  coverError.value = ''
  priceError.value = ''
  formError.value = ''

  const title = formTitle.value.trim()
  if (!title) titleError.value = '请填写专栏标题'
  else if (title.length > 50) titleError.value = '标题最多 50 个字'

  if (formDescription.value.length > 255) descriptionError.value = '简介最多 255 个字'

  const cover = formCover.value.trim()
  if (cover && !/^https?:\/\//i.test(cover)) coverError.value = '封面需要是 http(s) 开头的图片地址'

  if (formPrice.value.trim()) {
    if (!/^\d+(\.\d{1,2})?$/.test(formPrice.value.trim())) {
      priceError.value = '价格最多两位小数'
    } else if (parseYuanToCents(formPrice.value.trim()) === null) {
      priceError.value = '价格不合法'
    }
  }

  return !titleError.value && !descriptionError.value && !coverError.value && !priceError.value
}

async function submitCreate(): Promise<void> {
  if (saving.value || !validateForm()) return

  saving.value = true
  try {
    const price = formPrice.value.trim() ? (parseYuanToCents(formPrice.value.trim()) ?? 0) : 0
    const columnId = await createColumn(
      {
        title: formTitle.value.trim(),
        description: formDescription.value.trim() || undefined,
        cover: formCover.value.trim() || undefined,
        price,
      },
      { skipErrorToast: true },
    )
    toast.success('专栏已创建')
    createOpen.value = false
    await router.push({ name: 'column-detail', params: { columnId } })
  } catch (err) {
    formError.value = isApiError(err) ? err.message : '创建失败，请稍后重试。'
  } finally {
    saving.value = false
  }
}

watch(
  [page, pageSize],
  () => {
    void loadColumns()
  },
  { immediate: true },
)

useSeo({
  title: '我的专栏',
  description: '创建专栏、查看专栏收录的文章数量与价格。',
})
</script>

<template>
  <PageContainer tag="section">
    <div class="my-columns-page">
      <header class="my-columns-head">
        <div>
          <p class="my-columns-eyebrow">[ COLUMNS / ME ]</p>
          <h1>我的专栏</h1>
        </div>
        <div class="my-columns-head__aside">
          <span class="my-columns-count">{{ total }} 个专栏</span>
          <WhButton size="sm" @click="openCreate">新建专栏</WhButton>
        </div>
      </header>

      <p class="my-columns-lede">
        专栏把文章按连载顺序组织起来；创建后可以继续归入或移出自己的已发布文章。
      </p>

      <div v-if="loading" class="my-columns-state" aria-live="polite">正在加载专栏…</div>

      <div v-else-if="error" class="my-columns-state my-columns-state--error" role="alert">
        <p>{{ error }}</p>
        <WhButton size="sm" variant="ghost" @click="loadColumns">重试</WhButton>
      </div>

      <div v-else-if="!columns.length" class="my-columns-empty">
        <p>还没有专栏。创建一个，把相关的文章串成一条阅读线。</p>
        <WhButton size="sm" @click="openCreate">创建第一个专栏</WhButton>
      </div>

      <div v-else class="my-columns-list">
        <article v-for="(column, index) in columns" :key="column.columnId" class="my-column">
          <span class="my-column__index">
            COL / {{ String((page - 1) * pageSize + index + 1).padStart(2, '0') }}
          </span>

          <div class="my-column__body">
            <h2 class="my-column__title">
              <RouterLink :to="{ name: 'column-detail', params: { columnId: column.columnId } }">
                {{ column.title }}
              </RouterLink>
            </h2>
            <p class="my-column__description">{{ column.description || '暂无简介。' }}</p>
            <div class="my-column__meta">
              <span class="my-column__price">{{ priceLabel(column.price) }}</span>
              <span>{{ column.articleCount }} 篇文章</span>
              <time :datetime="column.createTime">
                {{ formatDateTime(column.createTime, 'YYYY-MM-DD') }}
              </time>
            </div>
          </div>

          <RouterLink
            class="my-column__action"
            :to="{ name: 'column-detail', params: { columnId: column.columnId } }"
          >
            管理 →
          </RouterLink>
        </article>
      </div>

      <div v-if="total > pageSize" class="my-columns-pagination">
        <WhPagination v-model:page="page" :page-size="pageSize" :total="total" />
      </div>
    </div>

    <WhModal v-model="createOpen" title="新建专栏" size="md">
      <form class="column-form" novalidate @submit.prevent="submitCreate">
        <div class="column-field">
          <label for="column-title">标题</label>
          <WhInput
            id="column-title"
            v-model="formTitle"
            type="text"
            placeholder="例如：可观测缓存实战"
            :maxlength="50"
            :invalid="Boolean(titleError)"
            :error="titleError"
            size="lg"
          />
        </div>

        <div class="column-field">
          <label for="column-description">简介</label>
          <WhTextarea
            id="column-description"
            v-model="formDescription"
            placeholder="用一两句话说明这个专栏会讲什么"
            :rows="3"
            :maxlength="255"
            :invalid="Boolean(descriptionError)"
            :error="descriptionError"
            show-count
          />
        </div>

        <div class="column-field">
          <label for="column-cover">封面图地址（可选）</label>
          <WhInput
            id="column-cover"
            v-model="formCover"
            type="url"
            placeholder="https://example.com/cover.jpg"
            :maxlength="500"
            :invalid="Boolean(coverError)"
            :error="coverError"
          />
        </div>

        <div class="column-field">
          <label for="column-price">价格（元）</label>
          <WhInput
            id="column-price"
            v-model="formPrice"
            type="text"
            placeholder="0 表示免费"
            :maxlength="10"
            :invalid="Boolean(priceError)"
            :error="priceError"
            hint="以元填写，最多两位小数；0 表示免费。"
          />
        </div>

        <div v-if="formError" class="column-form__error" role="alert">{{ formError }}</div>

        <div class="column-form__actions">
          <WhButton type="button" variant="ghost" @click="createOpen = false">取消</WhButton>
          <WhButton type="submit" :loading="saving">
            {{ saving ? '创建中…' : '创建专栏' }}
          </WhButton>
        </div>
      </form>
    </WhModal>
  </PageContainer>
</template>

<style scoped>
.my-columns-page {
  display: grid;
  gap: var(--space-3);
  max-width: 1080px;
  margin: 0 auto;
}

.my-columns-head {
  display: flex;
  flex-wrap: wrap;
  align-items: flex-end;
  justify-content: space-between;
  gap: var(--space-3);
}

.my-columns-head__aside {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: var(--space-3);
}

.my-columns-eyebrow {
  color: var(--verm-deep);
  font-family: var(--mono);
  font-size: var(--text-2xs);
  letter-spacing: var(--tracking-wider);
}

.my-columns-head h1 {
  margin-top: var(--space-1);
  font-family: var(--serif);
  font-size: clamp(2rem, 4vw, 3rem);
  font-weight: var(--weight-black);
  line-height: var(--leading-tight);
  letter-spacing: var(--tracking-tight);
}

.my-columns-count {
  color: var(--text-muted);
  font-family: var(--mono);
  font-size: var(--text-2xs);
}

.my-columns-lede {
  max-width: 62ch;
  color: var(--text-muted);
  font-size: var(--text-sm);
  line-height: var(--leading-relaxed);
}

.my-columns-state {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: var(--space-3);
  padding: var(--space-8) 0;
  color: var(--text-muted);
  font-size: var(--text-sm);
}

.my-columns-state--error {
  color: var(--verm-deep);
}

.my-columns-empty {
  display: grid;
  justify-items: start;
  gap: var(--space-3);
  padding: var(--space-10) 0;
  border-top: 1px solid var(--ink);
  color: var(--text-muted);
  font-size: var(--text-sm);
}

.my-columns-list {
  display: grid;
  border-top: 1px solid var(--ink);
}

.my-column {
  display: grid;
  grid-template-columns: 76px minmax(0, 1fr) auto;
  align-items: start;
  gap: var(--space-4);
  padding: var(--space-5) 0;
  border-bottom: 1px solid var(--line);
}

.my-column__index {
  padding-top: 4px;
  color: var(--text-muted);
  font-family: var(--mono);
  font-size: var(--text-2xs);
  letter-spacing: var(--tracking-wide);
}

.my-column__body {
  display: grid;
  gap: var(--space-2);
  min-width: 0;
}

.my-column__title {
  font-family: var(--serif);
  font-size: var(--text-xl);
  font-weight: var(--weight-black);
  line-height: var(--leading-snug);
  letter-spacing: var(--tracking-tight);
}

.my-column__title a:hover {
  color: var(--verm-deep);
}

.my-column__description {
  color: var(--text-muted);
  font-size: var(--text-sm);
  line-height: var(--leading-relaxed);
}

.my-column__meta {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: var(--space-3);
  color: var(--text-muted);
  font-family: var(--mono);
  font-size: var(--text-2xs);
}

.my-column__price {
  color: var(--verm-deep);
  font-family: var(--serif);
  font-size: var(--text-md);
  font-weight: var(--weight-black);
}

.my-column__action {
  padding-top: 4px;
  padding-bottom: 2px;
  border-bottom: 1px solid var(--ink);
  color: var(--ink);
  font-family: var(--mono);
  font-size: var(--text-2xs);
  letter-spacing: var(--tracking-wide);
  white-space: nowrap;
}

.my-column__action:hover {
  color: var(--verm-deep);
  border-color: var(--verm-deep);
}

.my-columns-pagination {
  padding-top: var(--space-4);
  border-top: 1px solid var(--ink);
}

.column-form {
  display: grid;
  gap: var(--space-4);
}

.column-field {
  display: grid;
  gap: var(--space-2);
}

.column-field label {
  color: var(--text-muted);
  font-family: var(--mono);
  font-size: var(--text-2xs);
  letter-spacing: var(--tracking-wide);
  text-transform: uppercase;
}

.column-form__error {
  padding: var(--space-2) var(--space-3);
  border: 1px solid var(--verm);
  background: rgb(255 59 31 / 8%);
  color: var(--verm-deep);
  font-size: var(--text-sm);
}

.column-form__actions {
  display: flex;
  flex-wrap: wrap;
  justify-content: flex-end;
  gap: var(--space-2);
}

@media (max-width: 640px) {
  .my-column {
    grid-template-columns: 1fr auto;
  }

  .my-column__index {
    display: none;
  }

  .my-column__title {
    font-size: var(--text-lg);
  }

  .column-form__actions {
    justify-content: stretch;
  }

  .column-form__actions > * {
    flex: 1;
  }
}
</style>
