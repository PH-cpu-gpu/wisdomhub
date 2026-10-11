import type { Tag } from '@/api/tag'

/**
 * 标签字典的分组顺序（后端 tag.category）。
 *
 * ⚠️ 与 config/design-tokens.ts 的 TECH_CATEGORIES **无关** —— 那是 Neo4j 知识图谱
 * `Technology.category` 的枚举（LANGUAGE/FRAMEWORK/…），两套词表不通用。
 *
 * 分组名本身存在库里（中文，见 docker/mysql/init/23-content.sql），这里只定**展示顺序**：
 * 后端加一个新分组不用改这个文件，它会兜到最后一组；只有改名 / 删组才要动。
 */
export const TAG_CATEGORY_ORDER = [
  '编程语言',
  '计算机基础',
  '后端与框架',
  '架构与分布式',
  '前端',
  '数据库',
  '中间件与运维',
  '人工智能与数据',
  '移动与嵌入式',
  '开发实践',
] as const

export interface TagGroup {
  category: string
  tags: Tag[]
}

/** 未登记 / 空的分组名都兜进这个桶。它不在上面的顺序表里，所以永远排在最后一组 */
const UNKNOWN_CATEGORY = '其他'

/** 把全量标签按分组切好，空组不返回。组内顺序沿用接口给的 id ASC */
export function groupTags(tags: Tag[]): TagGroup[] {
  const known = new Set<string>(TAG_CATEGORY_ORDER)
  const buckets = new Map<string, Tag[]>(TAG_CATEGORY_ORDER.map((name) => [name, []]))

  for (const tag of tags) {
    const key = tag.category && known.has(tag.category) ? tag.category : UNKNOWN_CATEGORY
    const bucket = buckets.get(key)
    if (bucket) {
      bucket.push(tag)
    } else {
      // 第一次遇到才建桶 —— Map 的插入顺序就是展示顺序，所以兜底组必然在最后
      buckets.set(key, [tag])
    }
  }

  return [...buckets.entries()]
    .filter(([, list]) => list.length > 0)
    .map(([category, list]) => ({ category, tags: list }))
}
