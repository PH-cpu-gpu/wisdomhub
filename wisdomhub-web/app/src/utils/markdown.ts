import hljs from 'highlight.js/lib/core'
import bash from 'highlight.js/lib/languages/bash'
import c from 'highlight.js/lib/languages/c'
import cpp from 'highlight.js/lib/languages/cpp'
import csharp from 'highlight.js/lib/languages/csharp'
import css from 'highlight.js/lib/languages/css'
import dockerfile from 'highlight.js/lib/languages/dockerfile'
import go from 'highlight.js/lib/languages/go'
import java from 'highlight.js/lib/languages/java'
import javascript from 'highlight.js/lib/languages/javascript'
import json from 'highlight.js/lib/languages/json'
import kotlin from 'highlight.js/lib/languages/kotlin'
import markdownLanguage from 'highlight.js/lib/languages/markdown'
import nginx from 'highlight.js/lib/languages/nginx'
import php from 'highlight.js/lib/languages/php'
import python from 'highlight.js/lib/languages/python'
import rust from 'highlight.js/lib/languages/rust'
import sql from 'highlight.js/lib/languages/sql'
import typescript from 'highlight.js/lib/languages/typescript'
import xml from 'highlight.js/lib/languages/xml'
import yaml from 'highlight.js/lib/languages/yaml'
import MarkdownIt from 'markdown-it'

import 'highlight.js/styles/github-dark.css'

import { sanitizeHtml } from './sanitize'

const languages = {
  bash,
  c,
  cpp,
  csharp,
  css,
  dockerfile,
  go,
  java,
  javascript,
  json,
  kotlin,
  markdown: markdownLanguage,
  nginx,
  php,
  python,
  rust,
  sql,
  typescript,
  xml,
  yaml,
}

for (const [name, language] of Object.entries(languages)) {
  hljs.registerLanguage(name, language)
}

const markdown = new MarkdownIt({
  html: false,
  linkify: true,
  breaks: true,
})

markdown.options.highlight = (code, language) => {
  if (language && hljs.getLanguage(language)) {
    try {
      const highlighted = hljs.highlight(code, {
        language,
        ignoreIllegals: true,
      }).value
      return `<pre class="hljs"><code>${highlighted}</code></pre>`
    } catch {
      // fall through
    }
  }
  return `<pre class="hljs"><code>${markdown.utils.escapeHtml(code)}</code></pre>`
}

const defaultLinkOpen = markdown.renderer.rules.link_open
markdown.renderer.rules.link_open = (tokens, index, options, env, self) => {
  const token = tokens[index]
  const href = token?.attrGet('href') ?? ''
  if (/^https?:\/\//i.test(href)) {
    token?.attrSet('target', '_blank')
    token?.attrSet('rel', 'noopener noreferrer')
  }
  return defaultLinkOpen
    ? defaultLinkOpen(tokens, index, options, env, self)
    : self.renderToken(tokens, index, options)
}

/** Markdown → 消毒后的 HTML（编辑器预览 / 学习路线渲染共用） */
export function renderMarkdown(source: string): string {
  return sanitizeHtml(markdown.render(source ?? ''))
}
