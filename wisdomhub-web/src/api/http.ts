import axios, { AxiosError, type AxiosRequestConfig, type AxiosResponse } from 'axios'
import { ElMessage } from 'element-plus'
// 显式引样式：这个文件是纯 .ts，不走 SFC 的按需注入，
// 不引的话 ElMessage 会渲染成没有样式的裸文本。
import 'element-plus/es/components/message/style/css'

import { beginRequest, endRequest } from '@/composables/useLoading'
import { env } from '@/config/env'
import { router } from '@/router'
import { clearSession, getAccessToken } from '@/utils/auth-storage'

import { ApiCode, isR, type R } from './types'

/**
 * 请求层。整个前端与后端通信的唯一出口。
 *
 * 契约来源是技术文档 17.1，三条要记住：
 *
 * 1. **HTTP 状态码恒为 200，成败只看 `body.code`。**
 *    所以下面的 axios 默认行为（按 HTTP 状态码判定成功）在这套约定下是不适用的，
 *    真正的判断在响应拦截器里。
 *
 * 2. **`err` 分支不能删。** HTTP 恒 200 只是业务服务的约定；
 *    网关、Nginx、网络层仍会返回真实的 4xx/5xx，甚至根本没有 response。
 *    「只写 res 分支」是线上白屏最常见的原因。
 *
 * 3. **SSE 与文件下载不走 `R<T>`**，靠 `rawResponse` 开关绕过拆包。
 */

// ---------------------------------------------------------------------------
// 扩展 axios 的请求配置，加上本项目的两个开关
// ---------------------------------------------------------------------------
declare module 'axios' {
  export interface AxiosRequestConfig {
    /**
     * 跳过 `R<T>` 拆包，拿到原始 AxiosResponse。
     * 给 SSE（AI 流式对话，B3.3）与文件下载这类不套统一返回体的接口用
     * —— 技术文档 17.1「必须处理的边界 ②」。
     */
    rawResponse?: boolean
    /** 跳过自动错误提示，由调用方自己处理。用于轮询、可预期失败的探测类请求。 */
    silent?: boolean
  }
}

// ---------------------------------------------------------------------------
// 错误类型
// ---------------------------------------------------------------------------

/**
 * 业务错误。`code` 就是 `R.code`（或 HTTP 状态码），让调用方能程序化区分。
 *
 * 现阶段 9 个通用码的业务细节全在 message 里，前端大多只能弹提示。
 * 真正需要分支的是少数几个，例如注册时 **409 = 用户名/邮箱已被占用**（B1.7），
 * F1.1 要靠它把错误定位到具体输入框，而不是笼统弹一句。
 */
export class ApiError extends Error {
  code: number

  constructor(code: number, message: string) {
    super(message)
    this.name = 'ApiError'
    this.code = code
  }
}

// ---------------------------------------------------------------------------
// 401 处理
// ---------------------------------------------------------------------------

/**
 * 防止并发 401 重复跳转 / 重复提示。
 * 一个页面同时发 5 个请求，token 过期时会拿到 5 个 401，
 * 没有这个开关就会弹 5 次提示、跳 5 次路由。
 */
let handlingUnauthorized = false

/**
 * 认证接口自己的 401 是「账号或密码不对」，不是「会话失效」——
 * 不能走清 token + 跳登录那一套，否则在登录页输错一次密码就会被弹「登录已过期」并跳一次路由。
 * 技术文档 17.3 明写了这条（「前端的 401 拦截器要放行登录接口本身」）。
 */
function isAuthEndpoint(url: string | undefined): boolean {
  return typeof url === 'string' && url.startsWith('/auth/')
}

function handleUnauthorized(message?: string): void {
  clearSession()

  if (handlingUnauthorized) return
  handlingUnauthorized = true

  ElMessage.error(message || '登录已过期，请重新登录')

  const current = router.currentRoute.value
  const target =
    current.path === '/login'
      ? { path: '/login' } // 已经在登录页，别再往自己身上跳
      : { path: '/login', query: { redirect: current.fullPath } }

  // 用 replace 而不是 push：回退时不该退回那个已经失效的页面
  void router.replace(target).finally(() => {
    // 留一点时间让导航完成，避免同一批请求里后面的 401 又触发一轮
    window.setTimeout(() => {
      handlingUnauthorized = false
    }, 300)
  })
}

// ---------------------------------------------------------------------------
// 错误文案
// ---------------------------------------------------------------------------

/**
 * 把各种失败情况收敛成一句能展示给用户的话。
 *
 * 优先级：R 的 message（后端最清楚发生了什么）> 状态码兜底 > 网络层兜底。
 */
function resolveErrorMessage(error: AxiosError): string {
  const body = error.response?.data

  // 网关自己的错误（公网访问 /internal/** 的 403、限流 429）body 已经是 R 形状，
  // 但 Nginx / 网络层的不是 —— 两种都兜住。
  if (isR(body) && body.message) return body.message

  const status = error.response?.status
  if (status === ApiCode.TOO_MANY_REQUESTS) return '请求过于频繁，请稍后重试'
  if (status) return `请求失败 (${status})`

  // 没有 response：请求根本没到达服务器
  if (error.code === 'ECONNABORTED') return '请求超时，请检查网络后重试'
  return '网络异常，请检查网络连接'
}

// ---------------------------------------------------------------------------
// axios 实例
// ---------------------------------------------------------------------------

const http = axios.create({
  // 相对路径 `/api`，开发由 dev server 转发、生产由 Nginx 反代到网关。
  // 见 src/config/env.ts 与 vite.config.ts 的 server.proxy。
  baseURL: env.apiBaseUrl,
  timeout: 15000,
  headers: { Accept: 'application/json' },
})

/** 注入 token。注意是 `Bearer ` 带一个空格。 */
http.interceptors.request.use((config) => {
  const token = getAccessToken()
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  // 顶栏 loading 的计数：进一个请求加一，两个响应分支各减一（无论成败都恰好减一次）
  beginRequest()
  return config
})

http.interceptors.response.use(
  // ---- HTTP 200：可能是业务成功，也可能是业务失败（由 code 决定） ----
  (response) => {
    endRequest()
    const { config } = response

    // 绕过拆包的接口（SSE / 文件下载）原样返回，它们的 body 不套 R<T>
    if (config.rawResponse) return response

    const body: unknown = response.data

    // 不是 R 形状：业务服务不该出现这种情况，说明有接口没走统一返回体。
    // 这里**必须报错而不是放行** —— 放行的话调用方会拿到 undefined 一路往下走，
    // 然后在某个不相干的地方崩掉，排查方向完全错。
    // （技术文档 17.1 边界③说的正是这个失效模式。）
    if (!isR(body)) {
      const message = '接口返回格式不符合约定（期望 R 结构）'
      if (!config.silent) ElMessage.error(message)
      return Promise.reject(new ApiError(0, message))
    }

    const r = body as R<unknown>

    if (r.code === ApiCode.SUCCESS) {
      // 注意这里**不拆包**，原样返回 AxiosResponse。
      // 拆包交给下面的 request<T>()：在那里拆，axios 的类型是准的、不用断言；
      // 在拦截器里拆就得对 axios 撒谎（把 resolve 值改成 R.data），反而要写类型断言。
      return response
    }

    if (r.code === ApiCode.UNAUTHORIZED) {
      if (!isAuthEndpoint(config.url)) handleUnauthorized(r.message)
      return Promise.reject(new ApiError(r.code, r.message))
    }

    // 其余业务失败：统一提示，再把错误抛给调用方
    if (!config.silent) ElMessage.error(r.message || '请求失败')
    return Promise.reject(new ApiError(r.code, r.message))
  },

  // ---- HTTP 非 200，或请求根本没发出去 ----
  (error: AxiosError) => {
    endRequest()

    // 真实的 401（网关也可能返回）——同样走失效流程，但认证接口除外
    if (error.response?.status === ApiCode.UNAUTHORIZED) {
      if (!isAuthEndpoint(error.config?.url)) handleUnauthorized()
      return Promise.reject(new ApiError(ApiCode.UNAUTHORIZED, '登录已过期'))
    }

    const message = resolveErrorMessage(error)

    // silent 的请求连这里的提示也跳过，由调用方自己处理
    if (!error.config?.silent) ElMessage.error(message)

    return Promise.reject(new ApiError(error.response?.status ?? 0, message))
  },
)

// ---------------------------------------------------------------------------
// 对外方法
// ---------------------------------------------------------------------------

/**
 * 发起请求，返回**拆包后的业务数据**。
 *
 * 为什么拆包放在这里而不是拦截器：放在这里 axios 的类型全程是准的 ——
 * `http.request<R<T>>` 解析成 `Promise<AxiosResponse<R<T>>>`，
 * 于是 `res.data.data` 就是 `T`，一个类型断言都不需要。
 * 若在拦截器里 return `r.data`，axios 的签名仍然声明 resolve 出 AxiosResponse，
 * 就只能在那一处对类型系统撒谎（写断言），把不确定性藏进基础设施里。
 *
 * 到这里时 code 必然已经是 200 —— 其余情况在拦截器里就被 reject 了。
 */
export async function request<T>(config: AxiosRequestConfig): Promise<T> {
  const response = await http.request<R<T>>(config)
  return response.data.data
}

export function get<T>(url: string, params?: object, config?: AxiosRequestConfig): Promise<T> {
  return request<T>({ method: 'GET', url, params, ...config })
}

export function post<T>(url: string, data?: unknown, config?: AxiosRequestConfig): Promise<T> {
  return request<T>({ method: 'POST', url, data, ...config })
}

export function put<T>(url: string, data?: unknown, config?: AxiosRequestConfig): Promise<T> {
  return request<T>({ method: 'PUT', url, data, ...config })
}

export function del<T>(url: string, params?: object, config?: AxiosRequestConfig): Promise<T> {
  return request<T>({ method: 'DELETE', url, params, ...config })
}

/**
 * 不拆包的请求：拿到完整 AxiosResponse。
 * 用于 SSE 与文件下载（技术文档 17.1 边界 ②）。
 */
export function requestRaw<T = unknown>(config: AxiosRequestConfig): Promise<AxiosResponse<T>> {
  return http.request<unknown, AxiosResponse<T>>({ ...config, rawResponse: true })
}

/** 需要直接操作 axios 实例时用（例如挂自定义 cancelToken）。一般用不到。 */
export const axiosInstance = http
