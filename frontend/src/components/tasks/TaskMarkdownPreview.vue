<template>
  <div class="task-md-preview markdown-body" v-html="rendered"></div>
</template>

<script setup>
import { computed } from 'vue'
import { renderMarkdownDocument } from '../../utils/markdown.js'

const props = defineProps({ value: { type: String, default: '' } })

// 只读预览：与编辑器的渲染走同一份实现
const rendered = computed(() => renderMarkdownDocument(props.value))
</script>

<style scoped>
.task-md-preview { color: var(--ink); font: inherit; font-size: 13.5px; line-height: 1.7; overflow-wrap: anywhere; }
.task-md-preview :deep(p) { margin: .45em 0; }
.task-md-preview :deep(.md-blank) { margin: 0; min-height: .9em; }
.task-md-preview :deep(.md-blank) { margin: 0; min-height: .9em; }
.task-md-preview :deep(h1) { margin: .7em 0 .3em; font-size: 1.65em; font-weight: 700; }
.task-md-preview :deep(h2) { margin: .7em 0 .3em; font-size: 1.4em; font-weight: 700; }
.task-md-preview :deep(h3) { margin: .7em 0 .3em; font-size: 1.2em; font-weight: 700; }
.task-md-preview :deep(ul) { padding-left: 20px; list-style: disc outside; }
.task-md-preview :deep(ol) { padding-left: 22px; list-style: decimal outside; }
.task-md-preview :deep(blockquote) { margin: .5em 0; padding-left: 11px; border-left: 2px solid var(--line); color: var(--muted); }
.task-md-preview :deep(a) { color: var(--accent); }
.task-md-preview :deep(code) { padding: 2px 4px; border-radius: 3px; background: color-mix(in srgb, var(--ink) 8%, transparent); }
</style>
