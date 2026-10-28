import { nextTick, onBeforeUnmount, watch, type Ref } from 'vue'

const FOCUSABLE =
  'a[href], button:not([disabled]), textarea:not([disabled]), input:not([disabled]), select:not([disabled]), [tabindex]:not([tabindex="-1"])'

const layerStack: symbol[] = []
let scrollLockCount = 0

function lockScroll(): void {
  if (scrollLockCount === 0) document.body.classList.add('layer-open')
  scrollLockCount += 1
}

function unlockScroll(): void {
  scrollLockCount = Math.max(0, scrollLockCount - 1)
  if (scrollLockCount === 0) document.body.classList.remove('layer-open')
}

export interface DismissibleLayerOptions {
  isOpen: Ref<boolean>
  panelRef: Ref<HTMLElement | null>
  onClose: () => void
  closeOnEsc?: boolean
  lockScroll?: boolean
  restoreFocus?: boolean
}

/**
 * Modal / Drawer 共用：Escape 关闭、Tab 焦点陷阱、body 滚动锁定、关闭后恢复焦点。
 * 支持多层叠加，只有最上层响应键盘。
 */
export function useDismissibleLayer(options: DismissibleLayerOptions): void {
  const layerId = Symbol('dismissible-layer')
  let previouslyFocused: HTMLElement | null = null
  let locked = false

  const onKeydown = (event: KeyboardEvent): void => {
    if (!options.isOpen.value) return
    if (layerStack[layerStack.length - 1] !== layerId) return

    if (event.key === 'Escape' && options.closeOnEsc !== false) {
      event.preventDefault()
      options.onClose()
      return
    }

    if (event.key !== 'Tab') return

    const panel = options.panelRef.value
    if (!panel) return

    const focusables = Array.from(panel.querySelectorAll<HTMLElement>(FOCUSABLE))
    if (!focusables.length) {
      event.preventDefault()
      panel.focus()
      return
    }

    const first = focusables[0]
    const last = focusables[focusables.length - 1]

    if (event.shiftKey && document.activeElement === first) {
      event.preventDefault()
      last.focus()
    } else if (!event.shiftKey && document.activeElement === last) {
      event.preventDefault()
      first.focus()
    }
  }

  watch(
    options.isOpen,
    async (open) => {
      if (open) {
        previouslyFocused =
          document.activeElement instanceof HTMLElement ? document.activeElement : null
        layerStack.push(layerId)
        if (options.lockScroll !== false) {
          lockScroll()
          locked = true
        }
        document.addEventListener('keydown', onKeydown)
        await nextTick()
        const panel = options.panelRef.value
        const target = panel?.querySelector<HTMLElement>(FOCUSABLE) ?? panel
        target?.focus()
      } else {
        const index = layerStack.lastIndexOf(layerId)
        if (index >= 0) layerStack.splice(index, 1)
        document.removeEventListener('keydown', onKeydown)
        if (locked) {
          unlockScroll()
          locked = false
        }
        if (options.restoreFocus !== false) previouslyFocused?.focus()
        previouslyFocused = null
      }
    },
    { immediate: true },
  )

  onBeforeUnmount(() => {
    const index = layerStack.lastIndexOf(layerId)
    if (index >= 0) layerStack.splice(index, 1)
    document.removeEventListener('keydown', onKeydown)
    if (locked) unlockScroll()
  })
}
