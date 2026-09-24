/**
 * 后端接口契约类型 —— 与技术文档 17.1「统一返回体与异常约定」一一对应。
 *
 * 这里的定义必须和后端 common-core 里的一致：
 *   R<T>            ← com.wisdomhub.common.core.result.R
 *   PageResponse<T> ← com.wisdomhub.common.core.result.PageResponse
 *   ApiCode         ← com.wisdomhub.common.core.result.CommonErrorCode
 * 后端改了这几个类，这里要同步（B1.31 起可由 OpenAPI JSON 自动生成，届时以生成结果为准）。
 */

/**
 * 统一响应体。
 *
 * ⚠️ **HTTP 状态码恒为 200，成败由 code 表达** —— 不要用 axios 的默认行为
 * （它按 HTTP 状态码判断成功）去理解这个接口。前端只看 code。
 */
export interface R<T> {
  /** 200 表示成功；其余见 ApiCode */
  code: number
  /** 提示文案，code !== 200 时供前端展示 */
  message: string
  /** 业务数据，失败时为 null */
  data: T
}

/**
 * 分页返回。
 *
 * ⚠️ 四个数值字段都是 Integer 而不是 Long，这是刻意的（技术文档 17.2）：
 * 后端把 Long 一律序列化成字符串防雪花 ID 精度丢失，如果计数也用 Long，
 * total 会变成 "42" 这样的字符串，前端得到处 Number()。
 * 所以**只有 ID 是 string，计数是 number**。
 */
export interface PageResponse<T> {
  total: number
  pages: number
  pageSize: number
  page: number
  items: T[]
}

/**
 * 通用错误码。借用 HTTP 语义命名，前端 `if (code === 401)` 一行就能写。
 *
 * 用 `as const` 对象而不是 TS enum：tsconfig 开了 `erasableSyntaxOnly`，
 * enum 会生成运行时代码，不被允许。
 *
 * ⚠️ 现阶段只有这 9 个码，**业务细节全在 message 里**，前端无法对某类业务错误做
 * 程序化处理，只能弹提示。将来若需要（例如「余额不足」要弹充值入口），后端会引入
 * 五位数服务错误码段（1xxxx=user、2xxxx=content…），规则是
 * **三位数=通用错误（前端可能有特殊动作），五位数=业务错误（前端只弹 message）**。
 */
export const ApiCode = {
  SUCCESS: 200,
  BAD_REQUEST: 400,
  UNAUTHORIZED: 401,
  FORBIDDEN: 403,
  NOT_FOUND: 404,
  CONFLICT: 409,
  TOO_MANY_REQUESTS: 429,
  INTERNAL_ERROR: 500,
  SERVICE_UNAVAILABLE: 503,
} as const

/** 判断一个值是不是 R 形状。用于区分「业务响应」与「网关/代理返回的裸 body」。 */
export function isR(value: unknown): value is R<unknown> {
  return (
    typeof value === 'object' &&
    value !== null &&
    typeof (value as R<unknown>).code === 'number'
  )
}
