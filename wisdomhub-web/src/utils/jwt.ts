/**
 * 只读 JWT 的 payload —— **不校验签名**。
 *
 * 用途只有一个：知道「我是谁」。登录/注册的响应里没有 `user` 对象（只有两个 token），
 * 而「我的评论」「我的文章」这类判断都要当前用户 id。claim 名与后端
 * `JwtPayloadConstants.USER_ID` 一致（`userId`）。
 *
 * ⚠️ 这里解出来的东西**只能用来渲染**，绝不能当权限依据 —— 前端不验签，任何人都能改。
 * 真正的鉴权在网关（验签）与各服务的属主校验（403）。
 */

/** 从 token 里读当前用户 id；解不出来返回 null */
export function readUserId(token: string | null | undefined): string | null {
  if (!token) return null

  const json = decodePayloadJson(token)
  if (!json) return null

  /*
   * ⚠️ **不能用 `JSON.parse` 之后的对象取 userId**。
   *
   * 后端把 userId 作为 JSON **数字**放进 claim（`{"userId":2108911234548244481}`），
   * 而 19 位雪花 ID 超过 `Number.MAX_SAFE_INTEGER`（约 9e15）—— `JSON.parse` 出来的是
   * double，**末尾几位已经被抹平**：解出来的 id 与真实 id 差几十上百，拿去查「我的文章」
   * 只会得到空列表，评论的「是不是我写的」也永远是 false。
   *
   * 所以直接从**原始 JSON 文本**里抓数字串：文本是精确的，绕过浮点。
   * （后端的 HTTP 响应没这个问题 —— 那里 `Long` 被 Jackson 配成了字符串，见技术文档 17.2；
   * 只有 JWT 是另一套序列化，别被"响应里都是字符串"误导。）
   */
  const matched = /"userId"\s*:\s*"?(\d+)"?/.exec(json)
  return matched ? matched[1] : null
}

/**
 * `exp`（秒）—— 目前没人用：静默刷新还没做（F1.1 只到「401 就跳登录」）。
 * 留着是因为将来做刷新时必然要读它，而那时多半会想不起 payload 里本来就有。
 * `exp` 是小数字，`JSON.parse` 取它没有上面那个精度问题。
 */
export function readExpiresAt(token: string | null | undefined): number | null {
  if (!token) return null

  const json = decodePayloadJson(token)
  if (!json) return null

  try {
    return (JSON.parse(json) as { exp?: number }).exp ?? null
  } catch {
    return null
  }
}

/** 解码出 payload 的 **JSON 原文**（不做 JSON.parse，见 readUserId 的注释） */
function decodePayloadJson(token: string): string | null {
  const parts = token.split('.')
  if (parts.length !== 3) return null

  try {
    // base64url → base64：把 -/_ 换回来，并按 4 取模补 =（浏览器 atob 只吃标准 base64）
    const base64 = parts[1].replace(/-/g, '+').replace(/_/g, '/')
    const padded = base64.padEnd(base64.length + ((4 - (base64.length % 4)) % 4), '=')

    // 中文 claim 需要 UTF-8 解码：atob 给的是 latin1 字符串
    const binary = atob(padded)
    const bytes = Uint8Array.from(binary, (char) => char.charCodeAt(0))
    return new TextDecoder().decode(bytes)
  } catch {
    return null
  }
}
