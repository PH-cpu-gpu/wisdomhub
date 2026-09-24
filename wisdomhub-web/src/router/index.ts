import { createRouter, createWebHistory, type RouteRecordRaw } from 'vue-router'

import { env } from '@/config/env'

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
  meta: { title: string; task: string; note: string; sidebar?: boolean },
): RouteRecordRaw {
  return { path, name, component: PlaceholderView, meta }
}

export const routes: RouteRecordRaw[] = [
  // ==========================================================================
  // 默认外壳：顶部导航 + 侧边栏 + 页脚
  // ==========================================================================
  {
    path: '/',
    component: DefaultLayout,
    children: [
      {
        path: '',
        name: 'home',
        component: PlaceholderView,
        meta: {
          title: '首页',
          task: 'F1.2',
          note: '文章列表：分页、按最新/最热排序、骨架屏。数据来自 B1.23 的文章列表接口。',
        },
      },

      // ---- 内容 ----
      placeholder('article/:id', 'article-detail', {
        title: '文章详情',
        task: 'F1.4',
        note: 'Markdown 正文渲染、代码高亮、二级评论、点赞收藏。对应 B1.21 与 B1.25。',
        sidebar: false,
      }),
      placeholder('editor', 'article-editor', {
        title: '写文章',
        task: 'F1.3',
        note: 'Markdown 编辑 + 实时预览 + 自动存草稿 + 发布。对应 B1.18 与 B1.20。',
        sidebar: false,
      }),
      placeholder('editor/:id', 'article-editor-edit', {
        title: '编辑文章',
        task: 'F1.3',
        note: '在已有文章上继续编辑，每次发布生成一个版本（B1.19）。',
        sidebar: false,
      }),

      // ---- 标签 ----
      placeholder('tags', 'tag-list', {
        title: '标签',
        task: 'F1.5',
        note: '标签云与热门标签。热门标签走 B1.4 的多级缓存。',
      }),
      placeholder('tag/:name', 'tag-detail', {
        title: '标签详情',
        task: 'F1.5',
        note: '某个标签下的文章列表，按标签筛文章。',
      }),

      // ---- 问答 ----
      placeholder('questions', 'question-list', {
        title: '问答',
        task: 'F1.7',
        note: '问题列表与热门问题。对应 B1.28 的问答接口。',
      }),
      placeholder('question/ask', 'question-ask', {
        title: '提问',
        task: 'F1.7',
        note: '提问表单：标题、正文、标签。',
        sidebar: false,
      }),
      placeholder('question/:id', 'question-detail', {
        title: '问题详情',
        task: 'F1.7',
        note: '回答列表、回答点赞、问题收藏。',
        sidebar: false,
      }),

      // ---- 用户 ----
      placeholder('user/:id', 'user-profile', {
        title: '个人主页',
        task: 'F1.6',
        note: '资料、文章列表、关注/粉丝、关注按钮。对应 B1.11 与 B1.12。',
      }),

      // ---- 个人中心。三个子页共用侧边栏作为子导航。----
      { path: 'me', redirect: '/me/articles' },
      placeholder('me/articles', 'me-articles', {
        title: '我的文章',
        task: 'F1.8',
        note: '我发布的文章与草稿入口。',
      }),
      placeholder('me/drafts', 'me-drafts', {
        title: '草稿箱',
        task: 'F1.8',
        note: '编辑中自动保存的草稿，可以继续编辑或丢弃（B1.20）。',
      }),
      placeholder('me/favorites', 'me-favorites', {
        title: '我的收藏',
        task: 'F1.8',
        note: '收藏的文章，可以取消收藏。对应 B1.26。',
      }),

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

  // ==========================================================================
  // 无外壳：登录页与设计系统页
  // ==========================================================================
  {
    path: '/',
    component: BlankLayout,
    children: [
      placeholder('login', 'login', {
        title: '登录 / 注册',
        task: 'F1.1',
        note: '只收手机号或邮箱（没有用户名登录入口），首次登录引导设置用户名与密码。对应 B1.7 与 B1.8。',
      }),
      {
        path: 'design',
        name: 'design',
        component: () => import('@/views/DesignSystemView.vue'),
        meta: { title: '设计系统' },
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

// 页面标题：路由 meta.title + 应用名。放在 afterEach 而不是 beforeEach，
// 避免导航被取消时标题已经被改掉。
router.afterEach((to) => {
  document.title = to.meta.title ? `${to.meta.title} · ${env.appTitle}` : env.appTitle
})
