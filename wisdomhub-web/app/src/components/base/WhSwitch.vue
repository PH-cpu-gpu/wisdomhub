<script setup lang="ts">
const props = withDefaults(
  defineProps<{
    modelValue?: boolean
    disabled?: boolean
    label?: string
    size?: 'sm' | 'md'
  }>(),
  {
    modelValue: false,
    disabled: false,
    size: 'md',
  },
)

const emit = defineEmits<{
  'update:modelValue': [value: boolean]
  change: [value: boolean]
}>()

function toggle(): void {
  emit('update:modelValue', !props.modelValue)
  emit('change', !props.modelValue)
}
</script>

<template>
  <button
    class="wh-switch"
    :class="[`wh-switch--${size}`, { 'is-on': modelValue, 'is-disabled': disabled }]"
    type="button"
    role="switch"
    :aria-checked="modelValue"
    :disabled="disabled"
    @click="toggle"
  >
    <span class="wh-switch__track" aria-hidden="true">
      <span class="wh-switch__thumb"></span>
    </span>
    <span v-if="label || $slots.default" class="wh-switch__label">
      <slot>{{ label }}</slot>
    </span>
  </button>
</template>

<style scoped>
.wh-switch {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  cursor: pointer;
}

.wh-switch.is-disabled {
  opacity: 0.55;
  cursor: not-allowed;
}

.wh-switch__track {
  position: relative;
  display: block;
  width: 42px;
  height: 24px;
  border: 1px solid var(--ink);
  background: var(--bone-2);
  transition: background var(--dur) var(--ease);
}

.wh-switch__thumb {
  position: absolute;
  top: 3px;
  left: 3px;
  width: 16px;
  height: 16px;
  background: var(--ink);
  transition: transform var(--dur) var(--ease);
}

.wh-switch.is-on .wh-switch__track {
  background: var(--acid);
}

.wh-switch.is-on .wh-switch__thumb {
  transform: translateX(18px);
}

.wh-switch--sm .wh-switch__track {
  width: 34px;
  height: 20px;
}

.wh-switch--sm .wh-switch__thumb {
  top: 3px;
  left: 3px;
  width: 12px;
  height: 12px;
}

.wh-switch--sm.is-on .wh-switch__thumb {
  transform: translateX(14px);
}

.wh-switch__label {
  color: var(--text);
  font-size: var(--text-sm);
  line-height: 1.5;
}
</style>
