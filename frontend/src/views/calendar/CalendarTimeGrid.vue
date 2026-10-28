<template>
  <div class="cal-time" :class="{ 'is-multi': days.length > 1 }">
    <div class="cal-time-head" :style="gridStyle">
      <div class="cal-time-gutter"></div>
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

    <div class="cal-allday" :class="{ 'is-collapsed': allDayCollapsed }" :style="{ height: `${allDayCollapsed ? 26 : allDayHeight}px` }">
      <button type="button" class="cal-allday-toggle" :aria-expanded="!allDayCollapsed" @click="$emit('update:allDayCollapsed', !allDayCollapsed)">
        <span>全天</span>
        <ChevronDown v-if="allDayCollapsed" :size="13" /><ChevronUp v-else :size="13" />
      </button>
      <div v-show="!allDayCollapsed" class="cal-allday-cols" :style="colsStyle">
        <div v-for="day in days" :key="`allday-${toDateKey(day)}`" class="cal-allday-col">
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
          <p v-if="!allDayOf(day).length" class="cal-allday-empty">—</p>
        </div>
      </div>
      <div v-if="!allDayCollapsed" class="cal-allday-resize" role="separator" aria-label="拖动调整全天区高度" @pointerdown="startResizeAllDay"></div>
    </div>

    <div ref="scrollEl" class="cal-scroll">
      <div v-if="viewWindow.start > 0" class="cal-hidden" @pointerdown="startHiddenDrag('top', $event)">
        <span>{{ minutesToLabel(viewWindow.start) }} 前已收起</span>
        <i class="cal-hidden-handle" aria-hidden="true"><GripHorizontal :size="11" /></i>
      </div>

      <div class="cal-grid" :style="{ ...gridStyle, height: `${gridHeight}px` }">
        <div class="cal-hours" :style="{ width: `${GUTTER}px` }">
          <span v-for="mark in marks" :key="mark.minutes" class="cal-hour-label" :style="{ top: `${((mark.minutes - viewWindow.start) / 60) * hourHeight}px` }">{{ mark.label }}</span>
        </div>
        <div
          v-for="day in days"
          :key="`col-${toDateKey(day)}`"
          class="cal-col"
          :class="{ today: toDateKey(day) === todayKey }"
          @click="handleColumnClick(day, $event)"
        >
          <span v-for="mark in marks" :key="`line-${mark.minutes}`" class="cal-line" :style="{ top: `${((mark.minutes - viewWindow.start) / 60) * hourHeight}px` }"></span>
          <button
            v-for="block in blocksOf(day)"
            :key="`block-${block.event.id}`"
            type="button"
            class="cal-block"
            :class="[`kind-${block.event.kind}`, { 'is-done': block.event.done, 'is-short': block.geometry.height < 34, 'continues-before': block.event.continuesBefore, 'continues-after': block.event.continuesAfter }]"
            :style="blockStyle(block)"
            :title="blockTitle(block.event)"
            @click.stop="$emit('open-event', block.event, $event)"
          >
            <span class="cal-block-title">
              <Timer v-if="block.event.kind === 'focus'" :size="10" />
              <span>{{ block.event.title }}</span>
            </span>
            <span class="cal-block-time">{{ block.event.startLabel }}<template v-if="block.event.endLabel"> – {{ block.event.endLabel }}</template></span>
          </button>
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
import { formatWeekday, toDateKey } from './calendarRange.js'
import { tintStyle } from './calendarColors.js'
import {
  MINUTES_PER_DAY, blockGeometry, hourMarks, layoutColumns, minutesToLabel, snapMinutes, visibleWindow
} from './calendarTimeGrid.js'

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
  'open-event', 'create-at', 'select-day',
  'update:hiddenTop', 'update:hiddenBottom', 'update:allDayHeight', 'update:allDayCollapsed'
])

const GUTTER = 52
const scrollEl = ref(null)
const now = ref(new Date())

const gridStyle = computed(() => ({ gridTemplateColumns: `${GUTTER}px repeat(${props.days.length}, minmax(0, 1fr))` }))
const colsStyle = computed(() => ({ gridTemplateColumns: `repeat(${props.days.length}, minmax(0, 1fr))` }))
const viewWindow = computed(() => visibleWindow(props.hiddenTop, props.hiddenBottom))
const gridHeight = computed(() => ((viewWindow.value.minutes / 60) * props.hourHeight))
const marks = computed(() => hourMarks(viewWindow.value))
const nowMinutes = computed(() => now.value.getHours() * 60 + now.value.getMinutes())
const nowVisible = computed(() => nowMinutes.value >= viewWindow.value.start && nowMinutes.value <= viewWindow.value.end)
const nowTop = computed(() => ((nowMinutes.value - viewWindow.value.start) / 60) * props.hourHeight)

const buckets = computed(() => props.days.map(day => props.byDay.get(toDateKey(day)) || { allDay: [], timed: [] }))
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
  return index === -1 ? [] : buckets.value[index].allDay
}

function blocksOf(day) {
  const index = props.days.findIndex(item => toDateKey(item) === toDateKey(day))
  if (index === -1) return []
  const layout = layouts.value[index]
  return buckets.value[index].timed.map(event => {
    const geometry = blockGeometry(event.startMinutes, event.endMinutes, viewWindow.value, props.hourHeight)
    const slot = layout.get(event.id) || { column: 0, columns: 1 }
    return { event, geometry, column: slot.column, columns: slot.columns }
  })
}

function blockStyle(block) {
  const width = 100 / block.columns
  return {
    top: `${block.geometry.top}px`,
    height: `${Math.max(18, block.geometry.height)}px`,
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

function handleColumnClick(day, event) {
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
.cal-allday-cols {
  display: grid;
  flex: 1 1 auto;
  min-width: 0;
}
.cal-allday-col {
  display: flex;
  min-width: 0;
  flex-direction: column;
  gap: 2px;
  padding: 4px 4px 2px;
  border-right: 1px solid var(--line);
  overflow-y: auto;
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
