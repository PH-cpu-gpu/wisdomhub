<script setup lang="ts">
import { onBeforeUnmount, ref, useId } from 'vue'

const props = withDefaults(
  defineProps<{
    content: string
    placement?: 'top' | 'bottom' | 'left' | 'right'
    delay?: number
    disabled?: boolean
  }>(),
  {
    placement: 'top',
    delay: 120,
    disabled: false,
  },
)

const visible = ref(false)
const tooltipId = useId()
const wrapperRef = ref<HTMLElement | null>(null)
let timer: ReturnType<typeof setTimeout> | null = null

function clearTimer(): void {
  if (timer) {
    clearTimeout(timer)
    timer = null
  }
}

function show(): void {
  clearTimer()
  if (props.disabled || !props.content) return
  timer = setTimeout(() => {
    visible.value = true
  }, props.delay)
}

function hide(): void {
  clearTimer()
  visible.value = false
}

function onFocusOut(event: FocusEvent): void {
  const next = event.relatedTarget
  if (next instanceof Node && wrapperRef.value?.contains(next)) return
  hide()
}

onBeforeUnmount(clearTimer)
</script>

<template>
  <span
    ref="wrapperRef"
    class="wh-tooltip"
    @mouseenter="show"
    @mouseleave="hide"
    @focusin="show"
    @focusout="onFocusOut"
    @keydown.escape="hide"
  >
    <slot />
    <Transition name="wh-tooltip">
      <span
        v-if="visible"
        :id="tooltipId"
        class="wh-tooltip__bubble"
        :class="`wh-tooltip__bubble--${placement}`"
        role="tooltip"
      >
        {{ content }}
      </span>
    </Transition>
  </span>
</template>

<style scoped>
.wh-tooltip {
  position: relative;
  display: inline-flex;
}

.wh-tooltip__bubble {
  position: absolute;
  z-index: var(--z-dropdown);
  max-width: 240px;
  padding: 6px 9px;
  border: 1px solid var(--ink);
  background: var(--ink);
  color: var(--bone);
  box-shadow: var(--shadow-md);
  font-family: var(--sans);
  font-size: var(--text-2xs);
  line-height: 1.5;
  white-space: normal;
  pointer-events: none;
}

.wh-tooltip__bubble--top {
  bottom: calc(100% + 8px);
  left: 50%;
  transform: translateX(-50%);
}

.wh-tooltip__bubble--bottom {
  top: calc(100% + 8px);
  left: 50%;
  transform: translateX(-50%);
}

.wh-tooltip__bubble--left {
  top: 50%;
  right: calc(100% + 8px);
  transform: translateY(-50%);
}

.wh-tooltip__bubble--right {
  top: 50%;
  left: calc(100% + 8px);
  transform: translateY(-50%);
}

.wh-tooltip-enter-active,
.wh-tooltip-leave-active {
  transition: opacity var(--dur) var(--ease);
}

.wh-tooltip-enter-from,
.wh-tooltip-leave-to {
  opacity: 0;
}
</style>
