import DOMPurify from 'dompurify'

const ARTICLE_CONFIG = {
  ADD_ATTR: ['target', 'rel'],
  FORBID_TAGS: [
    'style',
    'form',
    'input',
    'button',
    'textarea',
    'select',
    'option',
    'iframe',
    'object',
    'embed',
  ],
  FORBID_ATTR: ['style'],
}

/** 文章正文 / Markdown 预览：服务端已消毒，这里再做一层白名单 */
export function sanitizeHtml(html: string): string {
  return hardenHtml(DOMPurify.sanitize(html, ARTICLE_CONFIG))
}

/** 外链统一新窗口打开；正文图片懒加载 */
function hardenHtml(html: string): string {
  if (typeof document === 'undefined') return html

  const template = document.createElement('template')
  template.innerHTML = html
  template.content.querySelectorAll<HTMLAnchorElement>('a[href]').forEach((anchor) => {
    const href = anchor.getAttribute('href') ?? ''
    if (/^https?:\/\//i.test(href)) {
      anchor.setAttribute('target', '_blank')
      anchor.setAttribute('rel', 'noopener noreferrer')
    }
  })
  template.content.querySelectorAll<HTMLImageElement>('img').forEach((image) => {
    image.setAttribute('loading', 'lazy')
    image.setAttribute('decoding', 'async')
    if (!image.hasAttribute('alt')) image.setAttribute('alt', '')
  })
  return template.innerHTML
}

/** 搜索高亮：只允许 <em>，其余全部剥离 */
export function sanitizeSearchHighlight(html: string): string {
  return DOMPurify.sanitize(html, {
    ALLOWED_TAGS: ['em'],
    ALLOWED_ATTR: [],
  })
}
