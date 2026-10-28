<script setup lang="ts">
import { computed, ref, watch } from 'vue'

const props = withDefaults(
  defineProps<{
    src?: string
    alt?: string
    name?: string
    size?: 'xs' | 'sm' | 'md' | 'lg' | 'xl' | number
    shape?: 'square' | 'circle'
  }>(),
  {
    size: 'md',
    shape: 'square',
  },
)

const sizeMap = { xs: 24, sm: 32, md: 40, lg: 48, xl: 64 } as const

const imageFailed = ref(false)
const sizePx = computed(() => (typeof props.size === 'number' ? props.size : sizeMap[props.size]))
const initials = computed(() => props.name?.trim().slice(0, 1).toUpperCase() || 'W')
const label = computed(() => props.alt ?? props.name ?? '用户头像')

watch(
  () => props.src,
  () => {
    imageFailed.value = false
  },
)
</script>

<template>
  <span
    class="wh-avatar"
    :class="`wh-avatar--${shape}`"
    :style="{
      width: `${sizePx}px`,
      height: `${sizePx}px`,
      fontSize: `${Math.max(10, Math.round(sizePx * 0.38))}px`,
    }"
  >
    <img
      v-if="src && !imageFailed"
      :src="src"
      :alt="label"
      loading="lazy"
      @error="imageFailed = true"
    />
    <span v-else class="wh-avatar__fallback" aria-hidden="true">{{ initials }}</span>
    <span v-if="!src || imageFailed" class="wh-sr-only">{{ label }}</span>
  </span>
</template>

<style scoped>
.wh-avatar {
  display: inline-flex;
  flex: 0 0 auto;
  align-items: center;
  justify-content: center;
  overflow: hidden;
  border: 1px solid var(--ink);
  border-radius: var(--radius-xs);
  background: var(--acid);
  color: var(--ink);
}

.wh-avatar--circle {
  border-radius: 50%;
}

.wh-avatar img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.wh-avatar__fallback {
  font-family: var(--serif);
  font-weight: var(--weight-black);
  line-height: 1;
}
</style>
