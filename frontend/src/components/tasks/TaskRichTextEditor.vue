<template>
  <section class="task-rich-editor" :class="{ 'preview-only': previewOnly }" :aria-label="label">
    <div v-if="previewOnly" class="task-rich-preview markdown-body" v-html="rendered"></div>
    <template v-else>
    <div class="task-rich-toolbar" role="toolbar" :aria-label="`${label}工具栏`" :aria-hidden="!toolbarVisible" :inert="!toolbarVisible">
      <button v-for="item in toolbar" :key="item.key" type="button" :aria-label="item.label" :title="item.label" @click="item.key === 'time' ? insertTime() : insert(item.before, item.after, item.placeholder)"><component :is="item.icon" :size="14" /></button>
      <label class="task-rich-color" title="文字颜色" aria-label="文字颜色"><Paintbrush :size="14" /><input type="color" aria-label="选择文字颜色" @input="applyColor($event.target.value)" /></label>
      <button type="button" aria-label="切换预览" title="切换预览" :class="{ active: preview }" @click="preview = !preview">预览</button>
    </div>
    <div class="task-rich-input-wrap">
      <textarea v-if="!preview" ref="input" :value="modelValue" class="task-rich-input" :placeholder="placeholder" :aria-label="label" @input="$emit('update:modelValue', $event.target.value)" @keydown="onKeydown" @blur="onBlur"></textarea>
      <div v-if="slashOpen" class="task-slash-menu" role="listbox" :aria-label="`${label}插入菜单`" @mousedown.prevent>
        <button v-for="item in slashCommands" :key="item.key" type="button" role="option" @click="chooseSlash(item)">
          <strong>{{ item.label }}</strong><small>{{ item.hint }}</small>
        </button>
      </div>
    </div>
    <div v-if="preview" class="task-rich-preview markdown-body" aria-live="polite" v-html="rendered"></div>
    <small class="task-rich-hint">Markdown · 支持 / 插入基础格式</small>
    </template>
  </section>
</template>

<script setup>
import { computed, ref } from 'vue'
import { marked } from 'marked'
import DOMPurify from 'dompurify'
import { Bold, CheckSquare, Clock3, Code, Heading1, Italic, Link, List, ListOrdered, Minus, Paintbrush, Quote, Strikethrough, Underline } from 'lucide-vue-next'

const emit = defineEmits(['update:modelValue', 'blur'])
const input = ref(null)
const preview = ref(false)
const slashOpen = ref(false)
const slashStart = ref(0)
const toolbar = [
  { key: 'heading', label: '标题', icon: Heading1, before: '## ', after: '', placeholder: '标题' },
  { key: 'bold', label: '加粗', icon: Bold, before: '**', after: '**', placeholder: '加粗' },
  { key: 'check', label: '检查项', icon: CheckSquare, before: '- [ ] ', after: '', placeholder: '检查项' },
  { key: 'list', label: '无序列表', icon: List, before: '- ', after: '', placeholder: '列表项' },
  { key: 'ordered', label: '有序列表', icon: ListOrdered, before: '1. ', after: '', placeholder: '列表项' },
  { key: 'italic', label: '斜体', icon: Italic, before: '*', after: '*', placeholder: '斜体' },
  { key: 'underline', label: '下划线', icon: Underline, before: '<u>', after: '</u>', placeholder: '下划线' },
  { key: 'strike', label: '删除线', icon: Strikethrough, before: '~~', after: '~~', placeholder: '删除线' },
  { key: 'divider', label: '分割线', icon: Minus, before: '\n---\n', after: '', placeholder: '' },
  { key: 'time', label: '增加时间', icon: Clock3 },
  { key: 'link', label: '链接', icon: Link, before: '[', after: '](https://)', placeholder: '链接文本' },
  { key: 'code', label: '代码', icon: Code, before: '`', after: '`', placeholder: '代码' },
  { key: 'quote', label: '引用', icon: Quote, before: '> ', after: '', placeholder: '引用' }
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
  { key: 'link', label: '链接', hint: '[文本](https://)', value: '[链接文本](https://)' },
]
const props = defineProps({ modelValue: { type: String, default: '' }, label: { type: String, default: '富文本' }, placeholder: { type: String, default: '输入内容…' }, previewOnly: { type: Boolean, default: false }, toolbarVisible: { type: Boolean, default: true } })
const rendered = computed(() => DOMPurify.sanitize(marked.parse(props.modelValue || '', { async: false }), { ALLOWED_URI_REGEXP: /^(?:https?:|mailto:)/i }))
function insert(before, after, placeholder) {
  const element = input.value
  if (!element) return
  const start = element.selectionStart ?? element.value.length; const end = element.selectionEnd ?? start
  const selected = element.value.slice(start, end) || placeholder
  const next = element.value.slice(0, start) + before + selected + after + element.value.slice(end)
  emit('update:modelValue', next)
  requestAnimationFrame(() => { element.focus(); const cursor = start + before.length + selected.length + after.length; element.setSelectionRange(cursor, cursor) })
}
function insertTime() { insert('', '', new Date().toLocaleString()) }
function applyColor(color) {
  const element = input.value
  if (!element) return
  const start = element.selectionStart ?? element.value.length; const end = element.selectionEnd ?? start
  const selected = element.value.slice(start, end) || '文字'
  const next = element.value.slice(0, start) + `<span style="color: ${color}">${selected}</span>` + element.value.slice(end)
  emit('update:modelValue', next)
  requestAnimationFrame(() => { element.focus(); const cursor = start + `<span style="color: ${color}">${selected}</span>`.length; element.setSelectionRange(cursor, cursor) })
}
function onKeydown(event) {
  if (event.key === 'Escape' && slashOpen.value) {
    slashOpen.value = false
    event.preventDefault()
    return
  }
  if (event.key === '/' && !event.ctrlKey && !event.metaKey && !event.altKey) {
    const start = event.target.selectionStart
    if (start === 0 || /\n\s*$/.test(event.target.value.slice(0, start))) {
      slashStart.value = start
      requestAnimationFrame(() => { slashOpen.value = true })
    }
  }
}
function chooseSlash(command) {
  const element = input.value
  if (!element) return
  const start = slashStart.value
  const end = element.selectionStart ?? element.value.length
  const value = command.key === 'time' ? new Date().toLocaleString() : command.value
  const next = element.value.slice(0, start) + value + element.value.slice(end)
  emit('update:modelValue', next)
  slashOpen.value = false
  requestAnimationFrame(() => {
    element.focus()
    const cursor = start + value.length
    element.setSelectionRange(cursor, cursor)
  })
}
function onBlur() {
  requestAnimationFrame(() => {
    if (document.activeElement?.closest('.task-slash-menu')) return
    slashOpen.value = false
  })
  emit('blur')
}
</script>

<style scoped>
.task-rich-editor{border:1px solid var(--line);border-radius:8px;background:var(--paper);overflow:hidden}.task-rich-toolbar{display:flex;flex-wrap:wrap;gap:4px;padding:6px;border-bottom:1px solid var(--line);background:color-mix(in srgb,var(--card) 92%,var(--paper))}.task-rich-toolbar button,.task-rich-color{display:grid;position:relative;min-width:30px;height:30px;place-items:center;border:0;border-radius:5px;background:transparent;color:var(--ink);font-weight:600;cursor:pointer}.task-rich-toolbar button:hover,.task-rich-toolbar button.active,.task-rich-color:hover{background:color-mix(in srgb,var(--accent) 14%,transparent);color:var(--accent)}.task-rich-color input{position:absolute;inset:0;width:100%;height:100%;opacity:0;cursor:pointer}.task-rich-input-wrap{position:relative}.task-rich-input,.task-rich-preview{display:block;width:100%;min-height:150px;padding:12px;border:0;resize:vertical;background:transparent;color:var(--ink);font:inherit;line-height:1.65;box-sizing:border-box}.task-rich-input:focus{outline:2px solid color-mix(in srgb,var(--accent) 30%,transparent);outline-offset:-2px}.task-rich-preview{overflow:auto}.task-rich-hint{display:block;padding:0 12px 8px;color:var(--muted);font-size:12px}.task-slash-menu{position:absolute;z-index:20;top:12px;left:12px;display:grid;min-width:190px;max-width:calc(100% - 24px);padding:6px;border:1px solid var(--line);border-radius:8px;background:var(--card);box-shadow:0 12px 28px rgba(15,23,42,.16)}.task-slash-menu button{display:grid;grid-template-columns:auto 1fr;gap:10px;align-items:center;padding:7px 9px;border:0;border-radius:6px;background:transparent;color:var(--ink);text-align:left;cursor:pointer}.task-slash-menu button:hover,.task-slash-menu button:focus-visible{background:var(--accent-soft);color:var(--accent);outline:none}.task-slash-menu small{color:var(--muted);font-size:11px}.markdown-body :deep(p){margin:.45em 0}.markdown-body :deep(h1),.markdown-body :deep(h2),.markdown-body :deep(h3){margin:.7em 0 .3em}.markdown-body :deep(a){color:var(--accent)}.markdown-body :deep(code){padding:2px 4px;border-radius:3px;background:color-mix(in srgb,var(--ink) 8%,transparent)}
</style>
