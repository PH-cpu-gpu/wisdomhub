<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { RouterLink, useRouter } from 'vue-router'

import { isApiError } from '@/api/error'
import { chatStream } from '@/api/modules/agent'
import { articles as searchArticles, tags as searchTags } from '@/api/modules/search'
import WhButton from '@/components/base/WhButton.vue'
import { confirm } from '@/components/base/confirm'
import { toast } from '@/components/base/toast'
import { useClickOutside } from '@/composables/useClickOutside'
import { useSeo } from '@/composables/useSeo'
import { useDictStore } from '@/stores'
import type { AgentEvent } from '@/types/agent'
import type { ID } from '@/types/common'
import type { TagVO } from '@/types/content'
import type { SearchTagBriefVO } from '@/types/search'
import { renderMarkdown } from '@/utils/markdown'

type Workflow = 'qa' | 'learning_path'

interface StreamRequest {
  messageId: string
  workflow: Workflow
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
  /** 重试所需：messageId / 工作流 / 原始问题 */
  request?: StreamRequest
}

interface ReferenceArticle {
  articleId: ID
  title: string
  summary: string
  tags: SearchTagBriefVO[]
}

interface RouteStage {
  index: number
  title: string
  id: string
  technologies: string[]
}

interface StageReference extends RouteStage {
  references: ReferenceArticle[]
  loading: boolean
  error: string
  loaded: boolean
  request: number
}

interface LocalSession {
  id: ID
  title: string
  workflow: Workflow
  conversationId: string | null
  messages: ChatMessage[]
  references: ReferenceArticle[]
  referencesQuestion: string
  referencesLoading: boolean
  referencesError: string
  referencesLoaded: boolean
  referenceRequest: number
  stageReferences: StageReference[]
  routeTarget: string
}

type TagNavigation =
  | { name: 'tech-detail'; params: { tagId: ID } }
  | { name: 'search'; query: { type: string; keyword: string } }

interface StoredMessage {
  id: string
  role: 'user' | 'assistant'
  content: string
  status: 'done' | 'error' | 'stopped'
  error?: string
  request?: StreamRequest
}

interface StoredStageReference {
  index: number
  title: string
  id: string
  technologies: string[]
  references: ReferenceArticle[]
  loaded: boolean
}

interface StoredSession {
  id: string
  title: string
  workflow: Workflow
  conversationId: string | null
  messages: StoredMessage[]
  references: ReferenceArticle[]
  referencesQuestion: string
  referencesLoaded: boolean
  stageReferences: StoredStageReference[]
  routeTarget: string
}

interface StoredPayload {
  version: number
  sessions: StoredSession[]
  activeSessionId: string
  targetTechnology: string
  knownTechnologies: string[]
}

const STORAGE_KEY = 'wisdomhub:ai.sessions'
const STORAGE_VERSION = 1
const MAX_SESSIONS = 30

const WORKFLOWS: { value: Workflow; label: string; desc: string }[] = [
  { value: 'qa', label: '技术问答', desc: '带引用的连续追问' },
  { value: 'learning_path', label: '学习路线', desc: '从目标到分阶段计划' },
]

const QA_SAMPLES = [
  'Redis 缓存一致性怎么保证？',
  'Spring 事务为什么会失效？',
  '知识图谱的实体关系怎么建模？',
]

const router = useRouter()
const dict = useDictStore()
const restored = loadStoredState()
const initialSessions: LocalSession[] = restored?.sessions.length
  ? restored.sessions
  : [createSession(createId(), 'qa')]
const sessions = ref<LocalSession[]>(initialSessions)
const initialActiveId =
  restored?.activeSessionId &&
  initialSessions.some((session) => session.id === restored.activeSessionId)
    ? restored.activeSessionId
    : (initialSessions[0]?.id ?? '')
const activeSessionId = ref<ID>(initialActiveId)
const workflow = ref<Workflow>(
  initialSessions.find((session) => session.id === initialActiveId)?.workflow ?? 'qa',
)
const draft = ref('')
const inputRef = ref<HTMLTextAreaElement | null>(null)
const messagesRef = ref<HTMLElement | null>(null)
const atBottom = ref(true)
const streaming = ref<{ sessionId: ID; messageId: string } | null>(null)
const tagRouteCache = new Map<string, TagNavigation>()
const restoredTarget = restored?.targetTechnology ?? ''
const targetTechnology = ref(restoredTarget)
const knownTechnologies = ref<string[]>(
  (restored?.knownTechnologies ?? []).filter(
    (item) => item.toLowerCase() !== restoredTarget.trim().toLowerCase(),
  ),
)
const targetPickerRef = ref<HTMLElement | null>(null)
const targetOpen = ref(false)
const knownFilter = ref('')
const customKnown = ref('')
const online = ref(typeof navigator === 'undefined' ? true : navigator.onLine)

let activeController: AbortController | null = null
let flushTimer: number | null = null
let saveTimer: number | null = null

const activeSession = computed(
  () => sessions.value.find((session) => session.id === activeSessionId.value) ?? null,
)
const activeMessages = computed(() => activeSession.value?.messages ?? [])
const references = computed(() => activeSession.value?.references ?? [])
const referencesQuestion = computed(() => activeSession.value?.referencesQuestion ?? '')
const referencesLoading = computed(() => activeSession.value?.referencesLoading ?? false)
const referencesError = computed(() => activeSession.value?.referencesError ?? '')
const referencesLoaded = computed(() => activeSession.value?.referencesLoaded ?? false)
const stageReferences = computed(() => activeSession.value?.stageReferences ?? [])
const routeTarget = computed(() => activeSession.value?.routeTarget ?? '')

const workflowLabel = computed(() => (workflow.value === 'qa' ? '技术问答' : '学习路线'))
const isStreaming = computed(() => streaming.value !== null)
const canSend = computed(() => draft.value.trim().length > 0 && !isStreaming.value)
const canGenerateRoute = computed(
  () => targetTechnology.value.trim().length > 0 && !isStreaming.value,
)

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
  if (isStreaming.value) return workflow.value === 'qa' ? '正在回答…' : '正在规划路线…'
  return workflow.value === 'qa' ? 'QA 可用' : '学习路线'
})

function createSession(id: ID, nextWorkflow: Workflow): LocalSession {
  return {
    id,
    title: '新的对话',
    workflow: nextWorkflow,
    conversationId: null,
    messages: [],
    references: [],
    referencesQuestion: '',
    referencesLoading: false,
    referencesError: '',
    referencesLoaded: false,
    referenceRequest: 0,
    stageReferences: [],
    routeTarget: '',
  }
}

function createId(): string {
  if (typeof crypto !== 'undefined' && typeof crypto.randomUUID === 'function') {
    return crypto.randomUUID()
  }
  return `id-${Date.now()}-${Math.random().toString(16).slice(2)}`
}

function isReferenceArticle(value: unknown): value is ReferenceArticle {
  if (typeof value !== 'object' || value === null) return false
  const record = value as Partial<ReferenceArticle>
  return typeof record.articleId === 'string' && typeof record.title === 'string'
}

function isStreamRequest(value: unknown): value is StreamRequest {
  if (typeof value !== 'object' || value === null) return false
  const record = value as Partial<StreamRequest>
  return (
    typeof record.messageId === 'string' &&
    (record.workflow === 'qa' || record.workflow === 'learning_path') &&
    typeof record.question === 'string'
  )
}

function hydrateSession(value: unknown): LocalSession | null {
  if (typeof value !== 'object' || value === null) return null
  const record = value as Partial<StoredSession>
  if (typeof record.id !== 'string' || !Array.isArray(record.messages)) return null

  const sessionWorkflow: Workflow = record.workflow === 'learning_path' ? 'learning_path' : 'qa'
  const messages = record.messages.flatMap((item) => {
    if (typeof item !== 'object' || item === null) return []
    const message = item as Partial<StoredMessage>
    if (typeof message.content !== 'string') return []

    const status =
      message.status === 'done' || message.status === 'error' ? message.status : 'stopped'
    const request: StreamRequest | undefined = isStreamRequest(message.request)
      ? {
          messageId: message.request.messageId,
          workflow: message.request.workflow,
          question: message.request.question,
          technology:
            typeof message.request.technology === 'string' ? message.request.technology : undefined,
          knownTechnologies: Array.isArray(message.request.knownTechnologies)
            ? message.request.knownTechnologies.filter(
                (item): item is string => typeof item === 'string',
              )
            : [],
        }
      : undefined

    const restoredMessage: ChatMessage = {
      id: typeof message.id === 'string' ? message.id : createId(),
      role: message.role === 'user' ? 'user' : 'assistant',
      content: message.content,
      status,
      error: typeof message.error === 'string' ? message.error : undefined,
      request,
    }
    restoredMessage.html =
      sessionWorkflow === 'learning_path'
        ? parseRoute(message.content).html
        : renderMarkdown(message.content)
    return [restoredMessage]
  })

  const references = Array.isArray(record.references)
    ? record.references.filter(isReferenceArticle).map((reference) => ({
        articleId: reference.articleId,
        title: reference.title,
        summary: typeof reference.summary === 'string' ? reference.summary : '',
        tags: Array.isArray(reference.tags) ? reference.tags : [],
      }))
    : []

  const stageReferences = Array.isArray(record.stageReferences)
    ? record.stageReferences.flatMap((item) => {
        if (typeof item !== 'object' || item === null) return []
        const stage = item as Partial<StoredStageReference>
        if (typeof stage.title !== 'string' || typeof stage.id !== 'string') return []

        const stageRefs = Array.isArray(stage.references)
          ? stage.references.filter(isReferenceArticle).map((reference) => ({
              articleId: reference.articleId,
              title: reference.title,
              summary: typeof reference.summary === 'string' ? reference.summary : '',
              tags: Array.isArray(reference.tags) ? reference.tags : [],
            }))
          : []

        return [
          reactive<StageReference>({
            index: typeof stage.index === 'number' ? stage.index : 1,
            title: stage.title,
            id: stage.id,
            technologies: Array.isArray(stage.technologies)
              ? stage.technologies.filter(
                  (technology): technology is string => typeof technology === 'string',
                )
              : [],
            references: stageRefs,
            loading: false,
            error: '',
            loaded: Boolean(stage.loaded) || stageRefs.length > 0,
            request: 0,
          }),
        ]
      })
    : []

  return {
    id: record.id,
    title: typeof record.title === 'string' && record.title ? record.title : '新的对话',
    workflow: sessionWorkflow,
    conversationId: typeof record.conversationId === 'string' ? record.conversationId : null,
    messages,
    references,
    referencesQuestion:
      typeof record.referencesQuestion === 'string' ? record.referencesQuestion : '',
    referencesLoading: false,
    referencesError: '',
    referencesLoaded: Boolean(record.referencesLoaded) || references.length > 0,
    referenceRequest: 0,
    stageReferences,
    routeTarget: typeof record.routeTarget === 'string' ? record.routeTarget : '',
  }
}

function loadStoredState(): {
  sessions: LocalSession[]
  activeSessionId: string
  targetTechnology: string
  knownTechnologies: string[]
} | null {
  try {
    const raw = localStorage.getItem(STORAGE_KEY)
    if (!raw) return null

    const payload = JSON.parse(raw) as Partial<StoredPayload>
    if (payload.version !== STORAGE_VERSION || !Array.isArray(payload.sessions)) return null

    const parsed = payload.sessions.slice(0, MAX_SESSIONS).flatMap((item) => {
      const session = hydrateSession(item)
      return session ? [session] : []
    })
    if (!parsed.length) return null

    const active =
      typeof payload.activeSessionId === 'string' &&
      parsed.some((session) => session.id === payload.activeSessionId)
        ? payload.activeSessionId
        : (parsed[0]?.id ?? '')

    return {
      sessions: parsed,
      activeSessionId: active,
      targetTechnology:
        typeof payload.targetTechnology === 'string' ? payload.targetTechnology : '',
      knownTechnologies: Array.isArray(payload.knownTechnologies)
        ? payload.knownTechnologies.filter((item): item is string => typeof item === 'string')
        : [],
    }
  } catch {
    return null
  }
}

function serializeSession(session: LocalSession): StoredSession {
  return {
    id: session.id,
    title: session.title,
    workflow: session.workflow,
    conversationId: session.conversationId,
    messages: session.messages.map((message) => ({
      id: message.id,
      role: message.role,
      content: message.content,
      status: message.status === 'done' || message.status === 'error' ? message.status : 'stopped',
      error: message.error,
      request: message.request,
    })),
    references: session.references,
    referencesQuestion: session.referencesQuestion,
    referencesLoaded: session.referencesLoaded,
    stageReferences: session.stageReferences.map((stage) => ({
      index: stage.index,
      title: stage.title,
      id: stage.id,
      technologies: stage.technologies,
      references: stage.references,
      loaded: stage.loaded,
    })),
    routeTarget: session.routeTarget,
  }
}

function saveState(): void {
  try {
    const payload: StoredPayload = {
      version: STORAGE_VERSION,
      sessions: sessions.value.slice(0, MAX_SESSIONS).map(serializeSession),
      activeSessionId: activeSessionId.value,
      targetTechnology: targetTechnology.value,
      knownTechnologies: [...knownTechnologies.value],
    }
    localStorage.setItem(STORAGE_KEY, JSON.stringify(payload))
  } catch {
    // 隐私模式 / 存储配额不足时静默失败，不影响对话
  }
}

function scheduleSave(): void {
  if (saveTimer !== null) window.clearTimeout(saveTimer)
  saveTimer = window.setTimeout(() => {
    saveTimer = null
    saveState()
  }, 300)
}

function trimSessions(): void {
  while (sessions.value.length > MAX_SESSIONS) {
    const removed = sessions.value.pop()
    if (removed && streaming.value?.sessionId === removed.id) {
      activeController?.abort()
      streaming.value = null
    }
  }
}

async function removeSession(session: LocalSession): Promise<void> {
  const ok = await confirm({
    title: '删除会话',
    message: `删除后本机保存的这段对话会消失，确定删除「${session.title}」？`,
    confirmText: '删除',
    danger: true,
  })
  if (!ok) return

  if (streaming.value?.sessionId === session.id) {
    activeController?.abort()
    streaming.value = null
  }

  const index = sessions.value.findIndex((item) => item.id === session.id)
  if (index < 0) return
  sessions.value.splice(index, 1)

  if (!sessions.value.length) {
    const next = createSession(createId(), workflow.value)
    sessions.value.push(next)
    activeSessionId.value = next.id
    draft.value = ''
  } else if (activeSessionId.value === session.id) {
    const next = sessions.value[Math.min(index, sessions.value.length - 1)] ?? sessions.value[0]
    if (next) {
      activeSessionId.value = next.id
      workflow.value = next.workflow
      atBottom.value = true
      void nextTick(scrollToBottom)
    }
  }

  saveState()
  toast.success('会话已删除')
}

function focusInput(): void {
  void nextTick(() => inputRef.value?.focus())
}

function selectWorkflow(next: Workflow): void {
  if (workflow.value === next) return
  workflow.value = next

  const session = activeSession.value
  if (session) {
    // 已有消息的会话不混用模式，切模式时开一个新会话
    if (session.messages.length > 0 && session.workflow !== next) newSession()
    else session.workflow = next
  }

  if (next === 'learning_path') void dict.loadTags()
  saveState()
}

function selectSession(session: LocalSession): void {
  activeSessionId.value = session.id
  workflow.value = session.workflow
  draft.value = ''
  atBottom.value = true
  void nextTick(scrollToBottom)
  saveState()
}

function newSession(): void {
  const session = createSession(createId(), workflow.value)
  sessions.value.unshift(session)
  activeSessionId.value = session.id
  draft.value = ''
  atBottom.value = true

  const overflowed = sessions.value.length > MAX_SESSIONS
  trimSessions()
  saveState()
  if (overflowed) toast.info('最多保留 30 个会话，最早的会话已移除')
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

watch([targetTechnology, knownTechnologies], scheduleSave, { deep: true })

function submit(): void {
  if (isStreaming.value) return
  const question = draft.value.trim()
  if (!question) return

  const session = activeSession.value
  if (!session) return
  void sendMessage(session, question, 'qa')
}

function generateRoute(): void {
  if (isStreaming.value) return
  const target = targetTechnology.value.trim()
  if (!target) {
    toast.info('请先填写目标技术')
    return
  }

  const session = activeSession.value
  if (!session) return

  const question = `请为我规划 ${target} 的学习路线`
  void sendMessage(session, question, 'learning_path', target, [...knownTechnologies.value])
}

async function sendMessage(
  session: LocalSession,
  question: string,
  mode: Workflow,
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
  const user: ChatMessage = { id: createId(), role: 'user', content: question, status: 'done' }
  // reactive：流式增量要直接触发视图更新，不能只改数组里的原始对象
  const assistant = reactive<ChatMessage>({
    id: createId(),
    role: 'assistant',
    content: '',
    status: 'streaming',
    request,
  })

  session.messages.push(user, assistant)
  if (session.title === '新的对话') {
    session.title = truncate(
      mode === 'learning_path' ? `规划 ${technology ?? question}` : question,
      18,
    )
  }
  session.workflow = mode
  session.routeTarget = mode === 'learning_path' ? (technology ?? '') : ''
  workflow.value = mode
  draft.value = ''
  resetReferences(session, request)
  saveState()

  atBottom.value = true
  void nextTick(scrollToBottom)

  await runStream(session, assistant)
}

function resetReferences(session: LocalSession, request: StreamRequest): void {
  session.references = []
  session.referencesQuestion = request.workflow === 'qa' ? request.question : ''
  session.referencesError = ''
  session.referencesLoading = false
  session.referencesLoaded = false
  session.referenceRequest += 1
  session.stageReferences = []
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
      if (request.workflow === 'learning_path') {
        const route = parseRoute(assistant.content)
        assistant.html = route.html
        if (status === 'done') void loadStageReferences(session, route.stages)
      } else {
        assistant.html = renderMarkdown(assistant.content)
        if (status === 'done') void loadReferences(session, request.question)
      }
    }

    if (activeSessionId.value === session.id && atBottom.value) {
      void nextTick(scrollToBottom)
    }

    saveState()
  }

  const handleEvent = (event: AgentEvent): void => {
    if (event.event === 'meta') {
      session.conversationId = event.data.conversationId
      saveState()
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
      return
    }
    if (event.event === 'error') {
      terminal = true
      finalize('error', event.data.message)
    }
  }

  try {
    await chatStream(
      {
        conversationId: session.conversationId,
        messageId: request.messageId,
        workflow: request.workflow,
        question: request.question,
        ...(request.workflow === 'learning_path'
          ? {
              technology: request.technology ?? null,
              knownTechnologies: request.knownTechnologies ?? [],
            }
          : {}),
      },
      {
        signal: controller.signal,
        onEvent: handleEvent,
      },
    )

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
  message.status = 'streaming'

  session.workflow = request.workflow
  session.routeTarget = request.workflow === 'learning_path' ? (request.technology ?? '') : ''
  workflow.value = request.workflow
  resetReferences(session, request)
  saveState()

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

async function loadReferences(session: LocalSession, question: string): Promise<void> {
  const request = (session.referenceRequest += 1)
  session.referencesQuestion = question
  session.referencesLoading = true
  session.referencesError = ''
  session.referencesLoaded = false

  try {
    const result = await searchArticles(
      { keyword: question, pageSize: 5 },
      { skipErrorToast: true },
    )
    if (request !== session.referenceRequest) return

    session.references = result.items.map((item) => ({
      articleId: item.articleId,
      title: item.title,
      summary: item.summary,
      tags: item.tags ?? [],
    }))
    session.referencesLoaded = true
  } catch (error) {
    if (request !== session.referenceRequest) return
    session.referencesError = isApiError(error) ? error.message : '参考资料加载失败。'
  } finally {
    if (request === session.referenceRequest) {
      session.referencesLoading = false
      saveState()
    }
  }
}

function retryReferences(): void {
  const session = activeSession.value
  if (!session?.referencesQuestion) return
  void loadReferences(session, session.referencesQuestion)
}

function parseRoute(markdown: string): { html: string; stages: RouteStage[] } {
  const headingPattern = /^##\s*第\s*(\d+)\s*阶段[：:]\s*(.+)$/gm
  const techPattern = /^-\s*技术[：:]\s*\[([^\]]+)\]\(\/tag\/([^)]*)\)/gm
  const matches = [...markdown.matchAll(headingPattern)]

  const stages: RouteStage[] = matches.map((match, index) => {
    const start = (match.index ?? 0) + match[0].length
    const end = matches[index + 1]?.index ?? markdown.length
    const section = markdown.slice(start, end)
    const technologies = [...new Set([...section.matchAll(techPattern)].map((item) => item[1]))]
    return {
      index: Number(match[1]) || index + 1,
      title: match[2].trim(),
      id: `stage-${index + 1}`,
      technologies,
    }
  })

  const template = document.createElement('template')
  template.innerHTML = renderMarkdown(markdown)

  let stageNo = 0
  for (const heading of template.content.querySelectorAll('h2')) {
    if (/^第\s*\d+\s*阶段/.test(heading.textContent?.trim() ?? '')) {
      stageNo += 1
      heading.id = `stage-${stageNo}`
    }
  }

  return { html: template.innerHTML, stages }
}

function createStageReference(stage: RouteStage): StageReference {
  return reactive<StageReference>({
    ...stage,
    references: [],
    loading: true,
    error: '',
    loaded: false,
    request: 0,
  })
}

async function loadStageReferences(session: LocalSession, stages: RouteStage[]): Promise<void> {
  const stageRefs = stages.map((stage) => createStageReference(stage))
  session.stageReferences = stageRefs
  await Promise.all(stageRefs.map((stage) => loadStageReference(stage)))
}

async function loadStageReference(stage: StageReference): Promise<void> {
  const request = (stage.request += 1)
  stage.loading = true
  stage.error = ''
  stage.loaded = false

  const keyword = stage.technologies[0] ?? stage.title
  try {
    const result = await searchArticles({ keyword, pageSize: 3 }, { skipErrorToast: true })
    if (request !== stage.request) return

    stage.references = result.items.map((item) => ({
      articleId: item.articleId,
      title: item.title,
      summary: item.summary,
      tags: item.tags ?? [],
    }))
    stage.loaded = true
  } catch (error) {
    if (request !== stage.request) return
    stage.error = isApiError(error) ? error.message : '参考资料加载失败。'
  } finally {
    if (request === stage.request) {
      stage.loading = false
      saveState()
    }
  }
}

function retryStage(stage: StageReference): void {
  void loadStageReference(stage)
}

function scrollToStage(id: string): void {
  if (!id) return
  const target = messagesRef.value?.querySelector<HTMLElement>(`[id="${id}"]`)
  target?.scrollIntoView({ behavior: 'smooth', block: 'start' })
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

onMounted(() => {
  void dict.loadTags()
  window.addEventListener('pagehide', saveState)
  window.addEventListener('offline', handleOffline)
  window.addEventListener('online', handleOnline)
})

onBeforeUnmount(() => {
  window.removeEventListener('pagehide', saveState)
  window.removeEventListener('offline', handleOffline)
  window.removeEventListener('online', handleOnline)
  saveState()
  activeController?.abort()
  if (flushTimer !== null) window.clearTimeout(flushTimer)
  if (saveTimer !== null) window.clearTimeout(saveTimer)
})

useSeo({
  title: 'AI 技术导师',
  description: '技术问答与学习路线双模式的工作台：会话、消息与参考资料。',
})
</script>

<template>
  <section class="ai-page">
    <div class="ai-shell">
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

          <p class="ai-sessions__local">仅保存在本机 · 最多 30 个会话</p>

          <ul class="ai-sessions__list">
            <li v-for="session in sessions" :key="session.id" class="ai-session-row">
              <button
                class="ai-session"
                :class="{ 'is-active': session.id === activeSessionId }"
                type="button"
                @click="selectSession(session)"
              >
                <span class="ai-session__title">{{ session.title }}</span>
                <span class="ai-session__mode">
                  {{ session.workflow === 'qa' ? '问答' : '路线' }}
                </span>
              </button>
              <button
                class="ai-session__delete"
                type="button"
                :aria-label="`删除会话 ${session.title}`"
                title="删除会话"
                @click="removeSession(session)"
              >
                ×
              </button>
            </li>
          </ul>

          <p class="ai-sessions__hint">刷新后会恢复；换浏览器或设备不会同步。</p>
        </div>

        <p class="ai-side__foot">WISDOMHUB · KNOWLEDGE IS A NETWORK</p>
      </aside>

      <main class="ai-main">
        <header class="ai-main__head">
          <div>
            <p class="ai-eyebrow">
              {{ workflow === 'qa' ? '[ QA / WORKBENCH ]' : '[ LEARNING / WORKBENCH ]' }}
            </p>
            <h2>{{ workflowLabel }}</h2>
          </div>
          <div class="ai-main__head-right">
            <p class="ai-main__status">
              <span class="ai-dot" aria-hidden="true"></span>
              {{ statusLabel }}
            </p>
            <WhButton
              v-if="isStreaming && workflow === 'learning_path'"
              size="sm"
              variant="danger"
              @click="stopStreaming"
            >
              停止生成
            </WhButton>
            <button
              v-else-if="workflow === 'learning_path' && activeMessages.length"
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
            <div v-if="!activeMessages.length" class="ai-empty">
              <p class="ai-empty__eyebrow">
                {{ workflow === 'qa' ? '[ EMPTY / QA ]' : '[ EMPTY / PATH ]' }}
              </p>

              <template v-if="workflow === 'qa'">
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

              <template v-else>
                <h3>先定目标，再排路径</h3>
                <p>
                  从标签库选择目标技术，或直接输入任意技术；已掌握可以多选，也可以自己补充。
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
                          placeholder="例如 Redis；也可以直接输入任意技术"
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
                              ? '标签库加载中，可直接输入技术名。'
                              : '没有匹配的标签，直接输入即可。'
                          }}
                        </li>
                      </ul>
                    </div>
                    <p class="ai-route__hint">
                      可以从标签库选择，也可以直接输入任意技术；知识图谱未收录的技术可能无法生成路线。
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

        <form v-if="workflow === 'qa'" class="ai-composer" @submit.prevent="submit">
          <label class="ai-composer__label" for="ai-input">输入你的问题</label>
          <textarea
            id="ai-input"
            ref="inputRef"
            v-model="draft"
            class="ai-composer__input"
            placeholder="描述你的问题、运行环境和已经尝试过的方案…"
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

      <aside class="ai-side ai-side--refs" aria-label="参考资料">
        <header class="ai-refs__head">
          <p class="ai-eyebrow">[ REFERENCES ]</p>
          <h2>参考资料</h2>
          <p v-if="workflow === 'qa' && referencesQuestion" class="ai-refs__query">
            Q / {{ referencesQuestion }}
          </p>
          <p v-else-if="workflow === 'learning_path' && routeTarget" class="ai-refs__query">
            ROUTE / {{ routeTarget }}
          </p>
        </header>

        <template v-if="workflow === 'learning_path'">
          <div v-if="!stageReferences.length" class="ai-refs__empty">
            <p v-if="routeTarget">路线生成后，这里会按阶段列出参考文章。</p>
            <p v-else>填写目标技术后，这里会按阶段列出参考文章。</p>
            <RouterLink class="ai-refs__link" :to="{ name: 'article-list' }">
              先去逛逛文章 →
            </RouterLink>
          </div>

          <div v-else class="ai-stages">
            <section v-for="stage in stageReferences" :key="stage.id" class="ai-stage-ref">
              <button class="ai-stage-ref__title" type="button" @click="scrollToStage(stage.id)">
                <span class="ai-stage-ref__index">
                  STAGE {{ String(stage.index).padStart(2, '0') }}
                </span>
                <span>{{ stage.title }}</span>
              </button>

              <div v-if="stage.technologies.length" class="ai-refs__tags">
                <RouterLink
                  v-for="technology in stage.technologies"
                  :key="technology"
                  :to="{ name: 'search', query: { type: 'tag', keyword: technology } }"
                >
                  {{ technology }}
                </RouterLink>
              </div>

              <div v-if="stage.loading" class="ai-refs__state">检索中…</div>
              <div
                v-else-if="stage.error"
                class="ai-refs__state ai-refs__state--error"
                role="alert"
              >
                <p>{{ stage.error }}</p>
                <button class="ai-refs__retry" type="button" @click="retryStage(stage)">
                  重试
                </button>
              </div>
              <ul v-else-if="stage.references.length" class="ai-stage-ref__list">
                <li v-for="(reference, index) in stage.references" :key="reference.articleId">
                  <span class="ai-refs__index">{{ String(index + 1).padStart(2, '0') }}</span>
                  <RouterLink
                    class="ai-stage-ref__article"
                    :to="{
                      name: 'article-detail',
                      params: { articleId: reference.articleId },
                    }"
                  >
                    {{ reference.title }}
                  </RouterLink>
                </li>
              </ul>
              <p v-else-if="stage.loaded" class="ai-refs__state">暂无参考文章。</p>
            </section>
          </div>
        </template>

        <template v-else>
          <div v-if="referencesLoading" class="ai-refs__state" aria-live="polite">
            正在检索相关文章…
          </div>

          <div
            v-else-if="referencesError"
            class="ai-refs__state ai-refs__state--error"
            role="alert"
          >
            <p>{{ referencesError }}</p>
            <button class="ai-refs__retry" type="button" @click="retryReferences">重试</button>
          </div>

          <ul v-else-if="references.length" class="ai-refs__list">
            <li v-for="(reference, index) in references" :key="reference.articleId">
              <span class="ai-refs__index">{{ String(index + 1).padStart(2, '0') }}</span>
              <RouterLink
                class="ai-refs__title"
                :to="{ name: 'article-detail', params: { articleId: reference.articleId } }"
              >
                {{ reference.title }}
              </RouterLink>
              <p class="ai-refs__summary">{{ reference.summary }}</p>
              <div v-if="reference.tags.length" class="ai-refs__tags">
                <RouterLink
                  v-for="tag in reference.tags"
                  :key="tag.tagId"
                  :to="{ name: 'tech-detail', params: { tagId: tag.tagId } }"
                >
                  {{ tag.name }}
                </RouterLink>
              </div>
            </li>
          </ul>

          <div v-else class="ai-refs__empty">
            <p v-if="referencesQuestion && referencesLoaded">没有检索到相关文章，换个问法试试。</p>
            <p v-else-if="referencesQuestion">回答完成后，这里会列出相关文章。</p>
            <p v-else>对话开始后，这里会列出回答引用的文章、问题与标签。</p>
            <RouterLink class="ai-refs__link" :to="{ name: 'article-list' }">
              先去逛逛文章 →
            </RouterLink>
          </div>
        </template>

        <p class="ai-side__foot">CITATIONS · LAZY LOAD</p>
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

.ai-session-row:hover .ai-session__delete,
.ai-session__delete:focus-visible {
  opacity: 1;
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

.ai-refs__state {
  color: var(--text-muted);
  font-size: var(--text-sm);
}

.ai-refs__state--error {
  display: grid;
  justify-items: start;
  gap: var(--space-2);
  color: var(--verm-deep);
}

.ai-refs__retry {
  padding-bottom: 2px;
  border-bottom: 1px solid currentColor;
  font-family: var(--mono);
  font-size: 10px;
}

.ai-refs__list {
  display: grid;
  gap: var(--space-4);
  margin: 0;
  padding: 0;
  list-style: none;
}

.ai-refs__list li {
  display: grid;
  gap: var(--space-1);
  padding-bottom: var(--space-3);
  border-bottom: 1px solid var(--line);
}

.ai-refs__index {
  color: var(--text-muted);
  font-family: var(--mono);
  font-size: 9px;
}

.ai-refs__title {
  font-family: var(--serif);
  font-size: var(--text-md);
  font-weight: var(--weight-semibold);
  line-height: var(--leading-snug);
}

.ai-refs__title:hover {
  color: var(--verm-deep);
}

.ai-refs__summary {
  display: -webkit-box;
  overflow: hidden;
  color: var(--text-muted);
  font-size: var(--text-xs);
  line-height: var(--leading-relaxed);
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
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

.ai-stage-ref__title:hover {
  color: var(--verm-deep);
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

.ai-stage-ref__list {
  display: grid;
  gap: var(--space-2);
  margin: 0;
  padding: 0;
  list-style: none;
}

.ai-stage-ref__list li {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr);
  gap: var(--space-2);
  align-items: start;
}

.ai-stage-ref__article {
  color: var(--text-body);
  font-size: var(--text-xs);
  line-height: var(--leading-relaxed);
}

.ai-stage-ref__article:hover {
  color: var(--verm-deep);
}

.ai-refs__empty {
  display: grid;
  gap: var(--space-3);
  color: var(--text-muted);
  font-size: var(--text-sm);
  line-height: var(--leading-relaxed);
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

.ai-side--refs .ai-side__foot {
  color: var(--text-muted);
}

@media (max-width: 1279px) {
  .ai-shell {
    grid-template-columns: 240px minmax(0, 1fr) 270px;
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
