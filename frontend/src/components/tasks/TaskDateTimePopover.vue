<template>
  <section class="task-date-time-popover" :class="{ 'is-range': mode === 'RANGE' }" @click.stop>
    <div v-if="showTabs" class="task-date-time-tabs" role="tablist" aria-label="日期类型">
      <button type="button" role="tab" :aria-selected="mode === 'DATE'" :class="{ active: mode === 'DATE' }" @click="$emit('update:mode', 'DATE')">日期</button>
      <button type="button" role="tab" :aria-selected="mode === 'RANGE'" :class="{ active: mode === 'RANGE' }" @click="$emit('update:mode', 'RANGE')">时间段</button>
    </div>

    <template v-if="mode === 'DATE'">
      <div class="task-date-time-shortcuts" aria-label="快捷日期">
        <button type="button" aria-label="今天" title="今天" @click="$emit('quick-date', 'TODAY')"><Sun :size="22" /></button>
        <button type="button" aria-label="明天" title="明天" @click="$emit('quick-date', 'TOMORROW')"><Sunrise :size="22" /></button>
        <button type="button" aria-label="下周" title="下周" @click="$emit('quick-date', 'NEXT_WEEK')"><CalendarPlus :size="22" /></button>
        <button type="button" aria-label="下月" title="下月" @click="$emit('quick-date', 'NEXT_MONTH')"><Moon :size="22" /></button>
      </div>

      <div class="task-date-time-month-head">
        <strong>{{ monthLabel }}</strong>
        <div>
          <button type="button" aria-label="上个月" title="上个月" @click="$emit('move-month', -1)"><ChevronLeft :size="17" /></button>
          <button type="button" aria-label="回到今天" title="回到今天" @click="$emit('quick-date', 'TODAY')"><Circle :size="13" /></button>
          <button type="button" aria-label="下个月" title="下个月" @click="$emit('move-month', 1)"><ChevronRight :size="17" /></button>
        </div>
      </div>

      <div class="task-date-time-calendar" role="grid" aria-label="选择日期">
        <span v-for="weekday in weekdays" :key="weekday" role="columnheader">{{ weekday }}</span>
        <button v-for="day in calendarDays" :key="day.key" type="button" role="gridcell" :aria-label="day.key" :aria-selected="day.selected" :class="{ 'other-month': day.otherMonth, today: day.today, selected: day.selected, repeat: day.repeat && !day.selected }" @click="$emit('select-date', day.key)"><span>{{ day.date.getDate() }}</span><small v-if="day.lunar">{{ day.lunar }}</small></button>
      </div>
    </template>

    <template v-else>
      <div class="task-date-time-range-fields">
        <label class="task-range-row">
          <span>开始</span>
          <input type="date" readonly :value="rangeStart" aria-label="开始日期" @click="rangePicker = rangePicker === 'start' ? '' : 'start'" />
          <input v-if="!allDay" type="time" :value="rangeStartTime" aria-label="开始时间" @change="$emit('update:rangeStartTime', $event.target.value)" />
        </label>
        <label class="task-range-row">
          <span>结束</span>
          <input type="date" readonly :value="rangeEnd" aria-label="结束日期" @click="rangePicker = rangePicker === 'end' ? '' : 'end'" />
          <input v-if="!allDay" type="time" :value="rangeEndTime" aria-label="结束时间" @change="$emit('update:rangeEndTime', $event.target.value)" />
        </label>
        <div class="task-range-allday">
          <span>全天</span>
          <button type="button" role="switch" :aria-checked="allDay" class="task-range-switch" :class="{ on: allDay }" @click="$emit('toggle-all-day', !allDay)"><i></i></button>
        </div>
        <div v-if="rangePicker" class="task-range-picker">
          <div class="task-date-time-month-head">
            <strong>{{ monthLabel }}</strong>
            <div>
              <button type="button" aria-label="上个月" title="上个月" @click="$emit('move-month', -1)"><ChevronLeft :size="17" /></button>
              <button type="button" aria-label="回到今天" title="回到今天" @click="$emit('quick-date', 'TODAY')"><Circle :size="13" /></button>
              <button type="button" aria-label="下个月" title="下个月" @click="$emit('move-month', 1)"><ChevronRight :size="17" /></button>
            </div>
          </div>
          <div class="task-date-time-calendar" role="grid" aria-label="选择时间段日期">
            <span v-for="weekday in weekdays" :key="weekday" role="columnheader">{{ weekday }}</span>
            <button v-for="day in pickerDays" :key="day.key" type="button" role="gridcell" :aria-label="day.key" :aria-selected="day.selected" :class="{ 'other-month': day.otherMonth, today: day.today, selected: day.selected, 'in-range': day.inRange, repeat: day.repeat }" @click="pickRangeDay(day.key)"><span>{{ day.date.getDate() }}</span><small v-if="day.lunar">{{ day.lunar }}</small></button>
          </div>
        </div>
      </div>
    </template>

    <slot v-if="mode === 'DATE' || mode === 'RANGE'" name="rows" :mode="mode" />

    <footer class="task-date-time-footer">
      <button type="button" class="task-date-time-clear-date" @click="$emit('clear-date')">{{ clearLabel }}</button>
      <button type="button" class="task-date-time-apply" @click="$emit('apply')">{{ applyLabel }}</button>
    </footer>
  </section>
</template>

<script setup>
import { computed, ref } from 'vue';
import { CalendarPlus, ChevronLeft, ChevronRight, Circle, Moon, Sun, Sunrise } from 'lucide-vue-next';

const props = defineProps({
  mode: { type: String, default: 'DATE' },
  /** 单一日期场景（如补记专注开始/结束时间）不显示「日期/时间段」页签 */
  showTabs: { type: Boolean, default: true },
  month: { type: Date, required: true },
  calendarDays: { type: Array, required: true },
  selectedKey: { type: String, default: '' },
  time: { type: String, default: '' },
  allDay: { type: Boolean, default: false },
  monthLabel: { type: String, required: true },
  applyLabel: { type: String, default: '确定' },
  clearLabel: { type: String, default: '清除' },
  rangeStart: { type: String, default: '' },
  rangeEnd: { type: String, default: '' },
  rangeStartTime: { type: String, default: '' },
  rangeEndTime: { type: String, default: '' },
});
const emit = defineEmits(['update:mode', 'quick-date', 'move-month', 'select-date', 'select-time', 'clear-time', 'clear-date', 'toggle-all-day', 'apply', 'update:rangeStart', 'update:rangeEnd', 'update:rangeStartTime', 'update:rangeEndTime']);
const weekdays = ['一', '二', '三', '四', '五', '六', '日'];
const rangePicker = ref('');
const pickerDays = computed(() => props.calendarDays.map(day => ({
  ...day,
  selected: day.key === props.rangeStart || day.key === props.rangeEnd,
  inRange: Boolean(props.rangeStart && props.rangeEnd && day.key > props.rangeStart && day.key < props.rangeEnd),
})));
function pickRangeDay(key) {
  emit('select-date', key);
  const other = rangePicker.value === 'start' ? props.rangeEnd : props.rangeStart;
  if (other) rangePicker.value = '';
  else rangePicker.value = rangePicker.value === 'start' ? 'end' : 'start';
}
</script>

<style scoped>
.task-date-time-popover {
  display: grid;
  width: min(378px, calc(100vw - 24px));
  max-height: min(690px, calc(100dvh - 24px));
  gap: 10px;
  overflow: auto;
  box-sizing: border-box;
  padding: 12px;
  border: 1px solid var(--line);
  border-radius: 8px;
  background: var(--card);
  color: var(--ink);
  box-shadow: 0 16px 34px rgb(15 23 42 / 20%);
  overscroll-behavior: contain;
}
.task-date-time-tabs { display: grid; grid-template-columns: 1fr 1fr; gap: 3px; padding: 3px; border-radius: 6px; background: var(--paper); }
.task-date-time-tabs button { min-height: 29px; border: 0; border-radius: 5px; background: transparent; color: var(--muted); font-size: 12px; cursor: pointer; }
.task-date-time-tabs button.active { background: var(--card); color: var(--accent); box-shadow: 0 1px 3px rgb(15 23 42 / 12%); }
.task-date-time-shortcuts { display: grid; grid-template-columns: repeat(4, 1fr); gap: 4px; }
.task-date-time-shortcuts button { display: grid; min-height: 34px; place-items: center; border: 0; border-radius: 6px; background: transparent; color: var(--muted); cursor: pointer; }
.task-date-time-shortcuts button:hover, .task-date-time-shortcuts button:focus-visible { background: var(--accent-soft); color: var(--accent); }
.task-date-time-month-head { display: grid; grid-template-columns: 1fr auto; align-items: center; gap: 8px; }
.task-date-time-month-head strong { font-size: 13px; font-weight: 600; }
.task-date-time-month-head > div { display: flex; gap: 2px; }
.task-date-time-month-head button { display: grid; width: 28px; min-height: 28px; place-items: center; border: 0; border-radius: 5px; background: transparent; color: var(--muted); cursor: pointer; }
.task-date-time-month-head button:hover, .task-date-time-month-head button:focus-visible { background: var(--accent-soft); color: var(--accent); }
.task-date-time-calendar { display: grid; grid-template-columns: repeat(7, minmax(0, 1fr)); gap: 2px; text-align: center; }
.task-date-time-calendar > span { display: grid; min-height: 22px; place-items: center; color: var(--muted); font-size: 10px; }
.task-date-time-calendar > button { display: flex; flex-direction: column; align-items: center; justify-content: center; width: 30px; max-width: 100%; aspect-ratio: 1; min-height: 0; margin: auto; border: 0; border-radius: 50%; background: transparent; color: var(--ink); font-size: 12px; line-height: 1.05; cursor: pointer; }
.task-date-time-calendar > button small { max-width: 28px; overflow: hidden; color: var(--muted); font-size: 8px; line-height: 1.1; white-space: nowrap; }
.task-date-time-calendar > button.selected small { color: inherit; opacity: .85; }
.task-date-time-calendar > button:hover { background: var(--accent-soft); }
.task-date-time-calendar > button.other-month { color: var(--muted); opacity: .48; }
.task-date-time-calendar > button.today { box-shadow: inset 0 0 0 1px var(--accent); }
.task-date-time-calendar > button.selected { background: var(--accent); color: var(--accent-contrast, #fff); }
.task-date-time-calendar > button.in-range { background: var(--accent-soft); border-radius: 0; }
.task-date-time-calendar > button.repeat { position: relative; }
.task-date-time-calendar > button.repeat::after { content: ""; position: absolute; left: 50%; bottom: 2px; width: 4px; height: 4px; margin-left: -2px; border-radius: 50%; background: var(--accent); }
.task-date-time-range-fields { display: grid; gap: 8px; }
.task-range-row { display: grid; grid-template-columns: 34px minmax(0, 1fr) 84px; align-items: center; gap: 7px; color: var(--ink2); font-size: 12px; }
.task-range-row:has(> input:only-of-type) { grid-template-columns: 34px minmax(0, 1fr); }
.task-range-row input { min-width: 0; min-height: 32px; padding: 0 8px; border: 1px solid var(--line); border-radius: 6px; background: var(--surface); color: var(--ink); font: inherit; font-size: 12px; }
.task-range-row input:focus { outline: none; border-color: color-mix(in srgb, var(--accent) 55%, var(--line)); }
.task-range-allday { display: flex; align-items: center; justify-content: space-between; min-height: 32px; color: var(--ink2); font-size: 12px; }
.task-range-switch { position: relative; width: 40px; height: 22px; border: 0; border-radius: 11px; background: var(--line2); cursor: pointer; transition: background .15s ease; }
.task-range-switch.on { background: var(--accent); }
.task-range-switch i { position: absolute; top: 2px; left: 2px; width: 18px; height: 18px; border-radius: 50%; background: #fff; transition: transform .15s ease; }
.task-range-switch.on i { transform: translateX(18px); }
.task-range-picker { display: grid; gap: 8px; padding: 10px; border: 1px solid var(--line); border-radius: 8px; background: var(--card); box-shadow: 0 10px 26px rgb(15 23 42 / 14%); }
.task-date-time-footer { display: flex; justify-content: space-between; gap: 8px; padding-top: 8px; border-top: 1px solid var(--line); }
.task-date-time-footer button { min-height: 30px; padding: 5px 12px; border: 0; border-radius: 5px; background: transparent; color: var(--muted); font-size: 12px; cursor: pointer; }
.task-date-time-footer .task-date-time-apply { background: var(--accent); color: var(--accent-contrast, #fff); }
</style>
