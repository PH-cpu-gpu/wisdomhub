<script setup lang="ts">
import { computed, ref, useId } from 'vue'

const props = withDefaults(
  defineProps<{
    modelValue?: string
    type?: string
    placeholder?: string
    disabled?: boolean
    readonly?: boolean
    clearable?: boolean
    invalid?: boolean
    size?: 'sm' | 'md' | 'lg'
    id?: string
    name?: string
    autocomplete?: string
    maxlength?: number
    error?: string
    hint?: string
  }>(),
  {
    modelValue: '',
    type: 'text',
    size: 'md',
    clearable: false,
    disabled: false,
    readonly: false,
    invalid: false,
  },
)

const emit = defineEmits<{
  'update:modelValue': [value: string]
  clear: []
  focus: [event: FocusEvent]
  blur: [event: FocusEvent]
  enter: [event: KeyboardEvent]
}>()

const inputRef = ref<HTMLInputElement | null>(null)
const showPassword = ref(false)
const autoId = useId()
const inputId = computed(() => props.id ?? `wh-input-${autoId}`)
const messageId = computed(() => `wh-input-message-${autoId}`)
const actualType = computed(() =>
  props.type === 'password' && showPassword.value ? 'text' : props.type,
)
const hasMessage = computed(() => Boolean(props.error || props.hint))

const model = computed({
  get: () => props.modelValue ?? '',
  set: (value: string) => emit('update:modelValue', value),
})

function clear(): void {
  model.value = ''
  emit('clear')
  inputRef.value?.focus()
}

function togglePassword(): void {
  showPassword.value = !showPassword.value
  inputRef.value?.focus()
}
</script>

<template>
  <div class="wh-field">
    <div
      class="wh-input"
      :class="[`wh-input--${size}`, { 'is-invalid': invalid, 'is-disabled': disabled }]"
      @click="inputRef?.focus()"
    >
      <span v-if="$slots.prefix" class="wh-input__affix"><slot name="prefix" /></span>

      <input
        :id="inputId"
        ref="inputRef"
        v-model="model"
        class="wh-input__control"
        :type="actualType"
        :placeholder="placeholder"
        :disabled="disabled"
        :readonly="readonly"
        :name="name"
        :autocomplete="autocomplete"
        :maxlength="maxlength"
        :aria-invalid="invalid || undefined"
        :aria-describedby="hasMessage ? messageId : undefined"
        @focus="emit('focus', $event)"
        @blur="emit('blur', $event)"
        @keydown.enter="emit('enter', $event)"
      />

      <button
        v-if="clearable && model && !disabled && !readonly"
        class="wh-input__action"
        type="button"
        aria-label="清空"
        @click.stop="clear"
      >
        ×
      </button>

      <button
        v-if="type === 'password' && !disabled"
        class="wh-input__action wh-input__action--text"
        type="button"
        :aria-label="showPassword ? '隐藏密码' : '显示密码'"
        @click.stop="togglePassword"
      >
        {{ showPassword ? '隐藏' : '显示' }}
      </button>

      <span v-if="$slots.suffix" class="wh-input__affix"><slot name="suffix" /></span>
    </div>

    <p v-if="error" :id="messageId" class="wh-field__message wh-field__message--error">
      {{ error }}
    </p>
    <p v-else-if="hint" :id="messageId" class="wh-field__message">{{ hint }}</p>
  </div>
</template>

<style scoped>
.wh-field {
  display: grid;
  gap: 6px;
}

.wh-input {
  display: flex;
  align-items: center;
  gap: 8px;
  min-height: 40px;
  padding-inline: 12px;
  border: 1px solid var(--line-strong);
  background: var(--surface-strong);
  color: var(--text);
  cursor: text;
  transition:
    border-color var(--dur) var(--ease),
    box-shadow var(--dur) var(--ease),
    background var(--dur) var(--ease);
}

.wh-input:hover:not(.is-disabled) {
  border-color: var(--ink);
}

.wh-input:focus-within {
  border-color: var(--ink);
  box-shadow: 0 0 0 3px rgb(11 11 14 / 10%);
}

.wh-input.is-invalid {
  border-color: var(--verm);
}

.wh-input.is-invalid:focus-within {
  box-shadow: 0 0 0 3px rgb(255 59 31 / 18%);
}

.wh-input.is-disabled {
  background: var(--bone);
  opacity: 0.55;
  cursor: not-allowed;
}

.wh-input--sm {
  min-height: 34px;
  padding-inline: 10px;
}

.wh-input--lg {
  min-height: 48px;
  padding-inline: 14px;
}

.wh-input__control {
  flex: 1;
  min-width: 0;
  height: 100%;
  padding: 0;
  border: 0;
  background: transparent;
  color: inherit;
  font: inherit;
  font-size: var(--text-sm);
  outline: none;
}

.wh-input--lg .wh-input__control {
  font-size: var(--text-md);
}

.wh-input__control::placeholder {
  color: color-mix(in srgb, var(--text-muted) 72%, transparent);
}

.wh-input__control:focus-visible {
  outline: none;
}

.wh-input__affix {
  display: inline-flex;
  align-items: center;
  color: var(--text-muted);
  font-family: var(--mono);
  font-size: var(--text-2xs);
  white-space: nowrap;
}

.wh-input__action {
  display: grid;
  place-items: center;
  width: 24px;
  height: 24px;
  color: var(--text-muted);
  font-size: 18px;
  line-height: 1;
}

.wh-input__action:hover {
  color: var(--ink);
}

.wh-input__action--text {
  width: auto;
  padding-inline: 2px;
  font-family: var(--mono);
  font-size: var(--text-2xs);
}

.wh-field__message {
  color: var(--text-muted);
  font-size: var(--text-xs);
  line-height: 1.5;
}

.wh-field__message--error {
  color: var(--verm-deep);
}
</style>
