import { onBeforeUnmount, onMounted, ref, type Ref } from 'vue'

/**
 * 是否允许播放过渡动画。
 *
 * - 页面不可见（后台标签页/隐藏窗口）时 rAF 被暂停，Vue 的 Transition 会卡在 leave 态、
 *   节点残留在 DOM 与无障碍树里，所以此时直接关掉 CSS 过渡，走即时移除；
 * - 用户系统开启「减少动效」时同样关闭。
 */
export function useMotionEnabled(): Ref<boolean> {
  const enabled = ref(true)
  let media: MediaQueryList | null = null

  function sync(): void {
    const hidden = typeof document !== 'undefined' && document.visibilityState === 'hidden'
    enabled.value = !hidden && !(media?.matches ?? false)
  }

  onMounted(() => {
    media = window.matchMedia('(prefers-reduced-motion: reduce)')
    media.addEventListener('change', sync)
    document.addEventListener('visibilitychange', sync)
    sync()
  })

  onBeforeUnmount(() => {
    media?.removeEventListener('change', sync)
    document.removeEventListener('visibilitychange', sync)
  })

  return enabled
}
