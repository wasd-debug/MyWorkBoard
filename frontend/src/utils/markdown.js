import { marked } from 'marked'
import DOMPurify from 'dompurify'

/**
 * Markdown → 安全 HTML。
 * breaks: true 让单个换行保留成 <br>；DOMPurify 用默认策略（自带 javascript:/data: 过滤）。
 * 注意不要给 sanitize 传自定义配置，那样会把 <input> 的 type 属性剥掉。
 */
export function renderMarkdown(value) {
  try {
    return DOMPurify.sanitize(marked.parse(value || '', { async: false, breaks: true }))
  } catch {
    return `<p>${String(value || '').replace(/[&<>]/g, char => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;' }[char]))}</p>`
  }
}

/**
 * 多行文档的只读渲染：按行切成独立块分别渲染。
 * 整段丢给 marked 时，"- 列表项" 下一行会被当成列表项的续行吞掉，
 * 而编辑器本来就是一行一块，这里保持一致。
 */
export function renderMarkdownDocument(value) {
  const lines = String(value ?? '').split('\n')
  return lines
    .map(line => (line.trim() ? renderMarkdown(line) : '<p class="md-blank"></p>'))
    .join('')
}
