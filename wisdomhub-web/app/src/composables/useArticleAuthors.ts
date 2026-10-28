import { toValue, watch, type MaybeRefOrGetter } from 'vue'

import type { ArticleListItemVO } from '@/types/content'

import { useAuthorBrief } from './useAuthorBrief'

export type ArticleAuthorSource = MaybeRefOrGetter<ArticleListItemVO[] | null | undefined>

/**
 * 文章列表作者批取：监听一个或多个文章列表，把 authorId 交给 useAuthorBrief 统一批取并缓存。
 * 推荐 / 热门 / 最新等多个区块共用时只发一轮 /profile/brief。
 */
export function useArticleAuthors(...sources: ArticleAuthorSource[]) {
  const { authors, loading, ensureAuthors } = useAuthorBrief()

  for (const source of sources) {
    watch(
      () => toValue(source),
      (items) => {
        if (!items?.length) return
        void ensureAuthors([...new Set(items.map((item) => item.authorId))])
      },
      { immediate: true },
    )
  }

  return { authors, loading }
}
