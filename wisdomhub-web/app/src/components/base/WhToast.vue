<script setup lang="ts">
import { dismissToast, toasts, type ToastType } from './toast'

const iconMap: Record<ToastType, string> = {
  success: '✓',
  error: '!',
  warning: '!',
  info: 'i',
}
</script>

<template>
  <Teleport to="body">
    <div class="wh-toast-host" aria-live="polite" aria-atomic="false">
      <TransitionGroup name="wh-toast">
        <div
          v-for="item in toasts"
          :key="item.id"
          class="wh-toast"
          :class="`wh-toast--${item.type}`"
          :role="item.type === 'error' || item.type === 'warning' ? 'alert' : 'status'"
        >
          <span class="wh-toast__icon" aria-hidden="true">{{ iconMap[item.type] }}</span>
          <div class="wh-toast__body">
            <p v-if="item.title" class="wh-toast__title">{{ item.title }}</p>
            <p class="wh-toast__message">{{ item.message }}</p>
          </div>
          <button
            class="wh-toast__close"
            type="button"
            aria-label="关闭提示"
            @click="dismissToast(item.id)"
          >
            ×
          </button>
        </div>
      </TransitionGroup>
    </div>
  </Teleport>
</template>

<style scoped>
.wh-toast-host {
  position: fixed;
  top: calc(var(--header-h) + var(--space-4));
  right: var(--space-4);
  z-index: var(--z-toast);
  display: flex;
  flex-direction: column;
  gap: var(--space-2);
  width: min(380px, calc(100vw - 32px));
  pointer-events: none;
}

.wh-toast {
  display: grid;
  grid-template-columns: 22px minmax(0, 1fr) 24px;
  gap: 10px;
  align-items: start;
  padding: 12px 12px 12px 14px;
  border: 1px solid var(--ink);
  border-left: 3px solid var(--acid);
  background: var(--ink);
  color: var(--bone);
  box-shadow: var(--shadow-lg);
  pointer-events: auto;
}

.wh-toast--error {
  border-left-color: var(--verm);
}

.wh-toast--warning {
  border-left-color: var(--verm-deep);
}

.wh-toast--info {
  border-left-color: var(--violet);
}

.wh-toast__icon {
  display: grid;
  place-items: center;
  width: 20px;
  height: 20px;
  margin-top: 1px;
  border-radius: 50%;
  background: var(--acid);
  color: var(--ink);
  font-family: var(--mono);
  font-size: 12px;
  font-weight: var(--weight-bold);
  line-height: 1;
}

.wh-toast--error .wh-toast__icon {
  background: var(--verm);
  color: var(--bone);
}

.wh-toast--warning .wh-toast__icon {
  background: var(--verm-deep);
  color: var(--bone);
}

.wh-toast--info .wh-toast__icon {
  background: var(--violet);
  color: var(--bone);
}

.wh-toast__body {
  min-width: 0;
}

.wh-toast__title {
  margin: 0 0 2px;
  font-size: var(--text-sm);
  font-weight: var(--weight-semibold);
  line-height: 1.4;
}

.wh-toast__message {
  margin: 0;
  color: rgb(242 237 223 / 78%);
  font-size: var(--text-sm);
  line-height: 1.55;
  overflow-wrap: anywhere;
}

.wh-toast__close {
  display: grid;
  place-items: center;
  width: 24px;
  height: 24px;
  color: rgb(242 237 223 / 48%);
  font-size: 18px;
  line-height: 1;
}

.wh-toast__close:hover {
  color: var(--acid);
}

.wh-toast-enter-active,
.wh-toast-leave-active,
.wh-toast-move {
  transition:
    opacity var(--dur) var(--ease),
    transform var(--dur) var(--ease);
}

.wh-toast-enter-from,
.wh-toast-leave-to {
  opacity: 0;
  transform: translateX(16px);
}
</style>
