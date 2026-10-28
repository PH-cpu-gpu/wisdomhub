<script setup lang="ts">
import { RouterLink, useRouter } from 'vue-router'

import ErrorState from '@/components/feedback/ErrorState.vue'

const router = useRouter()
</script>

<template>
  <ErrorState
    code="403"
    title="你没有权限访问这个资源。"
    description="你当前没有权限访问这个资源。只有内容作者本人可以修改或删除自己的内容。如果你认为这是误判，可以返回上一页重试。"
  >
    <template #actions>
      <button class="wh-action" type="button" @click="router.back()">返回上一页</button>
      <RouterLink class="wh-action wh-action--ghost" :to="{ name: 'home' }">回首页</RouterLink>
    </template>
  </ErrorState>
</template>
