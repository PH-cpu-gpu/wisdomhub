/**
 * 设计令牌的 TS 侧出口。
 *
 * 颜色值不在这里再写一遍 —— CSS 变量（src/styles/tokens.css）是唯一事实来源。
 * 重复一份 hex 出来，改色时必然会漏掉一边；而这里用 readToken() 现读，
 * 换主题后取到的值也会跟着变，图谱节点重绘不需要额外处理。
 */

/**
 * 技术类别 —— 对应技术文档 7.13 的 category 枚举，也是 Neo4j 上
 * `Technology.category` 的取值。前端只做展示，不要在这里增删，
 * 后端（B2.7/B2.9）改了这里要同步。
 */
export const TECH_CATEGORIES = [
  { value: 'LANGUAGE', label: '编程语言', token: '--wh-cat-language' },
  { value: 'FRAMEWORK', label: '框架', token: '--wh-cat-framework' },
  { value: 'MIDDLEWARE', label: '中间件', token: '--wh-cat-middleware' },
  { value: 'DATABASE', label: '数据库', token: '--wh-cat-database' },
  { value: 'CONCEPT', label: '概念', token: '--wh-cat-concept' },
] as const

export type TechCategory = (typeof TECH_CATEGORIES)[number]['value']

const CATEGORY_MAP = new Map<string, (typeof TECH_CATEGORIES)[number]>(
  TECH_CATEGORIES.map((c) => [c.value, c]),
)

/** 类别的中文名。遇到未知取值时原样返回，不抛错 —— 图谱数据来自后端，
 *  后端加了新类别不该让整个页面白屏。 */
export function techCategoryLabel(value: string): string {
  return CATEGORY_MAP.get(value)?.label ?? value
}

/** 类别对应的 CSS 变量名，用于 `var(...)` 或 readToken() */
export function techCategoryToken(value: string): string | undefined {
  return CATEGORY_MAP.get(value)?.token
}

/**
 * 读取一个 CSS 变量的当前值。
 *
 * 用于需要真实色值的场景（Canvas 绘图、图谱配色），普通样式直接写
 * `var(--wh-xxx)` 即可，不需要经过这里。
 *
 * 注意返回的是**当前主题下**的值：暗色/亮色切换后必须重新读一次，
 * 缓存下来的色值不会自己更新。
 */
export function readToken(name: string, el: HTMLElement = document.documentElement): string {
  return getComputedStyle(el).getPropertyValue(name).trim()
}

/** 一次读出全部技术类别色，键是 category 值 */
export function readCategoryColors(): Record<string, string> {
  const result: Record<string, string> = {}
  for (const { value, token } of TECH_CATEGORIES) {
    result[value] = readToken(token)
  }
  return result
}
