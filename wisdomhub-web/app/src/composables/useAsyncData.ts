import { onScopeDispose, ref, shallowRef, type Ref } from 'vue'

export interface UseAsyncDataOptions<T> {
  /** 默认 true：setup 后立即执行一次 */
  immediate?: boolean
  initialData?: T
  onSuccess?: (data: T) => void
  onError?: (error: unknown) => void
}

export interface UseAsyncDataResult<T> {
  data: Ref<T | undefined>
  loading: Ref<boolean>
  error: Ref<unknown>
  execute: () => Promise<T | undefined>
  refresh: () => Promise<T | undefined>
  reset: () => void
}

/**
 * 异步数据加载：loading / error / data 三态 + 竞态保护。
 * 只在组件 setup 中调用；页面切换参数时手动 execute()。
 */
export function useAsyncData<T>(
  loader: () => Promise<T>,
  options: UseAsyncDataOptions<T> = {},
): UseAsyncDataResult<T> {
  const data = shallowRef<T | undefined>(options.initialData)
  const loading = ref(false)
  const error = shallowRef<unknown>(null)
  let requestId = 0

  async function execute(): Promise<T | undefined> {
    const id = (requestId += 1)
    loading.value = true
    error.value = null

    try {
      const result = await loader()
      if (id !== requestId) return undefined
      data.value = result
      options.onSuccess?.(result)
      return result
    } catch (err) {
      if (id !== requestId) return undefined
      error.value = err
      options.onError?.(err)
      return undefined
    } finally {
      if (id === requestId) loading.value = false
    }
  }

  function reset(): void {
    requestId += 1
    data.value = options.initialData
    error.value = null
    loading.value = false
  }

  onScopeDispose(() => {
    requestId += 1
  })

  if (options.immediate !== false) void execute()

  return {
    data,
    loading,
    error,
    execute,
    refresh: execute,
    reset,
  }
}
