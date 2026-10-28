<script setup lang="ts">
withDefaults(
  defineProps<{
    variant?: 'primary' | 'secondary' | 'ghost' | 'danger' | 'text'
    size?: 'sm' | 'md' | 'lg'
    type?: 'button' | 'submit' | 'reset'
    disabled?: boolean
    loading?: boolean
    block?: boolean
    iconOnly?: boolean
  }>(),
  {
    variant: 'primary',
    size: 'md',
    type: 'button',
    disabled: false,
    loading: false,
    block: false,
    iconOnly: false,
  },
)
</script>

<template>
  <button
    class="wh-button"
    :class="[
      `wh-button--${variant}`,
      `wh-button--${size}`,
      { 'is-block': block, 'is-loading': loading, 'is-icon': iconOnly },
    ]"
    :type="type"
    :disabled="disabled || loading"
    :aria-busy="loading || undefined"
  >
    <span v-if="loading" class="wh-button__spinner" aria-hidden="true"></span>
    <span v-else-if="$slots.icon" class="wh-button__icon"><slot name="icon" /></span>
    <span v-if="$slots.default" class="wh-button__label"><slot /></span>
  </button>
</template>

<style scoped>
.wh-button {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  border: 1px solid transparent;
  font-family: var(--sans);
  font-size: var(--text-sm);
  font-weight: var(--weight-semibold);
  line-height: 1;
  letter-spacing: 0.02em;
  white-space: nowrap;
  transition:
    background var(--dur) var(--ease),
    color var(--dur) var(--ease),
    border-color var(--dur) var(--ease),
    box-shadow var(--dur) var(--ease),
    transform var(--dur) var(--ease);
}

.wh-button--sm {
  min-height: 32px;
  padding-inline: 12px;
  font-size: var(--text-xs);
}

.wh-button--md {
  min-height: 40px;
  padding-inline: 16px;
}

.wh-button--lg {
  min-height: 48px;
  padding-inline: 20px;
  font-size: var(--text-md);
}

.wh-button.is-block {
  width: 100%;
}

.wh-button.is-icon {
  width: 40px;
  padding-inline: 0;
}

.wh-button--sm.is-icon {
  width: 32px;
}

.wh-button--lg.is-icon {
  width: 48px;
}

.wh-button--primary {
  border-color: var(--ink);
  background: var(--acid);
  color: var(--ink);
}

.wh-button--primary:hover:not(:disabled) {
  background: var(--ink);
  color: var(--acid);
  box-shadow: var(--shadow-hard-sm);
  transform: translate(-1px, -1px);
}

.wh-button--secondary {
  border-color: var(--ink);
  background: var(--ink);
  color: var(--acid);
}

.wh-button--secondary:hover:not(:disabled) {
  background: var(--acid);
  color: var(--ink);
}

.wh-button--ghost {
  border-color: var(--line-strong);
  background: transparent;
  color: var(--ink);
}

.wh-button--ghost:hover:not(:disabled) {
  border-color: var(--ink);
  background: var(--surface);
}

.wh-button--danger {
  border-color: var(--verm-deep);
  background: var(--verm);
  color: var(--bone);
}

.wh-button--danger:hover:not(:disabled) {
  background: var(--verm-deep);
}

.wh-button--text {
  min-height: 32px;
  padding-inline: 6px;
  color: var(--ink);
}

.wh-button--text:hover:not(:disabled) {
  color: var(--verm-deep);
}

.wh-button:disabled {
  opacity: 0.55;
  cursor: not-allowed;
}

.wh-button__icon {
  display: inline-flex;
  align-items: center;
}

.wh-button__icon :deep(svg) {
  width: 16px;
  height: 16px;
}

.wh-button__spinner {
  width: 14px;
  height: 14px;
  border: 2px solid currentColor;
  border-top-color: transparent;
  border-radius: 50%;
  animation: wh-button-spin 0.7s linear infinite;
}

@keyframes wh-button-spin {
  to {
    transform: rotate(360deg);
  }
}
</style>
