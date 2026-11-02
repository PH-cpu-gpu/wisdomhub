import type { TagProposalStatus, TagProposalType } from '@/types/tag-proposal'

export const PROPOSAL_STATUS_LABELS: Record<TagProposalStatus, string> = {
  0: '待审',
  1: '已通过',
  2: '已拒绝',
  3: '已过期',
}

export const PROPOSAL_TYPE_LABELS: Record<TagProposalType, string> = {
  1: '新增标签',
  2: '新增关系',
}

const PROPOSER_LABELS: Record<number, string> = {
  1: 'AI',
  2: '管理员',
  3: '系统',
}

const RELATION_TYPE_LABELS: Record<string, string> = {
  PREREQUISITE: '前置知识',
  BASED_ON: '基于',
  USES: '使用',
  SIMILAR_TO: '相似',
  BELONGS_TO: '属于',
}

const EVIDENCE_LABELS: Record<string, string> = {
  source: '来源',
  reason: '理由',
  extractCount: '命中文章数',
  queryCount: '查询次数',
  sampleArticleId: '样本文章',
  firstSeen: '首次出现',
  lastSeen: '最近出现',
}

const EVIDENCE_SOURCE_LABELS: Record<string, string> = {
  unknown_entity: '未收录实体升级',
  agent_chat: 'AI 会话内申请',
}

export interface EvidenceRow {
  key: string
  label: string
  value: string
}

export function proposerLabel(type: number | null | undefined): string {
  return (type && PROPOSER_LABELS[type]) || '未知'
}

export function relationTypeLabel(type?: string | null): string {
  if (!type) return '关系'
  return RELATION_TYPE_LABELS[type] ?? type
}

/** 证据逐条展开，未知字段直接用原 key，便于后端扩展 */
export function evidenceRows(evidence?: Record<string, unknown> | null): EvidenceRow[] {
  if (!evidence) return []
  return Object.entries(evidence)
    .filter(([, value]) => value !== null && value !== undefined && value !== '')
    .map(([key, value]) => ({
      key,
      label: EVIDENCE_LABELS[key] ?? key,
      value:
        key === 'source'
          ? (EVIDENCE_SOURCE_LABELS[String(value)] ?? String(value))
          : String(value),
    }))
}

/** 列表里的一行证据摘要 */
export function evidenceSummary(evidence?: Record<string, unknown> | null): string {
  if (!evidence) return ''

  const parts: string[] = []
  if (typeof evidence.extractCount === 'number') parts.push(`命中 ${evidence.extractCount} 篇文章`)
  if (typeof evidence.queryCount === 'number' && evidence.queryCount > 0) {
    parts.push(`查询 ${evidence.queryCount} 次`)
  }
  if (typeof evidence.reason === 'string' && evidence.reason) parts.push(evidence.reason)
  return parts.join(' · ')
}
