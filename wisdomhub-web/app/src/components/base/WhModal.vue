<script setup lang="ts">
import { computed, ref, useId } from 'vue'

import { useDismissibleLayer } from '@/composables/useDismissibleLayer'

const props = withDefaults(
  defineProps<{
    modelValue: boolean
    title?: string
    ariaLabel?: string
    size?: 'sm' | 'md' | 'lg'
    closeOnOverlay?: boolean
    closeOnEsc?: boolean
    showClose?: boolean
  }>(),
  {
    size: 'md',
    closeOnOverlay: true,
    closeOnEsc: true,
    showClose: true,
  },
)

const emit = defineEmits<{
  'update:modelValue': [value: boolean]
  close: []
  opened: []
  closed: []
}>()

const panelRef = ref<HTMLElement | null>(null)
const titleId = useId()
const isOpen = computed(() => props.modelValue)

function close(): void {
  emit('update:modelValue', false)
  emit('close')
}

function onOverlay(): void {
  if (props.closeOnOverlay) close()
}

useDismissibleLayer({
  isOpen,
  panelRef,
  onClose: close,
  closeOnEsc: props.closeOnEsc,
})
</script>

<template>
  <Teleport to="body">
    <Transition name="wh-modal" @after-enter="emit('opened')" @after-leave="emit('closed')">
      <div v-if="modelValue" class="wh-modal" role="presentation" @mousedown.self="onOverlay">
        <div
          ref="panelRef"
          class="wh-modal__panel"
          :class="`wh-modal__panel--${size}`"
          role="dialog"
          aria-modal="true"
          :aria-labelledby="title ? titleId : undefined"
          :aria-label="title ? undefined : ariaLabel"
          tabindex="-1"
        >
          <header v-if="title || showClose || $slots.header" class="wh-modal__header">
            <slot name="header">
              <h2 v-if="title" :id="titleId" class="wh-modal__title">{{ title }}</h2>
            </slot>
            <button
              v-if="showClose"
              class="wh-modal__close"
              type="button"
              aria-label="关闭"
              @click="close"
            >
              ×
            </button>
          </header>

          <div class="wh-modal__body">
            <slot />
          </div>

          <footer v-if="$slots.footer" class="wh-modal__footer">
            <slot name="footer" />
          </footer>
        </div>
      </div>
    </Transition>
  </Teleport>
</template>

<style scoped>
.wh-modal {
  position: fixed;
  inset: 0;
  z-index: var(--z-modal);
  display: grid;
  place-items: center;
  padding: var(--space-4);
  background: rgb(11 11 14 / 55%);
  backdrop-filter: blur(4px);
  -webkit-backdrop-filter: blur(4px);
}

.wh-modal__panel {
  display: flex;
  flex-direction: column;
  width: min(var(--modal-width, 560px), calc(100vw - 32px));
  max-height: 84dvh;
  border: 1px solid var(--ink);
  background: var(--surface-strong);
  box-shadow: var(--shadow-lg);
}

.wh-modal__panel--sm {
  --modal-width: 420px;
}

.wh-modal__panel--md {
  --modal-width: 560px;
}

.wh-modal__panel--lg {
  --modal-width: 760px;
}

.wh-modal__header {
  display: flex;
  flex: 0 0 auto;
  align-items: flex-start;
  justify-content: space-between;
  gap: var(--space-4);
  padding: var(--space-4) var(--space-5);
  border-bottom: 1px solid var(--line);
}

.wh-modal__title {
  flex: 1;
  font-size: var(--text-xl);
  line-height: var(--leading-tight);
}

.wh-modal__close {
  display: grid;
  flex: 0 0 auto;
  place-items: center;
  width: 32px;
  height: 32px;
  margin-left: auto;
  color: var(--text-muted);
  font-family: var(--sans);
  font-size: 22px;
  line-height: 1;
}

.wh-modal__close:hover {
  color: var(--verm-deep);
}

.wh-modal__close:focus-visible {
  outline: none;
  background: color-mix(in srgb, var(--verm) 14%, transparent);
  color: var(--verm-deep);
}

.wh-modal__panel:focus {
  outline: none;
}

.wh-modal__body {
  flex: 1;
  padding: var(--space-5);
  overflow-y: auto;
  color: var(--text-body);
  font-size: var(--text-sm);
  line-height: var(--leading-relaxed);
}

.wh-modal__footer {
  display: flex;
  flex: 0 0 auto;
  flex-wrap: wrap;
  justify-content: flex-end;
  gap: var(--space-2);
  padding: var(--space-4) var(--space-5);
  border-top: 1px solid var(--line);
  background: color-mix(in srgb, var(--bone) 42%, var(--surface-strong));
}

.wh-modal-enter-active,
.wh-modal-leave-active {
  transition: opacity var(--dur) var(--ease);
}

.wh-modal-enter-from,
.wh-modal-leave-to {
  opacity: 0;
}

.wh-modal-enter-active .wh-modal__panel,
.wh-modal-leave-active .wh-modal__panel {
  transition: transform var(--dur) var(--ease);
}

.wh-modal-enter-from .wh-modal__panel,
.wh-modal-leave-to .wh-modal__panel {
  transform: translateY(12px) scale(0.98);
}
</style>
