<template>
  <section class="task-date-time-popover" :class="{ 'is-range': mode === 'RANGE' }" @click.stop>
    <div class="task-date-time-tabs" role="tablist" aria-label="日期类型">
      <button type="button" role="tab" :aria-selected="mode === 'DATE'" :class="{ active: mode === 'DATE' }" @click="$emit('update:mode', 'DATE')">日期</button>
      <button type="button" role="tab" :aria-selected="mode === 'RANGE'" :class="{ active: mode === 'RANGE' }" @click="$emit('update:mode', 'RANGE')">时间段</button>
    </div>

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
      <button v-for="day in calendarDays" :key="day.key" type="button" role="gridcell" :aria-label="day.key" :aria-selected="day.selected" :class="{ 'other-month': day.otherMonth, today: day.today, selected: day.selected }" @click="$emit('select-date', day.key)">{{ day.date.getDate() }}</button>
    </div>

    <template v-if="mode === 'DATE'">
      <div class="task-date-time-time-row">
        <button type="button" class="task-date-time-all-day" :aria-pressed="allDay" @click="$emit('toggle-all-day', !allDay)"><span class="task-date-time-checkbox" :class="{ checked: allDay }" aria-hidden="true">{{ allDay ? '✓' : '' }}</span><span>全天</span></button>
        <button type="button" class="task-date-time-row-trigger" :class="{ active: Boolean(time) }" :aria-expanded="timeOpen" @click="timeOpen = !timeOpen"><Clock3 :size="17" /><span>{{ allDay ? '全天' : (time || '时间') }}</span><ChevronRight :size="15" /></button>
        <button v-if="time" type="button" class="task-date-time-clear" aria-label="清除时间" title="清除时间" @click="$emit('clear-time')"><X :size="15" /></button>
      </div>
      <div v-if="timeOpen" class="task-date-time-time-list" role="listbox" aria-label="选择时间">
        <button v-for="option in halfHourOptions" :key="option" type="button" role="option" :aria-selected="option === time" :class="{ active: option === time }" @click="$emit('select-time', option); timeOpen = false">{{ option }}</button>
      </div>
    </template>
    <div v-else class="task-date-time-range-fields">
      <slot name="range" />
    </div>

    <slot name="reminder" />
    <slot name="repeat" />
    <footer class="task-date-time-footer">
      <button type="button" class="task-date-time-clear-date" @click="$emit('clear-date')">清除</button>
      <button type="button" class="task-date-time-apply" @click="$emit('apply')">{{ applyLabel }}</button>
    </footer>
  </section>
</template>

<script setup>
import { computed, ref } from 'vue';
import { CalendarPlus, ChevronLeft, ChevronRight, Circle, Clock3, Moon, Sun, Sunrise, X } from 'lucide-vue-next';

const props = defineProps({
  mode: { type: String, default: 'DATE' },
  month: { type: Date, required: true },
  calendarDays: { type: Array, required: true },
  selectedKey: { type: String, default: '' },
  time: { type: String, default: '' },
  allDay: { type: Boolean, default: false },
  monthLabel: { type: String, required: true },
  applyLabel: { type: String, default: '确定' },
});
defineEmits(['update:mode', 'quick-date', 'move-month', 'select-date', 'select-time', 'clear-time', 'clear-date', 'toggle-all-day', 'apply']);
const weekdays = ['一', '二', '三', '四', '五', '六', '日'];
const timeOpen = ref(false);
const halfHourOptions = computed(() => Array.from({ length: 48 }, (_, index) => `${String(Math.floor(index / 2)).padStart(2, '0')}:${index % 2 ? '30' : '00'}`));
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
.task-date-time-calendar > button { display: grid; width: 30px; max-width: 100%; aspect-ratio: 1; min-height: 0; margin: auto; place-items: center; border: 0; border-radius: 50%; background: transparent; color: var(--ink); font-size: 12px; cursor: pointer; }
.task-date-time-calendar > button:hover { background: var(--accent-soft); }
.task-date-time-calendar > button.other-month { color: var(--muted); opacity: .48; }
.task-date-time-calendar > button.today { box-shadow: inset 0 0 0 1px var(--accent); }
.task-date-time-calendar > button.selected { background: var(--accent); color: var(--accent-contrast, #fff); }
.task-date-time-time-row { display: flex; align-items: center; gap: 6px; padding-top: 8px; border-top: 1px solid var(--line); }
.task-date-time-all-day, .task-date-time-row-trigger { display: flex; min-height: 30px; align-items: center; gap: 7px; border: 0; border-radius: 5px; background: transparent; color: var(--ink2); font: inherit; font-size: 12px; cursor: pointer; }
.task-date-time-all-day { padding: 4px 6px 4px 0; }
.task-date-time-row-trigger { flex: 1; padding: 4px 6px; text-align: left; }
.task-date-time-row-trigger:hover, .task-date-time-row-trigger.active, .task-date-time-all-day:hover { background: var(--accent-soft); color: var(--accent); }
.task-date-time-row-trigger svg:last-child { margin-left: auto; }
.task-date-time-checkbox { display: grid; width: 14px; height: 14px; place-items: center; border: 1px solid var(--line2); border-radius: 4px; color: var(--accent-contrast, #fff); font-size: 10px; }
.task-date-time-checkbox.checked { border-color: var(--accent); background: var(--accent); }
.task-date-time-clear { display: grid; width: 26px; min-height: 26px; place-items: center; border: 0; border-radius: 5px; background: transparent; color: var(--muted); cursor: pointer; }
.task-date-time-clear:hover { background: var(--accent-soft); color: var(--accent); }
.task-date-time-time-list { display: grid; max-height: 190px; grid-template-columns: repeat(4, 1fr); gap: 3px; overflow: auto; padding: 4px; border: 1px solid var(--line); border-radius: 6px; background: var(--paper); }
.task-date-time-time-list button { min-height: 28px; border: 0; border-radius: 4px; background: transparent; color: var(--ink2); font-size: 11px; cursor: pointer; }
.task-date-time-time-list button:hover, .task-date-time-time-list button.active { background: var(--accent-soft); color: var(--accent); }
.task-date-time-range-fields { display: grid; gap: 7px; padding-top: 8px; border-top: 1px solid var(--line); }
.task-date-time-footer { display: flex; justify-content: space-between; gap: 8px; padding-top: 8px; border-top: 1px solid var(--line); }
.task-date-time-footer button { min-height: 30px; padding: 5px 12px; border: 0; border-radius: 5px; background: transparent; color: var(--muted); font-size: 12px; cursor: pointer; }
.task-date-time-footer .task-date-time-apply { background: var(--accent); color: var(--accent-contrast, #fff); }
</style>
