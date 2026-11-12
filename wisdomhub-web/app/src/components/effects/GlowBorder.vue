<script setup lang="ts">
import { ref, onMounted, onBeforeUnmount } from 'vue'

const props = withDefaults(
  defineProps<{
    color?: string
    speed?: number
    width?: number
    animated?: boolean
  }>(),
  {
    color: 'var(--acid)',
    speed: 4,
    width: 2,
    animated: true,
  },
)

const containerRef = ref<HTMLElement | null>(null)
const angle = ref(0)
let animationId = 0

function animate(): void {
  angle.value = (angle.value + 0.5) % 360
  updateGradient()
  animationId = requestAnimationFrame(animate)
}

function updateGradient(): void {
  const el = containerRef.value
  if (!el) return

  const x = 50 + Math.cos((angle.value * Math.PI) / 180) * 50
  const y = 50 + Math.sin((angle.value * Math.PI) / 180) * 50

  el.style.setProperty('--glow-x', `${x}%`)
  el.style.setProperty('--glow-y', `${y}%`)
}

onMounted(() => {
  if (props.animated) animate()
})

onBeforeUnmount(() => {
  cancelAnimationFrame(animationId)
})
</script>

<template>
  <div ref="containerRef" class="glow-border" :class="{ 'is-animated': animated }">
    <div class="glow-border__inner">
      <slot />
    </div>
  </div>
</template>

<style scoped>
.glow-border {
  position: relative;
  --glow-x: 50%;
  --glow-y: 0%;
}

.glow-border::before {
  content: '';
  position: absolute;
  inset: calc(var(--border-width, 2px) * -1);
  border-radius: inherit;
  background: radial-gradient(
    circle at var(--glow-x) var(--glow-y),
    var(--glow-color, var(--acid)),
    transparent 60%
  );
  opacity: 0.8;
  z-index: -1;
  transition: opacity var(--anim-normal) var(--anim-ease);
}

.glow-border::after {
  content: '';
  position: absolute;
  inset: 0;
  border-radius: inherit;
  border: var(--border-width, 2px) solid transparent;
  background: var(--surface, var(--paper)) border-box;
  mask:
    linear-gradient(#fff 0 0) padding-box,
    linear-gradient(#fff 0 0);
  mask-composite: exclude;
  -webkit-mask:
    linear-gradient(#fff 0 0) padding-box,
    linear-gradient(#fff 0 0);
  -webkit-mask-composite: xor;
  z-index: -1;
}

.glow-border__inner {
  position: relative;
  z-index: 1;
}

.glow-border:not(.is-animated)::before {
  background: var(--glow-color, var(--acid));
  opacity: 0;
  transition: opacity var(--anim-normal) var(--anim-ease);
}

.glow-border:not(.is-animated):hover::before {
  opacity: 0.6;
}
</style>
