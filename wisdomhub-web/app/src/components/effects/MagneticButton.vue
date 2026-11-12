<script setup lang="ts">
import { ref } from 'vue'

const props = withDefaults(
  defineProps<{
    strength?: number
    tag?: string
  }>(),
  {
    strength: 0.3,
    tag: 'button',
  },
)

const buttonRef = ref<HTMLElement | null>(null)
const transform = ref('translate(0, 0)')
const isHovering = ref(false)

function prefersReducedMotion(): boolean {
  return (
    typeof window !== 'undefined' &&
    window.matchMedia('(prefers-reduced-motion: reduce)').matches
  )
}

function handleMouseMove(e: MouseEvent): void {
  const button = buttonRef.value
  if (!button || prefersReducedMotion()) return

  const rect = button.getBoundingClientRect()
  const centerX = rect.left + rect.width / 2
  const centerY = rect.top + rect.height / 2

  const deltaX = (e.clientX - centerX) * props.strength
  const deltaY = (e.clientY - centerY) * props.strength

  transform.value = `translate(${deltaX}px, ${deltaY}px)`
}

function handleMouseEnter(): void {
  isHovering.value = true
}

function handleMouseLeave(): void {
  isHovering.value = false
  transform.value = 'translate(0, 0)'
}
</script>

<template>
  <component
    :is="tag"
    ref="buttonRef"
    class="magnetic-btn"
    :class="{ 'is-hovering': isHovering }"
    :style="{ transform }"
    @mousemove="handleMouseMove"
    @mouseenter="handleMouseEnter"
    @mouseleave="handleMouseLeave"
  >
    <slot />
  </component>
</template>

<style scoped>
.magnetic-btn {
  position: relative;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  transition: transform 300ms cubic-bezier(0.2, 0.7, 0.2, 1);
  will-change: transform;
  cursor: pointer;
}

.magnetic-btn.is-hovering {
  transition-duration: 150ms;
}
</style>
