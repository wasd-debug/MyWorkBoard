<template>
  <section ref="rootEl" class="task-md-editor" :class="{ 'is-compact': compact }" :aria-label="label" @mousedown="onRootMouseDown">
    <div class="task-md-blocks">
      <template v-for="(block, index) in blocks" :key="index">
        <div v-if="index === activeIndex" class="task-md-block is-editing">
          <textarea
            class="task-md-input"
            :value="draft"
            rows="1"
            :placeholder="index === 0 ? placeholder : ''"
            :aria-label="label"
            spellcheck="false"
            @input="onDraftInput"
            @keydown="onDraftKeydown"
            @blur="onDraftBlur"
            @compositionstart="composing = true"
            @compositionend="composing = false"
          ></textarea>
          <div v-if="slashOpen && filtered.length" class="task-md-slash" role="listbox" :aria-label="`${label}插入菜单`">
            <button
              v-for="(item, idx) in filtered"
              :key="item.key"
              type="button"
              role="option"
              :aria-selected="idx === slashActive"
              :class="{ on: idx === slashActive }"
              @mousedown.prevent
              @click="chooseSlash(item)"
            >
              <strong>{{ item.label }}</strong><small>{{ item.hint }}</small>
            </button>
          </div>
        </div>
        <div
          v-else
          class="task-md-block is-rendered"
          :class="{ 'is-empty': !block.trim() }"
          @click="startEdit(index, $event)"
        >
          <div v-if="block.trim()" class="markdown-body" v-html="render(block)"></div>
          <span v-else-if="blocks.length === 1" class="task-md-placeholder">{{ placeholder }}</span>
        </div>
      </template>
    </div>
    <small v-if="!compact" class="task-md-hint">Markdown · 回车后渲染，/ 插入格式</small>
  </section>
</template>

<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { renderMarkdown } from '../../utils/markdown.js'

const props = defineProps({
  modelValue: { type: String, default: '' },
  label: { type: String, default: '任务描述' },
  placeholder: { type: String, default: '输入内容，支持 Markdown' },
  compact: { type: Boolean, default: false }
})

const emit = defineEmits(['update:modelValue', 'focus', 'blur'])

const SLASH_COMMANDS = [
  { key: 'heading', label: '标题', hint: '# 标题', value: '# ' },
  { key: 'heading2', label: '二级标题', hint: '## 标题', value: '## ' },
  { key: 'bold', label: '加粗', hint: '**文本**', value: '**粗体**' },
  { key: 'italic', label: '斜体', hint: '*文本*', value: '*斜体*' },
  { key: 'strike', label: '删除线', hint: '~~文本~~', value: '~~删除线~~' },
  { key: 'list', label: '无序列表', hint: '- 列表项', value: '- ' },
  { key: 'ordered', label: '有序列表', hint: '1. 列表项', value: '1. ' },
  { key: 'check', label: '检查项', hint: '- [ ] 待办', value: '- [ ] ' },
  { key: 'quote', label: '引用', hint: '> 引用内容', value: '> ' },
  { key: 'divider', label: '分割线', hint: '---', value: '---' },
  { key: 'code', label: '代码', hint: '`代码`', value: '`代码`' },
  { key: 'link', label: '链接', hint: '[文本](https://)', value: '[链接文本](https://)' }
]

/** 每一行是一个块：正在编辑的那块是 textarea，其余渲染成 Markdown */
const blocks = ref(toBlocks(props.modelValue))
const activeIndex = ref(-1)
const draft = ref('')
const rootEl = ref(null)
const composing = ref(false)
const slashOpen = ref(false)
const slashQuery = ref('')
const slashStart = ref(-1)
const slashActive = ref(0)
let lastEmitted = props.modelValue

/** 编辑中的 textarea：放在 v-for 里的 ref 会被 Vue 收成数组，这里直接从根节点取 */
function inputEl() {
  return rootEl.value?.querySelector('textarea.task-md-input') || null
}

function toBlocks(value) {
  const text = String(value ?? '')
  if (!text) return ['']
  return text.split('\n')
}

function fromBlocks(list) {
  return list.join('\n')
}

function render(block) {
  return renderMarkdown(block)
}

function emitValue() {
  const next = fromBlocks(blocks.value)
  if (next === lastEmitted) return
  lastEmitted = next
  emit('update:modelValue', next)
}

const filtered = computed(() => {
  const query = slashQuery.value.trim().toLowerCase()
  if (!query) return SLASH_COMMANDS
  return SLASH_COMMANDS.filter(item => `${item.label} ${item.key} ${item.hint}`.toLowerCase().includes(query))
})

function autoGrow() {
  const el = inputEl()
  if (!el) return
  el.style.height = 'auto'
  el.style.height = `${Math.min(props.compact ? 200 : 320, Math.max(props.compact ? 22 : 24, el.scrollHeight))}px`
}

async function startEdit(index, event) {
  if (activeIndex.value === index) return
  if (activeIndex.value >= 0) commitDraft()
  activeIndex.value = index
  draft.value = blocks.value[index] ?? ''
  await nextTick()
  const el = inputEl()
  if (!el) return
  el.focus()
  // 点哪儿就把光标放哪儿：靠左点到行首，其它位置落到行尾
  const rect = event?.currentTarget?.getBoundingClientRect?.()
  const atStart = rect && event.clientX - rect.left < 24
  const pos = atStart ? 0 : el.value.length
  el.setSelectionRange(pos, pos)
  autoGrow()
  emit('focus')
}

function commitDraft() {
  if (activeIndex.value < 0) return
  blocks.value[activeIndex.value] = draft.value
  emitValue()
}

function onDraftInput(event) {
  draft.value = event.target.value
  blocks.value[activeIndex.value] = draft.value
  emitValue()
  autoGrow()
  syncSlash(draft.value, event.target.selectionStart)
}

/** 行首输入 / 打开插入菜单 */
function syncSlash(value, caret) {
  const match = /^\s*\/([^\s/]*)$/.exec(value.slice(0, caret))
  if (!match) {
    slashOpen.value = false
    return
  }
  slashQuery.value = match[1] || ''
  slashStart.value = caret - (slashQuery.value || '').length - 1
  slashActive.value = 0
  slashOpen.value = true
}

function chooseSlash(item) {
  const el = inputEl()
  if (!el) return
  const start = slashStart.value >= 0 ? slashStart.value : 0
  el.setRangeText(item.value, start, el.selectionStart ?? start, 'end')
  const caret = start + item.value.length
  el.setSelectionRange(caret, caret)
  draft.value = el.value
  blocks.value[activeIndex.value] = draft.value
  slashOpen.value = false
  emitValue()
  autoGrow()
  el.focus()
}

function onDraftKeydown(event) {
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
    if (event.key === 'Escape') {
      event.preventDefault()
      event.stopPropagation()
      slashOpen.value = false
      return
    }
  }

  if (event.key === 'Enter' && !event.shiftKey) {
    // 输入法组合中的回车是选词，不是换行
    if (composing.value || event.isComposing) return
    event.preventDefault()
    splitBlock()
    return
  }

  if (event.key === 'Backspace' && !draft.value && blocks.value.length > 1) {
    event.preventDefault()
    mergeWithPrevious()
    return
  }

  const el = inputEl()
  if (event.key === 'ArrowUp' && (el?.selectionStart ?? 0) === 0 && activeIndex.value > 0) {
    event.preventDefault()
    moveTo(activeIndex.value - 1, 'end')
    return
  }
  if (event.key === 'ArrowDown' && el && (el.selectionStart ?? 0) === el.value.length && activeIndex.value < blocks.value.length - 1) {
    event.preventDefault()
    moveTo(activeIndex.value + 1, 'start')
  }
}

/** 回车：提交当前行并在下面开新行 —— 换行后上一行就渲染成 Markdown 了 */
function splitBlock() {
  const index = activeIndex.value
  blocks.value[index] = draft.value
  blocks.value.splice(index + 1, 0, '')
  activeIndex.value = index + 1
  draft.value = ''
  slashOpen.value = false
  emitValue()
  nextTick(() => {
    const el = inputEl()
    el?.focus()
    el?.setSelectionRange(0, 0)
    autoGrow()
  })
}

function mergeWithPrevious() {
  const index = activeIndex.value
  if (index <= 0) return
  const previous = blocks.value[index - 1] || ''
  blocks.value.splice(index, 1)
  activeIndex.value = index - 1
  draft.value = previous
  blocks.value[index - 1] = previous
  emitValue()
  nextTick(() => {
    const el = inputEl()
    el?.focus()
    el?.setSelectionRange(previous.length, previous.length)
    autoGrow()
  })
}

async function moveTo(index, position) {
  commitDraft()
  activeIndex.value = index
  draft.value = blocks.value[index] ?? ''
  await nextTick()
  const el = inputEl()
  if (!el) return
  el.focus()
  const pos = position === 'end' ? el.value.length : 0
  el.setSelectionRange(pos, pos)
  autoGrow()
}

function onDraftBlur() {
  commitDraft()
  slashOpen.value = false
  emit('blur')
  // 焦点彻底离开本组件时退出编辑态，让所有行都渲染出来；
  // 点了本组件里另一个块的情况，焦点还在组件内，保持不变。
  window.setTimeout(() => {
    if (rootEl.value?.contains(document.activeElement)) return
    activeIndex.value = -1
  }, 120)
}

function onRootMouseDown(event) {
  if (event.target?.closest?.('.task-md-block')) return
  focusEditor()
}

/** 让某处进入编辑态（详情栏「+ 插入」菜单会先调它） */
function focusEditor() {
  if (activeIndex.value >= 0) {
    inputEl()?.focus()
    return
  }
  const index = blocks.value.length - 1
  activeIndex.value = index
  draft.value = blocks.value[index] ?? ''
  nextTick(() => {
    const el = inputEl()
    el?.focus()
    el?.setSelectionRange(el.value.length, el.value.length)
    autoGrow()
  })
}

/** 详情栏「+ 插入」菜单 */
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
  const wrapperMap = { bold: '**粗体**', italic: '*斜体*', strike: '~~删除线~~', code: '`代码`' }
  const insertion = prefixMap[key] ?? wrapperMap[key] ?? (key === 'divider' ? '---' : key === 'link' ? '[链接文本](https://)' : '')
  if (!insertion) return

  const el = inputEl()
  if (activeIndex.value >= 0 && el && !el.value.trim()) {
    el.setRangeText(insertion, 0, el.value.length, 'end')
    draft.value = el.value
    blocks.value[activeIndex.value] = draft.value
    emitValue()
    el.focus()
    return
  }
  if (activeIndex.value >= 0) commitDraft()
  const index = activeIndex.value >= 0 ? activeIndex.value + 1 : blocks.value.length
  blocks.value.splice(index, 0, insertion)
  activeIndex.value = index
  draft.value = insertion
  emitValue()
  nextTick(() => {
    const input = inputEl()
    input?.focus()
    input?.setSelectionRange(insertion.length, insertion.length)
    autoGrow()
  })
}

function insertLink(text, href) {
  const snippet = `[${text}](${href || ''})`
  const el = inputEl()
  if (activeIndex.value >= 0 && el) {
    const start = el.selectionStart ?? el.value.length
    el.setRangeText(snippet, start, start, 'end')
    draft.value = el.value
    blocks.value[activeIndex.value] = draft.value
    emitValue()
    el.focus()
    return
  }
  const index = blocks.value.length - 1
  blocks.value[index] = `${blocks.value[index] || ''}${snippet}`
  emitValue()
}

/** 外部值变化（换任务、切卡片）时重建块；正在编辑时不动，避免打断输入 */
watch(() => props.modelValue, value => {
  if (value === lastEmitted) return
  if (activeIndex.value >= 0) return
  blocks.value = toBlocks(value)
  lastEmitted = value
})

onMounted(() => { lastEmitted = props.modelValue })
onBeforeUnmount(() => { commitDraft() })

defineExpose({ runToolbar, insertLink, focusEditor })
</script>

<style scoped>
.task-md-editor { position: relative; display: flex; flex-direction: column; gap: 2px; }
.task-md-blocks { display: flex; flex-direction: column; gap: 1px; }

.task-md-block { position: relative; min-height: 22px; }
.task-md-block.is-rendered { cursor: text; }
.task-md-block.is-rendered.is-empty { min-height: 22px; }
.task-md-block.is-rendered :deep(> div) > :first-child { margin-top: 0; }
.task-md-block.is-rendered :deep(> div) > :last-child { margin-bottom: 0; }
.task-md-block :deep(p) { margin: 2px 0; }
.task-md-block :deep(h1) { margin: 6px 0 2px; font-size: 1.55em; font-weight: 700; line-height: 1.35; }
.task-md-block :deep(h2) { margin: 5px 0 2px; font-size: 1.32em; font-weight: 700; line-height: 1.4; }
.task-md-block :deep(h3) { margin: 4px 0 2px; font-size: 1.15em; font-weight: 700; line-height: 1.45; }
.task-md-block :deep(ul) { margin: 2px 0; padding-left: 20px; list-style: disc outside; }
.task-md-block :deep(ol) { margin: 2px 0; padding-left: 22px; list-style: decimal outside; }
.task-md-block :deep(li) { margin: 1px 0; }
.task-md-block :deep(blockquote) { margin: 3px 0; padding-left: 10px; border-left: 2px solid var(--line); color: var(--muted); }
.task-md-block :deep(code) { padding: 1px 4px; border-radius: 3px; background: color-mix(in srgb, var(--ink) 8%, transparent); }
.task-md-block :deep(a) { color: var(--accent); }
.task-md-block :deep(hr) { margin: 6px 0; border: 0; border-top: 1px solid var(--line); }
.task-md-block :deep(input[type='checkbox']) { margin-right: 6px; }

.task-md-placeholder { color: var(--muted); }

.task-md-input {
  display: block;
  width: 100%;
  min-height: 24px;
  padding: 2px 0;
  border: 0;
  border-radius: 0;
  background: transparent;
  color: var(--ink);
  font: inherit;
  font-size: 13.5px;
  line-height: 1.7;
  resize: none;
  outline: none;
  box-sizing: border-box;
  overflow: hidden;
  overflow-wrap: anywhere;
}
.task-md-input::placeholder { color: var(--muted); }
/* 全局 :where(...):focus-visible 会加一圈 4px 光晕，这里去掉，只保留细描边 */
.task-md-input, .task-md-input:focus, .task-md-input:focus-visible { box-shadow: none; }
/* 全局 :where(...):focus-visible 会加一圈 4px 光晕，这里去掉，只保留细描边 */
.task-md-input, .task-md-input:focus, .task-md-input:focus-visible { box-shadow: none; }

.task-md-hint { padding: 2px 0 0; color: var(--muted); font-size: 11.5px; }

.task-md-slash {
  position: absolute;
  z-index: 20;
  top: calc(100% + 2px);
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
