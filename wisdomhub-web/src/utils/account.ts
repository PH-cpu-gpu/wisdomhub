/**
 * 登录标识（邮箱 / 手机号）的识别与转换。
 *
 * 后端的登录/注册接口是按字段名区分标识的（`{email}` 或 `{phone}`），
 * 而前端只有一个输入框（F1.1 定：只收手机号或邮箱，没有用户名登录入口），
 * 所以在这里做一次识别。两个正则与后端 `LoginDTO` / `RegisterDTO` 上的**完全一致**，
 * 改一处必须同步另一处 —— 否则会出现「前端放行、后端 400」这种最难查的错。
 */

export const EMAIL_PATTERN = /^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}$/

export const PHONE_PATTERN = /^1[3-9]\d{9}$/

export type AccountKind = 'email' | 'phone' | 'unknown'

export function detectAccount(value: string): AccountKind {
  const trimmed = value.trim()
  if (EMAIL_PATTERN.test(trimmed)) return 'email'
  if (PHONE_PATTERN.test(trimmed)) return 'phone'
  return 'unknown'
}

/**
 * 转成后端要的字段形状。识别不出来时按 email 传 —— 让后端的格式校验去报错，
 * 前端只负责「看起来像哪个」。
 *
 * ⚠️ 邮箱由后端统一转小写后落库（§18 定案），前端不做转换：转了小写也还是要
 * 靠后端兜底，两边都转只会让「为什么我输了 A@x.com 却查到 a@x.com」多一处要解释的地方。
 */
export function toAccountPayload(value: string): { email?: string; phone?: string } {
  const trimmed = value.trim()
  return detectAccount(trimmed) === 'phone' ? { phone: trimmed } : { email: trimmed }
}
