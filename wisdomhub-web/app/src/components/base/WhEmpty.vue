<script setup lang="ts">
withDefaults(
  defineProps<{
    title?: string
    description?: string
    size?: 'sm' | 'md'
  }>(),
  {
    title: '暂无内容',
    description: '这里还没有数据。',
    size: 'md',
  },
)
</script>

<template>
  <div class="wh-empty" :class="`wh-empty--${size}`">
    <svg class="wh-empty__art" viewBox="0 0 120 90" fill="none" aria-hidden="true">
      <rect
        x="10.5"
        y="14.5"
        width="99"
        height="61"
        stroke="currentColor"
        stroke-dasharray="4 4"
        opacity="0.35"
      />
      <path class="wh-empty__link" d="M42 47 78 36" />
      <path class="wh-empty__link wh-empty__link--broken" d="M42 50 72 66" />
      <circle class="wh-empty__node wh-empty__node--acid" cx="38" cy="48" r="5" />
      <circle class="wh-empty__node" cx="82" cy="35" r="5" />
      <circle class="wh-empty__node wh-empty__node--broken" cx="75" cy="68" r="5" />
    </svg>

    <h3 class="wh-empty__title">{{ title }}</h3>
    <p v-if="description" class="wh-empty__desc">{{ description }}</p>
    <div v-if="$slots.default" class="wh-empty__action">
      <slot />
    </div>
  </div>
</template>

<style scoped>
.wh-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: clamp(28px, 5vw, 48px) var(--space-4);
  text-align: center;
}

.wh-empty__art {
  width: min(160px, 55%);
  height: auto;
  color: var(--line-strong);
  animation: wh-empty-rise var(--anim-slow) var(--anim-ease-out) both;
}

.wh-empty__link {
  stroke: var(--ink);
  stroke-width: 1.5;
}

.wh-empty__link--broken {
  stroke: var(--verm);
  stroke-dasharray: 3 3;
  animation: wh-empty-flow 6s linear infinite;
}

.wh-empty__node {
  fill: var(--surface-strong);
  stroke: var(--ink);
  transform-box: fill-box;
  transform-origin: center;
}

.wh-empty__node--acid {
  fill: var(--acid);
  animation: wh-empty-node 2.8s var(--anim-smooth) infinite;
}

.wh-empty__node--broken {
  stroke-dasharray: 2 2;
  animation: wh-empty-flicker 4s var(--anim-smooth) infinite;
}

.wh-empty__title {
  margin-top: var(--space-4);
  font-family: var(--serif);
  font-size: var(--text-lg);
  font-weight: var(--weight-bold);
  letter-spacing: var(--tracking-tight);
}

.wh-empty__desc {
  max-width: 40ch;
  margin-top: var(--space-2);
  color: var(--text-muted);
  font-size: var(--text-sm);
  line-height: 1.7;
}

.wh-empty__action {
  margin-top: var(--space-5);
}

.wh-empty--sm .wh-empty__art {
  width: min(110px, 50%);
}

.wh-empty--sm .wh-empty__title {
  font-size: var(--text-md);
}

@keyframes wh-empty-rise {
  from {
    opacity: 0;
    transform: translateY(10px);
  }

  to {
    opacity: 1;
    transform: translateY(0);
  }
}

@keyframes wh-empty-flow {
  to {
    stroke-dashoffset: -12;
  }
}

@keyframes wh-empty-node {
  0%,
  100% {
    transform: scale(1);
    opacity: 1;
  }

  50% {
    transform: scale(1.28);
    opacity: 0.72;
  }
}

@keyframes wh-empty-flicker {
  0%,
  100% {
    opacity: 1;
  }

  50% {
    opacity: 0.45;
  }
}

@media (prefers-reduced-motion: reduce) {
  .wh-empty__art,
  .wh-empty__link--broken,
  .wh-empty__node--acid,
  .wh-empty__node--broken {
    animation: none;
  }
}
</style>
