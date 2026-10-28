<script setup lang="ts">
withDefaults(
  defineProps<{
    tag?: string
    size?: 'default' | 'narrow' | 'reading'
  }>(),
  {
    tag: 'div',
    size: 'default',
  },
)
</script>

<template>
  <component :is="tag" class="page-container" :class="`page-container--${size}`">
    <slot />
  </component>
</template>

<style scoped>
.page-container {
  width: 100%;
  max-width: var(--max);
  margin-inline: auto;
  padding-inline: var(--pad);
  padding-block: clamp(24px, 4vw, 48px);
}

.page-container--narrow {
  max-width: var(--max-narrow);
}

.page-container--reading {
  max-width: calc(var(--max-reading) + var(--pad) * 2);
}
</style>
