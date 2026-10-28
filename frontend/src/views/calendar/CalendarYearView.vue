<template>
  <div class="cal-year">
    <section v-for="month in months" :key="month.month" class="cal-year-month" :aria-label="`${month.label}`">
      <header>
        <button type="button" :aria-label="`查看 ${month.label}`" @click="$emit('open-month', month.month)">{{ month.label }}</button>
        <em v-if="monthTotal(month)">{{ monthTotal(month) }}</em>
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
            [`is-l${levelOf(day)}`]: levelOf(day) > 0
          }"
          :title="`${month.month}月${day.getDate()}日 · 完成 ${countOf(day)} 项`"
          @click.stop="$emit('select-day', day, $event)"
        >
          <span>{{ day.getDate() }}</span>
        </button>
      </div>
    </section>

    <footer class="cal-year-legend">
      <span>已完成任务</span>
      <i class="is-empty" aria-label="0 项"></i>
      <i class="is-l1" aria-label="1 项"></i>
      <i class="is-l2" aria-label="2 项"></i>
      <i class="is-l3" aria-label="3-4 项"></i>
      <i class="is-l4" aria-label="5 项及以上"></i>
      <span class="is-more">多</span>
      <em v-if="yearTotal">全年 {{ yearTotal }} 项</em>
    </footer>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { WEEKDAY_LABELS, isoWeekNumber, toDateKey } from './calendarRange.js'

const props = defineProps({
  months: { type: Array, required: true },
  completedCounts: { type: Map, default: () => new Map() },
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
  return Number(props.completedCounts.get(dayKey(day)) || 0)
}

/** 0 = 无，1 = 1 项，2 = 2 项，3 = 3~4 项，4 = 5 项及以上 */
function levelOf(day) {
  const count = countOf(day)
  if (!count) return 0
  if (count === 1) return 1
  if (count === 2) return 2
  if (count <= 4) return 3
  return 4
}

function monthTotal(month) {
  const total = month.weeks.flat().reduce((sum, day) => sum + (day.getMonth() === month.month - 1 ? countOf(day) : 0), 0)
  return total || 0
}

const yearTotal = computed(() => props.months.reduce((sum, month) => sum + monthTotal(month), 0))
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
  align-items: baseline;
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
.cal-year-month header em {
  color: var(--muted);
  font-size: 10px;
  font-style: normal;
  font-variant-numeric: tabular-nums;
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
  cursor: pointer;
}
.cal-year-day:hover {
  box-shadow: inset 0 0 0 1px color-mix(in srgb, var(--ink) 18%, transparent);
}
/* 热力图：颜色深浅 = 当天完成的任务数 */
.cal-year-day.is-l1 { background: color-mix(in srgb, var(--accent) 20%, transparent); color: color-mix(in srgb, var(--accent) 78%, var(--ink)); }
.cal-year-day.is-l2 { background: color-mix(in srgb, var(--accent) 36%, transparent); color: color-mix(in srgb, var(--accent) 88%, var(--ink)); font-weight: 600; }
.cal-year-day.is-l3 { background: color-mix(in srgb, var(--accent) 54%, transparent); color: color-mix(in srgb, var(--accent) 92%, var(--ink)); font-weight: 650; }
.cal-year-day.is-l4 { background: color-mix(in srgb, var(--accent) 74%, transparent); color: var(--accent-contrast, #fff); font-weight: 700; }
.cal-year-day.muted {
  color: var(--muted);
  opacity: .4;
}
.cal-year-day.muted.is-l1,
.cal-year-day.muted.is-l2,
.cal-year-day.muted.is-l3,
.cal-year-day.muted.is-l4 {
  opacity: .45;
}
.cal-year-day.selected {
  box-shadow: inset 0 0 0 1.5px color-mix(in srgb, var(--accent) 55%, transparent);
}
.cal-year-day.today {
  box-shadow: inset 0 0 0 1.5px var(--accent);
  color: var(--accent);
  font-weight: 700;
}
.cal-year-day.today.is-l4 {
  color: var(--accent-contrast, #fff);
}
.cal-year-legend {
  grid-column: 1 / -1;
  display: flex;
  align-items: center;
  gap: 5px;
  padding-top: 4px;
  color: var(--muted);
  font-size: 10.5px;
}
.cal-year-legend i {
  width: 12px;
  height: 12px;
  border-radius: 3px;
  background: var(--paper);
}
.cal-year-legend i.is-l1 { background: color-mix(in srgb, var(--accent) 20%, transparent); }
.cal-year-legend i.is-l2 { background: color-mix(in srgb, var(--accent) 36%, transparent); }
.cal-year-legend i.is-l3 { background: color-mix(in srgb, var(--accent) 54%, transparent); }
.cal-year-legend i.is-l4 { background: color-mix(in srgb, var(--accent) 74%, transparent); }
.cal-year-legend .is-more {
  margin-left: 2px;
}
.cal-year-legend em {
  margin-left: auto;
  font-style: normal;
  font-variant-numeric: tabular-nums;
}
@media (max-width: 1023px) {
  .cal-year {
    grid-template-columns: repeat(2, minmax(0, 1fr));
    gap: 14px;
    padding: 12px;
  }
}
</style>
