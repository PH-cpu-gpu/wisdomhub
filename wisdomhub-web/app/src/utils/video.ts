/** 视频相关的展示格式化，列表/详情/上传页共用 */

export function formatBytes(bytes: number | string | null | undefined): string {
  const value = typeof bytes === 'string' ? Number(bytes) : bytes
  if (value == null || !Number.isFinite(value) || value <= 0) return '0 B'
  const units = ['B', 'KB', 'MB', 'GB']
  let size = value
  let unit = 0
  while (size >= 1024 && unit < units.length - 1) {
    size /= 1024
    unit += 1
  }
  return `${size >= 10 || unit === 0 ? Math.round(size) : size.toFixed(1)} ${units[unit]}`
}

/** 毫秒 → m:ss；空值统一给「—」；后端 Long 可能以字符串下发 */
export function formatVideoDuration(ms: number | string | null | undefined): string {
  const value = typeof ms === 'string' ? Number(ms) : ms
  if (value == null || !Number.isFinite(value) || value <= 0) return '—'
  const totalSeconds = Math.round(value / 1000)
  const minutes = Math.floor(totalSeconds / 60)
  const seconds = totalSeconds % 60
  return `${minutes}:${String(seconds).padStart(2, '0')}`
}
