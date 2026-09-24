/**
 * 环境变量的唯一读取入口。
 *
 * 业务代码不直接写 `import.meta.env.XXX`，理由：
 * 1. Vite 对不存在的变量只给 `undefined`，不报错。集中校验能让配置缺失在启动时
 *    就炸出来，而不是等到某个接口请求发出一个空 baseURL 才 404。
 * 2. 变量名和默认值只在这里出现一次，改名时不会漏掉散落各处的引用。
 */

function required(key: keyof ImportMetaEnv): string {
  const value = import.meta.env[key]
  if (typeof value !== 'string' || value.length === 0) {
    throw new Error(
      `[env] 缺少必填环境变量 ${key}。` +
        `请检查 wisdomhub-web/.env 与 .env.${import.meta.env.MODE}（模板见 .env.example）。`,
    )
  }
  return value
}

export const env = {
  /** 应用标题，用于浏览器标签页与页面标题 */
  appTitle: required('VITE_APP_TITLE'),

  /**
   * 接口前缀，默认 `/api`。
   * 保持相对路径，由 dev server 转发 / Nginx 反代到网关（见 vite.config.ts 的 server.proxy）。
   */
  apiBaseUrl: required('VITE_API_BASE_URL'),

  /** 当前构建模式：development / production */
  mode: import.meta.env.MODE,
  isDev: import.meta.env.DEV,
  isProd: import.meta.env.PROD,
} as const
