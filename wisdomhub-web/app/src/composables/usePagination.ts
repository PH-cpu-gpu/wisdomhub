import { computed, ref } from 'vue'

import type { PageQuery } from '@/types/common'

export interface UsePaginationOptions {
  page?: number
  pageSize?: number
}

export function usePagination(options: UsePaginationOptions = {}) {
  const page = ref(Math.max(1, options.page ?? 1))
  const pageSize = ref(Math.max(1, options.pageSize ?? 10))
  const total = ref(0)

  const pageCount = computed(() => Math.max(1, Math.ceil(total.value / pageSize.value)))
  const offset = computed(() => (page.value - 1) * pageSize.value)
  const query = computed<PageQuery>(() => ({
    page: page.value,
    pageSize: pageSize.value,
  }))

  function setTotal(value: number): void {
    total.value = Math.max(0, value)
    if (page.value > pageCount.value) page.value = pageCount.value
  }

  function go(value: number): void {
    page.value = Math.min(Math.max(1, Math.trunc(value)), pageCount.value)
  }

  function next(): void {
    go(page.value + 1)
  }

  function prev(): void {
    go(page.value - 1)
  }

  function reset(): void {
    page.value = 1
  }

  function changePageSize(value: number): void {
    const nextSize = Math.max(1, Math.trunc(value))
    if (nextSize === pageSize.value) return
    pageSize.value = nextSize
    page.value = 1
  }

  return {
    page,
    pageSize,
    total,
    pageCount,
    offset,
    query,
    setTotal,
    go,
    next,
    prev,
    reset,
    changePageSize,
  }
}
