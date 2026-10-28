<script setup lang="ts">
import { computed, ref, useId } from 'vue'

import { useDismissibleLayer } from '@/composables/useDismissibleLayer'

const props = withDefaults(
  defineProps<{
    modelValue: boolean
    title?: string
    ariaLabel?: string
    width?: string
    closeOnOverlay?: boolean
    closeOnEsc?: boolean
    showClose?: boolean
  }>(),
  {
    width: '420px',
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
    <Transition name="wh-drawer" @after-enter="emit('opened')" @after-leave="emit('closed')">
      <div v-if="modelValue" class="wh-drawer" role="presentation" @mousedown.self="onOverlay">
        <aside
          ref="panelRef"
          class="wh-drawer__panel"
          :style="{ width }"
          role="dialog"
          aria-modal="true"
          :aria-labelledby="title ? titleId : undefined"
          :aria-label="title ? undefined : ariaLabel"
          tabindex="-1"
        >
          <header v-if="title || showClose || $slots.header" class="wh-drawer__header">
            <slot name="header">
              <h2 v-if="title" :id="titleId" class="wh-drawer__title">{{ title }}</h2>
            </slot>
            <button
              v-if="showClose"
              class="wh-drawer__close"
              type="button"
              aria-label="关闭"
              @click="close"
            >
              ×
            </button>
          </header>

          <div class="wh-drawer__body">
            <slot />
          </div>

          <footer v-if="$slots.footer" class="wh-drawer__footer">
            <slot name="footer" />
          </footer>
        </aside>
      </div>
    </Transition>
  </Teleport>
</template>

<style scoped>
.wh-drawer {
  position: fixed;
  inset: 0;
  z-index: var(--z-drawer);
  display: flex;
  justify-content: flex-end;
  background: rgb(11 11 14 / 45%);
}

.wh-drawer__panel {
  display: flex;
  flex-direction: column;
  max-width: 100vw;
  height: 100dvh;
  border-left: 1px solid var(--ink);
  background: var(--surface-strong);
  box-shadow: var(--shadow-lg);
}

.wh-drawer__header {
  display: flex;
  flex: 0 0 auto;
  align-items: flex-start;
  justify-content: space-between;
  gap: var(--space-4);
  padding: var(--space-4) var(--space-5);
  border-bottom: 1px solid var(--line);
}

.wh-drawer__title {
  flex: 1;
  font-size: var(--text-xl);
  line-height: var(--leading-tight);
}

.wh-drawer__close {
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

.wh-drawer__close:hover {
  color: var(--verm-deep);
}

.wh-drawer__close:focus-visible {
  outline: none;
  background: color-mix(in srgb, var(--verm) 14%, transparent);
  color: var(--verm-deep);
}

.wh-drawer__panel:focus {
  outline: none;
}

.wh-drawer__body {
  flex: 1;
  padding: var(--space-5);
  overflow-y: auto;
  color: var(--text-body);
  font-size: var(--text-sm);
  line-height: var(--leading-relaxed);
}

.wh-drawer__footer {
  display: flex;
  flex: 0 0 auto;
  flex-wrap: wrap;
  justify-content: flex-end;
  gap: var(--space-2);
  padding: var(--space-4) var(--space-5);
  border-top: 1px solid var(--line);
  background: color-mix(in srgb, var(--bone) 42%, var(--surface-strong));
}

.wh-drawer-enter-active,
.wh-drawer-leave-active {
  transition: opacity var(--dur) var(--ease);
}

.wh-drawer-enter-from,
.wh-drawer-leave-to {
  opacity: 0;
}

.wh-drawer-enter-active .wh-drawer__panel,
.wh-drawer-leave-active .wh-drawer__panel {
  transition: transform var(--dur) var(--ease);
}

.wh-drawer-enter-from .wh-drawer__panel,
.wh-drawer-leave-to .wh-drawer__panel {
  transform: translateX(100%);
}
</style>
