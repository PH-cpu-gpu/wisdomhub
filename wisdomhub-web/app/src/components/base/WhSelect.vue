<script setup lang="ts">
import { computed, nextTick, ref, useId } from 'vue'

import { useClickOutside } from '@/composables/useClickOutside'

export interface WhSelectOption {
  label: string
  value: string | number
  disabled?: boolean
}

const props = withDefaults(
  defineProps<{
    modelValue: string | number | null
    options: WhSelectOption[]
    placeholder?: string
    disabled?: boolean
    clearable?: boolean
    invalid?: boolean
    size?: 'sm' | 'md' | 'lg'
    id?: string
    error?: string
    hint?: string
  }>(),
  {
    options: () => [],
    placeholder: '请选择',
    disabled: false,
    clearable: false,
    invalid: false,
    size: 'md',
  },
)

const emit = defineEmits<{
  'update:modelValue': [value: string | number | null]
  change: [option: WhSelectOption | null]
}>()

const autoId = useId()
const selectId = computed(() => props.id ?? `wh-select-${autoId}`)
const listboxId = computed(() => `wh-select-listbox-${autoId}`)
const messageId = computed(() => `wh-select-message-${autoId}`)

const rootRef = ref<HTMLElement | null>(null)
const triggerRef = ref<HTMLButtonElement | null>(null)
const open = ref(false)
const activeIndex = ref(-1)

const selected = computed(
  () => props.options.find((option) => option.value === props.modelValue) ?? null,
)
const selectedLabel = computed(() => selected.value?.label ?? props.placeholder)
const hasValue = computed(() => props.modelValue !== null && props.modelValue !== undefined)
const hasMessage = computed(() => Boolean(props.error || props.hint))

function optionId(index: number): string {
  return `${listboxId.value}-option-${index}`
}

function enabledIndexes(): number[] {
  return props.options
    .map((option, index) => (option.disabled ? -1 : index))
    .filter((index) => index >= 0)
}

function scrollActiveIntoView(): void {
  if (activeIndex.value < 0) return
  document.getElementById(optionId(activeIndex.value))?.scrollIntoView({ block: 'nearest' })
}

function openList(): void {
  if (props.disabled || open.value) return
  open.value = true
  const current = props.options.findIndex(
    (option) => option.value === props.modelValue && !option.disabled,
  )
  activeIndex.value = current >= 0 ? current : (enabledIndexes()[0] ?? -1)
  void nextTick(scrollActiveIntoView)
}

function closeList(): void {
  if (!open.value) return
  open.value = false
  activeIndex.value = -1
}

function toggle(): void {
  if (open.value) closeList()
  else openList()
}

function moveActive(step: 1 | -1): void {
  const indexes = enabledIndexes()
  if (!indexes.length) return
  const current = indexes.indexOf(activeIndex.value)
  const next =
    current === -1
      ? step > 0
        ? 0
        : indexes.length - 1
      : (current + step + indexes.length) % indexes.length
  activeIndex.value = indexes[next] ?? -1
  void nextTick(scrollActiveIntoView)
}

function selectOption(option: WhSelectOption): void {
  if (option.disabled) return
  emit('update:modelValue', option.value)
  emit('change', option)
  closeList()
  void nextTick(() => triggerRef.value?.focus())
}

function clear(): void {
  emit('update:modelValue', null)
  emit('change', null)
  closeList()
  void nextTick(() => triggerRef.value?.focus())
}

function onKeydown(event: KeyboardEvent): void {
  if (props.disabled) return

  switch (event.key) {
    case 'ArrowDown':
      event.preventDefault()
      if (!open.value) openList()
      else moveActive(1)
      break
    case 'ArrowUp':
      event.preventDefault()
      if (!open.value) openList()
      else moveActive(-1)
      break
    case 'Home':
      if (!open.value) return
      event.preventDefault()
      activeIndex.value = enabledIndexes()[0] ?? -1
      void nextTick(scrollActiveIntoView)
      break
    case 'End':
      if (!open.value) return
      event.preventDefault()
      activeIndex.value = enabledIndexes().at(-1) ?? -1
      void nextTick(scrollActiveIntoView)
      break
    case 'Enter':
    case ' ':
      event.preventDefault()
      if (!open.value) {
        openList()
      } else {
        const option = props.options[activeIndex.value]
        if (option) selectOption(option)
      }
      break
    case 'Escape':
      if (!open.value) return
      event.preventDefault()
      closeList()
      break
    case 'Tab':
      closeList()
      break
  }
}

useClickOutside(rootRef, closeList)
</script>

<template>
  <div class="wh-field">
    <div
      ref="rootRef"
      class="wh-select"
      :class="[
        `wh-select--${size}`,
        { 'is-open': open, 'is-invalid': invalid, 'is-disabled': disabled },
      ]"
    >
      <button
        :id="selectId"
        ref="triggerRef"
        class="wh-select__trigger"
        type="button"
        role="combobox"
        aria-haspopup="listbox"
        :aria-expanded="open"
        :aria-controls="listboxId"
        :aria-activedescendant="open && activeIndex >= 0 ? optionId(activeIndex) : undefined"
        :disabled="disabled"
        :aria-invalid="invalid || undefined"
        :aria-describedby="hasMessage ? messageId : undefined"
        @click="toggle"
        @keydown="onKeydown"
      >
        <span class="wh-select__value" :class="{ 'is-placeholder': !selected }">
          {{ selectedLabel }}
        </span>
        <svg
          class="wh-select__chevron"
          :class="{ 'is-open': open }"
          viewBox="0 0 24 24"
          fill="none"
        >
          <path
            d="m6 9 6 6 6-6"
            stroke="currentColor"
            stroke-width="1.8"
            stroke-linecap="round"
            stroke-linejoin="round"
          />
        </svg>
      </button>

      <button
        v-if="clearable && hasValue && !disabled"
        class="wh-select__clear"
        type="button"
        aria-label="清空"
        @click.stop="clear"
      >
        ×
      </button>

      <Transition name="wh-select">
        <ul
          v-if="open"
          :id="listboxId"
          class="wh-select__listbox"
          role="listbox"
          :aria-labelledby="selectId"
        >
          <li
            v-for="(option, index) in options"
            :id="optionId(index)"
            :key="option.value"
            class="wh-select__option"
            :class="{
              'is-active': index === activeIndex,
              'is-selected': option.value === modelValue,
              'is-disabled': option.disabled,
            }"
            role="option"
            :aria-selected="option.value === modelValue"
            :aria-disabled="option.disabled || undefined"
            @mouseenter="activeIndex = option.disabled ? activeIndex : index"
            @click="selectOption(option)"
          >
            <span>{{ option.label }}</span>
            <span v-if="option.value === modelValue" class="wh-select__check" aria-hidden="true">
              ✓
            </span>
          </li>
        </ul>
      </Transition>
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

.wh-select {
  position: relative;
}

.wh-select__trigger {
  display: flex;
  align-items: center;
  width: 100%;
  min-height: 40px;
  padding: 0 56px 0 12px;
  border: 1px solid var(--line-strong);
  background: var(--surface-strong);
  color: var(--text);
  text-align: left;
  transition:
    border-color var(--dur) var(--ease),
    box-shadow var(--dur) var(--ease),
    background var(--dur) var(--ease);
}

.wh-select__trigger:hover:not(:disabled) {
  border-color: var(--ink);
}

.wh-select.is-open .wh-select__trigger,
.wh-select__trigger:focus-visible {
  border-color: var(--ink);
  box-shadow: 0 0 0 3px rgb(11 11 14 / 10%);
  outline: none;
}

.wh-select.is-invalid .wh-select__trigger {
  border-color: var(--verm);
}

.wh-select.is-invalid .wh-select__trigger:focus-visible {
  box-shadow: 0 0 0 3px rgb(255 59 31 / 18%);
}

.wh-select.is-disabled .wh-select__trigger {
  background: var(--bone);
  opacity: 0.55;
  cursor: not-allowed;
}

.wh-select__value {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  font-size: var(--text-sm);
  text-overflow: ellipsis;
  white-space: nowrap;
}

.wh-select__value.is-placeholder {
  color: color-mix(in srgb, var(--text-muted) 72%, transparent);
}

.wh-select__chevron {
  position: absolute;
  top: 50%;
  right: 12px;
  width: 16px;
  height: 16px;
  color: var(--text-muted);
  transform: translateY(-50%);
  transition: transform var(--dur) var(--ease);
}

.wh-select__chevron.is-open {
  transform: translateY(-50%) rotate(180deg);
}

.wh-select__clear {
  position: absolute;
  top: 50%;
  right: 32px;
  display: grid;
  place-items: center;
  width: 20px;
  height: 20px;
  color: var(--text-muted);
  font-size: 16px;
  line-height: 1;
  transform: translateY(-50%);
}

.wh-select__clear:hover {
  color: var(--verm-deep);
}

.wh-select__listbox {
  position: absolute;
  top: calc(100% + 6px);
  right: 0;
  left: 0;
  z-index: var(--z-dropdown);
  max-height: 260px;
  margin: 0;
  padding: 4px;
  overflow: auto;
  border: 1px solid var(--ink);
  background: var(--surface-strong);
  box-shadow: var(--shadow-md);
  list-style: none;
}

.wh-select__option {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  padding: 8px 10px;
  color: var(--text);
  font-size: var(--text-sm);
  cursor: pointer;
}

.wh-select__option.is-active {
  background: var(--acid);
  color: var(--ink);
}

.wh-select__option.is-selected {
  font-weight: var(--weight-semibold);
}

.wh-select__option.is-disabled {
  opacity: 0.4;
  cursor: not-allowed;
}

.wh-select__check {
  font-family: var(--mono);
  font-size: var(--text-xs);
}

.wh-select--sm .wh-select__trigger {
  min-height: 34px;
  padding-left: 10px;
}

.wh-select--lg .wh-select__trigger {
  min-height: 48px;
  padding-left: 14px;
}

.wh-select--lg .wh-select__value {
  font-size: var(--text-md);
}

.wh-field__message {
  color: var(--text-muted);
  font-size: var(--text-xs);
  line-height: 1.5;
}

.wh-field__message--error {
  color: var(--verm-deep);
}

.wh-select-enter-active,
.wh-select-leave-active {
  transition:
    opacity var(--dur) var(--ease),
    transform var(--dur) var(--ease);
}

.wh-select-enter-from,
.wh-select-leave-to {
  opacity: 0;
  transform: translateY(-4px);
}
</style>
