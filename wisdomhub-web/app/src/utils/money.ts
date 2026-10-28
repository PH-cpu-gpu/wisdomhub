export interface MoneyOptions {
  /** 0 时显示的文案，如「免费」；不传则显示 ¥0.00 */
  freeLabel?: string
  symbol?: string
}

/** 分 → 元；0 可按需显示「免费」 */
export function formatMoney(value?: number | null, options: MoneyOptions = {}): string {
  if (value === null || value === undefined || Number.isNaN(value)) return '—'
  if (value === 0 && options.freeLabel) return options.freeLabel
  const symbol = options.symbol ?? '¥'
  return `${symbol}${(value / 100).toFixed(2)}`
}

/** 表单里用户输入的元 → 分；非法或负数返回 null */
export function parseYuanToCents(value: string | number): number | null {
  const amount = typeof value === 'number' ? value : Number(value)
  if (!Number.isFinite(amount) || amount < 0) return null
  return Math.round(amount * 100)
}
