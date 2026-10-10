import type { AxiosRequestConfig } from 'axios'

import { del, get, post, put } from './http'
import type { PageResponse } from './types'

/**
 * 用户资料与关注关系（B1.11 / B1.12）。
 *
 * 两组接口的**可见性不一样**，前端要分开对待：
 *   - `GET /user/profile/{id}` 在网关的**免登录名单**里 → 游客也能看别人主页
 *   - `follow/list` / `fans/list` / `isFollowed` 需要登录 → 游客拿这些会给 401。
 *     所以调用方要**静默调用并降级**（显示「登录后可见」），不要让 401 触发跳登录
 *     （游客逛主页时被弹「登录已过期」很莫名其妙）
 *
 * ⚠️ 资料里**没有 email**：它对外公开，邮箱不能进（要判断自己有没有邮箱看登录响应里的 hasEmail）
 */
export interface UserProfile {
  userId: string
  username: string
  avatar: string | null
  bio: string | null
  articleCount: number
  fansCount: number
  followCount: number
}

export interface UserBrief {
  userId: string
  username: string
  avatar: string | null
  bio: string | null
}

/** 个人主页聚合：一次拿齐资料 + 文章数 + 粉丝数 + 关注数 */
export function getUserProfile(userId: string, config?: AxiosRequestConfig) {
  return get<UserProfile>(`/user/profile/${userId}`, undefined, config)
}

/**
 * 按 ID 批量取用户摘要（列表页显示作者名用）。
 *
 * ⚠️ 为什么需要它：文章列表只返回 `authorId`（跨服务 JOIN 被禁止，原则六），
 * 要显示作者名就得再问一次 user-service。**别每条问一次** —— 一页 N 条会变 N 次请求，
 * 所以走这个批量接口（见 `composables/useUserNames`，它负责去重与缓存）。
 */
export function listUserBriefs(userIds: string[], config?: AxiosRequestConfig) {
  return get<UserBrief[]>('/user/profile/brief', { userIds: userIds.join(',') }, config)
}

/**
 * 改自己的资料。
 *
 * ⚠️ `username` 是**必填**（后端 `ProfileDTO` 上有 `@NotBlank`，2026-10-10 补的）——
 * 不能只提交 `bio`，会把整条请求打成 400。
 */
export function updateProfile(payload: { username: string; bio?: string }) {
  return put<void>('/user/profile', payload)
}

/**
 * 上传头像。后端**上传即落库**（写 `user.avatar`、失败会回滚、成功删旧文件），
 * 返回新的 URL —— 所以前端拿到返回值直接更新界面即可，不用再调一次改资料。
 *
 * ⚠️ 用 FormData 传，**不要手动设 `Content-Type`**：multipart 的 boundary 必须由浏览器生成。
 */
export function uploadAvatar(file: File) {
  const data = new FormData()
  data.append('file', file)
  return post<string>('/user/avatar', data)
}

/** 当前用户是否已关注 TA。需要登录 */
export function isFollowed(userId: string, config?: AxiosRequestConfig) {
  return get<boolean>(`/user/follow/${userId}`, undefined, config)
}

/** 关注 / 取关。都是「设置状态」，幂等，重复调用返回 200 */
export function followUser(userId: string) {
  return put<void>(`/user/follow/${userId}`)
}

export function unfollowUser(userId: string) {
  return del<void>(`/user/follow/${userId}`)
}

/** TA 关注的人。需要登录 */
export function listFollowing(userId: string, page = 1, pageSize = 10, config?: AxiosRequestConfig) {
  return get<PageResponse<UserBrief>>('/user/follow/list', { userId, page, pageSize }, config)
}

/** TA 的粉丝。需要登录 */
export function listFans(userId: string, page = 1, pageSize = 10, config?: AxiosRequestConfig) {
  return get<PageResponse<UserBrief>>('/user/fans/list', { userId, page, pageSize }, config)
}
