<script setup lang="ts">
import { computed } from 'vue'

const props = withDefaults(
  defineProps<{
    count?: number
    max?: number
    dot?: boolean
    variant?: 'acid' | 'verm' | 'ink'
    showZero?: boolean
  }>(),
  {
    count: 0,
    max: 99,
    dot: false,
    variant: 'verm',
    showZero: false,
  },
)

const visible = computed(() => props.dot || props.count > 0 || props.showZero)
const text = computed(() => (props.count > props.max ? `${props.max}+` : String(props.count)))
const label = computed(() => (props.dot ? '有新内容' : `${props.count} 条`))
</script>

<template>
  <span class="wh-badge">
    <slot />
    <span
      v-if="visible"
      class="wh-badge__bubble"
      :class="[`wh-badge__bubble--${variant}`, { 'is-dot': dot }]"
      :aria-label="label"
    >
      <template v-if="!dot">{{ text }}</template>
    </span>
  </span>
</template>

<style scoped>
.wh-badge {
  position: relative;
  display: inline-flex;
}

.wh-badge__bubble {
  position: absolute;
  top: -6px;
  right: -8px;
  display: grid;
  place-items: center;
  min-width: 18px;
  height: 18px;
  padding: 0 5px;
  border: 2px solid var(--bone);
  border-radius: var(--radius-pill);
  font-family: var(--mono);
  font-size: 10px;
  font-weight: var(--weight-bold);
  line-height: 1;
}

.wh-badge__bubble--acid {
  background: var(--acid);
  color: var(--ink);
}

.wh-badge__bubble--verm {
  background: var(--verm);
  color: var(--bone);
}

.wh-badge__bubble--ink {
  background: var(--ink);
  color: var(--acid);
}

.wh-badge__bubble.is-dot {
  top: -4px;
  right: -4px;
  width: 9px;
  min-width: 9px;
  height: 9px;
  padding: 0;
  border-width: 1.5px;
}
</style>
