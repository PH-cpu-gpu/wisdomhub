import axios from 'axios'

export interface ObjectUploadOptions {
  url: string
  file: Blob
  headers?: Record<string, string>
  onProgress?: (percent: number) => void
  signal?: AbortSignal
}

const MOCK_TOTAL_TICKS = 20
const MOCK_TICK_MS = 120

function delay(ms: number, signal?: AbortSignal): Promise<void> {
  return new Promise((resolve, reject) => {
    if (signal?.aborted) {
      reject(new DOMException('上传已取消', 'AbortError'))
      return
    }
    const timer = setTimeout(resolve, ms)
    signal?.addEventListener(
      'abort',
      () => {
        clearTimeout(timer)
        reject(new DOMException('上传已取消', 'AbortError'))
      },
      { once: true },
    )
  })
}

/**
 * mock 模式没有真实 COS：用定时器模拟进度，并支持 failUpload 注入。
 * 真实模式下文件直接 PUT 到预签名 URL，不经过业务网关。
 */
async function simulateUpload(options: ObjectUploadOptions): Promise<void> {
  const shouldFail = localStorage.getItem('wisdomhub:mock.failUpload') === '1'
  for (let tick = 1; tick <= MOCK_TOTAL_TICKS; tick += 1) {
    await delay(MOCK_TICK_MS, options.signal)
    if (shouldFail && tick === Math.floor(MOCK_TOTAL_TICKS / 2)) {
      throw new Error('网络中断，上传失败')
    }
    options.onProgress?.(Math.round((tick / MOCK_TOTAL_TICKS) * 100))
  }
}

/**
 * 直传对象存储：独立于业务 http 实例，不带 Authorization、不解包 R。
 * 超时设为 0，交给浏览器与对象存储自己处理长连接。
 */
export async function putObjectToStorage(options: ObjectUploadOptions): Promise<void> {
  if (import.meta.env.VITE_USE_MOCK === 'true') {
    await simulateUpload(options)
    return
  }

  await axios.put(options.url, options.file, {
    headers: options.headers,
    signal: options.signal,
    timeout: 0,
    withCredentials: false,
    onUploadProgress: (event) => {
      if (!event.total) return
      options.onProgress?.(Math.min(100, Math.round((event.loaded / event.total) * 100)))
    },
  })
}
