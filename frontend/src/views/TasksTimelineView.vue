<template>
  <section class="timeline-page">
    <header><div><p class="eyebrow">TASKS · TIMELINE</p><h1>时间线 / 甘特</h1><p>按开始时间和截止时间查看任务安排。</p></div><button type="button" @click="store.hydrate">刷新</button></header>
    <p v-if="!items.length" class="empty">没有包含时间范围的任务</p>
    <div v-else class="timeline-list">
      <article v-for="task in items" :key="task.publicId" class="timeline-row">
        <div class="timeline-label"><strong>{{ task.title }}</strong><small>{{ dateLabel(task.startAt || task.dueAt) }} - {{ dateLabel(task.dueAt || task.startAt) }}</small></div>
        <div class="timeline-track"><span class="timeline-bar" :class="`priority-${String(task.priority).toLowerCase()}`" :style="barStyle(task)" /></div>
        <RouterLink :to="`/tasks/list/${task.listId}`">打开任务列表</RouterLink>
      </article>
    </div>
  </section>
</template>
<script setup>
import { computed, onMounted } from 'vue'
import { RouterLink } from 'vue-router'
import { useTasksStore } from '../stores/tasks.js'
const store = useTasksStore()
const items = computed(() => store.tasks.filter(task => !task.deleted && (task.startAt || task.dueAt)).sort((a,b) => new Date(a.startAt || a.dueAt) - new Date(b.startAt || b.dueAt)))
function dateLabel(value) { return value ? new Intl.DateTimeFormat('zh-CN', { month: 'numeric', day: 'numeric', hour: '2-digit', minute: '2-digit' }).format(new Date(value)) : '无日期' }
function barStyle(task) { const start = new Date(task.startAt || task.dueAt).getTime(); const end = new Date(task.dueAt || new Date(start + Math.max(60, task.durationMinutes || 60) * 60000)).getTime(); const min = Math.min(...items.value.map(item => new Date(item.startAt || item.dueAt).getTime())); const max = Math.max(...items.value.map(item => new Date(item.dueAt || item.startAt || new Date()).getTime()), min + 86400000); const span = Math.max(max - min, 86400000); return { left: `${Math.max(0, (start - min) / span * 100)}%`, width: `${Math.max(2, (end - start) / span * 100)}%` } }
onMounted(() => store.init())
</script>
<style scoped>
.timeline-page{max-width:1200px;margin:0 auto;padding:8px 0 48px}.timeline-page header{display:flex;justify-content:space-between;align-items:flex-end;margin-bottom:20px}.timeline-page h1{margin:4px 0}.timeline-page header p{color:var(--muted)}.timeline-page button{border:1px solid var(--border);background:var(--surface);padding:8px 12px;border-radius:8px}.eyebrow{font-size:12px!important;color:var(--primary)!important;letter-spacing:.12em}.timeline-list{display:grid;gap:10px}.timeline-row{display:grid;grid-template-columns:minmax(220px,28%) 1fr auto;gap:14px;align-items:center;padding:12px;border:1px solid var(--border);border-radius:10px;background:var(--surface)}.timeline-label{display:grid;gap:4px}.timeline-label small{color:var(--muted);font-size:12px}.timeline-track{height:24px;position:relative;background:color-mix(in srgb,var(--surface) 75%,var(--border) 25%);border-radius:5px}.timeline-bar{position:absolute;top:4px;height:16px;min-width:8px;border-radius:4px;background:var(--primary)}.timeline-bar.priority-high{background:#dc2626}.timeline-bar.priority-medium{background:#d97706}.timeline-bar.priority-low{background:#2563eb}.timeline-row a{color:var(--primary);font-size:13px;white-space:nowrap}.empty{color:var(--muted)}@media(max-width:800px){.timeline-row{grid-template-columns:1fr}.timeline-row a{justify-self:start}}
</style>
