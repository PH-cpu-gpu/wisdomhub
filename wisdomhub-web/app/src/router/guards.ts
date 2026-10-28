import type { Router } from 'vue-router'

import { toast } from '@/components/base/toast'
import { pinia, useAuthStore } from '@/stores'

/**
 * F1.3 起守卫主动续期：access 过期但 refresh 存在时先刷新，失败才清 token 跳登录。
 */
export function installGuards(router: Router): void {
  router.beforeEach(async (to) => {
    const auth = useAuthStore(pinia)
    const hadSession = Boolean(auth.accessToken || auth.refreshToken)
    const authed = await auth.ensureSession()

    if (!authed && hadSession && to.meta.requiresAuth) {
      toast.warning('登录已过期，请重新登录')
    }

    if (to.meta.requiresAuth && !authed) {
      return { name: 'login', query: { redirect: to.fullPath } }
    }

    if (authed && (to.name === 'login' || to.name === 'register')) {
      return { name: 'home' }
    }

    return true
  })

  router.afterEach((to) => {
    const title = to.meta.title ? `${to.meta.title} · WisdomHub` : 'WisdomHub'
    document.title = title

    const description =
      typeof to.meta.description === 'string' && to.meta.description
        ? to.meta.description
        : DEFAULT_DESCRIPTION
    setMeta('description', description)
    setMeta('og:title', title)
    setMeta('og:description', description)
  })
}

const DEFAULT_DESCRIPTION =
  'WisdomHub 是面向开发者的技术知识社区。文章、问答、全文搜索、知识图谱、个性推荐与 AI 助手，共用同一张知识网络。'

/** 路由级兜底：页面挂载后 useSeo 会用页面数据覆盖 */
function setMeta(name: string, content: string): void {
  const attr = name.startsWith('og:') ? 'property' : 'name'
  let element = document.head.querySelector<HTMLMetaElement>(`meta[${attr}="${name}"]`)
  if (!element) {
    element = document.createElement('meta')
    element.setAttribute(attr, name)
    document.head.appendChild(element)
  }
  element.setAttribute('content', content)
}
