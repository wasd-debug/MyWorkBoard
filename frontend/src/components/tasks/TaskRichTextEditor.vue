<template>
  <section class="task-rich-editor" :class="{ 'preview-only': previewOnly }" :aria-label="label">
    <div v-if="previewOnly" class="task-rich-preview markdown-body" v-html="rendered"></div>
    <template v-else>
    <div class="task-rich-toolbar" role="toolbar" :aria-label="`${label}工具栏`">
      <button v-for="item in toolbar" :key="item.key" type="button" :aria-label="item.label" :title="item.label" @click="insert(item.before, item.after, item.placeholder)">{{ item.icon }}</button>
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

const emit = defineEmits(['update:modelValue'])
const input = ref(null)
const preview = ref(false)
const slashOpen = ref(false)
const slashStart = ref(0)
const toolbar = [
  { key: 'bold', label: '粗体', icon: 'B', before: '**', after: '**', placeholder: '粗体' },
  { key: 'italic', label: '斜体', icon: 'I', before: '_', after: '_', placeholder: '斜体' },
  { key: 'strike', label: '删除线', icon: 'S', before: '~~', after: '~~', placeholder: '删除线' },
  { key: 'code', label: '行内代码', icon: '`', before: '`', after: '`', placeholder: '代码' },
  { key: 'quote', label: '引用', icon: '❝', before: '> ', after: '', placeholder: '引用' },
  { key: 'list', label: '无序列表', icon: '•', before: '- ', after: '', placeholder: '列表项' },
  { key: 'check', label: '检查项', icon: '☑', before: '- [ ] ', after: '', placeholder: '检查项' },
  { key: 'link', label: '链接', icon: '↗', before: '[', after: '](https://)', placeholder: '链接文本' }
]
const slashCommands = [
  { key: 'heading', label: '标题', hint: '# 标题', value: '# ' },
  { key: 'list', label: '无序列表', hint: '- 列表项', value: '- ' },
  { key: 'check', label: '检查项', hint: '- [ ] 待办', value: '- [ ] ' },
  { key: 'quote', label: '引用', hint: '> 引用内容', value: '> ' },
  { key: 'divider', label: '分割线', hint: '---', value: '---\n' },
  { key: 'code', label: '代码', hint: '`代码`', value: '`代码`' },
  { key: 'link', label: '链接', hint: '[文本](https://)', value: '[链接文本](https://)' },
]
const props = defineProps({ modelValue: { type: String, default: '' }, label: { type: String, default: '富文本' }, placeholder: { type: String, default: '输入内容…' }, previewOnly: { type: Boolean, default: false } })
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
  const next = element.value.slice(0, start) + command.value + element.value.slice(end)
  emit('update:modelValue', next)
  slashOpen.value = false
  requestAnimationFrame(() => {
    element.focus()
    const cursor = start + command.value.length
    element.setSelectionRange(cursor, cursor)
  })
}
function onBlur() {
  requestAnimationFrame(() => {
    if (document.activeElement?.closest('.task-slash-menu')) return
    slashOpen.value = false
  })
}
</script>

<style scoped>
.task-rich-editor{border:1px solid var(--line);border-radius:8px;background:var(--paper);overflow:hidden}.task-rich-toolbar{display:flex;flex-wrap:wrap;gap:4px;padding:6px;border-bottom:1px solid var(--line);background:color-mix(in srgb,var(--card) 92%,var(--paper))}.task-rich-toolbar button{min-width:30px;height:30px;border:0;border-radius:5px;background:transparent;color:var(--ink);font-weight:600;cursor:pointer}.task-rich-toolbar button:hover,.task-rich-toolbar button.active{background:color-mix(in srgb,var(--accent) 14%,transparent);color:var(--accent)}.task-rich-input-wrap{position:relative}.task-rich-input,.task-rich-preview{display:block;width:100%;min-height:150px;padding:12px;border:0;resize:vertical;background:transparent;color:var(--ink);font:inherit;line-height:1.65;box-sizing:border-box}.task-rich-input:focus{outline:2px solid color-mix(in srgb,var(--accent) 30%,transparent);outline-offset:-2px}.task-rich-preview{overflow:auto}.task-rich-hint{display:block;padding:0 12px 8px;color:var(--muted);font-size:12px}.task-slash-menu{position:absolute;z-index:20;top:12px;left:12px;display:grid;min-width:190px;max-width:calc(100% - 24px);padding:6px;border:1px solid var(--line);border-radius:8px;background:var(--card);box-shadow:0 12px 28px rgba(15,23,42,.16)}.task-slash-menu button{display:grid;grid-template-columns:auto 1fr;gap:10px;align-items:center;padding:7px 9px;border:0;border-radius:6px;background:transparent;color:var(--ink);text-align:left;cursor:pointer}.task-slash-menu button:hover,.task-slash-menu button:focus-visible{background:var(--accent-soft);color:var(--accent);outline:none}.task-slash-menu small{color:var(--muted);font-size:11px}.markdown-body :deep(p){margin:.45em 0}.markdown-body :deep(h1),.markdown-body :deep(h2),.markdown-body :deep(h3){margin:.7em 0 .3em}.markdown-body :deep(a){color:var(--accent)}.markdown-body :deep(code){padding:2px 4px;border-radius:3px;background:color-mix(in srgb,var(--ink) 8%,transparent)}
</style>
