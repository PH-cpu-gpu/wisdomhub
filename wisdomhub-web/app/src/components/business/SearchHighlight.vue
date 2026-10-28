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
.search-highlight :deep(em) {
  background: var(--acid);
  color: var(--ink);
  font-style: normal;
  padding-inline: 2px;
}
</style>
