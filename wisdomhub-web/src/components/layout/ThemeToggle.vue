<script setup lang="ts">
import { Monitor, Moon, Sunny } from '@element-plus/icons-vue'
import { computed } from 'vue'

import { useAppStore, type ThemeMode } from '@/stores/app'

/**
 * 主题切换。三态：暗色 / 亮色 / 跟随系统。
 *
 * 用下拉而不是「点一下就切」的按钮：后者无法表达「跟随系统」这个状态，
 * 而这个项目是暗色优先的，用户需要能明确区分「我选了暗色」和「系统现在是暗色」。
 */
const app = useAppStore()

const icon = computed(() => {
  if (app.themeMode === 'system') return Monitor
  return app.isDark ? Moon : Sunny
})

const options: { value: ThemeMode; label: string; icon: typeof Moon }[] = [
  { value: 'dark', label: '暗色', icon: Moon },
  { value: 'light', label: '亮色', icon: Sunny },
  { value: 'system', label: '跟随系统', icon: Monitor },
]

function onSelect(value: string | number | object) {
  app.setThemeMode(value as ThemeMode)
}
</script>

<template>
  <el-dropdown trigger="click" @command="onSelect">
    <button class="theme-toggle" type="button" :aria-label="`主题：${app.themeLabel}`">
      <el-icon><component :is="icon" /></el-icon>
    </button>

    <template #dropdown>
      <el-dropdown-menu>
        <el-dropdown-item
          v-for="opt in options"
          :key="opt.value"
          :command="opt.value"
          :class="{ 'is-current': app.themeMode === opt.value }"
        >
          <el-icon><component :is="opt.icon" /></el-icon>
          <span>{{ opt.label }}</span>
        </el-dropdown-item>
      </el-dropdown-menu>
    </template>
  </el-dropdown>
</template>

<style scoped>
.theme-toggle {
  display: grid;
  place-items: center;

  width: 32px;
  height: 32px;
  padding: 0;

  color: var(--wh-text-2);
  background: none;
  border: var(--wh-border-width) solid transparent;
  border-radius: var(--wh-radius-md);
  cursor: pointer;

  transition:
    color var(--wh-duration-fast) var(--wh-ease),
    background-color var(--wh-duration-fast) var(--wh-ease);
}

.theme-toggle:hover {
  color: var(--wh-text-1);
  background-color: var(--wh-bg-raised);
}

/* 下拉里的当前项加一点标记：三态切换时要知道自己现在在哪一档 */
:deep(.el-dropdown-menu__item.is-current) {
  color: var(--wh-accent);
  font-weight: var(--wh-weight-medium);
}
</style>
