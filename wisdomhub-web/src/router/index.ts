import { createRouter, createWebHistory, type RouteRecordRaw } from 'vue-router'

import { env } from '@/config/env'
import { getSession } from '@/utils/auth-storage'

/**
 * 路由表。
 *
 * 阶段 0 的目标是**把信息架构定下来**，而不是把页面做出来：
 * 每条路由都指向占位页（PlaceholderView）或真实组件，靠 meta 说明它归哪个任务实现。
 * 实现某个任务时，把那一行的 component 换掉即可，导航与布局都不用动。
 *
 * 两条约定：
 *   - 路径按业务划分，与技术文档 5.1 的服务划分对齐（/questions 走 content-service、
 *     /graph 走 knowledge-service……），方便对照后端接口清单。
 *   - `meta.sidebar !== false` 的页面带侧边栏；阅读/编辑/图谱/AI 这类宽内容页显式关掉。
 */

declare module 'vue-router' {
  interface RouteMeta {
    /** 页面标题，同时用于浏览器标签页 */
    title?: string
    /** 这一页归哪个任务实现（阶段 0 里用来标明「还没做好」） */
    task?: string
    /** 一句话说明这页将来做什么 */
    note?: string
    /** 是否显示侧边栏。默认显示，显式写 false 关闭。 */
    sidebar?: boolean
    /**
     * 需要登录才能进（F1.9 的「未登录拦截」）。
     * 由下面的 beforeEach 统一处理：未登录 → 跳登录页并带上 redirect。
     */
    requiresAuth?: boolean
  }
}

const DefaultLayout = () => import('@/layouts/DefaultLayout.vue')
const BlankLayout = () => import('@/layouts/BlankLayout.vue')
const PlaceholderView = () => import('@/views/PlaceholderView.vue')

/**
 * 占位页的简写。
 *
 * 阶段 0 里二十来个页面都是「同一个组件 + 不同 meta」，逐条写 component 只会
 * 把差异淹没在重复里。等某个任务落地时，把这条换成自己的 component 即可。
 */
function placeholder(
  path: string,
  name: string,
  meta: { title: string; task: string; note: string; sidebar?: boolean; requiresAuth?: boolean },
): RouteRecordRaw {
  return { path, name, component: PlaceholderView, meta }
}

export const routes: RouteRecordRaw[] = [
  // ==========================================================================
  // 无外壳：开始页、登录页与设计系统页
  // ⚠️ 必须排在 DefaultLayout 之前：`path: ''` 的根路由只能挂在第一个 `/` 上，否则访问 `/` 匹配不到任何路由。
  // ==========================================================================
  {
    path: '/',
    component: BlankLayout,
    children: [
      {
        path: '',
        name: 'start',
        component: () => import('@/views/StartView.vue'),
        meta: { title: '开始' },
      },
      {
        path: 'login',
        name: 'login',
        component: () => import('@/views/LoginView.vue'),
        meta: { title: '登录' },
      },
      {
        path: 'design',
        name: 'design',
        component: () => import('@/views/DesignSystemView.vue'),
        meta: { title: '设计系统' },
      },
    ],
  },

  // ==========================================================================
  // 默认外壳：顶部导航 + 侧边栏 + 页脚
  // ==========================================================================
  {
    path: '/',
    component: DefaultLayout,
    children: [
      {
        path: 'home',
        name: 'home',
        component: () => import('@/views/HomeView.vue'),
        meta: { title: '首页' },
      },

      // ---- 内容 ----
      {
        path: 'article/:id',
        name: 'article-detail',
        component: () => import('@/views/ArticleDetailView.vue'),
        meta: { title: '文章详情', sidebar: false },
      },
      {
        path: 'editor',
        name: 'article-editor',
        component: () => import('@/views/EditorView.vue'),
        // 写作要登录：草稿与发布接口都会 401
        meta: { title: '写文章', sidebar: false, requiresAuth: true },
      },
      {
        path: 'editor/:id',
        name: 'article-editor-edit',
        component: () => import('@/views/EditorView.vue'),
        meta: { title: '编辑文章', sidebar: false, requiresAuth: true },
      },

      // ---- 标签 ----
      {
        path: 'tags',
        name: 'tag-list',
        component: () => import('@/views/TagListView.vue'),
        meta: { title: '标签' },
      },
      {
        // 用标签名而不是 id：可读、可分享。name → id 的转换在页面里用全量标签做
        path: 'tag/:name',
        name: 'tag-detail',
        component: () => import('@/views/TagDetailView.vue'),
        meta: { title: '标签' },
      },

      // ---- 问答 ----
      {
        path: 'questions',
        name: 'question-list',
        component: () => import('@/views/QuestionListView.vue'),
        meta: { title: '问答' },
      },
      {
        // 静态段优先于 `question/:id`，所以不用怕被模板段吃掉
        path: 'question/ask',
        name: 'question-ask',
        component: () => import('@/views/QuestionAskView.vue'),
        // 提问要登录：后端 createQuestion 首行就取当前用户，匿名必然 401
        meta: { title: '提问', sidebar: false, requiresAuth: true },
      },
      {
        path: 'question/:id',
        name: 'question-detail',
        component: () => import('@/views/QuestionDetailView.vue'),
        meta: { title: '问题详情', sidebar: false },
      },

      // ---- 用户 ----
      {
        path: 'user/:id',
        name: 'user-profile',
        component: () => import('@/views/UserProfileView.vue'),
        meta: { title: '个人主页' },
      },

      // ---- 个人中心。四个子页共用侧边栏作为子导航，都要登录。----
      // 默认落在「我的资料」：点个人中心的第一个期待是看到自己（头像/简介），不是内容列表
      { path: 'me', redirect: '/me/profile' },
      {
        path: 'me/profile',
        name: 'me-profile',
        component: () => import('@/views/MeProfileView.vue'),
        meta: { title: '我的资料', requiresAuth: true },
      },
      {
        path: 'me/articles',
        name: 'me-articles',
        component: () => import('@/views/MeArticlesView.vue'),
        meta: { title: '我的文章', requiresAuth: true },
      },
      {
        path: 'me/drafts',
        name: 'me-drafts',
        component: () => import('@/views/MeDraftsView.vue'),
        meta: { title: '草稿箱', requiresAuth: true },
      },
      {
        path: 'me/favorites',
        name: 'me-favorites',
        component: () => import('@/views/MeFavoritesView.vue'),
        meta: { title: '我的收藏', requiresAuth: true },
      },

      // ---- 搜索 ----
      placeholder('search', 'search', {
        title: '搜索',
        task: 'F2.1',
        note: '关键词高亮、标签/时间过滤、相关性与热度排序。对应 B2.4。',
      }),

      // ---- 知识图谱 ----
      placeholder('graph', 'graph', {
        title: '知识图谱',
        task: 'F2.3',
        note: '节点拖拽、缩放、点击展开邻居、按 category 着色。数据用 B2.12 的 {nodes, edges} DTO。',
        sidebar: false,
      }),
      placeholder('tech/:name', 'tech-detail', {
        title: '技术详情',
        task: 'F2.4',
        note: '技术简介、相关技术树、相关文章、相关问题。对应 B2.11。',
      }),

      // ---- 通知 ----
      placeholder('notifications', 'notifications', {
        title: '通知中心',
        task: 'F2.5',
        note: '未读红点、实时弹出、标记已读。对应 B2.16 与 B2.17。',
        sidebar: false,
        requiresAuth: true,
      }),

      // ---- 付费专栏 ----
      placeholder('columns', 'column-list', {
        title: '专栏',
        task: 'F2.7',
        note: '专栏列表与已购标识。对应 B1.27。',
      }),
      placeholder('column/:id', 'column-detail', {
        title: '专栏详情',
        task: 'F2.7',
        note: '专栏目录、下单入口、未付费时的解锁提示。涉及 Seata 事务（B2.24）。',
      }),

      // ---- AI ----
      placeholder('ai', 'ai-tutor', {
        title: 'AI 技术导师',
        task: 'F3.1',
        note: 'SSE 流式渲染、Markdown、代码高亮、会话切换。走 agent-service 的 /api/agent/**。',
        sidebar: false,
      }),

      // ---- 兜底。必须放在最后，否则会吃掉它之后的所有规则。----
      {
        path: ':pathMatch(.*)*',
        name: 'not-found',
        component: () => import('@/views/NotFoundView.vue'),
        meta: { title: '页面不存在' },
      },
    ],
  },
]

export const router = createRouter({
  /**
   * history 模式（URL 里没有 #）。
   *
   * 代价：直接访问 /design 或刷新页面时请求会先到服务器，
   * 生产环境的 Nginx 必须配 `try_files $uri $uri/ /index.html`，
   * 否则刷新子路由会 404。dev server 已内置这个回退，开发时不会暴露。
   * （部署配置见任务 X3）
   */
  history: createWebHistory(import.meta.env.BASE_URL),
  routes,
  // 切换路由回到顶部；保留浏览器前进/后退时的位置
  scrollBehavior(_to, _from, savedPosition) {
    return savedPosition ?? { top: 0 }
  },
})

/**
 * F1.9 未登录拦截：`meta.requiresAuth` 的页面，未登录一律送去登录页并带上回跳地址。
 *
 * 这里直接读 `utils/auth-storage` 而不是用 Pinia store：会话本来就存在那儿，
 * 而且 store 里的 `userId` 是从 token 解出来的 —— 但 token 有没有、失没失效，
 * 权威只有 auth-storage 这一份。少一层依赖也少一个「谁先初始化」的问题。
 *
 * ⚠️ 这只是**体验层**的拦截：真正的鉴权在网关（验签 + 黑名单）与各服务的属主校验。
 * 前端能拦住导航，拦不住任何人直接调接口。
 */
router.beforeEach((to) => {
  if (!to.meta.requiresAuth) return true
  if (getSession() !== null) return true

  return { path: '/login', query: { redirect: to.fullPath } }
})

// 页面标题：路由 meta.title + 应用名。放在 afterEach 而不是 beforeEach，
// 避免导航被取消时标题已经被改掉。
router.afterEach((to) => {
  document.title = to.meta.title ? `${to.meta.title} · ${env.appTitle}` : env.appTitle
})
