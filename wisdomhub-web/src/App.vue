<script setup lang="ts">
import zhCn from 'element-plus/es/locale/lang/zh-cn'
import { RouterView } from 'vue-router'

import AppLoadingBar from '@/components/layout/AppLoadingBar.vue'
import { useProfile } from '@/composables/useProfile'
import { useAppStore } from '@/stores/app'

// 在根组件初始化一次，让 app store 里的主题 watch 从应用启动起就一直在线，
// 而不是等到某个碰巧用到它的页面被打开才生效。
useAppStore()

// 已登录时把资料拉回来（顶部导航的头像/昵称要用）；未登录时它自己会跳过
void useProfile().load()
</script>

<template>
  <!--
    Element Plus 的中文文案（分页的「条/页」「共 N 条」这类内置文本）。
    组件是按需引入的、没走 app.use(ElementPlus)，所以语言包只能用 ConfigProvider 下发。
  -->
  <el-config-provider :locale="zhCn">
    <!-- 顶栏 loading 挂在根上：两套外壳（DefaultLayout / BlankLayout）都能用 -->
    <AppLoadingBar />

    <!-- 外壳（顶部导航 / 侧边栏 / 页脚）由路由选择的 layout 提供：
         多数页面走 DefaultLayout，登录页与设计系统页走 BlankLayout。
         见 src/router/index.ts 的两组顶层路由。 -->
    <RouterView />
  </el-config-provider>
</template>
