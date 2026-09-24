<script setup lang="ts">
import { useRouter } from 'vue-router'

const router = useRouter()
</script>

<template>
  <div class="not-found">
    <div class="not-found__code wh-mono">404</div>
    <h1 class="not-found__title">这个页面不存在</h1>
    <p class="not-found__hint">
      地址可能拼错了，或者内容已经被删除。检查一下 URL，也可以直接回首页。
    </p>
    <div class="not-found__actions">
      <el-button type="primary" @click="router.push('/')">回到首页</el-button>
      <el-button @click="router.back()">返回上一页</el-button>
    </div>
  </div>
</template>

<style scoped>
.not-found {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: var(--wh-space-4);

  min-height: 70vh;
  padding: var(--wh-space-6) var(--wh-space-4);
  text-align: center;
}

/* 唯一一处用大字重排版的地方：404 的「不存在」本身就是内容 */
.not-found__code {
  font-size: 4rem;
  font-weight: var(--wh-weight-semibold);
  line-height: 1;
  color: var(--wh-text-3);
}

.not-found__title {
  font-size: var(--wh-text-xl);
}

.not-found__hint {
  max-width: 34rem;
  color: var(--wh-text-2);
}

.not-found__actions {
  display: flex;
  gap: var(--wh-space-3);
  margin-top: var(--wh-space-2);
}
</style>
