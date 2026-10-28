import { AxiosHeaders, type AxiosResponse, type InternalAxiosRequestConfig } from 'axios'

import type { PageResponse, R } from '@/types/common'
import type { ArticleListItemVO, ColumnVO, CommentVO } from '@/types/content'
import type {
  GraphEdgeVO,
  GraphNodeVO,
  GraphRelation,
  GraphVO,
  PrerequisiteTreeVO,
  RelationGroupVO,
  TagDetailVO,
  TagRelationsVO,
} from '@/types/knowledge'
import type { OrderVO } from '@/types/order'
import type { QuestionListItemVO } from '@/types/qa'
import type { NotificationVO } from '@/types/notification'
import type { ArticleSearchVO, QuestionSearchVO, TagSearchVO, UserSearchVO } from '@/types/search'
import type { BrowseHistoryVO } from '@/types/user'
import {
  mockArticleBriefs,
  mockArticleDetail,
  mockAnswers,
  mockArticles,
  mockAuthors,
  MOCK_AVATAR_URL,
  mockColumnArticleIds,
  mockColumns,
  mockComments,
  mockHotTags,
  mockLogin,
  mockPaidColumns,
  mockProfile,
  mockPrerequisites,
  mockPurchasedColumns,
  mockQuestionContents,
  mockQuestions,
  mockRecommendations,
  mockSocialUsers,
  mockTagRelations,
  mockTags,
  MOCK_ARTICLE_MARKDOWN,
  mockToggleArticleReaction,
  type MockColumn,
} from './fixtures'

const MOCK_DELAY = 180
/** Mock 永远视为已登录的 1001 */
const MOCK_USER_ID = '1001'
const mockFollowing = new Set<string>()
const mockFavoriteArticleIds = new Set<string>([
  '1001',
  '1002',
  '1003',
  '1004',
  '1007',
  '1008',
  '1013',
])
const mockHistoryEntries = [
  { articleId: '1002', updateTime: '2026-10-26T09:30:00Z' },
  { articleId: '1003', updateTime: '2026-10-26T08:10:00Z' },
  { articleId: '1004', updateTime: '2026-10-25T20:40:00Z' },
  { articleId: '1005', updateTime: '2026-10-25T18:15:00Z' },
  { articleId: '1006', updateTime: '2026-10-25T10:20:00Z' },
  { articleId: '1009', updateTime: '2026-10-24T21:05:00Z' },
  { articleId: '1010', updateTime: '2026-10-24T16:45:00Z' },
  { articleId: '1013', updateTime: '2026-10-24T12:00:00Z' },
]

const mockColumnArticleMap = new Map<string, string[]>(
  Object.entries(mockColumnArticleIds).map(([columnId, articleIds]) => [columnId, [...articleIds]]),
)
let mockColumnSequence = 8100

function columnVO(column: MockColumn, purchased: boolean | null = column.purchased): ColumnVO {
  return {
    columnId: column.columnId,
    userId: column.userId,
    title: column.title,
    description: column.description,
    cover: column.cover,
    price: column.price,
    articleCount: column.articleCount,
    purchased,
    createTime: column.createTime,
  }
}

function articlesOfColumn(columnId: string): ArticleListItemVO[] {
  const ids = mockColumnArticleMap.get(columnId) ?? []
  return ids
    .map((articleId) => mockArticles.find((article) => article.articleId === articleId))
    .filter((article): article is ArticleListItemVO => Boolean(article))
}

const MOCK_ORDERS_KEY = 'wisdomhub:mock.orders'
const MOCK_ORDER_SEQ_KEY = 'wisdomhub:mock.orderSeq'

function loadMockOrders(): Map<string, OrderVO> {
  const map = new Map<string, OrderVO>()
  try {
    const raw = localStorage.getItem(MOCK_ORDERS_KEY)
    if (raw) {
      for (const [key, value] of Object.entries(JSON.parse(raw) as Record<string, OrderVO>)) {
        map.set(key, value)
      }
    }
  } catch {
    // Ignore malformed mock state and start without orders.
  }
  return map
}

const mockOrders = loadMockOrders()
let mockOrderSequence =
  Number(localStorage.getItem(MOCK_ORDER_SEQ_KEY) ?? 202610270000) || 202610270000

function persistMockOrders(): void {
  localStorage.setItem(MOCK_ORDERS_KEY, JSON.stringify(Object.fromEntries(mockOrders)))
}

const MOCK_OWNED_COLUMNS_KEY = 'wisdomhub:mock.ownedColumns'

function loadMockOwnedColumns(): Set<string> {
  try {
    const raw = localStorage.getItem(MOCK_OWNED_COLUMNS_KEY)
    return raw ? new Set(JSON.parse(raw) as string[]) : new Set()
  } catch {
    return new Set()
  }
}

const mockOwnedColumns = loadMockOwnedColumns()

function applyMockOwnedColumns(): void {
  for (const columnId of mockOwnedColumns) {
    const column = mockColumns.find((item) => item.columnId === columnId)
    if (column) column.purchased = true
    const demo = mockPaidColumns.find((item) => item.columnId === columnId)
    if (demo) demo.purchased = true
  }
}

function markMockColumnOwned(columnId: string): void {
  mockOwnedColumns.add(columnId)
  localStorage.setItem(MOCK_OWNED_COLUMNS_KEY, JSON.stringify([...mockOwnedColumns]))
  applyMockOwnedColumns()
}

function unmarkMockColumnOwned(columnId: string): void {
  mockOwnedColumns.delete(columnId)
  localStorage.setItem(MOCK_OWNED_COLUMNS_KEY, JSON.stringify([...mockOwnedColumns]))
  const column = mockColumns.find((item) => item.columnId === columnId)
  if (column) column.purchased = false
  const demo = mockPaidColumns.find((item) => item.columnId === columnId)
  if (demo) demo.purchased = false
}

applyMockOwnedColumns()

interface MockDraft {
  articleId: string
  title: string
  summary: string
  markdown: string
  state: 1 | 2 | 3
  tagIds: string[]
  updateTime: string
}

const mockDrafts = new Map<string, MockDraft>()
try {
  const rawDrafts = localStorage.getItem('wisdomhub:mock.drafts')
  if (rawDrafts) {
    for (const [key, value] of Object.entries(JSON.parse(rawDrafts) as Record<string, MockDraft>)) {
      mockDrafts.set(key, value)
    }
  }
} catch {
  // Ignore malformed mock state and start from an empty draft box.
}

function persistMockDrafts(): void {
  localStorage.setItem('wisdomhub:mock.drafts', JSON.stringify(Object.fromEntries(mockDrafts)))
}

const MOCK_PUBLISHED_SOURCES_KEY = 'wisdomhub:mock.publishedSources'
function loadMockPublishedSources(): Map<string, MockDraft> {
  const map = new Map<string, MockDraft>()
  try {
    const raw = localStorage.getItem(MOCK_PUBLISHED_SOURCES_KEY)
    if (raw) {
      for (const [key, value] of Object.entries(JSON.parse(raw) as Record<string, MockDraft>)) {
        map.set(key, value)
      }
    }
  } catch {
    // Ignore malformed mock state and fall back to the seeded article source.
  }
  return map
}

const mockPublishedSources = loadMockPublishedSources()
const mockQuestionContent = new Map<string, string>()
const mockQuestionAccepted: Record<string, string | null> = {
  '4001': '6001',
  '4002': '6101',
}
const MOCK_QUESTION_FAVORITES_KEY = 'wisdomhub:mock.questionFavorites'

function loadMockQuestionFavorites(): Set<string> {
  try {
    const raw = localStorage.getItem(MOCK_QUESTION_FAVORITES_KEY)
    return raw ? new Set(JSON.parse(raw) as string[]) : new Set()
  } catch {
    return new Set()
  }
}

const MOCK_READ_NOTIFICATIONS_KEY = 'wisdomhub:mock.readNotifications'
const mockNotifications: NotificationVO[] = [
  {
    notificationId: '7013',
    type: 2,
    targetType: 2,
    targetId: '6001',
    senderId: '2002',
    content: null,
    read: false,
    createTime: '2026-10-27T10:00:00Z',
  },
  {
    notificationId: '7001',
    type: 1,
    targetType: 1,
    targetId: '1001',
    senderId: '2001',
    content: null,
    read: false,
    createTime: '2026-10-27T09:30:00Z',
  },
  {
    notificationId: '7002',
    type: 2,
    targetType: 1,
    targetId: '1002',
    senderId: '2002',
    content: null,
    read: false,
    createTime: '2026-10-27T08:20:00Z',
  },
  {
    notificationId: '7003',
    type: 3,
    targetType: 3,
    targetId: '4001',
    senderId: '2001',
    content: null,
    read: false,
    createTime: '2026-10-26T21:10:00Z',
  },
  {
    notificationId: '7004',
    type: 4,
    targetType: 5,
    targetId: '3009',
    senderId: '2002',
    content: null,
    read: false,
    createTime: '2026-10-26T18:00:00Z',
  },
  {
    notificationId: '7005',
    type: 5,
    targetType: 4,
    targetId: '5001',
    senderId: '2001',
    content: null,
    read: false,
    createTime: '2026-10-26T12:00:00Z',
  },
  {
    notificationId: '7006',
    type: 6,
    targetType: 1,
    targetId: '1001',
    senderId: '2001',
    content: null,
    read: true,
    createTime: '2026-10-25T20:00:00Z',
  },
  {
    notificationId: '7007',
    type: 7,
    targetType: 1,
    targetId: '1001',
    senderId: null,
    content: '系统维护通知：今晚 23:00 - 23:30 将进行短时维护。',
    read: false,
    createTime: '2026-10-25T18:00:00Z',
  },
  {
    notificationId: '7008',
    type: 1,
    targetType: 1,
    targetId: '1003',
    senderId: '2001',
    content: null,
    read: false,
    createTime: '2026-10-25T16:00:00Z',
  },
  {
    notificationId: '7009',
    type: 2,
    targetType: 1,
    targetId: '1004',
    senderId: '2001',
    content: null,
    read: false,
    createTime: '2026-10-25T10:00:00Z',
  },
  {
    notificationId: '7010',
    type: 3,
    targetType: 3,
    targetId: '4002',
    senderId: '2002',
    content: null,
    read: false,
    createTime: '2026-10-24T19:00:00Z',
  },
  {
    notificationId: '7011',
    type: 1,
    targetType: 2,
    targetId: '6002',
    senderId: '2002',
    content: null,
    read: true,
    createTime: '2026-10-24T09:00:00Z',
  },
  {
    notificationId: '7012',
    type: 2,
    targetType: 1,
    targetId: '1006',
    senderId: '2002',
    content: null,
    read: true,
    createTime: '2026-10-23T14:00:00Z',
  },
]

function loadMockReadNotifications(): Set<string> {
  try {
    const raw = localStorage.getItem(MOCK_READ_NOTIFICATIONS_KEY)
    return raw ? new Set(JSON.parse(raw) as string[]) : new Set()
  } catch {
    return new Set()
  }
}

const mockReadNotifications = loadMockReadNotifications()

function isNotificationRead(notification: NotificationVO): boolean {
  return notification.read || mockReadNotifications.has(notification.notificationId)
}

function persistMockReadNotifications(): void {
  localStorage.setItem(MOCK_READ_NOTIFICATIONS_KEY, JSON.stringify([...mockReadNotifications]))
}

const mockQuestionFavorites = loadMockQuestionFavorites()

function persistMockQuestionFavorites(): void {
  localStorage.setItem(MOCK_QUESTION_FAVORITES_KEY, JSON.stringify([...mockQuestionFavorites]))
}
const mockAnswerComments: Record<string, CommentVO[]> = {
  '6001': [
    {
      commentId: '5101',
      userId: '1001',
      parentId: null,
      content: '拆到独立 Bean 之后事务确实生效了，感谢。',
      createTime: '2026-10-21T11:00:00Z',
      replies: [],
    },
    {
      commentId: '5102',
      userId: '2001',
      parentId: null,
      content: '补充：AopContext 方案需要开启 exposeProxy。',
      createTime: '2026-10-21T12:30:00Z',
      replies: [
        {
          commentId: '5103',
          userId: '2002',
          parentId: '5102',
          content: '这个细节很容易漏，记下了。',
          createTime: '2026-10-21T13:10:00Z',
          replies: [],
        },
      ],
    },
  ],
  '6101': [
    {
      commentId: '5111',
      userId: '1001',
      parentId: null,
      content: 'toRefs 这个点很关键，之前就是解构丢了响应性。',
      createTime: '2026-10-19T07:00:00Z',
      replies: [],
    },
  ],
}
const mockAnswerLikes = new Set<string>()
let mockAnswerSequence = 7000
const MOCK_CREATED_QUESTIONS_KEY = 'wisdomhub:mock.createdQuestions'

function loadMockCreatedQuestions(): QuestionListItemVO[] {
  try {
    const raw = localStorage.getItem(MOCK_CREATED_QUESTIONS_KEY)
    return raw ? (JSON.parse(raw) as QuestionListItemVO[]) : []
  } catch {
    return []
  }
}

const mockCreatedQuestions = loadMockCreatedQuestions()

function persistMockCreatedQuestions(): void {
  localStorage.setItem(MOCK_CREATED_QUESTIONS_KEY, JSON.stringify(mockCreatedQuestions))
}

let mockQuestionSequence =
  Number(localStorage.getItem('wisdomhub:mock.questionSeq') ?? 4006) || 4006
const seedArticle = mockArticles.find((item) => item.articleId === '1001')
if (!mockPublishedSources.has('1001') && seedArticle) {
  mockPublishedSources.set('1001', {
    articleId: '1001',
    title: seedArticle.title,
    summary: seedArticle.summary,
    markdown: MOCK_ARTICLE_MARKDOWN,
    state: 2,
    tagIds: ['4', '3'],
    updateTime: '2026-10-20T10:00:00Z',
  })
}
for (const article of mockArticles) {
  if (article.authorId === '1001' && !mockPublishedSources.has(article.articleId)) {
    mockPublishedSources.set(article.articleId, {
      articleId: article.articleId,
      title: article.title,
      summary: article.summary,
      markdown: MOCK_ARTICLE_MARKDOWN,
      state: 2,
      tagIds: [],
      updateTime: article.publishTime,
    })
  }
}

function persistMockPublishedSources(): void {
  localStorage.setItem(
    MOCK_PUBLISHED_SOURCES_KEY,
    JSON.stringify(Object.fromEntries(mockPublishedSources)),
  )
}

let mockDraftSequence = Number(localStorage.getItem('wisdomhub:mock.draftSeq') ?? 9000) || 9000
let mockArticleSequence =
  Number(localStorage.getItem('wisdomhub:mock.articleSeq') ?? 10000) || 10000
let mockCommentSequence = 6000

function delay(ms: number): Promise<void> {
  return new Promise((resolve) => setTimeout(resolve, ms))
}

function response<T>(config: InternalAxiosRequestConfig, data: T): AxiosResponse<R<T>> {
  return {
    data: { code: 200, message: 'ok', data },
    status: 200,
    statusText: 'OK',
    headers: new AxiosHeaders({ 'content-type': 'application/json' }),
    config,
  }
}

function failure(
  config: InternalAxiosRequestConfig,
  code: number,
  message: string,
): AxiosResponse<R<null>> {
  return {
    data: { code, message, data: null },
    status: 200,
    statusText: 'OK',
    headers: new AxiosHeaders({ 'content-type': 'application/json' }),
    config,
  }
}

function pageOf<T>(items: T[], config: InternalAxiosRequestConfig): PageResponse<T> {
  const params = (config.params ?? {}) as { page?: number | string; pageSize?: number | string }
  const page = Math.max(1, Math.trunc(Number(params.page ?? 1)) || 1)
  const rawSize = Math.trunc(Number(params.pageSize ?? 10)) || 10
  const pageSize = Math.min(100, Math.max(1, rawSize))
  const total = items.length
  const start = (page - 1) * pageSize

  return {
    total,
    pages: Math.ceil(total / pageSize),
    pageSize,
    page,
    items: items.slice(start, start + pageSize),
  }
}

function escapeHtml(value: string): string {
  return value
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/\"/g, '&quot;')
    .replace(/'/g, '&#39;')
}

function escapeRegExp(value: string): string {
  return value.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')
}

function highlightText(value: string | null | undefined, keyword: string): string {
  const text = value ?? ''
  if (!keyword) return escapeHtml(text)
  const pattern = new RegExp(`(${escapeRegExp(keyword)})`, 'gi')
  return escapeHtml(text).replace(pattern, '<em>$1</em>')
}

function buildMockGraph(large = false): GraphVO {
  const edges: GraphEdgeVO[] = []
  const seen = new Set<string>()
  const degrees = new Map<string, number>()

  const addEdge = (source: string, target: string, relation: GraphRelation) => {
    const key = `${source}-${target}-${relation}`
    if (seen.has(key)) return
    seen.add(key)
    edges.push({ source, target, relation })
    degrees.set(source, (degrees.get(source) ?? 0) + 1)
    degrees.set(target, (degrees.get(target) ?? 0) + 1)
  }

  for (const [source, groups] of Object.entries(mockTagRelations)) {
    for (const [relation, group] of Object.entries(groups)) {
      for (const target of group.out) addEdge(source, target, relation as GraphRelation)
      for (const from of group.in) addEdge(from, source, relation as GraphRelation)
    }
  }

  const nodes: GraphNodeVO[] = mockTags.map((tag) => ({
    id: tag.tagId,
    type: 'TAG',
    label: tag.name,
    category: tag.category,
    degree: degrees.get(tag.tagId) ?? 0,
  }))

  if (large) {
    const categories = [...new Set(mockTags.map((tag) => tag.category))]
    for (let index = 0; index < 300; index += 1) {
      const node: GraphNodeVO = {
        id: `perf-${index + 1}`,
        type: 'TAG',
        label: `扩展技术 ${String(index + 1).padStart(3, '0')}`,
        category: categories[index % categories.length] ?? '开发实践',
        degree: (index % 5) + 1,
      }
      nodes.push(node)
      if (index % 3 === 0) {
        const target = mockTags[index % mockTags.length]
        if (target) addEdge(node.id, target.tagId, 'USES')
      }
    }
  }

  return { nodes, edges, articlesTruncated: false }
}

function resolveTagRelations(tagId: string): Record<string, RelationGroupVO> {
  const source = mockTagRelations[tagId] ?? {}
  const toBrief = (ids: string[]) =>
    ids.flatMap((id) => {
      const tag = mockTags.find((item) => item.tagId === id)
      return tag ? [{ tagId: tag.tagId, name: tag.name }] : []
    })

  return Object.fromEntries(
    Object.entries(source).map(([relation, group]) => [
      relation,
      { out: toBrief(group.out), in: toBrief(group.in) },
    ]),
  )
}

function pathOf(config: InternalAxiosRequestConfig): string {
  return new URL(config.url ?? '/', 'http://mock.local').pathname
}

function limitOf(config: InternalAxiosRequestConfig, fallback: number): number {
  const params = config.params as { limit?: number } | undefined
  const limit = Number(params?.limit ?? fallback)
  return Number.isFinite(limit) ? Math.max(1, Math.trunc(limit)) : fallback
}

function idsOf(value: unknown): string[] {
  if (Array.isArray(value)) return value.map(String)
  if (typeof value === 'string') return value.split(',').filter(Boolean)
  return []
}

function bodyOf<T>(config: InternalAxiosRequestConfig): T | null {
  if (typeof config.data === 'string') {
    try {
      return JSON.parse(config.data) as T
    } catch {
      return null
    }
  }
  return (config.data as T | undefined) ?? null
}

/** axios adapter：只在 VITE_USE_MOCK=true 时挂载，响应仍走 R<T> 解包拦截器 */
export async function handleMockRequest(
  config: InternalAxiosRequestConfig,
): Promise<AxiosResponse> {
  await delay(MOCK_DELAY)

  const method = (config.method ?? 'get').toLowerCase()
  const path = pathOf(config)

  if (method === 'post' && path === '/api/auth/login') {
    const body = bodyOf<{ password?: string }>(config)
    if (body?.password === 'wrong') {
      return failure(config, 400, '账号或密码错误')
    }
    return response(config, mockLogin())
  }

  if (method === 'post' && path === '/api/auth/register') {
    const body = bodyOf<{ email?: string; phone?: string; username?: string }>(config)
    if (body?.username === 'taken') {
      return failure(config, 409, '用户名已被占用')
    }
    if (body?.phone === '13800000000') {
      return failure(config, 409, '手机号已被占用')
    }
    if (body?.email === 'taken@wisdomhub.dev') {
      return failure(config, 409, '邮箱已被占用')
    }
    return response(config, mockLogin(Boolean(body?.email)))
  }

  if (method === 'post' && path === '/api/auth/refresh') {
    const body = bodyOf<{ refreshToken?: string }>(config)
    if (body?.refreshToken === 'invalid-refresh') {
      return failure(config, 400, 'refreshToken无效或已过期')
    }
    return response(config, mockLogin())
  }

  if (method === 'post' && path === '/api/auth/logout') {
    const key = 'wisdomhub:mock.logoutHits'
    localStorage.setItem(key, String(Number(localStorage.getItem(key) ?? 0) + 1))
    return response(config, null)
  }

  if (method === 'post' && path.startsWith('/api/auth/code/')) {
    return response(config, null)
  }

  if (method === 'get' && path === '/api/content/tag') {
    return response(config, mockTags)
  }

  if (method === 'get' && path === '/api/knowledge/graph') {
    if (localStorage.getItem('wisdomhub:mock.failGraph') === '1') {
      return failure(config, 500, '图谱加载失败')
    }
    if (localStorage.getItem('wisdomhub:mock.emptyGraph') === '1') {
      return response(config, { nodes: [], edges: [], articlesTruncated: false })
    }
    return response(
      config,
      buildMockGraph(localStorage.getItem('wisdomhub:mock.largeGraph') === '1'),
    )
  }

  const neighborhoodMatch = path.match(/^\/api\/knowledge\/graph\/tag\/([^/]+)$/)
  if (method === 'get' && neighborhoodMatch?.[1]) {
    const tagId = decodeURIComponent(neighborhoodMatch[1])
    const center = mockTags.find((tag) => tag.tagId === tagId)
    if (!center) return failure(config, 404, '标签不存在')

    const params = (config.params ?? {}) as {
      depth?: number | string
      articleLimit?: number | string
    }
    const depth = Math.min(2, Math.max(1, Number(params.depth ?? 1) || 1))
    const articleLimit = Math.min(50, Math.max(1, Number(params.articleLimit ?? 20) || 20))

    const neighborIds = new Set<string>([tagId])
    let frontier = [tagId]
    for (let level = 0; level < depth; level += 1) {
      const next: string[] = []
      for (const current of frontier) {
        const relations = mockTagRelations[current] ?? {}
        for (const group of Object.values(relations)) {
          for (const id of [...group.out, ...group.in]) {
            if (!neighborIds.has(id)) {
              neighborIds.add(id)
              next.push(id)
            }
          }
        }
      }
      frontier = next
    }

    const full = buildMockGraph()
    const tagNodes = full.nodes.filter((node) => neighborIds.has(node.id))
    const tagEdges = full.edges.filter(
      (edge) => neighborIds.has(edge.source) && neighborIds.has(edge.target),
    )

    const centerArticles = mockArticles.filter((article) => {
      const recommendation = mockRecommendations.find(
        (item) => item.articleId === article.articleId,
      )
      return recommendation?.tags.some((tag) => tag.tagId === tagId)
    })
    const selectedArticles = centerArticles.slice(0, articleLimit)
    const articleNodes: GraphNodeVO[] = selectedArticles.map((article) => ({
      id: `article-${article.articleId}`,
      type: 'ARTICLE',
      label: article.title,
      category: null,
      degree: null,
    }))
    const articleEdges: GraphEdgeVO[] = selectedArticles.map((article) => ({
      source: `article-${article.articleId}`,
      target: tagId,
      relation: 'ABOUT',
    }))

    return response(config, {
      nodes: [...tagNodes, ...articleNodes],
      edges: [...tagEdges, ...articleEdges],
      articlesTruncated: centerArticles.length > selectedArticles.length,
    })
  }

  const tagDetailMatch = path.match(/^\/api\/knowledge\/tag\/([^/]+)$/)
  if (method === 'get' && tagDetailMatch?.[1]) {
    const tagId = decodeURIComponent(tagDetailMatch[1])
    const tag = mockTags.find((item) => item.tagId === tagId)
    if (!tag) return failure(config, 404, '标签不存在')

    const detail: TagDetailVO = { ...tag, relations: resolveTagRelations(tagId) }
    return response(config, detail)
  }

  const tagRelatedMatch = path.match(/^\/api\/knowledge\/tag\/([^/]+)\/related$/)
  if (method === 'get' && tagRelatedMatch?.[1]) {
    const tagId = decodeURIComponent(tagRelatedMatch[1])
    const tag = mockTags.find((item) => item.tagId === tagId)
    if (!tag) return failure(config, 404, '标签不存在')

    const result: TagRelationsVO = {
      tagId,
      name: tag.name,
      relations: resolveTagRelations(tagId),
    }
    return response(config, result)
  }

  const prerequisiteMatch = path.match(/^\/api\/knowledge\/tag\/([^/]+)\/prerequisites$/)
  if (method === 'get' && prerequisiteMatch?.[1]) {
    const tagId = decodeURIComponent(prerequisiteMatch[1])
    const tag = mockTags.find((item) => item.tagId === tagId)
    if (!tag) return failure(config, 404, '标签不存在')

    const source = mockPrerequisites[tagId]
    const levels = (source?.levels ?? []).map((level) => ({
      depth: level.depth,
      tags: level.tags.flatMap((id) => {
        const item = mockTags.find((tagItem) => tagItem.tagId === id)
        return item ? [{ tagId: item.tagId, name: item.name }] : []
      }),
    }))
    const result: PrerequisiteTreeVO = {
      tagId,
      name: tag.name,
      depth: source?.depth ?? 0,
      levels,
      truncated: source?.truncated ?? false,
    }
    return response(config, result)
  }

  if (method === 'get' && path === '/api/search/article') {
    const params = (config.params ?? {}) as {
      keyword?: string
      tagId?: string | number
      sort?: string
      startTime?: string
      endTime?: string
    }
    const keyword = String(params.keyword ?? '').trim()
    const lowerKeyword = keyword.toLowerCase()
    const tagId = params.tagId === undefined ? '' : String(params.tagId)
    const startTime = String(params.startTime ?? '')
    const endTime = String(params.endTime ?? '')

    const items = mockArticles.filter((article) => {
      if (
        keyword &&
        !article.title.toLowerCase().includes(lowerKeyword) &&
        !article.summary.toLowerCase().includes(lowerKeyword)
      ) {
        return false
      }
      if (tagId) {
        const recommendation = mockRecommendations.find(
          (item) => item.articleId === article.articleId,
        )
        if (!recommendation?.tags.some((tag) => tag.tagId === tagId)) return false
      }
      if (startTime && article.publishTime < startTime) return false
      if (endTime && article.publishTime > endTime) return false
      return true
    })

    const sort = params.sort ?? 'relevance'
    if (sort === 'new') items.sort((a, b) => b.publishTime.localeCompare(a.publishTime))
    else if (sort === 'hot') items.sort((a, b) => b.viewCount - a.viewCount)

    const results: ArticleSearchVO[] = items.map((article) => {
      const recommendation = mockRecommendations.find(
        (item) => item.articleId === article.articleId,
      )
      return {
        ...article,
        highlight: {
          title: highlightText(article.title, keyword),
          summary: highlightText(article.summary, keyword),
        },
        tags: recommendation?.tags ?? [],
      }
    })
    return response(config, pageOf(results, config))
  }

  if (method === 'get' && path === '/api/search/question') {
    const params = (config.params ?? {}) as {
      keyword?: string
      sort?: string
      startTime?: string
      endTime?: string
    }
    const keyword = String(params.keyword ?? '').trim()
    const lowerKeyword = keyword.toLowerCase()
    const startTime = String(params.startTime ?? '')
    const endTime = String(params.endTime ?? '')

    const items = [...mockCreatedQuestions, ...mockQuestions].filter((question) => {
      const content =
        mockQuestionContent.get(question.questionId) ??
        mockQuestionContents[question.questionId] ??
        ''
      if (
        keyword &&
        !question.title.toLowerCase().includes(lowerKeyword) &&
        !content.toLowerCase().includes(lowerKeyword)
      ) {
        return false
      }
      if (startTime && question.createTime < startTime) return false
      if (endTime && question.createTime > endTime) return false
      return true
    })

    const sort = params.sort ?? 'relevance'
    if (sort === 'new') items.sort((a, b) => b.createTime.localeCompare(a.createTime))
    else if (sort === 'hot') items.sort((a, b) => b.answerCount - a.answerCount)

    const results: QuestionSearchVO[] = items.map((question) => {
      const content =
        mockQuestionContent.get(question.questionId) ??
        mockQuestionContents[question.questionId] ??
        ''
      return {
        questionId: question.questionId,
        userId: question.userId,
        title: question.title,
        content,
        highlight: {
          title: highlightText(question.title, keyword),
          content: highlightText(content, keyword),
        },
        answerCount: question.answerCount,
        favoriteCount: question.favoriteCount,
        acceptedAnswerId: mockQuestionAccepted[question.questionId] ?? null,
        createTime: question.createTime,
        updateTime: question.createTime,
      }
    })
    return response(config, pageOf(results, config))
  }

  if (method === 'get' && path === '/api/search/user') {
    const params = (config.params ?? {}) as { keyword?: string; sort?: string }
    const keyword = String(params.keyword ?? '').trim()
    const lowerKeyword = keyword.toLowerCase()

    const items = mockSocialUsers.filter(
      (user) =>
        !keyword ||
        user.username.toLowerCase().includes(lowerKeyword) ||
        (user.bio ?? '').toLowerCase().includes(lowerKeyword),
    )

    const results: UserSearchVO[] = items.map((user) => ({
      ...user,
      highlight: {
        username: highlightText(user.username, keyword),
        bio: highlightText(user.bio, keyword),
      },
      createTime: '2026-01-01T00:00:00Z',
      updateTime: '2026-01-01T00:00:00Z',
    }))
    return response(config, pageOf(results, config))
  }

  if (method === 'get' && path === '/api/search/tag') {
    const params = (config.params ?? {}) as { keyword?: string }
    const keyword = String(params.keyword ?? '').trim()
    const lowerKeyword = keyword.toLowerCase()
    const items: TagSearchVO[] = mockTags
      .filter(
        (tag) =>
          !keyword ||
          tag.name.toLowerCase().includes(lowerKeyword) ||
          tag.description.toLowerCase().includes(lowerKeyword),
      )
      .map((tag) => ({
        ...tag,
        highlight: {
          name: highlightText(tag.name, keyword),
          description: highlightText(tag.description, keyword),
        },
        createTime: '2026-01-01T00:00:00Z',
        updateTime: '2026-01-01T00:00:00Z',
      }))
    return response(config, pageOf(items, config))
  }

  if (method === 'get' && path === '/api/search/suggest') {
    const params = (config.params ?? {}) as {
      keyword?: string
      type?: string
      size?: number | string
    }
    const keyword = String(params.keyword ?? '')
      .trim()
      .toLowerCase()
    if (!keyword) return response(config, [])

    const size = Math.min(20, Math.max(1, Number(params.size ?? 5) || 5))
    const type = params.type ?? 'article'
    const source =
      type === 'question'
        ? mockQuestions.map((question) => question.title)
        : type === 'user'
          ? mockSocialUsers.map((user) => user.username)
          : type === 'tag'
            ? mockTags.map((tag) => tag.name)
            : mockArticles.map((article) => article.title)

    const items = [...new Set(source.filter((item) => item.toLowerCase().includes(keyword)))]
    return response(config, items.slice(0, size))
  }

  if (method === 'get' && path === '/api/search/hotword') {
    const params = (config.params ?? {}) as { size?: number | string }
    const size = Math.min(50, Math.max(1, Number(params.size ?? 10) || 10))
    const words = [
      '缓存',
      'Spring Boot',
      'Vue',
      'Redis',
      '分布式锁',
      'JVM',
      '消息队列',
      '知识图谱',
      'AI',
      '全文搜索',
    ]
    return response(
      config,
      words.slice(0, size).map((word, index) => ({
        keyword: word,
        count: 240 - index * 17,
      })),
    )
  }

  if (method === 'get' && path === '/api/content/tag/hot') {
    if (localStorage.getItem('wisdomhub:mock.failHotTags') === '1') {
      return failure(config, 500, '热门技术加载失败')
    }
    return response(config, mockHotTags.slice(0, limitOf(config, 10)))
  }

  if (method === 'post' && path === '/api/content/column') {
    const body = bodyOf<{
      title?: string
      description?: string
      cover?: string
      price?: number
    }>(config)
    const title = body?.title?.trim() ?? ''
    const price = Number(body?.price ?? 0)

    if (!title) return failure(config, 400, '专栏标题不能为空')
    if (!Number.isFinite(price) || price < 0) return failure(config, 400, '价格不合法')

    const columnId = String((mockColumnSequence += 1))
    mockColumns.unshift({
      columnId,
      userId: MOCK_USER_ID,
      title,
      description: body?.description?.trim() ?? '',
      cover: body?.cover?.trim() || null,
      price: Math.round(price),
      articleCount: 0,
      purchased: null,
      createTime: new Date().toISOString(),
      hasPurchase: false,
    })
    mockColumnArticleMap.set(columnId, [])

    return response(config, columnId)
  }

  if (method === 'get' && path === '/api/content/column/mine') {
    const items = mockColumns
      .filter((column) => column.userId === MOCK_USER_ID)
      .map((column) => columnVO(column, null))
    return response(config, pageOf(items, config))
  }

  if (method === 'get' && path === '/api/content/column/purchased') {
    if (localStorage.getItem('wisdomhub:mock.failPurchased') === '1') {
      return failure(config, 500, '已购专栏加载失败')
    }
    if (localStorage.getItem('wisdomhub:mock.emptyPurchased') === '1') {
      return response(config, pageOf([], config))
    }
    return response(config, pageOf(mockPurchasedColumns, config))
  }

  const columnArticleMatch = path.match(/^\/api\/content\/column\/([^/]+)\/article\/([^/]+)$/)
  if (columnArticleMatch?.[1] && columnArticleMatch[2]) {
    const columnId = decodeURIComponent(columnArticleMatch[1])
    const articleId = decodeURIComponent(columnArticleMatch[2])
    const column = mockColumns.find((item) => item.columnId === columnId)
    if (!column) return failure(config, 404, '专栏不存在或已删除')
    if (column.userId !== MOCK_USER_ID) return failure(config, 403, '无权操作该专栏')

    const articleIds = mockColumnArticleMap.get(columnId) ?? []

    if (method === 'put') {
      const article = mockArticles.find((item) => item.articleId === articleId)
      if (!article) return failure(config, 404, '文章不存在或已删除')
      if (article.authorId !== MOCK_USER_ID) {
        return failure(config, 400, '只能归入自己的已发布文章')
      }
      if (!articleIds.includes(articleId)) articleIds.push(articleId)
      mockColumnArticleMap.set(columnId, articleIds)
      column.articleCount = articleIds.length
      return response(config, column.articleCount)
    }

    if (method === 'delete') {
      if (column.hasPurchase) {
        return failure(config, 409, '该专栏已有购买记录，无法移出文章')
      }
      const index = articleIds.indexOf(articleId)
      if (index >= 0) articleIds.splice(index, 1)
      mockColumnArticleMap.set(columnId, articleIds)
      column.articleCount = articleIds.length
      return response(config, column.articleCount)
    }
  }

  const columnArticlesMatch = path.match(/^\/api\/content\/column\/([^/]+)\/article$/)
  if (method === 'get' && columnArticlesMatch?.[1]) {
    const columnId = decodeURIComponent(columnArticlesMatch[1])
    const column = mockColumns.find((item) => item.columnId === columnId)
    if (!column) return failure(config, 404, '专栏不存在或已删除')
    return response(config, pageOf(articlesOfColumn(columnId), config))
  }

  const columnMatch = path.match(/^\/api\/content\/column\/([^/]+)$/)
  if (columnMatch?.[1]) {
    const columnId = decodeURIComponent(columnMatch[1])
    const column = mockColumns.find((item) => item.columnId === columnId)
    if (!column) return failure(config, 404, '专栏不存在或已删除')

    if (method === 'get') {
      const purchased = column.userId === MOCK_USER_ID ? false : (column.purchased ?? false)
      return response(config, columnVO(column, purchased))
    }

    if (method === 'delete') {
      if (column.userId !== MOCK_USER_ID) return failure(config, 403, '无权删除该专栏')
      if (column.hasPurchase) return failure(config, 409, '该专栏已有购买记录，无法删除')

      const index = mockColumns.findIndex((item) => item.columnId === columnId)
      if (index >= 0) mockColumns.splice(index, 1)
      mockColumnArticleMap.delete(columnId)
      return response(config, null)
    }
  }

  if (method === 'get' && path === '/api/content/article') {
    const params = (config.params ?? {}) as {
      authorId?: string | number
      tagId?: string | number
      sort?: string
    }
    const tagId = params.tagId === undefined ? '' : String(params.tagId)
    const taggedIds = tagId
      ? new Set(
          mockRecommendations
            .filter((item) => item.tags.some((tag) => tag.tagId === tagId))
            .map((item) => item.articleId),
        )
      : null

    const items = mockArticles.filter((article) => {
      if (params.authorId !== undefined && article.authorId !== String(params.authorId)) {
        return false
      }
      if (taggedIds && !taggedIds.has(article.articleId)) return false
      return true
    })

    items.sort(
      params.sort === 'hot'
        ? (left, right) => right.viewCount - left.viewCount
        : (left, right) => Date.parse(right.publishTime) - Date.parse(left.publishTime),
    )

    return response(config, pageOf(items, config))
  }

  if (method === 'get' && path === '/api/content/article/brief') {
    const params = (config.params ?? {}) as { articleIds?: string[] | string }
    return response(config, mockArticleBriefs(idsOf(params.articleIds)))
  }

  if (method === 'post' && path === '/api/content/draft') {
    if (localStorage.getItem('wisdomhub:mock.failDraft') === '1') {
      return failure(config, 500, '草稿保存失败，请稍后重试')
    }
    const body = bodyOf<{ title?: string; markdown?: string }>(config)
    const title = body?.title?.trim() ?? ''
    const markdown = body?.markdown ?? ''
    if (!title && !markdown.trim()) {
      return failure(config, 400, '标题和正文不能同时为空')
    }

    const articleId = String((mockDraftSequence += 1))
    localStorage.setItem('wisdomhub:mock.draftSeq', articleId)
    mockDrafts.set(articleId, {
      articleId,
      title,
      summary: '',
      markdown,
      state: 1,
      tagIds: [],
      updateTime: new Date().toISOString(),
    })
    persistMockDrafts()
    return response(config, articleId)
  }

  const draftMatch = path.match(/^\/api\/content\/draft\/([^/]+)$/)
  if (draftMatch?.[1] && method === 'put') {
    if (localStorage.getItem('wisdomhub:mock.failDraft') === '1') {
      return failure(config, 500, '草稿保存失败，请稍后重试')
    }
    const articleId = decodeURIComponent(draftMatch[1])
    const draft = mockDrafts.get(articleId)
    if (!draft) return failure(config, 404, '草稿不存在或已删除')
    if (draft.state !== 1) return failure(config, 409, '已发布文章不能作为草稿保存')

    const body = bodyOf<{ title?: string; markdown?: string }>(config)
    draft.title = body?.title?.trim() ?? ''
    draft.markdown = body?.markdown ?? ''
    draft.updateTime = new Date().toISOString()
    persistMockDrafts()
    return response(config, articleId)
  }

  if (draftMatch?.[1] && method === 'delete') {
    const articleId = decodeURIComponent(draftMatch[1])
    const draft = mockDrafts.get(articleId)
    if (!draft) return failure(config, 404, '草稿不存在或已删除')
    if (draft.state !== 1) return failure(config, 409, '已发布文章不能作为草稿删除')
    mockDrafts.delete(articleId)
    persistMockDrafts()
    return response(config, null)
  }

  if (method === 'get' && path === '/api/content/draft') {
    const items = [...mockDrafts.values()]
      .sort((a, b) => b.updateTime.localeCompare(a.updateTime))
      .map(({ articleId, title, updateTime }) => ({ articleId, title, updateTime }))
    return response(config, pageOf(items, config))
  }

  if (method === 'get' && path === '/api/content/article/collect') {
    const items = [...mockFavoriteArticleIds]
      .map((articleId) => mockArticles.find((article) => article.articleId === articleId))
      .filter((article): article is ArticleListItemVO => Boolean(article))
    return response(config, pageOf(items, config))
  }

  if (method === 'post' && path === '/api/content/question') {
    if (localStorage.getItem('wisdomhub:mock.failQuestion') === '1') {
      return failure(config, 500, '提问失败，请稍后重试')
    }

    const body = bodyOf<{ title?: string; content?: string }>(config)
    const title = body?.title?.trim() ?? ''
    const content = body?.content ?? ''

    if (!title) return failure(config, 400, '标题不能为空')
    if (title.length > 100) return failure(config, 400, '标题不能超过100字')
    if (!content.trim()) return failure(config, 400, '问题描述不能为空')
    if (content.length > 10000) {
      return failure(config, 400, '问题描述不能超过10000字')
    }

    const questionId = String((mockQuestionSequence += 1))
    const question: QuestionListItemVO = {
      questionId,
      userId: '1001',
      title,
      answerCount: 0,
      favoriteCount: 0,
      solved: false,
      createTime: new Date().toISOString(),
    }
    mockCreatedQuestions.unshift(question)
    persistMockCreatedQuestions()
    localStorage.setItem('wisdomhub:mock.questionSeq', questionId)
    mockQuestionContent.set(questionId, content)
    return response(config, questionId)
  }

  if (method === 'get' && path === '/api/content/question/favorite') {
    const items = [...mockCreatedQuestions, ...mockQuestions]
      .filter((question) => mockQuestionFavorites.has(question.questionId))
      .sort((a, b) => b.createTime.localeCompare(a.createTime))
    return response(config, pageOf(items, config))
  }

  if (method === 'get' && path === '/api/content/question') {
    const params = (config.params ?? {}) as { sort?: string }
    const items = [...mockCreatedQuestions, ...mockQuestions].sort((a, b) =>
      params.sort === 'hot'
        ? b.answerCount - a.answerCount
        : b.createTime.localeCompare(a.createTime),
    )
    return response(config, pageOf(items, config))
  }

  const questionDetailMatch = path.match(/^\/api\/content\/question\/([^/]+)$/)
  if (method === 'get' && questionDetailMatch?.[1]) {
    const questionId = decodeURIComponent(questionDetailMatch[1])
    const question = [...mockCreatedQuestions, ...mockQuestions].find(
      (item) => item.questionId === questionId,
    )
    if (!question) return failure(config, 404, '问题不存在或已删除')

    const acceptedAnswerId = mockQuestionAccepted[questionId] ?? null
    const acceptedAnswer = acceptedAnswerId
      ? (mockAnswers.find((answer) => answer.answerId === acceptedAnswerId) ?? null)
      : null

    return response(config, {
      questionId: question.questionId,
      userId: question.userId,
      title: question.title,
      content: mockQuestionContent.get(questionId) ?? mockQuestionContents[questionId] ?? '',
      answerCount: question.answerCount,
      favoriteCount: question.favoriteCount,
      acceptedAnswerId,
      acceptedAnswer,
      favorited: mockQuestionFavorites.has(questionId),
      createTime: question.createTime,
    })
  }

  const answerMatch = path.match(/^\/api\/content\/question\/([^/]+)\/answer$/)
  if (method === 'get' && answerMatch?.[1]) {
    const questionId = decodeURIComponent(answerMatch[1])
    const question = [...mockCreatedQuestions, ...mockQuestions].find(
      (item) => item.questionId === questionId,
    )
    if (!question) return failure(config, 404, '问题不存在或已删除')

    const acceptedAnswerId = mockQuestionAccepted[questionId] ?? null
    const items = mockAnswers
      .filter((answer) => answer.questionId === questionId)
      .sort((a, b) => {
        if (a.answerId === acceptedAnswerId) return -1
        if (b.answerId === acceptedAnswerId) return 1
        return a.createTime.localeCompare(b.createTime)
      })
    return response(config, pageOf(items, config))
  }

  if (method === 'post' && answerMatch?.[1]) {
    const questionId = decodeURIComponent(answerMatch[1])
    const question = [...mockCreatedQuestions, ...mockQuestions].find(
      (item) => item.questionId === questionId,
    )
    if (!question) return failure(config, 404, '问题不存在或已删除')

    const body = bodyOf<{ content?: string }>(config)
    const content = body?.content?.trim() ?? ''
    if (!content) return failure(config, 400, '回答内容不能为空')
    if (content.length > 10000) return failure(config, 400, '回答长度不能超过10000')

    const answerId = String((mockAnswerSequence += 1))
    mockAnswers.unshift({
      answerId,
      questionId,
      userId: '1001',
      content,
      likeCount: 0,
      commentCount: 0,
      createTime: new Date().toISOString(),
    })
    question.answerCount += 1
    return response(config, answerId)
  }

  const answerLikeMatch = path.match(/^\/api\/content\/answer\/([^/]+)\/like$/)
  if (answerLikeMatch?.[1] && (method === 'put' || method === 'delete')) {
    const answerId = decodeURIComponent(answerLikeMatch[1])
    const answer = mockAnswers.find((item) => item.answerId === answerId)
    if (!answer) return failure(config, 404, '回答不存在或已删除')

    const active = method === 'put'
    const liked = mockAnswerLikes.has(answerId)
    if (active && !liked) {
      mockAnswerLikes.add(answerId)
      answer.likeCount += 1
    } else if (!active && liked) {
      mockAnswerLikes.delete(answerId)
      answer.likeCount = Math.max(0, answer.likeCount - 1)
    }
    return response(config, answer.likeCount)
  }

  const answerDeleteMatch = path.match(/^\/api\/content\/answer\/([^/]+)$/)
  if (method === 'delete' && answerDeleteMatch?.[1]) {
    const answerId = decodeURIComponent(answerDeleteMatch[1])
    const index = mockAnswers.findIndex((item) => item.answerId === answerId)
    if (index < 0) return failure(config, 404, '回答不存在或已删除')
    if (mockAnswers[index]?.userId !== '1001') {
      return failure(config, 403, '无权删除该回答')
    }

    const [removed] = mockAnswers.splice(index, 1)
    const question = [...mockCreatedQuestions, ...mockQuestions].find(
      (item) => item.questionId === removed?.questionId,
    )
    if (question) question.answerCount = Math.max(0, question.answerCount - 1)
    if (removed && mockQuestionAccepted[removed.questionId] === answerId) {
      mockQuestionAccepted[removed.questionId] = null
    }
    return response(config, null)
  }

  const acceptMatch = path.match(/^\/api\/content\/question\/([^/]+)\/accept\/([^/]+)$/)
  if (acceptMatch?.[1] && acceptMatch[2] && (method === 'put' || method === 'delete')) {
    const questionId = decodeURIComponent(acceptMatch[1])
    const answerId = decodeURIComponent(acceptMatch[2])
    const question = [...mockCreatedQuestions, ...mockQuestions].find(
      (item) => item.questionId === questionId,
    )
    if (!question) return failure(config, 404, '问题不存在或已删除')
    if (question.userId !== '1001') {
      return failure(config, 403, '只有提问者可以采纳答案')
    }
    if (!mockAnswers.some((answer) => answer.answerId === answerId)) {
      return failure(config, 404, '回答不存在或已删除')
    }

    mockQuestionAccepted[questionId] = method === 'put' ? answerId : null
    return response(config, null)
  }

  const favoriteMatch = path.match(/^\/api\/content\/question\/([^/]+)\/favorite$/)
  if (favoriteMatch?.[1] && (method === 'put' || method === 'delete')) {
    const questionId = decodeURIComponent(favoriteMatch[1])
    const question = [...mockCreatedQuestions, ...mockQuestions].find(
      (item) => item.questionId === questionId,
    )
    if (!question) return failure(config, 404, '问题不存在或已删除')

    if (method === 'put') mockQuestionFavorites.add(questionId)
    else mockQuestionFavorites.delete(questionId)
    persistMockQuestionFavorites()
    return response(
      config,
      question.favoriteCount + (mockQuestionFavorites.has(questionId) ? 1 : 0),
    )
  }

  if (method === 'get' && path === '/api/content/comment') {
    const params = (config.params ?? {}) as {
      targetId?: string | number
      targetType?: string | number
    }
    const targetType = String(params.targetType ?? '1')
    const targetId = String(params.targetId ?? '')
    const roots =
      targetType === '2'
        ? (mockAnswerComments[targetId] ?? [])
        : targetType === '1' && targetId === '1001'
          ? mockComments.filter((comment) => !comment.parentId)
          : []
    return response(config, pageOf(roots, config))
  }

  if (method === 'post' && path === '/api/content/comment') {
    const body = bodyOf<{
      targetType?: number
      targetId?: string | number
      parentId?: string | number | null
      content?: string
    }>(config)
    const content = body?.content?.trim() ?? ''
    if (!content) return failure(config, 400, '评论内容不能为空')
    if (content.length > 1000) {
      return failure(config, 400, '评论内容长度不能超过1000')
    }

    const targetType = Number(body?.targetType ?? 1)
    const targetId = String(body?.targetId ?? '')
    if (targetType === 2 && !mockAnswerComments[targetId]) {
      mockAnswerComments[targetId] = []
    }
    const roots = targetType === 2 ? mockAnswerComments[targetId]! : mockComments

    let parentId =
      body?.parentId === undefined || body?.parentId === null ? null : String(body.parentId)
    if (parentId) {
      const all = roots.flatMap((comment) => [comment, ...comment.replies])
      const parent = all.find((comment) => comment.commentId === parentId)
      if (parent?.parentId) parentId = parent.parentId
    }

    const comment = {
      commentId: String((mockCommentSequence += 1)),
      userId: '1001',
      parentId,
      content,
      createTime: new Date().toISOString(),
      replies: [],
    }

    if (parentId) {
      const root = roots.find((item) => item.commentId === parentId)
      root?.replies.push(comment)
    } else {
      roots.push(comment)
    }

    if (targetType === 2) {
      const answer = mockAnswers.find((item) => item.answerId === targetId)
      if (answer) answer.commentCount += 1
    }
    return response(config, comment.commentId)
  }

  const commentDeleteMatch = path.match(/^\/api\/content\/comment\/([^/]+)$/)
  if (commentDeleteMatch?.[1] && method === 'delete') {
    const commentId = decodeURIComponent(commentDeleteMatch[1])
    const rootIndex = mockComments.findIndex((comment) => comment.commentId === commentId)
    if (rootIndex >= 0) {
      mockComments.splice(rootIndex, 1)
      return response(config, null)
    }
    for (const root of mockComments) {
      const replyIndex = root.replies.findIndex((reply) => reply.commentId === commentId)
      if (replyIndex >= 0) {
        root.replies.splice(replyIndex, 1)
        return response(config, null)
      }
    }

    for (const [answerId, roots] of Object.entries(mockAnswerComments)) {
      const answerRootIndex = roots.findIndex((comment) => comment.commentId === commentId)
      const answer = mockAnswers.find((item) => item.answerId === answerId)
      if (answerRootIndex >= 0) {
        roots.splice(answerRootIndex, 1)
        if (answer) answer.commentCount = Math.max(0, answer.commentCount - 1)
        return response(config, null)
      }
      for (const root of roots) {
        const replyIndex = root.replies.findIndex((reply) => reply.commentId === commentId)
        if (replyIndex >= 0) {
          root.replies.splice(replyIndex, 1)
          if (answer) answer.commentCount = Math.max(0, answer.commentCount - 1)
          return response(config, null)
        }
      }
    }
    return failure(config, 404, '评论不存在')
  }

  const likeMatch = path.match(/^\/api\/content\/article\/([^/]+)\/like$/)
  if (likeMatch?.[1] && (method === 'put' || method === 'delete')) {
    const articleId = decodeURIComponent(likeMatch[1])
    if (localStorage.getItem('wisdomhub:mock.failLike') === '1') {
      return failure(config, 500, '点赞失败，请稍后重试')
    }
    return response(config, mockToggleArticleReaction(articleId, 'like', method === 'put'))
  }

  const collectMatch = path.match(/^\/api\/content\/article\/([^/]+)\/collect$/)
  if (collectMatch?.[1] && (method === 'put' || method === 'delete')) {
    const articleId = decodeURIComponent(collectMatch[1])
    if (localStorage.getItem('wisdomhub:mock.failCollect') === '1') {
      return failure(config, 500, '收藏失败，请稍后重试')
    }
    const active = method === 'put'
    const count = mockToggleArticleReaction(articleId, 'collect', active)
    if (active) mockFavoriteArticleIds.add(articleId)
    else mockFavoriteArticleIds.delete(articleId)
    return response(config, count)
  }

  const sourceMatch = path.match(/^\/api\/content\/article\/([^/]+)\/source$/)
  if (method === 'get' && sourceMatch?.[1]) {
    const articleId = decodeURIComponent(sourceMatch[1])
    const draft = mockDrafts.get(articleId)
    if (draft) {
      return response(config, {
        articleId: draft.articleId,
        title: draft.title,
        summary: draft.summary,
        markdown: draft.markdown,
        state: draft.state,
        updateTime: draft.updateTime,
        tagIds: draft.tagIds,
      })
    }

    const published = mockPublishedSources.get(articleId)
    if (published) {
      return response(config, {
        articleId: published.articleId,
        title: published.title,
        summary: published.summary,
        markdown: published.markdown,
        state: published.state,
        updateTime: published.updateTime,
        tagIds: published.tagIds,
      })
    }

    return failure(config, 404, '文章不存在或已删除')
  }

  const detailMatch = path.match(/^\/api\/content\/article\/([^/]+)$/)
  if (method === 'get' && detailMatch?.[1]) {
    const articleId = decodeURIComponent(detailMatch[1])
    if (articleId === 'missing') {
      return failure(config, 404, '文章不存在或已删除')
    }
    const locked = localStorage.getItem('wisdomhub:mock.paywall') === '1'
    return response(config, mockArticleDetail(articleId, locked))
  }

  if (method === 'delete' && detailMatch?.[1]) {
    const articleId = decodeURIComponent(detailMatch[1])
    const index = mockArticles.findIndex((article) => article.articleId === articleId)
    if (index < 0) return failure(config, 404, '文章不存在或已删除')
    if (mockArticles[index].authorId !== '1001') {
      return failure(config, 403, '无权删除该文章')
    }

    mockArticles.splice(index, 1)
    mockFavoriteArticleIds.delete(articleId)
    const historyIndex = mockHistoryEntries.findIndex((entry) => entry.articleId === articleId)
    if (historyIndex >= 0) mockHistoryEntries.splice(historyIndex, 1)
    mockPublishedSources.delete(articleId)
    persistMockPublishedSources()
    return response(config, null)
  }

  if (method === 'get' && path === '/api/recommendation/home') {
    return response(config, mockRecommendations.slice(0, limitOf(config, 4)))
  }

  if (method === 'get' && path === '/api/recommendation/hot') {
    return response(config, mockRecommendations.slice(0, limitOf(config, 4)))
  }

  if (method === 'post' && path === '/api/content/article') {
    if (localStorage.getItem('wisdomhub:mock.failPublish') === '1') {
      return failure(config, 500, '发布失败，请稍后重试')
    }

    const body = bodyOf<{
      articleId?: string | number
      title?: string
      summary?: string
      markdown?: string
      tagIds?: Array<string | number> | null
    }>(config)
    const title = body?.title?.trim() ?? ''
    const markdown = body?.markdown ?? ''

    if (!title) return failure(config, 400, '标题不能为空')
    if (!markdown.trim()) return failure(config, 400, '正文不能为空')
    if (Array.isArray(body?.tagIds) && body.tagIds.length > 10) {
      return failure(config, 400, '一篇文章最多选择10个标签')
    }

    const rawId =
      body?.articleId !== undefined && body?.articleId !== null ? String(body.articleId) : ''
    const draft = rawId ? mockDrafts.get(rawId) : undefined
    const published = rawId ? mockPublishedSources.get(rawId) : undefined
    if (rawId && !draft && !published) {
      return failure(config, 404, '文章不存在或已删除')
    }

    const articleId = rawId || String((mockArticleSequence += 1))
    const existingTags = draft?.tagIds ?? published?.tagIds ?? []
    const tagIds = Array.isArray(body?.tagIds) ? body.tagIds.map(String) : existingTags
    const summary =
      body?.summary?.trim() ||
      draft?.summary ||
      published?.summary ||
      markdown
        .replace(/[#>*_`~\-[\]()!]/g, ' ')
        .trim()
        .slice(0, 80)

    const source: MockDraft = {
      articleId,
      title,
      summary,
      markdown,
      state: 2,
      tagIds,
      updateTime: new Date().toISOString(),
    }
    mockPublishedSources.set(articleId, source)
    localStorage.setItem('wisdomhub:mock.articleSeq', articleId)
    persistMockPublishedSources()

    const base = mockArticles.find((article) => article.articleId === articleId)
    if (base) {
      base.title = title
      base.summary = summary
      base.publishTime = source.updateTime
    } else {
      const created: ArticleListItemVO = {
        articleId,
        authorId: '1001',
        title,
        summary,
        publishTime: source.updateTime,
        viewCount: 0,
        likeCount: 0,
        commentCount: 0,
        favoriteCount: 0,
      }
      mockArticles.unshift(created)
    }

    if (draft) {
      mockDrafts.delete(articleId)
      persistMockDrafts()
    }

    return response(config, articleId)
  }

  const relatedMatch = path.match(/^\/api\/recommendation\/related\/([^/]+)$/)
  if (method === 'get' && relatedMatch?.[1]) {
    return response(config, mockRecommendations.slice(1, 4))
  }

  const analysisMatch = path.match(/^\/api\/agent\/analysis\/([^/]+)$/)
  if (method === 'get' && analysisMatch?.[1]) {
    const articleId = decodeURIComponent(analysisMatch[1])
    const mode = localStorage.getItem('wisdomhub:mock.analysisMode') ?? 'eventual'

    if (mode === 'error') {
      return response(config, {
        articleId,
        status: 'error',
        message: '文章分析失败，请稍后重试',
      })
    }
    if (mode === 'pending') {
      return response(config, { articleId, status: 'pending' })
    }

    const key = `wisdomhub:mock.analysisPolls.${articleId}`
    const polls = Number(localStorage.getItem(key) ?? 0) + 1
    localStorage.setItem(key, String(polls))
    if (polls <= 1) {
      return response(config, { articleId, status: 'pending' })
    }

    return response(config, {
      articleId,
      status: 'done',
      topic: '缓存一致性边界',
      entities: ['Redis', 'Spring Boot', '缓存'],
      knowledgePoints: ['缓存一致性边界', '回源与降级策略', '缓存预热与失效'],
    })
  }

  if (method === 'get' && path === '/api/user/profile/brief') {
    const hitsKey = 'wisdomhub:mock.briefHits'
    localStorage.setItem(hitsKey, String(Number(localStorage.getItem(hitsKey) ?? 0) + 1))

    const params = (config.params ?? {}) as { userIds?: string[] | string }
    const ids = idsOf(params.userIds)
    return response(
      config,
      ids.length ? mockAuthors.filter((author) => ids.includes(author.userId)) : mockAuthors,
    )
  }

  const profileMatch = path.match(/^\/api\/user\/profile\/([^/]+)$/)
  if (method === 'get' && profileMatch?.[1]) {
    const profileId = decodeURIComponent(profileMatch[1])
    const social = mockSocialUsers.find((user) => user.userId === profileId)

    if (social) {
      return response(config, {
        ...mockProfile,
        userId: profileId,
        username: social.username,
        avatar: social.avatar,
        bio: social.bio,
        articleCount: 0,
      })
    }

    return response(config, {
      ...mockProfile,
      userId: profileId,
      articleCount: mockArticles.filter((article) => article.authorId === profileId).length,
    })
  }

  if (method === 'put' && path === '/api/user/profile') {
    const body = bodyOf<{ username?: string; bio?: string }>(config)
    if (body?.username === 'taken') {
      return failure(config, 409, '用户名已被占用')
    }
    if (body?.username) mockProfile.username = body.username
    if (typeof body?.bio === 'string') mockProfile.bio = body.bio
    return response(config, null)
  }

  if (method === 'put' && path === '/api/user/email') {
    const body = bodyOf<{ email?: string }>(config)
    if (body?.email === 'taken@wisdomhub.dev') {
      return failure(config, 409, '邮箱已被占用')
    }
    return response(config, null)
  }

  if (method === 'post' && path === '/api/user/avatar') {
    mockProfile.avatar = MOCK_AVATAR_URL
    return response(config, MOCK_AVATAR_URL)
  }

  const historyMatch = path.match(/^\/api\/user\/history\/([^/]+)$/)
  if (historyMatch?.[1]) {
    const articleId = decodeURIComponent(historyMatch[1])

    if (method === 'put') {
      const key = `wisdomhub:mock.history.${articleId}`
      localStorage.setItem(key, String(Number(localStorage.getItem(key) ?? 0) + 1))
      const updateTime = new Date().toISOString()
      const existing = mockHistoryEntries.find((entry) => entry.articleId === articleId)
      if (existing) existing.updateTime = updateTime
      else mockHistoryEntries.unshift({ articleId, updateTime })
      return response(config, null)
    }

    if (method === 'delete') {
      const index = mockHistoryEntries.findIndex((entry) => entry.articleId === articleId)
      if (index >= 0) mockHistoryEntries.splice(index, 1)
      return response(config, null)
    }
  }

  if (method === 'get' && path === '/api/user/history') {
    const items = mockHistoryEntries
      .map(({ articleId, updateTime }) => {
        const article = mockArticles.find((item) => item.articleId === articleId)
        if (!article) return null
        return {
          articleId,
          title: article.title,
          summary: article.summary,
          updateTime,
        }
      })
      .filter((item): item is BrowseHistoryVO => Boolean(item))
    return response(config, pageOf(items, config))
  }

  if (method === 'delete' && path === '/api/user/history') {
    mockHistoryEntries.length = 0
    return response(config, null)
  }

  if (method === 'get' && (path === '/api/user/follow/list' || path === '/api/user/fans/list')) {
    return response(config, pageOf(mockSocialUsers, config))
  }

  const followMatch = path.match(/^\/api\/user\/follow\/([^/]+)$/)
  if (followMatch?.[1]) {
    const id = decodeURIComponent(followMatch[1])
    if (method === 'get') return response(config, mockFollowing.has(id))
    if (method === 'put') {
      mockFollowing.add(id)
      return response(config, null)
    }
    if (method === 'delete') {
      mockFollowing.delete(id)
      return response(config, null)
    }
  }

  if (method === 'post' && path === '/api/order') {
    const forcedError = localStorage.getItem('wisdomhub:mock.orderError')
    if (forcedError === '400') return failure(config, 400, '不能购买自己的专栏')
    if (forcedError === '409') return failure(config, 409, '该专栏已购买')
    if (forcedError === '500') return failure(config, 500, '订单服务暂不可用')

    const body = bodyOf<{ itemType?: number; itemId?: string | number }>(config)
    if (body?.itemType !== 1 || body.itemId === undefined || body.itemId === null) {
      return failure(config, 400, '只有专栏支持购买')
    }

    const itemId = String(body.itemId)
    const demo = mockPaidColumns.find((column) => column.columnId === itemId)
    const column = mockColumns.find((item) => item.columnId === itemId)
    if (!demo && !column) return failure(config, 404, '专栏不存在或已删除')

    let title = ''
    let price = 0
    let authorId = ''
    let purchased = false
    let purchasable = true
    let own = false

    if (demo) {
      title = demo.title
      price = demo.price
      authorId = '2001'
      purchased = demo.purchased
      purchasable = demo.purchasable
    } else if (column) {
      title = column.title
      price = column.price
      authorId = column.userId
      purchased = column.userId !== MOCK_USER_ID && column.purchased === true
      own = column.userId === MOCK_USER_ID
    }

    if (purchased) return failure(config, 409, '该专栏已购买')
    if (!purchasable) return failure(config, 400, '该专栏暂不可购买')
    if (own) return failure(config, 400, '不能购买自己的专栏')
    if (price === 0) return failure(config, 400, '免费专栏无需购买')

    const existing = [...mockOrders.values()].find(
      (order) =>
        order.state === 0 &&
        Date.parse(order.expireTime) > Date.now() &&
        order.items.some((item) => item.itemType === 1 && item.itemId === itemId),
    )
    if (existing) return response(config, existing)

    const orderNo = String((mockOrderSequence += 1))
    localStorage.setItem(MOCK_ORDER_SEQ_KEY, orderNo)
    const createdAt = new Date()
    const order: OrderVO = {
      orderNo,
      amount: price,
      state: 0,
      expireTime: new Date(createdAt.getTime() + 15 * 60_000).toISOString(),
      paidTime: null,
      refundTime: null,
      createTime: createdAt.toISOString(),
      items: [{ itemType: 1, itemId, authorId, title, price }],
      payment: null,
    }
    mockOrders.set(orderNo, order)
    persistMockOrders()

    return response(config, order)
  }

  const orderPayMatch = path.match(/^\/api\/order\/([^/]+)\/pay$/)
  if (method === 'post' && orderPayMatch?.[1]) {
    const orderNo = decodeURIComponent(orderPayMatch[1])
    const order = mockOrders.get(orderNo)
    if (!order) return failure(config, 404, '订单不存在')
    if (order.state === 1) return response(config, order)
    if (order.state === 2) return failure(config, 409, '订单已取消')
    if (order.state !== 0) return failure(config, 409, '订单当前状态不支持支付')

    const forcedError = localStorage.getItem('wisdomhub:mock.payError')
    if (forcedError === '500') return failure(config, 500, '支付失败，请稍后重试')

    if (forcedError === 'expired' || Date.parse(order.expireTime) <= Date.now()) {
      order.state = 2
      persistMockOrders()
      return failure(config, 409, '订单已过期')
    }

    const paidAt = new Date().toISOString()
    order.state = 1
    order.paidTime = paidAt
    order.payment = {
      paymentNo: `P${order.orderNo}`,
      payType: 1,
      state: 1,
      paidTime: paidAt,
      refundNo: null,
      refundAmount: null,
      refundState: 0,
      refundTime: null,
    }
    const itemId = order.items[0]?.itemId
    if (itemId) markMockColumnOwned(itemId)
    persistMockOrders()

    return response(config, order)
  }

  const orderCancelMatch = path.match(/^\/api\/order\/([^/]+)\/cancel$/)
  if (method === 'post' && orderCancelMatch?.[1]) {
    const orderNo = decodeURIComponent(orderCancelMatch[1])
    const order = mockOrders.get(orderNo)
    if (!order) return failure(config, 404, '订单不存在')
    if (order.state === 2) return response(config, null)
    if (order.state !== 0) return failure(config, 409, '只有待支付订单可以取消')

    order.state = 2
    persistMockOrders()
    return response(config, null)
  }

  const orderRefundMatch = path.match(/^\/api\/order\/([^/]+)\/refund$/)
  if (method === 'post' && orderRefundMatch?.[1]) {
    const orderNo = decodeURIComponent(orderRefundMatch[1])
    const order = mockOrders.get(orderNo)
    if (!order) return failure(config, 404, '订单不存在')

    const now = new Date().toISOString()
    const itemId = order.items[0]?.itemId

    if (order.state === 4) return response(config, order)

    if (order.state === 3) {
      order.state = 4
      order.refundTime = now
      if (order.payment) {
        order.payment.refundState = 2
        order.payment.refundTime = now
      }
      if (itemId) unmarkMockColumnOwned(itemId)
      persistMockOrders()
      return response(config, order)
    }

    if (order.state !== 1) return failure(config, 409, '只有已支付订单可以退款')
    if (!order.payment) return failure(config, 500, '支付流水缺失')

    const body = bodyOf<{ simulate?: string }>(config)
    const simulate = body?.simulate ?? 'success'
    if (!['success', 'failure', 'processing'].includes(simulate)) {
      return failure(config, 400, 'simulate 只支持 success/failure/processing')
    }

    order.payment.refundNo = order.payment.refundNo ?? `R${order.orderNo}`
    order.payment.refundAmount = order.amount

    if (simulate === 'failure') {
      order.payment.refundState = 3
      order.payment.refundTime = now
    } else if (simulate === 'processing') {
      order.state = 3
      order.payment.refundState = 1
      order.payment.refundTime = now
    } else {
      order.state = 4
      order.refundTime = now
      order.payment.refundState = 2
      order.payment.refundTime = now
      if (itemId) unmarkMockColumnOwned(itemId)
    }

    persistMockOrders()
    return response(config, order)
  }

  if (method === 'get' && path === '/api/order/mine') {
    if (localStorage.getItem('wisdomhub:mock.failOrders') === '1') {
      return failure(config, 500, '订单加载失败')
    }
    if (localStorage.getItem('wisdomhub:mock.emptyOrders') === '1') {
      return response(config, pageOf([], config))
    }

    const items = [...mockOrders.values()]
      .slice()
      .sort((left, right) => right.createTime.localeCompare(left.createTime))
      .map((order) => ({
        orderNo: order.orderNo,
        amount: order.amount,
        state: order.state,
        expireTime: order.expireTime,
        paidTime: order.paidTime,
        createTime: order.createTime,
        items: order.items,
      }))
    return response(config, pageOf(items, config))
  }

  const orderDetailMatch = path.match(/^\/api\/order\/([^/]+)$/)
  if (method === 'get' && orderDetailMatch?.[1]) {
    const orderNo = decodeURIComponent(orderDetailMatch[1])
    const order = mockOrders.get(orderNo)
    if (!order) return failure(config, 404, '订单不存在')
    return response(config, order)
  }

  if (method === 'get' && path === '/api/notification/unread/count') {
    const hitsKey = 'wisdomhub:mock.unreadHits'
    localStorage.setItem(hitsKey, String(Number(localStorage.getItem(hitsKey) ?? 0) + 1))
    return response(
      config,
      mockNotifications.filter((notification) => !isNotificationRead(notification)).length,
    )
  }

  if (method === 'get' && path === '/api/notification/list') {
    const items = mockNotifications
      .map((notification) => ({
        ...notification,
        read: isNotificationRead(notification),
      }))
      .sort((a, b) => b.createTime.localeCompare(a.createTime))
    return response(config, pageOf(items, config))
  }

  if (method === 'put' && path === '/api/notification/read-all') {
    mockNotifications.forEach((notification) => {
      mockReadNotifications.add(notification.notificationId)
    })
    persistMockReadNotifications()
    return response(config, null)
  }

  const notificationReadMatch = path.match(/^\/api\/notification\/([^/]+)\/read$/)
  if (method === 'put' && notificationReadMatch?.[1]) {
    const notificationId = decodeURIComponent(notificationReadMatch[1])
    if (!mockNotifications.some((item) => item.notificationId === notificationId)) {
      return failure(config, 404, '通知不存在')
    }
    mockReadNotifications.add(notificationId)
    persistMockReadNotifications()
    return response(config, null)
  }

  if (method === 'get' && path === '/api/echo') {
    return response(config, config.params ?? {})
  }

  return failure(config, 404, `Mock 未实现：${method.toUpperCase()} ${path}`)
}
