<script setup lang="ts">
import { computed } from 'vue'

import { useMotionEnabled } from '@/composables/useMotionEnabled'

defineProps<{
  name?: string
  mode?: 'default' | 'out-in' | 'in-out'
}>()

// 隐藏页 / 减少动效时关闭 CSS 过渡，并用异步 JS 钩子结束离场。
// 不能直接 css=false 无钩子：out-in 会同步结束离场，导致重入更新被跳过。
const motionEnabled = useMotionEnabled()

function finishLater(_el: Element, done: () => void): void {
  window.setTimeout(done, 0)
}

const transitionProps = computed(() =>
  motionEnabled.value
    ? {}
    : { css: false, onEnter: finishLater, onLeave: finishLater },
)
</script>

<template>
  <Transition :name="name || 'page'" :mode="mode || 'default'" v-bind="transitionProps">
    <slot />
  </Transition>
</template>

<style>
/* Page transitions */
.page-enter-active,
.page-leave-active {
  transition:
    opacity 280ms var(--anim-ease-out, cubic-bezier(0.16, 1, 0.3, 1)),
    transform 280ms var(--anim-ease-out, cubic-bezier(0.16, 1, 0.3, 1));
}

.page-enter-from {
  opacity: 0;
  transform: translateY(16px);
}

.page-leave-to {
  opacity: 0;
  transform: translateY(-8px);
}

/* Fade transition */
.fade-enter-active,
.fade-leave-active {
  transition: opacity 250ms ease;
}

.fade-enter-from,
.fade-leave-to {
  opacity: 0;
}

/* Scale transition */
.scale-enter-active,
.scale-leave-active {
  transition:
    opacity 250ms ease,
    transform 250ms cubic-bezier(0.34, 1.56, 0.64, 1);
}

.scale-enter-from,
.scale-leave-to {
  opacity: 0;
  transform: scale(0.95);
}

/* Slide right transition */
.slide-right-enter-active,
.slide-right-leave-active {
  transition:
    opacity 300ms ease,
    transform 300ms cubic-bezier(0.16, 1, 0.3, 1);
}

.slide-right-enter-from {
  opacity: 0;
  transform: translateX(-30px);
}

.slide-right-leave-to {
  opacity: 0;
  transform: translateX(30px);
}

/* Zoom transition */
.zoom-enter-active,
.zoom-leave-active {
  transition:
    opacity 300ms ease,
    transform 300ms cubic-bezier(0.34, 1.56, 0.64, 1);
}

.zoom-enter-from,
.zoom-leave-to {
  opacity: 0;
  transform: scale(0.9);
}

@media (prefers-reduced-motion: reduce) {
  .page-enter-active,
  .page-leave-active,
  .fade-enter-active,
  .fade-leave-active,
  .scale-enter-active,
  .scale-leave-active,
  .slide-right-enter-active,
  .slide-right-leave-active,
  .zoom-enter-active,
  .zoom-leave-active {
    transition: none;
  }

  .page-enter-from,
  .page-leave-to,
  .fade-enter-from,
  .fade-leave-to,
  .scale-enter-from,
  .scale-leave-to,
  .slide-right-enter-from,
  .slide-right-leave-to,
  .zoom-enter-from,
  .zoom-leave-to {
    opacity: 1;
    transform: none;
  }
}
</style>
