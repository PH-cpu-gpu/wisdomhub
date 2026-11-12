<script setup lang="ts">
import { nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'

export interface WhTabItem {
  label: string
  value: string
  disabled?: boolean
  badge?: string | number
}

const props = withDefaults(
  defineProps<{
    modelValue: string
    items: WhTabItem[]
    variant?: 'line' | 'pill'
    size?: 'sm' | 'md'
  }>(),
  {
    variant: 'line',
    size: 'md',
  },
)

const emit = defineEmits<{
  'update:modelValue': [value: string]
  change: [value: string]
}>()

const tablistRef = ref<HTMLElement | null>(null)
const indicatorReady = ref(false)
const indicatorStyle = ref<Record<string, string>>({
  transform: 'translateX(0px) scaleX(0)',
})
let resizeObserver: ResizeObserver | null = null

function measureIndicator(): void {
  const root = tablistRef.value
  if (!root || props.variant !== 'line') return

  const active = root.querySelector<HTMLElement>(`[data-value="${props.modelValue}"]`)
  if (!active || active.offsetWidth === 0) {
    indicatorStyle.value = { transform: 'translateX(0px) scaleX(0)' }
    return
  }

  const left = active.offsetLeft + 8
  const width = Math.max(0, active.offsetWidth - 16)
  indicatorStyle.value = { transform: `translateX(${left}px) scaleX(${width})` }
}

onMounted(() => {
  void nextTick(() => {
    measureIndicator()
    indicatorReady.value = true

    if (tablistRef.value && typeof ResizeObserver !== 'undefined') {
      resizeObserver = new ResizeObserver(measureIndicator)
      resizeObserver.observe(tablistRef.value)
    }
  })
})

onBeforeUnmount(() => {
  resizeObserver?.disconnect()
  resizeObserver = null
})

watch(
  () => [props.modelValue, props.items, props.variant, props.size],
  () => void nextTick(measureIndicator),
)

function select(item: WhTabItem): void {
  if (item.disabled || item.value === props.modelValue) return
  emit('update:modelValue', item.value)
  emit('change', item.value)
}

function focusTab(value: string): void {
  void nextTick(() => {
    tablistRef.value?.querySelector<HTMLElement>(`[data-value="${value}"]`)?.focus()
  })
}

function onKeydown(event: KeyboardEvent): void {
  const enabled = props.items.filter((item) => !item.disabled)
  if (!enabled.length) return

  const current = enabled.findIndex((item) => item.value === props.modelValue)
  let nextIndex = current

  switch (event.key) {
    case 'ArrowRight':
    case 'ArrowDown':
      nextIndex = current < 0 ? 0 : (current + 1) % enabled.length
      break
    case 'ArrowLeft':
    case 'ArrowUp':
      nextIndex = current < 0 ? 0 : (current - 1 + enabled.length) % enabled.length
      break
    case 'Home':
      nextIndex = 0
      break
    case 'End':
      nextIndex = enabled.length - 1
      break
    default:
      return
  }

  event.preventDefault()
  const next = enabled[nextIndex]
  if (!next) return
  if (next.value !== props.modelValue) {
    emit('update:modelValue', next.value)
    emit('change', next.value)
  }
  focusTab(next.value)
}
</script>

<template>
  <div
    ref="tablistRef"
    class="wh-tabs"
    :class="[`wh-tabs--${variant}`, `wh-tabs--${size}`]"
    role="tablist"
    @keydown="onKeydown"
  >
    <span
      v-if="variant === 'line'"
      class="wh-tabs__indicator"
      :class="{ 'is-ready': indicatorReady }"
      :style="indicatorStyle"
      aria-hidden="true"
    ></span>

    <button
      v-for="item in items"
      :key="item.value"
      class="wh-tabs__tab"
      :class="{ 'is-active': item.value === modelValue }"
      type="button"
      role="tab"
      :aria-selected="item.value === modelValue"
      :tabindex="item.value === modelValue ? 0 : -1"
      :disabled="item.disabled"
      :data-value="item.value"
      @click="select(item)"
    >
      <span>{{ item.label }}</span>
      <span v-if="item.badge !== undefined" class="wh-tabs__badge">{{ item.badge }}</span>
    </button>
  </div>
</template>

<style scoped>
.wh-tabs {
  position: relative;
  display: flex;
  gap: var(--space-1);
  border-bottom: 1px solid var(--line);
}

.wh-tabs__indicator {
  position: absolute;
  bottom: -1px;
  left: 0;
  width: 1px;
  height: 2px;
  background: var(--verm);
  transform-origin: left center;
  pointer-events: none;
}

.wh-tabs__indicator.is-ready {
  transition: transform var(--dur) var(--ease-out);
}

.wh-tabs__tab {
  position: relative;
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 10px 12px;
  color: var(--text-muted);
  font-size: var(--text-sm);
  font-weight: var(--weight-medium);
  white-space: nowrap;
  transition: color var(--dur) var(--ease);
}

.wh-tabs__tab:hover:not(:disabled) {
  color: var(--ink);
}

.wh-tabs__tab.is-active {
  color: var(--ink);
}

.wh-tabs__tab:disabled {
  opacity: 0.4;
  cursor: not-allowed;
}

.wh-tabs--pill {
  gap: 6px;
  border-bottom: 0;
}

.wh-tabs--pill .wh-tabs__tab {
  border: 1px solid var(--line-strong);
  background: transparent;
  transition:
    color var(--dur) var(--ease),
    background var(--dur) var(--ease),
    border-color var(--dur) var(--ease);
}

.wh-tabs--pill .wh-tabs__tab.is-active {
  border-color: var(--ink);
  background: var(--ink);
  color: var(--acid);
}

.wh-tabs__badge {
  display: inline-grid;
  place-items: center;
  min-width: 18px;
  height: 18px;
  padding: 0 5px;
  border: 1px solid var(--line);
  border-radius: var(--radius-pill);
  font-family: var(--mono);
  font-size: 10px;
  line-height: 1;
}

.wh-tabs__tab.is-active .wh-tabs__badge {
  border-color: var(--ink);
  background: var(--acid);
  color: var(--ink);
}

.wh-tabs--pill .wh-tabs__tab.is-active .wh-tabs__badge {
  border-color: var(--acid);
}

.wh-tabs--sm .wh-tabs__tab {
  padding: 6px 10px;
  font-size: var(--text-xs);
}
</style>
