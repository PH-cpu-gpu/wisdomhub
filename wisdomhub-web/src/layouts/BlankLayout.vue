<script setup lang="ts">
import { RouterView } from 'vue-router'

/**
 * 无外壳布局：只有内容，没有顶部导航与页脚。
 *
 * 用在两类页面上：
 *   1. 登录页（F1.1）—— 把注意力留给表单，别让导航分散注意力
 *   2. 设计系统页（F0.2）—— 它是一份独立文档，套上站点导航反而碍事
 */
</script>

<template>
  <div class="blank">
    <RouterView />
  </div>
</template>

<style scoped>
.blank {
  min-height: 100vh;
  background-color: var(--wh-bg-base);
}
</style>
