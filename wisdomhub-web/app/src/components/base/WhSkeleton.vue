<script setup lang="ts">
import { computed } from 'vue'

const props = withDefaults(
  defineProps<{
    width?: string | number
    height?: string | number
    circle?: boolean
    lines?: number
    animated?: boolean
  }>(),
  {
    width: '100%',
    circle: false,
    lines: 1,
    animated: true,
  },
)

const lineCount = computed(() => (props.circle ? 1 : Math.max(1, props.lines)))

function toSize(value: string | number | undefined, fallback: string): string {
  if (value === undefined) return fallback
  return typeof value === 'number' ? `${value}px` : value
}

function barStyle(index: number): Record<string, string> {
  if (props.circle) {
    const size = toSize(props.width, '40px')
    return { width: size, height: toSize(props.height, size), borderRadius: '50%' }
  }

  const isLast = index === lineCount.value - 1
  return {
    width: isLast && lineCount.value > 1 ? '72%' : toSize(props.width, '100%'),
    height: toSize(props.height, '14px'),
    borderRadius: 'var(--radius-xs)',
  }
}
</script>

<template>
  <div class="wh-skeleton" :class="{ 'is-animated': animated }" role="status" aria-label="加载中">
    <span
      v-for="index in lineCount"
      :key="index"
      class="wh-skeleton__bar"
      :style="barStyle(index - 1)"
      aria-hidden="true"
    ></span>
    <span class="wh-sr-only">加载中</span>
  </div>
</template>

<style scoped>
.wh-skeleton {
  display: grid;
  gap: 8px;
  width: 100%;
}

.wh-skeleton__bar {
  display: block;
  justify-self: start;
  background: linear-gradient(
    90deg,
    var(--bone-2) 25%,
    var(--surface-strong) 37%,
    var(--bone-2) 63%
  );
  background-size: 400% 100%;
}

.wh-skeleton.is-animated .wh-skeleton__bar {
  animation: wh-skeleton-shimmer 1.4s ease infinite;
}

@keyframes wh-skeleton-shimmer {
  0% {
    background-position: 100% 50%;
  }

  100% {
    background-position: 0 50%;
  }
}
</style>
