<script setup lang="ts">
import { computed } from 'vue'

import { sanitizeSearchHighlight } from '@/utils/sanitize'

const props = defineProps<{
  html?: string | null
  fallback?: string | null
}>()

const safeHtml = computed(() => sanitizeSearchHighlight(props.html || props.fallback || ''))
</script>

<template>
  <span class="search-highlight" v-html="safeHtml"></span>
</template>

<style scoped>
@keyframes highlight-scan {
  from {
    transform: scaleX(0);
  }
  to {
    transform: scaleX(1);
  }
}

.search-highlight :deep(em) {
  position: relative;
  z-index: 0;
  padding-inline: 2px;
  color: var(--ink);
  font-style: normal;
}

.search-highlight :deep(em)::before {
  content: '';
  position: absolute;
  inset: 0;
  z-index: -1;
  background: var(--acid);
  transform-origin: left center;
  animation: highlight-scan 480ms var(--anim-ease-out) both;
}

@media (prefers-reduced-motion: reduce) {
  .search-highlight :deep(em)::before {
    animation: none;
  }
}
</style>
