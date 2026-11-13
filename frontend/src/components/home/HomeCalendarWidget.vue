<template>
  <HomeWidgetFrame title="日历" kicker="CALENDAR" :meta="metaLabel" :editing="editing">
    <template #actions>
      <div class="hw-seg" role="tablist" aria-label="日历视图">
        <button
          v-for="key in HOME_CALENDAR_VIEWS"
          :key="key"
          type="button"
          role="tab"
          :class="{ active: view === key }"
          :aria-selected="view === key"
          @click="setView(key)"
        >{{ HOME_CALENDAR_VIEW_LABELS[key] }}</button>
      </div>
      <router-link class="hc-open" to="/calendar" title="打开完整日历" aria-label="打开完整日历"><CalendarDays :size="14" /></router-link>
    </template>

    <!-- 当天明细：点日期后就在卡片里展开，不跳页也不丢上下文 -->
    <div v-if="selectedDay" class="hc-view">
      <div class="hc-bar">
        <button class="hc-back" type="button" @click="selectedKey = ''"><ChevronLeft :size="13" />返回</button>
        <b class="hc-range">{{ selectedDay.date.getMonth() + 1 }}/{{ selectedDay.date.getDate() }} {{ selectedDay.weekday }}<em v-if="selectedDay.today" class="hc-today-tag">今天</em></b>
        <span class="hc-count">{{ selectedDay.count }} 项</span>
      </div>
      <div class="hc-day">
        <template v-if="selectedDay.count">
          <template v-for="task in selectedDay.tasks" :key="`task-${task.id}`">
            <div class="hc-line" :class="`is-${task.tone}`">
              <button
                class="hc-line-main"
                type="button"
                :title="`编辑「${task.title}」`"
                :aria-expanded="editingTaskId === task.id"
                @click="toggleTask(task)"
              >
                <span class="hc-tag is-task">任务</span>
                <span class="hc-time">{{ task.time }}</span>
                <span class="hc-label">{{ task.title }}</span>
              </button>
              <router-link
                class="hc-go"
                :to="{ path: '/tasks/all', query: { task: task.id } }"
                :aria-label="`在任务页打开 ${task.title}`"
                title="在任务页打开"
              ><ChevronRight :size="12" /></router-link>
            </div>
            <div v-if="editingTaskId === task.id && task.source" class="hc-editor">
              <HomeTaskEditor :task="task.source" @close="editingTaskId = ''" />
            </div>
          </template>
          <button
            v-for="flow in selectedDay.flows"
            :key="`flow-${flow.id}`"
            class="hc-line hc-line-flow"
            :class="`is-${flow.tone}`"
            type="button"
            :title="`编辑「${flow.title}」`"
            @click="openTransaction(flow)"
          >
            <span class="hc-tag is-flow">流水</span>
            <span class="hc-time"></span>
            <span class="hc-label">{{ flow.title }}</span>
            <b class="hc-amount">{{ flow.incoming ? '+' : '−' }}¥{{ money(flow.amount) }}</b>
          </button>
          <router-link
            v-if="selectedDay.work"
            class="hc-line is-work"
            :to="{ path: '/punch', query: { date: selectedDay.key } }"
            :title="`${selectedDay.work.minutes} 分钟`"
          >
            <span class="hc-tag is-work">工时</span>
            <span class="hc-time"></span>
            <span class="hc-label">{{ selectedDay.work.start || '—' }}–{{ selectedDay.work.end || '—' }}</span>
            <b class="hc-amount">{{ (selectedDay.work.minutes / 60).toFixed(1) }}h</b>
          </router-link>
        </template>
        <p v-else class="hc-empty">这天没有安排</p>
      </div>
      <router-link class="hw-btn hc-day-open" to="/calendar"><CalendarDays :size="12" />在完整日历中查看</router-link>
    </div>

    <div v-else class="hc-view">
      <div class="hc-bar">
        <div class="hc-nav-group">
          <button class="hc-nav" type="button" :disabled="view === 'agenda'" :aria-label="`上一${unitLabel}`" :title="`上一${unitLabel}`" @click="shift('prev')"><ChevronLeft :size="13" /></button>
          <button class="hc-nav" type="button" :disabled="view === 'agenda'" :aria-label="`下一${unitLabel}`" :title="`下一${unitLabel}`" @click="shift('next')"><ChevronRight :size="13" /></button>
        </div>
        <b class="hc-range">{{ rangeLabel }}</b>
        <button class="hc-today" type="button" :disabled="atToday" @click="goToday">今天</button>
      </div>

      <div v-if="view === 'month'" class="hc-month">
        <div class="hc-week"><span v-for="label in WEEKDAY_LABELS" :key="label">{{ label }}</span></div>
        <div class="hc-grid">
          <button
            v-for="cell in flatMonth"
            :key="cell.key"
            class="hc-cell"
            type="button"
            :class="{ 'is-today': cell.today, 'is-off': cell.off, 'is-out': !cell.inMonth }"
            :title="cellTitle(cell)"
            @click="selectedKey = cell.key"
          >
            <b>{{ cell.day }}</b>
            <span v-if="cell.holidayLabel" class="hc-holiday">{{ cell.holidayLabel }}</span>
            <span v-if="cell.count" class="hc-dots">
              <i v-if="cell.taskCount" class="hc-dot is-task"></i>
              <i v-if="cell.flowCount" class="hc-dot is-flow"></i>
              <i v-if="cell.hasWork" class="hc-dot is-work"></i>
            </span>
          </button>
        </div>
      </div>

      <ol v-else-if="view === 'week'" class="hc-weeklist">
        <li v-for="day in weekView" :key="day.key">
          <button
            class="hc-wrow"
            type="button"
            :class="{ 'is-today': day.today, 'is-off': day.off, 'is-empty': !day.count }"
            :title="cellTitle(day)"
            @click="selectedKey = day.key"
          >
            <span class="hc-wdate"><b>{{ day.day }}</b><small>{{ day.weekday.slice(1) }}</small></span>
            <span class="hc-witems">
              <span
                v-for="row in day.preview.rows"
                :key="row.id"
                class="hc-witem"
                :class="`is-${row.tone}`"
                :title="row.label"
              ><span v-if="row.time && row.time !== '全天'" class="hc-wtime">{{ row.time }}</span>{{ row.label }}</span>
              <span v-if="day.preview.more" class="hc-wmore">+{{ day.preview.more }}</span>
              <span v-if="!day.count" class="hc-wempty">没有安排</span>
            </span>
          </button>
        </li>
      </ol>

      <div v-else class="hc-agenda">
        <section v-for="day in agendaView" :key="day.key" class="hc-agroup">
          <button class="hc-ahead" type="button" :title="cellTitle(day)" @click="selectedKey = day.key">
            <b>{{ day.date.getMonth() + 1 }}/{{ day.date.getDate() }}</b>
            <small>{{ day.weekday }}</small>
            <em v-if="day.today" class="hc-today-tag">今天</em>
            <span class="hc-spacer"></span>
            <span class="hc-acount">{{ day.count }} 项</span>
          </button>
          <ul>
            <li v-for="row in day.preview.rows" :key="row.id" :class="`is-${row.tone}`" :title="row.label">
              <i class="hc-dot" :class="`is-${row.tone}`"></i>
              <span class="hc-alabel">{{ row.label }}</span>
              <span v-if="row.time && row.time !== '全天'" class="hc-wtime">{{ row.time }}</span>
            </li>
            <li v-if="day.preview.more" class="is-more" @click="selectedKey = day.key">还有 {{ day.preview.more }} 项</li>
          </ul>
        </section>
        <p v-if="!agendaView.length" class="hc-empty">接下来 {{ AGENDA_DAYS }} 天没有安排</p>
      </div>
    </div>

    <template #footer>
      <span class="hc-legend"><i class="hc-dot is-task" />任务 <i class="hc-dot is-flow" />流水 <i class="hc-dot is-work" />工时</span>
      <router-link class="hc-more" to="/calendar">完整日历 →</router-link>
    </template>

    <HomeTransactionEditor v-model:open="flowEditorOpen" :transaction="editingFlow" @saved="editingFlow = null" />
  </HomeWidgetFrame>
</template>

<script setup>
import { computed, ref, watch } from 'vue'
import { CalendarDays, ChevronLeft, ChevronRight } from 'lucide-vue-next'
import HomeWidgetFrame from './HomeWidgetFrame.vue'
import HomeTaskEditor from './HomeTaskEditor.vue'
import HomeTransactionEditor from './HomeTransactionEditor.vue'
import { compactMoney, dateKeyOf } from './homeMetrics.js'
import {
  AGENDA_DAYS, HOME_CALENDAR_VIEWS, HOME_CALENDAR_VIEW_LABELS, agendaGroups, buildDayIndex,
  calendarViewStorageKey, dayDetail, dayPreview, monthCells, monthProgress, rangeStats,
  rangeTitle, readCalendarView, weekDays, writeCalendarView
} from './homeCalendarModel.js'
import { WEEKDAY_LABELS, fromDateKey, shiftAnchor, toDateKey } from '../../views/calendar/calendarRange.js'
import { useAppStore } from '../../stores/app.js'
import { useTasksStore } from '../../stores/tasks.js'
import { useLedgerStore } from '../../stores/ledger.js'
import { useWorktimeStore } from '../../stores/worktime.js'

defineProps({ editing: Boolean })

const store = useAppStore()
const tasksStore = useTasksStore()
const ledgerStore = useLedgerStore()
const worktimeStore = useWorktimeStore()

const todayKey = dateKeyOf(new Date())
const storageKey = calendarViewStorageKey(store.accountScope)
const view = ref(readCalendarView(storageKey))
const anchorKey = ref(todayKey)
const selectedKey = ref('')
const editingTaskId = ref('')
const flowEditorOpen = ref(false)
const editingFlow = ref(null)

const holidays = computed(() => store.holidays || {})
const options = computed(() => ({ todayKey, holidays: holidays.value }))

/** 三个数据源摊平成「日期 -> 当天有什么」，切月/切周都复用同一份索引 */
const dayIndex = computed(() => buildDayIndex({
  tasks: tasksStore.tasks,
  transactions: ledgerStore.transactions,
  records: worktimeStore.records,
  settings: worktimeStore.settings
}))

const monthRows = computed(() => monthCells(anchorKey.value, dayIndex.value, options.value))
const flatMonth = computed(() => monthRows.value.flat())
const weekRows = computed(() => weekDays(anchorKey.value, dayIndex.value, options.value))
const agendaRows = computed(() => agendaGroups(dayIndex.value, todayKey, AGENDA_DAYS, options.value))

const weekView = computed(() => weekRows.value.map(day => ({ ...day, preview: dayPreview(day, 1) })))
const agendaView = computed(() => agendaRows.value.map(day => ({ ...day, preview: dayPreview(day, 3) })))
const selectedDay = computed(() => selectedKey.value ? dayDetail(dayIndex.value, selectedKey.value, todayKey) : null)

const unitLabel = computed(() => view.value === 'week' ? '周' : '月')
const rangeLabel = computed(() => rangeTitle(view.value, view.value === 'agenda' ? todayKey : anchorKey.value, AGENDA_DAYS))

const stats = computed(() => rangeStats(
  view.value === 'month' ? flatMonth.value : view.value === 'week' ? weekRows.value : agendaRows.value
))

const metaLabel = computed(() => {
  if (selectedDay.value) return itemsOfDay(selectedDay.value) || '这天没有安排'
  if (view.value === 'month') return `${stats.value.busy} 天有安排 · ${monthProgress(anchorKey.value, todayKey)}% 已过`
  if (view.value === 'week') return `本周 ${stats.value.busy}/${stats.value.days} 天有安排 · ${stats.value.total} 项`
  return `接下来 ${AGENDA_DAYS} 天 · ${stats.value.busy} 天有事`
})

const atToday = computed(() => {
  if (view.value === 'agenda') return true
  if (view.value === 'week') return weekRows.value.some(day => day.key === todayKey)
  return anchorKey.value.slice(0, 7) === todayKey.slice(0, 7)
})

function money(value) {
  return compactMoney(value)
}

/** 明细行就地编辑：任务用首页共享的任务编辑器，流水用账本表单，都不跳页 */
function toggleTask(task) {
  editingTaskId.value = editingTaskId.value === task.id ? '' : task.id
}

function openTransaction(flow) {
  if (!flow.source) return
  editingFlow.value = flow.source
  flowEditorOpen.value = true
}

function setView(key) {
  if (!HOME_CALENDAR_VIEWS.includes(key)) return
  view.value = key
  selectedKey.value = ''
  anchorKey.value = todayKey
  writeCalendarView(storageKey, key)
}

function shift(direction) {
  if (view.value === 'agenda') return
  anchorKey.value = toDateKey(shiftAnchor(fromDateKey(anchorKey.value), { view: view.value, direction }))
}

function goToday() {
  anchorKey.value = todayKey
  selectedKey.value = ''
}

function itemsLabel(cell) {
  const parts = []
  if (cell.taskCount) parts.push(`${cell.taskCount} 个任务`)
  if (cell.flowCount) parts.push(`${cell.flowCount} 笔流水`)
  if (cell.hasWork) parts.push('有工时记录')
  return parts.join(' · ')
}

/** 明细视图里的一天用的是数组字段，单独拼一次，避免和格子复用两套结构 */
function itemsOfDay(day) {
  const parts = []
  if (day?.tasks.length) parts.push(`${day.tasks.length} 个任务`)
  if (day?.flows.length) parts.push(`${day.flows.length} 笔流水`)
  if (day?.work) parts.push('有工时记录')
  return parts.join(' · ')
}

function cellTitle(cell) {
  return [cell.key.replaceAll('-', '/'), cell.holidayName, itemsLabel(cell) || '没有安排'].filter(Boolean).join(' · ')
}

// 切账号时换成那个账号上次选的视图，避免沿用别人的偏好
watch(() => store.accountScope, () => {
  view.value = readCalendarView(calendarViewStorageKey(store.accountScope))
  anchorKey.value = todayKey
  selectedKey.value = ''
})

// 换一天就收起任务编辑器，否则会把上一天的任务面板带到下一天
watch(selectedKey, () => { editingTaskId.value = '' })
</script>

<style scoped>
.hc-view { display: flex; height: 100%; min-height: 0; flex-direction: column; gap: 6px; }
.hc-bar { display: grid; flex: none; grid-template-columns: auto minmax(0, 1fr) auto; align-items: center; gap: 6px; }
.hc-nav-group { display: flex; gap: 2px; }
.hc-nav, .hc-back {
  display: inline-flex; min-width: 0; min-height: 0; height: 22px; align-items: center; gap: 1px;
  padding: 0 4px; border: 1px solid transparent; border-radius: 7px; background: transparent;
  color: var(--muted); font-size: 10.5px; font-weight: 650;
}
.hc-back { padding: 0 6px 0 2px; }
.hc-nav:hover:not(:disabled), .hc-back:hover { border-color: var(--line2); background: var(--card); color: var(--ink); }
.hc-nav:disabled { opacity: .35; cursor: default; }
.hc-range {
  overflow: hidden; color: var(--ink2); font-size: 11.5px; font-weight: 700;
  letter-spacing: -.01em; text-align: center; text-overflow: ellipsis; white-space: nowrap;
}
.hc-today-tag {
  margin-left: 5px; padding: 1px 5px; border-radius: 999px;
  background: color-mix(in srgb, var(--accent) 16%, transparent); color: var(--accent);
  font-size: 9px; font-style: normal; font-weight: 700; vertical-align: 1px;
}
.hc-today {
  height: 22px; min-width: 0; min-height: 0; padding: 0 8px; border: 1px solid var(--line2);
  border-radius: 7px; background: var(--card); color: var(--ink2); font-size: 10.5px; font-weight: 650;
}
.hc-today:hover:not(:disabled) { border-color: var(--accent); color: var(--accent); }
.hc-today:disabled { border-color: transparent; background: transparent; color: var(--muted); opacity: .55; cursor: default; }
.hc-count, .hc-acount { color: var(--muted); font-size: 10px; font-variant-numeric: tabular-nums; }
.hc-spacer { flex: 1; }
.hc-open {
  display: grid; width: 28px; min-width: 0; height: 28px; min-height: 0; place-items: center;
  border: 1px solid var(--line2); border-radius: 9px; background: var(--card); color: var(--ink2);
}
.hc-open:hover { border-color: var(--accent); color: var(--accent); }

/* 月视图 */
.hc-month { display: flex; min-height: 0; flex: 1; flex-direction: column; gap: 2px; }
.hc-week, .hc-grid { display: grid; grid-template-columns: repeat(7, minmax(0, 1fr)); gap: 2px; }
.hc-week { flex: none; }
.hc-week span { padding-bottom: 2px; color: var(--muted); font-size: 9.5px; text-align: center; }
.hc-grid { flex: 1; grid-auto-rows: minmax(0, 1fr); }
.hc-cell {
  position: relative; display: flex; min-width: 0; min-height: 0; flex-direction: column;
  align-items: center; justify-content: center; gap: 1px; padding: 0;
  border: 0; border-radius: 9px; background: transparent; color: var(--ink2);
  font-variant-numeric: tabular-nums;
}
.hc-cell:hover { background: var(--paper); }
.hc-cell b { position: relative; z-index: 1; display: grid; width: 24px; height: 24px; place-items: center; font-size: 12px; font-weight: 620; line-height: 1; }
.hc-cell.is-off { color: var(--muted); }
.hc-cell.is-out { opacity: .34; }
.hc-cell.is-today b { border-radius: 50%; background: var(--accent); color: var(--paper); font-weight: 780; }
.hc-cell.is-today { color: var(--accent); }
.hc-holiday { position: absolute; top: 0; right: 3px; color: var(--muted); font-size: 8px; line-height: 1.2; }
.hc-cell.is-today .hc-holiday { color: var(--accent); font-weight: 700; }
.hc-dots { display: flex; height: 4px; align-items: center; gap: 2px; }
.hc-dot { display: inline-block; width: 4px; height: 4px; flex: none; border-radius: 50%; background: var(--muted); }
.hc-dot.is-task { background: #4d78db; }
.hc-dot.is-flow { background: var(--down); }
.hc-dot.is-work { background: #2f9e44; }
.hc-dot.is-in { background: var(--down); }
.hc-dot.is-out { background: var(--up); }
.hc-dot.is-done { background: var(--line2); }
.hc-dot.is-work.is-work { background: #2f9e44; }

/* 周视图 */
.hc-weeklist { display: flex; min-height: 0; flex: 1; flex-direction: column; gap: 1px; margin: 0; padding: 0; list-style: none; }
.hc-weeklist li { display: flex; min-height: 0; flex: 1; }
.hc-wrow {
  display: grid; width: 100%; min-width: 0; min-height: 0; flex: 1; align-items: center; gap: 8px;
  grid-template-columns: 34px minmax(0, 1fr); padding: 0 6px;
  border: 0; border-radius: 9px; background: transparent; text-align: left;
}
.hc-wrow:hover { background: var(--paper); }
.hc-wrow.is-today { background: color-mix(in srgb, var(--accent) 8%, transparent); }
.hc-wdate { display: flex; align-items: baseline; gap: 3px; color: var(--ink2); font-variant-numeric: tabular-nums; }
.hc-wdate b { display: grid; width: 22px; height: 22px; place-items: center; border-radius: 50%; font-size: 12px; font-weight: 650; }
.hc-wdate small { color: var(--muted); font-size: 9px; }
.hc-wrow.is-today .hc-wdate b { background: var(--accent); color: var(--paper); font-weight: 780; }
.hc-wrow.is-today .hc-wdate small { color: var(--accent); }
.hc-wrow.is-off .hc-wdate { color: var(--muted); }
.hc-witems { display: flex; min-width: 0; align-items: center; gap: 6px; }
.hc-witem, .hc-wmore, .hc-wempty {
  overflow: hidden; color: var(--ink2); font-size: 10.5px; text-overflow: ellipsis; white-space: nowrap;
}
.hc-witem { min-width: 0; flex: 0 1 auto; }
.hc-witem::before { content: ""; display: inline-block; width: 3px; height: 3px; margin-right: 4px; border-radius: 50%; background: #4d78db; vertical-align: 1.5px; }
.hc-witem.is-in::before { background: var(--down); }
.hc-witem.is-out::before { background: var(--up); }
.hc-witem.is-move::before { background: var(--line2); }
.hc-witem.is-work::before { background: #2f9e44; }
.hc-witem.is-done { color: var(--muted); text-decoration: line-through; }
.hc-witem.is-done::before { background: var(--line2); }
.hc-wmore, .hc-wempty { flex: none; color: var(--muted); font-size: 9.5px; }
.hc-wtime { margin-right: 4px; color: var(--muted); font-variant-numeric: tabular-nums; }

/* 日程视图 */
.hc-agenda { display: flex; min-height: 0; flex: 1; flex-direction: column; gap: 9px; overflow-y: auto; padding-right: 2px; }
.hc-agroup { display: flex; flex-direction: column; gap: 2px; }
.hc-ahead {
  display: flex; width: 100%; min-width: 0; min-height: 0; align-items: center; gap: 5px;
  padding: 2px 4px; border: 0; border-radius: 7px; background: transparent; color: var(--ink2); text-align: left;
}
.hc-ahead:hover { background: var(--paper); }
.hc-ahead b { font-size: 11.5px; font-variant-numeric: tabular-nums; }
.hc-ahead small { color: var(--muted); font-size: 9.5px; }
.hc-agroup ul { display: flex; flex-direction: column; gap: 1px; margin: 0; padding: 0 0 0 6px; list-style: none; }
.hc-agroup li { display: flex; min-width: 0; align-items: center; gap: 5px; color: var(--ink2); font-size: 10.5px; }
.hc-agroup li.is-more { padding-left: 8px; color: var(--muted); font-size: 9.5px; cursor: pointer; }
.hc-agroup li.is-more:hover { color: var(--accent); }
.hc-alabel { overflow: hidden; flex: 1; text-overflow: ellipsis; white-space: nowrap; }
.hc-agroup li.is-done .hc-alabel { color: var(--muted); text-decoration: line-through; }
.hc-agroup li.is-move .hc-alabel { color: var(--muted); }

/* 当天明细 */
.hc-day { display: flex; min-height: 0; flex: 1; flex-direction: column; gap: 1px; overflow-y: auto; padding-right: 2px; }
.hc-line {
  display: grid; grid-template-columns: minmax(0, 1fr) auto; align-items: center; gap: 4px;
  padding: 2px 4px 2px 0; border: 0; border-radius: 8px; background: transparent; color: var(--ink2); text-decoration: none;
}
.hc-line:hover { background: var(--paper); }
/* 明细行主体是按钮（点了就地编辑），右侧箭头才是跳页入口 */
.hc-line-main, .hc-line-flow {
  display: grid; width: 100%; min-width: 0; min-height: 0; align-items: center; gap: 6px; padding: 2px 0 2px 6px;
  border: 0; border-radius: 8px; background: transparent; color: inherit; font: inherit; text-align: left;
}
.hc-line-flow { grid-template-columns: auto auto minmax(0, 1fr) auto; }
.hc-line-main { grid-template-columns: auto auto minmax(0, 1fr); }
.hc-line-main:hover .hc-label, .hc-line-flow:hover .hc-label { color: var(--accent); }
.hc-editor { margin: 2px 0 6px; }
.hc-line.is-done .hc-label, .hc-line.is-abandoned .hc-label { color: var(--muted); text-decoration: line-through; }
.hc-line.is-move .hc-label { color: var(--muted); }
.hc-tag { padding: 1px 5px; border-radius: 5px; background: var(--paper); color: var(--muted); font-size: 9px; font-weight: 650; }
.hc-tag.is-task { color: #4d78db; }
.hc-tag.is-flow { color: var(--down); }
.hc-tag.is-work { color: #2f9e44; }
.hc-time { min-width: 28px; color: var(--muted); font-size: 9.5px; font-variant-numeric: tabular-nums; }
.hc-label { overflow: hidden; font-size: 11px; text-overflow: ellipsis; white-space: nowrap; }
.hc-amount { color: var(--ink2); font-size: 10.5px; font-variant-numeric: tabular-nums; }
.hc-go { display: grid; width: 20px; height: 20px; min-width: 0; min-height: 0; place-items: center; color: var(--line2); }
.hc-line:hover .hc-go { color: var(--accent); }
.hc-line.is-in .hc-amount { color: var(--down); }
.hc-line.is-out .hc-amount { color: var(--up); }
.hc-day-open { flex: none; align-self: flex-start; margin-top: 2px; }
.hc-empty { margin: 0; padding: 18px 0; color: var(--muted); font-size: 11px; text-align: center; }

.hc-legend { display: inline-flex; align-items: center; gap: 5px; }
.hc-legend .hc-dot { margin-left: 5px; }
.hc-legend .hc-dot:first-child { margin-left: 0; }
.hc-more { color: var(--accent); font-weight: 650; }

@media (max-width: 900px) {
  .hc-view { min-height: 340px; }
}
</style>
