import dayjs from 'dayjs'
import 'dayjs/locale/zh-cn'
import relativeTime from 'dayjs/plugin/relativeTime'
import utc from 'dayjs/plugin/utc'

dayjs.extend(relativeTime)
dayjs.extend(utc)
dayjs.locale('zh-cn')

const EMPTY = '—'

function parse(value?: string | number | Date | null) {
  if (value === null || value === undefined || value === '') return null
  const parsed = dayjs(value)
  return parsed.isValid() ? parsed : null
}

/** 后端 UTC 时间 → 本地时区展示；空值返回 — */
export function formatDateTime(
  value?: string | number | Date | null,
  template = 'YYYY-MM-DD HH:mm',
): string {
  const parsed = parse(value)
  return parsed ? parsed.format(template) : EMPTY
}

export function formatDate(value?: string | number | Date | null): string {
  return formatDateTime(value, 'YYYY-MM-DD')
}

/** 相对时间：刚刚 / 3 分钟前 / 2 小时前 */
export function formatRelativeTime(value?: string | number | Date | null): string {
  const parsed = parse(value)
  if (!parsed) return EMPTY
  const seconds = Math.abs(dayjs().diff(parsed, 'second'))
  if (seconds < 60) return '刚刚'
  return parsed.fromNow()
}

/**
 * 本地时间 → 后端 LocalDateTime 需要的 UTC 墙钟字符串（不带 Z）。
 * 搜索的 startTime / endTime 用这个格式，避免 Jackson 二次换算。
 */
export function toBackendDateTime(value: Date | string | number): string {
  const parsed = dayjs(value)
  return parsed.isValid() ? parsed.utc().format('YYYY-MM-DDTHH:mm:ss') : ''
}
