<script setup lang="ts">
import PageContainer from '@/components/layout/PageContainer.vue'

withDefaults(
  defineProps<{
    code: string
    title: string
    description: string
    path?: string
    eyebrow?: string
  }>(),
  {
    eyebrow: 'ERROR',
  },
)
</script>

<template>
  <PageContainer>
    <div class="error-state">
      <p class="error-state__eyebrow">[ {{ eyebrow }} / {{ code }} ]</p>
      <p class="error-state__code" aria-hidden="true">{{ code }}</p>
      <h1>{{ title }}</h1>
      <p class="error-state__desc">{{ description }}</p>
      <code v-if="path" class="error-state__path">{{ path }}</code>
      <div class="error-state__actions">
        <slot name="actions" />
      </div>
    </div>
  </PageContainer>
</template>

<style scoped>
.error-state {
  max-width: 760px;
  padding-block: clamp(32px, 8vw, 96px);
}

.error-state__eyebrow {
  font-family: var(--mono);
  font-size: var(--text-xs);
  letter-spacing: var(--tracking-wider);
  color: var(--verm);
}

.error-state__code {
  margin-top: var(--space-4);
  color: var(--verm);
  font-family: var(--serif);
  font-size: clamp(6rem, 22vw, 14rem);
  font-weight: var(--weight-black);
  line-height: 0.8;
  letter-spacing: -0.06em;
}

.error-state h1 {
  margin-top: var(--space-6);
  font-size: var(--text-3xl);
}

.error-state__desc {
  max-width: 48ch;
  margin-top: var(--space-3);
  color: var(--text-muted);
  line-height: var(--leading-relaxed);
}

.error-state__path {
  display: inline-block;
  margin-top: var(--space-5);
  padding: var(--space-2) var(--space-3);
  border: var(--border);
  background: var(--surface);
  font-family: var(--mono);
  font-size: var(--text-xs);
  overflow-wrap: anywhere;
}

.error-state__actions {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-3);
  margin-top: var(--space-8);
}
</style>
