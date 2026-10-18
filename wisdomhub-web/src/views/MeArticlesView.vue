<script setup lang="ts">
import { computed } from 'vue'
import { useRouter } from 'vue-router'

import ArticleList from '@/components/article/ArticleList.vue'
import { useUserStore } from '@/stores/user'

/**
 * F1.8 我的文章。
 *
 * 走的是公开列表接口的 `authorId` 筛选（B1.23），**不是**另一个「我的文章」接口 ——
 * 所以它只列**已发布**的文章；草稿在草稿箱那一页（`/me/drafts`）。
 * 当前用户 id 从 accessToken 的 payload 里读（登录响应没有 user 对象）。
 */
const router = useRouter()
const user = useUserStore()

/** 路由守卫已经保证登录了，这里的兜底只为类型安全 */
const myId = computed(() => user.userId ?? '')
</script>

<template>
  <div class="me">
    <ArticleList
      v-if="myId"
      :author-id="myId"
      hide-author
      empty-text="你还没有发布过文章"
    >
      <template #heading>
        <div>
          <h1 class="me__title">我的文章</h1>
          <p class="me__sub">只有已发布的文章，草稿在「草稿箱」</p>
        </div>
        <el-button type="primary" size="small" @click="router.push('/editor')">写文章</el-button>
      </template>
    </ArticleList>
  </div>
</template>

<style scoped>
.me {
  padding-block: var(--wh-space-5) var(--wh-space-8);
}

.me__title {
  font-size: var(--wh-text-page);
}

.me__sub {
  margin-top: var(--wh-space-2);

  font-size: var(--wh-text-base);
  color: var(--wh-text-2);
}
</style>
