<template>
  <section class="timeline-page">
    <header class="timeline-head" :class="{ 'is-embedded': embedded }">
      <div v-if="!embedded"><p class="eyebrow">TASKS · TIMELINE</p><h1>时间线 / 甘特</h1></div>
      <div class="timeline-controls" aria-label="时间线显示设置">
        <div class="timeline-menu" ref="groupMenuRef">
          <button type="button" class="timeline-menu-trigger" aria-haspopup="menu" :aria-expanded="groupMenuOpen" aria-label="时间线分组" @click.stop="toggleMenu('group')"><span>分组</span><strong>{{ groupLabel }}</strong><ChevronDown :size="14" /></button>
          <div v-if="groupMenuOpen" class="timeline-menu-popover" role="menu" aria-label="时间线分组选项">
            <button v-for="option in groupOptions" :key="option.value" type="button" role="menuitemradio" :aria-checked="groupBy === option.value" :class="{ active: groupBy === option.value }" @click="selectGroup(option.value)"><span>{{ option.label }}</span><span v-if="groupBy === option.value" aria-hidden="true">✓</span></button>
          </div>
        </div>
        <div class="timeline-menu" ref="sortMenuRef">
          <button type="button" class="timeline-menu-trigger" aria-haspopup="menu" :aria-expanded="sortMenuOpen" aria-label="时间线排序" @click.stop="toggleMenu('sort')"><span>排序</span><strong>{{ sortLabel }}</strong><ChevronDown :size="14" /></button>
          <div v-if="sortMenuOpen" class="timeline-menu-popover" role="menu" aria-label="时间线排序选项">
            <button v-for="option in sortOptions" :key="option.value" type="button" role="menuitemradio" :aria-checked="sortBy === option.value" :class="{ active: sortBy === option.value }" @click="selectSort(option.value)"><span>{{ option.label }}</span><span v-if="sortBy === option.value" aria-hidden="true">✓</span></button>
          </div>
        </div>
      </div>
    </header>
    <div class="timeline-period">
      <div class="timeline-period-range"><strong>{{ rangeLabel }}</strong><button type="button" aria-label="上一时间段" @click="movePeriod(-1)"><ChevronLeft :size="16" /></button><button type="button" aria-label="下一时间段" @click="movePeriod(1)"><ChevronRight :size="16" /></button></div>
      <div class="timeline-period-controls"><button type="button" class="timeline-today" @click="goToday">今天</button><label><select :value="density" aria-label="时间线密度" @change="setDensity($event.target.value)"><option v-for="option in densityOptions" :key="option.value" :value="option.value">{{ option.label }}</option></select></label></div>
    </div>
    <p v-if="!rows.length" class="timeline-empty">没有包含时间范围的任务</p>
    <div v-else ref="scroller" class="timeline-scroll">
      <div class="timeline-grid" :style="gridStyle">
        <div class="timeline-axis-row"><div class="timeline-task-heading">{{ monthLabel }}</div><div class="timeline-axis">
          <div v-for="tick in ticks" :key="tick.key" class="timeline-tick" :class="{ 'is-today': tick.today, 'is-weekend': tick.weekend, 'is-month-start': tick.monthStart }"><span>{{ tick.label }}</span><i v-if="tick.today" ref="todayMarker" aria-label="今天"></i></div>
        </div></div>
        <template v-for="row in rows" :key="row.key">
          <div class="timeline-row" :class="{ 'is-group-row': !row.single }">
            <button v-if="!row.single" type="button" class="timeline-label timeline-group-label" :aria-expanded="!collapsed[row.key]" @click="collapsed[row.key] = !collapsed[row.key]">
              <ChevronDown :size="13" :class="{ rotated: collapsed[row.key] }" /><i v-if="groupSwatch(row)" class="timeline-group-swatch" :style="{ backgroundColor: groupSwatch(row) }"></i><strong>{{ row.label }}</strong><small>{{ row.tasks.length }}</small>
            </button>
            <div v-else class="timeline-label"><strong>{{ row.label }}</strong><small>{{ dateLabel(row.task.startAt || row.task.dueAt) }} - {{ dateLabel(row.task.dueAt || row.task.startAt) }}</small></div>
            <div class="timeline-track" :style="trackStyle(row)">
              <div class="timeline-today-line" :style="todayLineStyle"></div>
              <template v-if="row.single">
                <span v-if="intersectsRange(row.task)" class="timeline-bar" :class="[`priority-${String(row.task.priority).toLowerCase()}`, { milestone: isMilestone(row.task) }]" :style="barStyle(row.task)" :title="row.task.title" @click="emit('select-task', row.task)"></span>
              </template>
              <template v-else-if="!collapsed[row.key]">
                <span v-for="item in groupBars(row)" :key="item.task.publicId" class="timeline-bar" :class="{ milestone: isMilestone(item.task) }" :style="item.style" :title="item.task.title" @click="emit('select-task', item.task)"><em v-if="item.wide">{{ item.task.title }}</em></span>
              </template>
            </div>
          </div>
        </template>
      </div>
    </div>
  </section>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, reactive, ref, watch, nextTick } from 'vue'
import { ChevronDown, ChevronLeft, ChevronRight } from 'lucide-vue-next'
import { useTasksStore } from '../stores/tasks.js'
import { groupTaskItems } from './taskList.js'

const store = useTasksStore()
const props = defineProps({ embedded: { type: Boolean, default: false }, tasks: { type: Array, default: null } })
const emit = defineEmits(['select-task'])
const densityOptions = [{ value: 'day', label: '日' }, { value: 'week', label: '周' }, { value: 'month', label: '月' }]
const density = ref('week')
const groupBy = ref('list')
const sortBy = ref('default')
const groupMenuOpen = ref(false)
const sortMenuOpen = ref(false)
const groupMenuRef = ref(null)
const sortMenuRef = ref(null)
const groupOptions = [{ value: 'list', label: '清单' }, { value: 'tag', label: '标签' }, { value: 'priority', label: '优先级' }, { value: 'none', label: '无' }]
const sortOptions = [{ value: 'default', label: '默认' }, { value: 'time', label: '按时间' }]
const groupLabel = computed(() => groupOptions.find(option => option.value === groupBy.value)?.label || '清单')
const sortLabel = computed(() => sortOptions.find(option => option.value === sortBy.value)?.label || '默认')
const anchor = ref(startOfDay(new Date()))
const scroller = ref(null)
const todayMarker = ref(null)
const collapsed = reactive({})

const ticks = computed(() => {
  const start = rangeStart.value
  if (density.value === 'day') return Array.from({ length: 14 }, (_, index) => {
    const date = addDays(start, index)
    return { key: dateKey(date), date, label: new Intl.DateTimeFormat('zh-CN', { month: 'numeric', day: 'numeric' }).format(date), today: sameDay(date, new Date()), weekend: isWeekend(date) }
  })
  if (density.value === 'week') return Array.from({ length: 12 }, (_, index) => {
    const date = addDays(start, index * 7)
    const end = addDays(date, 6)
    return { key: dateKey(date), date, end, label: `${date.getMonth() + 1}/${date.getDate()}`, today: new Date() >= date && new Date() <= end, weekend: false }
  })
  return Array.from({ length: 120 }, (_, index) => {
    const date = addDays(start, index)
    const monthStart = date.getDate() === 1
    return { key: dateKey(date), date, end: date, label: String(date.getDate()), today: sameDay(date, new Date()), weekend: isWeekend(date), monthStart }
  })
})
const rangeStart = computed(() => {
  if (density.value === 'day') return addDays(anchor.value, -6)
  if (density.value === 'week') {
    const start = startOfDay(anchor.value)
    start.setDate(start.getDate() - ((start.getDay() + 6) % 7) - 28)
    return start
  }
  return new Date(anchor.value.getFullYear(), anchor.value.getMonth(), 1)
})
const rangeEnd = computed(() => addDays(rangeStart.value, density.value === 'day' ? 14 : density.value === 'week' ? 84 : 120))
const rangeLabel = computed(() => {
  const first = ticks.value[0]?.date
  const last = ticks.value.at(-1)?.end || ticks.value.at(-1)?.date
  if (!first || !last) return ''
  const format = value => new Intl.DateTimeFormat('zh-CN', { year: 'numeric', month: 'numeric', day: 'numeric' }).format(value)
  return `${format(first)} - ${format(last)}`
})
const monthLabel = computed(() => {
  const first = ticks.value[0]?.date
  return first ? new Intl.DateTimeFormat('zh-CN', { month: 'long' }).format(first) : ''
})
const gridStyle = computed(() => ({ '--timeline-label-width': '196px', '--timeline-tick-width': density.value === 'day' ? '54px' : density.value === 'week' ? '104px' : '18px', '--timeline-column-count': ticks.value.length }))
const todayIndex = computed(() => ticks.value.findIndex(tick => tick.today || sameDay(tick.date, new Date())))
const todayPosition = computed(() => {
  const from = rangeStart.value.getTime()
  const span = rangeEnd.value.getTime() - from
  return Math.min(100, Math.max(0, (Date.now() - from) / span * 100))
})
const todayLineStyle = computed(() => ({ left: `${todayPosition.value}%` }))
const datedTasks = computed(() => (props.tasks || store.tasks).filter(task => !task.deleted && task.status !== 'ABANDONED' && (task.startAt || task.dueAt)))
const rows = computed(() => {
  const sorted = sortBy.value === 'time'
    ? [...datedTasks.value].sort((a, b) => new Date(a.startAt || a.dueAt) - new Date(b.startAt || b.dueAt))
    : [...datedTasks.value].sort((a, b) => Number(a.sortOrder || 0) - Number(b.sortOrder || 0))
  if (groupBy.value === 'none') return sorted.map(task => ({ key: `task:${task.publicId}`, label: task.title, task, tasks: [task], single: true }))
  return groupTaskItems(sorted, groupBy.value, store.lists, new Date(), store.tags, { includeEmptyLists: groupBy.value === 'list' })
})

function startOfDay(value) { const date = new Date(value); date.setHours(0, 0, 0, 0); return date }
function addDays(value, days) { const date = new Date(value); date.setDate(date.getDate() + days); return date }
function sameDay(left, right) { return left.getFullYear() === right.getFullYear() && left.getMonth() === right.getMonth() && left.getDate() === right.getDate() }
function isWeekend(value) { return value.getDay() === 0 || value.getDay() === 6 }
function dateKey(value) { return `${value.getFullYear()}-${String(value.getMonth() + 1).padStart(2, '0')}-${String(value.getDate()).padStart(2, '0')}` }
function dateLabel(value) { return value ? new Intl.DateTimeFormat('zh-CN', { month: 'numeric', day: 'numeric' }).format(new Date(value)) : '无日期' }
function taskRange(task) {
  const due = task.dueAt ? new Date(task.dueAt).getTime() : null
  const start = task.startAt ? new Date(task.startAt).getTime() : due
  let end
  if (task.allDay) {
    end = startOfDay(new Date(task.dueAt || task.startAt)).getTime() + 86400000
  } else {
    const duration = Math.max(60, Number(task.durationMinutes || 60)) * 60000
    end = due && due > start ? due : task.startAt ? start + duration : start + 60000
  }
  return { start, end }
}
function intersectsRange(task) { const { start, end } = taskRange(task); return end >= rangeStart.value.getTime() && start < rangeEnd.value.getTime() }
function isMilestone(task) { return !task.allDay && (!task.startAt || !task.dueAt || new Date(task.dueAt).getTime() <= new Date(task.startAt || task.dueAt).getTime()) }
function barStyle(task) {
  const { start, end } = taskRange(task)
  const from = rangeStart.value.getTime(), span = rangeEnd.value.getTime() - from
  const clippedStart = Math.max(from, start), clippedEnd = Math.min(rangeEnd.value.getTime(), end)
  const left = Math.max(0, (clippedStart - from) / span * 100)
  const width = Math.max(density.value === 'month' ? 0.7 : 0.4, (clippedEnd - clippedStart) / span * 100)
  return { left: `${left}%`, width: isMilestone(task) ? '12px' : `${width}%` }
}
function groupSwatch(row) {
  if (groupBy.value === 'list') {
    const id = row.key.startsWith('list:') ? row.key.slice(5) : ''
    return store.lists.find(item => item.publicId === id)?.color || ''
  }
  if (groupBy.value === 'tag') {
    const id = row.key.startsWith('tag:') ? row.key.slice(4) : ''
    return store.tags.find(item => item.publicId === id)?.color || ''
  }
  return ''
}
function taskBarColor(task) {
  if (groupBy.value === 'priority') return { HIGH: '#dc2626', MEDIUM: '#d97706', LOW: '#2563eb' }[task.priority] || 'var(--primary)'
  const list = store.lists.find(item => item.publicId === task.listId)
  if (list?.color) return list.color
  const tag = store.tags.find(item => task.tagIds?.[0] === item.publicId)
  if (tag?.color) return tag.color
  return { HIGH: '#dc2626', MEDIUM: '#d97706', LOW: '#2563eb' }[task.priority] || 'var(--primary)'
}
function groupBars(row) {
  const from = rangeStart.value.getTime(), span = rangeEnd.value.getTime() - from
  const visible = row.tasks.filter(task => intersectsRange(task))
    .map(task => ({ task, ...taskRange(task) }))
    .sort((a, b) => a.start - b.start || a.end - b.end)
  const laneEnds = []
  for (const item of visible) {
    let lane = laneEnds.findIndex(end => item.start >= end)
    if (lane === -1) {
      laneEnds.push(item.end)
      lane = laneEnds.length - 1
    } else laneEnds[lane] = item.end
    item.lane = lane
    const widthPct = (Math.min(rangeEnd.value.getTime(), item.end) - Math.max(from, item.start)) / span * 100
    item.wide = widthPct >= 12 && !isMilestone(item.task)
    item.style = { ...barStyle(item.task), top: `${5 + lane * 26}px`, backgroundColor: taskBarColor(item.task) }
  }
  return visible
}
function trackStyle(row) {
  if (row.single) return {}
  const lanes = groupBars(row).reduce((max, item) => Math.max(max, item.lane + 1), 1)
  return { height: `${lanes * 26 + 12}px` }
}
function setDensity(value) { density.value = value }
function toggleMenu(menu) {
  if (menu === 'group') {
    groupMenuOpen.value = !groupMenuOpen.value
    sortMenuOpen.value = false
  } else {
    sortMenuOpen.value = !sortMenuOpen.value
    groupMenuOpen.value = false
  }
}
function selectGroup(value) { groupBy.value = value; groupMenuOpen.value = false }
function selectSort(value) { sortBy.value = value; sortMenuOpen.value = false }
function movePeriod(direction) {
  const date = new Date(anchor.value)
  if (density.value === 'day') date.setDate(date.getDate() + direction * 14)
  else if (density.value === 'week') date.setDate(date.getDate() + direction * 84)
  else {
    anchor.value = new Date(date.getFullYear(), date.getMonth() + direction * 4, 1)
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
function closeMenus(event) {
  if (!groupMenuRef.value?.contains(event.target)) groupMenuOpen.value = false
  if (!sortMenuRef.value?.contains(event.target)) sortMenuOpen.value = false
}
function onKeydown(event) {
  if (event.key === 'Escape') { groupMenuOpen.value = false; sortMenuOpen.value = false }
}
onMounted(() => {
  store.init()
  document.addEventListener('pointerdown', closeMenus)
  document.addEventListener('keydown', onKeydown)
})
onBeforeUnmount(() => {
  document.removeEventListener('pointerdown', closeMenus)
  document.removeEventListener('keydown', onKeydown)
})
</script>

<style scoped>
.timeline-page { display: grid; gap: 14px; min-width: 0; max-width: 1440px; margin: 0 auto; padding: 8px 0 48px; }
.timeline-head { display: flex; justify-content: space-between; align-items: end; gap: 16px; flex-wrap: wrap; }
.timeline-head.is-embedded { justify-content: flex-end; }
.timeline-head h1 { margin: 3px 0 0; font-size: 22px; }
.eyebrow { margin: 0; color: var(--primary); font-size: 11px; font-weight: 700; }
.timeline-controls { display: flex; align-items: end; gap: 8px; flex-wrap: wrap; }
.timeline-menu { position: relative; }
.timeline-menu-trigger { display: inline-flex; align-items: center; gap: 7px; min-height: 32px; padding: 4px 9px; border: 1px solid var(--border); border-radius: 6px; background: var(--surface); color: var(--muted); font-size: 12px; cursor: pointer; }
.timeline-menu-trigger strong { color: var(--text); font-weight: 550; }
.timeline-menu-trigger svg { color: var(--muted); }
.timeline-menu-trigger:hover,.timeline-menu-trigger[aria-expanded="true"] { border-color: color-mix(in srgb, var(--primary) 48%, var(--border)); background: color-mix(in srgb, var(--surface) 88%, var(--primary) 12%); }
.timeline-menu-popover { position: absolute; z-index: 30; top: calc(100% + 7px); right: 0; display: grid; min-width: 142px; padding: 6px; border: 1px solid var(--border); border-radius: 9px; background: var(--surface); box-shadow: 0 14px 34px rgb(15 23 42 / 18%); }
.timeline-menu-popover button { display: flex; align-items: center; justify-content: space-between; min-height: 34px; padding: 6px 9px; border: 0; border-radius: 6px; background: transparent; color: var(--text); text-align: left; font-size: 13px; cursor: pointer; }
.timeline-menu-popover button:hover,.timeline-menu-popover button.active { background: color-mix(in srgb, var(--primary) 12%, transparent); color: var(--primary); }
.timeline-period button,.timeline-period select { min-height: 32px; border: 1px solid var(--border); border-radius: 6px; background: var(--surface); color: var(--text); padding: 4px 9px; font-size: 12px; }
.timeline-period { display: flex; align-items: center; justify-content: space-between; gap: 14px; color: var(--muted); font-size: 12px; }
.timeline-period-range,.timeline-period-controls { display: flex; align-items: center; gap: 6px; }
.timeline-period-range strong { margin-right: 6px; }
.timeline-period-controls label { display: flex; }
.timeline-period .timeline-today { min-width: 48px; cursor: pointer; }
.timeline-scroll { min-width: 0; overflow: auto; border: 1px solid var(--border); border-radius: 7px; background: var(--surface); }
.timeline-grid { width: fit-content; min-width: 100%; }
.timeline-axis-row,.timeline-row { display: grid; grid-template-columns: var(--timeline-label-width) auto; border-bottom: 1px solid var(--border); }
.timeline-task-heading,.timeline-axis { position: sticky; top: 0; z-index: 3; background: var(--surface); }
.timeline-task-heading { left: 0; z-index: 4; display: flex; align-items: center; padding: 0 12px; min-height: 44px; border-right: 1px solid var(--border); font-size: 13px; font-weight: 650; }
.timeline-axis { grid-column: 2 / 3; display: grid; grid-template-columns: repeat(var(--timeline-column-count), var(--timeline-tick-width)); width: max-content; }
.timeline-tick { position: relative; display: flex; align-items: center; justify-content: center; min-height: 44px; padding: 0 4px; border-left: 1px solid var(--border); color: var(--muted); font-size: 11px; white-space: nowrap; }
.timeline-tick.is-weekend { background: color-mix(in srgb, var(--primary) 5%, transparent); }
.timeline-tick.is-month-start { color: var(--text); font-weight: 650; }
.timeline-tick.is-today { color: var(--danger, #dc2626); font-weight: 700; }
.timeline-tick i,.timeline-today-line { position: absolute; top: 0; bottom: 0; left: 0; width: 2px; background: var(--danger, #dc2626); opacity: .55; }
.timeline-row { min-height: 44px; grid-template-columns: var(--timeline-label-width) auto; align-items: stretch; }
.timeline-label { position: sticky; left: 0; z-index: 2; display: flex; align-items: center; gap: 6px; min-width: 0; padding: 4px 10px; border-right: 1px solid var(--border); background: var(--surface); text-align: left; font: inherit; }
.timeline-label strong { min-width: 0; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; font-size: 12px; font-weight: 550; }
.timeline-label small { flex: none; color: var(--muted); font-size: 10px; }
.timeline-group-label { cursor: pointer; }
.timeline-group-label:hover { background: color-mix(in srgb, var(--primary) 6%, var(--surface)); }
.timeline-group-label svg { flex: none; color: var(--muted); transition: transform .18s ease; }
.timeline-group-label svg.rotated { transform: rotate(-90deg); }
.timeline-group-swatch { width: 8px; height: 8px; flex: none; border-radius: 2px; }
.timeline-track { position: relative; align-self: stretch; display: block; width: calc(var(--timeline-column-count) * var(--timeline-tick-width)); background: repeating-linear-gradient(90deg, transparent 0 calc(var(--timeline-tick-width) - 1px), var(--border) calc(var(--timeline-tick-width) - 1px) var(--timeline-tick-width)); }
.timeline-bar { position: absolute; top: 5px; height: 20px; min-width: 8px; display: flex; align-items: center; overflow: hidden; border-radius: 5px; background: var(--primary); box-shadow: 0 2px 5px rgb(15 23 42 / 16%); cursor: pointer; transition: filter .15s ease; }
.timeline-bar:hover { filter: brightness(1.08); }
.timeline-bar em { min-width: 0; overflow: hidden; padding: 0 6px; color: #fff; font-size: 10px; font-style: normal; text-overflow: ellipsis; white-space: nowrap; }
.timeline-bar.milestone { width: 12px!important; min-width: 12px; justify-content: center; }
.timeline-bar.priority-high { background: var(--priority-high); }.timeline-bar.priority-medium { background: var(--priority-medium); }.timeline-bar.priority-low { background: var(--priority-low); }
.timeline-empty { margin: 0; color: var(--muted); font-size: 13px; }
@media(max-width:700px) { .timeline-head.is-embedded { justify-content: flex-start; }.timeline-controls { width: 100%; }.timeline-row,.timeline-axis-row { grid-template-columns: 160px auto; }.timeline-grid { --timeline-label-width:160px!important; }.timeline-controls { gap: 6px; }.timeline-menu { flex: 1; }.timeline-menu-trigger { width: 100%; justify-content: space-between; }.timeline-menu-popover { right: auto; left: 0; min-width: 100%; }.timeline-period { justify-content: space-between; }.timeline-period-range { gap: 3px; }.timeline-period-range strong { max-width: 35vw; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }.timeline-period-controls { gap: 4px; }.timeline-period select { max-width: 72px; } }
</style>
