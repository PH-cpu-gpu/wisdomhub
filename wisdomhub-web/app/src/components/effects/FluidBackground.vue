<script setup lang="ts">
import { computed } from 'vue'

const props = defineProps<{
  colors?: string[]
  speed?: number
  blur?: number
  opacity?: number
}>()

const defaultColors = ['#c9ff2e', '#ff3b1f', '#3b1dff', '#c9ff2e']

const gradientStyle = computed(() => ({
  background: `linear-gradient(-45deg, ${(props.colors ?? defaultColors).join(', ')})`,
  backgroundSize: '400% 400%',
  animation: `fluidGradient ${props.speed ?? 20}s ease infinite`,
  filter: `blur(${props.blur ?? 80}px)`,
  opacity: props.opacity ?? 0.4,
}))
</script>

<template>
  <div class="fluid-bg" :style="gradientStyle" aria-hidden="true" />
</template>

<style scoped>
.fluid-bg {
  position: absolute;
  inset: -50%;
  width: 200%;
  height: 200%;
  pointer-events: none;
  z-index: 0;
}

@keyframes fluidGradient {
  0% { background-position: 0% 50%; }
  50% { background-position: 100% 50%; }
  100% { background-position: 0% 50%; }
}

@media (prefers-reduced-motion: reduce) {
  .fluid-bg {
    animation: none !important;
  }
}
</style>
