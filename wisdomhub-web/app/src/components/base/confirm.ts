import { reactive } from 'vue'

export interface ConfirmOptions {
  title?: string
  message: string
  confirmText?: string
  cancelText?: string
  danger?: boolean
}

interface ConfirmState {
  open: boolean
  title: string
  message: string
  confirmText: string
  cancelText: string
  danger: boolean
}

export const confirmState = reactive<ConfirmState>({
  open: false,
  title: '确认操作',
  message: '',
  confirmText: '确认',
  cancelText: '取消',
  danger: false,
})

let resolver: ((value: boolean) => void) | null = null

export function confirm(options: ConfirmOptions): Promise<boolean> {
  if (resolver) {
    resolver(false)
    resolver = null
  }

  confirmState.title = options.title ?? '确认操作'
  confirmState.message = options.message
  confirmState.confirmText = options.confirmText ?? '确认'
  confirmState.cancelText = options.cancelText ?? '取消'
  confirmState.danger = options.danger ?? false
  confirmState.open = true

  return new Promise((resolve) => {
    resolver = resolve
  })
}

export function resolveConfirm(value: boolean): void {
  if (!resolver) {
    confirmState.open = false
    return
  }

  const resolve = resolver
  resolver = null
  confirmState.open = false
  resolve(value)
}
