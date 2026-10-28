import type { ID } from './common'

export interface LoginVO {
  accessToken: string
  refreshToken: string
  /** 当前账号是否已绑邮箱；refresh 返回 null 表示「不知道」 */
  hasEmail: boolean | null
}

export interface RegisterPayload {
  phone?: string | null
  email?: string | null
  code: string
  username: string
  password: string
}

export interface LoginPayload {
  phone?: string | null
  email?: string | null
  password: string
}

export interface RefreshPayload {
  refreshToken: string
}

export interface LogoutPayload {
  accessToken: string
  refreshToken: string
}

export interface UserProfileVO {
  userId: ID
  username: string
  avatar: string | null
  bio: string | null
  articleCount: number
  fansCount: number
  followCount: number
}

export interface UserBriefVO {
  userId: ID
  username: string
  avatar: string | null
  bio: string | null
}

export interface ProfilePayload {
  username: string
  bio?: string | null
}

export interface BindEmailPayload {
  email: string
  code: string
}

export interface BrowseHistoryVO {
  articleId: ID
  title: string
  summary: string
  updateTime: string
}
