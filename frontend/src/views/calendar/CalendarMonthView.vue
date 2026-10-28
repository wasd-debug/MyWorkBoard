<template>
  <div class="cal-month">
    <div class="cal-month-dow">
      <span class="cal-week-gutter"></span>
      <span v-for="label in WEEKDAY_LABELS" :key="label">{{ label }}</span>
    </div>
    <div class="cal-month-body" :style="{ gridTemplateRows: `repeat(${weeks.length}, minmax(0, 1fr))` }">
      <div
        v-for="(week, index) in weeks"
        :key="`week-${index}`"
        class="cal-week"
        :style="{ '--lane-height': `${weekViews[index].laneHeight}px` }"
        @pointerdown="onWeekPointerDown($event)"
      >
        <div class="cal-week-gutter" :aria-label="`第 ${weekNumber(week[0])} 周`">{{ weekNumber(week[0]) }}<small>周</small></div>
        <div
          v-for="day in week"
          :key="dayKey(day)"
          class="cal-day"
          :class="{
            muted: muteOutsideMonth && day.getMonth() !== monthIndex,
            today: dayKey(day) === todayKey,
            selected: dayKey(day) === selectedKey,
            weekend: [0, 6].includes(day.getDay()),
            'in-range': isInRange(day)
          }"
          :data-date="dayKey(day)"
          @pointerenter="onDayPointerEnter(day)"
          @click="handleDayClick(day, $event)"
          @dragover.prevent
          @drop.prevent="$emit('drop-task', dayKey(day), null, $event)"
        >
          <header class="cal-day-head">
            <span class="cal-day-num">{{ day.getDate() }}</span>
            <span v-if="dayKey(day) === todayKey" class="cal-day-today">今天</span>
            <span v-else-if="subLabel(day)" class="cal-day-sub">{{ subLabel(day) }}</span>
          </header>
          <div class="cal-day-events">
            <button
              v-for="event in visibleEvents(index, day)"
              :key="event.id"
              type="button"
              class="cal-event"
              :class="[`kind-${event.kind}`, { 'is-done': event.done }]"
              :style="tintStyle(event.color, event.kind === 'holiday' ? 18 : 15)"
              :title="eventTitle(event)"
              @click.stop="handleEventClick(event, $event)"
            >
              <span v-if="event.kind === 'task'" class="cal-ev-dot" :class="{ 'is-done': event.done }" :style="{ borderColor: event.color }"></span>
              <span v-else-if="event.kind === 'habit'" class="cal-ev-icon" :style="{ color: event.color }">
                <CircleCheck v-if="event.done" :size="10" /><Circle v-else :size="10" />
              </span>
              <span v-else-if="event.kind === 'countdown'" class="cal-ev-icon" :style="{ color: event.color }"><Hourglass :size="10" /></span>
              <span v-else-if="event.kind === 'holiday'" class="cal-ev-icon" :style="{ color: event.color }">
                <CalendarOff v-if="event.off" :size="10" /><CalendarClock v-else :size="10" />
              </span>
              <span v-else-if="event.kind === 'worktime'" class="cal-ev-icon" :style="{ color: event.color }"><Briefcase :size="10" /></span>
              <span v-else-if="event.kind === 'ledger'" class="cal-ev-icon" :style="{ color: event.color }"><Coins :size="10" /></span>
              <span class="cal-ev-title">{{ event.title }}</span>
              <span v-if="event.startLabel" class="cal-ev-time">{{ event.startLabel }}</span>
              <span v-else-if="event.openChecklist" class="cal-ev-extra">+{{ event.openChecklist }}</span>
            </button>
            <button
              v-if="hiddenCount(index, day)"
              type="button"
              class="cal-event-more"
              @click.stop="$emit('select-day', day, $event)"
            >+{{ hiddenCount(index, day) }}</button>
          </div>
        </div>
        <div class="cal-lanes" :style="{ gridTemplateRows: `repeat(${Math.max(weekViews[index].lanes.length, 1)}, 18px)` }">
          <button
            v-for="segment in weekViews[index].segments"
            :key="`lane-${segment.id}-${index}`"
            type="button"
            class="cal-lane"
            :class="[`kind-${segment.event.kind}`, { 'is-done': segment.event.done, 'continues-before': segment.continuesBefore, 'continues-after': segment.continuesAfter }]"
            :style="{
              gridRow: weekViews[index].laneIndex[segment.id] + 1,
              gridColumn: `${segment.startCol + 1} / ${segment.endCol + 2}`,
              ...tintStyle(segment.event.color, segment.event.kind === 'holiday' ? 18 : 15)
            }"
            :title="`${segment.event.title}${segment.continuesBefore ? '（前面还有）' : ''}${segment.continuesAfter ? '（后面还有）' : ''}`"
            @click.stop="handleEventClick(segment.event, $event)"
          >
            <span class="cal-ev-dot" :class="{ 'is-done': segment.event.done }" :style="{ borderColor: segment.event.color }"></span>
            <span class="cal-ev-title">{{ segment.event.title }}</span>
            <span v-if="segment.event.openChecklist" class="cal-ev-extra">+{{ segment.event.openChecklist }}</span>
          </button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed, ref } from 'vue'
import { Briefcase, CalendarClock, CalendarOff, Circle, CircleCheck, Coins, Hourglass } from 'lucide-vue-next'
import { WEEKDAY_LABELS, isoWeekNumber, toDateKey } from './calendarRange.js'
import { tintStyle } from './calendarColors.js'
import { packWeekLanes, visibleRowCount } from './calendarLanes.js'

const props = defineProps({
  weeks: { type: Array, required: true },
  byDay: { type: Map, default: () => new Map() },
  cellHeight: { type: Number, default: 120 },
  todayKey: { type: String, default: '' },
  selectedKey: { type: String, default: '' },
  monthIndex: { type: Number, required: true },
  muteOutsideMonth: { type: Boolean, default: true },
  lunar: { type: Object, default: () => ({}) },
  holidays: { type: Object, default: () => ({}) }
})

const emit = defineEmits(['select-day', 'open-event', 'select-range', 'drop-task'])

const dayDrag = ref(null)
let suppressClick = false

function cellAtPoint(x, y) {
  const stack = document.elementsFromPoint(x, y) || []
  return stack.find(element => element.classList?.contains('cal-day')) || null
}

function onWeekPointerDown(event) {
  if (event.button !== 0) return
  const cell = event.target.closest?.('.cal-day') || cellAtPoint(event.clientX, event.clientY)
  const key = cell?.dataset?.date
  if (!key) return
  dayDrag.value = { startKey: key, endKey: key, moved: false }
  window.addEventListener('pointermove', onDayPointerMove)
  window.addEventListener('pointerup', onDayPointerUp, { once: true })
}

// 泳道层盖在日期格之上，pointerenter 会落到泳道上，因此按坐标回溯命中的日期格
function onDayPointerMove(event) {
  if (!dayDrag.value) return
  const cell = cellAtPoint(event.clientX, event.clientY)
  const key = cell?.dataset?.date
  if (!key) return
  if (key !== dayDrag.value.startKey) dayDrag.value.moved = true
  dayDrag.value.endKey = key
}

function onDayPointerEnter(day) {
  if (!dayDrag.value) return
  const key = toDateKey(day)
  if (key !== dayDrag.value.startKey) dayDrag.value.moved = true
  dayDrag.value.endKey = key
}

function onDayPointerUp() {
  window.removeEventListener('pointermove', onDayPointerMove)
  const state = dayDrag.value
  dayDrag.value = null
  if (!state || !state.moved || state.startKey === state.endKey) return
  suppressClick = true
  window.setTimeout(() => { suppressClick = false }, 0)
  const [startKey, endKey] = state.startKey <= state.endKey ? [state.startKey, state.endKey] : [state.endKey, state.startKey]
  emit('select-range', startKey, endKey)
}

function handleDayClick(day, event) {
  if (suppressClick) {
    suppressClick = false
    return
  }
  emit('select-day', day, event)
}

function handleEventClick(event, nativeEvent) {
  if (suppressClick) {
    suppressClick = false
    return
  }
  emit('open-event', event, nativeEvent)
}

function isInRange(day) {
  if (!dayDrag.value || !dayDrag.value.moved) return false
  const key = toDateKey(day)
  const [from, to] = dayDrag.value.startKey <= dayDrag.value.endKey
    ? [dayDrag.value.startKey, dayDrag.value.endKey]
    : [dayDrag.value.endKey, dayDrag.value.startKey]
  return key >= from && key <= to
}

const weekViews = computed(() => props.weeks.map(week => {
  const packed = packWeekLanes(week, props.byDay)
  const laneIndex = {}
  packed.lanes.forEach((lane, index) => lane.forEach(item => { laneIndex[item.id] = index }))
  return {
    lanes: packed.lanes,
    segments: packed.segments,
    laneIds: packed.laneIds,
    laneHeight: packed.laneHeight,
    laneIndex,
    limit: visibleRowCount(props.cellHeight, packed.lanes.length)
  }
}))

function dayKey(day) {
  return toDateKey(day)
}

function weekNumber(day) {
  return isoWeekNumber(day)
}

function eventsOf(day, index) {
  const bucket = props.byDay.get(dayKey(day)) || { allDay: [], timed: [] }
  const laneIds = weekViews.value[index]?.laneIds || new Set()
  return [...bucket.allDay.filter(item => !laneIds.has(item.id)), ...bucket.timed]
}

function visibleEvents(index, day) {
  const items = eventsOf(day, index)
  const limit = weekViews.value[index]?.limit ?? 4
  return items.length > limit ? items.slice(0, limit - 1) : items
}

function hiddenCount(index, day) {
  const total = eventsOf(day, index).length
  const limit = weekViews.value[index]?.limit ?? 4
  return total > limit ? total - (limit - 1) : 0
}

function subLabel(day) {
  const key = dayKey(day)
  return props.holidays[key]?.name || props.lunar[key] || ''
}

function eventTitle(event) {
  const parts = [event.title]
  if (event.startLabel && event.endLabel) parts.push(`${event.startLabel} – ${event.endLabel}`)
  else if (event.startLabel) parts.push(event.startLabel)
  if (event.recurring && event.kind === 'task') parts.push('重复任务')
  if (event.openChecklist) parts.push(`${event.openChecklist} 个未完成检查项`)
  return parts.join(' · ')
}
</script>

<style scoped>
.cal-month {
  display: flex;
  flex-direction: column;
  height: 100%;
  min-height: 0;
  background: var(--card);
}
.cal-month-dow {
  display: grid;
  grid-template-columns: 36px repeat(7, minmax(0, 1fr));
  border-bottom: 1px solid var(--line);
  color: var(--muted);
  font-size: 11px;
  font-weight: 600;
  text-align: center;
}
.cal-month-dow > span:not(.cal-week-gutter) {
  padding: 7px 0;
}
.cal-month-body {
  display: grid;
  flex: 1 1 auto;
  min-height: 0;
}
.cal-week {
  position: relative;
  display: grid;
  grid-template-columns: 36px repeat(7, minmax(0, 1fr));
  min-height: 0;
  border-bottom: 1px solid var(--line);
}
.cal-week:last-child {
  border-bottom: 0;
}
.cal-week-gutter {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: flex-start;
  gap: 1px;
  padding-top: 5px;
  border-right: 1px solid var(--line);
  color: var(--muted);
  font-size: 10px;
  font-variant-numeric: tabular-nums;
}
.cal-week-gutter small {
  font-size: 8px;
  opacity: .7;
}
.cal-day {
  display: flex;
  flex-direction: column;
  min-width: 0;
  min-height: 0;
  padding: 4px 4px 3px;
  border-right: 1px solid var(--line);
  cursor: pointer;
  overflow: hidden;
}
.cal-day:last-of-type {
  border-right: 0;
}
.cal-day:hover {
  background: color-mix(in srgb, var(--paper) 60%, transparent);
}
.cal-day.muted .cal-day-num,
.cal-day.muted .cal-day-sub {
  color: var(--muted);
  opacity: .55;
}
.cal-day.selected {
  background: color-mix(in srgb, var(--accent) 5%, transparent);
}
.cal-day.in-range {
  background: color-mix(in srgb, var(--accent) 12%, transparent);
  box-shadow: inset 0 0 0 1px color-mix(in srgb, var(--accent) 34%, transparent);
}
.cal-day.in-range {
  background: color-mix(in srgb, var(--accent) 12%, transparent);
  box-shadow: inset 0 0 0 1px color-mix(in srgb, var(--accent) 34%, transparent);
}
.cal-day-head {
  display: flex;
  flex: 0 0 auto;
  align-items: center;
  gap: 4px;
  height: 19px;
  padding: 0 1px 2px;
  overflow: hidden;
}
.cal-day-num {
  color: var(--ink);
  font-size: 12px;
  font-weight: 600;
  font-variant-numeric: tabular-nums;
}
.cal-day.today .cal-day-num {
  display: inline-grid;
  place-items: center;
  width: 19px;
  height: 19px;
  border-radius: 50%;
  background: var(--accent);
  color: #fff;
  font-weight: 700;
}
.cal-day-today {
  color: var(--accent);
  font-size: 9.5px;
  font-weight: 650;
}
.cal-day-sub {
  min-width: 0;
  overflow: hidden;
  color: var(--muted);
  font-size: 9.5px;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.cal-day-events {
  display: flex;
  flex: 1 1 auto;
  flex-direction: column;
  gap: 2px;
  min-height: 0;
  padding-top: var(--lane-height, 0px);
  overflow: hidden;
}
.cal-lanes {
  position: absolute;
  z-index: 1;
  top: 21px;
  right: 0;
  left: 36px;
  display: grid;
  grid-template-columns: repeat(7, minmax(0, 1fr));
  gap: 2px 0;
  pointer-events: none;
}
.cal-lane {
  display: flex;
  align-items: center;
  gap: 4px;
  min-width: 0;
  height: 18px;
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
.cal-lane:hover {
  box-shadow: inset 0 0 0 1px color-mix(in srgb, var(--ink) 14%, transparent);
}
.cal-lane.continues-before {
  margin-left: 0;
  border-top-left-radius: 0;
  border-bottom-left-radius: 0;
}
.cal-lane.continues-after {
  margin-right: 0;
  border-top-right-radius: 0;
  border-bottom-right-radius: 0;
}
.cal-event {
  display: flex;
  flex: 0 0 auto;
  align-items: center;
  gap: 4px;
  width: 100%;
  height: 18px;
  padding: 0 4px;
  border: 1px solid transparent;
  border-radius: 4px;
  color: var(--ink);
  font-size: 10.5px;
  line-height: 1;
  text-align: left;
}
.cal-event:hover {
  filter: brightness(.97);
  box-shadow: inset 0 0 0 1px color-mix(in srgb, var(--ink) 12%, transparent);
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
.cal-ev-icon {
  display: inline-grid;
  flex: 0 0 auto;
  place-items: center;
  width: 10px;
  height: 10px;
}
.cal-ev-title {
  min-width: 0;
  flex: 1;
  overflow: hidden;
  font-weight: 500;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.cal-event.is-done .cal-ev-title,
.cal-lane.is-done .cal-ev-title {
  color: var(--muted);
  text-decoration: line-through;
}
.cal-ev-time {
  flex: 0 0 auto;
  color: var(--ink2);
  font-size: 9px;
  font-variant-numeric: tabular-nums;
  opacity: .85;
}
.cal-ev-extra {
  flex: 0 0 auto;
  color: var(--ink2);
  font-size: 9px;
  opacity: .8;
}
.cal-event.kind-holiday .cal-ev-title,
.cal-event.kind-countdown .cal-ev-title {
  font-weight: 600;
}
.cal-event-more {
  flex: 0 0 auto;
  height: 16px;
  padding: 0 4px;
  border: 0;
  border-radius: 4px;
  background: transparent;
  color: var(--muted);
  font-size: 9.5px;
  text-align: left;
}
.cal-event-more:hover {
  background: var(--paper);
  color: var(--ink);
}
</style>
