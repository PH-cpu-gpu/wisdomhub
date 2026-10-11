import { get } from './http'

/**
 * 标签（B1.24）。
 *
 * 只读：增/改/删三个接口后端**刻意注释掉了**（普通用户只能查，见 TagController），
 * 所以前端不要指望能建标签 —— 发布文章时的 `tagIds` 只能从下面这份里选。
 */
export interface Tag {
  tagId: string
  name: string
  /** 展示分组，中文。顺序见 config/tag-categories.ts */
  category: string
  description: string
}

/** ⚠️ 与 TagVO 的差别：热门标签**不返回 description**，多一个 articleCount（后端 HotTagVO） */
export interface HotTag {
  tagId: string
  name: string
  articleCount: number
}

/** 全量标签（走两级缓存：Caffeine + Redis） */
export function listTags() {
  return get<Tag[]>('/content/tag')
}

/** 热门标签，limit 上限 50（后端会钳制，传超了不报错） */
export function listHotTags(limit = 10) {
  return get<HotTag[]>('/content/tag/hot', { limit })
}
