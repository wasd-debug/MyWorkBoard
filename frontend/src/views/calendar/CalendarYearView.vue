<template>
  <div class="cal-year">
    <section v-for="month in months" :key="month.month" class="cal-year-month" :aria-label="`${month.label}`">
      <header>
        <button type="button" :aria-label="`查看 ${month.label}`" @click="$emit('open-month', month.month)">{{ month.label }}</button>
      </header>
      <div class="cal-year-dow">
        <span class="cal-year-wn-head">周</span>
        <span v-for="label in WEEKDAY_LABELS" :key="label">{{ label }}</span>
      </div>
      <div v-for="(week, index) in month.weeks" :key="`${month.month}-${index}`" class="cal-year-week">
        <span class="cal-year-wn">{{ weekNumber(week[0]) }}</span>
        <button
          v-for="day in week"
          :key="dayKey(day)"
          type="button"
          class="cal-year-day"
          :class="{
            muted: day.getMonth() !== month.month - 1,
            today: dayKey(day) === todayKey,
            selected: dayKey(day) === selectedKey,
            'has-event': countOf(day) > 0
          }"
          :title="`${month.month}月${day.getDate()}日 · ${countOf(day)} 项`"
          @click.stop="$emit('select-day', day, $event)"
        >
          <span>{{ day.getDate() }}</span>
          <i v-if="countOf(day)"></i>
        </button>
      </div>
    </section>
  </div>
</template>

<script setup>
import { WEEKDAY_LABELS, isoWeekNumber, toDateKey } from './calendarRange.js'

const props = defineProps({
  months: { type: Array, required: true },
  byDay: { type: Map, default: () => new Map() },
  todayKey: { type: String, default: '' },
  selectedKey: { type: String, default: '' }
})

defineEmits(['select-day', 'open-month'])

function dayKey(day) {
  return toDateKey(day)
}

function weekNumber(day) {
  return isoWeekNumber(day)
}

function countOf(day) {
  const bucket = props.byDay.get(dayKey(day))
  return bucket ? bucket.allDay.length + bucket.timed.length : 0
}
</script>

<style scoped>
.cal-year {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(232px, 1fr));
  gap: 16px 22px;
  padding: 16px 18px 28px;
}
.cal-year-month header {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-bottom: 6px;
}
.cal-year-month header button {
  padding: 2px 6px;
  border: 0;
  border-radius: 6px;
  background: transparent;
  color: var(--ink);
  font-size: 12.5px;
  font-weight: 650;
}
.cal-year-month header button:hover {
  background: var(--paper);
  color: var(--accent);
}
.cal-year-dow,
.cal-year-week {
  display: grid;
  grid-template-columns: 22px repeat(7, minmax(0, 1fr));
  text-align: center;
}
.cal-year-dow span {
  padding-bottom: 3px;
  color: var(--muted);
  font-size: 9px;
}
.cal-year-dow .cal-year-wn-head {
  font-size: 8px;
}
.cal-year-wn {
  display: grid;
  place-items: center;
  color: var(--muted);
  font-size: 8.5px;
  font-variant-numeric: tabular-nums;
  opacity: .75;
}
.cal-year-day {
  position: relative;
  display: grid;
  place-items: center;
  height: 23px;
  margin: 1px;
  border: 0;
  border-radius: 4px;
  background: transparent;
  color: var(--ink);
  font-size: 10.5px;
  font-variant-numeric: tabular-nums;
}
.cal-year-day.has-event {
  background: color-mix(in srgb, var(--accent) 12%, transparent);
  color: var(--accent);
  font-weight: 600;
}
.cal-year-day.muted {
  color: var(--muted);
  opacity: .4;
}
.cal-year-day.selected {
  box-shadow: inset 0 0 0 1px color-mix(in srgb, var(--accent) 45%, transparent);
}
.cal-year-day.today {
  background: var(--accent);
  color: #fff;
  font-weight: 700;
}
.cal-year-day i {
  position: absolute;
  bottom: 2px;
  width: 3px;
  height: 3px;
  border-radius: 50%;
  background: currentColor;
  opacity: .8;
}
@media (max-width: 1023px) {
  .cal-year {
    grid-template-columns: repeat(2, minmax(0, 1fr));
    gap: 14px;
    padding: 12px;
  }
}
</style>
