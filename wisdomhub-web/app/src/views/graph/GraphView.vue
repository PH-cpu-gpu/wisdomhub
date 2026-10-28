<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { RouterLink, useRoute, useRouter } from 'vue-router'

import { isApiError } from '@/api/error'
import { graph, neighborhood } from '@/api/modules/knowledge'
import WhButton from '@/components/base/WhButton.vue'
import WhSelect from '@/components/base/WhSelect.vue'
import { toast } from '@/components/base/toast'
import GraphCanvas from '@/components/business/GraphCanvas.vue'
import GraphNodePanel from '@/components/business/GraphNodePanel.vue'
import { useSeo } from '@/composables/useSeo'
import type { GraphVO } from '@/types/knowledge'

const route = useRoute()
const router = useRouter()

const data = ref<GraphVO | null>(null)
const loading = ref(false)
const error = ref('')
const panelOpen = ref(false)
const panelTagId = ref('')
const expandedArticleTags = ref(new Set<string>())

const tagId = computed(() => String(route.params.tagId ?? route.query.tagId ?? ''))
const isNeighborhood = computed(() => Boolean(tagId.value))
const depth = computed(() => {
  const parsed = Number(route.query.depth)
  return Number.isFinite(parsed) && parsed >= 1 ? Math.min(2, Math.floor(parsed)) : 1
})
const articleLimit = computed(() => {
  const parsed = Number(route.query.articleLimit)
  return [10, 20, 50].includes(parsed) ? parsed : 20
})
const depthOptions = [
  { label: '1 层', value: 1 },
  { label: '2 层', value: 2 },
]
const articleLimitOptions = [
  { label: '10 篇', value: 10 },
  { label: '20 篇', value: 20 },
  { label: '50 篇', value: 50 },
]
const depthModel = computed({
  get: () => depth.value,
  set: (value: string | number | null) => {
    const next = Number(value)
    void router.push({
      query: {
        ...route.query,
        depth: next === 1 ? undefined : String(next),
      },
    })
  },
})
const articleLimitModel = computed({
  get: () => articleLimit.value,
  set: (value: string | number | null) => {
    const next = Number(value)
    void router.push({
      query: {
        ...route.query,
        articleLimit: next === 20 ? undefined : String(next),
      },
    })
  },
})
const nodeCount = computed(() => data.value?.nodes.length ?? 0)
const edgeCount = computed(() => data.value?.edges.length ?? 0)
const categoryCount = computed(
  () => new Set(data.value?.nodes.map((node) => node.category).filter(Boolean)).size,
)

async function load(): Promise<void> {
  loading.value = true
  error.value = ''
  data.value = null

  try {
    data.value = tagId.value
      ? await neighborhood(
          tagId.value,
          { depth: depth.value, articleLimit: articleLimit.value },
          { skipErrorToast: true },
        )
      : await graph({ skipErrorToast: true })
  } catch (err) {
    data.value = null
    error.value = isApiError(err) ? err.message : '图谱加载失败，请稍后重试。'
  } finally {
    loading.value = false
  }
}

function onNodeClick(node: { id: string; type: 'TAG' | 'ARTICLE'; label: string }): void {
  if (node.type === 'TAG') {
    panelTagId.value = node.id
    panelOpen.value = true
    return
  }
  if (node.id.startsWith('article-')) {
    void router.push({
      name: 'article-detail',
      params: { articleId: node.id.slice('article-'.length) },
    })
  }
}

function goNeighborhood(tagId: string): void {
  void router.push({ name: 'graph-tag', params: { tagId } })
}

async function expandArticles(tagId: string): Promise<void> {
  if (!data.value || expandedArticleTags.value.has(tagId)) return

  try {
    const extra = await neighborhood(
      tagId,
      { articleLimit: articleLimit.value },
      { skipErrorToast: true },
    )
    const current = data.value
    const currentIds = new Set(current.nodes.map((node) => node.id))
    const addedNodes = extra.nodes.filter(
      (node) => node.type === 'ARTICLE' && !currentIds.has(node.id),
    )

    if (!addedNodes.length) {
      expandedArticleTags.value.add(tagId)
      return
    }

    const addedIds = new Set(addedNodes.map((node) => node.id))
    const currentEdgeKeys = new Set(
      current.edges.map((edge) => `${edge.source}-${edge.target}-${edge.relation}`),
    )
    const addedEdges = extra.edges.filter(
      (edge) =>
        (addedIds.has(edge.source) || addedIds.has(edge.target)) &&
        !currentEdgeKeys.has(`${edge.source}-${edge.target}-${edge.relation}`),
    )

    data.value = {
      ...current,
      nodes: [...current.nodes, ...addedNodes],
      edges: [...current.edges, ...addedEdges],
    }
    expandedArticleTags.value.add(tagId)
    toast.success('已展开文章节点')
  } catch (err) {
    toast.error(isApiError(err) ? err.message : '文章节点展开失败，请稍后重试。')
  }
}

function goOverview(): void {
  if (route.name !== 'graph') void router.push({ name: 'graph' })
}

function onBackgroundClick(): void {
  if (isNeighborhood.value) goOverview()
}

watch(
  () => route.fullPath,
  () => void load(),
  { immediate: true },
)

useSeo({
  title: '知识图谱',
  description: '技术关系总览、邻域展开与相关文章。',
})
</script>

<template>
  <div class="graph-page">
    <header class="graph-head">
      <div class="graph-head__title">
        <div class="graph-head__meta">
          <p class="graph-eyebrow">
            [ KNOWLEDGE / GRAPH / {{ isNeighborhood ? 'NEIGHBORHOOD' : 'OVERVIEW' }} ]
          </p>
          <button v-if="isNeighborhood" class="graph-back" type="button" @click="goOverview">
            ← 返回总览
          </button>
        </div>
        <h1>{{ isNeighborhood ? '技术邻域' : '知识图谱' }}</h1>
        <p class="graph-lede">
          {{
            isNeighborhood
              ? '展示该技术的一跳邻居与相关文章。'
              : '把技术之间的依赖、使用与相似关系连成一张图。'
          }}
        </p>
      </div>
      <div v-if="data && !loading && !error" class="graph-stats" aria-label="图谱规模">
        <span
          ><b>{{ nodeCount }}</b> 节点</span
        >
        <span
          ><b>{{ edgeCount }}</b> 条边</span
        >
        <span
          ><b>{{ categoryCount }}</b> 个分类</span
        >
      </div>
    </header>

    <section v-if="isNeighborhood" class="graph-controls" aria-label="邻域设置">
      <span class="graph-controls__label">[ NEIGHBORHOOD SETTINGS ]</span>
      <label class="graph-controls__field">
        <span>深度</span>
        <WhSelect v-model="depthModel" :options="depthOptions" size="sm" />
      </label>
      <label class="graph-controls__field">
        <span>文章</span>
        <WhSelect v-model="articleLimitModel" :options="articleLimitOptions" size="sm" />
      </label>
    </section>

    <div v-if="loading" class="graph-canvas graph-canvas--state" aria-live="polite">
      <div class="graph-loading">
        <span class="graph-loading__dot" aria-hidden="true"></span>
        正在加载图谱…
      </div>
    </div>

    <div v-else-if="error" class="graph-canvas graph-canvas--state" role="alert">
      <div class="graph-message graph-message--error">
        <p class="graph-message__eyebrow">[ GRAPH ERROR ]</p>
        <h2>图谱没能加载</h2>
        <p>{{ error }}</p>
        <WhButton size="sm" @click="load">重试</WhButton>
      </div>
    </div>

    <div v-else-if="!nodeCount" class="graph-canvas graph-canvas--state">
      <div class="graph-message">
        <p class="graph-message__eyebrow">[ EMPTY ]</p>
        <h2>暂无图谱数据</h2>
        <p>等技术标签与关系建立后，这里会出现总览图。</p>
        <RouterLink class="graph-link" :to="{ name: 'tag-list' }">去标签库看看</RouterLink>
      </div>
    </div>

    <div v-else class="graph-canvas">
      <div class="graph-canvas__grid" aria-hidden="true"></div>

      <GraphCanvas
        v-if="data"
        :data="data"
        @node-click="onNodeClick"
        @background-click="onBackgroundClick"
      />

      <div class="graph-canvas__hud">
        <span>{{ isNeighborhood ? 'NEIGHBORHOOD' : 'TOTAL OVERVIEW' }}</span>
        <span>{{ nodeCount }} NODES / {{ edgeCount }} EDGES</span>
      </div>

      <p v-if="nodeCount > 300" class="graph-density">
        节点数 {{ nodeCount }} &gt; 300，已自动降低标签密度。
      </p>

      <p v-if="data?.articlesTruncated" class="graph-truncated">文章已截断，仅展示前 20 篇。</p>

      <aside class="graph-mobile-hint">
        拖拽 / 缩放查看图谱；点击技术节点查看详情，点击空白返回总览。
      </aside>
    </div>

    <GraphNodePanel
      v-model="panelOpen"
      :tag-id="panelTagId"
      :articles-expanded="expandedArticleTags.has(panelTagId)"
      @neighborhood="goNeighborhood"
      @expand-articles="expandArticles"
    />
  </div>
</template>

<style scoped>
.graph-page {
  display: grid;
  gap: var(--space-4);
  width: 100%;
  padding: clamp(16px, 3vw, 32px);
}

.graph-head {
  display: flex;
  flex-wrap: wrap;
  align-items: flex-end;
  justify-content: space-between;
  gap: var(--space-4);
  padding-bottom: var(--space-3);
  border-bottom: 1px solid var(--ink);
}

.graph-head__title {
  display: grid;
  gap: var(--space-2);
}

.graph-head__meta {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: var(--space-3);
}

.graph-eyebrow {
  color: var(--verm-deep);
  font-family: var(--mono);
  font-size: var(--text-2xs);
  letter-spacing: var(--tracking-wider);
}

.graph-back {
  padding: 0;
  border: 0;
  background: transparent;
  color: var(--verm-deep);
  font-family: var(--mono);
  font-size: var(--text-2xs);
  text-decoration: underline;
  text-underline-offset: 3px;
  cursor: pointer;
}

.graph-head h1 {
  font-family: var(--serif);
  font-size: clamp(2rem, 4vw, 3rem);
  font-weight: var(--weight-black);
  line-height: var(--leading-tight);
  letter-spacing: var(--tracking-tight);
}

.graph-lede {
  color: var(--text-muted);
  font-size: var(--text-sm);
}

.graph-stats {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-4);
  color: var(--text-muted);
  font-family: var(--mono);
  font-size: var(--text-2xs);
}

.graph-stats b {
  color: var(--ink);
  font-size: var(--text-base);
}

.graph-controls {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: var(--space-3);
  padding: var(--space-2) var(--space-3);
  border: var(--border);
  background: var(--surface);
}

.graph-controls__label {
  margin-right: auto;
  color: var(--text-muted);
  font-family: var(--mono);
  font-size: var(--text-2xs);
  letter-spacing: var(--tracking-wide);
}

.graph-controls__field {
  display: inline-flex;
  align-items: center;
  gap: var(--space-2);
  color: var(--text-muted);
  font-family: var(--mono);
  font-size: var(--text-2xs);
}

.graph-canvas {
  position: relative;
  min-height: 520px;
  height: clamp(540px, calc(100dvh - 250px), 920px);
  overflow: hidden;
  border: 1px solid var(--ink);
  background:
    radial-gradient(circle at 20% 15%, rgb(212 255 58 / 16%), transparent 36%), var(--surface);
}

.graph-canvas--state {
  display: grid;
  place-items: center;
  height: clamp(360px, calc(100dvh - 300px), 620px);
  min-height: 360px;
}

.graph-canvas__grid {
  position: absolute;
  inset: 0;
  background-image:
    linear-gradient(var(--line) 1px, transparent 1px),
    linear-gradient(90deg, var(--line) 1px, transparent 1px);
  background-size: 32px 32px;
  opacity: 0.55;
}

.graph-canvas__hud {
  position: absolute;
  top: var(--space-3);
  right: var(--space-3);
  left: var(--space-3);
  z-index: 2;
  display: flex;
  justify-content: space-between;
  gap: var(--space-3);
  color: var(--text-muted);
  font-family: var(--mono);
  font-size: var(--text-2xs);
  letter-spacing: var(--tracking-wide);
}

.graph-mobile-hint {
  position: absolute;
  right: var(--space-3);
  bottom: var(--space-3);
  left: var(--space-3);
  z-index: 2;
  padding: var(--space-2) var(--space-3);
  border: 1px solid var(--line-strong);
  background: var(--surface);
  color: var(--text-muted);
  font-family: var(--mono);
  font-size: var(--text-2xs);
}

.graph-truncated {
  position: absolute;
  top: 52px;
  right: var(--space-3);
  z-index: 2;
  padding: 4px 8px;
  border: 1px solid var(--verm);
  background: var(--surface);
  color: var(--verm-deep);
  font-family: var(--mono);
  font-size: var(--text-2xs);
}

.graph-density {
  position: absolute;
  top: 84px;
  right: var(--space-3);
  z-index: 2;
  padding: 4px 8px;
  border: 1px solid var(--line-strong);
  background: var(--surface);
  color: var(--text-muted);
  font-family: var(--mono);
  font-size: var(--text-2xs);
}

.graph-message {
  display: grid;
  justify-items: center;
  gap: var(--space-2);
  max-width: 440px;
  padding: var(--space-5);
  text-align: center;
}

.graph-message__eyebrow {
  color: var(--verm-deep);
  font-family: var(--mono);
  font-size: var(--text-2xs);
  letter-spacing: var(--tracking-wider);
}

.graph-message h2 {
  font-family: var(--serif);
  font-size: var(--text-2xl);
  font-weight: var(--weight-black);
}

.graph-message p {
  color: var(--text-muted);
  font-size: var(--text-sm);
}

.graph-message--error {
  color: var(--verm-deep);
}

.graph-link {
  color: var(--verm-deep);
  font-family: var(--mono);
  font-size: var(--text-2xs);
  text-decoration: underline;
  text-underline-offset: 3px;
}

.graph-loading {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  color: var(--text-muted);
  font-family: var(--mono);
  font-size: var(--text-xs);
}

.graph-loading__dot {
  width: 8px;
  height: 8px;
  background: var(--acid);
  border: 1px solid var(--ink);
  animation: graph-pulse 1.1s ease-in-out infinite;
}

@keyframes graph-pulse {
  0%,
  100% {
    transform: scale(0.8);
    opacity: 0.55;
  }

  50% {
    transform: scale(1.15);
    opacity: 1;
  }
}

@media (min-width: 961px) {
  .graph-mobile-hint {
    display: none;
  }
}

@media (max-width: 640px) {
  .graph-canvas {
    min-height: 420px;
    height: calc(100dvh - 220px);
  }
}
</style>
