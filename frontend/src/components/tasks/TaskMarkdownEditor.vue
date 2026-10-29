<template>
  <section class="task-md-editor" :class="{ 'is-compact': compact }" :aria-label="label">
    <textarea
      ref="textarea"
      class="task-md-input"
      :value="modelValue"
      :rows="compact ? 1 : 6"
      :placeholder="placeholder"
      :aria-label="label"
      spellcheck="false"
      @input="onInput"
      @keydown="onKeydown"
      @focus="emit('focus')"
      @blur="emit('blur')"
    ></textarea>

    <div v-if="slashOpen && filtered.length" class="task-md-slash" role="listbox" :aria-label="`${label}插入菜单`">
      <button
        v-for="(item, index) in filtered"
        :key="item.key"
        type="button"
        role="option"
        :aria-selected="index === slashActive"
        :class="{ on: index === slashActive }"
        @mousedown.prevent
        @click="chooseSlash(item)"
      >
        <strong>{{ item.label }}</strong><small>{{ item.hint }}</small>
      </button>
    </div>

    <small v-if="!compact" class="task-md-hint">Markdown · 支持 / 插入格式</small>
  </section>
</template>

<script setup>
import { computed, nextTick, onMounted, ref, watch } from 'vue'

const props = defineProps({
  modelValue: { type: String, default: '' },
  label: { type: String, default: '任务描述' },
  placeholder: { type: String, default: '输入内容，支持 Markdown' },
  compact: { type: Boolean, default: false }
})

const emit = defineEmits(['update:modelValue', 'focus', 'blur'])

const SLASH_COMMANDS = [
  { key: 'heading', label: '标题', hint: '# 标题', value: '# ' },
  { key: 'bold', label: '加粗', hint: '**文本**', value: '****', caret: -2 },
  { key: 'italic', label: '斜体', hint: '*文本*', value: '**', caret: -1 },
  { key: 'strike', label: '删除线', hint: '~~文本~~', value: '~~~~', caret: -2 },
  { key: 'list', label: '无序列表', hint: '- 列表项', value: '- ' },
  { key: 'ordered', label: '有序列表', hint: '1. 列表项', value: '1. ' },
  { key: 'check', label: '检查项', hint: '- [ ] 待办', value: '- [ ] ' },
  { key: 'quote', label: '引用', hint: '> 引用内容', value: '> ' },
  { key: 'divider', label: '分割线', hint: '---', value: '---\n' },
  { key: 'code', label: '代码', hint: '`代码`', value: '``', caret: -1 },
  { key: 'link', label: '链接', hint: '[文本](https://)', value: '[链接文本](https://)' }
]

const textarea = ref(null)
const slashOpen = ref(false)
const slashQuery = ref('')
const slashStart = ref(-1)
const slashActive = ref(0)

const filtered = computed(() => {
  const query = slashQuery.value.trim().toLowerCase()
  if (!query) return SLASH_COMMANDS
  return SLASH_COMMANDS.filter(item => `${item.label} ${item.key} ${item.hint}`.toLowerCase().includes(query))
})

function autoGrow() {
  const el = textarea.value
  if (!el) return
  el.style.height = 'auto'
  const max = props.compact ? 200 : 520
  el.style.height = `${Math.min(max, Math.max(props.compact ? 22 : 120, el.scrollHeight))}px`
}

function onInput(event) {
  const el = event.target
  emit('update:modelValue', el.value)
  autoGrow()
  syncSlash(el.value, el.selectionStart)
}

/** 行首或空白之后输入 / 时打开插入菜单 */
function syncSlash(value, caret) {
  const before = value.slice(0, caret)
  const match = /(?:^|\n)[ \t]*\/([^\s/]*)$/.exec(before)
  if (!match) {
    slashOpen.value = false
    return
  }
  slashQuery.value = match[1] || ''
  slashStart.value = caret - (match[1] || '').length - 1
  slashActive.value = 0
  slashOpen.value = true
}

function insertText(text, caretOffset = 0) {
  const el = textarea.value
  if (!el) return
  const start = el.selectionStart ?? el.value.length
  const end = el.selectionEnd ?? start
  el.setRangeText(text, start, end, 'end')
  const caret = start + text.length + caretOffset
  el.setSelectionRange(caret, caret)
  emit('update:modelValue', el.value)
  autoGrow()
  el.focus()
}

function chooseSlash(item) {
  const el = textarea.value
  if (!el) return
  const start = slashStart.value >= 0 ? slashStart.value : el.selectionStart
  const end = el.selectionStart
  el.setRangeText(item.value, start, end, 'end')
  const caret = start + item.value.length + (item.caret || 0)
  el.setSelectionRange(caret, caret)
  emit('update:modelValue', el.value)
  slashOpen.value = false
  autoGrow()
  el.focus()
}

function onKeydown(event) {
  if (slashOpen.value && filtered.value.length) {
    if (event.key === 'ArrowDown') {
      event.preventDefault()
      slashActive.value = (slashActive.value + 1) % filtered.value.length
      return
    }
    if (event.key === 'ArrowUp') {
      event.preventDefault()
      slashActive.value = (slashActive.value - 1 + filtered.value.length) % filtered.value.length
      return
    }
    if (event.key === 'Enter') {
      event.preventDefault()
      chooseSlash(filtered.value[slashActive.value])
      return
    }
  }
  if (event.key === 'Escape' && slashOpen.value) {
    event.preventDefault()
    event.stopPropagation()
    slashOpen.value = false
    return
  }
  if (event.key === 'Tab') {
    // 让 Tab 缩进而不是跳走，Markdown 里常用
    event.preventDefault()
    insertText('  ')
  }
}

/** 在行首加前缀（标题/列表/引用等）；当前行已有内容时另起一行，避免把前缀塞到行中间 */
function prefixLine(prefix) {
  const el = textarea.value
  if (!el) return
  const value = el.value
  const caret = el.selectionStart ?? value.length
  const lineStart = value.lastIndexOf('\n', caret - 1) + 1
  const lineEndRaw = value.indexOf('\n', caret)
  const lineEnd = lineEndRaw === -1 ? value.length : lineEndRaw
  const lineHasContent = value.slice(lineStart, lineEnd).trim() !== ''
  if (lineHasContent) {
    const insertAt = lineEnd
    el.setRangeText(`\n${prefix}`, insertAt, insertAt, 'end')
    const next = insertAt + prefix.length + 1
    el.setSelectionRange(next, next)
  } else {
    el.setRangeText(prefix, lineStart, lineStart, 'end')
    el.setSelectionRange(lineStart + prefix.length, lineStart + prefix.length)
  }
  emit('update:modelValue', el.value)
  autoGrow()
  el.focus()
}

/** 包裹选中文本，没有选区就把光标放在中间 */
function wrapSelection(mark) {
  const el = textarea.value
  if (!el) return
  const start = el.selectionStart ?? 0
  const end = el.selectionEnd ?? start
  const selected = el.value.slice(start, end)
  const text = `${mark}${selected}${mark}`
  el.setRangeText(text, start, end, 'end')
  const caret = selected ? start + text.length : start + mark.length
  el.setSelectionRange(caret, caret)
  emit('update:modelValue', el.value)
  autoGrow()
  el.focus()
}

/** 供详情栏「+ 插入」菜单调用 */
function runToolbar(key) {
  const prefixMap = {
    heading: '# ',
    heading1: '# ',
    heading2: '## ',
    heading3: '### ',
    list: '- ',
    ordered: '1. ',
    check: '- [ ] ',
    quote: '> '
  }
  if (prefixMap[key]) {
    prefixLine(prefixMap[key])
    return
  }
  if (key === 'bold') return wrapSelection('**')
  if (key === 'italic') return wrapSelection('*')
  if (key === 'strike') return wrapSelection('~~')
  if (key === 'code') return wrapSelection('`')
  if (key === 'divider') return insertText('\n\n---\n\n')
  if (key === 'link') return insertText('[链接文本](https://)')
}

function insertLink(text, href) {
  insertText(`[${text}](${href || ''})`)
}

function focusEditor() {
  const el = textarea.value
  if (!el) return
  el.focus()
  const end = el.value.length
  el.setSelectionRange(end, end)
}

defineExpose({ runToolbar, insertLink, focusEditor })

// 换任务、切换卡片时外部值会变，高度要跟着重算
onMounted(() => nextTick(autoGrow))
watch(() => props.modelValue, () => nextTick(autoGrow))
</script>

<style scoped>
.task-md-editor { position: relative; display: flex; flex-direction: column; gap: 2px; }
.task-md-input {
  display: block;
  width: 100%;
  min-height: 150px;
  padding: 10px 12px;
  border: 1px solid var(--line);
  border-radius: 8px;
  background: var(--paper);
  color: var(--ink);
  font: inherit;
  font-size: 13.5px;
  line-height: 1.65;
  resize: vertical;
  outline: none;
  box-sizing: border-box;
  overflow-wrap: anywhere;
}
.task-md-input:focus { border-color: var(--accent); outline: none; }
.task-md-input::placeholder { color: var(--muted); }
.task-md-hint { padding: 0 2px; color: var(--muted); font-size: 11.5px; }

.task-md-editor.is-compact .task-md-input {
  min-height: 22px;
  padding: 2px 0;
  border: 0;
  border-radius: 0;
  background: transparent;
  font-size: 13.5px;
  resize: none;
  overflow: hidden;
}
.task-md-editor.is-compact .task-md-input:focus { border: 0; outline: none; }

.task-md-slash {
  position: absolute;
  z-index: 20;
  top: calc(100% - 4px);
  left: 0;
  display: grid;
  width: min(240px, calc(100vw - 60px));
  max-height: 260px;
  overflow: auto;
  padding: 6px;
  border: 1px solid var(--line2);
  border-radius: 8px;
  background: var(--card);
  box-shadow: 0 12px 28px rgb(15 23 42 / 16%);
}
.task-md-slash button {
  display: grid;
  grid-template-columns: auto 1fr;
  gap: 10px;
  align-items: center;
  padding: 7px 9px;
  border: 0;
  border-radius: 6px;
  background: transparent;
  color: var(--ink);
  text-align: left;
  cursor: pointer;
}
.task-md-slash button small { color: var(--muted); font-size: 11px; }
.task-md-slash button:hover,
.task-md-slash button.on { background: var(--accent-soft, color-mix(in srgb, var(--accent) 12%, transparent)); color: var(--accent); }
</style>
