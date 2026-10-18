<script setup lang="ts">
import { computed } from 'vue'
import { useRoute } from 'vue-router'

/**
 * 尚未实现的页面。
 *
 * ⚠️ **面向用户**：只说明「这个功能还在开发中」，不显示任务号、开发阶段、
 * 也不描述内部实现计划 —— 那些信息在任务表里，不该出现在站点上。
 */
const route = useRoute()

const title = computed(() => (route.meta.title as string | undefined) ?? '页面')
</script>

<template>
  <div class="ph">
    <h1 class="ph__title">{{ title }}</h1>

    <p class="ph__hint">这个功能还在开发中，敬请期待。</p>

    <div class="ph__actions">
      <el-button type="primary" @click="$router.push('/')">回到首页</el-button>
    </div>
  </div>
</template>

<style scoped>
.ph {
  display: flex;
  flex-direction: column;
  gap: var(--wh-space-3);
  max-width: var(--wh-reading);
  padding-block: var(--wh-space-8);
}

.ph__title {
  font-size: var(--wh-text-page);
}

.ph__hint {
  font-size: var(--wh-text-base);
  line-height: var(--wh-leading-reading);
  color: var(--wh-text-2);
}

.ph__actions {
  display: flex;
  flex-wrap: wrap;
  gap: var(--wh-space-3);
  margin-top: var(--wh-space-2);
}
</style>
