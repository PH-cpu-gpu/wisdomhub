<script setup lang="ts">
import { isBusy } from '@/composables/useLoading'

/**
 * 顶栏 loading 条（F1.9 的「全局 loading」）。
 *
 * 自己写而不是引 nprogress：只需要一条 2px 的横条，
 * 而且颜色要跟设计令牌走（nprogress 的主题色是写死的）。
 *
 * 计数来自 `composables/useLoading`，由请求层自动增减 —— 业务代码不用管。
 */
</script>

<template>
  <!-- 常驻 DOM、空闲时透明：比 v-if 少一次挂载，也就没有淡出时的跳变 -->
  <div class="bar" :class="{ 'is-busy': isBusy }" aria-hidden="true" />
</template>

<style scoped>
.bar {
  position: fixed;
  inset-block-start: 0;
  inset-inline: 0;

  z-index: var(--wh-z-overlay);
  height: 2px;

  pointer-events: none;
}

.bar.is-busy {
  background-image: linear-gradient(90deg, transparent, var(--wh-accent), transparent);
  background-size: 40% 100%;
  background-repeat: no-repeat;
  animation: wh-loading-slide 1.1s var(--wh-ease) infinite;
}

@keyframes wh-loading-slide {
  from {
    background-position-x: -40%;
  }

  to {
    background-position-x: 140%;
  }
}

/* 用户开了「减少动态效果」就不动，只留一条静止的提示条 */
@media (prefers-reduced-motion: reduce) {
  .bar.is-busy {
    background-image: none;
    background-color: var(--wh-accent);
    animation: none;
  }
}
</style>
