import { del, put } from '@/api/http'
import type { ID } from '@/types/common'

export function like(articleId: ID): Promise<number> {
  return put<number>(`/api/content/article/${articleId}/like`)
}

export function unlike(articleId: ID): Promise<number> {
  return del<number>(`/api/content/article/${articleId}/like`)
}

export function collect(articleId: ID): Promise<number> {
  return put<number>(`/api/content/article/${articleId}/collect`)
}

export function uncollect(articleId: ID): Promise<number> {
  return del<number>(`/api/content/article/${articleId}/collect`)
}
