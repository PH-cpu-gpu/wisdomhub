import { createRouter, createWebHistory, type RouteRecordRaw } from 'vue-router'

import AppLayout from '@/layouts/AppLayout.vue'

import { installGuards } from './guards'

const placeholder = () => import('@/views/PlaceholderView.vue')
const home = () => import('@/views/home/HomeView.vue')
const articleList = () => import('@/views/article/ArticleListView.vue')
const articleDetail = () => import('@/views/article/ArticleDetailView.vue')
const editor = () => import('@/views/editor/EditorView.vue')
const columnDetail = () => import('@/views/column/ColumnDetailView.vue')
const login = () => import('@/views/auth/LoginView.vue')
const register = () => import('@/views/auth/RegisterView.vue')
const meSettings = () => import('@/views/me/SettingsView.vue')
const meLibrary = () => import('@/views/me/LibraryView.vue')
const meDrafts = () => import('@/views/me/DraftsView.vue')
const meArticles = () => import('@/views/me/MyArticlesView.vue')
const questionList = () => import('@/views/question/QuestionListView.vue')
const askQuestion = () => import('@/views/question/AskView.vue')
const questionDetail = () => import('@/views/question/QuestionDetailView.vue')
const searchResults = () => import('@/views/search/SearchView.vue')
const tagLibrary = () => import('@/views/tag/TagLibraryView.vue')
const techDetail = () => import('@/views/tag/TechDetailView.vue')
const graphView = () => import('@/views/graph/GraphView.vue')
const notificationView = () => import('@/views/notification/NotificationView.vue')
const meColumns = () => import('@/views/me/MyColumnsView.vue')
const mePurchased = () => import('@/views/me/PurchasedColumnsView.vue')
const meOrders = () => import('@/views/me/MyOrdersView.vue')
const orderPay = () => import('@/views/order/OrderPayView.vue')
const aiWorkbench = () => import('@/views/ai/AiWorkbenchView.vue')
const userProfile = () => import('@/views/user/UserProfileView.vue')
const devUi = () => import('@/views/DevUiView.vue')
const devTokens = () => import('@/views/DevTokensView.vue')
const forbidden = () => import('@/views/ForbiddenView.vue')
const notFound = () => import('@/views/NotFoundView.vue')

const routes: RouteRecordRaw[] = [
  {
    // `/` 由静态落地页接管；SPA 内误跳时回到主页面
    path: '/',
    redirect: { name: 'home' },
  },
  {
    path: '/',
    component: AppLayout,
    children: [
      {
        path: 'home',
        name: 'home',
        component: home,
        meta: {
          title: '首页',
          section: 'HOME',
          task: 'F2.1',
          nav: 'home',
          description: '搜索、热门技术、个性化推荐、热门文章与热门问题。',
        },
      },
      {
        path: 'login',
        name: 'login',
        component: login,
        meta: {
          title: '登录',
          section: 'AUTH',
          task: 'F1.1',
          description: '邮箱 / 手机号 + 密码登录，成功后回跳 redirect。',
        },
      },
      {
        path: 'register',
        name: 'register',
        component: register,
        meta: {
          title: '注册',
          section: 'AUTH',
          task: 'F1.2',
          description: '邮箱 / 手机二选一 + 验证码，注册成功即登录。',
        },
      },
      {
        path: 'articles',
        name: 'article-list',
        component: articleList,
        meta: {
          title: '文章',
          section: 'CONTENT',
          task: 'F2.3',
          nav: 'articles',
          description: '最新 / 最热切换、标签与作者过滤、分页。',
        },
      },
      {
        path: 'article/:articleId',
        name: 'article-detail',
        component: articleDetail,
        meta: {
          title: '文章详情',
          section: 'CONTENT',
          task: 'F2.4',
          nav: 'articles',
          description: '正文、作者、点赞收藏、评论、相关推荐、AI 分析与付费墙。',
        },
      },
      {
        path: 'editor/new',
        name: 'editor-new',
        component: editor,
        meta: {
          title: '写文章',
          section: 'EDITOR',
          task: 'F3.1',
          requiresAuth: true,
          description: 'Markdown 分栏预览、工具栏、自动保存草稿与发布设置。',
        },
      },
      {
        path: 'editor/:articleId',
        name: 'editor-edit',
        component: editor,
        meta: {
          title: '编辑文章',
          section: 'EDITOR',
          task: 'F3.2',
          requiresAuth: true,
          description: '读取 /source，恢复草稿或覆盖发布。',
        },
      },
      {
        path: 'columns',
        name: 'column-list',
        component: placeholder,
        meta: {
          title: '专栏',
          section: 'COLUMN',
          task: 'BE-6',
          nav: 'articles',
          description: '专栏广场依赖后端 BE-6；未获批前保持占位。',
        },
      },
      {
        path: 'column/:columnId',
        name: 'column-detail',
        component: columnDetail,
        meta: {
          title: '专栏详情',
          section: 'COLUMN',
          task: 'F3.7',
          nav: 'articles',
          description: '专栏信息、文章列表（归入时间升序）、购买入口与属主管理。',
        },
      },
      {
        path: 'questions',
        name: 'question-list',
        component: questionList,
        meta: {
          title: '问答',
          section: 'QA',
          task: 'F4.1',
          nav: 'questions',
          description: '最新 / 最热、已解决标记、分页。',
        },
      },
      {
        path: 'questions/:questionId',
        name: 'question-detail',
        component: questionDetail,
        meta: {
          title: '问题详情',
          section: 'QA',
          task: 'F4.3',
          nav: 'questions',
          description: '问题正文、采纳答案置顶、回答分页与收藏。',
        },
      },
      {
        path: 'ask',
        name: 'ask',
        component: askQuestion,
        meta: {
          title: '提问',
          section: 'QA',
          task: 'F4.2',
          nav: 'questions',
          requiresAuth: true,
          description: '标题 + 内容，发布后跳问题详情。',
        },
      },
      {
        path: 'tags',
        name: 'tag-list',
        component: tagLibrary,
        meta: {
          title: '技术',
          section: 'TAG',
          task: 'F5.3',
          nav: 'tech',
          description: '按 category 分组的标签库、热门标签与标签搜索。',
        },
      },
      {
        path: 'tech/:tagId',
        name: 'tech-detail',
        component: techDetail,
        meta: {
          title: '技术详情',
          section: 'KNOWLEDGE',
          task: 'F5.4',
          nav: 'tech',
          description: '技术简介、六类关系分组、前置链、相关文章与相关问题。',
        },
      },
      {
        path: 'graph',
        name: 'graph',
        component: graphView,
        meta: {
          title: '知识图谱',
          section: 'GRAPH',
          task: 'F6.1',
          nav: 'graph',
          description: '总览、邻域展开、拖拽缩放、按 category 着色与节点详情。',
        },
      },
      {
        path: 'graph/tag/:tagId',
        name: 'graph-tag',
        component: graphView,
        meta: {
          title: '图谱邻域',
          section: 'GRAPH',
          task: 'F6.2',
          nav: 'graph',
          description: '单标签邻域、相关文章节点与返回总览。',
        },
      },
      {
        path: 'search',
        name: 'search',
        component: searchResults,
        meta: {
          title: '搜索',
          section: 'SEARCH',
          task: 'F5.2',
          description: '文章 / 问题 / 用户 / 标签四类结果、高亮与过滤。',
        },
      },
      {
        path: 'user/:userId',
        name: 'user-profile',
        component: userProfile,
        meta: {
          title: '个人主页',
          section: 'USER',
          task: 'F1.5',
          description: '资料卡、文章统计、关注 / 粉丝列表与关注按钮。',
        },
      },
      {
        path: 'me/settings',
        name: 'me-settings',
        component: meSettings,
        meta: {
          title: '账号设置',
          section: 'ME',
          task: 'F1.4',
          requiresAuth: true,
          description: '用户名、简介、头像上传与邮箱绑定。',
        },
      },
      {
        path: 'me/articles',
        name: 'me-articles',
        component: meArticles,
        meta: {
          title: '我的文章',
          section: 'ME',
          task: 'F3.5',
          requiresAuth: true,
          description: '已发布文章列表、编辑、查看与删除。',
        },
      },
      {
        path: 'me/drafts',
        name: 'me-drafts',
        component: meDrafts,
        meta: {
          title: '草稿箱',
          section: 'ME',
          task: 'F3.4',
          requiresAuth: true,
          description: '草稿分页、继续编辑与删除。',
        },
      },
      {
        path: 'me/favorites',
        name: 'me-favorites',
        component: meLibrary,
        meta: {
          title: '我的收藏',
          section: 'ME',
          task: 'F2.8',
          requiresAuth: true,
          description: '文章收藏与浏览历史（两个 Tab 分页）。',
        },
      },
      {
        path: 'me/history',
        name: 'me-history',
        component: meLibrary,
        meta: {
          title: '浏览历史',
          section: 'ME',
          task: 'F2.8',
          requiresAuth: true,
          description: '浏览历史分页、单条删除与清空。',
        },
      },
      {
        path: 'me/columns',
        name: 'me-columns',
        component: meColumns,
        meta: {
          title: '我的专栏',
          section: 'ME',
          task: 'F3.6',
          requiresAuth: true,
          description: '创建专栏、我的专栏分页与入口管理。',
        },
      },
      {
        path: 'me/purchased',
        name: 'me-purchased',
        component: mePurchased,
        meta: {
          title: '已购专栏',
          section: 'ME',
          task: 'F3.8',
          requiresAuth: true,
          description: '实付价、购买时间与下架标记。',
        },
      },
      {
        path: 'me/orders',
        name: 'me-orders',
        component: meOrders,
        meta: {
          title: '我的订单',
          section: 'ME',
          task: 'F8.3',
          requiresAuth: true,
          description: '订单分页、状态标签、详情与退款。',
        },
      },
      {
        path: 'notifications',
        name: 'notifications',
        component: notificationView,
        meta: {
          title: '通知',
          section: 'NOTIFICATION',
          task: 'F7.1',
          requiresAuth: true,
          description: '未读高亮、标记已读、全部已读与实时推送。',
        },
      },
      {
        path: 'order/:orderNo',
        name: 'order-detail',
        component: orderPay,
        meta: {
          title: '订单支付',
          section: 'ORDER',
          task: 'F8.2',
          requiresAuth: true,
          description: '15 分钟倒计时、模拟支付、取消与权益开通。',
        },
      },
      {
        path: 'ai',
        name: 'ai',
        component: aiWorkbench,
        meta: {
          title: 'AI 技术导师',
          section: 'AI',
          task: 'F9.1',
          nav: 'ai',
          requiresAuth: true,
          description: 'QA 与学习路线双模式，SSE 流式输出与参考文章。',
        },
      },
      {
        path: 'dev/ui',
        name: 'dev-ui',
        component: devUi,
        meta: {
          title: '组件预览',
          section: 'DEV',
          task: 'F0.4',
          description: '基础组件与 Toast / Confirm 服务的完整预览。',
        },
      },
      {
        path: 'dev/tokens',
        name: 'dev-tokens',
        component: devTokens,
        meta: {
          title: '设计令牌',
          section: 'DEV',
          task: 'F0.2',
          description: '颜色、字体、间距、断点与动效令牌样张。',
        },
      },
      {
        path: '403',
        name: 'forbidden',
        component: forbidden,
        meta: {
          title: '无权限',
          section: 'ERROR',
          description: '当前账号没有访问该资源的权限。',
        },
      },
      {
        path: ':pathMatch(.*)*',
        name: 'not-found',
        component: notFound,
        meta: {
          title: '页面不存在',
          section: 'ERROR',
          description: '页面不存在或已被删除，返回首页继续浏览。',
        },
      },
    ],
  },
]

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes,
  scrollBehavior(to, _from, savedPosition) {
    if (savedPosition) return savedPosition
    if (to.hash) return { el: to.hash, top: 80, behavior: 'smooth' }
    return { top: 0 }
  },
})

installGuards(router)

export default router
