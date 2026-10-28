import { del, get, post, put, type RequestOptions } from '@/api/http'
import type { ID, PageQuery, PageResponse } from '@/types/common'
import type {
  BindEmailPayload,
  BrowseHistoryVO,
  ProfilePayload,
  UserBriefVO,
  UserProfileVO,
} from '@/types/user'

export function getProfile(userId: ID, options: RequestOptions = {}): Promise<UserProfileVO> {
  return get<UserProfileVO>(`/api/user/profile/${userId}`, undefined, options)
}

export function listUserBrief(userIds: ID[], options: RequestOptions = {}): Promise<UserBriefVO[]> {
  return get<UserBriefVO[]>('/api/user/profile/brief', { userIds }, options)
}

export function updateProfile(
  payload: ProfilePayload,
  options: RequestOptions = {},
): Promise<void> {
  return put<void>('/api/user/profile', payload, options)
}

export function bindEmail(payload: BindEmailPayload, options: RequestOptions = {}): Promise<void> {
  return put<void>('/api/user/email', payload, options)
}

export function uploadAvatar(file: File, options: RequestOptions = {}): Promise<string> {
  const form = new FormData()
  form.append('file', file)
  return post<string>('/api/user/avatar', form, options)
}

export function follow(userId: ID): Promise<void> {
  return put<void>(`/api/user/follow/${userId}`)
}

export function unfollow(userId: ID): Promise<void> {
  return del<void>(`/api/user/follow/${userId}`)
}

export function isFollowing(userId: ID, options: RequestOptions = {}): Promise<boolean> {
  return get<boolean>(`/api/user/follow/${userId}`, undefined, options)
}

export function listFollowing(
  params: PageQuery & { userId: ID },
  options: RequestOptions = {},
): Promise<PageResponse<UserBriefVO>> {
  return get<PageResponse<UserBriefVO>>('/api/user/follow/list', params, options)
}

export function listFans(
  params: PageQuery & { userId: ID },
  options: RequestOptions = {},
): Promise<PageResponse<UserBriefVO>> {
  return get<PageResponse<UserBriefVO>>('/api/user/fans/list', params, options)
}

export function recordHistory(articleId: ID, options: RequestOptions = {}): Promise<void> {
  return put<void>(`/api/user/history/${articleId}`, undefined, options)
}

export function listHistory(
  params: PageQuery = {},
  options: RequestOptions = {},
): Promise<PageResponse<BrowseHistoryVO>> {
  return get<PageResponse<BrowseHistoryVO>>('/api/user/history', params, options)
}

export function deleteHistory(articleId: ID, options: RequestOptions = {}): Promise<void> {
  return del<void>(`/api/user/history/${articleId}`, options)
}

export function clearHistory(options: RequestOptions = {}): Promise<void> {
  return del<void>('/api/user/history', options)
}
