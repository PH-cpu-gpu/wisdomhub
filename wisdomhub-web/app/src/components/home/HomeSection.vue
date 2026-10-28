<script setup lang="ts">
import { computed } from 'vue'
import { RouterLink, type RouteLocationRaw } from 'vue-router'

import { isApiError } from '@/api/error'
import WhButton from '@/components/base/WhButton.vue'
import WhSkeleton from '@/components/base/WhSkeleton.vue'

type SkeletonKind = 'article' | 'tag' | 'question'

const props = withDefaults(
  defineProps<{
    eyebrow: string
    title: string
    actionLabel?: string
    actionTo?: RouteLocationRaw
    loading?: boolean
    error?: unknown
    empty?: boolean
    emptyText?: string
    skeleton?: SkeletonKind
  }>(),
  {
    actionLabel: '',
    loading: false,
    empty: false,
    emptyText: '暂时没有内容。',
    skeleton: 'article',
  },
)

defineEmits<{
  retry: []
}>()

const errorMessage = computed(() => {
  if (!props.error) return ''
  return isApiError(props.error) ? props.error.message : '加载失败，请稍后重试。'
})
</script>

<template>
  <section class="home-section">
    <header class="home-section__head">
      <div class="home-section__heading">
        <p class="home-section__eyebrow">{{ eyebrow }}</p>
        <h2>{{ title }}</h2>
      </div>
      <RouterLink v-if="actionLabel && actionTo" class="home-section__action" :to="actionTo || '/'">
        {{ actionLabel }}
        <span aria-hidden="true">→</span>
      </RouterLink>
    </header>

    <div v-if="loading" class="home-section__skeleton" :class="`is-${skeleton}`" aria-busy="true">
      <template v-if="skeleton === 'tag'">
        <div v-for="index in 5" :key="index" class="home-skeleton-tag">
          <WhSkeleton width="32%" height="10px" />
          <WhSkeleton width="72%" height="26px" />
          <WhSkeleton width="45%" height="10px" />
        </div>
      </template>

      <template v-else-if="skeleton === 'question'">
        <div v-for="index in 5" :key="index" class="home-skeleton-row">
          <WhSkeleton width="36px" height="12px" />
          <div class="home-skeleton-row__main">
            <WhSkeleton width="64%" height="16px" />
            <WhSkeleton width="36%" height="10px" />
          </div>
        </div>
      </template>

      <template v-else>
        <div v-for="index in 4" :key="index" class="home-skeleton-card">
          <WhSkeleton width="30%" height="10px" />
          <WhSkeleton :lines="2" height="16px" />
          <WhSkeleton width="88%" :lines="2" height="10px" />
        </div>
      </template>
    </div>

    <div
      v-else-if="errorMessage"
      class="home-section__state home-section__state--error"
      role="alert"
    >
      <p>{{ errorMessage }}</p>
      <WhButton size="sm" variant="ghost" @click="$emit('retry')">重试</WhButton>
    </div>

    <div v-else-if="empty" class="home-section__state">{{ emptyText }}</div>

    <slot v-else />
  </section>
</template>

<style scoped>
.home-section {
  display: grid;
  gap: var(--space-5);
}

.home-section__head {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: var(--space-4);
  padding-bottom: var(--space-3);
  border-bottom: 1px solid var(--ink);
}

.home-section__heading {
  display: grid;
  gap: var(--space-1);
}

.home-section__eyebrow {
  color: var(--verm-deep);
  font-family: var(--mono);
  font-size: var(--text-2xs);
  letter-spacing: var(--tracking-wider);
}

.home-section__heading h2 {
  font-family: var(--serif);
  font-size: var(--text-2xl);
  font-weight: var(--weight-black);
  letter-spacing: var(--tracking-tight);
}

.home-section__action {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  flex-shrink: 0;
  color: var(--text-muted);
  font-family: var(--mono);
  font-size: var(--text-2xs);
  letter-spacing: 0.08em;
  text-decoration: none;
  transition: color var(--dur) var(--ease);
}

.home-section__action:hover {
  color: var(--verm-deep);
}

.home-section__skeleton {
  display: grid;
  gap: var(--space-5);
}

.home-section__skeleton.is-article {
  grid-template-columns: repeat(2, minmax(0, 1fr));
  column-gap: var(--space-8);
}

.home-section__skeleton.is-tag {
  grid-template-columns: repeat(auto-fit, minmax(150px, 1fr));
  gap: 1px;
  border: 1px solid var(--line);
  background: var(--line);
}

.home-skeleton-card {
  display: grid;
  gap: var(--space-3);
  padding-block: var(--space-5);
  border-top: var(--border);
}

.home-skeleton-tag {
  display: grid;
  gap: var(--space-3);
  padding: var(--space-4);
  background: var(--surface);
}

.home-skeleton-row {
  display: grid;
  grid-template-columns: 36px minmax(0, 1fr);
  gap: var(--space-3);
  align-items: start;
  padding-block: var(--space-4);
  border-top: var(--border);
}

.home-skeleton-row__main {
  display: grid;
  gap: var(--space-2);
}

.home-section__state {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: var(--space-3);
  padding: var(--space-6);
  border: 1px dashed var(--line-strong);
  color: var(--text-muted);
  font-size: var(--text-sm);
}

.home-section__state--error {
  border-color: var(--verm);
  color: var(--verm-deep);
}

@media (max-width: 720px) {
  .home-section__skeleton.is-article {
    grid-template-columns: 1fr;
  }
}
</style>
