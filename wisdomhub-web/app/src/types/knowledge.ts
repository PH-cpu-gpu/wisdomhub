import type { ID } from './common'

/** 图谱里的技术节点摘要，只有身份与名字 */
export interface KnowledgeTagBriefVO {
  tagId: ID
  name: string
}

/** 一种关系类型的邻居：out = 我指向对方，in = 对方指向我 */
export interface RelationGroupVO {
  out: KnowledgeTagBriefVO[]
  in: KnowledgeTagBriefVO[]
}

export interface TagDetailVO {
  tagId: ID
  name: string
  category: string
  description: string
  relations: Record<string, RelationGroupVO>
}

export interface TagRelationsVO {
  tagId: ID
  name: string
  relations: Record<string, RelationGroupVO>
}

export interface PrerequisiteLevelVO {
  depth: number
  tags: KnowledgeTagBriefVO[]
}

export interface PrerequisiteTreeVO {
  tagId: ID
  name: string
  depth: number
  levels: PrerequisiteLevelVO[]
  truncated: boolean
}

export type GraphNodeType = 'TAG' | 'ARTICLE'

export type GraphRelation =
  'BASED_ON' | 'USES' | 'PREREQUISITE' | 'SIMILAR_TO' | 'BELONGS_TO' | 'ABOUT'

export interface GraphNodeVO {
  id: ID
  type: GraphNodeType
  label: string
  /** tag 表的中文分组名，前端着色按它走 */
  category: string | null
  /** Tag→Tag 邻居数；文章节点为 null */
  degree: number | null
}

export interface GraphEdgeVO {
  source: ID
  target: ID
  relation: GraphRelation
}

export interface GraphVO {
  nodes: GraphNodeVO[]
  edges: GraphEdgeVO[]
  articlesTruncated: boolean
}
