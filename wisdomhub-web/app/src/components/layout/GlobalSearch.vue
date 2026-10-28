<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, ref, watch } from 'vue'
import { useRouter } from 'vue-router'

import { hotWords, suggest } from '@/api/modules/search'
import WhInput from '@/components/base/WhInput.vue'
import WhModal from '@/components/base/WhModal.vue'
import type { HotWordVO } from '@/types/search'

const DEBOUNCE_MS = 200
const HOT_SIZE = 10

const router = useRouter()

const open = ref(false)
const keyword = ref('')
const suggestions = ref<string[]>([])
const hot = ref<HotWordVO[]>([])
const hotLoading = ref(false)
const suggesting = ref(false)
const activeIndex = ref(-1)
const panelRef = ref<HTMLElement | null>(null)

let debounceTimer: ReturnType<typeof setTimeout> | null = null
let suggestRequest = 0
let hotLoaded = false

const trimmedKeyword = computed(() => keyword.value.trim())
const listItems = computed(() =>
  trimmedKeyword.value ? suggestions.value : hot.value.map((item) => item.keyword),
)

function clearDebounce(): void {
  if (debounceTimer) {
    clearTimeout(debounceTimer)
    debounceTimer = null
  }
}

async function loadHotWords(): Promise<void> {
  if (hotLoaded || hotLoading.value) return

  hotLoading.value = true
  try {
    hot.value = await hotWords({ size: HOT_SIZE }, { skipErrorToast: true })
    hotLoaded = true
  } catch {
    hot.value = []
  } finally {
    hotLoading.value = false
  }
}

function openSearch(): void {
  open.value = true
  void loadHotWords()
}

function focusInput(): void {
  void nextTick(() => {
    panelRef.value?.querySelector<HTMLInputElement>('input')?.focus()
  })
}

function reset(): void {
  clearDebounce()
  suggestRequest += 1
  keyword.value = ''
  suggestions.value = []
  activeIndex.value = -1
  suggesting.value = false
}

function submit(value: string): void {
  const query = value.trim()
  if (!query) return

  open.value = false
  void router.push({ name: 'search', query: { keyword: query } })
}

async function loadSuggestions(value: string): Promise<void> {
  const request = (suggestRequest += 1)
  try {
    const result = await suggest(
      { keyword: value, type: 'article', size: 6 },
      { skipErrorToast: true },
    )
    if (request !== suggestRequest) return
    suggestions.value = result
    activeIndex.value = -1
  } catch {
    if (request === suggestRequest) suggestions.value = []
  } finally {
    if (request === suggestRequest) suggesting.value = false
  }
}

function scrollActive(): void {
  void nextTick(() => {
    document
      .getElementById(`global-search-option-${activeIndex.value}`)
      ?.scrollIntoView({ block: 'nearest' })
  })
}

function onKeydown(event: KeyboardEvent): void {
  if (!open.value) return

  if (event.key === 'ArrowDown') {
    event.preventDefault()
    if (!listItems.value.length) return
    activeIndex.value = (activeIndex.value + 1) % listItems.value.length
    scrollActive()
  } else if (event.key === 'ArrowUp') {
    event.preventDefault()
    if (!listItems.value.length) return
    activeIndex.value = (activeIndex.value - 1 + listItems.value.length) % listItems.value.length
    scrollActive()
  } else if (event.key === 'Enter') {
    event.preventDefault()
    submit(
      activeIndex.value >= 0 ? (listItems.value[activeIndex.value] ?? '') : trimmedKeyword.value,
    )
  } else if (event.key === 'Escape') {
    event.preventDefault()
    open.value = false
  }
}

watch(keyword, () => {
  clearDebounce()
  activeIndex.value = -1
  const value = trimmedKeyword.value

  if (!value) {
    suggestions.value = []
    suggesting.value = false
    return
  }

  suggesting.value = true
  debounceTimer = setTimeout(() => {
    void loadSuggestions(value)
  }, DEBOUNCE_MS)
})

onBeforeUnmount(clearDebounce)
</script>

<template>
  <div class="global-search">
    <button class="global-search__trigger" type="button" aria-label="搜索" @click="openSearch">
      <svg
        viewBox="0 0 24 24"
        fill="none"
        stroke="currentColor"
        stroke-width="1.8"
        aria-hidden="true"
      >
        <circle cx="11" cy="11" r="7" />
        <path d="m20 20-3.5-3.5" stroke-linecap="round" />
      </svg>
    </button>

    <WhModal
      v-model="open"
      size="lg"
      :show-close="false"
      aria-label="全局搜索"
      @opened="focusInput"
      @closed="reset"
    >
      <div ref="panelRef" class="global-search__panel" @keydown="onKeydown">
        <div class="global-search__top">
          <WhInput
            v-model="keyword"
            size="lg"
            placeholder="搜索文章、问题、用户或标签"
            aria-label="搜索关键词"
            clearable
          />
          <button
            class="global-search__esc"
            type="button"
            aria-label="关闭搜索"
            @click="open = false"
          >
            Esc
          </button>
        </div>

        <section v-if="!trimmedKeyword" class="global-search__section">
          <p class="global-search__label">[ HOT SEARCH ]</p>
          <p v-if="hotLoading" class="global-search__hint-line">正在加载热搜…</p>
          <div v-else-if="hot.length" class="global-search__hot">
            <button
              v-for="(item, index) in hot"
              :key="item.keyword"
              class="global-search__hot-item"
              type="button"
              @click="submit(item.keyword)"
            >
              <span class="global-search__hot-index">
                {{ String(index + 1).padStart(2, '0') }}
              </span>
              <span>{{ item.keyword }}</span>
            </button>
          </div>
          <p v-else class="global-search__hint-line">暂无热搜词。</p>
        </section>

        <section v-else class="global-search__section">
          <p class="global-search__label">[ SUGGESTIONS ]</p>
          <p v-if="suggesting" class="global-search__hint-line">正在联想…</p>
          <ul v-else-if="suggestions.length" class="global-search__suggestions">
            <li
              v-for="(item, index) in suggestions"
              :id="`global-search-option-${index}`"
              :key="item"
            >
              <button
                class="global-search__suggestion"
                :class="{ 'is-active': index === activeIndex }"
                type="button"
                @click="submit(item)"
              >
                {{ item }}
              </button>
            </li>
          </ul>
          <p v-else class="global-search__hint-line">没有匹配的联想词，按回车直接搜索。</p>
        </section>

        <p class="global-search__hint-line">↑ ↓ 选择 · Enter 搜索 · Esc 关闭</p>
      </div>
    </WhModal>
  </div>
</template>

<style scoped>
.global-search__trigger {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 34px;
  height: 34px;
  padding: 0;
  border: 1px solid transparent;
  background: transparent;
  color: var(--ink);
  cursor: pointer;
  transition:
    border-color var(--dur) var(--ease),
    background var(--dur) var(--ease);
}

.global-search__trigger:hover {
  border-color: var(--line-strong);
  background: var(--surface-strong);
}

.global-search__trigger svg {
  width: 18px;
  height: 18px;
}

.global-search__panel {
  display: grid;
  gap: var(--space-4);
}

.global-search__top {
  display: flex;
  align-items: center;
  gap: var(--space-2);
}

.global-search__top :deep(.wh-field) {
  flex: 1;
}

.global-search__esc {
  padding: 4px 8px;
  border: 1px solid var(--line-strong);
  background: transparent;
  color: var(--text-muted);
  font-family: var(--mono);
  font-size: var(--text-2xs);
  cursor: pointer;
}

.global-search__section {
  display: grid;
  gap: var(--space-2);
}

.global-search__label {
  color: var(--verm-deep);
  font-family: var(--mono);
  font-size: var(--text-2xs);
  letter-spacing: var(--tracking-wider);
}

.global-search__hot {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 1px;
  border: 1px solid var(--line);
  background: var(--line);
}

.global-search__hot-item {
  display: flex;
  align-items: center;
  gap: var(--space-3);
  padding: var(--space-2) var(--space-3);
  border: 0;
  background: var(--surface);
  color: var(--ink);
  font-size: var(--text-sm);
  text-align: left;
  cursor: pointer;
  transition: background var(--dur) var(--ease);
}

.global-search__hot-item:hover {
  background: var(--acid);
}

.global-search__hot-index {
  color: var(--text-muted);
  font-family: var(--mono);
  font-size: var(--text-2xs);
}

.global-search__suggestions {
  display: grid;
  gap: 1px;
  border: 1px solid var(--line);
  background: var(--line);
}

.global-search__suggestion {
  width: 100%;
  padding: var(--space-3);
  border: 0;
  background: var(--surface);
  color: var(--ink);
  font-size: var(--text-sm);
  text-align: left;
  cursor: pointer;
}

.global-search__suggestion.is-active,
.global-search__suggestion:hover {
  background: var(--acid);
}

.global-search__hint-line {
  color: var(--text-muted);
  font-family: var(--mono);
  font-size: var(--text-2xs);
}

@media (max-width: 640px) {
  .global-search__hot {
    grid-template-columns: 1fr;
  }

  .global-search__panel {
    gap: var(--space-3);
  }
}
</style>
