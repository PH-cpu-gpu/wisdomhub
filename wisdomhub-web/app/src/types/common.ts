export type ID = string

/** 后端统一返回体：HTTP 恒 200，成败由 code 表达 */
export interface R<T> {
  code: number
  message: string
  data: T
}

/** 分页返回体 */
export interface PageResponse<T> {
  total: number
  pages: number
  pageSize: number
  page: number
  items: T[]
}

/** 列表接口通用分页参数 */
export interface PageQuery {
  page?: number
  pageSize?: number
}
