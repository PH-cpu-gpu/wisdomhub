<script setup lang="ts">
import { RouterView } from 'vue-router'

import WhConfirm from '@/components/base/WhConfirm.vue'
import WhToast from '@/components/base/WhToast.vue'
import AppFooter from '@/components/layout/AppFooter.vue'
import AppHeader from '@/components/layout/AppHeader.vue'
</script>

<template>
  <div class="app-shell">
    <a class="wh-skip-link" href="#main">跳到主要内容</a>
    <AppHeader />
    <main id="main" class="app-shell__main" tabindex="-1">
      <RouterView />
    </main>
    <AppFooter />
    <WhToast />
    <WhConfirm />
  </div>
</template>

<style scoped>
.app-shell {
  display: flex;
  flex-direction: column;
  min-height: 100dvh;
}

.app-shell__main {
  flex: 1;
}

.app-shell__main:focus {
  outline: none;
}
</style>
