<template>
  <div class="cal-month">
    <div class="cal-month-dow">
      <span class="cal-week-gutter"></span>
      <span v-for="label in WEEKDAY_LABELS" :key="label">{{ label }}</span>
    </div>
    <div class="cal-month-body" :style="{ gridTemplateRows: `repeat(${weeks.length}, minmax(0, 1fr))` }">
      <div v-for="(week, index) in weeks" :key="`week-${index}`" class="cal-week">
        <div class="cal-week-gutter" :aria-label="`第 ${weekNumber(week[0])} 周`">{{ weekNumber(week[0]) }}<small>周</small></div>
        <div
          v-for="day in week"
          :key="dayKey(day)"
          class="cal-day"
          :class="{
            muted: muteOutsideMonth && day.getMonth() !== monthIndex,
            today: dayKey(day) === todayKey,
            selected: dayKey(day) === selectedKey,
            weekend: [0, 6].includes(day.getDay())
          }"
          @click="$emit('select-day', day)"
        >
          <header class="cal-day-head">
            <span class="cal-day-num">{{ day.getDate() }}</span>
            <span v-if="dayKey(day) === todayKey" class="cal-day-today">今天</span>
            <span v-else-if="subLabel(day)" class="cal-day-sub">{{ subLabel(day) }}</span>
          </header>
          <div class="cal-day-events">
            <button
              v-for="event in visibleEvents(day)"
              :key="event.id"
              type="button"
              class="cal-event"
              :class="[`kind-${event.kind}`, { 'is-done': event.done, 'is-muted': event.kind === 'task' && event.task?.status === 'ABANDONED' }]"
              :style="tintStyle(event.color, event.kind === 'holiday' ? 18 : 15)"
              :title="eventTitle(event)"
              @click.stop="$emit('open-event', event, $event)"
            >
              <span v-if="event.kind === 'task'" class="cal-ev-dot" :class="{ 'is-done': event.done }" :style="{ borderColor: event.color }"></span>
              <span v-else-if="event.kind === 'habit'" class="cal-ev-icon" :style="{ color: event.color }">
                <CircleCheck v-if="event.done" :size="10" /><Circle v-else :size="10" />
              </span>
              <span v-else-if="event.kind === 'countdown'" class="cal-ev-icon" :style="{ color: event.color }"><Hourglass :size="10" /></span>
              <span v-else-if="event.kind === 'holiday'" class="cal-ev-icon" :style="{ color: event.color }">
                <CalendarOff v-if="event.off" :size="10" /><CalendarClock v-else :size="10" />
              </span>
              <span class="cal-ev-title">{{ event.title }}</span>
              <span v-if="event.startLabel" class="cal-ev-time">{{ event.startLabel }}</span>
              <span v-else-if="event.openChecklist" class="cal-ev-extra">+{{ event.openChecklist }}</span>
            </button>
            <button
              v-if="hiddenCount(day)"
              type="button"
              class="cal-event-more"
              @click.stop="$emit('select-day', day)"
            >+{{ hiddenCount(day) }}</button>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { CalendarClock, CalendarOff, Circle, CircleCheck, Hourglass } from 'lucide-vue-next'
import { WEEKDAY_LABELS, isoWeekNumber, toDateKey } from './calendarRange.js'
import { tintStyle } from './calendarColors.js'

const props = defineProps({
  weeks: { type: Array, required: true },
  byDay: { type: Map, default: () => new Map() },
  rowLimit: { type: Number, default: 4 },
  todayKey: { type: String, default: '' },
  selectedKey: { type: String, default: '' },
  monthIndex: { type: Number, required: true },
  muteOutsideMonth: { type: Boolean, default: true },
  lunar: { type: Object, default: () => ({}) },
  holidays: { type: Object, default: () => ({}) }
})

defineEmits(['select-day', 'open-event'])

const limit = computed(() => Math.max(2, Number(props.rowLimit) || 4))

function dayKey(day) {
  return toDateKey(day)
}

function weekNumber(day) {
  return isoWeekNumber(day)
}

function bucket(day) {
  return props.byDay.get(dayKey(day)) || { allDay: [], timed: [] }
}

function eventsOf(day) {
  const items = bucket(day)
  return [...items.allDay, ...items.timed]
}

function visibleEvents(day) {
  const items = eventsOf(day)
  return items.length > limit.value ? items.slice(0, limit.value - 1) : items
}

function hiddenCount(day) {
  const total = eventsOf(day).length
  return total > limit.value ? total - (limit.value - 1) : 0
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
  cursor: default;
  overflow: hidden;
}
.cal-day:last-child {
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
.cal-day-head {
  display: flex;
  flex: 0 0 auto;
  align-items: center;
  gap: 4px;
  min-height: 17px;
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
  overflow: hidden;
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
  border-color: currentColor;
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
.cal-event.is-done .cal-ev-title {
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
.cal-event.kind-holiday .cal-ev-title {
  font-weight: 600;
}
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
