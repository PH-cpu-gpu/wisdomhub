<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { GraphChart } from 'echarts/charts'
import { LegendComponent, TooltipComponent } from 'echarts/components'
import * as echarts from 'echarts/core'
import type { ECharts, EChartsCoreOption } from 'echarts/core'
import { CanvasRenderer } from 'echarts/renderers'

import type { GraphEdgeVO, GraphNodeVO, GraphVO } from '@/types/knowledge'

echarts.use([GraphChart, LegendComponent, TooltipComponent, CanvasRenderer])

const CATEGORY_COLORS: Record<string, string> = {
  人工智能与数据: '#7a8f2a',
  编程语言: '#c9e63f',
  中间件与运维: '#ff6b4a',
  后端与框架: '#7c5cff',
  数据库: '#2f9e8f',
  前端: '#e0a800',
  架构与分布式: '#4a6fa5',
  移动与嵌入式: '#b05a9b',
  计算机基础: '#8a8a93',
  开发实践: '#d97642',
}
const ARTICLE_COLOR = '#d6d6cf'
const FALLBACK_COLOR = '#4a4a52'
/** 待审提案统一用这个颜色（与 verm 系一致，但写死给 ECharts 用） */
const PENDING_COLOR = '#ff6b4a'

const props = withDefaults(
  defineProps<{
    data: GraphVO
    /** 待审关系：虚线预览，不归属任何分类 */
    pendingEdges?: GraphEdgeVO[]
    /** 参与待审关系的节点 ID，描边改为虚线提醒 */
    pendingTagIds?: string[]
  }>(),
  {
    pendingEdges: () => [],
    pendingTagIds: () => [],
  },
)

const emit = defineEmits<{
  nodeClick: [node: { id: string; type: 'TAG' | 'ARTICLE'; label: string }]
  backgroundClick: []
}>()

const chartRef = ref<HTMLElement | null>(null)
let chart: ECharts | null = null
let observer: ResizeObserver | null = null

const showLabels = computed(() => props.data.nodes.length <= 300)

function buildOption(): EChartsCoreOption {
  const pendingTagIds = new Set(props.pendingTagIds)
  const categories = [
    ...new Set(
      props.data.nodes
        .map((node) => node.category)
        .filter((category): category is string => Boolean(category)),
    ),
  ]

  const nodes = props.data.nodes.map((node) => {
    const isTag = node.type === 'TAG'
    const color = isTag ? (CATEGORY_COLORS[node.category ?? ''] ?? FALLBACK_COLOR) : ARTICLE_COLOR
    const pending = pendingTagIds.has(node.id)

    return {
      id: node.id,
      name: node.label,
      category: isTag ? categories.indexOf(node.category ?? '') : undefined,
      symbolSize: isTag ? 16 + Math.min(32, (node.degree ?? 0) * 4) : 11,
      itemStyle: pending
        ? { color, borderColor: PENDING_COLOR, borderWidth: 2, borderType: 'dashed' }
        : { color },
      nodeType: node.type,
      degree: node.degree,
      pending,
    }
  })

  const links = [
    ...props.data.edges.map((edge) => ({
      source: edge.source,
      target: edge.target,
      relation: edge.relation,
      pending: false,
    })),
    ...props.pendingEdges.map((edge) => ({
      source: edge.source,
      target: edge.target,
      relation: edge.relation,
      pending: true,
      lineStyle: {
        type: 'dashed',
        color: PENDING_COLOR,
        opacity: 0.9,
        width: 1.4,
        curveness: 0.12,
      },
    })),
  ]

  return {
    backgroundColor: 'transparent',
    tooltip: {
      trigger: 'item',
      confine: true,
      backgroundColor: '#0b0b0e',
      borderWidth: 0,
      textStyle: { color: '#f4f4ef', fontSize: 12 },
      formatter: (params: { dataType?: string; data?: Record<string, unknown> }) => {
        if (params.dataType === 'node' && params.data) {
          const kind = params.data.nodeType === 'TAG' ? '技术' : '文章'
          const degree = params.data.nodeType === 'TAG' ? ` · 连接 ${params.data.degree ?? 0}` : ''
          const pending = params.data.pending ? ' · 有申请中的关系' : ''
          return `<strong>${params.data.name}</strong><br/>${kind}${degree}${pending}`
        }
        if (params.dataType === 'edge' && params.data) {
          const suffix = params.data.pending ? '（申请中）' : ''
          return `${String(params.data.relation ?? '')}${suffix}`
        }
        return ''
      },
    },
    legend: {
      type: 'scroll',
      bottom: 14,
      left: 16,
      right: 16,
      icon: 'circle',
      itemWidth: 10,
      itemHeight: 10,
      textStyle: { fontFamily: 'monospace', fontSize: 11, color: '#55555c' },
      data: categories,
    },
    series: [
      {
        type: 'graph',
        layout: 'force',
        roam: true,
        draggable: true,
        categories: categories.map((category) => ({
          name: category,
          itemStyle: { color: CATEGORY_COLORS[category] ?? FALLBACK_COLOR },
        })),
        data: nodes,
        links,
        edgeSymbol: ['none', 'arrow'],
        edgeSymbolSize: 6,
        force: {
          repulsion: 320,
          edgeLength: [90, 180],
          gravity: 0.08,
        },
        label: {
          show: showLabels.value,
          position: 'right',
          fontSize: 11,
          color: '#3a3a40',
          formatter: '{b}',
        },
        labelLayout: { hideOverlap: true },
        lineStyle: { color: 'source', opacity: 0.35, width: 1, curveness: 0.12 },
        emphasis: {
          focus: 'adjacency',
          lineStyle: { width: 2, opacity: 0.8 },
        },
        selectedMode: 'single',
        itemStyle: { borderColor: '#f4f4ef', borderWidth: 1 },
      },
    ],
  }
}

function render(): void {
  if (!chart) return
  chart.setOption(buildOption(), true)
}

function onClick(params: { dataType?: string; data?: unknown }): void {
  if (params.dataType !== 'node' || !params.data || typeof params.data !== 'object') return

  const data = params.data as Record<string, unknown>
  emit('nodeClick', {
    id: String(data.id ?? ''),
    type: data.nodeType === 'ARTICLE' ? 'ARTICLE' : 'TAG',
    label: String(data.name ?? ''),
  })
}

function onBackgroundClick(event: { target?: unknown }): void {
  if (!event.target) emit('backgroundClick')
}

function emitNode(node: GraphNodeVO): void {
  emit('nodeClick', { id: node.id, type: node.type, label: node.label })
}

function initChart(): void {
  if (!chartRef.value) return

  chart = echarts.init(chartRef.value)
  chart.on('click', onClick)
  chart.getZr().on('click', onBackgroundClick)
  observer = new ResizeObserver(() => chart?.resize())
  observer.observe(chartRef.value)
  render()
}

onMounted(initChart)

onBeforeUnmount(() => {
  observer?.disconnect()
  observer = null
  chart?.getZr().off('click', onBackgroundClick)
  chart?.dispose()
  chart = null
})

watch(() => [props.data, props.pendingEdges, props.pendingTagIds], render, { deep: true })
</script>

<template>
  <div
    class="graph-chart"
    :data-labels="showLabels ? 'on' : 'off'"
    :data-pending-edges="pendingEdges.length"
    :data-pending-nodes="pendingTagIds.length"
  >
    <div ref="chartRef" class="graph-chart__canvas" role="img" aria-label="知识图谱力导向图"></div>
    <ul class="graph-chart__nodes" aria-label="图谱节点">
      <li v-for="node in data.nodes" :key="node.id">
        <button
          type="button"
          :aria-label="`${node.type === 'TAG' ? '技术' : '文章'}：${node.label}`"
          @click.stop="emitNode(node)"
        >
          {{ node.label }}
        </button>
      </li>
    </ul>
  </div>
</template>

<style scoped>
.graph-chart {
  position: absolute;
  inset: 0;
}

.graph-chart__canvas {
  position: absolute;
  inset: 0;
}

.graph-chart__nodes {
  position: absolute;
  width: 1px;
  height: 1px;
  margin: -1px;
  padding: 0;
  overflow: hidden;
  clip: rect(0 0 0 0);
  white-space: nowrap;
  border: 0;
}
</style>
