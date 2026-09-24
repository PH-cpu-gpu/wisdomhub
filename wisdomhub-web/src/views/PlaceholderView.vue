<script setup lang="ts">
import { computed } from 'vue'
import { useRoute } from 'vue-router'

/**
 * 路由占位页 —— 阶段 0 所有业务页面的统一承载。
 *
 * 存在的意义不是「先随便放个空白」，而是把**信息架构**先定下来：
 * 路由路径、页面标题、以及这一页归哪个任务实现，都在这里可见。
 * 这样 F0.3 的验收（导航能点、路由能到）成立，同时不会让人误以为页面做好了。
 *
 * 实现某个任务时，只要把 router 里那一行的 component 换成真组件即可，
 * 不需要动导航、也不用改这里的任何东西。
 *
 * 页面自己说明「我是谁的占位」—— 靠的是路由 meta，不是每个页面各写一份。
 */
const route = useRoute()

const title = computed(() => (route.meta.title as string | undefined) ?? '页面')
const task = computed(() => route.meta.task as string | undefined)
const note = computed(() => route.meta.note as string | undefined)
</script>

<template>
  <div class="ph">
    <p class="ph__eyebrow wh-mono">
      <span v-if="task">{{ task }}</span>
      <span v-else>待实现</span>
    </p>

    <h1 class="ph__title">{{ title }}</h1>

    <p v-if="note" class="ph__note">{{ note }}</p>

    <p class="ph__hint">
      这个页面还没有实现。路由、导航和布局已经就位，等内容接口定稿后由
      <span class="wh-mono">{{ task ?? '对应任务' }}</span> 填充。
    </p>

    <div class="ph__actions">
      <el-button type="primary" @click="$router.push('/')">回到首页</el-button>
      <el-button @click="$router.push('/design')">查看设计系统</el-button>
    </div>
  </div>
</template>

<style scoped>
.ph {
  display: flex;
  flex-direction: column;
  gap: var(--wh-space-3);
  max-width: var(--wh-reading);
  padding-block: var(--wh-space-7);
}

.ph__eyebrow {
  font-size: var(--wh-text-xs);
  letter-spacing: var(--wh-tracking-wide);
  color: var(--wh-accent);
}

.ph__title {
  font-size: var(--wh-text-2xl);
}

.ph__note {
  color: var(--wh-text-2);
}

.ph__hint {
  padding: var(--wh-space-4);

  font-size: var(--wh-text-sm);
  color: var(--wh-text-3);
  background-color: var(--wh-bg-surface);
  border: var(--wh-border-width) dashed var(--wh-border-strong);
  border-radius: var(--wh-radius-md);
}

.ph__actions {
  display: flex;
  flex-wrap: wrap;
  gap: var(--wh-space-3);
  margin-top: var(--wh-space-2);
}
</style>
