<script setup lang="ts">
import { computed } from 'vue'
import { RouterLink, useRoute } from 'vue-router'

import PageContainer from '@/components/layout/PageContainer.vue'

const route = useRoute()

const meta = computed(() => route.meta)
const params = computed(() => Object.entries(route.params))
const query = computed(() => Object.entries(route.query))
const isAuthPage = computed(() => route.name === 'login' || route.name === 'register')
</script>

<template>
  <PageContainer size="narrow">
    <article class="placeholder">
      <p class="placeholder__eyebrow">
        [ {{ meta.section ?? 'PAGE' }} / {{ meta.task ?? 'TODO' }} ]
      </p>
      <h1 class="placeholder__title">{{ meta.title ?? '页面占位' }}</h1>
      <p class="placeholder__desc">{{ meta.description }}</p>

      <dl class="placeholder__facts">
        <div>
          <dt>路由</dt>
          <dd>{{ route.path }}</dd>
        </div>
        <div v-if="params.length">
          <dt>路径参数</dt>
          <dd>{{ JSON.stringify(Object.fromEntries(params)) }}</dd>
        </div>
        <div v-if="query.length">
          <dt>查询参数</dt>
          <dd>{{ JSON.stringify(Object.fromEntries(query)) }}</dd>
        </div>
      </dl>

      <p v-if="isAuthPage" class="placeholder__note">
        登录 / 注册表单将在 F1.1 / F1.2 实现。当前守卫会把未登录用户带到这里，并保留
        <code>redirect</code> 参数。
      </p>

      <div class="placeholder__actions">
        <RouterLink class="wh-action" :to="{ name: 'home' }">返回首页</RouterLink>
        <RouterLink class="wh-action wh-action--ghost" to="/dev/ui">查看组件库</RouterLink>
      </div>

      <p class="placeholder__foot">
        这是 F0.3 的路由骨架占位页；对应页面会在 {{ meta.task }} 实现。
      </p>
    </article>
  </PageContainer>
</template>

<style scoped>
.placeholder {
  max-width: 720px;
  padding-block: clamp(24px, 6vw, 64px);
}

.placeholder__eyebrow {
  font-family: var(--mono);
  font-size: var(--text-xs);
  letter-spacing: var(--tracking-wider);
  color: var(--verm);
}

.placeholder__title {
  margin-top: var(--space-4);
  font-size: var(--text-3xl);
}

.placeholder__desc {
  margin-top: var(--space-3);
  color: var(--text-muted);
  line-height: var(--leading-relaxed);
}

.placeholder__facts {
  display: grid;
  gap: var(--space-3);
  margin-top: var(--space-6);
  padding: var(--space-4);
  border: var(--border);
  background: var(--surface);
}

.placeholder__facts div {
  display: grid;
  grid-template-columns: 110px minmax(0, 1fr);
  gap: var(--space-3);
}

.placeholder__facts dt {
  font-family: var(--mono);
  font-size: var(--text-2xs);
  letter-spacing: 0.08em;
  color: var(--text-muted);
}

.placeholder__facts dd {
  margin: 0;
  font-family: var(--mono);
  font-size: var(--text-xs);
  overflow-wrap: anywhere;
}

.placeholder__note {
  margin-top: var(--space-4);
  padding: var(--space-3) var(--space-4);
  border-left: 3px solid var(--verm);
  background: var(--surface);
  color: var(--text-muted);
  font-size: var(--text-sm);
  line-height: var(--leading-relaxed);
}

.placeholder__note code {
  font-family: var(--mono);
  font-size: 0.92em;
}

.placeholder__actions {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-3);
  margin-top: var(--space-6);
}

.placeholder__foot {
  margin-top: var(--space-6);
  font-family: var(--mono);
  font-size: var(--text-2xs);
  color: var(--text-muted);
}

@media (max-width: 639px) {
  .placeholder__facts div {
    grid-template-columns: 1fr;
    gap: var(--space-1);
  }
}
</style>
