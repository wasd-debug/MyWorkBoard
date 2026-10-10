<template>
  <section class="task-calendar-page">
    <header class="calendar-head">
      <div><p>任务</p><h1>日历</h1></div>
      <div class="calendar-actions">
        <button type="button" title="上一时间段" aria-label="上一时间段" @click="navigate('prev')"><ChevronLeft /></button>
        <button type="button" @click="navigate('today')">今天</button>
        <button type="button" title="下一时间段" aria-label="下一时间段" @click="navigate('next')"><ChevronRight /></button>
      </div>
    </header>

    <div class="calendar-toolbar">
      <div class="view-switch" aria-label="日历视图">
        <button v-for="item in views" :key="item.key" type="button" :class="{ on: view === item.key }" @click="setView(item.key)">{{ item.label }}</button>
      </div>
      <div v-if="view === 'week' || view === 'month'" class="range-switch" :aria-label="view === 'week' ? '多日范围' : '多周范围'">
        <button v-for="size in rangeOptions" :key="size" type="button" :class="{ on: rangeSize === size }" @click="setRangeSize(size)">{{ size }}{{ view === 'week' ? '日' : '周' }}</button>
      </div>
      <fieldset class="layer-switch"><legend>叠加层</legend>
        <label v-for="item in layerOptions" :key="item.key"><input v-model="layers[item.key]" type="checkbox" @change="saveAndReload">{{ item.label }}<span v-if="layerFailed(item.key)">暂不可用</span></label>
        <label><input v-model="lunarEnabled" type="checkbox" @change="saveAndReload">农历</label>
      </fieldset>
    </div>

    <p v-if="error" class="calendar-error">{{ error }}</p>
    <div v-if="loading" class="calendar-loading">正在加载日历…</div>

    <div v-show="view !== 'year'" class="full-calendar-wrap">
      <FullCalendar ref="calendarRef" :options="calendarOptions" />
    </div>

    <section v-if="view === 'year'" class="year-grid" :aria-label="`${year} 年任务密度`">
      <button v-for="month in yearMonths" :key="month.month" type="button" class="year-month" @click="openMonth(month.month)">
        <span>{{ month.month }} 月</span><b>{{ month.count }}</b><small>{{ month.count ? '项任务' : '暂无任务' }}</small>
        <i :style="{ '--density': month.density }"></i>
      </button>
    </section>

    <dialog ref="rescheduleDialog" class="reschedule-dialog" @close="clearSelected">
      <form method="dialog" @submit.prevent="submitReschedule">
        <header><div><small>移动任务</small><h2>{{ selectedTask?.title }}</h2></div><button type="button" aria-label="关闭" @click="rescheduleDialog.close()"><X /></button></header>
        <label>开始时间<input v-model="rescheduleForm.startAt" type="datetime-local"></label>
        <label>截止时间<input v-model="rescheduleForm.dueAt" type="datetime-local"></label>
        <label class="all-day"><input v-model="rescheduleForm.allDay" type="checkbox">全天任务</label>
        <p v-if="rescheduleError" class="calendar-error">{{ rescheduleError }}</p>
        <footer><button type="submit" class="primary" :disabled="saving">{{ saving ? '保存中…' : '保存改期' }}</button></footer>
      </form>
    </dialog>
  </section>
</template>

<script setup>
import { computed, nextTick, onMounted, reactive, ref, watch } from 'vue'
import FullCalendar from '@fullcalendar/vue3'
import dayGridPlugin from '@fullcalendar/daygrid'
import timeGridPlugin from '@fullcalendar/timegrid'
import interactionPlugin from '@fullcalendar/interaction'
import zhCnLocale from '@fullcalendar/core/locales/zh-cn'
import { ChevronLeft, ChevronRight, X } from 'lucide-vue-next'
import { apiGetTaskCalendar } from '../../packages/api-client/src/index.js'
import { useTasksStore } from '../stores/tasks.js'
import { useAppStore } from '../stores/app.js'

const tasksStore = useTasksStore()
const appStore = useAppStore()
const calendarRef = ref(null)
const rescheduleDialog = ref(null)
const loading = ref(false)
const saving = ref(false)
const error = ref('')
const rescheduleError = ref('')
const view = ref(window.innerWidth <= 1024 ? 'day' : 'month')
const rangeSize = ref(window.innerWidth <= 1024 ? 3 : 6)
const anchor = ref(new Date())
const response = ref({ tasks: [], worktime: [], ledger: [], holidays: [], lunar: [], layers: {} })
const layers = reactive({ worktime: true, ledger: true, holiday: true })
const lunarEnabled = ref(true)
const selectedTask = ref(null)
const rescheduleForm = reactive({ startAt: '', dueAt: '', allDay: false })
const views = [{ key: 'day', label: '日' }, { key: 'week', label: '周' }, { key: 'month', label: '月' }, { key: 'year', label: '年' }]
const layerOptions = [{ key: 'worktime', label: '工时' }, { key: 'ledger', label: '账本' }, { key: 'holiday', label: '节假日' }]
const preferenceKey = computed(() => `task_calendar_preferences_v1:${appStore.authUser?.id || 'local'}`)

function pad(value) { return String(value).padStart(2, '0') }
function dateOnly(value) { return `${value.getFullYear()}-${pad(value.getMonth() + 1)}-${pad(value.getDate())}` }
function localInput(value) {
  if (!value) return ''
  const date = new Date(value)
  return `${dateOnly(date)}T${pad(date.getHours())}:${pad(date.getMinutes())}`
}
function instant(value) { return value ? new Date(value).toISOString() : null }
function readPreferences() {
  try {
    const stored = JSON.parse(localStorage.getItem(preferenceKey.value) || '{}')
    if (views.some(item => item.key === stored.view)) view.value = stored.view
    if ([2, 3, 4, 5, 6, 7].includes(stored.rangeSize)) rangeSize.value = stored.rangeSize
    Object.assign(layers, stored.layers || {})
    if (typeof stored.lunar === 'boolean') lunarEnabled.value = stored.lunar
  } catch { /* use defaults */ }
}
function savePreferences() { localStorage.setItem(preferenceKey.value, JSON.stringify({ view: view.value, rangeSize: rangeSize.value, layers: { ...layers }, lunar: lunarEnabled.value })) }
function range() {
  const current = anchor.value
  if (view.value === 'year') return { from: `${current.getFullYear()}-01-01`, to: `${current.getFullYear() + 1}-01-01` }
  const api = calendarRef.value?.getApi()
  if (api) return { from: dateOnly(api.view.activeStart), to: dateOnly(api.view.activeEnd) }
  const from = new Date(current.getFullYear(), current.getMonth(), 1)
  return { from: dateOnly(from), to: dateOnly(new Date(current.getFullYear(), current.getMonth() + 1, 1)) }
}
async function load() {
  loading.value = true; error.value = ''
  try {
    const selected = Object.entries(layers).filter(([, enabled]) => enabled).map(([key]) => key).join(',')
    response.value = await apiGetTaskCalendar({ ...range(), layers: selected, lunar: lunarEnabled.value ? 1 : 0 })
  } catch (cause) { error.value = cause?.response?.data?.detail || cause?.message || '日历加载失败' }
  finally { loading.value = false }
}
async function saveAndReload() { savePreferences(); await load() }
function layerFailed(key) { return layers[key] && response.value.layers?.[key]?.available === false }
function eventSource() {
  const events = response.value.tasks.map(task => ({ id: task.publicId, title: task.title, start: task.startAt || task.dueAt,
    end: task.dueAt && task.startAt ? task.dueAt : undefined, allDay: task.allDay, editable: task.status !== 'COMPLETED',
    classNames: [`priority-${String(task.priority).toLowerCase()}`], extendedProps: { type: 'task', task } }))
  for (const item of response.value.worktime || []) events.push({ title: `${item.start}–${item.end || '未打卡'}${item.overtimeMinutes > 0 ? ` · 加班${Math.round(item.overtimeMinutes / 60 * 10) / 10}h` : ''}`,
    start: `${item.date}T${item.start || '00:00'}`, end: item.end ? `${item.date}T${item.end}` : undefined, editable: false, classNames: ['overlay-worktime'] })
  for (const item of response.value.ledger || []) events.push({ title: `收 ${item.income} / 支 ${item.expense}`, start: item.date, allDay: true, editable: false, classNames: ['overlay-ledger'] })
  for (const item of response.value.holidays || []) events.push({ title: `${item.off ? '休' : '班'} · ${item.name}`, start: item.date, allDay: true, editable: false, classNames: ['overlay-holiday'] })
  return events.filter(item => item.start)
}
const lunarMap = computed(() => new Map((response.value.lunar || []).map(item => [item.date, item])))
const rangeOptions = computed(() => view.value === 'week' ? [3, 5, 7] : [2, 4, 6])
const calendarOptions = computed(() => ({ plugins: [dayGridPlugin, timeGridPlugin, interactionPlugin], initialView: calendarView(view.value),
  headerToolbar: false, locale: zhCnLocale, firstDay: 1, height: 'auto', editable: window.innerWidth > 1024, eventResizableFromStart: true,
  views: { timeGridRange: { type: 'timeGrid', duration: { days: rangeSize.value }, dateIncrement: { days: rangeSize.value } },
    dayGridRange: { type: 'dayGrid', duration: { weeks: rangeSize.value }, dateIncrement: { weeks: rangeSize.value } } },
  dayMaxEvents: 3, nowIndicator: true, events: eventSource(), datesSet(info) { anchor.value = info.view.currentStart; if (!loading.value) load() },
  dayCellContent(info) { const lunar = lunarMap.value.get(dateOnly(info.date)); return { html: `<span>${info.dayNumberText}</span>${lunar ? `<small>${lunar.festival || lunar.solarTerm || lunar.lunarDate}</small>` : ''}` } },
  eventDrop: persistEventChange, eventResize: persistEventChange, eventClick: info => { if (info.event.extendedProps.type === 'task') openReschedule(info.event.extendedProps.task) } }))
function calendarView(value) { return value === 'day' ? 'timeGridDay' : value === 'week' ? 'timeGridRange' : 'dayGridRange' }
async function setView(next) {
  view.value = next; savePreferences()
  if (next !== 'year') await nextTick(() => { calendarRef.value?.getApi().changeView(calendarView(next)); load() })
  else await load()
}
async function setRangeSize(size) { rangeSize.value = size; savePreferences(); await nextTick(); calendarRef.value?.getApi().changeView(calendarView(view.value)); await load() }
function navigate(direction) {
  if (view.value === 'year') {
    const delta = direction === 'prev' ? -1 : direction === 'next' ? 1 : 0
    anchor.value = direction === 'today' ? new Date() : new Date(anchor.value.getFullYear() + delta, 0, 1)
    load(); return
  }
  const api = calendarRef.value?.getApi(); if (!api) return
  api[direction](); anchor.value = api.getDate()
}
async function persistEventChange(change) {
  const task = localTask(change.event.extendedProps.task)
  try {
    await tasksStore.update(task, { startAt: change.event.start?.toISOString() || null, dueAt: change.event.end?.toISOString() || change.event.start?.toISOString() || null,
      allDay: change.event.allDay, durationMinutes: change.event.end && change.event.start ? Math.round((change.event.end - change.event.start) / 60000) : task.durationMinutes })
  } catch (cause) { change.revert(); error.value = cause?.message || '任务改期失败，已恢复原时间' }
}
function openReschedule(task) {
  selectedTask.value = localTask(task); rescheduleError.value = ''
  Object.assign(rescheduleForm, { startAt: localInput(selectedTask.value.startAt), dueAt: localInput(selectedTask.value.dueAt), allDay: selectedTask.value.allDay })
  rescheduleDialog.value?.showModal()
}
function localTask(calendarTask) { return { ...(tasksStore.tasks.find(item => item.publicId === calendarTask.publicId) || {}), ...calendarTask } }
async function submitReschedule() {
  if (!selectedTask.value) return
  saving.value = true; rescheduleError.value = ''
  try {
    await tasksStore.update(selectedTask.value, { startAt: instant(rescheduleForm.startAt), dueAt: instant(rescheduleForm.dueAt), allDay: rescheduleForm.allDay })
    rescheduleDialog.value.close(); await load()
  } catch (cause) { rescheduleError.value = cause?.message || '任务改期失败' }
  finally { saving.value = false }
}
function clearSelected() { selectedTask.value = null; rescheduleError.value = '' }
const year = computed(() => anchor.value.getFullYear())
const yearMonths = computed(() => Array.from({ length: 12 }, (_, index) => {
  const count = response.value.tasks.filter(task => { const date = new Date(task.startAt || task.dueAt); return date.getFullYear() === year.value && date.getMonth() === index }).length
  return { month: index + 1, count, density: Math.min(1, count / 12) }
}))
async function openMonth(month) {
  anchor.value = new Date(year.value, month - 1, 1); view.value = 'month'; savePreferences(); await nextTick()
  rangeSize.value = 6
  const api = calendarRef.value?.getApi(); api?.changeView('dayGridRange', anchor.value); await load()
}
watch(() => appStore.authUser?.id, () => { readPreferences(); load() })
onMounted(async () => { readPreferences(); await tasksStore.init({ waitForRemote: false }); await load() })
</script>

<style scoped>
.task-calendar-page{max-width:1500px;margin:0 auto;padding:34px 36px 90px;color:var(--ink)}
.calendar-head,.calendar-toolbar{display:flex;align-items:center;justify-content:space-between;gap:20px}
.calendar-head p{margin:0 0 4px;color:var(--muted);font-size:.75rem;font-weight:700}.calendar-head h1{margin:0;font-size:2rem;letter-spacing:0}
.calendar-actions,.view-switch,.range-switch{display:flex;border:1px solid var(--line);background:var(--panel)}
.calendar-actions button,.view-switch button,.range-switch button{min-height:38px;border:0;border-right:1px solid var(--line);background:transparent;color:var(--ink);padding:0 13px}.calendar-actions button:last-child,.view-switch button:last-child,.range-switch button:last-child{border-right:0}.calendar-actions svg{width:17px}
.view-switch button.on,.range-switch button.on{background:var(--ink);color:var(--panel)}
.calendar-toolbar{margin:24px 0 18px;padding:13px 0;border-top:1px solid var(--line);border-bottom:1px solid var(--line)}
.layer-switch{display:flex;flex-wrap:wrap;gap:14px;border:0;padding:0;margin:0}.layer-switch legend{position:absolute;width:1px;height:1px;overflow:hidden}.layer-switch label{display:flex;align-items:center;gap:6px;font-size:.8125rem}.layer-switch span{color:var(--down);font-size:.6875rem}
.calendar-error{padding:10px 12px;border-left:3px solid var(--down);background:color-mix(in srgb,var(--down) 8%,transparent)}.calendar-loading{padding:12px 0;color:var(--muted)}
.full-calendar-wrap{min-height:620px}.full-calendar-wrap :deep(.fc){--fc-border-color:var(--line);--fc-page-bg-color:transparent;--fc-neutral-bg-color:var(--panel2);--fc-list-event-hover-bg-color:var(--panel2);font-size:.8125rem}.full-calendar-wrap :deep(.fc-theme-standard td),.full-calendar-wrap :deep(.fc-theme-standard th){border-color:var(--line)}.full-calendar-wrap :deep(.fc-col-header-cell-cushion){padding:10px 4px;color:var(--muted)}.full-calendar-wrap :deep(.fc-daygrid-day-number){display:flex;justify-content:space-between;width:100%;padding:7px}.full-calendar-wrap :deep(.fc-daygrid-day-number small){color:var(--muted);font-size:.65rem}.full-calendar-wrap :deep(.fc-event){border-radius:2px;border:0;padding:2px 4px}.full-calendar-wrap :deep(.overlay-worktime){background:#2f746f}.full-calendar-wrap :deep(.overlay-ledger){background:#667049}.full-calendar-wrap :deep(.overlay-holiday){background:#a45449}.full-calendar-wrap :deep(.priority-high){background:var(--down)}.full-calendar-wrap :deep(.priority-medium){background:var(--warn);color:#1f2321}
.year-grid{display:grid;grid-template-columns:repeat(4,minmax(0,1fr));gap:1px;background:var(--line);border:1px solid var(--line)}.year-month{position:relative;display:grid;grid-template-columns:1fr auto;gap:5px;text-align:left;min-height:150px;padding:18px;border:0;background:var(--panel);color:var(--ink);overflow:hidden}.year-month>span{font-weight:700}.year-month>b{font-size:1.75rem}.year-month small{color:var(--muted)}.year-month i{position:absolute;left:0;right:0;bottom:0;height:calc(10px + var(--density) * 58px);background:color-mix(in srgb,#2f746f calc(18% + var(--density) * 60%),transparent)}
.reschedule-dialog{width:min(440px,calc(100vw - 28px));border:1px solid var(--line);background:var(--panel);color:var(--ink);padding:0}.reschedule-dialog::backdrop{background:rgba(0,0,0,.45)}.reschedule-dialog form{padding:20px;display:grid;gap:15px}.reschedule-dialog header{display:flex;justify-content:space-between;align-items:flex-start}.reschedule-dialog h2{margin:3px 0;font-size:1.125rem}.reschedule-dialog header button{width:36px;height:36px;display:grid;place-items:center;border:1px solid var(--line);background:transparent;color:var(--ink)}.reschedule-dialog header svg{width:18px}.reschedule-dialog label{display:grid;gap:6px;font-size:.75rem;font-weight:700}.reschedule-dialog input[type=datetime-local]{width:100%;box-sizing:border-box;padding:10px;border:1px solid var(--line);background:var(--panel2);color:var(--ink)}.reschedule-dialog .all-day{display:flex;align-items:center}.reschedule-dialog footer{display:flex;justify-content:flex-end}.reschedule-dialog .primary{border:0;background:var(--ink);color:var(--panel);padding:10px 16px}
@media(max-width:1023px){.task-calendar-page{padding:22px 14px 110px}.calendar-head{align-items:flex-end}.calendar-toolbar{align-items:flex-start;flex-direction:column}.view-switch,.range-switch{width:100%}.view-switch button,.range-switch button{flex:1}.year-grid{grid-template-columns:repeat(2,minmax(0,1fr))}.year-month{min-height:112px}.full-calendar-wrap{min-height:540px}.full-calendar-wrap :deep(.fc-event-title){white-space:normal}.calendar-actions button{padding:0 10px}}
@media(max-width:420px){.calendar-head h1{font-size:1.65rem}.calendar-actions button:nth-child(2){padding:0 8px}.year-month{padding:12px}.layer-switch{gap:10px}.full-calendar-wrap :deep(.fc-timegrid-axis){display:none}}
</style>
