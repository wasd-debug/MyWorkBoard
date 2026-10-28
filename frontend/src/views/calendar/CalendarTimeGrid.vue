<template>
  <div class="cal-time" :class="{ 'is-multi': days.length > 1 }">
    <div class="cal-time-head" :style="{ ...gridStyle, minWidth: `${minWidth}px` }">
      <div class="cal-time-gutter">
        <span v-if="days.length > 1" class="cal-time-week">{{ weekLabel }}</span>
      </div>
      <div
        v-for="day in days"
        :key="`dow-${toDateKey(day)}`"
        class="cal-time-dow"
        :class="{ today: toDateKey(day) === todayKey }"
        @click="$emit('select-day', day, $event)"
      >
        <b>{{ day.getDate() }}</b>
        <span>{{ weekdayLabel(day) }}</span>
        <small v-if="subLabel(day)">{{ subLabel(day) }}</small>
      </div>
    </div>

    <div class="cal-allday" :class="{ 'is-collapsed': allDayCollapsed }" :style="{ height: `${allDayCollapsed ? 26 : bandHeight}px` }">
      <button type="button" class="cal-allday-toggle" :aria-expanded="!allDayCollapsed" @click="$emit('update:allDayCollapsed', !allDayCollapsed)">
        <span>全天</span>
        <ChevronDown v-if="allDayCollapsed" :size="13" /><ChevronUp v-else :size="13" />
      </button>
      <div v-show="!allDayCollapsed" class="cal-allday-body">
        <div class="cal-allday-cols" :style="colsStyle">
          <div
            v-for="day in days"
            :key="`allday-${toDateKey(day)}`"
            class="cal-allday-col"
            :style="{ paddingTop: `${laneHeight}px` }"
            :title="`在 ${day.getMonth() + 1}月${day.getDate()}日 新建全天任务`"
            @click="$emit('create-allday', day)"
          >
            <button
              v-for="event in allDayOf(day)"
              :key="event.id"
              type="button"
              class="cal-allday-item"
              :class="[`kind-${event.kind}`, { 'is-done': event.done }]"
              :style="tintStyle(event.color, 16)"
              :title="event.title"
              @click.stop="$emit('open-event', event, $event)"
            >
              <span class="cal-ev-dot" :class="{ 'is-done': event.done }" :style="{ borderColor: event.color }"></span>
              <span class="cal-ev-title">{{ event.title }}</span>
              <span v-if="event.openChecklist" class="cal-ev-extra">+{{ event.openChecklist }}</span>
            </button>
            <p v-if="!allDayOf(day).length && !laneRows.length" class="cal-allday-empty">—</p>
          </div>
        </div>
        <div class="cal-allday-lanes" :style="{ gridTemplateRows: `repeat(${Math.max(laneRows.length, 1)}, 19px)` }">
          <button
            v-for="segment in laneSegments"
            :key="`lane-${segment.id}`"
            type="button"
            class="cal-allday-lane"
            :class="{ 'is-done': segment.event.done, 'continues-before': segment.continuesBefore, 'continues-after': segment.continuesAfter }"
            :style="{
              gridRow: laneIndex[segment.id] + 1,
              gridColumn: `${segment.startCol + 1} / ${segment.endCol + 2}`,
              ...tintStyle(segment.event.color, 16)
            }"
            :title="segment.event.title"
            @click.stop="$emit('open-event', segment.event, $event)"
          >
            <span class="cal-ev-dot" :class="{ 'is-done': segment.event.done }" :style="{ borderColor: segment.event.color }"></span>
            <span class="cal-ev-title">{{ segment.event.title }}</span>
            <span v-if="segment.continuesAfter" class="cal-ev-extra">→</span>
          </button>
        </div>
      </div>
      <div v-if="!allDayCollapsed" class="cal-allday-resize" role="separator" aria-label="拖动调整全天区高度" @pointerdown="startResizeAllDay"></div>
    </div>

    <div ref="scrollEl" class="cal-scroll">
      <div v-if="viewWindow.start > 0" class="cal-hidden" @pointerdown="startHiddenDrag('top', $event)">
        <span>{{ minutesToLabel(viewWindow.start) }} 前已收起</span>
        <i class="cal-hidden-handle" aria-hidden="true"><GripHorizontal :size="11" /></i>
      </div>

      <div class="cal-grid" :style="{ ...gridStyle, height: `${gridHeight}px`, minWidth: `${minWidth}px` }">
        <div class="cal-hours" :style="{ width: `${GUTTER}px` }">
          <span v-for="mark in marks" :key="mark.minutes" class="cal-hour-label" :style="{ top: `${((mark.minutes - viewWindow.start) / 60) * hourHeight}px` }">{{ mark.label }}</span>
        </div>
        <div
          v-for="day in days"
          :key="`col-${toDateKey(day)}`"
          class="cal-col"
          :class="{ today: toDateKey(day) === todayKey }"
          @pointerdown="startRangeDrag(day, $event)"
          @click="handleColumnClick(day, $event)"
          @dragover.prevent
          @drop.prevent="onColumnDrop(day, $event)"
        >
          <span v-for="mark in marks" :key="`line-${mark.minutes}`" class="cal-line" :style="{ top: `${((mark.minutes - viewWindow.start) / 60) * hourHeight}px` }"></span>
          <button
            v-for="block in blocksOf(day)"
            :key="`block-${block.event.id}`"
            type="button"
            class="cal-block"
            :class="[`kind-${block.event.kind}`, {
              'is-done': block.event.done,
              'is-short': block.geometry.height < 34,
              'is-clipped-top': block.geometry.clippedStart,
              'is-clipped-bottom': block.geometry.clippedEnd,
              'continues-before': block.event.continuesBefore,
              'continues-after': block.event.continuesAfter
            }]"
            :style="blockStyle(block)"
            :title="blockTitle(block.event)"
            @pointerdown.stop="startBlockDrag(block, $event)"
            @click.stop="handleBlockClick(block.event, $event)"
          >
            <span class="cal-block-handle is-top" @pointerdown.stop="startBlockResize(block, 'start', $event)"></span>
            <span class="cal-block-handle is-bottom" @pointerdown.stop="startBlockResize(block, 'end', $event)"></span>
            <span class="cal-block-title">
              <Timer v-if="block.event.kind === 'focus'" :size="10" />
              <span>{{ block.event.title }}</span>
            </span>
            <span class="cal-block-time">{{ blockLabel(block) }}</span>
          </button>
          <div v-if="selection && selection.dayKey === toDateKey(day)" class="cal-selection" :style="selectionStyle">
            <span>{{ selectionLabel }}</span>
          </div>
          <div v-if="toDateKey(day) === todayKey && nowVisible" class="cal-now" :style="{ top: `${nowTop}px` }"><i></i></div>
        </div>
      </div>

      <div v-if="viewWindow.end < MINUTES_PER_DAY" class="cal-hidden" @pointerdown="startHiddenDrag('bottom', $event)">
        <span>{{ minutesToLabel(viewWindow.end) }} 后已收起</span>
        <i class="cal-hidden-handle" aria-hidden="true"><GripHorizontal :size="11" /></i>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { ChevronDown, ChevronUp, GripHorizontal, Timer } from 'lucide-vue-next'
import { formatWeekday, isoWeekNumber, toDateKey } from './calendarRange.js'
import { packWeekLanes } from './calendarLanes.js'
import { tintStyle } from './calendarColors.js'
import {
  MINUTES_PER_DAY, blockGeometry, hourMarks, layoutColumns, minutesToLabel, snapMinutes, visibleWindow
} from './calendarTimeGrid.js'
import { dragRange, formatDuration, moveBlock, pointerToMinutes, resizeBlock } from './calendarDrag.js'

const props = defineProps({
  days: { type: Array, required: true },
  byDay: { type: Map, default: () => new Map() },
  todayKey: { type: String, default: '' },
  lunar: { type: Object, default: () => ({}) },
  holidays: { type: Object, default: () => ({}) },
  hiddenTop: { type: Number, default: 420 },
  hiddenBottom: { type: Number, default: 1380 },
  allDayHeight: { type: Number, default: 84 },
  allDayCollapsed: { type: Boolean, default: false },
  hourHeight: { type: Number, default: 48 }
})

const emit = defineEmits([
  'open-event', 'create-at', 'create-allday', 'create-range', 'move-block', 'resize-block', 'select-day', 'drop-task',
  'update:hiddenTop', 'update:hiddenBottom', 'update:allDayHeight', 'update:allDayCollapsed'
])

const GUTTER = 52
const scrollEl = ref(null)
const now = ref(new Date())
const selection = ref(null)
const dragPreview = ref(null)
let dragContext = null
let suppressColumnClick = false
let suppressBlockClick = false

const gridStyle = computed(() => ({ gridTemplateColumns: `${GUTTER}px repeat(${props.days.length}, minmax(0, 1fr))` }))
const colsStyle = computed(() => ({ gridTemplateColumns: `repeat(${props.days.length}, minmax(0, 1fr))` }))
// 列宽下限：多列视图在窄屏保持可读，横向滚动留在日历内部
const COLUMN_MIN_WIDTH = 88
const minWidth = computed(() => GUTTER + props.days.length * COLUMN_MIN_WIDTH)
const weekLabel = computed(() => (props.days.length ? `${isoWeekNumber(props.days[0])}周` : ''))
// 全天区按内容自适应：条目少时不占满用户设定的高度，超出时以设定值为上限
const bandHeight = computed(() => {
  const maxItems = Math.max(0, ...props.days.map(day => allDayOf(day).length))
  const needed = laneHeight.value + maxItems * 21 + 10
  return Math.min(props.allDayHeight, Math.max(26, needed))
})
const viewWindow = computed(() => visibleWindow(props.hiddenTop, props.hiddenBottom))
const gridHeight = computed(() => ((viewWindow.value.minutes / 60) * props.hourHeight))
const marks = computed(() => hourMarks(viewWindow.value))
const nowMinutes = computed(() => now.value.getHours() * 60 + now.value.getMinutes())
const nowVisible = computed(() => nowMinutes.value >= viewWindow.value.start && nowMinutes.value <= viewWindow.value.end)
const nowTop = computed(() => ((nowMinutes.value - viewWindow.value.start) / 60) * props.hourHeight)

const buckets = computed(() => props.days.map(day => props.byDay.get(toDateKey(day)) || { allDay: [], timed: [] }))
const lanePack = computed(() => packWeekLanes(props.days, props.byDay))
const laneRows = computed(() => lanePack.value.lanes)
const laneSegments = computed(() => lanePack.value.segments)
const laneHeight = computed(() => lanePack.value.laneHeight)
const laneIndex = computed(() => {
  const map = {}
  laneRows.value.forEach((lane, index) => lane.forEach(item => { map[item.id] = index }))
  return map
})
const layouts = computed(() => buckets.value.map(bucket => layoutColumns(bucket.timed)))

function weekdayLabel(day) {
  return formatWeekday(day)
}

function subLabel(day) {
  const key = toDateKey(day)
  return props.holidays[key]?.name || props.lunar[key] || ''
}

function allDayOf(day) {
  const index = props.days.findIndex(item => toDateKey(item) === toDateKey(day))
  if (index === -1) return []
  const laneIds = lanePack.value.laneIds
  return buckets.value[index].allDay.filter(item => !laneIds.has(item.id))
}

function blocksOf(day) {
  const index = props.days.findIndex(item => toDateKey(item) === toDateKey(day))
  if (index === -1) return []
  const layout = layouts.value[index]
  return buckets.value[index].timed.filter(event => Number.isFinite(Number(event.startMinutes))).map(event => {
    const geometry = blockGeometry(event.startMinutes, event.endMinutes, viewWindow.value, props.hourHeight)
    const slot = layout.get(event.id) || { column: 0, columns: 1 }
    return { event, geometry, column: slot.column, columns: slot.columns }
  })
}

function blockTimes(block) {
  const preview = dragPreview.value?.id === block.event.id ? dragPreview.value : null
  const startMinutes = preview ? preview.startMinutes : block.event.startMinutes
  const endMinutes = preview ? preview.endMinutes : block.event.endMinutes
  return { startMinutes, endMinutes, preview: Boolean(preview) }
}

function blockStyle(block) {
  const width = 100 / block.columns
  const { startMinutes, endMinutes } = blockTimes(block)
  const geometry = blockGeometry(startMinutes, endMinutes, viewWindow.value, props.hourHeight)
  return {
    top: `${geometry.top}px`,
    height: `${Math.max(18, geometry.height)}px`,
    left: `${(block.column * width).toFixed(3)}%`,
    width: `calc(${width.toFixed(3)}% - 4px)`,
    borderLeftColor: block.event.color,
    ...tintStyle(block.event.color, 16)
  }
}

function blockTitle(event) {
  const parts = [event.title]
  if (event.startLabel && event.endLabel) parts.push(`${event.startLabel} – ${event.endLabel}`)
  if (event.continuesBefore) parts.push('接前一天')
  if (event.continuesAfter) parts.push('延续到次日')
  return parts.join(' · ')
}

function blockLabel(block) {
  const { startMinutes, endMinutes } = blockTimes(block)
  return `${minutesToLabel(startMinutes)} – ${minutesToLabel(endMinutes)}`
}

const selectionStyle = computed(() => {
  if (!selection.value) return {}
  const start = Math.max(selection.value.startMinutes, viewWindow.value.start)
  const end = Math.min(selection.value.endMinutes, viewWindow.value.end)
  return {
    top: `${((start - viewWindow.value.start) / 60) * props.hourHeight}px`,
    height: `${Math.max(12, ((end - start) / 60) * props.hourHeight)}px`
  }
})

const selectionLabel = computed(() => {
  if (!selection.value) return ''
  return `${minutesToLabel(selection.value.startMinutes)} – ${minutesToLabel(selection.value.endMinutes)} · ${formatDuration(selection.value.endMinutes - selection.value.startMinutes)}`
})

function startRangeDrag(day, event) {
  if (event.button !== 0 || event.target.closest('.cal-block')) return
  const rect = event.currentTarget.getBoundingClientRect()
  const startMinutes = pointerToMinutes(event.clientY - rect.top, props.hourHeight, viewWindow.value.start)
  dragContext = { day, rect, startMinutes, startY: event.clientY, moved: false }
  selection.value = null
  window.addEventListener('pointermove', onRangeMove)
  window.addEventListener('pointerup', onRangeUp)
}

function onRangeMove(event) {
  if (!dragContext) return
  if (Math.abs(event.clientY - dragContext.startY) > 5) dragContext.moved = true
  if (!dragContext.moved) return
  const current = pointerToMinutes(event.clientY - dragContext.rect.top, props.hourHeight, viewWindow.value.start)
  const range = dragRange(dragContext.startMinutes, current)
  selection.value = { dayKey: toDateKey(dragContext.day), ...range }
}

function onRangeUp() {
  window.removeEventListener('pointermove', onRangeMove)
  window.removeEventListener('pointerup', onRangeUp)
  const context = dragContext
  const picked = selection.value
  dragContext = null
  selection.value = null
  if (!context || !context.moved || !picked) return
  suppressColumnClick = true
  window.setTimeout(() => { suppressColumnClick = false }, 0)
  emit('create-range', context.day, picked.startMinutes, picked.endMinutes)
}

function startBlockDrag(block, event) {
  if (event.button !== 0) return
  startBlockGesture(block, 'move', event)
}

function startBlockResize(block, edge, event) {
  if (event.button !== 0) return
  startBlockGesture(block, edge, event)
}

function startBlockGesture(block, mode, event) {
  const startY = event.clientY
  const originStart = block.event.startMinutes
  const originEnd = block.event.endMinutes
  let moved = false
  const move = moveEvent => {
    if (Math.abs(moveEvent.clientY - startY) > 3) moved = true
    if (!moved) return
    const delta = ((moveEvent.clientY - startY) / props.hourHeight) * 60
    const next = mode === 'move'
      ? moveBlock(originStart, originEnd - originStart, delta)
      : resizeBlock(originStart, originEnd, mode, delta)
    dragPreview.value = { id: block.event.id, ...next }
  }
  const up = () => {
    window.removeEventListener('pointermove', move)
    window.removeEventListener('pointerup', up)
    const preview = dragPreview.value
    dragPreview.value = null
    if (!moved || !preview) return
    suppressBlockClick = true
    window.setTimeout(() => { suppressBlockClick = false }, 0)
    emit(mode === 'move' ? 'move-block' : 'resize-block', block.event, preview.startMinutes, preview.endMinutes)
  }
  window.addEventListener('pointermove', move)
  window.addEventListener('pointerup', up)
}

function handleBlockClick(event, nativeEvent) {
  if (suppressBlockClick) {
    suppressBlockClick = false
    return
  }
  emit('open-event', event, nativeEvent)
}

function onColumnDrop(day, event) {
  const rect = event.currentTarget.getBoundingClientRect()
  const minutes = pointerToMinutes(event.clientY - rect.top, props.hourHeight, viewWindow.value.start)
  emit('drop-task', toDateKey(day), minutes, event)
}

function handleColumnClick(day, event) {
  if (suppressColumnClick) {
    suppressColumnClick = false
    return
  }
  if (event.target.closest('.cal-block')) return
  const column = event.currentTarget
  const rect = column.getBoundingClientRect()
  const offset = event.clientY - rect.top
  const minutes = snapMinutes(viewWindow.value.start + (offset / props.hourHeight) * 60, 15)
  emit('create-at', day, minutes)
}

function startResizeAllDay(event) {
  event.preventDefault()
  const startY = event.clientY
  const startHeight = props.allDayHeight
  const move = moveEvent => emit('update:allDayHeight', Math.round(Math.min(260, Math.max(32, startHeight + moveEvent.clientY - startY))))
  const up = () => {
    window.removeEventListener('pointermove', move)
    window.removeEventListener('pointerup', up)
  }
  window.addEventListener('pointermove', move)
  window.addEventListener('pointerup', up)
}

function startHiddenDrag(edge, event) {
  event.preventDefault()
  event.stopPropagation()
  const startY = event.clientY
  const startValue = edge === 'top' ? viewWindow.value.start : viewWindow.value.end
  const move = moveEvent => {
    const deltaMinutes = ((moveEvent.clientY - startY) / props.hourHeight) * 60
    const next = snapMinutes(startValue + deltaMinutes, 30)
    if (edge === 'top') emit('update:hiddenTop', Math.min(720, Math.max(0, next)))
    else emit('update:hiddenBottom', Math.min(MINUTES_PER_DAY, Math.max(720, next)))
  }
  const up = () => {
    window.removeEventListener('pointermove', move)
    window.removeEventListener('pointerup', up)
  }
  window.addEventListener('pointermove', move)
  window.addEventListener('pointerup', up)
}

let ticker
onMounted(() => {
  ticker = window.setInterval(() => { now.value = new Date() }, 60_000)
})
onBeforeUnmount(() => { window.clearInterval(ticker) })
</script>

<style scoped>
.cal-time {
  display: flex;
  height: 100%;
  min-height: 0;
  flex-direction: column;
  overflow-x: auto;
  overflow-y: hidden;
  background: var(--card);
}
.cal-time-head {
  display: grid;
  flex: 0 0 auto;
  border-bottom: 1px solid var(--line);
  color: var(--muted);
  font-size: 10.5px;
  text-align: center;
}
.cal-time-gutter {
  display: grid;
  place-items: center;
  border-right: 1px solid var(--line);
}
.cal-time-week {
  color: var(--muted);
  font-size: 9.5px;
  font-variant-numeric: tabular-nums;
  writing-mode: vertical-rl;
}
.cal-time-gutter {
  display: grid;
  place-items: center;
  border-right: 1px solid var(--line);
}
.cal-time-week {
  color: var(--muted);
  font-size: 9.5px;
  font-variant-numeric: tabular-nums;
  writing-mode: vertical-rl;
}
.cal-time-dow {
  display: flex;
  align-items: baseline;
  justify-content: center;
  gap: 5px;
  min-width: 0;
  padding: 7px 4px;
  cursor: pointer;
}
.cal-time-dow b {
  color: var(--ink);
  font-size: 15px;
  font-weight: 650;
  font-variant-numeric: tabular-nums;
}
.cal-time-dow span {
  color: var(--ink2);
  font-size: 11px;
}
.cal-time-dow small {
  min-width: 0;
  overflow: hidden;
  color: var(--muted);
  font-size: 9.5px;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.cal-time-dow.today b {
  display: inline-grid;
  place-items: center;
  width: 22px;
  height: 22px;
  border-radius: 50%;
  background: var(--accent);
  color: #fff;
}
.cal-time-dow.today span {
  color: var(--accent);
  font-weight: 650;
}
.cal-allday {
  position: relative;
  display: flex;
  flex: 0 0 auto;
  gap: 0;
  overflow: hidden;
  border-bottom: 1px solid var(--line);
}
.cal-allday.is-collapsed {
  border-bottom-color: var(--line);
}
.cal-allday-toggle {
  display: flex;
  flex: 0 0 52px;
  width: 52px;
  align-items: center;
  justify-content: center;
  gap: 2px;
  border: 0;
  border-right: 1px solid var(--line);
  background: transparent;
  color: var(--muted);
  font-size: 10px;
}
.cal-allday-toggle:hover {
  color: var(--ink);
}
.cal-allday-body {
  position: relative;
  flex: 1 1 auto;
  min-width: 0;
  overflow: hidden;
}
.cal-allday-cols {
  display: grid;
  height: 100%;
}
.cal-allday-col {
  display: flex;
  min-width: 0;
  flex-direction: column;
  gap: 2px;
  padding: 4px 4px 2px;
  border-right: 1px solid var(--line);
  overflow: hidden;
}
.cal-allday-lanes {
  position: absolute;
  inset: 0;
  display: grid;
  gap: 2px 0;
  pointer-events: none;
}
.cal-allday-lane {
  display: flex;
  align-items: center;
  gap: 4px;
  min-width: 0;
  height: 19px;
  margin: 0 4px;
  padding: 0 5px;
  border: 1px solid transparent;
  border-radius: 4px;
  color: var(--ink);
  font-size: 10.5px;
  line-height: 1;
  pointer-events: auto;
  text-align: left;
}
.cal-allday-lane:hover {
  box-shadow: inset 0 0 0 1px color-mix(in srgb, var(--ink) 12%, transparent);
}
.cal-allday-lane.is-done .cal-ev-title {
  color: var(--muted);
  text-decoration: line-through;
}
.cal-allday-lane.continues-before {
  margin-left: 0;
  border-top-left-radius: 0;
  border-bottom-left-radius: 0;
}
.cal-allday-lane.continues-after {
  margin-right: 0;
  border-top-right-radius: 0;
  border-bottom-right-radius: 0;
}
.cal-allday-col:last-child {
  border-right: 0;
}
.cal-allday-item {
  display: flex;
  flex: 0 0 auto;
  align-items: center;
  gap: 4px;
  width: 100%;
  height: 19px;
  padding: 0 5px;
  border: 1px solid transparent;
  border-radius: 4px;
  color: var(--ink);
  font-size: 10.5px;
  text-align: left;
}
.cal-allday-item:hover {
  box-shadow: inset 0 0 0 1px color-mix(in srgb, var(--ink) 12%, transparent);
}
.cal-allday-item.is-done .cal-ev-title {
  color: var(--muted);
  text-decoration: line-through;
}
.cal-allday-empty {
  margin: 0;
  color: var(--muted);
  font-size: 10px;
  opacity: .6;
}
.cal-allday-resize {
  position: absolute;
  right: 0;
  bottom: 0;
  left: 0;
  height: 6px;
  cursor: ns-resize;
}
.cal-allday-resize:hover {
  background: color-mix(in srgb, var(--accent) 16%, transparent);
}
.cal-scroll {
  flex: 1 1 auto;
  min-height: 0;
  overflow-y: auto;
}
.cal-hidden {
  position: relative;
  display: flex;
  align-items: center;
  justify-content: flex-start;
  gap: 6px;
  height: 22px;
  padding-left: 8px;
  border-bottom: 1px dashed var(--line);
  background: color-mix(in srgb, var(--paper) 70%, transparent);
  color: var(--muted);
  font-size: 9.5px;
  cursor: ns-resize;
}
.cal-hidden:hover {
  color: var(--ink2);
}
.cal-hidden-handle {
  display: grid;
  place-items: center;
  width: 16px;
  height: 16px;
  border: 1px solid var(--line2);
  border-radius: 50%;
  background: var(--card);
  color: var(--muted);
}
.cal-grid {
  position: relative;
  display: grid;
}
.cal-hours {
  position: relative;
  flex: 0 0 auto;
  border-right: 1px solid var(--line);
}
.cal-hour-label {
  position: absolute;
  right: 6px;
  color: var(--muted);
  font-size: 9.5px;
  font-variant-numeric: tabular-nums;
  transform: translateY(-50%);
}
.cal-col {
  position: relative;
  min-width: 0;
  border-right: 1px solid var(--line);
  cursor: crosshair;
  touch-action: none;
}
.cal-selection {
  position: absolute;
  right: 2px;
  left: 2px;
  z-index: 2;
  border: 1px dashed var(--accent);
  border-radius: 4px;
  background: color-mix(in srgb, var(--accent) 14%, transparent);
  pointer-events: none;
}
.cal-selection span {
  position: absolute;
  top: 2px;
  left: 5px;
  color: var(--accent);
  font-size: 9.5px;
  font-weight: 600;
  white-space: nowrap;
}
.cal-block-handle {
  position: absolute;
  right: 0;
  left: 0;
  height: 6px;
  cursor: ns-resize;
  opacity: 0;
}
.cal-block-handle.is-top {
  top: -2px;
}
.cal-block-handle.is-bottom {
  bottom: -2px;
}
.cal-block:hover .cal-block-handle {
  opacity: 1;
  background: color-mix(in srgb, var(--accent) 28%, transparent);
}
.cal-col:last-child {
  border-right: 0;
}
.cal-col.today {
  background: color-mix(in srgb, var(--accent) 3%, transparent);
}
.cal-line {
  position: absolute;
  right: 0;
  left: 0;
  height: 1px;
  background: var(--line);
  opacity: .7;
}
.cal-block.is-short {
  flex-direction: row;
  align-items: center;
  gap: 5px;
  padding: 0 5px;
}
.cal-block.is-short .cal-block-time {
  flex: 0 0 auto;
  opacity: .75;
}
.cal-block.is-short {
  flex-direction: row;
  align-items: center;
  gap: 5px;
  padding: 0 5px;
}
.cal-block.is-short .cal-block-time {
  flex: 0 0 auto;
  opacity: .75;
}
.cal-block {
  position: absolute;
  touch-action: none;
  display: flex;
  flex-direction: column;
  gap: 1px;
  overflow: hidden;
  padding: 3px 5px;
  border: 1px solid transparent;
  border-left-width: 3px;
  border-radius: 5px;
  background: color-mix(in srgb, var(--accent) 12%, transparent);
  color: var(--ink);
  font-size: 10.5px;
  line-height: 1.25;
  text-align: left;
}
.cal-block-title {
  display: flex;
  align-items: center;
  gap: 3px;
  overflow: hidden;
  font-weight: 550;
}
.cal-block-title span {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.cal-block-time {
  color: var(--ink2);
  font-size: 9px;
  font-variant-numeric: tabular-nums;
  opacity: .85;
}
.cal-block.is-done {
  opacity: .62;
}
.cal-block.is-done .cal-block-title {
  text-decoration: line-through;
}
.cal-block.is-clipped-top {
  border-top-style: dashed;
  border-top-color: color-mix(in srgb, var(--ink) 22%, transparent);
}
.cal-block.is-clipped-bottom {
  border-bottom-style: dashed;
  border-bottom-color: color-mix(in srgb, var(--ink) 22%, transparent);
}
.cal-block.is-clipped-top {
  border-top-style: dashed;
  border-top-color: color-mix(in srgb, var(--ink) 22%, transparent);
}
.cal-block.is-clipped-bottom {
  border-bottom-style: dashed;
  border-bottom-color: color-mix(in srgb, var(--ink) 22%, transparent);
}
.cal-block.continues-before {
  border-top-left-radius: 0;
  border-top-right-radius: 0;
  border-top-style: dashed;
}
.cal-block.continues-after {
  border-bottom-right-radius: 0;
  border-bottom-left-radius: 0;
  border-bottom-style: dashed;
}
.cal-now {
  position: absolute;
  right: 0;
  left: 0;
  z-index: 3;
  height: 0;
  border-top: 1.5px solid var(--down, #e34d59);
  pointer-events: none;
}
.cal-now i {
  position: absolute;
  top: -4px;
  left: -4px;
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: var(--down, #e34d59);
}
.cal-ev-dot {
  flex: 0 0 auto;
  width: 8px;
  height: 8px;
  border: 1.5px solid currentColor;
  border-radius: 50%;
}
.cal-ev-dot.is-done {
  background: currentColor;
  box-shadow: inset 0 0 0 1.5px var(--card);
}
.cal-ev-title {
  min-width: 0;
  flex: 1;
  overflow: hidden;
  font-weight: 500;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.cal-ev-extra {
  flex: 0 0 auto;
  color: var(--ink2);
  font-size: 9px;
}
@media (max-width: 767px) {
  .cal-time-dow small {
    display: none;
  }
  .cal-allday-toggle {
    flex-basis: 44px;
    width: 44px;
    font-size: 9px;
  }
}
</style>
