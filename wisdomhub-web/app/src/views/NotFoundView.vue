<script setup lang="ts">
import { RouterLink, useRoute } from 'vue-router'

import ErrorState from '@/components/feedback/ErrorState.vue'

const route = useRoute()
</script>

<template>
  <ErrorState
    code="404"
    title="这条路径还没有连上知识网络。"
    description="地址可能写错了，或者内容已经被删除。"
    :path="route.fullPath"
  >
    <template #actions>
      <RouterLink class="wh-action" :to="{ name: 'home' }">返回首页</RouterLink>
      <RouterLink class="wh-action wh-action--ghost" to="/search">去搜索</RouterLink>
    </template>
  </ErrorState>
</template>
