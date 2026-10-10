<template>
  <section class="task-calendar-page" @click="closeDateMenu">
    <header class="calendar-head">
      <div><p>{{ aggregateMode ? '工作台' : '任务' }}</p><h1>{{ aggregateMode ? '日历' : '任务日历' }}</h1></div>
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
      <fieldset v-if="aggregateMode" class="layer-switch"><legend>显示内容</legend>
        <label v-for="item in layerOptions" :key="item.key"><input v-model="layers[item.key]" type="checkbox" @change="saveAndReload">{{ item.label }}<span v-if="layerFailed(item.key)">暂不可用</span></label>
        <label><input v-model="lunarEnabled" type="checkbox" @change="saveAndReload">农历</label>
      </fieldset>
    </div>

    <p v-if="error" class="calendar-error">{{ error }}</p>
    <div v-if="loading" class="calendar-loading">正在加载日历…</div>
    <div v-show="view !== 'year'" class="full-calendar-wrap" @contextmenu="openDateMenu"><FullCalendar ref="calendarRef" :options="calendarOptions" /></div>

    <section v-if="view === 'year'" class="year-grid" :aria-label="`${year} 年任务密度`">
      <button v-for="month in yearMonths" :key="month.month" type="button" class="year-month" @click="openMonth(month.month)">
        <span>{{ month.month }} 月</span><b>{{ month.count }}</b><small>{{ month.count ? '项任务' : '暂无任务' }}</small><i :style="{ '--density': month.density }"></i>
      </button>
    </section>

    <div v-if="dateMenu.open" class="calendar-context-menu" :style="dateMenuStyle" role="menu" :aria-label="`${dateMenu.date} 新增记录`" @click.stop>
      <header><b>{{ formatMenuDate(dateMenu.date) }}</b><button type="button" aria-label="关闭菜单" @click="closeDateMenu"><X /></button></header>
      <button type="button" role="menuitem" @click="createForDate('task')"><ListPlus />新增任务</button>
      <button v-if="aggregateMode" type="button" role="menuitem" @click="createForDate('ledger')"><ReceiptText />新增流水</button>
      <button v-if="aggregateMode" type="button" role="menuitem" @click="createForDate('worktime')"><Clock3 />新增工时</button>
      <button v-if="aggregateMode" type="button" role="menuitem" @click="createForDate('focus')"><Timer />新增专注</button>
    </div>

    <Drawer v-model:open="editorOpen" :title="selectedTask ? '编辑任务' : '新建任务'">
      <form id="calendar-task-editor" class="calendar-task-form" @submit.prevent="saveTask">
        <label>标题<Input v-model="taskForm.title" placeholder="要完成什么？" /></label>
        <div class="calendar-form-grid"><label>优先级<select v-model="taskForm.priority"><option value="NONE">无</option><option value="LOW">低</option><option value="MEDIUM">中</option><option value="HIGH">高</option></select></label><label>全天任务<span class="calendar-check"><input v-model="taskForm.allDay" type="checkbox">全天</span></label></div>
        <label>开始时间<Input v-model="taskForm.startAt" type="datetime-local" /></label>
        <label>截止时间<Input v-model="taskForm.dueAt" type="datetime-local" /></label>
        <p v-if="editorError" class="calendar-error">{{ editorError }}</p>
      </form>
      <template #footer><Button v-if="selectedTask" variant="danger" :disabled="saving" @click="deleteTask"><Trash2 />删除</Button><Button type="submit" form="calendar-task-editor" :disabled="saving || !taskForm.title.trim()"><Save />{{ saving ? '保存中…' : '保存' }}</Button></template>
    </Drawer>
  </section>
</template>

<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import FullCalendar from '@fullcalendar/vue3'
import dayGridPlugin from '@fullcalendar/daygrid'
import timeGridPlugin from '@fullcalendar/timegrid'
import interactionPlugin from '@fullcalendar/interaction'
import zhCnLocale from '@fullcalendar/core/locales/zh-cn'
import { ChevronLeft, ChevronRight, Clock3, ListPlus, ReceiptText, Save, Timer, Trash2, X } from 'lucide-vue-next'
import { apiGetTaskCalendar } from '../../packages/api-client/src/index.js'
import Button from '../components/ui/Button.vue'
import Drawer from '../components/ui/Drawer.vue'
import Input from '../components/ui/Input.vue'
import { message } from '../services/message.js'
import { useTasksStore } from '../stores/tasks.js'
import { useAppStore } from '../stores/app.js'

const route = useRoute(); const router = useRouter(); const tasksStore = useTasksStore(); const appStore = useAppStore()
const aggregateMode = computed(() => route.path === '/calendar')
const calendarRef = ref(null); const loading = ref(false); const saving = ref(false); const error = ref(''); const editorError = ref('')
const editorOpen = ref(false); const selectedTask = ref(null); const view = ref(window.innerWidth <= 1024 ? 'day' : 'month')
const rangeSize = ref(window.innerWidth <= 1024 ? 3 : 6); const anchor = ref(new Date())
const response = ref({ tasks: [], worktime: [], ledger: [], holidays: [], habits: [], lunar: [], layers: {} })
const layers = reactive({ worktime: true, ledger: true, holiday: true, habit: true }); const lunarEnabled = ref(true)
const taskForm = reactive({ title: '', priority: 'NONE', startAt: '', dueAt: '', allDay: false })
const dateMenu = reactive({ open: false, date: '', x: 0, y: 0 })
const views = [{ key: 'day', label: '日' }, { key: 'week', label: '周' }, { key: 'month', label: '月' }, { key: 'year', label: '年' }]
const layerOptions = [{ key: 'worktime', label: '工时' }, { key: 'ledger', label: '流水' }, { key: 'holiday', label: '节假日' }, { key: 'habit', label: '习惯' }]
const preferenceKey = computed(() => `calendar_preferences_v2:${aggregateMode.value ? 'global' : 'tasks'}:${appStore.authUser?.id || 'local'}`)
const dateMenuStyle = computed(() => ({ left: `${dateMenu.x}px`, top: `${dateMenu.y}px` }))

function pad(value) { return String(value).padStart(2, '0') }
function dateOnly(value) { return `${value.getFullYear()}-${pad(value.getMonth() + 1)}-${pad(value.getDate())}` }
function localInput(value) { if (!value) return ''; const date = new Date(value); return `${dateOnly(date)}T${pad(date.getHours())}:${pad(date.getMinutes())}` }
function instant(value) { return value ? new Date(value).toISOString() : '' }
function readPreferences() { try { const stored = JSON.parse(localStorage.getItem(preferenceKey.value) || '{}'); if (views.some(item => item.key === stored.view)) view.value = stored.view; if ([2, 3, 4, 5, 6, 7].includes(stored.rangeSize)) rangeSize.value = stored.rangeSize; Object.assign(layers, stored.layers || {}); if (typeof stored.lunar === 'boolean') lunarEnabled.value = stored.lunar } catch { /* use defaults */ } }
function savePreferences() { localStorage.setItem(preferenceKey.value, JSON.stringify({ view: view.value, rangeSize: rangeSize.value, layers: { ...layers }, lunar: lunarEnabled.value })) }
function range() { const current = anchor.value; if (view.value === 'year') return { from: `${current.getFullYear()}-01-01`, to: `${current.getFullYear() + 1}-01-01` }; const api = calendarRef.value?.getApi(); if (api) return { from: dateOnly(api.view.activeStart), to: dateOnly(api.view.activeEnd) }; const from = new Date(current.getFullYear(), current.getMonth(), 1); return { from: dateOnly(from), to: dateOnly(new Date(current.getFullYear(), current.getMonth() + 1, 1)) } }
async function load() { loading.value = true; error.value = ''; try { const selected = aggregateMode.value ? Object.entries(layers).filter(([, enabled]) => enabled).map(([key]) => key).join(',') : ''; response.value = await apiGetTaskCalendar({ ...range(), layers: selected, lunar: lunarEnabled.value ? 1 : 0 }) } catch (cause) { error.value = cause?.response?.data?.detail || cause?.message || '日历加载失败' } finally { loading.value = false } }
async function saveAndReload() { savePreferences(); await load() }
function layerFailed(key) { return layers[key] && response.value.layers?.[key]?.available === false }
function eventSource() { const events = response.value.tasks.map(task => ({ id: task.publicId, title: task.title, start: task.startAt || task.dueAt, end: task.dueAt && task.startAt ? task.dueAt : undefined, allDay: task.allDay, editable: task.status !== 'COMPLETED', classNames: [`priority-${String(task.priority).toLowerCase()}`], extendedProps: { type: 'task', task } })); if (!aggregateMode.value) return events.filter(item => item.start); for (const item of response.value.worktime || []) events.push({ title: `${item.start}–${item.end || '未打卡'}${item.overtimeMinutes > 0 ? ` · 加班${Math.round(item.overtimeMinutes / 60 * 10) / 10}h` : ''}`, start: `${item.date}T${item.start || '00:00'}`, end: item.end ? `${item.date}T${item.end}` : undefined, editable: false, classNames: ['overlay-worktime'], extendedProps: { type: 'worktime', date: item.date } }); for (const item of response.value.ledger || []) events.push({ title: `收 ${item.income} / 支 ${item.expense}`, start: item.date, allDay: true, editable: false, classNames: ['overlay-ledger'], extendedProps: { type: 'ledger', date: item.date } }); for (const item of response.value.holidays || []) events.push({ title: `${item.off ? '休' : '班'} · ${item.name}`, start: item.date, allDay: true, editable: false, classNames: ['overlay-holiday'], extendedProps: { type: 'holiday', date: item.date } }); for (const item of response.value.habits || []) events.push({ title: `习惯 ${item.completed}/${item.total}`, start: item.date, allDay: true, editable: false, classNames: ['overlay-habit'], extendedProps: { type: 'habit', date: item.date } }); return events.filter(item => item.start) }
const lunarMap = computed(() => new Map((response.value.lunar || []).map(item => [item.date, item])))
const rangeOptions = computed(() => view.value === 'week' ? [3, 5, 7] : [2, 4, 6])
const calendarOptions = computed(() => ({ plugins: [dayGridPlugin, timeGridPlugin, interactionPlugin], initialView: calendarView(view.value), headerToolbar: false, locale: zhCnLocale, firstDay: 1, height: 'auto', editable: window.innerWidth > 1024, eventResizableFromStart: true, fixedWeekCount: rangeSize.value === 6, showNonCurrentDates: true, views: { timeGridRange: { type: 'timeGrid', duration: { days: rangeSize.value }, dateIncrement: { days: rangeSize.value } }, dayGridRange: { type: 'dayGrid', duration: { weeks: 2 }, dateIncrement: { weeks: 2 } } }, dayMaxEvents: 3, nowIndicator: true, events: eventSource(), datesSet(info) { anchor.value = info.view.currentStart; if (!loading.value) load() }, dayCellContent(info) { const lunar = lunarMap.value.get(dateOnly(info.date)); return { html: `<span>${info.dayNumberText}</span>${lunar ? `<small>${lunar.festival || lunar.solarTerm || lunar.lunarDate}</small>` : ''}` } }, eventDrop: persistEventChange, eventResize: persistEventChange, eventClick: handleEventClick }))
function calendarView(value) { return value === 'day' ? 'timeGridDay' : value === 'week' ? 'timeGridRange' : rangeSize.value >= 4 ? 'dayGridMonth' : 'dayGridRange' }
async function restorePreferencesAndView() {
  readPreferences()
  await nextTick()
  if (view.value === 'year') return load()
  const api = calendarRef.value?.getApi()
  const restoredView = calendarView(view.value)
  if (api && api.view.type !== restoredView) api.changeView(restoredView, anchor.value)
  else await load()
}
async function setView(next) { view.value = next; savePreferences(); if (next !== 'year') await nextTick(() => { calendarRef.value?.getApi().changeView(calendarView(next)); load() }); else await load() }
async function setRangeSize(size) { rangeSize.value = size; savePreferences(); await nextTick(); calendarRef.value?.getApi().changeView(calendarView(view.value)); await load() }
function navigate(direction) { if (view.value === 'year') { const delta = direction === 'prev' ? -1 : direction === 'next' ? 1 : 0; anchor.value = direction === 'today' ? new Date() : new Date(anchor.value.getFullYear() + delta, 0, 1); load(); return } const api = calendarRef.value?.getApi(); if (!api) return; api[direction](); anchor.value = api.getDate() }
function localTask(calendarTask) { return { ...(tasksStore.tasks.find(item => item.publicId === calendarTask.publicId) || {}), ...calendarTask } }
function localTaskProjection() {
  const { from, to } = range()
  return tasksStore.tasks.filter(task => {
    if (task.deleted) return false
    const value = task.startAt || task.dueAt
    if (!value) return false
    const date = dateOnly(new Date(value))
    return date >= from && date < to
  }).map(task => ({ publicId: task.publicId, title: task.title, status: task.status, priority: task.priority,
    startAt: task.startAt, dueAt: task.dueAt, allDay: task.allDay, timezone: task.timezone,
    durationMinutes: task.durationMinutes, revision: task.revision }))
}
async function refreshTasks() {
  if (tasksStore.online) { await tasksStore.syncNow(); await load() }
  else response.value = { ...response.value, tasks: localTaskProjection() }
}
function openTask(task = null, date = '') { selectedTask.value = task ? localTask(task) : null; editorError.value = ''; Object.assign(taskForm, { title: selectedTask.value?.title || '', priority: selectedTask.value?.priority || 'NONE', startAt: localInput(selectedTask.value?.startAt), dueAt: selectedTask.value?.dueAt ? localInput(selectedTask.value.dueAt) : date ? `${date}T09:00` : '', allDay: Boolean(selectedTask.value?.allDay) }); editorOpen.value = true }
async function saveTask() { saving.value = true; editorError.value = ''; try { const payload = { ...(selectedTask.value || {}), title: taskForm.title.trim(), priority: taskForm.priority, startAt: instant(taskForm.startAt), dueAt: instant(taskForm.dueAt), allDay: taskForm.allDay, timezone: selectedTask.value?.timezone || Intl.DateTimeFormat().resolvedOptions().timeZone || 'Asia/Shanghai' }; if (selectedTask.value) await tasksStore.update(selectedTask.value, payload); else await tasksStore.create({ ...payload, listId: tasksStore.inbox?.publicId || '' }); editorOpen.value = false; await refreshTasks() } catch (cause) { editorError.value = cause?.message || '任务保存失败' } finally { saving.value = false } }
async function deleteTask() { if (!selectedTask.value || !window.confirm(`删除任务“${selectedTask.value.title}”？`)) return; saving.value = true; try { await tasksStore.remove(selectedTask.value); editorOpen.value = false; await refreshTasks() } catch (cause) { editorError.value = cause?.message || '任务删除失败' } finally { saving.value = false } }
async function persistEventChange(change) { const task = localTask(change.event.extendedProps.task); try { await tasksStore.update(task, { startAt: change.event.start?.toISOString() || '', dueAt: change.event.end?.toISOString() || change.event.start?.toISOString() || '', allDay: change.event.allDay, durationMinutes: change.event.end && change.event.start ? Math.round((change.event.end - change.event.start) / 60000) : task.durationMinutes }); await refreshTasks() } catch (cause) { change.revert(); error.value = cause?.message || '任务改期失败，已恢复原时间' } }
function handleEventClick(info) { const data = info.event.extendedProps; if (data.type === 'task') openTask(data.task); else if (data.type === 'worktime') router.push({ path: '/punch', query: { date: data.date } }); else if (data.type === 'ledger') router.push({ path: '/ledger/transactions', query: { from: data.date, to: data.date } }) }
function contextDate(target) { const cell = target.closest('[data-date]'); if (cell?.dataset.date) return cell.dataset.date.slice(0, 10); return dateOnly(calendarRef.value?.getApi().getDate() || anchor.value) }
function openDateMenu(event) { const date = contextDate(event.target); if (!date) return; event.preventDefault(); event.stopPropagation(); dateMenu.date = date; dateMenu.x = Math.min(event.clientX, window.innerWidth - 210); dateMenu.y = Math.min(event.clientY, window.innerHeight - (aggregateMode.value ? 230 : 120)); dateMenu.open = true }
function closeDateMenu() { dateMenu.open = false }
function createForDate(type) { const date = dateMenu.date; closeDateMenu(); if (type === 'task') openTask(null, date); else if (type === 'ledger') router.push({ path: '/ledger/transactions', query: { action: 'create', date, from: date, to: date } }); else if (type === 'worktime') router.push({ path: '/punch', query: { date } }); else router.push({ path: '/tasks/focus', query: { date } }) }
function formatMenuDate(value) { return value ? new Intl.DateTimeFormat('zh-CN', { month: 'long', day: 'numeric', weekday: 'short' }).format(new Date(`${value}T00:00:00`)) : '' }
const year = computed(() => anchor.value.getFullYear())
const yearMonths = computed(() => Array.from({ length: 12 }, (_, index) => { const count = response.value.tasks.filter(task => { const date = new Date(task.startAt || task.dueAt); return date.getFullYear() === year.value && date.getMonth() === index }).length; return { month: index + 1, count, density: Math.min(1, count / 12) } }))
async function openMonth(month) { anchor.value = new Date(year.value, month - 1, 1); view.value = 'month'; rangeSize.value = 6; savePreferences(); await nextTick(); const api = calendarRef.value?.getApi(); api?.changeView('dayGridMonth', anchor.value); await load() }
function handleEscape(event) { if (event.key === 'Escape') closeDateMenu() }
watch(() => appStore.authUser?.id, () => restorePreferencesAndView())
onMounted(async () => { await tasksStore.init({ waitForRemote: false }); await restorePreferencesAndView(); window.addEventListener('keydown', handleEscape) })
onBeforeUnmount(() => window.removeEventListener('keydown', handleEscape))
</script>

<style scoped>
.task-calendar-page{max-width:1500px;margin:0 auto;padding:34px 36px 90px;color:var(--ink)}.calendar-head,.calendar-toolbar{display:flex;align-items:center;justify-content:space-between;gap:20px}.calendar-head p{margin:0 0 4px;color:var(--muted);font-size:.75rem;font-weight:700}.calendar-head h1{margin:0;font-size:2rem;letter-spacing:0}.calendar-actions,.view-switch,.range-switch{display:flex;border:1px solid var(--line);background:var(--panel)}.calendar-actions button,.view-switch button,.range-switch button{min-height:38px;border:0;border-right:1px solid var(--line);background:transparent;color:var(--ink);padding:0 13px}.calendar-actions button:last-child,.view-switch button:last-child,.range-switch button:last-child{border-right:0}.calendar-actions svg{width:17px}.view-switch button.on,.range-switch button.on{background:var(--accent);color:#fff;box-shadow:inset 0 0 0 1px color-mix(in srgb,var(--accent) 72%,#000)}:global(html.dark) .view-switch button.on,:global(html.dark) .range-switch button.on{color:#132019}.calendar-toolbar{margin:24px 0 18px;padding:13px 0;border-top:1px solid var(--line);border-bottom:1px solid var(--line)}.layer-switch{display:flex;flex-wrap:wrap;gap:14px;border:0;padding:0;margin:0}.layer-switch legend{position:absolute;width:1px;height:1px;overflow:hidden}.layer-switch label{display:flex;align-items:center;gap:6px;font-size:.8125rem}.layer-switch span{color:var(--down);font-size:.6875rem}.calendar-error{padding:10px 12px;border-left:3px solid var(--down);background:color-mix(in srgb,var(--down) 8%,transparent)}.calendar-loading{padding:12px 0;color:var(--muted)}.full-calendar-wrap{min-height:620px}.full-calendar-wrap :deep(.fc){--fc-border-color:var(--line);--fc-page-bg-color:transparent;--fc-neutral-bg-color:var(--panel2);--fc-list-event-hover-bg-color:var(--panel2);font-size:.8125rem}.full-calendar-wrap :deep(.fc-theme-standard td),.full-calendar-wrap :deep(.fc-theme-standard th){border-color:var(--line)}.full-calendar-wrap :deep(.fc-col-header-cell-cushion){padding:10px 4px;color:var(--muted)}.full-calendar-wrap :deep(.fc-daygrid-day-number){display:flex;justify-content:space-between;width:100%;padding:7px}.full-calendar-wrap :deep(.fc-daygrid-day-number small){color:var(--muted);font-size:.65rem}.full-calendar-wrap :deep(.fc-day-other){background:color-mix(in srgb,var(--panel2) 62%,transparent)}.full-calendar-wrap :deep(.fc-day-other .fc-daygrid-day-top),.full-calendar-wrap :deep(.fc-day-other .fc-daygrid-day-events){opacity:.42}.full-calendar-wrap :deep(.fc-event){border-radius:2px;border:0;padding:2px 4px;cursor:pointer}.full-calendar-wrap :deep(.overlay-worktime){background:#2f746f}.full-calendar-wrap :deep(.overlay-ledger){background:#667049}.full-calendar-wrap :deep(.overlay-holiday){background:#a45449;cursor:default}.full-calendar-wrap :deep(.priority-high){background:var(--down)}.full-calendar-wrap :deep(.priority-medium){background:var(--warn);color:#1f2321}.year-grid{display:grid;grid-template-columns:repeat(4,minmax(0,1fr));gap:1px;background:var(--line);border:1px solid var(--line)}.year-month{position:relative;display:grid;grid-template-columns:1fr auto;gap:5px;text-align:left;min-height:150px;padding:18px;border:0;background:var(--panel);color:var(--ink);overflow:hidden}.year-month>span{font-weight:700}.year-month>b{font-size:1.75rem}.year-month small{color:var(--muted)}.year-month i{position:absolute;left:0;right:0;bottom:0;height:calc(10px + var(--density) * 58px);background:color-mix(in srgb,#2f746f calc(18% + var(--density) * 60%),transparent)}.calendar-context-menu{position:fixed;z-index:80;width:190px;border:1px solid var(--line2);background:var(--panel);box-shadow:0 14px 36px #0003}.calendar-context-menu header{display:flex;align-items:center;justify-content:space-between;padding:10px 12px;border-bottom:1px solid var(--line);font-size:.75rem}.calendar-context-menu header button{display:grid;place-items:center;width:26px;height:26px;border:0;background:transparent;color:var(--muted)}.calendar-context-menu svg{width:16px;height:16px}.calendar-context-menu>button{display:flex;align-items:center;gap:10px;width:100%;min-height:40px;padding:0 12px;border:0;background:transparent;color:var(--ink);text-align:left}.calendar-context-menu>button:hover{background:var(--accent-soft);color:var(--accent)}.calendar-task-form{display:grid;gap:16px}.calendar-task-form>label,.calendar-form-grid>label{display:grid;gap:7px;color:var(--ink2);font-size:.75rem;font-weight:700}.calendar-form-grid{display:grid;grid-template-columns:1fr 1fr;gap:12px}.calendar-task-form select{min-height:40px;padding:8px 10px;border:1px solid var(--line2);background:var(--card);color:var(--ink)}.calendar-check{display:flex;align-items:center;min-height:40px;gap:8px;font-weight:500}
@media(max-width:1023px){.task-calendar-page{padding:22px 14px 110px}.calendar-head{align-items:flex-end}.calendar-toolbar{align-items:flex-start;flex-direction:column}.view-switch,.range-switch{width:100%}.view-switch button,.range-switch button{flex:1}.year-grid{grid-template-columns:repeat(2,minmax(0,1fr))}.year-month{min-height:112px}.full-calendar-wrap{min-height:540px}.full-calendar-wrap :deep(.fc-event-title){white-space:normal}.calendar-actions button{padding:0 10px}}
@media(max-width:420px){.calendar-head h1{font-size:1.65rem}.calendar-actions button:nth-child(2){padding:0 8px}.year-month{padding:12px}.layer-switch{gap:10px}.full-calendar-wrap :deep(.fc-timegrid-axis){display:none}.calendar-form-grid{grid-template-columns:1fr}}
.full-calendar-wrap :deep(.overlay-habit){background:#6a5680;cursor:default}
</style>
