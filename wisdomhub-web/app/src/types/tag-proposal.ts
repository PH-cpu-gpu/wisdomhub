import type { ID, PageQuery } from './common'

/** 1-新增标签 2-新增关系 */
export type TagProposalType = 1 | 2

/** 0-待审 1-已通过 2-已拒绝 3-已过期 */
export type TagProposalStatus = 0 | 1 | 2 | 3

/** 1-AI 2-管理员 3-系统 */
export type TagProposerType = 1 | 2 | 3

/** 与后端 TagRelationConstants 的白名单一致 */
export type TagRelationType =
  | 'PREREQUISITE'
  | 'BASED_ON'
  | 'USES'
  | 'SIMILAR_TO'
  | 'BELONGS_TO'

/** 标签 / 关系提案；标签类填 name/category/description，关系类填 fromTagId/toTagId/relationType */
export interface TagProposalVO {
  id: ID
  proposalType: TagProposalType
  status: TagProposalStatus
  /** 去重键：标签=归一化名；关系=from:type:to */
  targetKey: string
  name?: string | null
  category?: string | null
  description?: string | null
  fromTagId?: ID | null
  toTagId?: ID | null
  relationType?: TagRelationType | null
  proposerType: TagProposerType
  proposerId?: ID | null
  decidedBy?: ID | null
  decidedAt?: string | null
  createTime: string
  /** 触发证据：unknown_entity 升级时带 extractCount / queryCount / sampleArticleId */
  evidence?: Record<string, unknown> | null
}

export interface TagProposalQuery extends PageQuery {
  status?: TagProposalStatus
  proposalType?: TagProposalType
}
