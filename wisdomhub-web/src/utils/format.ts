/**
 * 展示层格式化。
 *
 * ⚠️ 时间一律是后端给的 UTC ISO 串（带 `Z`，见技术文档 17.2），
 * `new Date(iso)` 会正确解析成浏览器本地时区 —— 不要自己拼字符串或手动 ±8 小时。
 */

/**
 * 数字缩写：1234 → 1.2k、1234567 → 1.2m。
 * 列表里的阅读量/点赞数用它压宽度，完整值放 `title` 属性里。
 */
export function formatCount(value: number | null | undefined): string {
  const n = value ?? 0
  if (n < 1000) return String(n)
  if (n < 1_000_000) return `${trimZero(n / 1000)}k`
  return `${trimZero(n / 1_000_000)}m`
}

function trimZero(n: number): string {
  // 1.0k 读起来比 1k 别扭
  return n.toFixed(1).replace(/\.0$/, '')
}

const MINUTE = 60_000
const HOUR = 60 * MINUTE
const DAY = 24 * HOUR

/**
 * 相对时间：刚刚 / 5 分钟前 / 3 小时前 / 2 天前，超过 7 天直接给日期。
 * 列表里用相对时间，鼠标悬停能看到绝对时间（`formatDateTime`）。
 */
export function formatRelativeTime(iso: string | null | undefined): string {
  if (!iso) return ''
  const time = new Date(iso).getTime()
  if (Number.isNaN(time)) return ''

  const diff = Date.now() - time
  if (diff < MINUTE) return '刚刚'
  if (diff < HOUR) return `${Math.floor(diff / MINUTE)} 分钟前`
  if (diff < DAY) return `${Math.floor(diff / HOUR)} 小时前`
  if (diff < 7 * DAY) return `${Math.floor(diff / DAY)} 天前`

  return formatDateTime(iso).slice(0, 10)
}

/** 本地时区的 `YYYY-MM-DD HH:mm`，用于 title 属性 */
export function formatDateTime(iso: string | null | undefined): string {
  if (!iso) return ''
  const d = new Date(iso)
  if (Number.isNaN(d.getTime())) return ''

  const pad = (n: number) => String(n).padStart(2, '0')
  return (
    `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ` +
    `${pad(d.getHours())}:${pad(d.getMinutes())}`
  )
}
