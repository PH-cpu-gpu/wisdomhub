// 环境变量的类型声明。没有这块，import.meta.env.VITE_XXX 在 TS 里是 any，
// 拼错变量名要到运行时才暴露（Vite 对未定义变量只会给 undefined，不报错）。
//
// 变量清单见仓库根目录的 .env.example。
// 本文件不能出现顶层 import/export，否则会变成模块、interface 不再与 vite/client 全局合并。

/** Vite 内置变量，只声明我们会用到的那几个 */
interface ImportMetaEnv {
  /** 浏览器标签页标题 */
  readonly VITE_APP_TITLE: string
  /** 接口前缀，默认 `/api` */
  readonly VITE_API_BASE_URL: string
  /** 仅 dev server：/api 转发目标（网关地址） */
  readonly VITE_API_PROXY_TARGET?: string

  readonly MODE: string
  readonly DEV: boolean
  readonly PROD: boolean
}

interface ImportMeta {
  readonly env: ImportMetaEnv
}
