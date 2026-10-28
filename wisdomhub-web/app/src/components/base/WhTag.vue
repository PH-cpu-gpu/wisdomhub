<script setup lang="ts">
withDefaults(
  defineProps<{
    variant?: 'default' | 'acid' | 'verm' | 'violet' | 'outline'
    size?: 'sm' | 'md'
    closable?: boolean
  }>(),
  {
    variant: 'default',
    size: 'md',
    closable: false,
  },
)

const emit = defineEmits<{
  close: []
}>()
</script>

<template>
  <span class="wh-tag" :class="[`wh-tag--${variant}`, `wh-tag--${size}`]">
    <slot />
    <button
      v-if="closable"
      class="wh-tag__close"
      type="button"
      aria-label="移除标签"
      @click.stop="emit('close')"
    >
      ×
    </button>
  </span>
</template>

<style scoped>
.wh-tag {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  border: 1px solid var(--line-strong);
  background: transparent;
  color: var(--text);
  font-family: var(--sans);
  font-size: var(--text-xs);
  font-weight: var(--weight-semibold);
  line-height: 1;
  letter-spacing: 0.02em;
  white-space: nowrap;
}

.wh-tag--sm {
  padding: 3px 6px;
  font-size: var(--text-2xs);
}

.wh-tag--md {
  padding: 4px 9px;
}

.wh-tag--default {
  background: var(--surface-strong);
}

.wh-tag--acid {
  border-color: var(--ink);
  background: var(--acid);
  color: var(--ink);
}

.wh-tag--verm {
  border-color: var(--verm-deep);
  background: var(--verm);
  color: var(--bone);
}

.wh-tag--violet {
  border-color: var(--violet);
  background: var(--violet);
  color: var(--bone);
}

.wh-tag--outline {
  border-color: var(--ink);
  background: transparent;
}

.wh-tag__close {
  display: grid;
  place-items: center;
  width: 14px;
  height: 14px;
  margin-right: -2px;
  color: currentColor;
  font-size: 14px;
  line-height: 1;
  opacity: 0.65;
}

.wh-tag__close:hover {
  opacity: 1;
}
</style>
