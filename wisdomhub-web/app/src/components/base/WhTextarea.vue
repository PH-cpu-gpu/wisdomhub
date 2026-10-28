<script setup lang="ts">
import { computed, nextTick, onMounted, ref, useId, watch } from 'vue'

const props = withDefaults(
  defineProps<{
    modelValue?: string
    placeholder?: string
    rows?: number
    disabled?: boolean
    readonly?: boolean
    invalid?: boolean
    maxlength?: number
    showCount?: boolean
    autoResize?: boolean
    error?: string
    hint?: string
    id?: string
    name?: string
  }>(),
  {
    modelValue: '',
    rows: 4,
    disabled: false,
    readonly: false,
    invalid: false,
    showCount: false,
    autoResize: false,
  },
)

const emit = defineEmits<{
  'update:modelValue': [value: string]
  focus: [event: FocusEvent]
  blur: [event: FocusEvent]
}>()

const textareaRef = ref<HTMLTextAreaElement | null>(null)
const autoId = useId()
const textareaId = computed(() => props.id ?? `wh-textarea-${autoId}`)
const messageId = computed(() => `wh-textarea-message-${autoId}`)
const hasMeta = computed(() =>
  Boolean(props.error || props.hint || (props.showCount && props.maxlength)),
)

const model = computed({
  get: () => props.modelValue ?? '',
  set: (value: string) => emit('update:modelValue', value),
})

function resize(): void {
  if (!props.autoResize || !textareaRef.value) return
  const element = textareaRef.value
  element.style.height = 'auto'
  element.style.height = `${element.scrollHeight}px`
}

function onInput(): void {
  void nextTick(resize)
}

onMounted(resize)
watch(
  () => props.modelValue,
  () => {
    void nextTick(resize)
  },
)
</script>

<template>
  <div class="wh-field">
    <div
      class="wh-textarea"
      :class="{ 'is-invalid': invalid, 'is-disabled': disabled, 'is-auto': autoResize }"
    >
      <textarea
        :id="textareaId"
        ref="textareaRef"
        v-model="model"
        class="wh-textarea__control"
        :rows="rows"
        :placeholder="placeholder"
        :disabled="disabled"
        :readonly="readonly"
        :maxlength="maxlength"
        :name="name"
        :aria-invalid="invalid || undefined"
        :aria-describedby="hasMeta ? messageId : undefined"
        @input="onInput"
        @focus="emit('focus', $event)"
        @blur="emit('blur', $event)"
      ></textarea>
    </div>

    <div v-if="hasMeta" :id="messageId" class="wh-textarea__meta">
      <span v-if="error" class="wh-field__message wh-field__message--error">{{ error }}</span>
      <span v-else-if="hint" class="wh-field__message">{{ hint }}</span>
      <span v-if="showCount && maxlength" class="wh-textarea__count">
        {{ model.length }} / {{ maxlength }}
      </span>
    </div>
  </div>
</template>

<style scoped>
.wh-field {
  display: grid;
  gap: 6px;
}

.wh-textarea {
  display: flex;
  padding: 10px 12px;
  border: 1px solid var(--line-strong);
  background: var(--surface-strong);
  transition:
    border-color var(--dur) var(--ease),
    box-shadow var(--dur) var(--ease),
    background var(--dur) var(--ease);
}

.wh-textarea:hover:not(.is-disabled) {
  border-color: var(--ink);
}

.wh-textarea:focus-within {
  border-color: var(--ink);
  box-shadow: 0 0 0 3px rgb(11 11 14 / 10%);
}

.wh-textarea.is-invalid {
  border-color: var(--verm);
}

.wh-textarea.is-invalid:focus-within {
  box-shadow: 0 0 0 3px rgb(255 59 31 / 18%);
}

.wh-textarea.is-disabled {
  background: var(--bone);
  opacity: 0.55;
}

.wh-textarea__control {
  width: 100%;
  min-width: 0;
  padding: 0;
  border: 0;
  background: transparent;
  color: inherit;
  font: inherit;
  font-size: var(--text-sm);
  line-height: 1.7;
  outline: none;
  resize: vertical;
}

.wh-textarea.is-auto .wh-textarea__control {
  resize: none;
  overflow: hidden;
}

.wh-textarea__control::placeholder {
  color: color-mix(in srgb, var(--text-muted) 72%, transparent);
}

.wh-textarea__control:focus-visible {
  outline: none;
}

.wh-textarea__meta {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: var(--space-3);
}

.wh-textarea__count {
  margin-left: auto;
  color: var(--text-muted);
  font-family: var(--mono);
  font-size: var(--text-2xs);
  white-space: nowrap;
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
