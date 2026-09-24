<script setup lang="ts">
import { RouterView } from 'vue-router'

import { useAppStore } from '@/stores/app'

// 在根组件初始化一次，让 app store 里的主题 watch 从应用启动起就一直在线，
// 而不是等到某个碰巧用到它的页面被打开才生效。
useAppStore()
</script>

<template>
  <!-- 外壳（顶部导航 / 侧边栏 / 页脚）由路由选择的 layout 提供：
       多数页面走 DefaultLayout，登录页与设计系统页走 BlankLayout。
       见 src/router/index.ts 的两组顶层路由。 -->
  <RouterView />
</template>
