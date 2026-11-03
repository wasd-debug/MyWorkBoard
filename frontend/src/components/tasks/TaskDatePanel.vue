<template>
  <section class="task-date-panel" @click.stop>
    <TaskDateTimePopover
      :mode="mode"
      :month="calendarMonth"
      :calendar-days="calendarDays"
      :month-label="calendarTitle"
      :selected-key="selectedDate"
      :time="allDay ? '' : time"
      :all-day="allDay"
      :range-start="rangeStart"
      :range-end="rangeEnd"
      :range-start-time="rangeStartTime"
      :range-end-time="rangeEndTime"
      range-controlled
      :apply-label="applyLabel"
      @update:mode="setMode"
      @quick-date="chooseDatePreset"
      @move-month="moveMonth"
      @select-date="selectCalendarDate"
      @clear-time="clearTime"
      @clear-date="$emit('clear')"
      @toggle-all-day="setAllDay"
      @update:range-start="applyRangeStart"
      @update:range-end="applyRangeEnd"
      @update:range-start-time="rangeStartTime = $event"
      @update:range-end-time="rangeEndTime = $event"
      @apply="applyPanel"
    >
      <template #rows="{ mode: rowMode }">
        <section v-if="repeatEditorOpen" class="clone-quick-repeat-panel" aria-label="自定义重复设置">
          <header><strong>重复</strong><button type="button" aria-label="关闭重复设置" @click="closeRepeatEditor"><X :size="14" /></button></header>
          <template v-if="repeat.preset === 'CUSTOM'">
            <div class="clone-quick-repeat-mode"><select v-model="repeat.mode" aria-label="重复方式"><option value="DUE_DATE">按到期日期</option><option value="COMPLETION_DATE">按完成日期</option></select></div>
            <div class="clone-quick-repeat-interval"><span>每</span><input v-model.number="repeat.interval" type="number" min="1" max="365" aria-label="重复间隔" /><select :value="repeat.frequency" @change="setRepeatFrequency" aria-label="重复频率"><option value="DAILY">日</option><option value="WEEKLY">周</option><option value="MONTHLY">月</option><option value="YEARLY">年</option></select></div>
            <div v-if="repeat.frequency === 'WEEKLY'" class="clone-quick-repeat-circles" aria-label="重复星期"><button v-for="day in dayCodes" :key="day" type="button" :aria-pressed="repeat.weekdays.includes(day)" :class="{ active: repeat.weekdays.includes(day) }" @click="toggleRepeatWeekday(day)">{{ weekdayShort[day] }}</button></div>
            <div v-if="['MONTHLY', 'YEARLY'].includes(repeat.frequency)" class="clone-quick-repeat-grid"><label>重复日期<select v-model="repeat.monthMode"><option value="DATE">按日期</option><option value="WEEKDAY">按星期</option><option v-if="repeat.frequency === 'MONTHLY'" value="WORKDAY">按工作日</option></select></label><label v-if="repeat.frequency === 'YEARLY'">月份<select v-model.number="repeat.month"><option v-for="month in 12" :key="month" :value="month">{{ month }} 月</option></select></label><template v-if="repeat.monthMode === 'WEEKDAY'"><label>第几周<select v-model.number="repeat.ordinal"><option v-for="ordinal in 5" :key="ordinal" :value="ordinal">第 {{ ordinal }} 个</option><option :value="-1">最后一个</option></select></label><label>星期<select v-model="repeat.weekday"><option v-for="day in dayCodes" :key="day" :value="day">{{ weekdayFull[day] }}</option></select></label></template><label v-if="repeat.monthMode === 'WORKDAY'">工作日<select v-model.number="repeat.workdayPosition"><option :value="1">第一个工作日</option><option :value="-1">最后一个工作日</option></select></label></div>
            <div v-if="['MONTHLY', 'YEARLY'].includes(repeat.frequency) && repeat.monthMode === 'DATE'" class="clone-quick-repeat-date-calendar" aria-label="选择重复日期"><span v-for="weekday in weekdays" :key="`rd-${weekday}`">{{ weekday }}</span><span v-for="(_, index) in repeatCalendarDays.filter(value => !value)" :key="`rdl-${index}`" class="clone-quick-repeat-date-spacer"></span><button v-for="day in repeatCalendarDays.filter(Boolean)" :key="`rd-${day}`" type="button" :aria-pressed="!repeat.lastDay && repeat.monthDays.includes(day)" :class="{ active: !repeat.lastDay && repeat.monthDays.includes(day) }" @click="toggleRepeatMonthDay(day)">{{ day }}</button><button type="button" class="clone-quick-repeat-last-day" :aria-pressed="repeat.lastDay" :class="{ active: repeat.lastDay }" @click="repeat.lastDay = true">最后一天</button></div>
            <div class="clone-quick-repeat-checks"><label v-if="repeat.frequency !== 'WEEKLY' && repeat.monthMode !== 'WORKDAY'" class="clone-quick-repeat-check"><input v-model="repeat.skipWeekends" type="checkbox" />跳过双休日</label><label class="clone-quick-repeat-check"><input v-model="repeat.skipLegalHolidays" type="checkbox" />跳过法定节假日</label></div>
          </template>
          <div v-if="['LUNAR_MONTHLY', 'LUNAR_YEARLY'].includes(repeat.preset)" class="clone-quick-repeat-grid"><label v-if="repeat.preset === 'LUNAR_YEARLY'">农历月份<select v-model.number="repeat.lunarMonth"><option v-for="month in 12" :key="month" :value="month">{{ month }} 月</option></select></label><label>农历日期<select v-model.number="repeat.lunarDay"><option v-for="day in 30" :key="day" :value="day">{{ day }} 日</option></select></label></div>
          <p v-if="repeatError" class="clone-error" role="alert">{{ repeatError }}</p>
          <footer><button type="button" @click="cancelRepeatEditor">取消</button><button type="button" class="clone-quick-date-apply" @click="applyRepeatEditor">确定</button></footer>
        </section>
        <div v-else class="clone-date-prop-rows" aria-label="任务属性">
          <div v-if="rowMode === 'DATE'" class="clone-date-prop-row">
            <button ref="timeToggleRef" type="button" class="clone-date-prop-trigger" :class="{ active: Boolean(time) }" :aria-expanded="dateMenu === 'time'" @click="toggleMenu('time')"><Clock3 :size="15" /><span>{{ time || '时间' }}</span><ChevronRight :size="14" :class="{ 'is-open': dateMenu === 'time' }" /></button>
            <button v-if="time" type="button" class="clone-date-prop-clear" aria-label="清除时间" title="清除时间" @click="clearTime"><X :size="14" /></button>
          </div>
          <div v-if="dateMenu === 'time'" class="clone-date-time-list" role="listbox" aria-label="选择时间">
            <button v-for="option in timeOptions" :key="option" type="button" role="option" :aria-selected="option === time" :class="{ active: option === time }" @click="selectTime(option)">{{ option }}<Check v-if="option === time" :size="14" /></button>
          </div>
          <div class="clone-date-prop-row">
            <button ref="reminderToggleRef" type="button" class="clone-date-prop-trigger" :class="{ active: reminder.presets.length > 0 }" :aria-expanded="dateMenu === 'reminder'" @click="toggleMenu('reminder')"><Bell :size="15" /><span>{{ reminder.presets.length ? reminderSummary : '提醒' }}</span><ChevronRight :size="14" :class="{ 'is-open': dateMenu === 'reminder' }" /></button>
            <button v-if="reminder.presets.length" type="button" class="clone-date-prop-clear" aria-label="清除全部提醒" title="清除全部提醒" @click="clearReminders"><X :size="14" /></button>
          </div>
          <div class="clone-date-prop-row">
            <button ref="repeatToggleRef" type="button" class="clone-date-prop-trigger" :class="{ active: Boolean(repeatRule) }" :aria-expanded="dateMenu === 'repeat'" @click="toggleMenu('repeat')"><Repeat2 :size="15" /><span>{{ quickRepeatLabel(repeat) }}</span><ChevronRight :size="14" :class="{ 'is-open': dateMenu === 'repeat' }" /></button>
          </div>
          <div v-if="showEndRow" class="clone-date-prop-row">
            <button ref="endToggleRef" type="button" class="clone-date-prop-trigger" :aria-expanded="dateMenu === 'end'" @click="toggleMenu('end')"><Timer :size="15" /><span>{{ endLabel }}</span><ChevronRight :size="14" :class="{ 'is-open': dateMenu === 'end' }" /></button>
          </div>
          <p v-if="error" class="clone-error" role="alert">{{ error }}</p>
        </div>
        <teleport to="body">
          <div v-if="dateMenu === 'reminder'" ref="reminderMenuRef" class="clone-quick-reminder-menu clone-quick-flyout" :style="reminderMenuStyle" role="listbox" aria-label="提醒设置" aria-multiselectable="true" @click.stop>
            <button v-for="option in reminderMenuOptions" :key="option.value" type="button" role="option" :aria-selected="reminder.presets.includes(option.value)" :class="{ active: reminder.presets.includes(option.value) }" @click="selectReminder(option.value)">{{ option.label }}<Check v-if="reminder.presets.includes(option.value)" :size="14" /></button>
            <template v-if="!allDay && recentReminderOptions.length">
              <span class="clone-quick-flyout-separator"></span>
              <span class="clone-quick-flyout-title">最近使用</span>
              <button v-for="option in recentReminderOptions" :key="`recent-${option.value}`" type="button" role="option" :aria-selected="reminder.presets.includes(option.value)" :class="{ active: reminder.presets.includes(option.value) }" @click="selectReminder(option.value)">{{ option.label }}<Check v-if="reminder.presets.includes(option.value)" :size="14" /></button>
            </template>
            <template v-if="reminder.presets.includes('CUSTOM')">
              <span class="clone-quick-flyout-separator"></span>
              <div class="clone-quick-reminder-custom">
                <label><span>提前</span><input v-model.number="reminder.amount" type="number" min="1" :max="allDay ? 365 : 10080" aria-label="自定义提醒数量" /><select v-model="reminder.unit" aria-label="自定义提醒单位"><option v-if="!allDay" value="MINUTE">分钟</option><option v-if="!allDay" value="HOUR">小时</option><option value="DAY">天</option><option value="WEEK">周</option></select></label>
                <label v-if="allDay">提醒时间<select v-model="reminder.time" aria-label="自定义提醒时间"><option v-for="option in timeOptions" :key="`reminder-time-${option}`" :value="option">{{ option }}</option></select></label>
                <p class="clone-quick-reminder-preview">{{ reminderCustomPreview }}</p>
                <div class="clone-quick-flyout-actions"><button type="button" @click="cancelReminderCustom">取消</button><button type="button" class="primary" @click="applyReminderCustom">确定</button></div>
              </div>
            </template>
          </div>
          <div v-if="dateMenu === 'repeat'" ref="repeatMenuRef" class="clone-quick-repeat-menu clone-quick-flyout" :style="repeatMenuStyle" role="listbox" aria-label="重复规则" @click.stop>
            <template v-for="option in repeatMenuOptions" :key="option.key">
              <span v-if="option.separator" class="clone-quick-flyout-separator"></span>
              <button v-else-if="option.isGroup" type="button" :aria-expanded="repeatGroupOpen === option.key" @click="repeatGroupOpen = repeatGroupOpen === option.key ? '' : option.key">{{ option.label }}<ChevronRight :size="14" :class="{ 'is-open': repeatGroupOpen === option.key }" /></button>
              <button v-else-if="option.child" v-show="repeatGroupOpen === option.groupKey" type="button" role="option" :aria-selected="option.active" :class="{ active: option.active }" @click="selectRepeatMenuOption(option)">{{ option.label }}<Check v-if="option.active" :size="14" /></button>
              <button v-else type="button" role="option" :aria-selected="option.active" :class="{ active: option.active }" @click="selectRepeatMenuOption(option)">{{ option.label }}<Check v-if="option.active" :size="14" /></button>
            </template>
          </div>
          <div v-if="dateMenu === 'end'" ref="endMenuRef" class="clone-quick-end-menu clone-quick-flyout" :style="endMenuStyle" role="listbox" aria-label="结束重复" @click.stop>
            <button type="button" role="option" :aria-selected="repeat.endMode === 'NEVER'" :class="{ active: repeat.endMode === 'NEVER' }" @click="selectEndMode('NEVER')">永不结束<Check v-if="repeat.endMode === 'NEVER'" :size="14" /></button>
            <span class="clone-quick-flyout-separator"></span>
            <button type="button" :aria-expanded="endPanel === 'date'" @click="toggleEndPanel('date')">按日期结束<ChevronRight :size="14" :class="{ 'is-open': endPanel === 'date' }" /></button>
            <div v-if="endPanel === 'date'" class="clone-quick-end-calendar">
              <div class="clone-quick-end-cal-head"><button type="button" aria-label="上个月" @click="moveEndMonth(-1)"><ChevronLeft :size="15" /></button><strong>{{ endCalendarTitle }}</strong><button type="button" aria-label="下个月" @click="moveEndMonth(1)"><ChevronRight :size="15" /></button></div>
              <div class="clone-quick-end-cal-grid"><span v-for="weekday in weekdays" :key="`end-${weekday}`">{{ weekday }}</span><button v-for="day in endCalendarDays" :key="day.key" type="button" :class="{ 'other-month': !day.inMonth, selected: day.selected }" @click="pickEndDate(day.key)">{{ day.date.getDate() }}</button></div>
            </div>
            <button type="button" :aria-expanded="endPanel === 'count'" @click="toggleEndPanel('count')">按次数结束<ChevronRight :size="14" :class="{ 'is-open': endPanel === 'count' }" /></button>
            <div v-if="endPanel === 'count'" class="clone-quick-end-count">
              <label>重复<input v-model.number="endCountDraft" type="number" min="1" aria-label="重复次数" />次</label>
              <div class="clone-quick-flyout-actions"><button type="button" @click="cancelEndCount">取消</button><button type="button" class="primary" @click="applyEndCount">确定</button></div>
            </div>
          </div>
        </teleport>
      </template>
    </TaskDateTimePopover>
  </section>
</template>

<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue';
import { Bell, Check, ChevronLeft, ChevronRight, Clock3, Repeat2, Timer, X } from 'lucide-vue-next';
import TaskDateTimePopover from './TaskDateTimePopover.vue';
import { apiGetTaskCalendar } from '../../../packages/api-client/src/index.js';
import { calendarMonthDays, localDateKey, quickReminderOptions as getQuickReminderOptions, quickReminderPresetLabel, QUICK_ALL_DAY_REMINDER_TIME } from '../../views/taskQuickAddDate.js';
import { buildQuickRepeatRule, createQuickRepeatState, DAY_CODES, parseLunarLabel, quickRepeatLabel, quickRepeatMatchesDate, quickRepeatValidation } from '../../views/taskQuickAddRepeat.js';

const props = defineProps({
  /** 任务的到期时间（ISO 或本地 datetime-local 字符串），空串表示没有日期 */
  dueAt: { type: String, default: '' },
  startAt: { type: String, default: '' },
  allDay: { type: Boolean, default: false },
  rrule: { type: String, default: '' },
  recurrenceAnchor: { type: String, default: 'DUE_DATE' },
  /** { presets: [], amount, unit, time, dailyUntilDone } 或 null，仅用于回显 */
  reminder: { type: Object, default: null },
  /** 没有时间时新选日期用的默认时刻，如 "09:00" */
  defaultTime: { type: String, default: '' },
  defaultReminderMinutes: { type: Number, default: null },
  /** 快捷添加习惯「一打开提醒就先给个默认值」，详情栏不要替用户擅自打开提醒 */
  autoReminderDefault: { type: Boolean, default: false },
  /** 面板挂载/该值变化时自动展开某个二级菜单（time / reminder / repeat / end） */
  initialMenu: { type: String, default: '' },
  applyLabel: { type: String, default: '确定' },
});
const emit = defineEmits(['apply', 'clear', 'update:reminder', 'update:rrule']);

const weekdays = ['一', '二', '三', '四', '五', '六', '日'];
const dayCodes = DAY_CODES;
const weekdayShort = { MO: '一', TU: '二', WE: '三', TH: '四', FR: '五', SA: '六', SU: '日' };
const weekdayFull = { MO: '周一', TU: '周二', WE: '周三', TH: '周四', FR: '周五', SA: '周六', SU: '周日' };
const QUICK_WEEKDAY_TEXT = ['周日', '周一', '周二', '周三', '周四', '周五', '周六'];
const QUICK_REPEAT_GROUPS = { WORKDAY: 'group-workday', LEGAL_WORKDAY: 'group-workday', WEEKEND: 'group-holiday', LEGAL_HOLIDAY: 'group-holiday', LUNAR_MONTHLY: 'group-lunar', LUNAR_YEARLY: 'group-lunar' };
const REMINDER_RECENT_KEY = 'salary-sync.quickReminderRecent';
const ALL_DAY_REMINDER_VALID = ['TODAY', '1_DAY', '2_DAY', '3_DAY', '1_WEEK'];

function toLocal(value) {
  if (!value) return '';
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return '';
  return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')}T${String(date.getHours()).padStart(2, '0')}:${String(date.getMinutes()).padStart(2, '0')}`;
}
function lunarDayLabel(value) {
  if (!value) return '';
  const text = String(value).trim();
  if (/^(?:闰)?(?:正|冬|腊|[一二三四五六七八九十]{1,3}|\d{1,2})月/.test(text)) return text.replace(/^(?:闰)?(?:正|冬|腊|[一二三四五六七八九十]{1,3}|\d{1,2})月/, '');
  return text;
}

/* ---------- 日期 ---------- */
const dueLocal = toLocal(props.dueAt);
const startLocal = toLocal(props.startAt);
const initialRange = Boolean(startLocal && dueLocal && startLocal !== dueLocal);
const mode = ref(initialRange ? 'RANGE' : 'DATE');
const selectedDate = ref(dueLocal ? dueLocal.slice(0, 10) : '');
const time = ref(!props.allDay && dueLocal ? dueLocal.slice(11, 16) : '');
const allDay = ref(props.allDay);
const rangeStart = ref(initialRange ? startLocal.slice(0, 10) : '');
const rangeEnd = ref(initialRange ? dueLocal.slice(0, 10) : '');
const rangeStartTime = ref(initialRange ? startLocal.slice(11, 16) : '09:00');
const rangeEndTime = ref(initialRange ? dueLocal.slice(11, 16) : '10:00');
const error = ref('');
const monthBase = dueLocal ? new Date(dueLocal) : new Date();
const calendarMonth = ref(new Date(monthBase.getFullYear(), monthBase.getMonth(), 1));
const timeOptions = computed(() => Array.from({ length: 48 }, (_, index) => `${String(Math.floor(index / 2)).padStart(2, '0')}:${index % 2 ? '30' : '00'}`));
const todayKey = localDateKey(new Date());
const calendarTitle = computed(() => new Intl.DateTimeFormat('zh-CN', { year: 'numeric', month: 'long' }).format(calendarMonth.value));

/* ---------- 重复 ---------- */
const repeat = reactive(createQuickRepeatState());
const repeatError = ref('');
const repeatTouched = ref(false);
const repeatEditorOpen = ref(false);
const repeatGroupOpen = ref('');
const repeatSnapshot = ref(null);
/** 打开自定义编辑器之前「重复是否已被改过」，取消时要还原，否则点了取消也会把重复写进任务 */
const repeatTouchedBeforeEditor = ref(false);
const repeatRule = computed(() => buildQuickRepeatRule(repeat));
const repeatAnchorDate = computed(() => {
  const key = selectedDate.value || (dueLocal ? dueLocal.slice(0, 10) : '');
  const parsed = key ? new Date(`${key}T00:00:00`) : new Date();
  return Number.isNaN(parsed.getTime()) ? new Date() : parsed;
});
const repeatCalendarDays = computed(() => {
  const anchor = repeatAnchorDate.value;
  const year = anchor.getFullYear();
  const monthIndex = repeat.frequency === 'YEARLY' ? Number(repeat.month || 1) - 1 : anchor.getMonth();
  const first = new Date(year, monthIndex, 1);
  const offset = (first.getDay() + 6) % 7;
  return [...Array(offset).fill(null), ...Array.from({ length: new Date(year, monthIndex + 1, 0).getDate() }, (_, index) => index + 1)];
});
function syncRepeatFromRule(rule = '', anchor = 'DUE_DATE') {
  Object.assign(repeat, createQuickRepeatState());
  const value = String(rule || '');
  const parts = Object.fromEntries(value.split(';').map(item => item.split('=')).filter(item => item.length === 2));
  if (!value) return;
  repeat.mode = anchor === 'COMPLETION_DATE' ? 'COMPLETION_DATE' : 'DUE_DATE';
  if (value === 'FREQ=DAILY') repeat.preset = 'DAILY';
  else if (value === 'FREQ=WEEKLY') repeat.preset = 'WEEKLY';
  else if (value === 'FREQ=MONTHLY') repeat.preset = 'MONTHLY';
  else if (value === 'FREQ=YEARLY') repeat.preset = 'YEARLY';
  else if (value === 'FREQ=WEEKLY;BYDAY=MO,TU,WE,TH,FR') repeat.preset = 'WORKDAY';
  else if (value === 'FREQ=WEEKLY;BYDAY=SA,SU') repeat.preset = 'WEEKEND';
  else if (parts['X-TASK-KIND'] === 'LEGAL_WORKDAY') repeat.preset = 'LEGAL_WORKDAY';
  else if (parts['X-TASK-KIND'] === 'LEGAL_HOLIDAY') repeat.preset = 'LEGAL_HOLIDAY';
  else if (parts['X-TASK-KIND'] === 'LUNAR_MONTHLY') { repeat.preset = 'LUNAR_MONTHLY'; repeat.lunarDay = Number(parts['X-LUNAR-DAY']) || 1; }
  else if (parts['X-TASK-KIND'] === 'LUNAR_YEARLY') { repeat.preset = 'LUNAR_YEARLY'; repeat.lunarMonth = Number(parts['X-LUNAR-MONTH']) || 1; repeat.lunarDay = Number(parts['X-LUNAR-DAY']) || 1; }
  else {
    repeat.preset = 'CUSTOM';
    repeat.frequency = parts.FREQ || 'WEEKLY';
    repeat.interval = Number(parts.INTERVAL) || 1;
    const byDays = parts.BYDAY ? parts.BYDAY.split(',') : [];
    const ordinalDay = byDays.find(day => /^-?\d+(MO|TU|WE|TH|FR|SA|SU)$/.test(day));
    repeat.weekdays = byDays.map(day => day.replace(/^-?\d+/, '')).filter(day => DAY_CODES.includes(day));
    if (ordinalDay) {
      repeat.monthMode = 'WEEKDAY';
      repeat.ordinal = Number(ordinalDay.match(/^-?\d+/)?.[0]) || 1;
      repeat.weekday = ordinalDay.replace(/^-?\d+/, '');
    } else if (parts.BYSETPOS) {
      repeat.monthMode = 'WORKDAY';
      repeat.workdayPosition = Number(parts.BYSETPOS) < 0 ? -1 : 1;
    }
    repeat.month = Number(parts.BYMONTH) || 1;
    repeat.lastDay = Number(parts.BYMONTHDAY) === -1;
    const byMonthDays = String(parts.BYMONTHDAY || '').split(',').map(Number).filter(value => Number.isFinite(value) && value >= 1 && value <= 31);
    repeat.monthDays = byMonthDays.length ? byMonthDays : [1];
    repeat.endMode = parts.COUNT ? 'COUNT' : parts.UNTIL ? 'UNTIL' : 'NEVER';
    repeat.count = Number(parts.COUNT) || 10;
    repeat.until = parts.UNTIL ? `${parts.UNTIL.slice(0, 4)}-${parts.UNTIL.slice(4, 6)}-${parts.UNTIL.slice(6, 8)}` : '';
    repeat.skipLegalHolidays = parts['X-SKIP-HOLIDAYS'] === 'TRUE';
    repeat.skipWeekends = parts['X-SKIP-WEEKENDS'] === 'TRUE';
  }
  if (parts.COUNT) { repeat.endMode = 'COUNT'; repeat.count = Number(parts.COUNT) || 1; }
  if (parts.UNTIL) { repeat.endMode = 'UNTIL'; repeat.until = `${parts.UNTIL.slice(0, 4)}-${parts.UNTIL.slice(4, 6)}-${parts.UNTIL.slice(6, 8)}`; }
}
syncRepeatFromRule(props.rrule, props.recurrenceAnchor);

/* ---------- 提醒 ---------- */
const reminder = reactive({
  presets: [...(props.reminder?.presets || [])],
  amount: Number(props.reminder?.amount) || 1,
  unit: props.reminder?.unit || 'DAY',
  time: props.reminder?.time || QUICK_ALL_DAY_REMINDER_TIME,
  dailyUntilDone: Boolean(props.reminder?.dailyUntilDone),
});
const reminderTouched = ref(false);
const reminderOptions = computed(() => getQuickReminderOptions(allDay.value));
const reminderTime = computed(() => reminder.time || QUICK_ALL_DAY_REMINDER_TIME);
const reminderUnitText = { MINUTE: '分钟', HOUR: '小时', DAY: '天', WEEK: '周' };
const reminderSummary = computed(() => reminder.presets.map(value => value === 'CUSTOM'
  ? `自定义 ${Math.max(1, Number(reminder.amount) || 1)} ${reminderUnitText[reminder.unit] || ''}`
  : quickReminderPresetLabel(value, allDay.value, reminderTime.value)).join('、'));
const reminderMenuOptions = computed(() => reminderOptions.value.map(option => ({
  ...option,
  label: allDay.value && option.value !== 'CUSTOM' ? `${option.label} (${reminderTime.value})` : option.label,
})));
const reminderRecent = ref((() => {
  try {
    const raw = JSON.parse(localStorage.getItem(REMINDER_RECENT_KEY) || '[]');
    return Array.isArray(raw) ? raw.filter(value => typeof value === 'string') : [];
  } catch { return []; }
})());
const recentReminderOptions = computed(() => {
  const valid = value => {
    if (/^CUSTOM:(\d+):(MINUTE|HOUR|DAY|WEEK)$/.test(value)) return allDay.value ? !value.endsWith(':MINUTE') && !value.endsWith(':HOUR') : true;
    return allDay.value ? ALL_DAY_REMINDER_VALID.includes(value) : ['ON_TIME', '5_MIN', '30_MIN', '1_HOUR', '1_DAY'].includes(value);
  };
  return reminderRecent.value
    .filter(value => value !== 'CUSTOM' && valid(value) && !reminderOptions.value.some(option => option.value === value))
    .map(value => ({ value, label: quickReminderPresetLabel(value, allDay.value, reminderTime.value) }));
});
const reminderCustomPreview = computed(() => {
  const key = selectedDate.value || (dueLocal ? dueLocal.slice(0, 10) : localDateKey(new Date()));
  if (!key) return '';
  const base = allDay.value ? new Date(`${key}T00:00:00`) : new Date(`${key}T${time.value || props.defaultTime || '09:00'}`);
  if (Number.isNaN(base.getTime())) return '';
  const amount = Math.max(1, Number(reminder.amount) || 1);
  if (allDay.value) {
    base.setDate(base.getDate() - amount * (reminder.unit === 'WEEK' ? 7 : 1));
    const [hours, minutes] = String(reminderTime.value).split(':').map(Number);
    base.setHours(hours || 0, minutes || 0, 0, 0);
  } else {
    const offset = amount * ({ MINUTE: 1, HOUR: 60, DAY: 1440, WEEK: 10080 }[reminder.unit] || 1);
    base.setMinutes(base.getMinutes() - offset);
  }
  return `将于 ${new Intl.DateTimeFormat('zh-CN', { month: 'long', day: 'numeric', hour: '2-digit', minute: '2-digit', hour12: false }).format(base)} 提醒`;
});

/* ---------- 日历元数据 ---------- */
const calendarMeta = ref({ holidays: {}, lunar: new Map() });
let metaRequest = 0;
async function loadCalendarMeta() {
  const request = ++metaRequest;
  const days = calendarMonthDays(calendarMonth.value);
  const from = localDateKey(days[0].date);
  const last = days[days.length - 1].date;
  const to = localDateKey(new Date(last.getFullYear(), last.getMonth(), last.getDate() + 1));
  try {
    const result = await apiGetTaskCalendar({ from, to, layers: 'holiday', lunar: 1 });
    if (request !== metaRequest) return;
    calendarMeta.value = {
      holidays: Object.fromEntries((result?.holidays || []).map(item => [item.date, { off: Boolean(item.off), name: item.name }])),
      lunar: new Map((result?.lunar || []).map(item => {
        const parsed = parseLunarLabel(item.lunarDate);
        return parsed ? [item.date, { ...parsed, label: item.festival || item.solarTerm || lunarDayLabel(item.lunarDate) || '' }] : null;
      }).filter(Boolean)),
    };
  } catch { /* 元数据失败只是少了农历/节假日角标 */ }
}
watch(calendarMonth, () => { void loadCalendarMeta(); });
onMounted(() => { void loadCalendarMeta(); });
const calendarDays = computed(() => calendarMonthDays(calendarMonth.value).map((day) => ({
  ...day,
  otherMonth: !day.inMonth,
  selected: mode.value === 'DATE'
    ? day.key === selectedDate.value
    : day.key === rangeStart.value || day.key === rangeEnd.value,
  today: day.key === todayKey,
  lunar: calendarMeta.value.lunar.get(day.key)?.label || '',
  repeat: Boolean(repeatRule.value) && day.key >= todayKey && quickRepeatMatchesDate(repeat, day.date, { holidays: calendarMeta.value.holidays, lunar: calendarMeta.value.lunar, anchor: repeatAnchorDate.value }),
})));

/* ---------- 浮层打开状态与定位 ---------- */
const dateMenu = ref('');
const timeToggleRef = ref(null);
const reminderToggleRef = ref(null);
const reminderMenuRef = ref(null);
const repeatToggleRef = ref(null);
const repeatMenuRef = ref(null);
const endToggleRef = ref(null);
const endMenuRef = ref(null);
const reminderMenuStyle = ref({});
const repeatMenuStyle = ref({});
const endMenuStyle = ref({});
let flyoutObserver = null;
function flyoutStyle(toggle, menu, minWidth = 224) {
  if (!toggle || !menu) return {};
  const rect = toggle.getBoundingClientRect();
  const menuHeight = Math.min(menu.scrollHeight || 240, 320);
  const popover = toggle.closest('.task-date-time-popover');
  const popRect = popover?.getBoundingClientRect();
  const menuWidth = Math.max(rect.width, minWidth);
  const top = Math.max(12, Math.min(rect.top - 6, window.innerHeight - menuHeight - 12));
  const spaceRight = popRect ? window.innerWidth - popRect.right - 12 : 0;
  if (popRect && spaceRight >= menuWidth + 8) {
    return { position: 'fixed', top: `${top}px`, left: `${popRect.right + 8}px`, width: `${menuWidth}px` };
  }
  // 详情栏贴在窗口右侧，右边放不下就往左开，别盖住「重复/结束」这些下面的行
  const spaceLeft = popRect ? popRect.left - 12 : 0;
  if (popRect && spaceLeft >= menuWidth + 8) {
    return { position: 'fixed', top: `${top}px`, left: `${popRect.left - menuWidth - 8}px`, width: `${menuWidth}px` };
  }
  const spaceBelow = window.innerHeight - rect.bottom - 12;
  const below = spaceBelow >= menuHeight ? rect.bottom + 4 : Math.max(12, rect.top - Math.min(menuHeight, spaceBelow) - 4);
  return { position: 'fixed', top: `${Math.max(12, Math.min(below, window.innerHeight - menuHeight - 12))}px`, left: `${rect.left}px`, width: `${menuWidth}px` };
}
function menuRef(name) {
  if (name === 'reminder') return reminderMenuRef.value;
  if (name === 'repeat') return repeatMenuRef.value;
  if (name === 'end') return endMenuRef.value;
  return null;
}
function toggleRef(name) {
  if (name === 'reminder') return reminderToggleRef.value;
  if (name === 'repeat') return repeatToggleRef.value;
  if (name === 'end') return endToggleRef.value;
  return null;
}
function applyFlyoutPosition(name) {
  const style = flyoutStyle(toggleRef(name), menuRef(name));
  if (name === 'reminder') reminderMenuStyle.value = style;
  else if (name === 'repeat') repeatMenuStyle.value = style;
  else if (name === 'end') endMenuStyle.value = style;
}
function positionMenu(name) {
  stopFlyoutObserver();
  const target = menuRef(name);
  if (!target) return;
  flyoutObserver = new ResizeObserver(() => applyFlyoutPosition(name));
  flyoutObserver.observe(target);
  applyFlyoutPosition(name);
}
function stopFlyoutObserver() {
  flyoutObserver?.disconnect();
  flyoutObserver = null;
}
function toggleMenu(name) {
  dateMenu.value = dateMenu.value === name ? '' : name;
  if (!dateMenu.value) { stopFlyoutObserver(); return; }
  endPanel.value = '';
  if (name === 'reminder' && !reminder.presets.length && props.autoReminderDefault) applyReminderDefaults();
  if (name === 'repeat') repeatGroupOpen.value = QUICK_REPEAT_GROUPS[repeat.preset] || '';
  if (name === 'end') {
    const anchor = repeat.until ? new Date(`${repeat.until}T00:00:00`) : repeatAnchorDate.value;
    endCalendarMonth.value = new Date(anchor.getFullYear(), anchor.getMonth(), 1);
    endCountDraft.value = Math.max(1, Number(repeat.count) || 10);
  }
  nextTick(() => positionMenu(name));
}
function closeMenu() {
  dateMenu.value = '';
  endPanel.value = '';
  stopFlyoutObserver();
}
/** 外部（任务行的重复标记等）要求直接展开某个二级菜单 */
function openMenu(name) {
  if (!name || dateMenu.value === name) return;
  toggleMenu(name);
}
watch(() => props.initialMenu, (value) => { if (value) nextTick(() => openMenu(value)); });

/* ---------- 日期交互 ---------- */
function moveMonth(offset) {
  calendarMonth.value = new Date(calendarMonth.value.getFullYear(), calendarMonth.value.getMonth() + offset, 1);
}
function selectCalendarDate(key) {
  selectedDate.value = key;
  if (mode.value !== 'RANGE') return;
  if (!rangeStart.value) rangeStart.value = key;
}
function applyRangeStart(key) {
  rangeStart.value = key;
  if (rangeEnd.value && rangeEnd.value < key) rangeEnd.value = '';
}
function applyRangeEnd(key) {
  if (rangeStart.value && key < rangeStart.value) {
    rangeEnd.value = rangeStart.value;
    rangeStart.value = key;
    return;
  }
  rangeEnd.value = key;
}
function chooseDatePreset(preset) {
  const now = new Date();
  const date = new Date(now.getFullYear(), now.getMonth(), now.getDate());
  if (preset === 'TOMORROW') date.setDate(date.getDate() + 1);
  if (preset === 'NEXT_WEEK') {
    const daysUntilNextMonday = ((8 - date.getDay()) % 7) || 7;
    date.setDate(date.getDate() + daysUntilNextMonday);
  }
  if (preset === 'NEXT_MONTH') date.setMonth(date.getMonth() + 1, 1);
  const key = localDateKey(date);
  calendarMonth.value = new Date(date.getFullYear(), date.getMonth(), 1);
  if (mode.value === 'RANGE') {
    rangeStart.value = key;
    rangeEnd.value = '';
  } else selectedDate.value = key;
}
function setMode(next) {
  mode.value = next;
  if (next === 'RANGE') {
    allDay.value = false;
    if (!rangeStart.value) rangeStart.value = selectedDate.value || '';
    return;
  }
  rangeStart.value = '';
  rangeEnd.value = '';
}
function setAllDay(value) {
  allDay.value = value;
  if (value) reminder.unit = 'DAY';
  if (reminder.presets.length) {
    const portableCustoms = reminder.presets.filter(item => /^CUSTOM:\d+:(DAY|WEEK)$/.test(item));
    reminder.presets = [value ? 'TODAY' : '30_MIN', ...portableCustoms];
    markReminderTouched();
  }
}
function selectTime(value) {
  allDay.value = false;
  time.value = value;
  closeMenu();
}
function clearTime() {
  time.value = '';
  allDay.value = true;
  if (selectedDate.value) time.value = '';
}

/* ---------- 提醒交互 ---------- */
function markReminderTouched() {
  reminderTouched.value = true;
  emit('update:reminder', reminderPayload());
}
function reminderPayload() {
  return {
    presets: [...reminder.presets],
    amount: Math.max(1, Number(reminder.amount) || 1),
    unit: reminder.unit,
    time: reminder.time,
    dailyUntilDone: reminder.dailyUntilDone,
    enabled: reminder.presets.length > 0,
  };
}
function rememberPreset(preset) {
  if (!preset || preset === 'CUSTOM') return;
  reminderRecent.value = [preset, ...reminderRecent.value.filter(value => value !== preset)].slice(0, 5);
  try { localStorage.setItem(REMINDER_RECENT_KEY, JSON.stringify(reminderRecent.value)); } catch { /* 忽略存储失败 */ }
}
function applyReminderDefaults() {
  const minutes = props.defaultReminderMinutes;
  const timedPreset = ({ 0: 'ON_TIME', 5: '5_MIN', 30: '30_MIN', 60: '1_HOUR', 1440: '1_DAY' })[minutes];
  const dayPreset = ({ 0: 'TODAY', 1440: '1_DAY', 2880: '2_DAY', 4320: '3_DAY', 10080: '1_WEEK' })[minutes];
  reminder.presets = [allDay.value ? dayPreset || 'TODAY' : timedPreset || '30_MIN'];
  if (!allDay.value && !timedPreset && minutes != null) {
    reminder.amount = Math.max(1, minutes);
    reminder.unit = 'MINUTE';
  }
  rememberPreset(reminder.presets[0]);
  markReminderTouched();
}
function selectReminder(preset) {
  const selected = reminder.presets.includes(preset);
  reminder.presets = selected ? reminder.presets.filter(value => value !== preset) : [...reminder.presets, preset];
  if (!selected) rememberPreset(preset);
  if (preset === 'CUSTOM' && !selected) {
    reminder.amount = 1;
    reminder.unit = allDay.value ? 'DAY' : 'MINUTE';
  }
  markReminderTouched();
}
function applyReminderCustom() {
  const amount = Math.max(1, Math.round(Number(reminder.amount) || 1));
  reminder.amount = amount;
  const encoded = `CUSTOM:${amount}:${reminder.unit}`;
  reminder.presets = reminder.presets.map(value => value === 'CUSTOM' ? encoded : value);
  if (!reminder.presets.includes(encoded)) reminder.presets = [...reminder.presets, encoded];
  rememberPreset(encoded);
  markReminderTouched();
}
function cancelReminderCustom() {
  reminder.presets = reminder.presets.filter(value => value !== 'CUSTOM');
  markReminderTouched();
}
function clearReminders() {
  reminder.presets = [];
  markReminderTouched();
}
// 详情栏的提醒是「打开浮层之后」才异步回读的，props 后到也要能回显；
// 用户已经在面板里改过（reminderTouched）就不再拿 props 覆盖他的选择
watch(() => props.reminder, (value) => {
  if (reminderTouched.value) return;
  const next = value || {};
  const presets = [...(next.presets || [])];
  if (presets.join() !== reminder.presets.join()) reminder.presets = presets;
  reminder.amount = Number(next.amount) || reminder.amount || 1;
  reminder.unit = next.unit || reminder.unit;
  reminder.time = next.time || reminder.time;
  reminder.dailyUntilDone = Boolean(next.dailyUntilDone);
}, { deep: true });

/* ---------- 重复交互 ---------- */
function markRepeatTouched() {
  repeatTouched.value = true;
  emit('update:rrule', { rrule: buildQuickRepeatRule(repeat), recurrenceAnchor: repeat.mode === 'COMPLETION_DATE' ? 'COMPLETION_DATE' : 'DUE_DATE' });
}
const repeatMenuOptions = computed(() => {
  const preset = repeat.preset;
  const date = repeatAnchorDate.value;
  const weekdayText = QUICK_WEEKDAY_TEXT[date.getDay()];
  const monthDay = date.getDate();
  const month = date.getMonth() + 1;
  return [
    { key: 'none', label: '不重复', kind: 'preset', value: '', active: !preset },
    { key: 'DAILY', label: '每天', kind: 'preset', value: 'DAILY', active: preset === 'DAILY' },
    { key: 'WEEKLY', label: `每周（${weekdayText}）`, kind: 'preset', value: 'WEEKLY', active: preset === 'WEEKLY' },
    { key: 'MONTHLY', label: `每月（${monthDay}日）`, kind: 'preset', value: 'MONTHLY', active: preset === 'MONTHLY' },
    { key: 'YEARLY', label: `每年（${month}月${monthDay}日）`, kind: 'preset', value: 'YEARLY', active: preset === 'YEARLY' },
    { key: 'group-workday', label: '工作日', isGroup: true },
    { key: 'WORKDAY', label: '每周工作日（周一至五）', kind: 'preset', value: 'WORKDAY', active: preset === 'WORKDAY', child: true, groupKey: 'group-workday' },
    { key: 'LEGAL_WORKDAY', label: '法定工作日', kind: 'preset', value: 'LEGAL_WORKDAY', active: preset === 'LEGAL_WORKDAY', child: true, groupKey: 'group-workday' },
    { key: 'group-holiday', label: '节假日', isGroup: true },
    { key: 'WEEKEND', label: '每周末', kind: 'preset', value: 'WEEKEND', active: preset === 'WEEKEND', child: true, groupKey: 'group-holiday' },
    { key: 'LEGAL_HOLIDAY', label: '法定节假日', kind: 'preset', value: 'LEGAL_HOLIDAY', active: preset === 'LEGAL_HOLIDAY', child: true, groupKey: 'group-holiday' },
    { key: 'group-lunar', label: '农历重复', isGroup: true },
    { key: 'LUNAR_MONTHLY', label: `农历每月（${monthDay}日）`, kind: 'preset', value: 'LUNAR_MONTHLY', active: preset === 'LUNAR_MONTHLY', child: true, groupKey: 'group-lunar' },
    { key: 'LUNAR_YEARLY', label: `农历每年（${month}月${monthDay}日）`, kind: 'preset', value: 'LUNAR_YEARLY', active: preset === 'LUNAR_YEARLY', child: true, groupKey: 'group-lunar' },
    { key: 'separator-2', separator: true },
    { key: 'CUSTOM', label: '自定义', kind: 'custom', active: preset === 'CUSTOM' },
  ];
});
function selectRepeatMenuOption(option) {
  if (!option || option.separator || option.isGroup) return;
  if (option.kind === 'custom') {
    closeMenu();
    // 快照要在把 preset 改成 CUSTOM 之前拍，否则取消后仍停在「自定义」上
    repeatSnapshot.value = JSON.parse(JSON.stringify(repeat));
    repeatTouchedBeforeEditor.value = repeatTouched.value;
    if (!['CUSTOM', 'LUNAR_MONTHLY', 'LUNAR_YEARLY'].includes(repeat.preset)) repeat.preset = 'CUSTOM';
    repeatEditorOpen.value = true;
    return;
  }
  const value = option.value;
  if (!value) {
    Object.assign(repeat, createQuickRepeatState());
  } else {
    repeat.preset = value;
    const anchor = repeatAnchorDate.value;
    if (value === 'WEEKLY') repeat.weekdays = [DAY_CODES[(anchor.getDay() + 6) % 7]];
    if (value === 'MONTHLY') {
      repeat.monthMode = 'DATE';
      repeat.monthDays = [anchor.getDate()];
      repeat.lastDay = false;
    }
    if (value === 'YEARLY') {
      repeat.monthMode = 'DATE';
      repeat.month = anchor.getMonth() + 1;
      repeat.monthDays = [anchor.getDate()];
      repeat.lastDay = false;
    }
    if (value === 'LUNAR_MONTHLY') repeat.lunarDay = Math.min(30, anchor.getDate());
    if (value === 'LUNAR_YEARLY') {
      repeat.lunarMonth = Math.min(12, anchor.getMonth() + 1);
      repeat.lunarDay = Math.min(30, anchor.getDate());
    }
  }
  repeatError.value = '';
  closeMenu();
  markRepeatTouched();
}
function setRepeatFrequency(event) {
  repeat.frequency = event.target.value;
  if (repeat.frequency === 'YEARLY' && repeat.monthMode === 'WORKDAY') repeat.monthMode = 'DATE';
}
function toggleRepeatWeekday(day) {
  const selected = repeat.weekdays.includes(day) ? repeat.weekdays.filter(item => item !== day) : [...repeat.weekdays, day];
  repeat.weekdays = DAY_CODES.filter(code => selected.includes(code));
}
function toggleRepeatMonthDay(day) {
  const selected = repeat.monthDays.includes(day) ? repeat.monthDays.filter(value => value !== day) : [...repeat.monthDays, day];
  repeat.monthDays = selected.sort((a, b) => a - b);
  repeat.lastDay = false;
}
function applyRepeatEditor() {
  repeatError.value = quickRepeatValidation(repeat);
  if (repeatError.value) return;
  repeatEditorOpen.value = false;
  markRepeatTouched();
}
function cancelRepeatEditor() {
  if (repeatSnapshot.value) Object.assign(repeat, repeatSnapshot.value);
  repeatError.value = '';
  repeatEditorOpen.value = false;
  repeatTouched.value = repeatTouchedBeforeEditor.value;
}
function closeRepeatEditor() {
  // 右上角关闭等同「取消」：没点确定就不该把重复写进任务
  cancelRepeatEditor();
}
const showEndRow = computed(() => Boolean(buildQuickRepeatRule(repeat)));
const endLabel = computed(() => {
  if (repeat.endMode === 'UNTIL' && repeat.until) return `直到 ${repeat.until}`;
  if (repeat.endMode === 'COUNT') return `${Math.max(1, Number(repeat.count) || 1)} 次重复`;
  return '永不结束';
});
const endPanel = ref('');
const endCountDraft = ref(10);
const endCalendarMonth = ref(new Date(new Date().getFullYear(), new Date().getMonth(), 1));
const endCalendarTitle = computed(() => new Intl.DateTimeFormat('zh-CN', { year: 'numeric', month: 'long' }).format(endCalendarMonth.value));
const endCalendarDays = computed(() => calendarMonthDays(endCalendarMonth.value).map(day => ({ ...day, selected: repeat.endMode === 'UNTIL' && repeat.until === day.key })));
function selectEndMode(value) {
  repeat.endMode = value;
  if (value === 'NEVER') repeat.until = '';
  endPanel.value = '';
  closeMenu();
  markRepeatTouched();
}
function toggleEndPanel(panel) {
  endPanel.value = endPanel.value === panel ? '' : panel;
}
function moveEndMonth(offset) {
  endCalendarMonth.value = new Date(endCalendarMonth.value.getFullYear(), endCalendarMonth.value.getMonth() + offset, 1);
}
function pickEndDate(key) {
  repeat.endMode = 'UNTIL';
  repeat.until = key;
  endPanel.value = '';
  closeMenu();
  markRepeatTouched();
}
function applyEndCount() {
  const count = Number(endCountDraft.value);
  if (!Number.isInteger(count) || count < 1) return;
  repeat.endMode = 'COUNT';
  repeat.count = count;
  endPanel.value = '';
  closeMenu();
  markRepeatTouched();
}
function cancelEndCount() {
  endCountDraft.value = Math.max(1, Number(repeat.count) || 10);
  endPanel.value = '';
}

/* ---------- 提交 ---------- */
function applyPanel() {
  let payload = null;
  if (mode.value === 'RANGE') {
    const startValue = `${rangeStart.value}T${allDay.value ? '00:00' : (rangeStartTime.value || '09:00')}`;
    const endValue = `${rangeEnd.value}T${allDay.value ? '00:00' : (rangeEndTime.value || '10:00')}`;
    if (!rangeStart.value || !rangeEnd.value || endValue <= startValue) {
      error.value = '请选择有效的开始和结束日期';
      return;
    }
    payload = { dueAt: endValue, startAt: startValue, allDay: allDay.value };
  } else {
    if (!selectedDate.value) {
      error.value = '请选择日期';
      return;
    }
    const clock = allDay.value ? '00:00' : (time.value || props.defaultTime || '00:00');
    payload = { dueAt: `${selectedDate.value}T${clock}`, startAt: '', allDay: allDay.value };
  }
  error.value = '';
  if (repeatTouched.value) {
    payload.rrule = buildQuickRepeatRule(repeat);
    payload.recurrenceAnchor = repeat.mode === 'COMPLETION_DATE' ? 'COMPLETION_DATE' : 'DUE_DATE';
  }
  if (reminderTouched.value) payload.reminder = reminderPayload();
  emit('apply', payload);
}

/* ---------- 点外部收起浮层 ---------- */
function onDocPointerDown(event) {
  if (!dateMenu.value) return;
  if (event.target.closest?.('.clone-quick-flyout, .clone-date-prop-trigger, .clone-date-prop-clear, .clone-date-time-list')) return;
  closeMenu();
}
onMounted(() => {
  document.addEventListener('pointerdown', onDocPointerDown);
  if (props.initialMenu) nextTick(() => openMenu(props.initialMenu));
});
onBeforeUnmount(() => {
  document.removeEventListener('pointerdown', onDocPointerDown);
  stopFlyoutObserver();
});
</script>

<style scoped>
/* 与快捷添加面板同一套外观：这里的内嵌类名沿用 clone-quick-* / clone-date-prop-*，
   之所以在本组件内再声明一遍，是因为父级视图的样式是 scoped 的，管不到子组件 DOM。 */
.task-date-panel { display: contents; }
.clone-date-prop-rows { display: grid; gap: 2px; padding-top: 8px; border-top: 1px solid var(--line); }
.clone-date-prop-row { display: flex; align-items: center; gap: 6px; }
.clone-date-prop-trigger { display: inline-flex; flex: 1; min-width: 0; min-height: 32px; align-items: center; gap: 8px; padding: 4px 10px; border: 0; border-radius: 6px; background: transparent; color: var(--ink2); font: inherit; font-size: 13px; cursor: pointer; text-align: left; }
.clone-date-prop-trigger span { flex: 1; min-width: 0; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.clone-date-prop-trigger:hover { background: var(--accent-soft); color: var(--accent); }
.clone-date-prop-trigger.active { color: var(--accent); }
.clone-date-prop-trigger svg.is-open { transform: rotate(90deg); transition: transform .15s; }
.clone-date-prop-clear { display: grid; width: 26px; min-height: 26px; flex: none; place-items: center; border: 0; border-radius: 5px; background: transparent; color: var(--muted); cursor: pointer; }
.clone-date-prop-clear:hover { background: var(--accent-soft); color: var(--accent); }
.clone-date-time-list { display: grid; max-height: 216px; gap: 1px; overflow: auto; overscroll-behavior: contain; }
.clone-date-time-list button { display: flex; min-height: 30px; align-items: center; justify-content: space-between; gap: 8px; padding: 0 10px; border: 0; border-radius: 5px; background: transparent; color: var(--ink2); font: inherit; font-size: 13px; cursor: pointer; text-align: left; }
.clone-date-time-list button:hover { background: var(--accent-soft); }
.clone-date-time-list button.active { color: var(--accent); }
.clone-quick-flyout { z-index: 1400; display: grid; align-content: start; gap: 1px; max-height: 320px; overflow: auto; padding: 4px; border: 1px solid var(--line); border-radius: 6px; background: var(--card); color: var(--ink); box-shadow: 0 10px 24px rgb(15 23 42 / 16%); overscroll-behavior: contain; }
.clone-quick-flyout > button { display: flex; min-height: 30px; align-items: center; justify-content: space-between; gap: 8px; padding: 0 8px; border: 0; border-radius: 5px; background: transparent; color: var(--ink2); font: inherit; font-size: 13px; cursor: pointer; text-align: left; }
.clone-quick-flyout > button:hover { background: var(--accent-soft); }
.clone-quick-flyout > button.active { color: var(--accent); }
.clone-quick-flyout > button svg.is-open { transform: rotate(90deg); transition: transform .15s; }
.clone-quick-flyout-separator { height: 1px; margin: 4px 0; background: var(--line); }
.clone-quick-flyout-title { padding: 2px 8px; color: var(--muted); font-size: 11px; }
.clone-quick-flyout-actions { display: flex; justify-content: flex-end; gap: 8px; }
.clone-quick-flyout-actions button { min-height: 26px; padding: 3px 10px; border: 0; border-radius: 5px; background: transparent; color: var(--muted); font: inherit; font-size: 12px; cursor: pointer; }
.clone-quick-flyout-actions button.primary { background: var(--accent); color: var(--accent-contrast, #fff); }
.clone-quick-flyout .clone-quick-reminder-custom { display: grid; gap: 7px; padding: 7px; border-radius: 5px; background: var(--paper); }
.clone-quick-flyout .clone-quick-reminder-custom label { display: flex; justify-content: space-between; align-items: center; gap: 8px; color: var(--ink2); font-size: 12px; }
.clone-quick-flyout .clone-quick-reminder-custom input { width: 72px; }
.clone-quick-flyout .clone-quick-reminder-custom select { min-width: 88px; }
.clone-quick-reminder-preview { margin: 0; color: var(--muted); font-size: 11px; }
.clone-quick-repeat-menu { z-index: 1400; display: grid; gap: 1px; max-height: 240px; overflow: auto; padding: 4px; border: 1px solid var(--line); border-radius: 6px; background: var(--card); box-shadow: 0 10px 24px rgb(15 23 42 / 16%); overscroll-behavior: contain; }
.clone-quick-repeat-menu > button { display: flex; min-height: 30px; align-items: center; justify-content: space-between; gap: 8px; padding: 0 8px; border: 0; border-radius: 5px; background: transparent; color: var(--ink2); font: inherit; font-size: 13px; cursor: pointer; text-align: left; }
.clone-quick-repeat-menu > button:hover { background: var(--accent-soft); }
.clone-quick-repeat-menu > button.active { color: var(--accent); }
.clone-quick-end-calendar { display: grid; gap: 4px; padding: 6px 4px; border-radius: 5px; background: var(--paper); }
.clone-quick-end-cal-head { display: flex; align-items: center; justify-content: space-between; }
.clone-quick-end-cal-head strong { font-size: 12px; }
.clone-quick-end-cal-head button { display: grid; width: 24px; min-height: 24px; place-items: center; border: 0; border-radius: 4px; background: transparent; color: var(--muted); cursor: pointer; }
.clone-quick-end-cal-grid { display: grid; grid-template-columns: repeat(7, 1fr); gap: 2px; text-align: center; }
.clone-quick-end-cal-grid > span { display: grid; min-height: 20px; place-items: center; color: var(--muted); font-size: 10px; }
.clone-quick-end-cal-grid > button { display: grid; min-height: 24px; border: 0; border-radius: 50%; background: transparent; color: var(--ink2); font-size: 11px; cursor: pointer; }
.clone-quick-end-cal-grid > button:hover { background: var(--accent-soft); }
.clone-quick-end-cal-grid > button.other-month { color: var(--muted); opacity: .48; }
.clone-quick-end-cal-grid > button.selected { background: var(--accent); color: var(--accent-contrast, #fff); }
.clone-quick-end-count { display: grid; gap: 7px; padding: 6px 4px; border-radius: 5px; background: var(--paper); }
.clone-quick-end-count label { display: flex; align-items: center; gap: 8px; color: var(--ink2); font-size: 12px; }
.clone-quick-end-count input { width: 64px; }
.clone-quick-repeat-panel { display: grid; gap: 10px; width: 100%; min-width: 0; box-sizing: border-box; }
.clone-quick-repeat-panel > header { display: flex; align-items: center; justify-content: space-between; }
.clone-quick-repeat-panel > header button { width: 26px; min-height: 26px; padding: 0; }
.clone-quick-repeat-mode > select { width: 100%; }
.clone-quick-repeat-interval { display: flex; align-items: center; gap: 8px; color: var(--ink2); font-size: 12px; }
.clone-quick-repeat-interval input { width: 64px; }
.clone-quick-repeat-interval select { flex: 1; min-width: 0; }
.clone-quick-repeat-circles { display: flex; justify-content: space-between; gap: 4px; }
.clone-quick-repeat-circles button { display: grid; width: 30px; aspect-ratio: 1; min-height: 0; place-items: center; padding: 0; border: 0; border-radius: 50%; background: transparent; color: var(--ink2); font-size: 12px; cursor: pointer; }
.clone-quick-repeat-circles button:hover { background: var(--accent-soft); color: var(--accent); }
.clone-quick-repeat-circles button.active { background: var(--accent); color: var(--accent-contrast, #fff); }
.clone-quick-repeat-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 8px; }
.clone-quick-repeat-grid label { display: grid; gap: 4px; color: var(--ink2); font-size: 12px; }
.clone-quick-repeat-date-calendar { display: grid; grid-template-columns: repeat(7, minmax(0, 1fr)); gap: 3px; text-align: center; }
.clone-quick-repeat-date-calendar > span { color: var(--muted); font-size: 11px; }
.clone-quick-repeat-date-calendar > button { min-height: 26px; border: 0; border-radius: 6px; background: transparent; color: var(--ink2); font-size: 12px; cursor: pointer; }
.clone-quick-repeat-date-calendar > button:hover { background: var(--accent-soft); }
.clone-quick-repeat-date-calendar > button.active { background: var(--accent); color: var(--accent-contrast, #fff); }
.clone-quick-repeat-last-day { grid-column: span 7; }
.clone-quick-repeat-checks { display: flex; flex-wrap: wrap; gap: 10px; }
.clone-quick-repeat-check { display: inline-flex; align-items: center; gap: 6px; color: var(--ink2); font-size: 12px; }
.clone-quick-date-apply { min-height: 28px; padding: 4px 12px; border: 0; border-radius: 5px; background: var(--accent); color: var(--accent-contrast, #fff); font: inherit; font-size: 12px; cursor: pointer; }
.clone-error { margin: 0; color: var(--danger, #dc2626); font-size: 12px; }
</style>
