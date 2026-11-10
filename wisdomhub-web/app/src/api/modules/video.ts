import { get, post, type RequestOptions } from '@/api/http'
import type { ID, PageQuery, PageResponse } from '@/types/common'
import type {
  MediaAssetStatusVO,
  MyVideoVO,
  UploadTicketVO,
  VideoDetailVO,
  VideoListItemVO,
  VideoListQuery,
  VideoPublishPayload,
} from '@/types/video'

/** 申请直传凭证；objectKey 与预签名 URL 都由服务端生成 */
export function requestUploadTicket(
  payload: { fileSize: number; mimeType: string },
  options: RequestOptions = {},
): Promise<UploadTicketVO> {
  return post<UploadTicketVO>('/api/video/upload-ticket', payload, options)
}

/** 直传完成确认：服务端 HEAD 校验后置 UPLOADED 并提交转码 */
export function completeUpload(
  assetId: ID,
  options: RequestOptions = {},
): Promise<MediaAssetStatusVO> {
  return post<MediaAssetStatusVO>(`/api/video/assets/${assetId}/complete`, undefined, options)
}

/** 轮询资产状态（仅所有者） */
export function getAsset(assetId: ID, options: RequestOptions = {}): Promise<MediaAssetStatusVO> {
  return get<MediaAssetStatusVO>(`/api/video/assets/${assetId}`, undefined, options)
}

/** 公开视频列表：只返回已发布，可按作者/标签筛选 */
export function list(
  params: VideoListQuery = {},
  options: RequestOptions = {},
): Promise<PageResponse<VideoListItemVO>> {
  return get<PageResponse<VideoListItemVO>>('/api/video', params, options)
}

/** 我的视频：含已下架，未 READY 的资产不在 video 表所以不会出现 */
export function mine(
  params: PageQuery = {},
  options: RequestOptions = {},
): Promise<PageResponse<MyVideoVO>> {
  return get<PageResponse<MyVideoVO>>('/api/video/mine', params, options)
}

/** 视频详情：未发布的视频仅作者可读 */
export function detail(videoId: ID, options: RequestOptions = {}): Promise<VideoDetailVO> {
  return get<VideoDetailVO>(`/api/video/${videoId}`, undefined, options)
}

/** 发布视频：只有 READY 的资产可以发布 */
export function publish(payload: VideoPublishPayload, options: RequestOptions = {}): Promise<ID> {
  return post<ID>('/api/video', payload, options)
}
