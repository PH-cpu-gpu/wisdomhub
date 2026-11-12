<script setup lang="ts">
import { ref } from 'vue'

const props = withDefaults(
  defineProps<{
    maxTilt?: number
    scale?: number
    speed?: number
    glare?: boolean
  }>(),
  {
    maxTilt: 15,
    scale: 1.02,
    speed: 300,
    glare: true,
  },
)

const cardRef = ref<HTMLElement | null>(null)
const isHovering = ref(false)
const transform = ref('perspective(1000px) rotateX(0deg) rotateY(0deg) scale3d(1, 1, 1)')
const glareTransform = ref('translateX(-50%) translateY(-50%) rotate(0deg)')
const glareOpacity = ref(0)

function handleMouseMove(e: MouseEvent): void {
  const card = cardRef.value
  if (!card) return

  const rect = card.getBoundingClientRect()
  const x = e.clientX - rect.left
  const y = e.clientY - rect.top
  const centerX = rect.width / 2
  const centerY = rect.height / 2

  const rotateX = ((y - centerY) / centerY) * -props.maxTilt
  const rotateY = ((x - centerX) / centerX) * props.maxTilt

  transform.value = `perspective(1000px) rotateX(${rotateX}deg) rotateY(${rotateY}deg) scale3d(${props.scale}, ${props.scale}, ${props.scale})`

  if (props.glare) {
    const angle = Math.atan2(y - centerY, x - centerX) * (180 / Math.PI)
    glareTransform.value = `translateX(-50%) translateY(-50%) rotate(${angle}deg)`
    glareOpacity.value = 0.2
  }
}

function handleMouseEnter(): void {
  isHovering.value = true
}

function handleMouseLeave(): void {
  isHovering.value = false
  transform.value = 'perspective(1000px) rotateX(0deg) rotateY(0deg) scale3d(1, 1, 1)'
  glareOpacity.value = 0
}
</script>

<template>
  <div
    ref="cardRef"
    class="tilt-card"
    :class="{ 'is-hovering': isHovering }"
    :style="{
      transform,
      transition: `transform ${speed}ms cubic-bezier(0.03, 0.98, 0.52, 0.99)`,
    }"
    @mousemove="handleMouseMove"
    @mouseenter="handleMouseEnter"
    @mouseleave="handleMouseLeave"
  >
    <div class="tilt-card__content">
      <slot />
    </div>
    <div
      v-if="glare"
      class="tilt-card__glare"
      :style="{
        transform: glareTransform,
        opacity: glareOpacity,
        transition: `opacity ${speed}ms ease`,
      }"
    />
  </div>
</template>

<style scoped>
.tilt-card {
  position: relative;
  transform-style: preserve-3d;
  will-change: transform;
}

.tilt-card__content {
  position: relative;
  z-index: 1;
}

.tilt-card__glare {
  position: absolute;
  top: 50%;
  left: 50%;
  width: 200%;
  height: 200%;
  background: linear-gradient(
    90deg,
    transparent 20%,
    rgb(255 255 255 / 30%) 50%,
    transparent 80%
  );
  pointer-events: none;
  z-index: 2;
}
</style>
