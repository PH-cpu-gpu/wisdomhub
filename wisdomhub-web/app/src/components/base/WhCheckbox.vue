<script setup lang="ts">
import { computed, onMounted, ref, useId, watch } from 'vue'

const props = withDefaults(
  defineProps<{
    modelValue?: boolean
    indeterminate?: boolean
    disabled?: boolean
    label?: string
    id?: string
    name?: string
  }>(),
  {
    modelValue: false,
    indeterminate: false,
    disabled: false,
  },
)

const emit = defineEmits<{
  'update:modelValue': [value: boolean]
  change: [value: boolean]
}>()

const inputRef = ref<HTMLInputElement | null>(null)
const autoId = useId()
const inputId = computed(() => props.id ?? `wh-checkbox-${autoId}`)

function syncIndeterminate(): void {
  if (inputRef.value) inputRef.value.indeterminate = props.indeterminate
}

function onChange(event: Event): void {
  const checked = (event.target as HTMLInputElement).checked
  emit('update:modelValue', checked)
  emit('change', checked)
}

onMounted(syncIndeterminate)
watch(
  () => props.indeterminate,
  () => {
    syncIndeterminate()
  },
)
</script>

<template>
  <label class="wh-checkbox" :class="{ 'is-disabled': disabled }" :for="inputId">
    <input
      :id="inputId"
      ref="inputRef"
      class="wh-checkbox__native"
      type="checkbox"
      :name="name"
      :checked="modelValue"
      :disabled="disabled"
      @change="onChange"
    />
    <span class="wh-checkbox__box" aria-hidden="true">
      <svg v-if="modelValue && !indeterminate" viewBox="0 0 16 16" fill="none">
        <path
          d="m3 8.5 3 3 7-7"
          stroke="currentColor"
          stroke-width="2"
          stroke-linecap="round"
          stroke-linejoin="round"
        />
      </svg>
      <span v-else-if="indeterminate" class="wh-checkbox__dash"></span>
    </span>
    <span v-if="label || $slots.default" class="wh-checkbox__label">
      <slot>{{ label }}</slot>
    </span>
  </label>
</template>

<style scoped>
.wh-checkbox {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  cursor: pointer;
}

.wh-checkbox.is-disabled {
  opacity: 0.55;
  cursor: not-allowed;
}

.wh-checkbox__native {
  position: absolute;
  width: 1px;
  height: 1px;
  margin: -1px;
  overflow: hidden;
  clip: rect(0 0 0 0);
  white-space: nowrap;
  border: 0;
  opacity: 0;
}

.wh-checkbox__box {
  display: grid;
  flex: 0 0 auto;
  place-items: center;
  width: 18px;
  height: 18px;
  border: 1px solid var(--ink);
  background: var(--surface-strong);
  color: var(--ink);
  transition:
    background var(--dur) var(--ease),
    border-color var(--dur) var(--ease);
}

.wh-checkbox__box svg {
  width: 14px;
  height: 14px;
}

.wh-checkbox__dash {
  width: 8px;
  height: 2px;
  background: currentColor;
}

.wh-checkbox__native:checked + .wh-checkbox__box,
.wh-checkbox__native:indeterminate + .wh-checkbox__box {
  background: var(--acid);
}

.wh-checkbox__native:focus-visible + .wh-checkbox__box {
  outline: 2px solid var(--focus);
  outline-offset: 2px;
}

.wh-checkbox__label {
  color: var(--text);
  font-size: var(--text-sm);
  line-height: 1.5;
}
</style>
