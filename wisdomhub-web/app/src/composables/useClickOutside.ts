import { onBeforeUnmount, onMounted, type Ref } from 'vue'

/**
 * 点击目标元素外部时触发。使用 composedPath，兼容 Teleport 与 Shadow DOM。
 */
export function useClickOutside(
  target: Ref<HTMLElement | null>,
  handler: (event: PointerEvent) => void,
): void {
  const listener = (event: PointerEvent): void => {
    const element = target.value
    if (!element) return
    if (!event.composedPath().includes(element)) handler(event)
  }

  onMounted(() => document.addEventListener('pointerdown', listener, true))
  onBeforeUnmount(() => document.removeEventListener('pointerdown', listener, true))
}
