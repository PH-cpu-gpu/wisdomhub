import { toValue, watchEffect, type MaybeRefOrGetter } from 'vue'

const SITE_NAME = 'WisdomHub'
const DEFAULT_DESCRIPTION =
  'WisdomHub 是面向开发者的技术知识社区。文章、问答、全文搜索、知识图谱、个性推荐与 AI 助手，共用同一张知识网络。'

export interface SeoOptions {
  title?: MaybeRefOrGetter<string | undefined>
  description?: MaybeRefOrGetter<string | undefined>
  image?: MaybeRefOrGetter<string | undefined>
  type?: MaybeRefOrGetter<string | undefined>
}

function ensureMeta(attr: 'name' | 'property', key: string): HTMLMetaElement {
  let element = document.head.querySelector<HTMLMetaElement>(`meta[${attr}="${key}"]`)
  if (!element) {
    element = document.createElement('meta')
    element.setAttribute(attr, key)
    document.head.appendChild(element)
  }
  return element
}

function setMeta(attr: 'name' | 'property', key: string, content?: string): void {
  if (!content) return
  ensureMeta(attr, key).setAttribute('content', content)
}

function removeMeta(attr: 'name' | 'property', key: string): void {
  document.head.querySelector<HTMLMetaElement>(`meta[${attr}="${key}"]`)?.remove()
}

function withSuffix(title?: string): string {
  if (!title) return SITE_NAME
  return title.includes(SITE_NAME) ? title : `${title} · ${SITE_NAME}`
}

/**
 * 页面级 title / description / Open Graph。
 * router.afterEach 负责基础标题，页面需要更具体的描述时调用本 composable。
 */
export function useSeo(options: SeoOptions): void {
  watchEffect(() => {
    const title = toValue(options.title)
    const description = toValue(options.description) ?? DEFAULT_DESCRIPTION
    const image = toValue(options.image)
    const type = toValue(options.type) ?? 'website'
    const fullTitle = withSuffix(title)

    document.title = fullTitle
    setMeta('name', 'description', description)
    setMeta('property', 'og:title', fullTitle)
    setMeta('property', 'og:description', description)
    setMeta('property', 'og:type', type)

    if (image) setMeta('property', 'og:image', image)
    else removeMeta('property', 'og:image')
  })
}
