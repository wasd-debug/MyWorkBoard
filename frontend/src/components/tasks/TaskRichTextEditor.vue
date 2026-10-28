<template>
  <section class="task-rich-editor" :class="{ 'preview-only': previewOnly }" :aria-label="label">
    <div v-if="previewOnly" class="task-rich-preview markdown-body" v-html="rendered"></div>
    <template v-else>
      <div class="task-rich-toolbar" role="toolbar" :aria-label="`${label}工具栏`" :aria-hidden="!toolbarVisible" :inert="!toolbarVisible">
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
          @compositionstart="composing = true"
          @compositionend="onCompositionEnd"
          @keydown="onKeydown"
          @blur="onBlur"
        ></div>
        <div v-if="slashOpen" class="task-slash-menu" role="listbox" :aria-label="`${label}插入菜单`" @mousedown.prevent>
          <button v-for="item in slashCommands" :key="item.key" type="button" role="option" @click="chooseSlash(item)">
            <strong>{{ item.label }}</strong><small>{{ item.hint }}</small>
          </button>
        </div>
      </div>
      <small class="task-rich-hint">Markdown · 支持 / 插入基础格式</small>
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
const composing = ref(false)
let slashRange = null
let renderTimer = null
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
const props = defineProps({ modelValue: { type: String, default: '' }, label: { type: String, default: '富文本' }, placeholder: { type: String, default: '输入内容…' }, previewOnly: { type: Boolean, default: false }, toolbarVisible: { type: Boolean, default: true } })
const rendered = computed(() => renderMarkdown(props.modelValue))

function renderMarkdown(value) {
  try {
    return DOMPurify.sanitize(marked.parse(value || '', { async: false }), { ALLOWED_URI_REGEXP: /^(?:https?:|mailto:)/i })
  } catch {
    // 极端内容（超长/异常 Markdown）渲染失败时降级为转义纯文本，避免抛错带崩整页
    return `<p>${String(value || '').replace(/[&<>]/g, char => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;' }[char])).slice(0, 20000)}</p>`
  }
}

function nodeMarkdown(node) {
  if (node.nodeType === Node.TEXT_NODE) return node.nodeValue || ''
  if (node.nodeType !== Node.ELEMENT_NODE) return ''
  const children = [...node.childNodes].map(nodeMarkdown).join('')
  switch (node.tagName) {
    case 'BR': return '\n'
    case 'P': return children
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
        const checkbox = item.querySelector(':scope > input[type="checkbox"]')
        const text = [...item.childNodes].filter(child => child !== checkbox && !(child.nodeType === Node.ELEMENT_NODE && ['UL', 'OL'].includes(child.tagName))).map(nodeMarkdown).join('').trim()
        const prefix = checkbox ? `- [${checkbox.checked ? 'x' : ' '}] ` : ordered ? `${index + 1}. ` : '- '
        const nested = [...item.children].filter(child => ['UL', 'OL'].includes(child.tagName)).map(nodeMarkdown).join('').trimEnd()
        return `${prefix}${text}${nested ? `\n${nested.split('\n').map(line => `  ${line}`).join('\n')}` : ''}`
      }).join('\n')
    }
    case 'LI': return children
    case 'DIV': return children
    case 'SPAN': {
      const color = node.style?.color
      return color ? `<span style="color: ${color}">${children}</span>` : children
    }
    default: return children
  }
}

function markdownFromEditor() {
  return [...(editor.value?.childNodes || [])].map(nodeMarkdown).join('\n\n').replace(/\n{3,}/g, '\n\n').trimEnd()
}

function insertCaretMarker(element) {
  const selection = window.getSelection()
  if (!selection?.rangeCount || !selection.isCollapsed || !element.contains(selection.anchorNode)) return null
  const marker = `taskeditormarker${++caretMarkerSequence}end`
  const range = selection.getRangeAt(0)
  range.insertNode(document.createTextNode(marker))
  return marker
}

function restoreCaretMarker(element, marker) {
  if (!marker) return false
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

function onInput() {
  const markdown = currentMarkdown()
  emit('update:modelValue', markdown)
  clearTimeout(renderTimer)
  if (composing.value) return
  renderTimer = setTimeout(renderEditorMarkdown, 300)
}

function onCompositionEnd() {
  composing.value = false
  onInput()
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
}

function onBlur() {
  clearTimeout(renderTimer)
  renderEditorMarkdown()
  requestAnimationFrame(() => {
    if (document.activeElement?.closest('.task-slash-menu')) return
    slashOpen.value = false
  })
  emit('blur')
}

function syncEditorValue(value) {
  if (!editor.value || document.activeElement === editor.value) return
  const html = renderMarkdown(value)
  if (editor.value.innerHTML !== html) editor.value.innerHTML = html
}

onMounted(() => syncEditorValue(props.modelValue))
watch(() => props.modelValue, syncEditorValue)
onBeforeUnmount(() => clearTimeout(renderTimer))
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
</style>
