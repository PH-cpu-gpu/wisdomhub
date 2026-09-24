import { get } from './http'

/**
 * 阶段 0 的连通性冒烟靶子（技术文档 5.1「阶段 0 的连通性探测端点」）。
 *
 *   GET /api/knowledge/ping → 200 + {"code":200,"message":"ok","data":"pong"}
 *
 * 放在 knowledge-service 是因为它只依赖 Neo4j 驱动、不依赖 MySQL，启动成本最低。
 * 任务 F0.5 用它确认「前端经网关能拿到后端响应」——
 * 这个请求一路经过 Nginx/dev-server → Gateway → knowledge-service，
 * 能通就说明路由、跨域、响应包装这几条链路都是好的。
 *
 * 阶段 1 各服务有真接口后可删。
 */
export function ping(): Promise<string> {
  return get<string>('/knowledge/ping')
}
