<script setup lang="ts">
import { ref } from 'vue'
import { RouterLink } from 'vue-router'

import { articleApi, authApi, clearTokens, get, recommendationApi, setTokens } from '@/api'
import { useAuthStore } from '@/stores'
import { useAsyncData } from '@/composables/useAsyncData'
import { usePagination } from '@/composables/usePagination'
import { formatDateTime, formatMoney, formatRelativeTime, toBackendDateTime } from '@/utils'
import { renderMarkdown } from '@/utils/markdown'
import WhAvatar from '@/components/base/WhAvatar.vue'
import WhBadge from '@/components/base/WhBadge.vue'
import WhButton from '@/components/base/WhButton.vue'
import WhCheckbox from '@/components/base/WhCheckbox.vue'
import WhDrawer from '@/components/base/WhDrawer.vue'
import WhEmpty from '@/components/base/WhEmpty.vue'
import WhInput from '@/components/base/WhInput.vue'
import WhModal from '@/components/base/WhModal.vue'
import WhPagination from '@/components/base/WhPagination.vue'
import WhSelect from '@/components/base/WhSelect.vue'
import WhSkeleton from '@/components/base/WhSkeleton.vue'
import WhSwitch from '@/components/base/WhSwitch.vue'
import WhTabs from '@/components/base/WhTabs.vue'
import WhTag from '@/components/base/WhTag.vue'
import WhTextarea from '@/components/base/WhTextarea.vue'
import WhTooltip from '@/components/base/WhTooltip.vue'
import { confirm } from '@/components/base/confirm'
import { toast } from '@/components/base/toast'

const input = ref('')
const email = ref('pulsar@')
const password = ref('')
const textarea = ref('先把问题说清楚，再开始写代码。')
const textareaError = ref('这一行会触发错误状态。')
const inputSm = ref('')
const inputLg = ref('')
const select = ref<string | number | null>(2)
const checked = ref(true)
const unchecked = ref(false)
const indeterminate = ref(true)
const switched = ref(true)
const switchOff = ref(false)
const switchedSm = ref(false)
const tab = ref('articles')
const tabPill = ref('week')
const page = ref(1)
const pageSize = ref(10)
const total = ref(128)
const modalOpen = ref(false)
const modalTitle = ref('')
const modalDescription = ref('')
const drawerOpen = ref(false)
const tags = ref(['Vue 3', 'Spring Boot', 'Redis'])

const authStore = useAuthStore()
const mockEnabled = import.meta.env.VITE_USE_MOCK === 'true'
const apiBase = import.meta.env.VITE_API_BASE_URL || '(same-origin)'
const mockModeLabel = mockEnabled ? 'Mock fixtures（不请求后端）' : `API ${apiBase}`
const mockHomeResult = ref<string | null>(null)
const mockDetailResult = ref<string | null>(null)
const mockLoginResult = ref<string | null>(null)

const avatarSrc =
  "data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 64 64'%3E%3Crect width='64' height='64' fill='%230b0b0e'/%3E%3Ctext x='32' y='42' font-family='monospace' font-size='26' fill='%23c9ff2e' text-anchor='middle'%3E64%3C/text%3E%3C/svg%3E"

const selectOptions = [
  { label: '全部标签', value: 0 },
  { label: 'Java', value: 1 },
  { label: 'Spring Boot', value: 2 },
  { label: 'Redis', value: 3 },
  { label: '知识图谱', value: 4, disabled: true },
]

const tabs = [
  { label: '文章', value: 'articles', badge: 12 },
  { label: '问答', value: 'questions' },
  { label: '用户', value: 'users', disabled: true },
]

const pillTabs = [
  { label: '本周', value: 'week' },
  { label: '本月', value: 'month' },
  { label: '全部', value: 'all' },
]

const sections = [
  { id: 'buttons', label: '按钮' },
  { id: 'forms', label: '表单' },
  { id: 'selection', label: '选择' },
  { id: 'display', label: '展示' },
  { id: 'tabs', label: '标签页' },
  { id: 'pagination', label: '分页' },
  { id: 'http', label: '请求层' },
  { id: 'mock', label: 'Mock' },
  { id: 'tools', label: '工具' },
  { id: 'feedback', label: '反馈' },
  { id: 'overlay', label: '覆盖层' },
]

function removeTag(tag: string): void {
  tags.value = tags.value.filter((item) => item !== tag)
}

function resetTags(): void {
  tags.value = ['Vue 3', 'Spring Boot', 'Redis']
}

type DemoToastType = 'success' | 'error' | 'info' | 'warning'

function showToast(type: DemoToastType): void {
  const messages: Record<DemoToastType, string> = {
    success: '文章已发布',
    error: '保存失败，请稍后重试',
    info: '已复制到剪贴板',
    warning: '当前内容尚未保存',
  }
  toast[type](messages[type], type === 'error' ? { title: '操作失败' } : undefined)
}

async function askConfirm(): Promise<void> {
  const ok = await confirm({
    title: '删除这篇文章？',
    message: '删除后不可恢复，评论、点赞与收藏也会一并清理。',
    confirmText: '删除',
    danger: true,
  })
  if (ok) toast.success('已确认删除')
  else toast.info('已取消')
}

async function testTagRequest(): Promise<void> {
  try {
    const tags = await get<{ name: string }[]>('/api/content/tag')
    toast.success(`GET /api/content/tag 成功，${tags.length} 个标签`)
  } catch {
    // 错误提示由请求层统一处理
  }
}

async function testArrayParams(): Promise<void> {
  try {
    const result = await get<{ query: string }>('/api/echo', {
      ids: [1, 2],
      tags: ['a', 'b'],
    })
    toast.info(`query: ${result.query}`)
  } catch {
    // ignore
  }
}

async function testBusinessError(): Promise<void> {
  try {
    await get<null>('/api/business-error')
  } catch {
    // 请求层已 toast
  }
}

async function testServerError(): Promise<void> {
  try {
    await get<null>('/api/server-error')
  } catch {
    // 请求层已 toast
  }
}

async function testNetworkError(): Promise<void> {
  try {
    await get<unknown>('/api/content/tag', undefined, { baseURL: 'http://127.0.0.1:9099' })
  } catch {
    // 请求层已 toast
  }
}

async function testRefreshSingleFlight(): Promise<void> {
  setTokens({ accessToken: 'mock-access', refreshToken: 'mock-refresh' })
  try {
    const before = await get<{ refreshCount: number }>('/api/mock-stats')
    const results = await Promise.all([
      get<number>('/api/notification/unread/count'),
      get<number>('/api/notification/unread/count'),
      get<number>('/api/notification/unread/count'),
    ])
    const after = await get<{ refreshCount: number }>('/api/mock-stats')
    toast.success(
      `并发 ${results.length} 个请求成功，refresh ${after.refreshCount - before.refreshCount} 次`,
    )
  } catch {
    // 请求层已 toast
  } finally {
    clearTokens()
  }
}

async function testUnauthorized(): Promise<void> {
  clearTokens()
  try {
    await get<number>('/api/notification/unread/count')
  } catch {
    // 请求层会 toast 并跳登录
  }
}

async function testMockHome(): Promise<void> {
  mockHomeResult.value = '加载中…'
  try {
    const items = await recommendationApi.home(4)
    mockHomeResult.value = `${items.length} 条：${items.map((item) => item.title).join(' / ')}`
  } catch {
    mockHomeResult.value = '加载失败'
  }
}

async function testMockDetail(): Promise<void> {
  mockDetailResult.value = '加载中…'
  try {
    const detail = await articleApi.detail('1001')
    mockDetailResult.value = `${detail.title} · locked=${detail.locked} · html=${detail.html?.length ?? 0} 字符`
  } catch {
    mockDetailResult.value = '加载失败'
  }
}

async function testMockLogin(): Promise<void> {
  mockLoginResult.value = '登录中…'
  try {
    const session = await authApi.login({ phone: '13800000000', password: 'demo' })
    authStore.setSession(session)
    mockLoginResult.value = `userId=${authStore.userId}，hasEmail=${authStore.hasEmail}`
    authStore.clearSession()
  } catch {
    mockLoginResult.value = '登录失败'
  }
}

const {
  page: demoPage,
  pageSize: demoPageSize,
  total: demoTotal,
  query: demoQuery,
  setTotal: setDemoTotal,
} = usePagination({ pageSize: 10 })
setDemoTotal(56)

const {
  data: asyncDemoData,
  loading: asyncDemoLoading,
  error: asyncDemoError,
  execute: reloadAsyncDemo,
} = useAsyncData(async () => {
  await new Promise((resolve) => setTimeout(resolve, 600))
  return { message: '异步数据已加载', at: new Date().toISOString() }
})

const formattedDate = formatDateTime('2026-09-24T13:08:14Z')
const relativeDate = formatRelativeTime(new Date(Date.now() - 2 * 60 * 60 * 1000).toISOString())
const formattedMoney = formatMoney(12900)
const freeMoney = formatMoney(0, { freeLabel: '免费' })
const backendDate = toBackendDateTime('2026-09-24T13:08:14Z')
const markdownSample = [
  '# Markdown 预览',
  '',
  '- 支持列表、链接与 `code`',
  '- 代码高亮由 highlight.js 提供',
  '',
  '```ts',
  'const answer = 42',
  '```',
  '',
  '> 引用块',
].join('\n')
const markdownHtml = renderMarkdown(markdownSample)
</script>

<template>
  <div class="dev">
    <div class="wh-container">
      <header class="dev__head">
        <p class="dev__eyebrow">[ F0.4 / BASE COMPONENTS ]</p>
        <h1 class="dev__title">组件库 v1</h1>
        <p class="dev__lede">
          18 个基础组件与 Toast / Confirm 服务。这个页面只用于开发验收，不进入产品导航。
        </p>
        <div class="dev__head-actions">
          <RouterLink class="wh-action wh-action--ghost" to="/dev/tokens">设计令牌</RouterLink>
          <RouterLink class="wh-action wh-action--ghost" :to="{ name: 'home' }">回首页</RouterLink>
        </div>
      </header>

      <nav class="dev__nav" aria-label="组件分组">
        <a v-for="section in sections" :key="section.id" :href="`#${section.id}`">
          {{ section.label }}
        </a>
      </nav>

      <section id="buttons" class="dev-section">
        <header class="dev-section__head">
          <h2>Button</h2>
          <code>WhButton</code>
        </header>
        <div class="dev-grid">
          <div class="dev-card">
            <h3>变体</h3>
            <div class="dev-row">
              <WhButton>主要</WhButton>
              <WhButton variant="secondary">次要</WhButton>
              <WhButton variant="ghost">幽灵</WhButton>
              <WhButton variant="danger">危险</WhButton>
              <WhButton variant="text">文字</WhButton>
            </div>
          </div>

          <div class="dev-card">
            <h3>尺寸</h3>
            <div class="dev-row">
              <WhButton size="sm">小按钮</WhButton>
              <WhButton>中按钮</WhButton>
              <WhButton size="lg">大按钮</WhButton>
              <WhButton size="sm" icon-only aria-label="搜索">
                <template #icon>
                  <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8">
                    <circle cx="11" cy="11" r="7" />
                    <path d="m20 20-3.5-3.5" stroke-linecap="round" />
                  </svg>
                </template>
              </WhButton>
            </div>
          </div>

          <div class="dev-card">
            <h3>状态</h3>
            <div class="dev-row">
              <WhButton loading>提交中</WhButton>
              <WhButton disabled>禁用</WhButton>
              <WhButton variant="secondary" loading>加载</WhButton>
            </div>
          </div>

          <div class="dev-card">
            <h3>块级</h3>
            <WhButton block>块级按钮</WhButton>
          </div>
        </div>
      </section>

      <section id="forms" class="dev-section">
        <header class="dev-section__head">
          <h2>Input / Textarea / Select</h2>
          <code>WhInput · WhTextarea · WhSelect</code>
        </header>
        <div class="dev-grid">
          <div class="dev-card">
            <h3>Input</h3>
            <div class="dev-stack">
              <div>
                <label class="dev-label" for="demo-input">用户名</label>
                <WhInput
                  id="demo-input"
                  v-model="input"
                  placeholder="请输入用户名"
                  clearable
                  hint="2-20 个字符"
                />
              </div>
              <div>
                <label class="dev-label" for="demo-password">密码</label>
                <WhInput
                  id="demo-password"
                  v-model="password"
                  type="password"
                  placeholder="至少 8 位"
                />
              </div>
              <div>
                <label class="dev-label" for="demo-error">邮箱</label>
                <WhInput id="demo-error" v-model="email" invalid error="邮箱格式不正确" />
              </div>
              <div>
                <label class="dev-label" for="demo-prefix">站点</label>
                <WhInput id="demo-prefix" placeholder="wisdomhub" disabled>
                  <template #prefix>https://</template>
                  <template #suffix>.dev</template>
                </WhInput>
              </div>
            </div>
          </div>

          <div class="dev-card">
            <h3>Input 尺寸</h3>
            <div class="dev-stack">
              <WhInput v-model="inputSm" size="sm" placeholder="小尺寸" />
              <WhInput v-model="input" placeholder="中尺寸" />
              <WhInput v-model="inputLg" size="lg" placeholder="大尺寸" />
            </div>
          </div>

          <div class="dev-card">
            <h3>Textarea</h3>
            <div class="dev-stack">
              <WhTextarea
                v-model="textarea"
                placeholder="写点什么…"
                :maxlength="200"
                show-count
                auto-resize
                hint="支持 Markdown"
              />
              <WhTextarea v-model="textareaError" invalid error="内容不能为空" :rows="3" />
            </div>
          </div>

          <div class="dev-card">
            <h3>Select</h3>
            <div class="dev-stack">
              <WhSelect v-model="select" :options="selectOptions" clearable hint="选择一个标签" />
              <WhSelect
                :model-value="9"
                :options="selectOptions"
                invalid
                error="请选择一个有效标签"
              />
              <WhSelect
                :model-value="null"
                :options="selectOptions"
                disabled
                placeholder="已禁用"
              />
            </div>
          </div>
        </div>
      </section>

      <section id="selection" class="dev-section">
        <header class="dev-section__head">
          <h2>Checkbox / Switch</h2>
          <code>WhCheckbox · WhSwitch</code>
        </header>
        <div class="dev-grid">
          <div class="dev-card">
            <h3>Checkbox</h3>
            <div class="dev-stack">
              <WhCheckbox v-model="checked" label="接收评论通知" />
              <WhCheckbox v-model="unchecked" label="未选中" />
              <WhCheckbox :model-value="true" :indeterminate="indeterminate" label="部分选中" />
              <WhCheckbox :model-value="false" disabled label="禁用" />
            </div>
          </div>

          <div class="dev-card">
            <h3>Switch</h3>
            <div class="dev-stack">
              <WhSwitch v-model="switched" label="公开文章" />
              <WhSwitch v-model="switchOff" label="关闭状态" />
              <WhSwitch :model-value="true" disabled label="禁用" />
              <WhSwitch v-model="switchedSm" size="sm" label="小尺寸" />
            </div>
          </div>
        </div>
      </section>

      <section id="display" class="dev-section">
        <header class="dev-section__head">
          <h2>Tag / Badge / Avatar / Empty / Skeleton / Tooltip</h2>
          <code>WhTag · WhBadge · WhAvatar · WhEmpty · WhSkeleton · WhTooltip</code>
        </header>
        <div class="dev-grid">
          <div class="dev-card">
            <h3>Tag</h3>
            <div class="dev-row">
              <WhTag>默认</WhTag>
              <WhTag variant="acid">Acid</WhTag>
              <WhTag variant="verm">危险</WhTag>
              <WhTag variant="violet">AI</WhTag>
              <WhTag variant="outline" size="sm">小标签</WhTag>
            </div>
            <div class="dev-row dev-row--spaced">
              <WhTag v-for="tag in tags" :key="tag" closable @close="removeTag(tag)">
                {{ tag }}
              </WhTag>
              <WhButton v-if="!tags.length" size="sm" variant="ghost" @click="resetTags">
                重置标签
              </WhButton>
            </div>
          </div>

          <div class="dev-card">
            <h3>Badge</h3>
            <div class="dev-row">
              <WhBadge :count="3">
                <WhButton variant="ghost" size="sm">通知</WhButton>
              </WhBadge>
              <WhBadge :count="120" :max="99">
                <WhButton variant="ghost" size="sm">消息</WhButton>
              </WhBadge>
              <WhBadge dot>
                <WhAvatar name="Pulsar" size="sm" />
              </WhBadge>
              <WhBadge :count="0" show-zero variant="acid">
                <WhButton variant="ghost" size="sm">零值</WhButton>
              </WhBadge>
            </div>
          </div>

          <div class="dev-card">
            <h3>Avatar</h3>
            <div class="dev-row">
              <WhAvatar name="Pulsar" size="xs" />
              <WhAvatar name="Pulsar" size="sm" />
              <WhAvatar name="Pulsar" size="md" />
              <WhAvatar name="Pulsar" size="lg" shape="circle" />
              <WhAvatar :src="avatarSrc" name="WisdomHub" size="xl" shape="circle" />
            </div>
          </div>

          <div class="dev-card">
            <h3>Empty</h3>
            <WhEmpty title="还没有文章" description="发布第一篇文章，开始建立你的知识网络。">
              <WhButton size="sm">写第一篇</WhButton>
            </WhEmpty>
          </div>

          <div class="dev-card">
            <h3>Skeleton</h3>
            <div class="dev-stack">
              <WhSkeleton :lines="3" />
              <div class="dev-skeleton-row">
                <WhSkeleton width="48px" height="48px" circle />
                <div class="dev-skeleton-lines">
                  <WhSkeleton width="60%" height="12px" />
                  <WhSkeleton width="80%" height="12px" />
                </div>
              </div>
            </div>
          </div>

          <div class="dev-card">
            <h3>Tooltip</h3>
            <div class="dev-row">
              <WhTooltip content="顶部提示">
                <WhButton variant="ghost" size="sm">Top</WhButton>
              </WhTooltip>
              <WhTooltip content="底部提示" placement="bottom">
                <WhButton variant="ghost" size="sm">Bottom</WhButton>
              </WhTooltip>
              <WhTooltip content="左侧提示" placement="left">
                <WhButton variant="ghost" size="sm">Left</WhButton>
              </WhTooltip>
              <WhTooltip content="右侧提示" placement="right">
                <WhButton variant="ghost" size="sm">Right</WhButton>
              </WhTooltip>
            </div>
          </div>
        </div>
      </section>

      <section id="tabs" class="dev-section">
        <header class="dev-section__head">
          <h2>Tabs</h2>
          <code>WhTabs</code>
        </header>
        <div class="dev-grid">
          <div class="dev-card">
            <h3>Line</h3>
            <WhTabs v-model="tab" :items="tabs" />
            <p class="dev-note">当前：{{ tab }}</p>
          </div>
          <div class="dev-card">
            <h3>Pill / sm</h3>
            <WhTabs v-model="tabPill" :items="pillTabs" variant="pill" size="sm" />
            <p class="dev-note">当前：{{ tabPill }}</p>
          </div>
        </div>
      </section>

      <section id="pagination" class="dev-section">
        <header class="dev-section__head">
          <h2>Pagination</h2>
          <code>WhPagination</code>
        </header>
        <div class="dev-card">
          <WhPagination
            v-model:page="page"
            v-model:page-size="pageSize"
            :total="total"
            show-size-changer
          />
          <p class="dev-note">page={{ page }} · pageSize={{ pageSize }} · total={{ total }}</p>
        </div>
      </section>

      <section id="http" class="dev-section">
        <header class="dev-section__head">
          <h2>HTTP 请求层</h2>
          <code>get / post / put / del</code>
        </header>
        <div class="dev-grid">
          <div class="dev-card">
            <h3>成功与参数</h3>
            <div class="dev-row">
              <WhButton size="sm" variant="ghost" @click="testTagRequest">GET 标签</WhButton>
              <WhButton size="sm" variant="ghost" @click="testArrayParams">数组参数</WhButton>
            </div>
            <p class="dev-note">数组参数应序列化为 `ids=1,2`，而不是 `ids[]=1`。</p>
          </div>

          <div class="dev-card">
            <h3>错误映射</h3>
            <div class="dev-row">
              <WhButton size="sm" variant="ghost" @click="testBusinessError">业务 409</WhButton>
              <WhButton size="sm" variant="ghost" @click="testServerError">服务 503</WhButton>
              <WhButton size="sm" variant="ghost" @click="testNetworkError">网络异常</WhButton>
            </div>
            <p class="dev-note">
              业务错误走 body.code，服务错误走真实 HTTP 状态码；两者都由请求层统一 toast。
            </p>
          </div>

          <div class="dev-card">
            <h3>401 与会话</h3>
            <div class="dev-row">
              <WhButton size="sm" variant="secondary" @click="testRefreshSingleFlight">
                401 单飞刷新
              </WhButton>
              <WhButton size="sm" variant="danger" @click="testUnauthorized"> 401 跳登录 </WhButton>
            </div>
            <p class="dev-note">需要 9090 上的网关或本地 mock。401 跳登录会离开当前页。</p>
          </div>
        </div>
      </section>

      <section id="mock" class="dev-section">
        <header class="dev-section__head">
          <h2>Mock 冒烟</h2>
          <code>VITE_USE_MOCK</code>
        </header>
        <div class="dev-grid">
          <div class="dev-card">
            <h3>当前模式</h3>
            <div class="dev-stack">
              <p class="dev-note">{{ mockModeLabel }}</p>
              <p class="dev-note">Mock：<code>pnpm dev:mock</code>（5174）</p>
              <p class="dev-note">非 Mock：<code>pnpm dev</code>（5173 → 9090）</p>
            </div>
          </div>

          <div class="dev-card">
            <h3>首页推荐</h3>
            <WhButton size="sm" variant="ghost" @click="testMockHome">加载推荐</WhButton>
            <p class="dev-note">{{ mockHomeResult ?? '未加载' }}</p>
          </div>

          <div class="dev-card">
            <h3>文章详情</h3>
            <WhButton size="sm" variant="ghost" @click="testMockDetail">查看文章</WhButton>
            <p class="dev-note">{{ mockDetailResult ?? '未加载' }}</p>
          </div>

          <div class="dev-card">
            <h3>登录</h3>
            <WhButton size="sm" variant="ghost" @click="testMockLogin">模拟登录</WhButton>
            <p class="dev-note">{{ mockLoginResult ?? '未登录' }}</p>
          </div>
        </div>
      </section>

      <section id="tools" class="dev-section">
        <header class="dev-section__head">
          <h2>工具与 composables</h2>
          <code>utils · usePagination · useAsyncData</code>
        </header>
        <div class="dev-grid">
          <div class="dev-card">
            <h3>格式化</h3>
            <div class="dev-stack">
              <p class="dev-note">formatDateTime: {{ formattedDate }}</p>
              <p class="dev-note">formatRelativeTime: {{ relativeDate }}</p>
              <p class="dev-note">formatMoney: {{ formattedMoney }} / {{ freeMoney }}</p>
              <p class="dev-note">toBackendDateTime: {{ backendDate }}</p>
            </div>
          </div>

          <div class="dev-card">
            <h3>Markdown</h3>
            <!-- renderMarkdown 内部用 DOMPurify 消毒 -->
            <div class="wh-prose dev-markdown" v-html="markdownHtml"></div>
          </div>

          <div class="dev-card">
            <h3>usePagination</h3>
            <WhPagination
              v-model:page="demoPage"
              v-model:page-size="demoPageSize"
              :total="demoTotal"
              show-size-changer
            />
            <p class="dev-note">
              query: page={{ demoQuery.page }} · pageSize={{ demoQuery.pageSize }}
            </p>
          </div>

          <div class="dev-card">
            <h3>useAsyncData</h3>
            <div class="dev-stack">
              <p v-if="asyncDemoLoading" class="dev-note">加载中…</p>
              <p v-else-if="asyncDemoError" class="dev-note">加载失败</p>
              <p v-else-if="asyncDemoData" class="dev-note">
                {{ asyncDemoData.message }} · {{ formatDateTime(asyncDemoData.at) }}
              </p>
              <WhButton size="sm" variant="ghost" @click="reloadAsyncDemo">重新加载</WhButton>
            </div>
          </div>
        </div>
      </section>

      <section id="feedback" class="dev-section">
        <header class="dev-section__head">
          <h2>Toast / Confirm</h2>
          <code>toast · confirm</code>
        </header>
        <div class="dev-grid">
          <div class="dev-card">
            <h3>Toast</h3>
            <div class="dev-row">
              <WhButton size="sm" variant="ghost" @click="showToast('success')">成功</WhButton>
              <WhButton size="sm" variant="ghost" @click="showToast('error')">错误</WhButton>
              <WhButton size="sm" variant="ghost" @click="showToast('info')">信息</WhButton>
              <WhButton size="sm" variant="ghost" @click="showToast('warning')">警告</WhButton>
              <WhButton
                size="sm"
                variant="text"
                @click="toast.info('这条会一直显示，直到手动关闭', { duration: 0 })"
              >
                常驻
              </WhButton>
              <WhButton size="sm" variant="text" @click="toast.clear()">清空</WhButton>
            </div>
          </div>

          <div class="dev-card">
            <h3>Confirm</h3>
            <WhButton size="sm" variant="danger" @click="askConfirm">打开确认框</WhButton>
            <p class="dev-note">返回 Promise&lt;boolean&gt;，适合删除、退款等二次确认。</p>
          </div>
        </div>
      </section>

      <section id="overlay" class="dev-section">
        <header class="dev-section__head">
          <h2>Modal / Drawer</h2>
          <code>WhModal · WhDrawer</code>
        </header>
        <div class="dev-card">
          <div class="dev-row">
            <WhButton @click="modalOpen = true">打开 Modal</WhButton>
            <WhButton variant="secondary" @click="drawerOpen = true">打开 Drawer</WhButton>
          </div>
        </div>
      </section>
    </div>

    <WhModal v-model="modalOpen" title="创建专栏" size="md">
      <div class="dev-stack">
        <div>
          <label class="dev-label" for="modal-title">专栏名</label>
          <WhInput id="modal-title" v-model="modalTitle" placeholder="例如：Spring 深入浅出" />
        </div>
        <div>
          <label class="dev-label" for="modal-desc">简介</label>
          <WhTextarea
            id="modal-desc"
            v-model="modalDescription"
            placeholder="介绍一下这个专栏…"
            :rows="3"
            :maxlength="200"
            show-count
          />
        </div>
      </div>
      <template #footer>
        <WhButton variant="ghost" @click="modalOpen = false">取消</WhButton>
        <WhButton @click="modalOpen = false">创建</WhButton>
      </template>
    </WhModal>

    <WhDrawer v-model="drawerOpen" title="筛选" width="420px">
      <div class="dev-stack">
        <p class="dev-note">Drawer 复用同一套焦点陷阱与滚动锁定。</p>
        <WhSelect v-model="select" :options="selectOptions" clearable hint="按标签筛选" />
        <WhCheckbox v-model="checked" label="只看已解决" />
        <WhCheckbox v-model="unchecked" label="只看关注的人" />
      </div>
      <template #footer>
        <WhButton variant="ghost" @click="drawerOpen = false">重置</WhButton>
        <WhButton @click="drawerOpen = false">应用</WhButton>
      </template>
    </WhDrawer>
  </div>
</template>

<style scoped>
.dev {
  padding-block: clamp(32px, 5vw, 64px) var(--space-16);
}

.dev__head {
  padding-bottom: var(--space-8);
  border-bottom: var(--border);
}

.dev__eyebrow {
  color: var(--verm);
  font-family: var(--mono);
  font-size: var(--text-xs);
  letter-spacing: var(--tracking-wider);
}

.dev__title {
  margin-top: var(--space-3);
  font-size: var(--text-3xl);
  letter-spacing: var(--tracking-tight);
}

.dev__lede {
  max-width: 56ch;
  margin-top: var(--space-4);
  color: var(--text-muted);
  line-height: var(--leading-relaxed);
}

.dev__head-actions {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-2);
  margin-top: var(--space-5);
}

.dev__nav {
  position: sticky;
  top: var(--header-h);
  z-index: var(--z-sticky);
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-2);
  padding-block: var(--space-3);
  border-bottom: var(--border);
  background: color-mix(in srgb, var(--bone) 92%, transparent);
  backdrop-filter: blur(10px);
  -webkit-backdrop-filter: blur(10px);
}

.dev__nav a {
  padding: 6px 10px;
  border: 1px solid var(--line-strong);
  font-family: var(--mono);
  font-size: var(--text-2xs);
  letter-spacing: 0.08em;
}

.dev__nav a:hover {
  background: var(--ink);
  color: var(--acid);
}

.dev-section {
  padding-block: clamp(28px, 4vw, 48px);
  border-bottom: var(--border);
  scroll-margin-top: calc(var(--header-h) + 56px);
}

.dev-section__head {
  display: flex;
  flex-wrap: wrap;
  align-items: baseline;
  justify-content: space-between;
  gap: var(--space-2) var(--space-4);
  margin-bottom: var(--space-5);
}

.dev-section__head h2 {
  font-size: var(--text-xl);
}

.dev-section__head code {
  color: var(--text-muted);
  font-family: var(--mono);
  font-size: var(--text-2xs);
}

.dev-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(min(280px, 100%), 1fr));
  gap: var(--space-4);
}

.dev-card {
  padding: var(--space-4);
  border: var(--border);
  background: var(--surface);
}

.dev-card h3 {
  margin-bottom: var(--space-3);
  color: var(--text-muted);
  font-family: var(--mono);
  font-size: var(--text-2xs);
  font-weight: var(--weight-regular);
  letter-spacing: 0.08em;
}

.dev-row {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: var(--space-3);
}

.dev-row--spaced {
  margin-top: var(--space-3);
}

.dev-stack {
  display: grid;
  gap: var(--space-3);
}

.dev-label {
  display: block;
  margin-bottom: var(--space-2);
  color: var(--text-muted);
  font-family: var(--mono);
  font-size: var(--text-2xs);
  letter-spacing: 0.08em;
}

.dev-note {
  margin-top: var(--space-3);
  color: var(--text-muted);
  font-family: var(--mono);
  font-size: var(--text-2xs);
  line-height: 1.7;
}

.dev-skeleton-row {
  display: flex;
  align-items: center;
  gap: var(--space-3);
}

.dev-skeleton-lines {
  display: grid;
  flex: 1;
  gap: 8px;
}

.dev-markdown {
  max-height: 280px;
  overflow: auto;
  padding-right: var(--space-2);
}

.dev-markdown :deep(h1) {
  margin-top: 0;
  font-size: var(--text-xl);
}

.dev-markdown :deep(h2) {
  font-size: var(--text-lg);
}
</style>
