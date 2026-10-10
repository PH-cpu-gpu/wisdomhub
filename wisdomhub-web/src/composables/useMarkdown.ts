import hljs from 'highlight.js/lib/common'
import MarkdownIt from 'markdown-it'

/**
 * Markdown 渲染与代码高亮。
 *
 * **两个消费者用途不同，别搞混**：
 *   - 编辑器预览（F1.3）：用这里的 `renderMarkdown()` 在前端渲染 —— 后端没有预览接口
 *   - 文章详情（F1.4）：正文用**后端渲染好的 html**（已过 jsoup 白名单消毒），
 *     前端只负责 `highlightIn()` 上色。**不要**拿 markdown 再渲染一遍：
 *     两边是不同实现（flexmark vs markdown-it），一定会出现「预览和发布后不一样」
 *
 * 关于安全：markdown-it 的 `html: false` 会把原始 HTML 转义掉（作者写 `<script>` 只会看到这串字面量）；
 * 后端渲染那份则过白名单消毒。所以两边的 `v-html` 都不会引入存储型 XSS。
 */

const md = new MarkdownIt({
  // 原始 HTML 一律转义，不给前端留第二条注入路径
  html: false,
  // 裸 URL 自动成链接：与后端开的 AutolinkExtension 对齐
  linkify: true,
  // 单个换行不当成换行（CommonMark 语义，与后端一致）
  breaks: false,
  highlight(code, language) {
    if (language && hljs.getLanguage(language)) {
      try {
        return hljs.highlight(code, { language, ignoreIllegals: true }).value
      } catch {
        /* 落到下面的空串：markdown-it 会自己转义 */
      }
    }
    // 返回空串表示「不接管」，交给 markdown-it 转义 —— 比手写 escapeHtml 稳
    return ''
  },
})

/** markdown → 消毒过的 HTML（给编辑器预览用） */
export function renderMarkdown(markdown: string): string {
  return md.render(markdown ?? '')
}

/**
 * 给容器里每个 `<pre><code>` 上色（给文章详情用）。
 *
 * 语言从 `class="language-xxx"` 推断 —— 后端白名单只对 `code` 放开了 `class` 一个属性，
 * 注释里写明就是为这个准备的。
 */
export function highlightIn(container: HTMLElement | null | undefined): void {
  if (!container) return
  container.querySelectorAll<HTMLElement>('pre code').forEach((block) => {
    // hljs 11 处理过的元素会打上 data-highlighted，重复处理会抛错，这里先挡一道
    if (block.dataset.highlighted === 'yes') return
    hljs.highlightElement(block)
  })
}
