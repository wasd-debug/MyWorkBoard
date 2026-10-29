<template>
  <section class="task-rich-editor" :class="{ 'preview-only': previewOnly, 'is-compact': compact }" :aria-label="label">
    <div v-if="previewOnly" class="task-rich-preview markdown-body" v-html="rendered"></div>
    <template v-else>
      <div v-if="!compact" class="task-rich-toolbar" role="toolbar" :aria-label="`${label}工具栏`" :aria-hidden="!toolbarVisible" :inert="!toolbarVisible">
        <button v-for="item in toolbar" :key="item.key" type="button" :aria-label="item.label" :title="item.label" @mousedown.prevent @click="runToolbar(item)"><component :is="item.icon" :size="14" /></button>
        <label class="task-rich-color" title="文字颜色" aria-label="文字颜色"><Paintbrush :size="14" /><input type="color" aria-label="选择文字颜色" @input="applyColor($event.target.value)" /></label>
      </div>
      <div class="task-rich-input-wrap">
        <div
          ref="editor"
          class="task-rich-input markdown-body"
          contenteditable="true"
          role="textbox"
          aria-multiline="true"
          :aria-label="label"
          :data-placeholder="placeholder"
          @input="onInput"
          @compositionend="onInput"
          @keydown="onKeydown"
          @blur="onBlur"
        ></div>
        <div v-if="slashOpen" class="task-slash-menu" role="listbox" :aria-label="`${label}插入菜单`" @mousedown.prevent>
          <button v-for="item in slashCommands" :key="item.key" type="button" role="option" @click="chooseSlash(item)">
            <strong>{{ item.label }}</strong><small>{{ item.hint }}</small>
          </button>
        </div>
      </div>
      <small v-if="!compact" class="task-rich-hint">Markdown · 支持 / 插入基础格式</small>
    </template>
  </section>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { marked } from 'marked'
import DOMPurify from 'dompurify'
import { Bold, CheckSquare, Clock3, Code, Heading1, Italic, Link, List, ListOrdered, Minus, Paintbrush, Quote, Strikethrough, Underline } from 'lucide-vue-next'

const emit = defineEmits(['update:modelValue', 'blur'])
const editor = ref(null)
const slashOpen = ref(false)
let slashRange = null
let normalizeTimer = null
let caretMarkerSequence = 0
const EMPTY_BLOCK_CARET = '\u200b'
const toolbar = [
  { key: 'heading', label: '标题', icon: Heading1 },
  { key: 'bold', label: '加粗', icon: Bold },
  { key: 'check', label: '检查项', icon: CheckSquare },
  { key: 'list', label: '无序列表', icon: List },
  { key: 'ordered', label: '有序列表', icon: ListOrdered },
  { key: 'italic', label: '斜体', icon: Italic },
  { key: 'underline', label: '下划线', icon: Underline },
  { key: 'strike', label: '删除线', icon: Strikethrough },
  { key: 'divider', label: '分割线', icon: Minus },
  { key: 'time', label: '增加时间', icon: Clock3 },
  { key: 'link', label: '链接', icon: Link },
  { key: 'code', label: '代码', icon: Code },
  { key: 'quote', label: '引用', icon: Quote }
]
const slashCommands = [
  { key: 'heading', label: '标题', hint: '# 标题', value: '# ' },
  { key: 'bold', label: '加粗', hint: '**文本**', value: '****' },
  { key: 'italic', label: '斜体', hint: '*文本*', value: '**' },
  { key: 'underline', label: '下划线', hint: '<u>文本</u>', value: '<u></u>' },
  { key: 'strike', label: '删除线', hint: '~~文本~~', value: '~~~~' },
  { key: 'list', label: '无序列表', hint: '- 列表项', value: '- ' },
  { key: 'ordered', label: '有序列表', hint: '1. 列表项', value: '1. ' },
  { key: 'check', label: '检查项', hint: '- [ ] 待办', value: '- [ ] ' },
  { key: 'quote', label: '引用', hint: '> 引用内容', value: '> ' },
  { key: 'divider', label: '分割线', hint: '---', value: '---\n' },
  { key: 'time', label: '增加时间', hint: '插入当前日期和时间', value: '' },
  { key: 'code', label: '代码', hint: '`代码`', value: '`代码`' },
  { key: 'link', label: '链接', hint: '[文本](https://)', value: '[链接文本](https://)' }
]
const props = defineProps({ modelValue: { type: String, default: '' }, label: { type: String, default: '富文本' }, placeholder: { type: String, default: '输入内容…' }, previewOnly: { type: Boolean, default: false }, toolbarVisible: { type: Boolean, default: true }, compact: { type: Boolean, default: false } })
const rendered = computed(() => renderMarkdown(props.modelValue))

function renderMarkdown(value) {
  try {
    // breaks: true → 单个换行渲染成 <br>，否则"按一次回车"的换行会在回写时被吃掉
    const html = marked.parse(value || '', { async: false, breaks: true })
    // 块级标签之间的换行/缩进是文本节点，编辑器又用了 pre-wrap，会被当成真实空行显示出来
    const compact = html.replace(/(<pre[\s\S]*?<\/pre>)|>\s*\n\s*</g, (match, pre) => pre || '><')
    // 不要传 ALLOWED_URI_REGEXP：DOMPurify 一旦收到自定义配置，就会把 <input> 的 type 属性丢掉，
    // 任务清单的复选框会退化成没有类型的输入框，"- [ ] 待办" 回写时也会丢掉方括号。
    // 默认配置本身就会过滤 javascript: / data: 链接、剥掉 on* 事件，够用。
    return DOMPurify.sanitize(compact)
  } catch {
    // 极端内容（超长/异常 Markdown）渲染失败时降级为转义纯文本，避免抛错带崩整页
    return `<p>${String(value || '').replace(/[&<>]/g, char => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;' }[char])).slice(0, 20000)}</p>`
  }
}

const BLOCK_TAGS = new Set(['P', 'DIV', 'SECTION', 'ARTICLE', 'H1', 'H2', 'H3', 'H4', 'H5', 'H6', 'UL', 'OL', 'BLOCKQUOTE', 'PRE', 'HR', 'TABLE'])

/**
 * 把块级容器的直接子节点按"块 / 行内"切开：块与块之间是一个空行，行内节点之间按 <br> 走。
 * 之前直接拼 children，浏览器按回车生成的 <div> / <p> 之间没有分隔符，两行会粘成一行。
 */
function containerMarkdown(node) {
  const parts = []
  let inline = []
  const flush = () => {
    if (!inline.length) return
    const text = inline.map(nodeMarkdown).join('')
    inline = []
    if (text.trim()) parts.push(text)
  }
  for (const child of node.childNodes) {
    if (child.nodeType === Node.ELEMENT_NODE && BLOCK_TAGS.has(child.tagName)) {
      flush()
      const md = nodeMarkdown(child).replace(/^\n+|\n+$/g, '')
      if (md.trim()) parts.push(md)
    } else {
      inline.push(child)
    }
  }
  flush()
  return parts.join('\n\n')
}

function nodeMarkdown(node) {
  if (node.nodeType === Node.TEXT_NODE) return node.nodeValue || ''
  if (node.nodeType !== Node.ELEMENT_NODE) return ''
  const children = [...node.childNodes].map(nodeMarkdown).join('')
  switch (node.tagName) {
    case 'BR': return '\n'
    case 'P': return containerMarkdown(node)
    case 'H1': return `# ${children}`
    case 'H2': return `## ${children}`
    case 'H3': return `### ${children}`
    case 'STRONG': case 'B': return `**${children}**`
    case 'EM': case 'I': return `*${children}*`
    case 'U': return `<u>${children}</u>`
    case 'S': case 'DEL': case 'STRIKE': return `~~${children}~~`
    case 'CODE': return `\`${children}\``
    case 'PRE': return `\`\`\`\n${node.textContent || ''}\n\`\`\``
    case 'A': {
      const href = node.getAttribute('href') || ''
      return /^(https?:|mailto:)/i.test(href) ? `[${children}](${href})` : children
    }
    case 'BLOCKQUOTE': return children.trim().split('\n').map(line => line ? `> ${line}` : '>').join('\n')
    case 'HR': return '---'
    // 表格必须还原为 Markdown 表格语法，否则编辑其它段落时表格会被拍平成纯文本而消失
    case 'TABLE': {
      const rows = [...node.querySelectorAll('tr')]
      if (!rows.length) return children
      const cellText = cell => (cell.textContent || '').replace(/\s+/g, ' ').trim().replace(/\|/g, '\\|')
      const cellsOf = row => [...row.children].filter(cell => ['TH', 'TD'].includes(cell.tagName)).map(cellText)
      const header = cellsOf(rows[0])
      if (!header.length) return children
      const body = rows.slice(1).map(row => {
        const values = cellsOf(row)
        while (values.length < header.length) values.push('')
        return `| ${values.slice(0, header.length).join(' | ')} |`
      })
      return [`| ${header.join(' | ')} |`, `| ${header.map(() => '---').join(' | ')} |`, ...body].join('\n')
    }
    case 'UL': case 'OL': {
      const ordered = node.tagName === 'OL'
      return [...node.children].filter(child => child.tagName === 'LI').map((item, index) => {
        const checkbox = item.querySelector(':scope > input')
        const text = [...item.childNodes].filter(child => child !== checkbox && !(child.nodeType === Node.ELEMENT_NODE && ['UL', 'OL'].includes(child.tagName))).map(nodeMarkdown).join('').trim()
        const prefix = checkbox ? `- [${checkbox.checked ? 'x' : ' '}] ` : ordered ? `${index + 1}. ` : '- '
        const nested = [...item.children].filter(child => ['UL', 'OL'].includes(child.tagName)).map(nodeMarkdown).join('').trimEnd()
        return `${prefix}${text}${nested ? `\n${nested.split('\n').map(line => `  ${line}`).join('\n')}` : ''}`
      }).join('\n')
    }
    case 'LI': return children
    case 'DIV': case 'SECTION': case 'ARTICLE': return containerMarkdown(node)
    case 'SPAN': {
      const color = node.style?.color
      return color ? `<span style="color: ${color}">${children}</span>` : children
    }
    default: return children
  }
}

function markdownFromEditor() {
  const root = editor.value
  if (!root) return ''
  return containerMarkdown(root).replace(/\n{3,}/g, '\n\n').trim()
}

function hasEditorFocus(element) {
  const active = document.activeElement
  return active === element || Boolean(element.contains(active))
}

function insertCaretMarker(element) {
  // 选区会残留在 contenteditable 里，即使用户已经把焦点点到别处；
  // 此时如果按选区插标记再还原，addRange 会把焦点抢回编辑器，导致点别的输入框打不进字。
  if (!hasEditorFocus(element)) return null
  const selection = window.getSelection()
  if (!selection?.rangeCount || !selection.isCollapsed || !element.contains(selection.anchorNode)) return null
  const marker = `taskeditormarker${++caretMarkerSequence}end`
  const range = selection.getRangeAt(0)
  range.insertNode(document.createTextNode(marker))
  return marker
}

function restoreCaretMarker(element, marker) {
  if (!marker || !hasEditorFocus(element)) return false
  const walker = document.createTreeWalker(element, NodeFilter.SHOW_TEXT)
  let node
  while ((node = walker.nextNode())) {
    const index = node.nodeValue.indexOf(marker)
    if (index < 0) continue
    const markerNode = node.splitText(index)
    const afterMarker = markerNode.splitText(marker.length)
    const parent = markerNode.parentNode
    const parentOffset = [...parent.childNodes].indexOf(markerNode)
    markerNode.remove()
    const range = document.createRange()
    if (index === 0) {
      if (!node.nodeValue) {
        node.nodeValue = EMPTY_BLOCK_CARET
        if (!afterMarker.nodeValue) afterMarker.remove()
        range.setStart(node, node.nodeValue.length)
      } else {
        range.setStart(parent, Math.min(parentOffset, parent.childNodes.length))
      }
    } else {
      range.setStart(node, index)
    }
    range.collapse(true)
    const selection = window.getSelection()
    selection.removeAllRanges()
    selection.addRange(range)
    return true
  }
  return false
}

function currentMarkdown() {
  return markdownFromEditor().replaceAll(EMPTY_BLOCK_CARET, '')
}

function renderEditorMarkdown() {
  const element = editor.value
  if (!element) return
  const marker = insertCaretMarker(element)
  const rawMarkdown = markdownFromEditor()
  const renderedMarkdown = rawMarkdown.replaceAll(EMPTY_BLOCK_CARET, '')
  const markdown = marker ? renderedMarkdown.replace(marker, '') : renderedMarkdown
  emit('update:modelValue', markdown)
  element.innerHTML = renderMarkdown(renderedMarkdown)
  restoreCaretMarker(element, marker)
}

function autoGrow() {
  const element = editor.value
  if (!element || !props.compact) return
  element.style.height = 'auto'
  element.style.height = `${Math.min(180, Math.max(22, element.scrollHeight))}px`
}

function onInput() {
  const markdown = currentMarkdown()
  emit('update:modelValue', markdown)
  autoGrow()
  // 打字过程中不回写 DOM：每 300ms 把半成品 Markdown 重新解析一次，
  // 会把「1. 有序」并进上一个列表项、按残留光标位置还原时吞字，
  // 这就是"输入 Markdown 文本出现异常"的来源。回写只在失焦和外部赋值时做。
  clearTimeout(normalizeTimer)
}

function replaceSelection(text) {
  editor.value?.focus()
  document.execCommand('insertText', false, text)
  onInput()
}

function runToolbar(item) {
  editor.value?.focus()
  const commands = {
    heading: () => document.execCommand('formatBlock', false, 'h2'),
    bold: () => document.execCommand('bold'),
    check: () => document.execCommand('insertHTML', false, '<ul><li><input type="checkbox"> </li></ul>'),
    list: () => document.execCommand('insertUnorderedList'),
    ordered: () => document.execCommand('insertOrderedList'),
    italic: () => document.execCommand('italic'),
    underline: () => document.execCommand('underline'),
    strike: () => document.execCommand('strikeThrough'),
    divider: () => document.execCommand('insertHorizontalRule'),
    time: () => document.execCommand('insertText', false, new Date().toLocaleString()),
    link: () => {
      const href = window.prompt('链接地址', 'https://')
      if (href) document.execCommand('createLink', false, href)
    },
    code: () => document.execCommand('insertHTML', false, '<code>代码</code>'),
    quote: () => document.execCommand('formatBlock', false, 'blockquote')
  }
  commands[item.key]?.()
  onInput()
}

function applyColor(color) {
  editor.value?.focus()
  document.execCommand('foreColor', false, color)
  onInput()
}

function onKeydown(event) {
  if (event.key === 'Escape' && slashOpen.value) {
    slashOpen.value = false
    event.preventDefault()
    event.stopPropagation()
    return
  }
  if (event.key === '/' && !event.ctrlKey && !event.metaKey && !event.altKey) {
    const selection = window.getSelection()
    if (!selection?.rangeCount) return
    const range = selection.getRangeAt(0)
    const prefix = range.startContainer.textContent?.slice(0, range.startOffset) || ''
    if (range.collapsed && (range.startOffset === 0 || /\s$/.test(prefix))) {
      slashRange = range.cloneRange()
      requestAnimationFrame(() => { slashOpen.value = true })
    }
  }
}

function chooseSlash(command) {
  const element = editor.value
  if (!element || !slashRange) return
  element.focus()
  const range = slashRange
  range.setEnd(range.endContainer, Math.min(range.endOffset + 1, range.endContainer.textContent?.length || 0))
  const selection = window.getSelection()
  selection.removeAllRanges()
  selection.addRange(range)
  slashOpen.value = false
  slashRange = null
  if (command.key === 'time') replaceSelection(new Date().toLocaleString())
  else replaceSelection(command.value)
  // 插入的是 Markdown 前缀（# 、- [ ] 等），立刻渲染一次让用户看到结构生效
  renderEditorMarkdown()
}

function onBlur() {
  // 不在 blur 里同步重渲染：触发 blur 的那次点击正处在 mousedown 与 mouseup 之间，
  // 这时改写编辑器 DOM 会把 mouseup 甩到被替换的节点上，表现为「第一次点按钮没反应」。
  if (!document.activeElement?.closest?.('.task-slash-menu')) slashOpen.value = false
  emit('blur')
  // 离开输入框后再把 Markdown 渲染成富文本，给点击留出完成时间
  clearTimeout(normalizeTimer)
  normalizeTimer = setTimeout(() => { if (!hasEditorFocus(editor.value)) renderEditorMarkdown() }, 160)
}

function syncEditorValue(value) {
  if (!editor.value || document.activeElement === editor.value) return
  const html = renderMarkdown(value)
  if (editor.value.innerHTML !== html) editor.value.innerHTML = html
}

onMounted(() => { syncEditorValue(props.modelValue); autoGrow() })
watch(() => props.modelValue, () => { syncEditorValue(props.modelValue); autoGrow() })
onBeforeUnmount(() => clearTimeout(normalizeTimer))
</script>

<style scoped>
.task-rich-editor{position:relative;border:1px solid var(--line);border-radius:8px;background:var(--paper);overflow:hidden}.task-rich-toolbar{display:flex;flex-wrap:wrap;gap:4px;padding:6px;border-bottom:1px solid var(--line);background:color-mix(in srgb,var(--card) 92%,var(--paper))}.task-rich-toolbar button,.task-rich-color{display:grid;position:relative;min-width:30px;height:30px;place-items:center;border:0;border-radius:5px;background:transparent;color:var(--ink);font-weight:600;cursor:pointer}.task-rich-toolbar button:hover,.task-rich-color:hover{background:color-mix(in srgb,var(--accent) 14%,transparent);color:var(--accent)}.task-rich-color input{position:absolute;inset:0;width:100%;height:100%;opacity:0;cursor:pointer}.task-rich-input-wrap{position:relative}.task-rich-input{display:block;width:100%;min-height:150px;padding:12px;border:0;resize:vertical;background:transparent;color:var(--ink);font:inherit;line-height:1.65;box-sizing:border-box;overflow-wrap:anywhere;outline:none;white-space:pre-wrap}.task-rich-input:empty::before{content:attr(data-placeholder);color:var(--muted);pointer-events:none}.task-rich-input:focus{outline:none}.task-rich-input :deep(p){margin:.45em 0}.task-rich-input :deep(h1),.task-rich-input :deep(h2),.task-rich-input :deep(h3){margin:.7em 0 .3em}.task-rich-input :deep(ul),.task-rich-input :deep(ol){padding-left:22px}.task-rich-input :deep(blockquote){margin:.5em 0;padding-left:11px;border-left:2px solid var(--line);color:var(--muted)}.task-rich-input :deep(a),.task-rich-preview :deep(a){color:var(--accent)}.task-rich-input :deep(code),.task-rich-preview :deep(code){padding:2px 4px;border-radius:3px;background:color-mix(in srgb,var(--ink) 8%,transparent)}.task-rich-preview{display:block;width:100%;min-height:150px;padding:12px;color:var(--ink);font:inherit;line-height:1.65;box-sizing:border-box}.task-rich-hint{display:block;padding:0 12px 8px;color:var(--muted);font-size:12px}.task-slash-menu{position:absolute;z-index:20;top:12px;left:12px;display:grid;min-width:190px;max-width:calc(100% - 24px);max-height:270px;overflow:auto;padding:6px;border:1px solid var(--line);border-radius:8px;background:var(--card);box-shadow:0 12px 28px rgba(15,23,42,.16)}.task-slash-menu button{display:grid;grid-template-columns:auto 1fr;gap:10px;align-items:center;padding:7px 9px;border:0;border-radius:6px;background:transparent;color:var(--ink);text-align:left;cursor:pointer}.task-slash-menu button:hover,.task-slash-menu button:focus-visible{background:var(--accent-soft);color:var(--accent);outline:none}.task-slash-menu small{color:var(--muted);font-size:11px}.markdown-body :deep(p){margin:.45em 0}.markdown-body :deep(h1),.markdown-body :deep(h2),.markdown-body :deep(h3){margin:.7em 0 .3em}.markdown-body :deep(a){color:var(--accent)}.markdown-body :deep(code){padding:2px 4px;border-radius:3px;background:color-mix(in srgb,var(--ink) 8%,transparent)}
</style>

<style scoped>
.task-rich-input :deep(h1),
.task-rich-preview :deep(h1) { font-size: 1.65em; font-weight: 700; line-height: 1.3; }
.task-rich-input :deep(h2),
.task-rich-preview :deep(h2) { font-size: 1.4em; font-weight: 700; line-height: 1.35; }
.task-rich-input :deep(h3),
.task-rich-preview :deep(h3) { font-size: 1.2em; font-weight: 700; line-height: 1.4; }

/* 紧凑模式：用在快速添加卡片这类窄容器里，去掉工具栏和提示行，随内容增高 */
.task-rich-editor.is-compact { border: 0; border-radius: 0; background: transparent; overflow: visible; }
.task-rich-editor.is-compact .task-rich-input {
  min-height: 22px;
  max-height: 180px;
  padding: 2px 0;
  overflow-y: auto;
  font-size: 13.5px;
  line-height: 1.6;
}
.task-rich-editor.is-compact .task-slash-menu {
  top: calc(100% + 4px);
  left: 0;
  min-width: min(240px, calc(100vw - 60px));
}
</style>
