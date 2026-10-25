<template>
  <section class="task-milkdown-editor" :aria-label="label" @focusin="focused = true" @focusout="leaveEditor">
    <span v-if="!modelValue && !focused" class="task-milkdown-placeholder">{{ placeholder }}</span>
    <div v-if="toolbarVisible" class="task-rich-toolbar" role="toolbar" :aria-label="`${label}工具栏`">
      <button v-for="item in toolbar" :key="item.key" type="button" :aria-label="item.label" :title="item.label" @mousedown.prevent @click="surface?.runToolbar(item.key)"><component :is="item.icon" :size="14" /></button>
    </div>
    <MilkdownProvider>
      <TaskMilkdownEditorSurface
        ref="surface"
        :model-value="modelValue"
        @update:model-value="emit('update:modelValue', $event)"
        @focus="emit('focus')"
        @blur="emit('blur')"
      />
    </MilkdownProvider>
  </section>
</template>

<script setup>
import '@milkdown/kit/prose/view/style/prosemirror.css'
import { Bold, CheckSquare, Code, Heading1, Italic, List, ListOrdered, Minus, Quote, Strikethrough } from 'lucide-vue-next'
import { ref } from 'vue'
import { MilkdownProvider } from '@milkdown/vue'
import TaskMilkdownEditorSurface from './TaskMilkdownEditorSurface.vue'

const props = defineProps({
  modelValue: { type: String, default: '' },
  label: { type: String, default: '任务描述' },
  placeholder: { type: String, default: '输入内容…' },
  toolbarVisible: { type: Boolean, default: false }
})

const emit = defineEmits(['update:modelValue', 'focus', 'blur'])
const surface = ref(null)
const focused = ref(false)
const toolbar = [
  { key: 'heading', label: '标题', icon: Heading1 },
  { key: 'bold', label: '加粗', icon: Bold },
  { key: 'check', label: '检查项', icon: CheckSquare },
  { key: 'list', label: '无序列表', icon: List },
  { key: 'ordered', label: '有序列表', icon: ListOrdered },
  { key: 'italic', label: '斜体', icon: Italic },
  { key: 'strike', label: '删除线', icon: Strikethrough },
  { key: 'divider', label: '分割线', icon: Minus },
  { key: 'code', label: '代码', icon: Code },
  { key: 'quote', label: '引用', icon: Quote }
]

function leaveEditor(event) {
  if (!event.currentTarget.contains(event.relatedTarget)) focused.value = false
}
</script>

<style scoped>
.task-milkdown-editor {
  position: relative;
  --milkdown-theme-color: var(--accent);
  min-height: 150px;
  color: var(--ink);
  font: inherit;
}

.task-milkdown-editor .task-rich-toolbar { display: flex; }

.task-milkdown-placeholder {
  position: absolute;
  z-index: 1;
  top: 12px;
  left: 12px;
  color: var(--muted);
  pointer-events: none;
}

.task-milkdown-editor :deep([data-milkdown-root]) {
  min-height: 150px;
  padding: 12px;
  color: var(--ink);
  font: inherit;
  line-height: 1.65;
  box-sizing: border-box;
}

.task-milkdown-editor :deep(.ProseMirror) {
  min-height: 126px;
  outline: none;
}

.task-milkdown-editor :deep(.ProseMirror p) {
  margin: 0.45em 0;
}

.task-milkdown-editor :deep(.ProseMirror h1),
.task-milkdown-editor :deep(.ProseMirror h2),
.task-milkdown-editor :deep(.ProseMirror h3) {
  margin: 0.7em 0 0.3em;
  color: var(--ink);
}

.task-milkdown-editor :deep(.ProseMirror h1) { font-size: 1.65em; }
.task-milkdown-editor :deep(.ProseMirror h2) { font-size: 1.4em; }
.task-milkdown-editor :deep(.ProseMirror h3) { font-size: 1.2em; }

.task-milkdown-editor :deep(.ProseMirror ul),
.task-milkdown-editor :deep(.ProseMirror ol) {
  padding-left: 22px;
}

.task-milkdown-editor :deep(.ProseMirror ul) { list-style: disc outside; }
.task-milkdown-editor :deep(.ProseMirror ol) { list-style: decimal outside; }
.task-milkdown-editor :deep(.ProseMirror li) { display: list-item; }
.task-milkdown-editor :deep(.ProseMirror li[data-item-type="task"]) {
  position: relative;
  list-style: none;
}

.task-milkdown-editor :deep(.ProseMirror li[data-item-type="task"]::before) {
  position: absolute;
  top: 0.3em;
  left: -21px;
  width: 13px;
  height: 13px;
  border: 1.5px solid var(--muted);
  border-radius: 3px;
  background: var(--card);
  box-sizing: border-box;
  content: '';
  cursor: pointer;
}

.task-milkdown-editor :deep(.ProseMirror li[data-item-type="task"]::after) {
  position: absolute;
  top: 0.34em;
  left: -17px;
  width: 4px;
  height: 7px;
  border: solid var(--card);
  border-width: 0 1.5px 1.5px 0;
  content: '';
  opacity: 0;
  pointer-events: none;
  transform: rotate(45deg);
}

.task-milkdown-editor :deep(.ProseMirror li[data-item-type="task"][data-checked="true"]::before) {
  border-color: var(--accent);
  background: var(--accent);
}

.task-milkdown-editor :deep(.ProseMirror li[data-item-type="task"][data-checked="true"]::after) {
  opacity: 1;
}

.task-milkdown-editor :deep(.ProseMirror li[data-item-type="task"][data-checked="true"] > p) {
  color: var(--muted);
  text-decoration: line-through;
}

.task-milkdown-editor :deep(.ProseMirror blockquote) {
  margin: 0.5em 0;
  padding-left: 11px;
  border-left: 2px solid var(--line);
  color: var(--muted);
}

.task-milkdown-editor :deep(.ProseMirror a) {
  color: var(--accent);
}
</style>
