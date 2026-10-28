import { shallowReactive } from 'vue'

export type ToastType = 'success' | 'error' | 'info' | 'warning'

export interface ToastOptions {
  title?: string
  /** 毫秒；0 表示不自动关闭 */
  duration?: number
}

export interface ToastItem {
  id: number
  type: ToastType
  message: string
  title?: string
  duration: number
}

export const toasts = shallowReactive<ToastItem[]>([])

const timers = new Map<number, ReturnType<typeof setTimeout>>()
let seed = 0

export function dismissToast(id: number): void {
  const timer = timers.get(id)
  if (timer) {
    clearTimeout(timer)
    timers.delete(id)
  }
  const index = toasts.findIndex((item) => item.id === id)
  if (index >= 0) toasts.splice(index, 1)
}

export function pushToast(type: ToastType, message: string, options: ToastOptions = {}): number {
  const id = (seed += 1)
  const duration = options.duration ?? 3200

  if (toasts.length >= 5) {
    const oldest = toasts[0]
    if (oldest) dismissToast(oldest.id)
  }

  toasts.push({ id, type, message, title: options.title, duration })

  if (duration > 0) {
    timers.set(
      id,
      setTimeout(() => {
        dismissToast(id)
      }, duration),
    )
  }

  return id
}

export const toast = {
  success: (message: string, options?: ToastOptions) => pushToast('success', message, options),
  error: (message: string, options?: ToastOptions) => pushToast('error', message, options),
  info: (message: string, options?: ToastOptions) => pushToast('info', message, options),
  warning: (message: string, options?: ToastOptions) => pushToast('warning', message, options),
  dismiss: dismissToast,
  clear: (): void => {
    for (const id of [...timers.keys()]) dismissToast(id)
    toasts.splice(0, toasts.length)
  },
}
