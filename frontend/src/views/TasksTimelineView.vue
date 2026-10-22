<template>
  <section class="timeline-page">
    <header class="timeline-head">
      <div><p class="eyebrow">TASKS · TIMELINE</p><h1>时间线 / 甘特</h1></div>
      <div class="timeline-controls">
        <label>分组<select v-model="groupBy" aria-label="时间线分组"><option value="none">无</option><option value="list">清单</option><option value="tag">标签</option><option value="priority">优先级</option></select></label>
        <label>排序<select v-model="sortBy" aria-label="时间线排序"><option value="default">默认</option><option value="time">按时间</option></select></label>
        <div class="timeline-density" role="group" aria-label="时间线密度"><button v-for="option in densityOptions" :key="option.value" type="button" :class="{ active: density === option.value }" :aria-pressed="density === option.value" @click="setDensity(option.value)">{{ option.label }}</button></div>
        <button type="button" class="timeline-today" @click="goToday">今天</button>
      </div>
    </header>
    <div class="timeline-period">
      <button type="button" aria-label="上一时间段" @click="movePeriod(-1)"><ChevronLeft :size="16" /></button>
      <strong>{{ rangeLabel }}</strong>
      <button type="button" aria-label="下一时间段" @click="movePeriod(1)"><ChevronRight :size="16" /></button>
    </div>
    <p v-if="!groups.length" class="timeline-empty">没有包含时间范围的任务</p>
    <div v-else ref="scroller" class="timeline-scroll">
      <div class="timeline-grid" :style="gridStyle">
        <div class="timeline-axis-row"><div class="timeline-task-heading">任务</div><div class="timeline-axis">
          <div v-for="(tick, index) in ticks" :key="tick.key" class="timeline-tick" :class="{ 'is-today': tick.today }"><span>{{ tick.label }}</span><i v-if="tick.today" ref="todayMarker" aria-label="今天"></i></div>
        </div></div>
        <template v-for="group in groups" :key="group.key">
          <button v-if="groupBy !== 'none'" class="timeline-group-heading" type="button" :aria-expanded="!collapsed[group.key]" @click="collapsed[group.key] = !collapsed[group.key]">
            <ChevronDown :size="14" :class="{ rotated: collapsed[group.key] }" /><span>{{ group.label }}</span><small>{{ group.tasks.length }}</small>
          </button>
          <div v-for="task in (collapsed[group.key] ? [] : group.tasks)" :key="task.publicId" class="timeline-row">
            <div class="timeline-label"><strong>{{ task.title }}</strong><small>{{ dateLabel(task.startAt || task.dueAt) }} - {{ dateLabel(task.dueAt || task.startAt) }}</small></div>
            <div class="timeline-track" :class="{ 'is-outside': !intersectsRange(task) }">
              <div class="timeline-today-line" :style="todayLineStyle"></div>
              <span v-if="intersectsRange(task)" class="timeline-bar" :class="[`priority-${String(task.priority).toLowerCase()}`, { milestone: isMilestone(task) }]" :style="barStyle(task)" :title="task.title"></span>
            </div>
            <RouterLink :to="`/tasks/list/${task.listId}`">打开任务列表</RouterLink>
          </div>
        </template>
      </div>
    </div>
  </section>
</template>

<script setup>
import { computed, onMounted, reactive, ref, watch, nextTick } from 'vue'
import { RouterLink } from 'vue-router'
import { ChevronDown, ChevronLeft, ChevronRight } from 'lucide-vue-next'
import { useTasksStore } from '../stores/tasks.js'
import { groupTaskItems } from './taskList.js'

const store = useTasksStore()
const densityOptions = [{ value: 'day', label: '日' }, { value: 'week', label: '周' }, { value: 'month', label: '月' }]
const density = ref('week')
const groupBy = ref('list')
const sortBy = ref('default')
const anchor = ref(startOfDay(new Date()))
const scroller = ref(null)
const todayMarker = ref(null)
const collapsed = reactive({})

const ticks = computed(() => {
  const start = rangeStart.value
  if (density.value === 'day') return Array.from({ length: 14 }, (_, index) => {
    const date = addDays(start, index)
    return { key: dateKey(date), date, label: new Intl.DateTimeFormat('zh-CN', { month: 'numeric', day: 'numeric' }).format(date), today: sameDay(date, new Date()) }
  })
  if (density.value === 'week') return Array.from({ length: 12 }, (_, index) => {
    const date = addDays(start, index * 7)
    const end = addDays(date, 6)
    return { key: dateKey(date), date, end, label: `${date.getMonth() + 1}/${date.getDate()}`, today: new Date() >= date && new Date() <= end }
  })
  return Array.from({ length: 12 }, (_, index) => {
    const date = new Date(start.getFullYear(), start.getMonth() + index, 1)
    return { key: dateKey(date), date, end: new Date(date.getFullYear(), date.getMonth() + 1, 0), label: `${date.getFullYear()}.${date.getMonth() + 1}`, today: date.getFullYear() === new Date().getFullYear() && date.getMonth() === new Date().getMonth() }
  })
})
const rangeStart = computed(() => {
  if (density.value === 'day') return addDays(anchor.value, -6)
  if (density.value === 'week') {
    const start = startOfDay(anchor.value)
    start.setDate(start.getDate() - ((start.getDay() + 6) % 7) - 28)
    return start
  }
  return new Date(anchor.value.getFullYear(), anchor.value.getMonth() - 5, 1)
})
const rangeEnd = computed(() => density.value === 'month'
  ? new Date(rangeStart.value.getFullYear(), rangeStart.value.getMonth() + 12, 1)
  : addDays(rangeStart.value, density.value === 'day' ? 14 : 84))
const rangeLabel = computed(() => {
  const first = ticks.value[0]?.date
  const last = ticks.value.at(-1)?.end || ticks.value.at(-1)?.date
  if (!first || !last) return ''
  const format = value => new Intl.DateTimeFormat('zh-CN', { year: 'numeric', month: 'numeric', day: 'numeric' }).format(value)
  return `${format(first)} - ${format(last)}`
})
const gridStyle = computed(() => ({ '--timeline-label-width': '210px', '--timeline-tick-width': density.value === 'day' ? '54px' : density.value === 'week' ? '104px' : '142px', '--timeline-column-count': ticks.value.length }))
const todayIndex = computed(() => ticks.value.findIndex(tick => tick.today || sameDay(tick.date, new Date())))
const todayLineStyle = computed(() => ({ left: `${Math.max(0, todayIndex.value) * 100 / ticks.value.length}%` }))
const datedTasks = computed(() => store.tasks.filter(task => !task.deleted && task.status !== 'ABANDONED' && (task.startAt || task.dueAt)))
const groups = computed(() => {
  const rows = sortBy.value === 'time'
    ? [...datedTasks.value].sort((a, b) => new Date(a.startAt || a.dueAt) - new Date(b.startAt || b.dueAt))
    : [...datedTasks.value].sort((a, b) => Number(a.sortOrder || 0) - Number(b.sortOrder || 0))
  if (groupBy.value === 'none') return rows.length ? [{ key: 'all', label: '所有任务', tasks: rows }] : []
  return groupTaskItems(rows, groupBy.value, store.lists, new Date(), store.tags)
})

function startOfDay(value) { const date = new Date(value); date.setHours(0, 0, 0, 0); return date }
function addDays(value, days) { const date = new Date(value); date.setDate(date.getDate() + days); return date }
function sameDay(left, right) { return left.getFullYear() === right.getFullYear() && left.getMonth() === right.getMonth() && left.getDate() === right.getDate() }
function dateKey(value) { return `${value.getFullYear()}-${String(value.getMonth() + 1).padStart(2, '0')}-${String(value.getDate()).padStart(2, '0')}` }
function dateLabel(value) { return value ? new Intl.DateTimeFormat('zh-CN', { month: 'numeric', day: 'numeric' }).format(new Date(value)) : '无日期' }
function taskRange(task) {
  const start = new Date(task.startAt || task.dueAt).getTime()
  const due = task.dueAt ? new Date(task.dueAt).getTime() : null
  const duration = Math.max(60, Number(task.durationMinutes || 60)) * 60000
  const end = due && due > start ? due : task.startAt ? start + duration : start + 60000
  return { start, end }
}
function intersectsRange(task) { const { start, end } = taskRange(task); return end >= rangeStart.value.getTime() && start < rangeEnd.value.getTime() }
function isMilestone(task) { return !task.startAt || !task.dueAt || new Date(task.dueAt).getTime() <= new Date(task.startAt || task.dueAt).getTime() }
function barStyle(task) {
  const { start, end } = taskRange(task)
  const from = rangeStart.value.getTime(), span = rangeEnd.value.getTime() - from
  const clippedStart = Math.max(from, start), clippedEnd = Math.min(rangeEnd.value.getTime(), end)
  const left = Math.max(0, (clippedStart - from) / span * 100)
  const width = Math.max(density.value === 'month' ? 0.7 : 0.4, (clippedEnd - clippedStart) / span * 100)
  return { left: `${left}%`, width: isMilestone(task) ? '12px' : `${width}%` }
}
function setDensity(value) { density.value = value }
function movePeriod(direction) {
  const date = new Date(anchor.value)
  if (density.value === 'day') date.setDate(date.getDate() + direction * 14)
  else if (density.value === 'week') date.setDate(date.getDate() + direction * 84)
  else {
    anchor.value = new Date(date.getFullYear(), date.getMonth() + direction * 12, 1)
    return
  }
  anchor.value = date
}
async function goToday() {
  anchor.value = startOfDay(new Date())
  await nextTick()
  if (scroller.value) {
    const track = scroller.value.querySelector('.timeline-track')
    const width = track?.clientWidth || 0
    const tickWidth = width / ticks.value.length
    const index = Math.max(0, todayIndex.value)
    scroller.value.scrollTo({ left: Math.max(0, index * tickWidth - scroller.value.clientWidth / 2), behavior: 'smooth' })
  }
}
watch(density, () => { void nextTick(() => { if (scroller.value) scroller.value.scrollLeft = 0 }) })
onMounted(() => store.init())
</script>

<style scoped>
.timeline-page { display: grid; gap: 14px; min-width: 0; max-width: 1440px; margin: 0 auto; padding: 8px 0 48px; }
.timeline-head { display: flex; justify-content: space-between; align-items: end; gap: 16px; flex-wrap: wrap; }
.timeline-head h1 { margin: 3px 0 0; font-size: 22px; }
.eyebrow { margin: 0; color: var(--primary); font-size: 11px; font-weight: 700; }
.timeline-controls { display: flex; align-items: end; gap: 8px; flex-wrap: wrap; }
.timeline-controls label { display: grid; gap: 3px; color: var(--muted); font-size: 11px; }
.timeline-controls select,.timeline-controls button,.timeline-period button { min-height: 32px; border: 1px solid var(--border); border-radius: 6px; background: var(--surface); color: var(--text); padding: 4px 9px; font-size: 12px; }
.timeline-density { display: inline-flex; border: 1px solid var(--border); border-radius: 6px; overflow: hidden; }
.timeline-density button { border: 0; border-radius: 0; }
.timeline-density button+button { border-left: 1px solid var(--border); }
.timeline-density button.active { background: var(--primary); color: #fff; }
.timeline-period { display: flex; align-items: center; justify-content: center; gap: 14px; color: var(--muted); font-size: 12px; }
.timeline-scroll { min-width: 0; overflow: auto; border: 1px solid var(--border); border-radius: 7px; background: var(--surface); }
.timeline-grid { min-width: calc(var(--timeline-label-width) + var(--timeline-column-count) * var(--timeline-tick-width)); }
.timeline-axis-row,.timeline-row { display: grid; grid-template-columns: var(--timeline-label-width) minmax(0,1fr) auto; min-height: 54px; border-bottom: 1px solid var(--border); }
.timeline-task-heading,.timeline-axis { position: sticky; top: 0; z-index: 3; background: var(--surface); }
.timeline-task-heading { left: 0; z-index: 4; display: flex; align-items: center; padding: 0 12px; border-right: 1px solid var(--border); font-size: 12px; font-weight: 650; }
.timeline-axis { grid-column: 2 / 4; display: grid; grid-template-columns: repeat(var(--timeline-column-count), minmax(var(--timeline-tick-width),1fr)); }
.timeline-tick { position: relative; display: flex; align-items: center; padding: 0 8px; border-left: 1px solid var(--border); color: var(--muted); font-size: 11px; white-space: nowrap; }
.timeline-tick.is-today { color: var(--primary); font-weight: 700; }
.timeline-tick i,.timeline-today-line { position: absolute; top: 0; bottom: 0; left: 0; width: 2px; background: var(--primary); opacity: .72; }
.timeline-group-heading { grid-column: 1 / -1; display: flex; align-items: center; gap: 7px; min-height: 34px; padding: 0 10px; border: 0; border-bottom: 1px solid var(--border); background: color-mix(in srgb,var(--surface) 92%,var(--primary) 8%); color: var(--text); text-align: left; font: inherit; font-size: 12px; cursor: pointer; }
.timeline-group-heading svg { transition: transform .18s ease; }.timeline-group-heading svg.rotated { transform: rotate(-90deg); }.timeline-group-heading small { color: var(--muted); }
.timeline-row { min-height: 48px; grid-template-columns: var(--timeline-label-width) minmax(0,1fr) auto; align-items: center; }
.timeline-label { position: sticky; left: 0; z-index: 2; align-self: stretch; display: grid; align-content: center; gap: 3px; padding: 4px 10px; border-right: 1px solid var(--border); background: var(--surface); min-width: 0; }
.timeline-label strong { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; font-size: 12px; font-weight: 550; }.timeline-label small { color: var(--muted); font-size: 10px; }
.timeline-track { position: relative; align-self: stretch; display: block; background: repeating-linear-gradient(90deg, transparent 0 calc(var(--timeline-tick-width) - 1px), var(--border) calc(var(--timeline-tick-width) - 1px) var(--timeline-tick-width)); }
.timeline-bar { position: absolute; top: 16px; height: 16px; min-width: 8px; border-radius: 4px; background: var(--primary); }
.timeline-bar.milestone { top: 18px; width: 12px!important; height: 12px; border-radius: 2px; transform: rotate(45deg); }
.timeline-bar.priority-high { background: #dc2626; }.timeline-bar.priority-medium { background: #d97706; }.timeline-bar.priority-low { background: #2563eb; }
.timeline-row>a { align-self: center; margin: 0 10px; color: var(--primary); font-size: 11px; white-space: nowrap; }
.timeline-empty { margin: 0; color: var(--muted); font-size: 13px; }
@media(max-width:700px) { .timeline-controls { width: 100%; }.timeline-row,.timeline-axis-row { grid-template-columns: 160px minmax(0,1fr) auto; }.timeline-grid { --timeline-label-width:160px!important; }.timeline-controls label { flex: 1; }.timeline-period { justify-content: flex-start; } }
</style>
