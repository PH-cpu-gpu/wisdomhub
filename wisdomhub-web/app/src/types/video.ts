import type { ID, PageQuery } from './common'

/** 与后端 MediaAssetConstants 对齐 */
export const MEDIA_ASSET_STATUS = {
  UPLOADING: 1,
  UPLOADED: 2,
  TRANSCODING: 3,
  FAILED: 4,
  REVIEWING: 5,
  REJECTED: 6,
  READY: 7,
} as const

export type MediaAssetStatusType = (typeof MEDIA_ASSET_STATUS)[keyof typeof MEDIA_ASSET_STATUS]

/** 各状态的展示文案，页面与状态机共用 */
export const MEDIA_ASSET_STATUS_LABEL: Record<number, string> = {
  [MEDIA_ASSET_STATUS.UPLOADING]: '待上传',
  [MEDIA_ASSET_STATUS.UPLOADED]: '已上传',
  [MEDIA_ASSET_STATUS.TRANSCODING]: '转码中',
  [MEDIA_ASSET_STATUS.FAILED]: '处理失败',
  [MEDIA_ASSET_STATUS.REVIEWING]: '审核中',
  [MEDIA_ASSET_STATUS.REJECTED]: '审核未通过',
  [MEDIA_ASSET_STATUS.READY]: '可发布',
}

export interface UploadTicketVO {
  assetId: ID
  objectKey: string
  uploadUrl: string
  method: string
  /** 直传时必须原样携带的请求头 */
  headers: Record<string, string>
  /** UTC */
  expiresAt: string
}

export interface MediaAssetStatusVO {
  assetId: ID
  status: MediaAssetStatusType
  fileSize: number
  durationMs: number | null
  width: number | null
  height: number | null
  coverKey: string | null
  failReason: string | null
}

export interface VideoPublishPayload {
  assetId: ID
  title: string
  summary?: string
  /** 最多 10 个，必须是 content 标签库已存在的标签 */
  tagIds?: ID[]
}

export interface VideoTagVO {
  tagId: ID
  name: string
  category: string
}

/** 视频详情：播放地址、封面、标签都由服务端组装好 */
export interface VideoDetailVO {
  videoId: ID
  authorId: ID
  title: string
  summary: string | null
  coverKey: string | null
  coverUrl: string
  /** 相对路径 /api/video/{id}/playlist，播放器负责拼 API 基址 */
  playlistUrl: string
  durationMs: number | null
  width: number | null
  height: number | null
  viewCount: number
  /** 1-已发布 2-已下架 3-已删除 */
  status: number
  publishTime: string
  tags: VideoTagVO[]
}

/** 公开视频列表项：只返回已发布，未 READY 的资产不会出现在这里 */
export interface VideoListItemVO {
  videoId: ID
  authorId: ID
  title: string
  summary: string | null
  coverKey: string | null
  coverUrl: string
  durationMs: number | null
  viewCount: number
  publishTime: string
  tags: VideoTagVO[]
}

/** 我的视频：比公开列表多 status / createTime，不含作者 */
export interface MyVideoVO {
  videoId: ID
  title: string
  summary: string | null
  coverKey: string | null
  coverUrl: string
  durationMs: number | null
  viewCount: number
  /** 1-已发布 2-已下架 3-已删除 */
  status: number
  publishTime: string
  createTime: string
  tags: VideoTagVO[]
}

export interface VideoListQuery extends PageQuery {
  authorId?: ID
  tagId?: ID
}

/** 断点续传记录：跨刷新只认同一份文件（name+size+lastModified） */
export interface PendingUpload {
  fingerprint: string
  assetId: ID
  objectKey: string
  uploadUrl: string
  headers: Record<string, string>
  expiresAt: string
  fileSize: number
  mimeType: string
  /** 已 PUT 成功：续传时跳过直传，只做确认与轮询 */
  uploaded: boolean
  /** 已转码完成、等待发布；刷新后直接恢复发布表单 */
  ready?: boolean
}

/** 与后端 VideoMimeTypes 白名单一致 */
export const ACCEPTED_VIDEO_TYPES = [
  { mime: 'video/mp4', extension: 'mp4', label: 'MP4' },
  { mime: 'video/quicktime', extension: 'mov', label: 'MOV' },
  { mime: 'video/webm', extension: 'webm', label: 'WebM' },
  { mime: 'video/x-matroska', extension: 'mkv', label: 'MKV' },
] as const

export const ACCEPT_ATTRIBUTE = ACCEPTED_VIDEO_TYPES.map(
  (item) => `${item.mime},.${item.extension}`,
).join(',')

/** 后端 UploadProperties / VideoQuotaProperties 的默认值，仅用于前置校验 */
export const MAX_VIDEO_SIZE_BYTES = 2 * 1024 * 1024 * 1024
export const MAX_VIDEO_DURATION_SECONDS = 30 * 60
export const DAILY_UPLOAD_LIMIT = 10

export function acceptedMimeOf(file: File): string | null {
  const mime = (file.type || '').toLowerCase()
  if (ACCEPTED_VIDEO_TYPES.some((item) => item.mime === mime)) {
    return mime
  }
  // 某些系统给不出 MIME，用扩展名兜底
  const extension = file.name.split('.').pop()?.toLowerCase() ?? ''
  const matched = ACCEPTED_VIDEO_TYPES.find((item) => item.extension === extension)
  return matched ? matched.mime : null
}
