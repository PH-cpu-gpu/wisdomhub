<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { RouterLink, useRoute, useRouter } from 'vue-router'

import { isApiError } from '@/api/error'
import { chatStream } from '@/api/modules/agent'
import { deleteConversation, getConversation, listConversations } from '@/api/modules/conversation'
import { tags as searchTags } from '@/api/modules/search'
import WhButton from '@/components/base/WhButton.vue'
import { confirm } from '@/components/base/confirm'
import { toast } from '@/components/base/toast'
import { useClickOutside } from '@/composables/useClickOutside'
import { useSeo } from '@/composables/useSeo'
import { useDictStore } from '@/stores'
import {
  WORKFLOW,
  WORKFLOW_ENDPOINTS,
  WORKFLOW_LABELS,
  isWorkflowKind,
  type AgentEvent,
  type AgentRoute,
  type AgentStep,
  type AgentStepStatus,
  type AgentTechRef,
  type ChatRequest,
  type WorkflowKind,
} from '@/types/agent'
import type { ID } from '@/types/common'
import type { TagVO } from '@/types/content'
import type { ConversationDetailVO, ConversationItemVO } from '@/types/conversation'
import { AI_TEXT_MAX_LENGTH, consumeTextSelection } from '@/utils/ai-entry'
import { renderMarkdown } from '@/utils/markdown'

interface StreamRequest {
  messageId: string
  workflow: WorkflowKind
  question: string
  technology?: string
  knownTechnologies?: string[]
}

interface ChatMessage {
  id: string
  role: 'user' | 'assistant'
  content: string
  status: 'streaming' | 'done' | 'error' | 'stopped'
  error?: string
  html?: string
  steps: AgentStep[]
  /** 重试所需：messageId / 工作流 / 原始问题 */
  request?: StreamRequest
}

interface RouteStage {
  depth: number
  tags: AgentTechRef[]
}

interface StageReference extends RouteStage {
  id: string
}

interface LocalSession {
  id: ID
  title: string
  workflow: WorkflowKind
  conversationId: string | null
  messages: ChatMessage[]
  stageReferences: StageReference[]
  routeTarget: string
  /** 文本问答的选中文本；每轮请求都要带，后端不落库 */
  textContext: string
  /** 是否已从服务端加载过详情；本地草稿视为已加载 */
  detailLoaded: boolean
}

type TagNavigation =
  | { name: 'tech-detail'; params: { tagId: ID } }
  | { name: 'search'; query: { type: string; keyword: string } }

interface DraftMessageSnapshot {
  id: string
  role: 'user' | 'assistant'
  content: string
  status: 'done' | 'error' | 'stopped'
  error?: string
  steps: AgentStep[]
  request?: StreamRequest
}

/** 尚未拿到 meta 的新会话草稿：只存在于本地，不进服务端列表。 */
interface DraftSnapshot {
  id: string
  title: string
  workflow: WorkflowKind
  textContext: string
  routeTarget: string
  stageReferences: StageReference[]
  messages: DraftMessageSnapshot[]
}

/** localStorage 只留草稿：输入内容、路线参数、未上服务端的会话草稿。 */
interface DraftCache {
  draft: string
  targetTechnology: string
  knownTechnologies: string[]
  textContexts: Record<string, string>
  drafts: DraftSnapshot[]
  activeDraftId: string | null
}

const DRAFT_KEY = 'wisdomhub:ai.draft'
/** 会话列表分页大小：后端 pageSize 上限 100 */
const SESSIONS_PAGE_SIZE = 20

const WORKFLOWS: { value: WorkflowKind; label: string; desc: string }[] = [
  { value: WORKFLOW.QA, label: '技术问答', desc: '连续追问的技术答疑' },
  { value: WORKFLOW.LEARNING_PATH, label: '学习路线', desc: '从目标到分阶段计划' },
]

const STEP_STATUS_LABELS: Record<AgentStepStatus, string> = {
  running: '执行中',
  done: '完成',
  error: '失败',
}

const QA_SAMPLES = [
  'Redis 缓存一致性怎么保证？',
  'Spring 事务为什么会失效？',
  '知识图谱的实体关系怎么建模？',
]

const router = useRouter()
const route = useRoute()
const dict = useDictStore()
const draftCache = loadDraftCache()
const sessions = ref<LocalSession[]>([])
const drafts = ref<LocalSession[]>(restoreDrafts(draftCache.drafts))
const restoredActiveDraftId = draftCache.activeDraftId
const activeSessionId = ref<ID>('')
const workflow = ref<WorkflowKind>(WORKFLOW.QA)
const draft = ref(draftCache.draft)
const inputRef = ref<HTMLTextAreaElement | null>(null)
const messagesRef = ref<HTMLElement | null>(null)
const atBottom = ref(true)
const streaming = ref<{ sessionId: ID; messageId: string } | null>(null)
const tagRouteCache = new Map<string, TagNavigation>()
const targetTechnology = ref(draftCache.targetTechnology)
const knownTechnologies = ref<string[]>(
  draftCache.knownTechnologies.filter(
    (item) => item.toLowerCase() !== draftCache.targetTechnology.trim().toLowerCase(),
  ),
)
const targetPickerRef = ref<HTMLElement | null>(null)
const targetOpen = ref(false)
const knownFilter = ref('')
const customKnown = ref('')
const online = ref(typeof navigator === 'undefined' ? true : navigator.onLine)
const sessionsLoading = ref(true)
const detailLoading = ref(false)
const sessionPage = ref(1)
const sessionTotal = ref(0)
const hasMore = ref(false)
const loadingMore = ref(false)
const deletingId = ref<ID | null>(null)

let activeController: AbortController | null = null
let flushTimer: number | null = null
let draftTimer: number | null = null
let detailTicket = 0
const detailTickets = new Map<string, number>()

const activeSession = computed(
  () =>
    drafts.value.find((session) => session.id === activeSessionId.value) ??
    sessions.value.find((session) => session.id === activeSessionId.value) ??
    null,
)
/** 侧栏展示顺序：未发送的草稿在前，服务端会话在后。 */
const listedSessions = computed(() => [...drafts.value, ...sessions.value])
const activeMessages = computed(() => activeSession.value?.messages ?? [])
const stageReferences = computed(() => activeSession.value?.stageReferences ?? [])
const routeTarget = computed(() => activeSession.value?.routeTarget ?? '')
const activeTextContext = computed(() => activeSession.value?.textContext ?? '')
/** 路线流程结束但没有阶段数据：如实提示，不把空侧栏留作“生成中”。 */
const routeMissing = computed(
  () =>
    workflow.value === WORKFLOW.LEARNING_PATH &&
    stageReferences.value.length === 0 &&
    activeMessages.value.some(
      (message) => message.role === 'assistant' && message.status === 'done',
    ),
)

const sessionsMeta = computed(() =>
  sessionsLoading.value
    ? '正在载入会话…'
    : `服务端共 ${sessionTotal.value} 个会话 · 切换时按需加载`,
)
const workflowLabel = computed(() => WORKFLOW_LABELS[workflow.value])
const isStreaming = computed(() => streaming.value !== null)
const canSend = computed(
  () =>
    draft.value.trim().length > 0 &&
    !isStreaming.value &&
    !sessionsLoading.value &&
    !detailLoading.value &&
    (workflow.value !== WORKFLOW.TEXT_QA || activeTextContext.value.length > 0),
)
/** 目标技术必须精确命中标签库，图谱外的技术不生成路线。 */
const matchedTarget = computed(() => {
  const name = targetTechnology.value.trim().toLowerCase()
  return name ? dict.tags.find((tag) => tag.name.toLowerCase() === name) : undefined
})
const canGenerateRoute = computed(() => matchedTarget.value !== undefined && !isStreaming.value)

/** 目标技术候选：按输入过滤标签库；不限制 8 个，输入为空时给出前 12 个。 */
const targetOptions = computed(() => {
  const keyword = targetTechnology.value.trim().toLowerCase()
  const matched = dict.tags.filter(
    (tag) =>
      !keyword ||
      tag.name.toLowerCase().includes(keyword) ||
      tag.category.toLowerCase().includes(keyword) ||
      tag.description.toLowerCase().includes(keyword),
  )
  return matched.slice(0, 12)
})

/** 已掌握候选：排除当前目标技术，再按搜索词过滤。 */
const filteredKnown = computed(() => {
  const keyword = knownFilter.value.trim().toLowerCase()
  const target = targetTechnology.value.trim().toLowerCase()
  return dict.tags.filter((tag) => {
    if (target && tag.name.toLowerCase() === target) return false
    if (!keyword) return true
    return (
      tag.name.toLowerCase().includes(keyword) ||
      tag.category.toLowerCase().includes(keyword) ||
      tag.description.toLowerCase().includes(keyword)
    )
  })
})

/** 按标签库原有 category 分组，避免 100+ 标签平铺成一坨。 */
const knownGroups = computed<{ category: string; tags: TagVO[] }[]>(() => {
  const groups: { category: string; tags: TagVO[] }[] = []
  const index = new Map<string, number>()
  for (const tag of filteredKnown.value) {
    let position = index.get(tag.category)
    if (position === undefined) {
      position = groups.length
      index.set(tag.category, position)
      groups.push({ category: tag.category, tags: [] })
    }
    groups[position].tags.push(tag)
  }
  return groups
})

const composerHint = computed(() => {
  if (isStreaming.value) return '正在生成回答，可随时停止'
  return 'Enter 发送 · Shift + Enter 换行'
})

const statusLabel = computed(() => {
  if (workflow.value === WORKFLOW.QA) {
    return isStreaming.value ? '正在回答…' : 'QA 可用'
  }
  if (workflow.value === WORKFLOW.TEXT_QA) {
    return isStreaming.value ? '正在分析…' : '文本问答'
  }
  return isStreaming.value ? '正在规划路线…' : '学习路线'
})

const workbenchEyebrow = computed(() => {
  if (workflow.value === WORKFLOW.QA) return '[ QA / WORKBENCH ]'
  if (workflow.value === WORKFLOW.TEXT_QA) return '[ TEXT / WORKBENCH ]'
  return '[ LEARNING / WORKBENCH ]'
})

function queryValue(value: unknown): string {
  if (Array.isArray(value)) return typeof value[0] === 'string' ? value[0] : ''
  return typeof value === 'string' ? value : ''
}

/** 深链只消费一次：应用后清掉 query，刷新不会重复带入文本。 */
async function applyEntryQuery(): Promise<void> {
  const rawWorkflow = queryValue(route.query.workflow)
  const technology = queryValue(route.query.technology).trim()
  const tagId = queryValue(route.query.tagId).trim()
  if (!rawWorkflow && !technology && !tagId) return

  const parsedWorkflow = Number(rawWorkflow)
  const mode: WorkflowKind | null = isWorkflowKind(parsedWorkflow) ? parsedWorkflow : null

  if (technology || tagId) {
    let target = technology
    if (tagId) {
      await dict.loadTags()
      target = dict.getTag(tagId)?.name ?? target
    }
    if (target) targetTechnology.value = target
  }

  if (mode === WORKFLOW.TEXT_QA) {
    const text = consumeTextSelection()
    if (text) {
      const reuse = drafts.value.find((session) => session.messages.length === 0)
      const draftSession = reuse ?? createDraftSession(createId(), WORKFLOW.TEXT_QA)
      if (!reuse) drafts.value.unshift(draftSession)
      draftSession.workflow = WORKFLOW.TEXT_QA
      draftSession.textContext = text
      activeSessionId.value = draftSession.id
      workflow.value = WORKFLOW.TEXT_QA
      draft.value = ''
      atBottom.value = true
      scheduleDraftSave()
      focusInput()
    } else {
      toast.info('未读取到选中的文本，请回到文章中重新选择。')
    }
  } else if (mode !== null) {
    selectWorkflow(mode)
  }

  await router.replace({ query: {} })
}

/** 新会话在拿到 meta 之前只是本地草稿，不产生服务端记录。 */
function createDraftSession(id: ID, nextWorkflow: WorkflowKind): LocalSession {
  return {
    id,
    title: '新的对话',
    workflow: nextWorkflow,
    conversationId: null,
    messages: [],
    stageReferences: [],
    routeTarget: '',
    textContext: '',
    detailLoaded: true,
  }
}

function createId(): string {
  if (typeof crypto !== 'undefined' && typeof crypto.randomUUID === 'function') {
    return crypto.randomUUID()
  }
  return `id-${Date.now()}-${Math.random().toString(16).slice(2)}`
}

function isAgentStep(value: unknown): value is AgentStep {
  if (typeof value !== 'object' || value === null) return false
  const record = value as Partial<AgentStep>
  return (
    typeof record.id === 'string' &&
    typeof record.tool === 'string' &&
    typeof record.label === 'string' &&
    (record.status === 'running' || record.status === 'done' || record.status === 'error')
  )
}

const cachedTextContexts = new Map<string, string>(Object.entries(draftCache.textContexts))

function isStreamRequest(value: unknown): value is StreamRequest {
  if (typeof value !== 'object' || value === null) return false
  const record = value as Partial<StreamRequest>
  return (
    typeof record.messageId === 'string' &&
    isWorkflowKind(record.workflow) &&
    typeof record.question === 'string' &&
    (record.technology === undefined || typeof record.technology === 'string') &&
    (record.knownTechnologies === undefined ||
      (Array.isArray(record.knownTechnologies) &&
        record.knownTechnologies.every((item) => typeof item === 'string')))
  )
}

function isTechRef(value: unknown): value is AgentTechRef {
  if (typeof value !== 'object' || value === null) return false
  const record = value as Partial<AgentTechRef>
  return typeof record.tagId === 'string' && typeof record.name === 'string'
}

function parseStageReferences(value: unknown): StageReference[] {
  if (!Array.isArray(value)) return []
  const stages: StageReference[] = []
  for (const item of value) {
    if (typeof item !== 'object' || item === null) continue
    const record = item as Partial<StageReference>
    if (typeof record.id !== 'string' || typeof record.depth !== 'number') continue
    if (!Array.isArray(record.tags)) continue
    stages.push({ id: record.id, depth: record.depth, tags: record.tags.filter(isTechRef) })
  }
  return stages
}

function parseDraftMessages(value: unknown): DraftMessageSnapshot[] {
  if (!Array.isArray(value)) return []
  const messages: DraftMessageSnapshot[] = []
  for (const item of value) {
    if (typeof item !== 'object' || item === null) continue
    const record = item as Partial<DraftMessageSnapshot> & { status?: unknown }
    if (typeof record.id !== 'string' || typeof record.content !== 'string') continue
    if (record.role !== 'user' && record.role !== 'assistant') continue
    // 缓存时仍在流式的消息，恢复后按“已停止”收口
    const status =
      record.status === 'done' || record.status === 'error' || record.status === 'stopped'
        ? record.status
        : 'stopped'
    messages.push({
      id: record.id,
      role: record.role,
      content: record.content,
      status,
      error: typeof record.error === 'string' ? record.error : undefined,
      steps: Array.isArray(record.steps) ? record.steps.filter(isAgentStep) : [],
      request: isStreamRequest(record.request) ? record.request : undefined,
    })
  }
  return messages
}

function parseDraftSnapshots(value: unknown): DraftSnapshot[] {
  if (!Array.isArray(value)) return []
  const snapshots: DraftSnapshot[] = []
  for (const item of value) {
    if (typeof item !== 'object' || item === null) continue
    const record = item as Partial<DraftSnapshot>
    if (typeof record.id !== 'string' || !isWorkflowKind(record.workflow)) continue
    snapshots.push({
      id: record.id,
      title: typeof record.title === 'string' && record.title ? record.title : '新的对话',
      workflow: record.workflow,
      textContext:
        typeof record.textContext === 'string'
          ? record.textContext.slice(0, AI_TEXT_MAX_LENGTH)
          : '',
      routeTarget: typeof record.routeTarget === 'string' ? record.routeTarget : '',
      stageReferences: parseStageReferences(record.stageReferences),
      messages: parseDraftMessages(record.messages),
    })
  }
  return snapshots
}

function restoreDrafts(snapshots: DraftSnapshot[]): LocalSession[] {
  return snapshots.map((snapshot) => ({
    id: snapshot.id,
    title: snapshot.title,
    workflow: snapshot.workflow,
    conversationId: null,
    messages: snapshot.messages.map((message) => ({
      ...message,
      html: renderMarkdown(message.content),
    })),
    stageReferences: snapshot.stageReferences,
    routeTarget: snapshot.routeTarget,
    textContext: snapshot.textContext,
    detailLoaded: true,
  }))
}

/** 只缓存有内容的草稿：空草稿刷新后没有可恢复的东西。 */
function serializeDrafts(): DraftSnapshot[] {
  return drafts.value
    .filter((session) => session.messages.length > 0 || session.textContext)
    .map((session) => ({
      id: session.id,
      title: session.title,
      workflow: session.workflow,
      textContext: session.textContext,
      routeTarget: session.routeTarget,
      stageReferences: session.stageReferences,
      messages: session.messages.map((message) => ({
        id: message.id,
        role: message.role,
        content: message.content,
        status: message.status === 'streaming' ? 'stopped' : message.status,
        error: message.error,
        steps: message.steps,
        request: message.request,
      })),
    }))
}

function loadDraftCache(): DraftCache {
  const empty: DraftCache = {
    draft: '',
    targetTechnology: '',
    knownTechnologies: [],
    textContexts: {},
    drafts: [],
    activeDraftId: null,
  }
  try {
    const raw = localStorage.getItem(DRAFT_KEY)
    if (!raw) return empty
    const parsed = JSON.parse(raw) as Partial<DraftCache>

    const textContexts: Record<string, string> = {}
    if (parsed.textContexts && typeof parsed.textContexts === 'object') {
      for (const [key, value] of Object.entries(parsed.textContexts)) {
        if (typeof value === 'string') textContexts[key] = value.slice(0, AI_TEXT_MAX_LENGTH)
      }
    }

    return {
      draft: typeof parsed.draft === 'string' ? parsed.draft : '',
      targetTechnology: typeof parsed.targetTechnology === 'string' ? parsed.targetTechnology : '',
      knownTechnologies: Array.isArray(parsed.knownTechnologies)
        ? parsed.knownTechnologies.filter((item): item is string => typeof item === 'string')
        : [],
      textContexts,
      drafts: parseDraftSnapshots(parsed.drafts),
      activeDraftId: typeof parsed.activeDraftId === 'string' ? parsed.activeDraftId : null,
    }
  } catch {
    return empty
  }
}

/** 只落草稿：输入、路线参数、未上服务端的会话；服务端会话内容以接口为准。 */
function saveDraft(): void {
  try {
    const textContexts: Record<string, string> = Object.fromEntries(cachedTextContexts)
    for (const session of sessions.value) {
      if (!session.textContext) continue
      textContexts[session.conversationId ?? session.id] = session.textContext
    }

    const payload: DraftCache = {
      draft: draft.value,
      targetTechnology: targetTechnology.value,
      knownTechnologies: [...knownTechnologies.value],
      textContexts,
      drafts: serializeDrafts(),
      activeDraftId: drafts.value.some((session) => session.id === activeSessionId.value)
        ? activeSessionId.value
        : null,
    }
    localStorage.setItem(DRAFT_KEY, JSON.stringify(payload))
  } catch {
    // 隐私模式 / 存储配额不足时静默跳过，不影响对话
  }
}

function scheduleDraftSave(): void {
  if (draftTimer !== null) window.clearTimeout(draftTimer)
  draftTimer = window.setTimeout(() => {
    draftTimer = null
    saveDraft()
  }, 300)
}

function toSessionSummary(item: ConversationItemVO): LocalSession {
  return {
    id: item.id,
    title: item.title || '新的对话',
    workflow: isWorkflowKind(item.workflow) ? item.workflow : WORKFLOW.QA,
    conversationId: item.id,
    messages: [],
    stageReferences: [],
    routeTarget: '',
    textContext: cachedTextContexts.get(item.id)?.slice(0, AI_TEXT_MAX_LENGTH) ?? '',
    detailLoaded: false,
  }
}

async function bootstrapSessions(): Promise<void> {
  sessionsLoading.value = true
  try {
    const result = await listConversations(
      { page: 1, pageSize: SESSIONS_PAGE_SIZE },
      { skipErrorToast: true },
    )
    const summaries = result.items.map(toSessionSummary)
    sessionPage.value = result.page
    sessionTotal.value = result.total
    hasMore.value = result.page < result.pages
    sessions.value = summaries

    const restored = drafts.value.find((session) => session.id === restoredActiveDraftId)
    if (restored) {
      activeSessionId.value = restored.id
      workflow.value = restored.workflow
      return
    }

    if (summaries.length) {
      const first = summaries[0]
      activeSessionId.value = first.id
      workflow.value = first.workflow
      await loadSessionDetail(first)
      return
    }

    if (!drafts.value.length) drafts.value.push(createDraftSession(createId(), WORKFLOW.QA))
    const draft = drafts.value[0]
    activeSessionId.value = draft.id
    workflow.value = draft.workflow
  } catch {
    sessionPage.value = 1
    sessionTotal.value = 0
    hasMore.value = false
    sessions.value = []
    if (!drafts.value.length) drafts.value.push(createDraftSession(createId(), WORKFLOW.QA))
    const draft = drafts.value[0]
    activeSessionId.value = draft.id
    workflow.value = draft.workflow
    toast.warning('会话列表加载失败，已切换为本地新建对话')
  } finally {
    sessionsLoading.value = false
  }
}

/** 追加下一页；服务端按 updated_at 排序，客户端只按会话 id 去重、不重排。 */
async function loadMoreSessions(): Promise<void> {
  if (loadingMore.value || sessionsLoading.value || deletingId.value || !hasMore.value) return

  loadingMore.value = true
  try {
    const result = await listConversations(
      { page: sessionPage.value + 1, pageSize: SESSIONS_PAGE_SIZE },
      { skipErrorToast: true },
    )
    const knownIds = new Set(
      sessions.value
        .map((session) => session.conversationId)
        .filter((id): id is string => id !== null),
    )
    const appended = result.items.filter((item) => !knownIds.has(item.id)).map(toSessionSummary)

    if (appended.length) sessions.value.push(...appended)
    sessionPage.value = result.page
    sessionTotal.value = result.total
    hasMore.value = result.page < result.pages
  } catch {
    toast.error('加载更多会话失败，请重试')
  } finally {
    loadingMore.value = false
  }
}

async function loadSessionDetail(session: LocalSession): Promise<void> {
  const conversationId = session.conversationId
  if (session.detailLoaded || !conversationId || detailTickets.has(session.id)) return

  const ticket = ++detailTicket
  detailTickets.set(session.id, ticket)
  if (activeSessionId.value === session.id) detailLoading.value = true

  try {
    const detail = await getConversation(conversationId, { skipErrorToast: true })
    // 请求期间切换 / 重载会话时，旧响应直接丢弃，避免串到别的会话
    if (detailTickets.get(session.id) !== ticket) return
    if (!sessions.value.includes(session)) return
    applyDetail(session, detail)
  } catch {
    if (detailTickets.get(session.id) === ticket && activeSessionId.value === session.id) {
      toast.error('会话详情加载失败，请稍后重试')
    }
  } finally {
    if (detailTickets.get(session.id) === ticket) {
      detailTickets.delete(session.id)
      if (activeSessionId.value === session.id) detailLoading.value = false
    }
  }
}

function applyDetail(session: LocalSession, detail: ConversationDetailVO): void {
  session.messages = detail.messages.map((message) => ({
    id: message.id,
    role: message.role,
    content: message.content,
    status: message.status === 'done' ? 'done' : message.status === 'error' ? 'error' : 'stopped',
    steps: Array.isArray(message.steps) ? message.steps.filter(isAgentStep) : [],
    html: renderMarkdown(message.content),
  }))
  session.detailLoaded = true
  if (detail.title) session.title = detail.title

  let nextWorkflow: WorkflowKind | null = isWorkflowKind(detail.workflow) ? detail.workflow : null
  if (nextWorkflow === null) {
    for (let index = detail.messages.length - 1; index >= 0; index -= 1) {
      const itemWorkflow = detail.messages[index].workflow
      if (isWorkflowKind(itemWorkflow)) {
        nextWorkflow = itemWorkflow
        break
      }
    }
  }
  if (nextWorkflow !== null) session.workflow = nextWorkflow

  let route: AgentRoute | null = null
  for (let index = detail.messages.length - 1; index >= 0; index -= 1) {
    const item = detail.messages[index]
    if (item.role === 'assistant' && item.artifacts?.route) {
      route = item.artifacts.route
      break
    }
  }
  if (route) {
    applyRoute(session, route)
  } else {
    session.routeTarget = ''
    session.stageReferences = []
  }

  if (session.workflow === WORKFLOW.TEXT_QA) {
    session.textContext =
      cachedTextContexts.get(session.conversationId ?? session.id)?.slice(0, AI_TEXT_MAX_LENGTH) ??
      ''
  }

  if (activeSessionId.value === session.id) {
    workflow.value = session.workflow
    atBottom.value = true
    void nextTick(scrollToBottom)
  }
}

async function removeSession(session: LocalSession): Promise<void> {
  if (deletingId.value) return

  const isDraft = session.conversationId === null
  const ok = await confirm({
    title: isDraft ? '删除草稿' : '删除会话',
    message: isDraft
      ? `删除后草稿「${session.title}」中未发送的内容将无法恢复，确定删除？`
      : `删除后「${session.title}」与其中的消息将无法恢复，确定删除？`,
    confirmText: '删除',
    danger: true,
  })
  if (!ok) return

  if (isDraft) {
    removeDraftSession(session)
    scheduleDraftSave()
    toast.success('草稿已删除')
    return
  }

  const conversationId = session.conversationId
  if (!conversationId) return

  deletingId.value = session.id
  try {
    await deleteConversation(conversationId, { skipErrorToast: true })
    removeServerSession(session)
    scheduleDraftSave()
    toast.success('会话已删除')
    await refreshSessionsAfterDelete()
  } catch (error) {
    if (isApiError(error) && error.code === 404) {
      // 已被其他端删除或不属于当前用户：按“已删除”收口
      removeServerSession(session)
      scheduleDraftSave()
      toast.info('会话不存在或已被删除，已从列表移除')
      await refreshSessionsAfterDelete()
    } else {
      toast.error(isApiError(error) ? error.message : '删除失败，请稍后重试')
    }
  } finally {
    deletingId.value = null
  }
}

function stopSessionStream(session: LocalSession): void {
  if (streaming.value?.sessionId !== session.id) return
  activeController?.abort()
  streaming.value = null
}

/** 草稿只存在于本地：删除不调接口，也不影响服务端会话总数。 */
function removeDraftSession(session: LocalSession): void {
  stopSessionStream(session)

  const index = drafts.value.findIndex((item) => item.id === session.id)
  if (index < 0) return
  drafts.value.splice(index, 1)

  if (activeSessionId.value !== session.id) return

  const next = drafts.value[index] ?? drafts.value[0] ?? sessions.value[0]
  if (next) {
    activateSession(next)
    return
  }
  const fresh = createDraftSession(createId(), session.workflow)
  if (session.workflow === WORKFLOW.TEXT_QA) fresh.textContext = session.textContext
  drafts.value.push(fresh)
  activeSessionId.value = fresh.id
  workflow.value = fresh.workflow
  detailLoading.value = false
  draft.value = ''
}

function removeServerSession(session: LocalSession): void {
  stopSessionStream(session)

  const index = sessions.value.findIndex((item) => item.id === session.id)
  if (index < 0) return
  sessions.value.splice(index, 1)

  sessionTotal.value = Math.max(0, sessionTotal.value - 1)
  hasMore.value = sessions.value.length < sessionTotal.value

  if (activeSessionId.value !== session.id) return

  const next =
    sessions.value[Math.min(index, sessions.value.length - 1)] ??
    sessions.value[0] ??
    drafts.value[0]
  if (next) {
    activateSession(next)
    return
  }
  const fresh = createDraftSession(createId(), session.workflow)
  if (session.workflow === WORKFLOW.TEXT_QA) fresh.textContext = session.textContext
  drafts.value.push(fresh)
  activeSessionId.value = fresh.id
  workflow.value = fresh.workflow
  detailLoading.value = false
  draft.value = ''
}

/** 删除后同步列表：只回第一页，按会话 id 复用已加载对象，避免 offset 分页漂移。 */
async function refreshSessionsAfterDelete(): Promise<void> {
  try {
    const result = await listConversations(
      { page: 1, pageSize: SESSIONS_PAGE_SIZE },
      { skipErrorToast: true },
    )
    const existing = new Map<string, LocalSession>()
    for (const session of sessions.value) {
      if (session.conversationId) existing.set(session.conversationId, session)
    }
    const firstPage = result.items.map((item) => existing.get(item.id) ?? toSessionSummary(item))

    sessions.value = firstPage
    sessionPage.value = result.page
    sessionTotal.value = result.total
    hasMore.value = result.page < result.pages

    const activeIsDraft = drafts.value.some((session) => session.id === activeSessionId.value)
    if (!activeIsDraft && !sessions.value.some((session) => session.id === activeSessionId.value)) {
      const next = drafts.value[0] ?? sessions.value[0]
      if (next) activateSession(next)
    }
  } catch {
    // 删除已经生效，刷新失败时沿用本地移除结果
  }
}

/** 首次 meta 到达：草稿晋升为服务端会话，进入服务端列表并计入总数。 */
function promoteDraft(session: LocalSession): void {
  const index = drafts.value.findIndex((item) => item.id === session.id)
  if (index < 0) return
  drafts.value.splice(index, 1)
  sessions.value.unshift(session)
  sessionTotal.value += 1
  hasMore.value = sessions.value.length < sessionTotal.value
}

function activateSession(session: LocalSession): void {
  activeSessionId.value = session.id
  workflow.value = session.workflow
  atBottom.value = true
  detailLoading.value = !session.detailLoaded && session.conversationId !== null
  void nextTick(scrollToBottom)
  if (!session.detailLoaded) void loadSessionDetail(session)
}

function focusInput(): void {
  void nextTick(() => inputRef.value?.focus())
}

function selectWorkflow(next: WorkflowKind): void {
  if (workflow.value === next) return
  workflow.value = next

  const session = activeSession.value
  if (session) {
    // 已有消息的会话不混用模式，切模式时开一个新会话
    if (session.messages.length > 0 && session.workflow !== next) {
      newSession()
    } else {
      session.workflow = next
      if (next !== WORKFLOW.TEXT_QA) session.textContext = ''
    }
  }

  if (next === WORKFLOW.LEARNING_PATH) void dict.loadTags()
  scheduleDraftSave()
}

function selectSession(session: LocalSession): void {
  activeSessionId.value = session.id
  workflow.value = session.workflow
  draft.value = ''
  atBottom.value = true
  detailLoading.value = !session.detailLoaded && session.conversationId !== null
  void nextTick(scrollToBottom)
  scheduleDraftSave()
  if (!session.detailLoaded) void loadSessionDetail(session)
}

function newSession(): void {
  // 空白草稿复用，不产生多条空会话；已有内容的草稿保留
  const emptyDraft = drafts.value.find((session) => session.messages.length === 0)
  const session = emptyDraft ?? createDraftSession(createId(), workflow.value)
  if (!emptyDraft) drafts.value.unshift(session)

  session.workflow = workflow.value
  session.textContext = workflow.value === WORKFLOW.TEXT_QA ? activeTextContext.value : ''
  activeSessionId.value = session.id
  workflow.value = session.workflow
  draft.value = ''
  atBottom.value = true

  scheduleDraftSave()
  focusInput()
}

function useSample(text: string): void {
  draft.value = text
  focusInput()
}

function chooseTarget(name: string): void {
  targetTechnology.value = name
  targetOpen.value = false
}

function toggleKnown(name: string): void {
  const index = knownTechnologies.value.indexOf(name)
  if (index >= 0) knownTechnologies.value.splice(index, 1)
  else knownTechnologies.value.push(name)
}

/** 已掌握支持自由输入：命中标签库时归一成标签名，否则保留原文。 */
function addCustomKnown(): void {
  const value = customKnown.value.trim()
  if (!value) return

  const existing = knownTechnologies.value.find(
    (item) => item.toLowerCase() === value.toLowerCase(),
  )
  if (existing) {
    toast.info('这项技术已经在已掌握列表里了')
    customKnown.value = ''
    return
  }

  const canonical = dict.tags.find((tag) => tag.name.toLowerCase() === value.toLowerCase())?.name
  knownTechnologies.value.push(canonical ?? value)
  customKnown.value = ''
}

watch(targetTechnology, (value) => {
  const target = value.trim().toLowerCase()
  if (!target) return
  const index = knownTechnologies.value.findIndex((item) => item.toLowerCase() === target)
  if (index >= 0) knownTechnologies.value.splice(index, 1)
})

useClickOutside(targetPickerRef, () => {
  targetOpen.value = false
})

watch([targetTechnology, knownTechnologies], scheduleDraftSave, { deep: true })
watch(draft, scheduleDraftSave)

function submit(): void {
  if (isStreaming.value || sessionsLoading.value || detailLoading.value) return
  const question = draft.value.trim()
  if (!question) return

  const session = activeSession.value
  if (!session) return
  if (workflow.value === WORKFLOW.TEXT_QA && !session.textContext) {
    toast.info('选中文本已失效，请回到文章中重新选择文本')
    return
  }
  void sendMessage(session, question, workflow.value)
}

function generateRoute(): void {
  if (isStreaming.value) return
  const matched = matchedTarget.value
  if (!matched) {
    toast.info(
      targetTechnology.value.trim() ? '请从标签库中选择目标技术' : '请先选择目标技术',
    )
    return
  }

  const session = activeSession.value
  if (!session) return

  targetTechnology.value = matched.name
  const question = `请为我规划 ${matched.name} 的学习路线`
  void sendMessage(session, question, WORKFLOW.LEARNING_PATH, matched.name, [
    ...knownTechnologies.value,
  ])
}

async function sendMessage(
  session: LocalSession,
  question: string,
  mode: WorkflowKind,
  technology?: string,
  known?: string[],
): Promise<void> {
  const request: StreamRequest = {
    messageId: createId(),
    workflow: mode,
    question,
    technology,
    knownTechnologies: known ?? [],
  }
  const user: ChatMessage = {
    id: createId(),
    role: 'user',
    content: question,
    status: 'done',
    steps: [],
  }
  // reactive：流式增量要直接触发视图更新，不能只改数组里的原始对象
  const assistant = reactive<ChatMessage>({
    id: createId(),
    role: 'assistant',
    content: '',
    status: 'streaming',
    steps: [],
    request,
  })

  session.messages.push(user, assistant)
  if (session.title === '新的对话') {
    session.title = truncate(
      mode === WORKFLOW.LEARNING_PATH ? `规划 ${technology ?? question}` : question,
      18,
    )
  }
  session.workflow = mode
  session.routeTarget = mode === WORKFLOW.LEARNING_PATH ? (technology ?? '') : ''
  session.stageReferences = []
  workflow.value = mode
  draft.value = ''
  scheduleDraftSave()

  atBottom.value = true
  void nextTick(scrollToBottom)

  await runStream(session, assistant)
}

async function runStream(session: LocalSession, assistant: ChatMessage): Promise<void> {
  const request = assistant.request
  if (!request) return

  const controller = new AbortController()
  activeController = controller
  streaming.value = { sessionId: session.id, messageId: assistant.id }

  let buffer = ''
  let terminal = false

  const flush = (): void => {
    if (!buffer) return
    assistant.content += buffer
    buffer = ''
    if (activeSessionId.value === session.id && atBottom.value) {
      void nextTick(scrollToBottom)
    }
  }

  const scheduleFlush = (): void => {
    if (flushTimer !== null) return
    flushTimer = window.setTimeout(() => {
      flushTimer = null
      flush()
    }, 50)
  }

  const finalize = (status: ChatMessage['status'], error?: string): void => {
    if (flushTimer !== null) {
      window.clearTimeout(flushTimer)
      flushTimer = null
    }
    flush()

    assistant.status = status
    assistant.error = error

    if (status !== 'streaming') {
      assistant.html = renderMarkdown(assistant.content)
    }

    if (activeSessionId.value === session.id && atBottom.value) {
      void nextTick(scrollToBottom)
    }

    scheduleDraftSave()
  }

  const handleEvent = (event: AgentEvent): void => {
    if (event.event === 'meta') {
      const previousId = session.conversationId
      session.conversationId = event.data.conversationId
      session.detailLoaded = true
      promoteDraft(session)
      if (session.textContext) {
        if (previousId) cachedTextContexts.delete(previousId)
        // 草稿阶段可能按本地 id 记过选中文本，晋升后统一挂到会话 id 上
        if (session.id !== session.conversationId) cachedTextContexts.delete(session.id)
        cachedTextContexts.set(session.conversationId, session.textContext)
      }
      const request = assistant.request
      if (request && request.messageId !== event.data.messageId) {
        request.messageId = event.data.messageId
      }
      scheduleDraftSave()
      return
    }
    if (event.event === 'route') {
      applyRoute(session, event.data)
      return
    }
    if (event.event === 'steps') {
      assistant.steps = event.data.steps
      return
    }
    if (event.event === 'delta') {
      buffer += event.data.text
      scheduleFlush()
      return
    }
    if (event.event === 'done') {
      terminal = true
      finalize('done')
      void syncTurnDetail(session, assistant)
      return
    }
    if (event.event === 'error') {
      terminal = true
      finalize('error', event.data.message)
    }
  }

  const endpoint = WORKFLOW_ENDPOINTS[request.workflow]
  const payload: ChatRequest =
    request.workflow === WORKFLOW.LEARNING_PATH
      ? {
          conversationId: session.conversationId,
          messageId: request.messageId,
          question: request.question,
          technology: request.technology ?? '',
          knownTechnologies: request.knownTechnologies ?? [],
        }
      : request.workflow === WORKFLOW.TEXT_QA
        ? {
            conversationId: session.conversationId,
            messageId: request.messageId,
            question: request.question,
            text: session.textContext,
          }
        : {
            conversationId: session.conversationId,
            messageId: request.messageId,
            question: request.question,
          }

  try {
    await chatStream(endpoint, payload, {
      signal: controller.signal,
      onEvent: handleEvent,
    })

    if (controller.signal.aborted) {
      finalize('stopped')
    } else if (!terminal) {
      finalize('error', '回答中断，请重试。')
    }
  } catch (error) {
    if (controller.signal.aborted) {
      finalize('stopped')
    } else {
      finalize('error', isApiError(error) ? error.message : '回答失败，请稍后重试。')
    }
  } finally {
    if (flushTimer !== null) {
      window.clearTimeout(flushTimer)
      flushTimer = null
    }
    if (assistant.status === 'streaming') finalize('error', '回答中断，请重试。')
    if (activeController === controller) activeController = null
    if (streaming.value?.messageId === assistant.id) streaming.value = null
  }
}

function retryMessage(message: ChatMessage): void {
  if (isStreaming.value || !message.request) return

  const session = activeSession.value
  if (!session || message.role !== 'assistant') return

  // 用户主动停止：换新 messageId；异常 / 断流：沿用原 messageId 重放
  const request: StreamRequest =
    message.status === 'stopped' ? { ...message.request, messageId: createId() } : message.request

  message.request = request
  message.content = ''
  message.error = undefined
  message.html = undefined
  message.steps = []
  message.status = 'streaming'

  session.workflow = request.workflow
  session.routeTarget =
    request.workflow === WORKFLOW.LEARNING_PATH ? (request.technology ?? '') : ''
  workflow.value = request.workflow
  session.stageReferences = []
  scheduleDraftSave()

  atBottom.value = true
  void nextTick(scrollToBottom)
  void runStream(session, message)
}

function stopStreaming(): void {
  activeController?.abort()
}

function handleOffline(): void {
  online.value = false
  toast.warning('网络已断开，回答可能中断')
}

function handleOnline(): void {
  online.value = true
  toast.success('网络已恢复，可重试失败的问答')
}

/** route 事件直出阶段数据：不再解析模型输出的 markdown。 */
function applyRoute(session: LocalSession, route: AgentRoute): void {
  session.routeTarget = route.target.name
  session.stageReferences = route.stages.map((stage) => ({
    id: `stage-${stage.depth}`,
    depth: stage.depth,
    tags: [...stage.tags],
  }))
}

/** done 后以服务端详情为准回填终态：SSE 漏包或 REPLAY 时补齐 steps / artifacts。 */
async function syncTurnDetail(session: LocalSession, assistant: ChatMessage): Promise<void> {
  const conversationId = session.conversationId
  const request = assistant.request
  if (!conversationId || !request) return

  const detail = await getConversation(conversationId, { skipErrorToast: true }).catch(() => null)
  if (!detail) return
  if (assistant.request !== request || assistant.status !== 'done') return

  const message = detail.messages.find(
    (item) => item.messageId === request.messageId && item.role === 'assistant',
  )
  if (!message) return

  if (message.content) {
    assistant.content = message.content
    assistant.html = renderMarkdown(message.content)
  }
  if (message.steps) assistant.steps = message.steps.filter(isAgentStep)
  if (message.artifacts?.route) applyRoute(session, message.artifacts.route)

  if (message.status === 'error') assistant.status = 'error'
  else if (message.status === 'aborted') assistant.status = 'stopped'

  if (detail.title) session.title = detail.title
}

function resetRoute(): void {
  if (isStreaming.value) return
  newSession()
}

function onMessagesScroll(): void {
  const element = messagesRef.value
  if (!element) return
  atBottom.value = element.scrollHeight - element.scrollTop - element.clientHeight < 80
}

function scrollToBottom(): void {
  const element = messagesRef.value
  if (!element) return
  element.scrollTop = element.scrollHeight
  atBottom.value = true
}

async function copyMessage(message: ChatMessage): Promise<void> {
  if (!message.content) return
  try {
    if (!navigator.clipboard?.writeText) throw new Error('clipboard unavailable')
    await navigator.clipboard.writeText(message.content)
    toast.success('已复制回答')
  } catch {
    toast.error('复制失败，请手动选择文本')
  }
}

async function onMarkdownClick(event: MouseEvent): Promise<void> {
  const anchor = (event.target as Element | null)?.closest('a')
  if (!anchor) return

  const href = anchor.getAttribute('href') ?? ''
  if (!href || href.startsWith('#') || /^https?:\/\//i.test(href)) return
  if (!href.startsWith('/') || href.startsWith('//')) return

  // 技术链接：模型输出 /tag/<name>，本站路由是 /tech/:tagId
  if (href.startsWith('/tag/')) {
    event.preventDefault()
    let name = href.slice('/tag/'.length)
    try {
      name = decodeURIComponent(name)
    } catch {
      // 保留原始编码
    }
    if (!name) return

    const cached = tagRouteCache.get(name)
    if (cached) {
      await router.push(cached)
      return
    }

    try {
      const result = await searchTags({ keyword: name, pageSize: 1 }, { skipErrorToast: true })
      const match = result.items.find((item) => item.name.toLowerCase() === name.toLowerCase())
      const target: TagNavigation = match
        ? { name: 'tech-detail', params: { tagId: match.tagId } }
        : { name: 'search', query: { type: 'tag', keyword: name } }
      tagRouteCache.set(name, target)
      await router.push(target)
    } catch {
      await router.push({ name: 'search', query: { type: 'tag', keyword: name } })
    }
    return
  }

  // 其他站内链接走 SPA，避免整页刷新
  event.preventDefault()
  await router.push(href)
}

function truncate(text: string, max: number): string {
  const value = text.replace(/\s+/g, ' ').trim()
  return value.length > max ? `${value.slice(0, max)}…` : value
}

watch(
  () => route.query,
  () => {
    void applyEntryQuery()
  },
)

onMounted(async () => {
  window.addEventListener('pagehide', saveDraft)
  window.addEventListener('offline', handleOffline)
  window.addEventListener('online', handleOnline)
  void dict.loadTags()
  await bootstrapSessions()
  await applyEntryQuery()
})

onBeforeUnmount(() => {
  window.removeEventListener('pagehide', saveDraft)
  window.removeEventListener('offline', handleOffline)
  window.removeEventListener('online', handleOnline)
  saveDraft()
  activeController?.abort()
  if (flushTimer !== null) window.clearTimeout(flushTimer)
  if (draftTimer !== null) window.clearTimeout(draftTimer)
})

useSeo({
  title: 'AI 技术导师',
  description: '技术问答与学习路线双模式的工作台：会话、消息与参考资料。',
})
</script>

<template>
  <section class="ai-page">
    <div class="ai-shell" :class="{ 'ai-shell--solo': workflow !== WORKFLOW.LEARNING_PATH }">
      <aside class="ai-side ai-side--sessions" aria-label="会话与模式">
        <header class="ai-brand">
          <p class="ai-eyebrow">[ AI / TUTOR ]</p>
          <h1>AI 技术导师</h1>
          <span class="ai-brand__tag">BETA</span>
        </header>

        <div class="ai-modes" role="group" aria-label="对话模式">
          <button
            v-for="mode in WORKFLOWS"
            :key="mode.value"
            class="ai-mode"
            :class="{ 'is-active': workflow === mode.value }"
            type="button"
            :aria-pressed="workflow === mode.value"
            @click="selectWorkflow(mode.value)"
          >
            <span class="ai-mode__label">{{ mode.label }}</span>
            <span class="ai-mode__desc">{{ mode.desc }}</span>
          </button>
        </div>

        <div class="ai-sessions">
          <div class="ai-sessions__head">
            <span class="ai-sessions__title">会话</span>
            <button class="ai-sessions__new" type="button" @click="newSession">＋ 新建</button>
          </div>

          <p class="ai-sessions__local">{{ sessionsMeta }}</p>

          <ul class="ai-sessions__list">
            <li v-for="session in listedSessions" :key="session.id" class="ai-session-row">
              <button
                class="ai-session"
                :class="{ 'is-active': session.id === activeSessionId }"
                type="button"
                @click="selectSession(session)"
              >
                <span class="ai-session__title">{{ session.title }}</span>
                <span v-if="session.conversationId === null" class="ai-session__draft">草稿</span>
                <span class="ai-session__mode">
                  {{
                    session.workflow === WORKFLOW.QA
                      ? '问答'
                      : session.workflow === WORKFLOW.LEARNING_PATH
                        ? '路线'
                        : '文本'
                  }}
                </span>
              </button>
              <button
                class="ai-session__delete"
                type="button"
                :aria-label="`删除会话 ${session.title}`"
                :aria-busy="deletingId === session.id"
                :disabled="deletingId === session.id"
                title="删除会话"
                @click="removeSession(session)"
              >
                ×
              </button>
            </li>
          </ul>

          <button
            v-if="hasMore"
            class="ai-sessions__more"
            type="button"
            :disabled="loadingMore"
            :aria-busy="loadingMore"
            @click="loadMoreSessions"
          >
            {{ loadingMore ? '加载中…' : '加载更多' }}
          </button>

          <p class="ai-sessions__hint">登录同一账号可在其他设备继续对话。</p>
        </div>

        <p class="ai-side__foot">WISDOMHUB · KNOWLEDGE IS A NETWORK</p>
      </aside>

      <main class="ai-main">
        <header class="ai-main__head">
          <div>
            <p class="ai-eyebrow">{{ workbenchEyebrow }}</p>
            <h2>{{ workflowLabel }}</h2>
          </div>
          <div class="ai-main__head-right">
            <p class="ai-main__status">
              <span class="ai-dot" aria-hidden="true"></span>
              {{ statusLabel }}
            </p>
            <WhButton
              v-if="isStreaming && workflow === WORKFLOW.LEARNING_PATH"
              size="sm"
              variant="danger"
              @click="stopStreaming"
            >
              停止生成
            </WhButton>
            <button
              v-else-if="workflow === WORKFLOW.LEARNING_PATH && activeMessages.length"
              class="ai-main__reset"
              type="button"
              @click="resetRoute"
            >
              重新规划
            </button>
          </div>
          <p v-if="!online" class="ai-offline" role="status">
            网络已断开，恢复后可重试失败的问答。
          </p>
        </header>

        <div class="ai-messages-wrap">
          <div ref="messagesRef" class="ai-messages" aria-live="polite" @scroll="onMessagesScroll">
            <div v-if="sessionsLoading" class="ai-empty">
              <p class="ai-empty__eyebrow">[ SYNC / CONVERSATIONS ]</p>
              <h3>正在载入会话…</h3>
              <p>从服务端读取历史对话，请稍候。</p>
            </div>

            <div v-else-if="detailLoading" class="ai-empty">
              <p class="ai-empty__eyebrow">[ SYNC / DETAIL ]</p>
              <h3>正在载入对话…</h3>
              <p>历史消息与阶段数据正在同步。</p>
            </div>

            <div v-else-if="!activeMessages.length" class="ai-empty">
              <p class="ai-empty__eyebrow">
                {{
                  workflow === WORKFLOW.QA
                    ? '[ EMPTY / QA ]'
                    : workflow === WORKFLOW.TEXT_QA
                      ? '[ EMPTY / TEXT ]'
                      : '[ EMPTY / PATH ]'
                }}
              </p>

              <template v-if="workflow === WORKFLOW.QA">
                <h3>把问题问清楚，剩下的交给知识网络</h3>
                <p>描述现象、运行环境与已经尝试过的方案；回答里的技术标签可以继续跳转。</p>
                <div class="ai-samples" aria-label="示例问题">
                  <button
                    v-for="sample in QA_SAMPLES"
                    :key="sample"
                    class="ai-sample"
                    type="button"
                    @click="useSample(sample)"
                  >
                    {{ sample }}
                  </button>
                </div>
              </template>

              <template v-else-if="workflow === WORKFLOW.TEXT_QA">
                <h3>就选中的文本提问</h3>
                <p v-if="activeTextContext">
                  选中的文本已在下方带入，可以追问概念、让 AI 解释段落或总结要点。
                </p>
                <p v-else>未读取到选中的文本，请回到文章中重新选择。</p>
              </template>

              <template v-else>
                <h3>先定目标，再排路径</h3>
                <p>
                  目标技术需要从站内标签库选择；已掌握可以多选，也可以自己补充。
                </p>

                <form class="ai-route" @submit.prevent="generateRoute">
                  <div class="ai-route__field">
                    <label class="ai-route__label" for="ai-target">目标技术</label>
                    <div ref="targetPickerRef" class="ai-combo">
                      <div class="ai-combo__control">
                        <input
                          id="ai-target"
                          v-model="targetTechnology"
                          class="ai-route__input"
                          type="text"
                          placeholder="搜索标签库，如 Redis"
                          autocomplete="off"
                          role="combobox"
                          aria-autocomplete="list"
                          aria-controls="ai-target-options"
                          :aria-expanded="targetOpen"
                          @focus="targetOpen = true"
                          @click="targetOpen = true"
                          @keydown.esc="targetOpen = false"
                          @keydown.tab="targetOpen = false"
                        />
                        <button
                          class="ai-combo__toggle"
                          type="button"
                          :aria-label="targetOpen ? '收起标签库' : '从标签库选择'"
                          :aria-expanded="targetOpen"
                          @click="targetOpen = !targetOpen"
                        >
                          {{ targetOpen ? '收起' : '选择' }}
                        </button>
                      </div>

                      <ul
                        v-if="targetOpen"
                        id="ai-target-options"
                        class="ai-combo__menu"
                        role="listbox"
                        aria-label="目标技术候选"
                      >
                        <li v-for="tag in targetOptions" :key="tag.tagId">
                          <button
                            class="ai-combo__option"
                            type="button"
                            role="option"
                            :aria-selected="tag.name === targetTechnology"
                            @click="chooseTarget(tag.name)"
                          >
                            <span class="ai-combo__name">{{ tag.name }}</span>
                            <span class="ai-combo__category">{{ tag.category }}</span>
                          </button>
                        </li>
                        <li v-if="!targetOptions.length" class="ai-combo__empty">
                          {{
                            dict.tagsLoading
                              ? '标签库加载中，请稍候。'
                              : '没有匹配的标签，请换个关键词。'
                          }}
                        </li>
                      </ul>
                    </div>
                    <p class="ai-route__hint">
                      目标技术需从站内标签库选择；未收录的技术暂不支持生成图谱路线。
                    </p>
                  </div>

                  <div class="ai-route__field">
                    <div class="ai-route__label-row">
                      <span class="ai-route__label">已掌握（可多选）</span>
                      <span class="ai-route__meta">已选 {{ knownTechnologies.length }}</span>
                    </div>

                    <div
                      v-if="knownTechnologies.length"
                      class="ai-known-selected"
                      aria-label="已选技术"
                    >
                      <button
                        v-for="name in knownTechnologies"
                        :key="name"
                        class="ai-known-selected__chip"
                        type="button"
                        :aria-label="`移除 ${name}`"
                        @click="toggleKnown(name)"
                      >
                        {{ name }} <span aria-hidden="true">×</span>
                      </button>
                    </div>

                    <input
                      v-model="knownFilter"
                      class="ai-route__input"
                      type="search"
                      placeholder="搜索标签库：Java、Redis、数据库…"
                      autocomplete="off"
                    />

                    <div class="ai-known-list" role="group" aria-label="标签库">
                      <section
                        v-for="group in knownGroups"
                        :key="group.category"
                        class="ai-known-group"
                      >
                        <p class="ai-known-group__title">{{ group.category }}</p>
                        <div class="ai-route__chips">
                          <button
                            v-for="tag in group.tags"
                            :key="tag.tagId"
                            class="ai-known-chip"
                            :class="{ 'is-selected': knownTechnologies.includes(tag.name) }"
                            type="button"
                            :aria-pressed="knownTechnologies.includes(tag.name)"
                            @click="toggleKnown(tag.name)"
                          >
                            {{ tag.name }}
                          </button>
                        </div>
                      </section>
                      <p v-if="!filteredKnown.length" class="ai-route__hint">
                        {{
                          dict.tagsLoading
                            ? '标签库加载中，也可以在下方直接添加。'
                            : '没有匹配的标签，可以在下方直接添加。'
                        }}
                      </p>
                    </div>

                    <div class="ai-known-custom">
                      <input
                        v-model="customKnown"
                        class="ai-route__input"
                        type="text"
                        placeholder="自定义技术名，回车添加"
                        autocomplete="off"
                        @keydown.enter.prevent="addCustomKnown"
                      />
                      <WhButton
                        size="sm"
                        variant="ghost"
                        :disabled="!customKnown.trim()"
                        @click="addCustomKnown"
                      >
                        添加
                      </WhButton>
                    </div>
                    <p class="ai-route__hint">标签库没收录的技术也可以直接添加。</p>
                  </div>

                  <div class="ai-route__foot">
                    <span class="ai-route__hint">
                      {{
                        knownTechnologies.length
                          ? `已选 ${knownTechnologies.length} 项已掌握技术`
                          : '未选择已掌握技术（可以跳过）'
                      }}
                    </span>
                    <WhButton type="submit" :disabled="!canGenerateRoute"> 生成学习路线 </WhButton>
                  </div>
                </form>
              </template>
            </div>

            <div v-else class="ai-thread">
              <article
                v-for="message in activeMessages"
                :key="message.id"
                class="ai-message"
                :class="`is-${message.role}`"
              >
                <div class="ai-message__meta">
                  <span class="ai-message__role">{{
                    message.role === 'user' ? '你' : 'AI 导师'
                  }}</span>
                  <span v-if="message.status === 'streaming'" class="ai-message__state"
                    >生成中…</span
                  >
                  <span v-else-if="message.status === 'stopped'" class="ai-message__state">
                    已停止
                  </span>
                  <span v-else-if="message.status === 'error'" class="ai-message__state is-error">
                    生成失败
                  </span>
                </div>

                <div class="ai-message__bubble">
                  <p v-if="message.role === 'user'" class="ai-message__user-text">
                    {{ message.content }}
                  </p>
                  <template v-else>
                    <ol v-if="message.steps.length" class="ai-steps" aria-label="执行步骤">
                      <li
                        v-for="step in message.steps"
                        :key="step.id"
                        class="ai-step"
                        :class="`is-${step.status}`"
                      >
                        <div class="ai-step__head">
                          <span class="ai-step__label">{{ step.label }}</span>
                          <span class="ai-step__status">{{ STEP_STATUS_LABELS[step.status] }}</span>
                        </div>
                        <p v-if="step.summary" class="ai-step__summary">{{ step.summary }}</p>
                      </li>
                    </ol>
                    <p
                      v-if="message.steps.some((step) => step.status === 'error')"
                      class="ai-steps__error"
                      role="alert"
                    >
                      部分步骤执行失败，回答可能不完整。
                    </p>
                    <div v-if="message.status === 'streaming'" class="ai-message__stream">
                      {{ message.content }}<span class="ai-cursor" aria-hidden="true"></span>
                    </div>
                    <div
                      v-else
                      class="ai-markdown"
                      v-html="message.html ?? ''"
                      @click="onMarkdownClick"
                    ></div>

                    <p v-if="message.error" class="ai-message__error" role="alert">
                      {{ message.error }}
                    </p>

                    <div v-if="message.status !== 'streaming'" class="ai-message__actions">
                      <button
                        v-if="
                          message.request &&
                          (message.status === 'error' || message.status === 'stopped')
                        "
                        class="ai-message__retry"
                        type="button"
                        :disabled="isStreaming"
                        @click="retryMessage(message)"
                      >
                        {{ message.status === 'stopped' ? '重新生成' : '重试' }}
                      </button>
                      <button class="ai-message__copy" type="button" @click="copyMessage(message)">
                        复制
                      </button>
                    </div>
                  </template>
                </div>
              </article>
            </div>
          </div>

          <button v-if="!atBottom" class="ai-scroll-bottom" type="button" @click="scrollToBottom">
            回到底部 ↓
          </button>
        </div>

        <form
          v-if="workflow !== WORKFLOW.LEARNING_PATH"
          class="ai-composer"
          @submit.prevent="submit"
        >
          <div v-if="workflow === WORKFLOW.TEXT_QA" class="ai-text-context">
            <div class="ai-text-context__head">
              <span class="ai-text-context__label">[ TEXT ] 选中文本</span>
              <span class="ai-text-context__count">
                {{ activeTextContext.length }} / {{ AI_TEXT_MAX_LENGTH }} 字
              </span>
              <span
                v-if="activeTextContext.length >= AI_TEXT_MAX_LENGTH"
                class="ai-text-context__warn"
              >
                仅保留前 {{ AI_TEXT_MAX_LENGTH }} 字
              </span>
            </div>
            <p v-if="activeTextContext" class="ai-text-context__body">
              {{ activeTextContext }}
            </p>
            <p v-else class="ai-text-context__body is-empty">
              未读取到选中的文本，请回到文章中重新选择。
            </p>
          </div>
          <label class="ai-composer__label" for="ai-input">
            {{ workflow === WORKFLOW.TEXT_QA ? '针对选中文本提问' : '输入你的问题' }}
          </label>
          <textarea
            id="ai-input"
            ref="inputRef"
            v-model="draft"
            class="ai-composer__input"
            :placeholder="
              workflow === WORKFLOW.TEXT_QA
                ? '这段文本里的概念、结论或写法，想问什么？'
                : '描述你的问题、运行环境和已经尝试过的方案…'
            "
            rows="3"
            @keydown.enter.exact.prevent="submit"
          ></textarea>
          <div class="ai-composer__foot">
            <span class="ai-composer__hint">{{ composerHint }}</span>
            <WhButton v-if="isStreaming" variant="danger" @click="stopStreaming">
              停止生成
            </WhButton>
            <WhButton v-else type="submit" :disabled="!canSend">发送</WhButton>
          </div>
        </form>
      </main>

      <aside
        v-if="workflow === WORKFLOW.LEARNING_PATH"
        class="ai-side ai-side--refs"
        aria-label="路线阶段"
      >
        <header class="ai-refs__head">
          <p class="ai-eyebrow">[ PLAN ]</p>
          <h2>路线阶段</h2>
          <p v-if="routeTarget" class="ai-refs__query">ROUTE / {{ routeTarget }}</p>
        </header>

        <div v-if="!stageReferences.length" class="ai-refs__empty">
          <p v-if="routeMissing" class="ai-refs__missing">本次未返回路线阶段，请重新生成路线。</p>
          <p v-else-if="routeTarget">路线生成后，这里会按阶段列出先修技术。</p>
          <p v-else>填写目标技术后，这里会按阶段列出先修技术。</p>
          <RouterLink class="ai-refs__link" :to="{ name: 'article-list' }">
            先去逛逛文章 →
          </RouterLink>
        </div>

        <div v-else class="ai-stages">
          <section v-for="stage in stageReferences" :key="stage.id" class="ai-stage-ref">
            <div class="ai-stage-ref__title">
              <span class="ai-stage-ref__index">
                STAGE {{ String(stage.depth).padStart(2, '0') }}
              </span>
              <span>阶段 {{ stage.depth }}</span>
            </div>

            <div v-if="stage.tags.length" class="ai-refs__tags">
              <RouterLink
                v-for="tag in stage.tags"
                :key="tag.tagId"
                :to="{ name: 'tech-detail', params: { tagId: tag.tagId } }"
              >
                {{ tag.name }}
              </RouterLink>
            </div>
          </section>
        </div>
      </aside>
    </div>
  </section>
</template>

<style scoped>
.ai-page {
  width: 100%;
  max-width: var(--max);
  margin-inline: auto;
  padding: clamp(16px, 2.5vw, 28px) var(--pad);
}

.ai-shell {
  display: grid;
  grid-template-columns: 280px minmax(0, 1fr) 320px;
  height: min(860px, calc(100dvh - var(--header-h) - 56px));
  min-height: 560px;
  border: 1px solid var(--ink);
  background: var(--paper);
  box-shadow: var(--shadow-hard);
}
.ai-shell--solo {
  grid-template-columns: 280px minmax(0, 1fr);
}

.ai-side {
  display: flex;
  flex-direction: column;
  gap: var(--space-5);
  min-height: 0;
  padding: var(--space-5);
  overflow-y: auto;
}

.ai-side--sessions {
  background: var(--ink);
  color: var(--bone);
}

.ai-eyebrow {
  color: var(--verm-deep);
  font-family: var(--mono);
  font-size: var(--text-2xs);
  letter-spacing: var(--tracking-wider);
}

.ai-side--sessions .ai-eyebrow {
  color: var(--acid);
}

.ai-brand {
  position: relative;
  display: grid;
  gap: var(--space-2);
}

.ai-brand h1 {
  max-width: 12ch;
  color: var(--bone);
  font-family: var(--serif);
  font-size: var(--text-2xl);
  font-weight: var(--weight-black);
  line-height: var(--leading-tight);
  letter-spacing: var(--tracking-tight);
}

.ai-brand__tag {
  position: absolute;
  top: 0;
  right: 0;
  padding: 2px 6px;
  border: 1px solid var(--acid);
  color: var(--acid);
  font-family: var(--mono);
  font-size: 9px;
  letter-spacing: 0.1em;
}

.ai-modes {
  display: grid;
  gap: var(--space-2);
}

.ai-mode {
  display: grid;
  gap: 2px;
  padding: var(--space-3);
  border: 1px solid var(--line-inverse);
  background: transparent;
  color: var(--bone);
  text-align: left;
  transition:
    background var(--dur) var(--ease),
    border-color var(--dur) var(--ease),
    color var(--dur) var(--ease);
}

.ai-mode:hover {
  border-color: var(--acid);
}

.ai-mode.is-active {
  border-color: var(--acid);
  background: var(--acid);
  color: var(--ink);
}

.ai-mode__label {
  font-family: var(--sans);
  font-size: var(--text-sm);
  font-weight: var(--weight-semibold);
}

.ai-mode__desc {
  color: rgb(242 237 223 / 62%);
  font-family: var(--mono);
  font-size: 10px;
  letter-spacing: 0.02em;
}

.ai-mode.is-active .ai-mode__desc {
  color: rgb(11 11 14 / 65%);
}

.ai-sessions {
  display: grid;
  gap: var(--space-2);
  min-height: 0;
}

.ai-sessions__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-2);
  padding-bottom: var(--space-2);
  border-bottom: 1px solid var(--line-inverse);
}

.ai-sessions__title {
  color: rgb(242 237 223 / 70%);
  font-family: var(--mono);
  font-size: var(--text-2xs);
  letter-spacing: var(--tracking-wider);
}

.ai-sessions__new {
  color: var(--acid);
  font-family: var(--mono);
  font-size: var(--text-2xs);
  letter-spacing: var(--tracking-wide);
}

.ai-sessions__new:hover {
  color: var(--bone);
}

.ai-sessions__local {
  color: rgb(242 237 223 / 45%);
  font-family: var(--mono);
  font-size: 10px;
  letter-spacing: 0.04em;
}

.ai-sessions__list {
  display: grid;
  gap: 2px;
  margin: 0;
  padding: 0;
  list-style: none;
}

.ai-sessions__more {
  justify-self: center;
  padding: var(--space-1) var(--space-3);
  border: 1px solid var(--line-inverse);
  color: rgb(242 237 223 / 65%);
  font-family: var(--mono);
  font-size: var(--text-2xs);
  letter-spacing: var(--tracking-wide);
  transition:
    color var(--dur) var(--ease),
    border-color var(--dur) var(--ease);
}

.ai-sessions__more:hover:not(:disabled) {
  border-color: rgb(242 237 223 / 35%);
  color: var(--bone);
}

.ai-sessions__more:disabled {
  color: rgb(242 237 223 / 35%);
}

.ai-session-row {
  display: flex;
  align-items: stretch;
  gap: 2px;
}

.ai-session {
  display: flex;
  flex: 1;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-2);
  width: auto;
  min-width: 0;
  padding: var(--space-2) var(--space-3);
  border-left: 2px solid transparent;
  color: rgb(242 237 223 / 72%);
  font-size: var(--text-sm);
  text-align: left;
  transition:
    background var(--dur) var(--ease),
    color var(--dur) var(--ease);
}

.ai-session__delete {
  display: grid;
  place-items: center;
  width: 24px;
  color: rgb(242 237 223 / 38%);
  font-family: var(--mono);
  font-size: 14px;
  line-height: 1;
  opacity: 0;
  transition:
    opacity var(--dur) var(--ease),
    color var(--dur) var(--ease);
}

.ai-session-row:hover .ai-session__delete:not(:disabled),
.ai-session__delete:focus-visible {
  opacity: 1;
}

.ai-session__delete:disabled {
  opacity: 0.4;
  cursor: not-allowed;
}

.ai-session__delete:hover {
  color: var(--verm);
}

.ai-session:hover {
  background: rgb(242 237 223 / 6%);
  color: var(--bone);
}

.ai-session.is-active {
  border-left-color: var(--acid);
  background: rgb(201 255 46 / 10%);
  color: var(--bone);
}

.ai-session__title {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.ai-session__draft {
  flex: none;
  margin-left: auto;
  padding: 1px 5px;
  border: 1px solid rgb(242 237 223 / 22%);
  color: rgb(242 237 223 / 60%);
  font-family: var(--mono);
  font-size: 9px;
  letter-spacing: 0.08em;
  line-height: 1.4;
}

.ai-session.is-active .ai-session__draft {
  border-color: rgb(201 255 46 / 45%);
  color: var(--acid);
}

.ai-session__mode {
  color: rgb(242 237 223 / 48%);
  font-family: var(--mono);
  font-size: 9px;
  letter-spacing: 0.1em;
}

.ai-sessions__hint {
  margin-top: var(--space-2);
  color: rgb(242 237 223 / 45%);
  font-family: var(--mono);
  font-size: 10px;
  line-height: var(--leading-relaxed);
}

.ai-side__foot {
  margin-top: auto;
  color: rgb(242 237 223 / 38%);
  font-family: var(--mono);
  font-size: 9px;
  letter-spacing: var(--tracking-wider);
}

.ai-main {
  display: grid;
  grid-template-rows: auto minmax(0, 1fr) auto;
  min-width: 0;
  min-height: 0;
  border-inline: 1px solid var(--ink);
  background: var(--surface);
}

.ai-main__head {
  display: flex;
  flex-wrap: wrap;
  align-items: flex-end;
  justify-content: space-between;
  gap: var(--space-3);
  padding: var(--space-4) var(--space-5);
  border-bottom: 1px solid var(--line);
}

.ai-main__head h2 {
  margin-top: var(--space-1);
  font-family: var(--serif);
  font-size: var(--text-xl);
  font-weight: var(--weight-black);
  letter-spacing: var(--tracking-tight);
}

.ai-main__head-right {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: var(--space-3);
}

.ai-main__status {
  display: flex;
  align-items: center;
  gap: 6px;
  color: var(--text-muted);
  font-family: var(--mono);
  font-size: 10px;
  letter-spacing: 0.08em;
  white-space: nowrap;
}

.ai-main__reset {
  padding-bottom: 2px;
  border-bottom: 1px solid var(--ink);
  color: var(--ink);
  font-family: var(--mono);
  font-size: 10px;
  letter-spacing: 0.06em;
}

.ai-main__reset:hover {
  border-color: var(--verm-deep);
  color: var(--verm-deep);
}

.ai-dot {
  width: 6px;
  height: 6px;
  border: 1px solid var(--ink);
  background: var(--acid);
}

.ai-messages-wrap {
  position: relative;
  min-height: 0;
}

.ai-messages {
  height: 100%;
  padding: clamp(20px, 4vw, 40px) clamp(18px, 4vw, 40px);
  overflow-y: auto;
}

.ai-empty {
  display: grid;
  gap: var(--space-3);
  max-width: 560px;
  margin: 0 auto;
}

.ai-empty__eyebrow {
  color: var(--verm-deep);
  font-family: var(--mono);
  font-size: 10px;
  letter-spacing: var(--tracking-wider);
}

.ai-empty h3 {
  font-family: var(--serif);
  font-size: clamp(1.5rem, 3vw, 2.25rem);
  font-weight: var(--weight-black);
  line-height: var(--leading-snug);
  letter-spacing: var(--tracking-tight);
}

.ai-empty p {
  max-width: 52ch;
  color: var(--text-muted);
  font-size: var(--text-sm);
  line-height: var(--leading-relaxed);
}

.ai-samples {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-2);
  margin-top: var(--space-2);
}

.ai-sample {
  padding: 8px 12px;
  border: 1px solid var(--line-strong);
  background: var(--bone);
  color: var(--ink);
  font-size: var(--text-xs);
  text-align: left;
  transition:
    background var(--dur) var(--ease),
    border-color var(--dur) var(--ease);
}

.ai-sample:hover {
  border-color: var(--ink);
  background: var(--acid);
}

.ai-route {
  display: grid;
  gap: var(--space-3);
  width: 100%;
  max-width: 540px;
  margin-top: var(--space-2);
  padding: var(--space-4);
  border: 1px solid var(--line-strong);
  background: var(--bone);
}

.ai-route__field {
  display: grid;
  gap: var(--space-2);
}

.ai-route__label {
  color: var(--text-muted);
  font-family: var(--mono);
  font-size: 10px;
  letter-spacing: var(--tracking-wide);
  text-transform: uppercase;
}

.ai-route__label-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-2);
}

.ai-route__meta {
  color: var(--text-muted);
  font-family: var(--mono);
  font-size: 10px;
}

.ai-combo {
  position: relative;
  display: grid;
  gap: 6px;
}

.ai-combo__control {
  display: flex;
  gap: 6px;
}

.ai-combo__control .ai-route__input {
  flex: 1;
  min-width: 0;
}

.ai-combo__toggle {
  padding-inline: 10px;
  border: 1px solid var(--line-strong);
  background: var(--bone);
  color: var(--text-muted);
  font-family: var(--mono);
  font-size: 10px;
  letter-spacing: 0.04em;
  white-space: nowrap;
  transition:
    border-color var(--dur) var(--ease),
    color var(--dur) var(--ease);
}

.ai-combo__toggle:hover {
  border-color: var(--ink);
  color: var(--ink);
}

.ai-combo__menu {
  position: absolute;
  z-index: 30;
  top: calc(100% + 4px);
  right: 0;
  left: 0;
  max-height: 240px;
  padding: 4px 0;
  overflow-y: auto;
  border: 1px solid var(--ink);
  background: var(--surface-strong);
  box-shadow: var(--shadow-hard-sm);
  list-style: none;
}

.ai-combo__option {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: var(--space-3);
  width: 100%;
  padding: 8px 12px;
  text-align: left;
}

.ai-combo__option:hover {
  background: var(--acid);
}

.ai-combo__name {
  font-size: var(--text-sm);
  font-weight: var(--weight-medium);
}

.ai-combo__category {
  color: var(--text-muted);
  font-family: var(--mono);
  font-size: 9px;
  letter-spacing: 0.04em;
}

.ai-combo__empty {
  padding: 10px 12px;
  color: var(--text-muted);
  font-size: var(--text-xs);
}

.ai-known-selected {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.ai-known-selected__chip {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  padding: 3px 8px;
  border: 1px solid var(--ink);
  background: var(--acid);
  color: var(--ink);
  font-family: var(--mono);
  font-size: 10px;
}

.ai-known-selected__chip span {
  color: var(--verm-deep);
  font-size: 12px;
  line-height: 1;
}

.ai-known-selected__chip:hover {
  background: var(--verm);
  color: var(--bone);
}

.ai-known-selected__chip:hover span {
  color: var(--bone);
}

.ai-known-list {
  display: grid;
  gap: var(--space-3);
  max-height: 240px;
  padding: var(--space-2);
  overflow-y: auto;
  border: 1px solid var(--line-strong);
  background: var(--surface-strong);
}

.ai-known-group {
  display: grid;
  gap: 6px;
}

.ai-known-group__title {
  color: var(--text-muted);
  font-family: var(--mono);
  font-size: 9px;
  letter-spacing: 0.08em;
  text-transform: uppercase;
}

.ai-known-custom {
  display: flex;
  gap: var(--space-2);
}

.ai-known-custom .ai-route__input {
  flex: 1;
  min-width: 0;
}

.ai-route__input {
  width: 100%;
  padding: 9px 12px;
  border: 1px solid var(--line-strong);
  background: var(--surface-strong);
  color: var(--ink);
  font: inherit;
  font-size: var(--text-sm);
}

.ai-route__input:focus {
  border-color: var(--ink);
  outline: 2px solid var(--focus);
  outline-offset: 1px;
}

.ai-route__chips {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-2);
}

.ai-target-chip,
.ai-known-chip {
  padding: 3px 8px;
  border: 1px solid var(--line-strong);
  color: var(--text-muted);
  font-family: var(--mono);
  font-size: 10px;
  transition:
    background var(--dur) var(--ease),
    border-color var(--dur) var(--ease),
    color var(--dur) var(--ease);
}

.ai-target-chip:hover {
  border-color: var(--ink);
  color: var(--ink);
}

.ai-known-chip:hover {
  border-color: var(--ink);
  color: var(--ink);
}

.ai-known-chip.is-selected {
  border-color: var(--ink);
  background: var(--acid);
  color: var(--ink);
}

.ai-route__hint {
  color: var(--text-muted);
  font-family: var(--mono);
  font-size: 10px;
  line-height: var(--leading-relaxed);
}

.ai-route__foot {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-3);
  padding-top: var(--space-2);
  border-top: 1px solid var(--line);
}

.ai-thread {
  display: grid;
  gap: var(--space-5);
  max-width: 760px;
  margin: 0 auto;
}

.ai-message {
  display: grid;
  gap: var(--space-2);
}

.ai-message.is-user {
  justify-items: end;
}

.ai-message__meta {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  color: var(--text-muted);
  font-family: var(--mono);
  font-size: 10px;
  letter-spacing: 0.08em;
}

.ai-message.is-user .ai-message__meta {
  justify-content: flex-end;
}

.ai-message__role {
  color: var(--ink);
}

.ai-message__state {
  color: var(--verm-deep);
}

.ai-message__state.is-error {
  color: var(--verm);
}

.ai-message__bubble {
  width: fit-content;
  max-width: min(640px, 94%);
  padding: var(--space-3) var(--space-4);
  border: 1px solid var(--line-strong);
  background: var(--bone);
}

.ai-message.is-user .ai-message__bubble {
  max-width: min(540px, 90%);
  border-color: var(--ink);
  background: var(--acid);
  color: var(--ink);
}

.ai-message__user-text {
  white-space: pre-wrap;
  font-size: var(--text-sm);
  line-height: var(--leading-relaxed);
  overflow-wrap: anywhere;
}

.ai-message__stream {
  white-space: pre-wrap;
  font-size: var(--text-sm);
  line-height: var(--leading-relaxed);
  overflow-wrap: anywhere;
}

.ai-steps {
  display: grid;
  gap: var(--space-2);
  margin: 0 0 var(--space-3);
  padding: 0 0 0 var(--space-3);
  border-left: 1px solid var(--line-strong);
  list-style: none;
}

.ai-step {
  position: relative;
  display: grid;
  gap: 2px;
}

.ai-step::before {
  position: absolute;
  top: 5px;
  left: calc(-1 * var(--space-3) - 3px);
  width: 5px;
  height: 5px;
  border-radius: 50%;
  background: var(--line-strong);
  content: '';
}

.ai-step.is-running::before {
  background: var(--verm);
  animation: ai-blink 1s steps(1) infinite;
}

.ai-step.is-done::before {
  background: var(--ink);
}

.ai-step.is-error::before {
  background: var(--verm);
}

.ai-steps__error {
  margin: 0 0 var(--space-3);
  padding: var(--space-2) var(--space-3);
  border: 1px solid var(--verm);
  background: rgb(255 59 31 / 8%);
  color: var(--verm-deep);
  font-size: var(--text-xs);
}

.ai-step__head {
  display: flex;
  flex-wrap: wrap;
  align-items: baseline;
  justify-content: space-between;
  gap: var(--space-2);
}

.ai-step__label {
  color: var(--ink);
  font-size: var(--text-xs);
}

.ai-step__status {
  color: var(--text-muted);
  font-family: var(--mono);
  font-size: 9px;
  letter-spacing: 0.08em;
}

.ai-step.is-running .ai-step__status,
.ai-step.is-error .ai-step__status {
  color: var(--verm-deep);
}

.ai-step__summary {
  color: var(--text-muted);
  font-family: var(--mono);
  font-size: 10px;
  line-height: var(--leading-relaxed);
  overflow-wrap: anywhere;
}

.ai-cursor {
  display: inline-block;
  width: 6px;
  height: 1em;
  margin-left: 2px;
  background: var(--verm);
  vertical-align: text-bottom;
  animation: ai-blink 1s steps(1) infinite;
}

@keyframes ai-blink {
  50% {
    opacity: 0;
  }
}

.ai-message__error {
  margin-top: var(--space-2);
  padding: var(--space-2) var(--space-3);
  border: 1px solid var(--verm);
  background: rgb(255 59 31 / 8%);
  color: var(--verm-deep);
  font-size: var(--text-sm);
}

.ai-message__actions {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: var(--space-3);
  margin-top: var(--space-2);
}

.ai-message__copy,
.ai-message__retry {
  padding-bottom: 1px;
  border-bottom: 1px solid var(--line-strong);
  color: var(--text-muted);
  font-family: var(--mono);
  font-size: 10px;
  letter-spacing: 0.08em;
}

.ai-message__retry {
  border-color: var(--verm-deep);
  color: var(--verm-deep);
}

.ai-message__copy:hover {
  border-color: var(--verm-deep);
  color: var(--verm-deep);
}

.ai-message__retry:hover:not(:disabled) {
  background: var(--verm);
  color: var(--text-inverse);
}

.ai-message__retry:disabled {
  opacity: 0.45;
  cursor: not-allowed;
}

.ai-offline {
  flex-basis: 100%;
  padding: var(--space-2) var(--space-3);
  border: 1px solid var(--verm);
  background: rgb(255 59 31 / 8%);
  color: var(--verm-deep);
  font-family: var(--mono);
  font-size: 10px;
  line-height: var(--leading-relaxed);
}

.ai-markdown {
  font-size: var(--text-sm);
  line-height: var(--leading-reading);
  overflow-wrap: anywhere;
}

.ai-markdown :deep(p) {
  margin: 0 0 var(--space-3);
}

.ai-markdown :deep(p:last-child) {
  margin-bottom: 0;
}

.ai-markdown :deep(h1),
.ai-markdown :deep(h2),
.ai-markdown :deep(h3) {
  margin: var(--space-4) 0 var(--space-2);
  font-family: var(--serif);
  font-weight: var(--weight-black);
  line-height: var(--leading-snug);
  letter-spacing: var(--tracking-tight);
}

.ai-markdown :deep(h2) {
  font-size: var(--text-xl);
}

.ai-markdown :deep(h3) {
  font-size: var(--text-lg);
}

.ai-markdown :deep(ul),
.ai-markdown :deep(ol) {
  margin: 0 0 var(--space-3);
  padding-left: 1.4em;
}

.ai-markdown :deep(li) {
  margin-bottom: var(--space-1);
}

.ai-markdown :deep(a) {
  color: var(--link);
  text-decoration: underline;
  text-underline-offset: 3px;
}

.ai-markdown :deep(code) {
  padding: 1px 4px;
  background: var(--bone-2);
  font-family: var(--mono);
  font-size: 0.92em;
}

.ai-markdown :deep(pre) {
  margin: 0 0 var(--space-3);
  padding: var(--space-3);
  overflow-x: auto;
  border: 1px solid var(--ink);
  background: var(--ink);
}

.ai-markdown :deep(pre code) {
  padding: 0;
  background: transparent;
  color: var(--bone);
}

.ai-markdown :deep(blockquote) {
  margin: 0 0 var(--space-3);
  padding-left: var(--space-3);
  border-left: 2px solid var(--ink);
  color: var(--text-muted);
}

.ai-markdown :deep(table) {
  width: 100%;
  margin-bottom: var(--space-3);
  border-collapse: collapse;
}

.ai-markdown :deep(th),
.ai-markdown :deep(td) {
  padding: var(--space-1) var(--space-2);
  border: 1px solid var(--line-strong);
  text-align: left;
}

.ai-scroll-bottom {
  position: absolute;
  right: clamp(14px, 3vw, 32px);
  bottom: 16px;
  padding: 6px 10px;
  border: 1px solid var(--ink);
  background: var(--acid);
  color: var(--ink);
  font-family: var(--mono);
  font-size: 10px;
  letter-spacing: 0.06em;
  box-shadow: var(--shadow-hard-sm);
}

.ai-scroll-bottom:hover {
  background: var(--ink);
  color: var(--acid);
}

.ai-composer {
  display: grid;
  gap: var(--space-2);
  padding: var(--space-3) var(--space-5) var(--space-4);
  border-top: 1px solid var(--ink);
  background: var(--paper);
}

.ai-text-context {
  display: grid;
  gap: var(--space-2);
  padding: var(--space-3);
  border: 1px solid var(--line-strong);
  border-left: 3px solid var(--ink);
  background: var(--bone);
}

.ai-text-context__head {
  display: flex;
  flex-wrap: wrap;
  align-items: baseline;
  justify-content: space-between;
  gap: var(--space-2);
  color: var(--text-muted);
  font-family: var(--mono);
  font-size: 10px;
  letter-spacing: 0.06em;
}

.ai-text-context__label {
  color: var(--ink);
}

.ai-text-context__warn {
  color: var(--verm-deep);
}

.ai-text-context__body {
  max-height: 120px;
  margin: 0;
  overflow: auto;
  color: var(--ink);
  font-size: var(--text-sm);
  line-height: var(--leading-relaxed);
  white-space: pre-wrap;
  overflow-wrap: anywhere;
}

.ai-text-context__body.is-empty {
  color: var(--verm-deep);
}

.ai-composer__label {
  color: var(--text-muted);
  font-family: var(--mono);
  font-size: 10px;
  letter-spacing: var(--tracking-wide);
  text-transform: uppercase;
}

.ai-composer__input {
  width: 100%;
  min-height: 84px;
  max-height: 200px;
  padding: var(--space-3);
  resize: none;
  border: 1px solid var(--line-strong);
  background: var(--surface-strong);
  color: var(--ink);
  font: inherit;
  font-size: var(--text-sm);
  line-height: var(--leading-relaxed);
}

.ai-composer__input:focus {
  border-color: var(--ink);
  outline: 2px solid var(--focus);
  outline-offset: 1px;
}

.ai-composer__foot {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-2);
}

.ai-composer__hint {
  color: var(--text-muted);
  font-family: var(--mono);
  font-size: 10px;
}

.ai-side--refs {
  background: var(--bone);
}

.ai-refs__head {
  display: grid;
  gap: var(--space-2);
  padding-bottom: var(--space-3);
  border-bottom: 1px solid var(--ink);
}

.ai-refs__head h2 {
  font-family: var(--serif);
  font-size: var(--text-xl);
  font-weight: var(--weight-black);
  letter-spacing: var(--tracking-tight);
}

.ai-refs__query {
  color: var(--text-muted);
  font-family: var(--mono);
  font-size: 10px;
  line-height: var(--leading-relaxed);
  overflow-wrap: anywhere;
}

.ai-refs__tags {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-2);
}

.ai-refs__tags a {
  padding: 1px 6px;
  border: 1px solid var(--line-strong);
  color: var(--text-muted);
  font-family: var(--mono);
  font-size: 9px;
}

.ai-refs__tags a:hover {
  border-color: var(--ink);
  color: var(--ink);
}

.ai-stages {
  display: grid;
  gap: var(--space-4);
}

.ai-stage-ref {
  display: grid;
  gap: var(--space-2);
  padding-bottom: var(--space-3);
  border-bottom: 1px solid var(--line);
}

.ai-stage-ref__title {
  display: flex;
  flex-direction: column;
  gap: 2px;
  color: var(--ink);
  text-align: left;
}

.ai-stage-ref__index {
  color: var(--verm-deep);
  font-family: var(--mono);
  font-size: 9px;
  letter-spacing: var(--tracking-wide);
}

.ai-stage-ref__title > span:last-child {
  font-family: var(--serif);
  font-size: var(--text-md);
  font-weight: var(--weight-semibold);
  line-height: var(--leading-snug);
}

.ai-refs__empty {
  display: grid;
  gap: var(--space-3);
  color: var(--text-muted);
  font-size: var(--text-sm);
  line-height: var(--leading-relaxed);
}

.ai-refs__missing {
  color: var(--verm-deep);
}

.ai-refs__link {
  justify-self: start;
  padding-bottom: 2px;
  border-bottom: 1px solid var(--ink);
  color: var(--ink);
  font-family: var(--mono);
  font-size: var(--text-2xs);
}

.ai-refs__link:hover {
  color: var(--verm-deep);
  border-color: var(--verm-deep);
}

@media (max-width: 1279px) {
  .ai-shell {
    grid-template-columns: 240px minmax(0, 1fr) 270px;
  }
  .ai-shell--solo {
    grid-template-columns: 240px minmax(0, 1fr);
  }
}

@media (max-width: 959px) {
  .ai-page {
    padding: var(--space-3) var(--pad) var(--space-6);
  }

  .ai-shell {
    grid-template-columns: minmax(0, 1fr);
    height: auto;
    min-height: calc(100dvh - var(--header-h) - 24px);
    box-shadow: var(--shadow-hard-sm);
  }

  .ai-side--sessions {
    gap: var(--space-4);
    padding: var(--space-4);
  }

  .ai-brand h1 {
    max-width: none;
    font-size: var(--text-xl);
  }

  .ai-modes {
    grid-template-columns: 1fr 1fr;
  }

  .ai-mode {
    padding: var(--space-2) var(--space-3);
  }

  .ai-mode__desc {
    display: none;
  }

  .ai-sessions__list {
    display: flex;
    gap: var(--space-2);
    padding-bottom: 2px;
    overflow-x: auto;
  }

  .ai-session {
    width: auto;
    border: 1px solid var(--line-inverse);
    border-left-width: 2px;
    white-space: nowrap;
  }

  .ai-session__delete {
    opacity: 1;
  }

  .ai-sessions__hint,
  .ai-side__foot {
    display: none;
  }

  .ai-main {
    min-height: 520px;
    border-inline: 0;
    border-top: 1px solid var(--ink);
    border-bottom: 1px solid var(--ink);
  }

  .ai-main__head {
    padding: var(--space-3) var(--space-4);
  }

  .ai-messages {
    height: auto;
    min-height: 320px;
    max-height: 62vh;
    padding: var(--space-5) var(--space-4);
  }

  .ai-composer {
    padding: var(--space-3) var(--space-4) var(--space-4);
  }

  .ai-side--refs {
    padding: var(--space-4);
  }

  .ai-message__bubble {
    max-width: 94%;
  }
}
</style>
