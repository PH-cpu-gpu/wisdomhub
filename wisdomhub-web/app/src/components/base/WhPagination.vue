<script setup lang="ts">
import { computed } from 'vue'

import WhSelect from './WhSelect.vue'

const props = withDefaults(
  defineProps<{
    page: number
    pageSize: number
    total: number
    pageSizes?: number[]
    showSizeChanger?: boolean
    showTotal?: boolean
  }>(),
  {
    pageSizes: () => [10, 20, 50],
    showSizeChanger: false,
    showTotal: true,
  },
)

const emit = defineEmits<{
  'update:page': [page: number]
  'update:pageSize': [pageSize: number]
  change: [payload: { page: number; pageSize: number }]
}>()

const pages = computed(() => Math.max(1, Math.ceil(props.total / props.pageSize)))
const items = computed(() => buildPages(props.page, pages.value))
const sizeOptions = computed(() =>
  props.pageSizes.map((size) => ({ label: `${size} 条/页`, value: size })),
)

function buildPages(current: number, total: number): (number | 'ellipsis')[] {
  if (total <= 7) return Array.from({ length: total }, (_, index) => index + 1)

  const result: (number | 'ellipsis')[] = [1]
  const start = Math.max(2, current - 1)
  const end = Math.min(total - 1, current + 1)

  if (start > 2) result.push('ellipsis')
  for (let page = start; page <= end; page += 1) result.push(page)
  if (end < total - 1) result.push('ellipsis')
  result.push(total)

  return result
}

function go(page: number): void {
  const next = Math.min(Math.max(1, page), pages.value)
  if (next === props.page) return
  emit('update:page', next)
  emit('change', { page: next, pageSize: props.pageSize })
}

function goItem(item: number | 'ellipsis'): void {
  if (typeof item === 'number') go(item)
}

function changePageSize(value: string | number | null): void {
  if (typeof value !== 'number' || value === props.pageSize) return
  emit('update:pageSize', value)
  if (props.page !== 1) emit('update:page', 1)
  emit('change', { page: 1, pageSize: value })
}
</script>

<template>
  <nav class="wh-pagination" aria-label="分页">
    <span v-if="showTotal" class="wh-pagination__total">共 {{ total }} 条</span>

    <div class="wh-pagination__pages">
      <button
        class="wh-pagination__button"
        type="button"
        aria-label="上一页"
        :disabled="page <= 1"
        @click="go(page - 1)"
      >
        <svg viewBox="0 0 24 24" fill="none" aria-hidden="true">
          <path
            d="m15 6-6 6 6 6"
            stroke="currentColor"
            stroke-width="1.8"
            stroke-linecap="round"
            stroke-linejoin="round"
          />
        </svg>
      </button>

      <template v-for="(item, index) in items" :key="`${item}-${index}`">
        <span v-if="item === 'ellipsis'" class="wh-pagination__ellipsis">…</span>
        <button
          v-else
          class="wh-pagination__button"
          :class="{ 'is-active': item === page }"
          type="button"
          :aria-current="item === page ? 'page' : undefined"
          :aria-label="`第 ${item} 页`"
          @click="goItem(item)"
        >
          {{ item }}
        </button>
      </template>

      <button
        class="wh-pagination__button"
        type="button"
        aria-label="下一页"
        :disabled="page >= pages"
        @click="go(page + 1)"
      >
        <svg viewBox="0 0 24 24" fill="none" aria-hidden="true">
          <path
            d="m9 6 6 6-6 6"
            stroke="currentColor"
            stroke-width="1.8"
            stroke-linecap="round"
            stroke-linejoin="round"
          />
        </svg>
      </button>
    </div>

    <div v-if="showSizeChanger" class="wh-pagination__size">
      <WhSelect
        :model-value="pageSize"
        :options="sizeOptions"
        size="sm"
        @update:model-value="changePageSize"
      />
    </div>
  </nav>
</template>

<style scoped>
.wh-pagination {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: var(--space-3);
}

.wh-pagination__total {
  color: var(--text-muted);
  font-family: var(--mono);
  font-size: var(--text-2xs);
  letter-spacing: 0.04em;
  white-space: nowrap;
}

.wh-pagination__pages {
  display: flex;
  align-items: center;
  gap: 2px;
}

.wh-pagination__button {
  display: grid;
  place-items: center;
  width: 32px;
  height: 32px;
  border: 1px solid transparent;
  color: var(--text);
  font-family: var(--mono);
  font-size: var(--text-xs);
  transition:
    background var(--dur) var(--ease),
    border-color var(--dur) var(--ease),
    color var(--dur) var(--ease);
}

.wh-pagination__button svg {
  width: 14px;
  height: 14px;
}

.wh-pagination__button:hover:not(:disabled):not(.is-active) {
  border-color: var(--line-strong);
  background: var(--surface);
}

.wh-pagination__button.is-active {
  border-color: var(--ink);
  background: var(--acid);
  color: var(--ink);
  font-weight: var(--weight-bold);
}

.wh-pagination__button:disabled {
  opacity: 0.35;
  cursor: not-allowed;
}

.wh-pagination__ellipsis {
  display: grid;
  place-items: center;
  width: 24px;
  height: 32px;
  color: var(--text-muted);
  font-family: var(--mono);
  font-size: var(--text-xs);
}

.wh-pagination__size {
  width: 120px;
  margin-left: auto;
}
</style>
