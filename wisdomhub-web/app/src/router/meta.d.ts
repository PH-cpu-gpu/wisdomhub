import 'vue-router'

declare module 'vue-router' {
  interface RouteMeta {
    /** 页面标题，用于 document.title 与占位页 */
    title?: string
    /** 占位页左上角的栏目缩写，如 HOME / AUTH / CONTENT */
    section?: string
    /** 对应开发计划里的任务号，如 F2.1 */
    task?: string
    description?: string
    /** 未登录时跳转登录页 */
    requiresAuth?: boolean
    /** 顶部导航高亮项 */
    nav?: 'home' | 'articles' | 'questions' | 'tech' | 'graph' | 'ai'
  }
}

export {}
