<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'

const props = withDefaults(
  defineProps<{
    value: number
    duration?: number
    formatter?: (value: number) => string
    prefix?: string
    suffix?: string
  }>(),
  {
    duration: 1500,
    prefix: '',
    suffix: '',
  },
)

const displayValue = ref(0)
const hasAnimated = ref(false)
const elementRef = ref<HTMLElement | null>(null)

const formattedValue = computed(() => {
  const formatted = props.formatter
    ? props.formatter(displayValue.value)
    : Math.round(displayValue.value).toLocaleString()
  return `${props.prefix}${formatted}${props.suffix}`
})

function animateValue(start: number, end: number): void {
  const startTime = performance.now()
  const diff = end - start

  function update(currentTime: number): void {
    const elapsed = currentTime - startTime
    const progress = Math.min(elapsed / props.duration, 1)
    const eased = 1 - Math.pow(1 - progress, 3)

    displayValue.value = start + diff * eased

    if (progress < 1) {
      requestAnimationFrame(update)
    }
  }

  requestAnimationFrame(update)
}

onMounted(() => {
  const el = elementRef.value
  if (!el) return

  const observer = new IntersectionObserver(
    (entries) => {
      for (const entry of entries) {
        if (entry.isIntersecting && !hasAnimated.value) {
          hasAnimated.value = true
          animateValue(0, props.value)
          observer.unobserve(el)
        }
      }
    },
    { threshold: 0.3 },
  )

  observer.observe(el)
})

watch(() => props.value, (newValue, oldValue) => {
  if (hasAnimated.value) {
    animateValue(oldValue ?? 0, newValue)
  }
})
</script>

<template>
  <span ref="elementRef" class="animated-counter">{{ formattedValue }}</span>
</template>

<style scoped>
.animated-counter {
  font-variant-numeric: tabular-nums;
  display: inline-block;
}
</style>
