import type { AxiosRequestConfig } from 'axios'

import { post, put } from './http'

/**
 * 认证接口。契约见技术文档 17.3「六、认证接口契约」。
 *
 * ⚠️ 三处入参形式别想当然，都是后端实际行为（不是文档的旧版本）：
 *   - `register` **必须带 `code`**（验证码）—— 文档老版本漏了它，照那个写会 400
 *   - `refresh` / `logout` 都是 **JSON body**（refresh 原先是 query，后端已统一到 body）
 *   - `logout` 要 body 里再带一次两个 token：网关仍要求带 Authorization 头，
 *     body 里的那份是给「把两个 token 都拉黑」用的（见 B1.9）
 */

export interface LoginResult {
  accessToken: string
  refreshToken: string
  /**
   * 当前账号是否已绑邮箱。`false` 时前端提示补绑（B1.10 的 `PUT /api/user/email`）。
   * 后端 `LoginVO` 目前没返回这个字段 —— 没返回时是 `undefined`，前端不提示，不会误报。
   */
  hasEmail?: boolean
}

export interface RegisterPayload {
  email?: string
  phone?: string
  code: string
  username: string
  password: string
}

/**
 * 登录。
 * 失败语义（前端要分支）：`404` 账号不存在 → 切到注册引导；`401` 密码错误。
 */
export function login(
  account: { email?: string; phone?: string },
  password: string,
  config?: AxiosRequestConfig,
) {
  return post<LoginResult>('/auth/login', { ...account, password }, config)
}

/** 注册 = 首次登录引导的第二步，建号即登录（直接返回双 token） */
export function register(payload: RegisterPayload, config?: AxiosRequestConfig) {
  return post<LoginResult>('/auth/register', payload, config)
}

/** 刷新 accessToken。当前只被「静默刷新」用，F1.1 暂未实现（401 直接跳登录） */
export function refresh(refreshToken: string) {
  return post<LoginResult>('/auth/refresh', { refreshToken })
}

export function logout(accessToken: string, refreshToken: string) {
  return post<void>('/auth/logout', { accessToken, refreshToken })
}

/**
 * 取验证码。当前后端是**模拟实现**：把码打进 user-service 日志，不真发短信/邮件
 * —— 联调时从日志里拿（`生成验证码为: 123456`），别以为是接口坏了。
 */
export function sendCodeByEmail(email: string, config?: AxiosRequestConfig) {
  return post<void>('/auth/code/email', undefined, { params: { email }, ...config })
}

export function sendCodeByPhone(phone: string, config?: AxiosRequestConfig) {
  return post<void>('/auth/code/phone', undefined, { params: { phone }, ...config })
}

/** 绑定 / 改绑邮箱。要验证码、被占用返回 409（B1.10） */
export function bindEmail(email: string, code: string) {
  return put<void>('/user/email', { email, code })
}
