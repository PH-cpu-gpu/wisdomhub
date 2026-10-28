<script setup lang="ts">
import { RouterLink } from 'vue-router'

import WhAvatar from '@/components/base/WhAvatar.vue'
import type { UserBriefVO } from '@/types/user'

withDefaults(
  defineProps<{
    author?: UserBriefVO | null
    size?: 'xs' | 'sm' | 'md'
    link?: boolean
  }>(),
  {
    size: 'xs',
    link: true,
  },
)
</script>

<template>
  <RouterLink
    v-if="author && link"
    class="author-chip"
    :to="{ name: 'user-profile', params: { userId: author.userId } }"
  >
    <WhAvatar
      :src="author.avatar ?? undefined"
      :name="author.username"
      :size="size"
      shape="circle"
    />
    <span class="author-chip__name">{{ author.username }}</span>
  </RouterLink>

  <span v-else class="author-chip">
    <WhAvatar
      :src="author?.avatar ?? undefined"
      :name="author?.username ?? '作者'"
      :size="size"
      shape="circle"
    />
    <span class="author-chip__name">{{ author?.username ?? '未知作者' }}</span>
  </span>
</template>

<style scoped>
.author-chip {
  display: inline-flex;
  align-items: center;
  gap: var(--space-2);
  min-width: 0;
  color: var(--text-muted);
  font-family: var(--mono);
  font-size: var(--text-2xs);
  text-decoration: none;
}

.author-chip__name {
  overflow: hidden;
  max-width: 16ch;
  color: var(--ink);
  text-overflow: ellipsis;
  white-space: nowrap;
}

a.author-chip {
  transition: color var(--dur) var(--ease);
}

a.author-chip:hover .author-chip__name {
  color: var(--verm-deep);
}
</style>
