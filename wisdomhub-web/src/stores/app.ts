import { defineStore } from 'pinia'
import { computed, ref, watch } from 'vue'

/**
 * 全局外观状态。目前只有主题。
 *
 * 主题是**暗色优先**：用户没表过态时一律暗色，不跟随系统；
 * 只有用户主动选了「跟随系统」才去读 prefers-color-scheme。
 *
 * ⚠️ 存储键 `wisdomhub:theme` 与取值口径（dark | light | system）
 * 同时写在 index.html 的内联脚本里 —— 那份要在首屏绘制前就把 class 定下来，
 * 不能等这里的 JS 加载完。改这里必须同步改那边，否则会出现
 * 「刷新一下亮色闪一下再变暗色」。
 */
export type ThemeMode = 'dark' | 'light' | 'system'

const STORAGE_KEY = 'wisdomhub:theme'

function readStoredMode(): ThemeMode {
  try {
    const saved = localStorage.getItem(STORAGE_KEY)
    if (saved === 'dark' || saved === 'light' || saved === 'system') return saved
  } catch {
    /* localStorage 不可用（隐私模式 / 被策略禁用）时退回默认值 */
  }
  return 'dark'
}

export const useAppStore = defineStore('app', () => {
  const themeMode = ref<ThemeMode>(readStoredMode())

  // 系统的深浅色偏好。只有 themeMode === 'system' 时才会被用到。
  const systemPrefersDark = ref(
    typeof window !== 'undefined' && window.matchMedia('(prefers-color-scheme: dark)').matches,
  )

  if (typeof window !== 'undefined') {
    window
      .matchMedia('(prefers-color-scheme: dark)')
      .addEventListener('change', (e) => {
        systemPrefersDark.value = e.matches
      })
  }

  const isDark = computed(() =>
    themeMode.value === 'system' ? systemPrefersDark.value : themeMode.value === 'dark',
  )

  const themeLabel = computed(() => {
    switch (themeMode.value) {
      case 'dark':
        return '暗色'
      case 'light':
        return '亮色'
      default:
        return '跟随系统'
    }
  })

  /**
   * 把主题写到 <html> 上。
   *
   * `dark` 这个类名不是随便起的：Element Plus 的暗色变量集
   * （element-plus/theme-chalk/dark/css-vars.css）就是挂在 `html.dark` 下的，
   * 本项目自己的令牌也挂在同一个选择器上，两者因此能一起切换。
   * 换成别的类名等于两套主题各切各的。
   */
  function applyTheme(dark: boolean) {
    document.documentElement.classList.toggle('dark', dark)
  }

  function setThemeMode(mode: ThemeMode) {
    themeMode.value = mode
    try {
      localStorage.setItem(STORAGE_KEY, mode)
    } catch {
      /* 存不下就算了，本次会话内仍然生效，只是刷新后回默认 */
    }
  }

  /** 在暗色 / 亮色之间切换；当前是 system 时按「实际显示的反面」切，
   *  这样在跟随系统的状态下点一下也能得到肉眼可见的变化。 */
  function toggleTheme() {
    setThemeMode(isDark.value ? 'light' : 'dark')
  }

  // immediate: true —— store 一被首次使用就把 class 对齐，
  // 不依赖任何组件记得去调 applyTheme。
  watch(isDark, applyTheme, { immediate: true })

  return { themeMode, isDark, themeLabel, setThemeMode, toggleTheme }
})
