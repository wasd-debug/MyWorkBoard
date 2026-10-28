<template>
  <section ref="shellEl" class="cal-shell" :class="{ 'is-collapsed': collapsed, 'is-task-route': isTaskRoute }" @click="closeMenus">
    <CalendarSidebar
      v-if="!collapsed"
      :anchor="anchor"
      :today="today"
      :selected="selectedDay"
      :lunar="lunarLabels"
      :holidays="holidayMap"
      :lists="calendarLists"
      :tags="calendarTags"
      :filters="filters"
      :scope="scope"
      @move-month="moveMonth"
      @go-today="goToday"
      @select="jumpToDate"
      @update:scope="applyScope"
    />

    <div class="cal-main">
      <header class="cal-head">
        <div class="cal-head-left">
          <button
            type="button"
            class="cal-icon-btn"
            :aria-label="collapsed ? '展开侧边栏' : '收起侧边栏'"
            :title="collapsed ? '展开侧边栏' : '收起侧边栏'"
            @click.stop="toggleSidebar"
          >
            <PanelLeft :size="16" />
          </button>
          <span class="cal-title"><CalendarDays :size="15" aria-hidden="true" />{{ range.title }}</span>
        </div>
        <div class="cal-head-right">
          <button type="button" class="cal-icon-btn" aria-label="新增任务" title="新增任务" @click.stop="openQuickAdd()"><Plus :size="17" /></button>
          <div class="cal-menu-wrap">
            <button type="button" class="cal-text-btn" :aria-expanded="viewMenu" @click.stop="toggleViewMenu">
              {{ viewLabelText }}<ChevronDown :size="13" aria-hidden="true" />
            </button>
            <div v-if="viewMenu" class="cal-menu" role="menu" @click.stop>
              <button
                v-for="key in VIEW_KEYS"
                :key="key"
                type="button"
                role="menuitemradio"
                :aria-checked="view === key"
                class="cal-menu-row"
                @click="setView(key)"
              >
                <span class="cal-menu-label">{{ VIEW_LABELS[key] }}</span>
                <span class="cal-menu-tail">{{ VIEW_SHORTCUTS[key] }}</span>
              </button>
              <div class="cal-menu-divider"></div>
              <div
                v-for="key in SPAN_KEYS"
                :key="key"
                class="cal-menu-row is-span"
                :class="{ active: view === key }"
                role="menuitemradio"
                :aria-checked="view === key"
                tabindex="0"
                @click="setView(key)"
                @keydown.enter.prevent="setView(key)"
              >
                <span class="cal-menu-label">{{ SPAN_CONFIG[key].label }}</span>
                <span class="cal-span-stepper" @click.stop>
                  <button type="button" :aria-label="`减少${SPAN_CONFIG[key].label}`" :disabled="spans[key] <= SPAN_CONFIG[key].min" @click="changeSpan(key, -1)">−</button>
                  <b>{{ spans[key] }}</b>
                  <button type="button" :aria-label="`增加${SPAN_CONFIG[key].label}`" :disabled="spans[key] >= SPAN_CONFIG[key].max" @click="changeSpan(key, 1)">+</button>
                  <small>{{ SPAN_CONFIG[key].unit }}</small>
                </span>
              </div>
            </div>
          </div>
          <div class="cal-nav">
            <button type="button" aria-label="上一段" title="上一段" @click.stop="navigate('prev')"><ChevronLeft :size="15" /></button>
            <button type="button" class="cal-nav-today" @click.stop="navigate('today')">今天</button>
            <button type="button" aria-label="下一段" title="下一段" @click.stop="navigate('next')"><ChevronRight :size="15" /></button>
          </div>
          <div class="cal-menu-wrap">
            <button type="button" class="cal-icon-btn" aria-label="更多" title="更多" :aria-expanded="moreOpen" @click.stop="moreOpen = !moreOpen"><MoreHorizontal :size="17" /></button>
            <div v-if="moreOpen" class="cal-menu is-more" role="menu" @click.stop>
              <button type="button" role="menuitem" class="cal-menu-row" @click="openSettings"><Settings2 :size="15" /><span class="cal-menu-label">显示设置</span></button>
              <button type="button" role="menuitem" class="cal-menu-row" @click="openPlanPanel"><ListTodo :size="15" /><span class="cal-menu-label">安排任务</span></button>
              <div class="cal-menu-row is-print" role="menuitem" tabindex="0" @click.stop="printMenuOpen = !printMenuOpen" @keydown.enter.prevent="printMenuOpen = !printMenuOpen">
                <Printer :size="15" /><span class="cal-menu-label">打印</span>
                <ChevronDown :size="12" :style="{ transform: printMenuOpen ? 'rotate(180deg)' : 'none' }" />
              </div>
              <div v-if="printMenuOpen" class="cal-menu-sub">
                <button type="button" class="cal-menu-row" @click="printCalendar('compact')">缩略打印</button>
                <button type="button" class="cal-menu-row" @click="printCalendar('detail')">打印详细</button>
              </div>
            </div>
          </div>
        </div>
      </header>

      <p v-if="error" class="cal-error">{{ error }}</p>
      <div v-if="loading" class="cal-loading" role="status" aria-label="正在加载日历数据"></div>

      <div
        ref="bodyEl"
        class="cal-body"
        :class="{ 'is-snap': view === 'multiWeek' }"
        @pointerdown="onSwipeStart"
        @pointerup="onSwipeEnd"
        @pointercancel="resetSwipe"
      >
        <CalendarMonthView
          v-if="view === 'month' || view === 'multiWeek'"
          :weeks="range.weeks"
          :by-day="calendarData.byDay"
          :cell-height="cellHeight"
          :today-key="todayKey"
          :selected-key="selectedKey"
          :month-index="view === 'month' ? anchor.getMonth() : -1"
          :mute-outside-month="view === 'month'"
          :lunar="lunarLabels"
          :holidays="holidayMap"
          @select-day="selectDay"
          @open-event="openEvent"
          @select-range="createSpan"
          @drop-task="dropTask"
        />

        <CalendarYearView
          v-else-if="view === 'year'"
          :months="yearMonths(anchor)"
          :by-day="calendarData.byDay"
          :today-key="todayKey"
          :selected-key="selectedKey"
          @select-day="selectDay"
          @open-month="openMonthFromYear"
        />

        <CalendarTimeGrid
          v-else-if="isTimeGridView"
          :days="range.days"
          :by-day="calendarData.byDay"
          :today-key="todayKey"
          :lunar="lunarLabels"
          :holidays="holidayMap"
          :hidden-top="timePrefs.hiddenTop"
          :hidden-bottom="timePrefs.hiddenBottom"
          :all-day-height="timePrefs.allDayHeight"
          :all-day-collapsed="timePrefs.allDayCollapsed"
          @open-event="openEvent"
          @select-day="selectDay"
          @create-at="createAt"
          @create-allday="createAllday"
          @create-range="createRange"
          @move-block="rescheduleBlock"
          @resize-block="rescheduleBlock"
          @update:hidden-top="updateTimePrefs({ hiddenTop: $event })"
          @update:hidden-bottom="updateTimePrefs({ hiddenBottom: $event })"
          @update:all-day-height="updateTimePrefs({ allDayHeight: $event })"
          @update:all-day-collapsed="updateTimePrefs({ allDayCollapsed: $event })"
          @drop-task="dropTask"
        />

        <CalendarAgendaView
          v-else
          :days="range.days"
          :by-day="calendarData.byDay"
          :today-key="todayKey"
          :lunar="lunarLabels"
          :holidays="holidayMap"
          :more-before="agendaExtra.before < AGENDA_HORIZON"
          :more-after="agendaExtra.after < AGENDA_HORIZON"
          @open-event="openEvent"
          @load-more="loadAgendaMore"
        />
      </div>
    </div>

    <CalendarTaskPanel
      v-if="planPanel.open"
      :tasks="tasksStore.tasks"
      :lists="calendarLists"
      :tags="calendarTags"
      :group-by="planPanel.groupBy"
      :undated="planPanel.undated"
      :overdue="planPanel.overdue"
      :hide-notes="planPanel.hideNotes"
      :hide-hierarchy="planPanel.hideHierarchy"
      @close="planPanel.open = false"
      @update:group-by="updatePlanPanel({ groupBy: $event })"
      @update:undated="updatePlanPanel({ undated: $event })"
      @update:overdue="updatePlanPanel({ overdue: $event })"
      @update:hide-notes="updatePlanPanel({ hideNotes: $event })"
      @update:hide-hierarchy="updatePlanPanel({ hideHierarchy: $event })"
      @schedule="scheduleFromPanel"
    />

    <CalendarEventCard
      v-if="eventCard.open && cardEvent"
      :event="cardEvent"
      :task="cardTask"
      :habit="cardHabit"
      :countdown="cardCountdown"
      :session="cardFocus"
      :lists="calendarLists"
      :tags="calendarTags"
      :tasks="tasksStore.tasks"
      :lunar="lunarLabels"
      :habit-streak="cardStreak"
      :focus-minutes="cardFocusMinutes"
      :style="cardStyle"
      @close="closeEventCard"
      @complete="completeCard"
      @reopen="reopenCard"
      @update="updateCardTask"
      @delete="deleteCard"
      @focus="focusCard"
      @open-detail="openDetail"
      @checkin="checkinCard"
      @undo-checkin="undoCheckinCard"
      @update-focus="updateCardFocus"
      @delete-focus="deleteCardFocus"
    />

    <div v-if="dayPopover.open" class="cal-day-popover" :style="dayPopoverStyle" role="dialog" :aria-label="dayPopoverTitle" @click.stop>
      <header>
        <div>
          <b>{{ dayPopoverTitle }}</b>
          <small v-if="dayPopoverSub">{{ dayPopoverSub }}</small>
        </div>
        <button type="button" aria-label="关闭当日列表" @click="closeDayPopover"><X :size="14" /></button>
      </header>
      <div class="cal-popover-list">
        <button
          v-for="event in dayPopoverEvents"
          :key="`popover-${event.id}`"
          type="button"
          class="cal-row"
          :class="{ 'is-done': event.done, 'is-static': event.kind === 'holiday' }"
          :style="tintStyle(event.color, 14)"
          @click="openEvent(event)"
        >
          <span class="cal-row-dot" :style="{ borderColor: event.color }" :class="{ 'is-done': event.done }"></span>
          <span class="cal-row-title">{{ event.title }}</span>
          <span v-if="event.startLabel" class="cal-row-time">{{ event.startLabel }}</span>
          <span v-else-if="event.openChecklist" class="cal-row-extra">+{{ event.openChecklist }}</span>
        </button>
        <p v-if="!dayPopoverEvents.length" class="cal-popover-empty">当天没有安排</p>
      </div>
      <footer>
        <button type="button" class="cal-popover-day" @click="openDayView">打开日视图</button>
        <button type="button" class="cal-popover-add" @click="addFromPopover"><Plus :size="14" />添加任务</button>
      </footer>
    </div>

    <div v-if="quickAdd.open" class="cal-quick" role="dialog" aria-label="新增任务" @click.stop>
      <header>
        <span class="cal-quick-date"><CalendarDays :size="14" />{{ quickAddDateLabel }}</span>
        <button type="button" class="cal-quick-flag" :class="{ on: quickAdd.priority === 'HIGH' }" :aria-pressed="quickAdd.priority === 'HIGH'" aria-label="设为高优先级" title="高优先级" @click="quickAdd.priority = quickAdd.priority === 'HIGH' ? 'NONE' : 'HIGH'"><Flag :size="14" /></button>
      </header>
      <textarea
        ref="quickInputEl"
        v-model="quickAdd.title"
        class="cal-quick-input"
        rows="3"
        placeholder="准备做什么？"
        @keydown.enter.exact.prevent="saveQuickAdd"
        @keydown.esc.prevent="closeQuickAdd"
      ></textarea>
      <footer>
        <div class="cal-quick-list">
          <FolderClosed :size="14" />
          <select v-model="quickAdd.listId" aria-label="清单">
            <option v-for="list in calendarLists" :key="list.publicId" :value="list.publicId">{{ list.name }}</option>
          </select>
        </div>
        <div class="cal-quick-actions">
          <button type="button" class="cal-quick-cancel" @click="closeQuickAdd">取消</button>
          <button type="button" class="cal-quick-save" :disabled="!quickAdd.title.trim() || quickAdd.saving" @click="saveQuickAdd">{{ quickAdd.saving ? '保存中…' : '保存' }}</button>
        </div>
      </footer>
    </div>

    <CalendarSettingsDialog
      :open="settings.open"
      :lists="calendarLists"
      :habits="tasksStore.habits"
      :countdowns="tasksStore.countdowns"
      :settings="colorSettings"
      :aggregate="aggregateMode"
      :layers="calendarLayers"
      @close="settings.open = false"
      @update:layers="applyLayers"
      @update:settings="applyColorSettings"
      @update-list-color="updateListColor"
      @update-habit-color="updateHabitColor"
      @update-countdown-color="updateCountdownColor"
    />
  </section>
</template>

<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { CalendarDays, ChevronDown, ChevronLeft, ChevronRight, Flag, FolderClosed, ListTodo, MoreHorizontal, PanelLeft, Plus, Printer, Settings2, X } from 'lucide-vue-next'
import CalendarSidebar from './calendar/CalendarSidebar.vue'
import CalendarMonthView from './calendar/CalendarMonthView.vue'
import CalendarTimeGrid from './calendar/CalendarTimeGrid.vue'
import CalendarEventCard from './calendar/CalendarEventCard.vue'
import CalendarYearView from './calendar/CalendarYearView.vue'
import CalendarAgendaView from './calendar/CalendarAgendaView.vue'
import CalendarSettingsDialog from './calendar/CalendarSettingsDialog.vue'
import CalendarTaskPanel from './calendar/CalendarTaskPanel.vue'
import { message } from '../services/message.js'
import { useAppStore } from '../stores/app.js'
import { useTasksStore } from '../stores/tasks.js'
import { useTaskFocusStore } from '../stores/taskFocus.js'
import { apiDeleteFocus, apiGetTaskCalendar, apiListFocusSessions, apiListTaskFilterTasks, apiListTaskFilters, apiUpdateFocusSession } from '../../packages/api-client/src/index.js'
import {
  SPAN_CONFIG, SPAN_KEYS, VIEW_KEYS, VIEW_LABELS, VIEW_SHORTCUTS, WEEKDAY_LABELS,
  addDays, addMonths, clampSpan, formatWeekday, monthWeeks, resolveRange, shiftAnchor, startOfDay, toDateKey, viewLabel as spanViewLabel, yearMonths
} from './calendar/calendarRange.js'
import { buildCalendarEvents } from './calendar/calendarEvents.js'
import { formatLunarLabel } from './calendar/calendarLunar.js'
import { minutesToLabel, snapMinutes } from './calendar/calendarTimeGrid.js'
import { diffDays, fromDateKey } from './calendar/calendarRange.js'
import { formatDuration, spanToInstants } from './calendar/calendarDrag.js'
import { mergedColorSettings, tintStyle } from './calendar/calendarColors.js'

const route = useRoute()
const router = useRouter()
const tasksStore = useTasksStore()
const appStore = useAppStore()

const isTaskRoute = computed(() => route.path.startsWith('/tasks'))
const CAL_ROW_MIN_HEIGHT = 96
// 日程视图按「步进 30 天」向两端增量取数，单侧最多补到半年，避免接口范围过大
const AGENDA_STEP_DAYS = 30
const AGENDA_HORIZON = 180
const today = ref(startOfDay(new Date()))
const todayKey = computed(() => toDateKey(today.value))
const anchor = ref(startOfDay(new Date()))
const selectedDay = ref(startOfDay(new Date()))
const view = ref('month')
const spans = reactive({ multiDay: SPAN_CONFIG.multiDay.defaultSpan, multiWeek: SPAN_CONFIG.multiWeek.defaultSpan })
const colorSettings = reactive(mergedColorSettings())
const scope = ref({ mode: 'all', listIds: [], tagIds: [], filterId: '' })
const filters = ref([])
const holidayMap = ref({})
const lunarLabels = ref({})
const focusSessions = ref([])
const filterTaskIds = ref(new Set())
const worktimeDays = ref([])
const ledgerDays = ref([])
const aggregateMode = computed(() => route.path === '/calendar')
const isTimeGridView = computed(() => ['day', 'week', 'multiDay'].includes(view.value))
const loading = ref(false)
const error = ref('')
const viewMenu = ref(false)
const moreOpen = ref(false)
const printMenuOpen = ref(false)
const settings = reactive({ open: false })
const quickAdd = reactive({ open: false, title: '', priority: 'NONE', listId: '', saving: false, date: startOfDay(new Date()), startMinutes: null, endMinutes: null, spanEnd: null })
const dayPopover = reactive({ open: false, date: startOfDay(new Date()), x: 0, y: 0 })
const eventCard = reactive({ open: false, event: null, x: 0, y: 0 })
const focusStore = useTaskFocusStore()
const quickInputEl = ref(null)
const shellEl = ref(null)
const bodyEl = ref(null)
const widthBucket = value => (value < 768 ? 'phone' : value < 1180 ? 'tablet' : 'wide')
let currentBucket = typeof window !== 'undefined' ? widthBucket(window.innerWidth) : 'wide'
const narrow = ref(currentBucket !== 'wide')
const collapsedManual = ref(null)
const cellHeight = ref(120)
const timePrefs = reactive({ hiddenTop: 420, hiddenBottom: 1380, allDayHeight: 120, allDayCollapsed: false })
const agendaExtra = reactive({ before: 0, after: 0 })
const calendarLayers = reactive({ worktime: true, ledger: true, holiday: true, habit: true })
const planPanel = reactive({ open: false, groupBy: 'list', undated: true, overdue: false, hideNotes: false, hideHierarchy: false })
const collapsed = computed(() => collapsedManual.value ?? narrow.value)

const calendarLists = computed(() => tasksStore.lists.filter(item => !item.archived))
const calendarTags = computed(() => tasksStore.tags)
const range = computed(() => resolveRange({
  view: view.value, span: spans[view.value], anchor: anchor.value,
  agendaBefore: view.value === 'agenda' ? agendaExtra.before : 0,
  agendaAfter: view.value === 'agenda' ? agendaExtra.after : 0
}))
const viewLabelText = computed(() => spanViewLabel(view.value, spans[view.value]))
const selectedKey = computed(() => toDateKey(selectedDay.value))

const scopeFilter = computed(() => {
  const current = scope.value
  if (current.mode === 'filter') return task => filterTaskIds.value.has(task.publicId)
  if (current.mode !== 'custom') return null
  const listIds = new Set(current.listIds || [])
  const tagIds = new Set(current.tagIds || [])
  if (!listIds.size && !tagIds.size) return null
  return task => (listIds.size ? listIds.has(task.listId) : true)
    && (tagIds.size ? (task.tagIds || []).some(id => tagIds.has(id)) : true)
})

const calendarData = computed(() => buildCalendarEvents({
  days: range.value.days,
  tasks: tasksStore.tasks,
  habits: tasksStore.habits,
  habitCheckins: tasksStore.habitCheckins,
  countdowns: tasksStore.countdowns,
  focusSessions: focusSessions.value,
  holidays: Object.values(holidayMap.value),
  worktime: aggregateMode.value ? worktimeDays.value : [],
  ledger: aggregateMode.value ? ledgerDays.value : [],
  lists: tasksStore.lists,
  tags: tasksStore.tags,
  colorSettings,
  taskFilter: scopeFilter.value
}))

const quickAddDateLabel = computed(() => {
  const day = quickAdd.date
  const same = toDateKey(day) === todayKey.value
  const base = `${day.getMonth() + 1}月${day.getDate()}日，${same ? '今天' : formatWeekday(day)}`
  if (quickAdd.spanEnd) return `${day.getMonth() + 1}月${day.getDate()}日 – ${quickAdd.spanEnd.getMonth() + 1}月${quickAdd.spanEnd.getDate()}日，全天`
  if (quickAdd.startMinutes == null) return base
  const from = minutesToLabel(snapMinutes(quickAdd.startMinutes, 15))
  const to = minutesToLabel(snapMinutes(quickAdd.endMinutes ?? quickAdd.startMinutes + 30, 15))
  return `${base}, ${from} – ${to}`
})

const dayPopoverTitle = computed(() => `${dayPopover.date.getMonth() + 1}月${dayPopover.date.getDate()}日 ${formatWeekday(dayPopover.date)}`)
const dayPopoverSub = computed(() => subLabel(dayPopover.date))
const dayPopoverEvents = computed(() => dayEvents(dayPopover.date))
const dayPopoverStyle = computed(() => ({ left: `${dayPopover.x}px`, top: `${dayPopover.y}px` }))

const cardTask = computed(() => {
  const event = eventCard.event
  if (!event || event.kind !== 'task') return null
  const task = tasksStore.tasks.find(item => item.publicId === event.id)
  if (!task) return null
  return { ...task, openChecklistCount: (task.checklist || []).filter(item => !item.completed).length }
})

const cardHabit = computed(() => {
  const event = eventCard.event
  if (!event || event.kind !== 'habit') return null
  return tasksStore.habits.find(item => item.publicId === event.habit?.publicId) || null
})

const cardCountdown = computed(() => {
  const event = eventCard.event
  if (!event || event.kind !== 'countdown') return null
  return tasksStore.countdowns.find(item => item.publicId === event.countdown?.publicId) || null
})

const cardEvent = computed(() => {
  const base = eventCard.event
  if (!base) return null
  if (base.kind === 'task' && cardTask.value) {
    return { ...base, title: cardTask.value.title, done: cardTask.value.status === 'COMPLETED', color: cardTask.value.priority ? base.color : base.color }
  }
  if (base.kind === 'habit' && cardHabit.value) {
    const habit = cardHabit.value
    const done = tasksStore.habitCheckins.some(item => item.habitId === habit.publicId
      && item.date === base.startKey && item.status === 'DONE'
      && Number(item.count || 0) >= Number(habit.targetCount || 1))
    return { ...base, done, title: habit.name || base.title }
  }
  if (base.kind === 'countdown' && cardCountdown.value) return { ...base, title: cardCountdown.value.title || base.title }
  return base
})

// 专注浮层直接从会话列表取实时数据（id 形如 focus:<publicId>:<日期>）
const cardFocus = computed(() => {
  const base = eventCard.event
  if (!base || base.kind !== 'focus') return null
  const publicId = String(base.id || '').split(':')[1] || ''
  return focusSessions.value.find(item => item.publicId === publicId) || base.session || null
})

const cardFocusMinutes = computed(() => {
  const task = cardTask.value
  if (!task) return 0
  const total = focusSessions.value
    .filter(item => item.taskId === task.publicId && item.status !== 'ABORTED')
    .reduce((sum, item) => sum + (Number(item.actualMinutes) || 0), 0)
  return Math.max(total, Number(task.focusMinutes) || 0)
})

const cardStreak = computed(() => {
  const habit = cardHabit.value
  if (!habit) return 0
  const target = Number(habit.targetCount || 1)
  const done = new Set(tasksStore.habitCheckins
    .filter(item => item.habitId === habit.publicId && item.status === 'DONE' && Number(item.count || 0) >= target)
    .map(item => item.date))
  let cursor = startOfDay(new Date())
  if (!done.has(toDateKey(cursor))) cursor = addDays(cursor, -1)
  let streak = 0
  while (done.has(toDateKey(cursor))) {
    streak += 1
    cursor = addDays(cursor, -1)
  }
  return streak
})

const cardStyle = computed(() => ({ left: `${eventCard.x}px`, top: `${eventCard.y}px` }))

function closeEventCard() {
  eventCard.open = false
  eventCard.event = null
}

async function completeCard() {
  if (!cardTask.value) return
  await tasksStore.complete(cardTask.value)
  message.success('已完成')
}

async function reopenCard() {
  if (!cardTask.value) return
  try {
    await tasksStore.reopen(cardTask.value)
    message.success('已恢复')
  } catch (cause) {
    message.error(cause?.message || '无法恢复任务')
  }
}

async function updateCardTask(patch) {
  const task = cardTask.value
  if (!task || !patch) return
  try {
    await tasksStore.update(task, patch)
  } catch (cause) {
    message.error(cause?.response?.data?.detail || cause?.message || '保存失败')
  }
}

async function updateCardFocus(patch) {
  const session = cardFocus.value
  if (!session || !patch) return
  try {
    const next = await apiUpdateFocusSession(session.publicId, patch, session.revision)
    const index = focusSessions.value.findIndex(item => item.publicId === session.publicId)
    if (index >= 0) focusSessions.value.splice(index, 1, next)
    else focusSessions.value = [...focusSessions.value, next]
    message.success('已保存')
  } catch (cause) {
    message.error(cause?.response?.data?.detail || cause?.message || '保存失败')
  }
}

async function deleteCardFocus() {
  const session = cardFocus.value
  if (!session) return
  if (!window.confirm('删除这条专注记录？')) return
  try {
    await apiDeleteFocus(session.publicId, session.revision)
    focusSessions.value = focusSessions.value.filter(item => item.publicId !== session.publicId)
    closeEventCard()
    message.success('已删除')
  } catch (cause) {
    message.error(cause?.response?.data?.detail || cause?.message || '删除失败')
  }
}

async function deleteCard() {
  const task = cardTask.value
  if (!task) return
  if (!window.confirm(`删除任务「${task.title}」？`)) return
  await tasksStore.remove(task)
  closeEventCard()
  message.success('已删除')
}

async function focusCard() {
  const task = cardTask.value
  if (!task) return
  try {
    await focusStore.start({ taskId: task.publicId, plannedMinutes: 25 })
    void router.push('/tasks/focus')
  } catch (cause) {
    message.error(cause?.message || '无法开始专注')
  }
}

async function checkinCard() {
  const habit = cardHabit.value
  if (!habit || !eventCard.event) return
  await tasksStore.checkinHabit(habit, { date: eventCard.event.startKey, status: 'DONE', count: 1 })
  message.success('已打卡')
}

async function undoCheckinCard() {
  const habit = cardHabit.value
  if (!habit || !eventCard.event) return
  await tasksStore.checkinHabit(habit, { date: eventCard.event.startKey, status: 'NONE', count: 0 })
}

function dayEvents(day) {
  const bucket = calendarData.value.byDay.get(toDateKey(day))
  return bucket ? [...bucket.allDay, ...bucket.timed] : []
}

function hasEvents(day) {
  return dayEvents(day).length > 0
}

function subLabel(day) {
  const key = toDateKey(day)
  return holidayMap.value[key]?.name || lunarLabels.value[key] || ''
}

const preferenceKey = computed(() => `calendar_view_v1:${appStore.authUser?.id || 'local'}`)

function savePreferences() {
  try {
    localStorage.setItem(preferenceKey.value, JSON.stringify({
      view: view.value, spans: { ...spans }, colorSettings: { ...colorSettings }, scope: scope.value,
      timePrefs: { ...timePrefs },
      layers: { ...calendarLayers },
      planPanel: { groupBy: planPanel.groupBy, undated: planPanel.undated, overdue: planPanel.overdue, hideNotes: planPanel.hideNotes, hideHierarchy: planPanel.hideHierarchy }
    }))
  } catch { /* 忽略存储配额错误 */ }
}

function readPreferences() {
  try {
    const stored = JSON.parse(localStorage.getItem(preferenceKey.value) || '{}')
    if (VIEW_KEYS.includes(stored.view) || SPAN_KEYS.includes(stored.view)) view.value = stored.view
    if (stored.spans) {
      spans.multiDay = clampSpan('multiDay', stored.spans.multiDay)
      spans.multiWeek = clampSpan('multiWeek', stored.spans.multiWeek)
    }
    Object.assign(colorSettings, mergedColorSettings(stored.colorSettings))
    if (stored.timePrefs) {
      Object.assign(timePrefs, {
        hiddenTop: Number(stored.timePrefs.hiddenTop) || timePrefs.hiddenTop,
        hiddenBottom: Number(stored.timePrefs.hiddenBottom) || timePrefs.hiddenBottom,
        allDayHeight: Number.isFinite(stored.timePrefs.allDayHeight) ? stored.timePrefs.allDayHeight : timePrefs.allDayHeight,
        allDayCollapsed: Boolean(stored.timePrefs.allDayCollapsed)
      })
    }
    if (stored.layers) Object.assign(calendarLayers, stored.layers)
    if (stored.planPanel) Object.assign(planPanel, stored.planPanel)
    if (stored.scope && ['all', 'custom', 'filter'].includes(stored.scope.mode)) {
      scope.value = { mode: stored.scope.mode, listIds: stored.scope.listIds || [], tagIds: stored.scope.tagIds || [], filterId: stored.scope.filterId || '' }
    }
  } catch { /* 使用默认偏好 */ }
}

function applyScope(next) {
  scope.value = next
  savePreferences()
  if (next.mode === 'filter' && next.filterId) void loadFilterTasks(next.filterId)
  else filterTaskIds.value = new Set()
}

async function loadFilterTasks(filterId) {
  try {
    const rows = await apiListTaskFilterTasks(filterId, { size: 500 })
    filterTaskIds.value = new Set((rows?.items || []).map(item => item.publicId))
  } catch (cause) {
    filterTaskIds.value = new Set()
    message.error(cause?.response?.data?.detail || '过滤器任务加载失败')
  }
}

function miniMonthRange() {
  const weeks = monthWeeks(anchor.value)
  const first = weeks[0][0]
  const last = weeks[weeks.length - 1][6]
  return { from: toDateKey(first), to: toDateKey(addDays(last, 1)) }
}

async function loadRange() {
  loading.value = true
  error.value = ''
  const mainFrom = toDateKey(range.value.start)
  const mainTo = toDateKey(range.value.end)
  let from = mainFrom
  let to = mainTo
  // 迷你月历始终展示整月，主视图只取 1 天或 7 天时要把它的范围并进来，否则农历会缺一大半
  const mainDays = diffDays(range.value.start, range.value.end)
  if (mainDays < 60) {
    const mini = miniMonthRange()
    if (mini.from < from) from = mini.from
    if (mini.to > to) to = mini.to
  }
  try {
    const layers = aggregateMode.value
      ? ['worktime', 'ledger', 'holiday'].filter(key => calendarLayers[key] !== false).join(',') || 'holiday'
      : 'holiday'
    const [calendar, sessions] = await Promise.all([
      apiGetTaskCalendar({ from, to, layers, lunar: 1 }),
      apiListFocusSessions({ from, to }).catch(() => [])
    ])
    holidayMap.value = Object.fromEntries((calendar?.holidays || []).map(item => [item.date, item]))
    lunarLabels.value = Object.fromEntries((calendar?.lunar || []).map(item => [item.date, formatLunarLabel(item)]))
    focusSessions.value = sessions || []
    worktimeDays.value = calendar?.worktime || []
    ledgerDays.value = calendar?.ledger || []
  } catch (cause) {
    error.value = cause?.response?.data?.detail || cause?.message || '日历数据加载失败'
  } finally {
    loading.value = false
    await nextTick()
    measure()
  }
}

async function loadFilters() {
  try {
    filters.value = (await apiListTaskFilters()) || []
  } catch {
    filters.value = []
  }
}

function measure() {
  if (typeof window === 'undefined') return
  const bucket = widthBucket(window.innerWidth)
  // 断点变化时清掉手动开关，避免“手机上被强制展开”的遗留状态
  if (bucket !== currentBucket) {
    currentBucket = bucket
    narrow.value = bucket !== 'wide'
    collapsedManual.value = null
  }
  const body = bodyEl.value
  if (!body) return
  if (view.value === 'month' || view.value === 'multiWeek') {
    const rows = range.value.weeks.length || 5
    const available = (body.clientHeight - 28) / rows
    cellHeight.value = Math.max(CAL_ROW_MIN_HEIGHT, Math.round(available))
  }
}

function toggleSidebar() {
  collapsedManual.value = !collapsed.value
}

function closeMenus() {
  viewMenu.value = false
  moreOpen.value = false
  printMenuOpen.value = false
  printMenuOpen.value = false
  dayPopover.open = false
  closeEventCard()
}

function toggleViewMenu() {
  moreOpen.value = false
  viewMenu.value = !viewMenu.value
}

function setView(next) {
  view.value = next
  viewMenu.value = false
  savePreferences()
}

function changeSpan(key, delta) {
  spans[key] = clampSpan(key, spans[key] + delta)
  savePreferences()
}

function navigate(direction) {
  anchor.value = shiftAnchor(anchor.value, { view: view.value, span: spans[view.value], direction })
  if (direction === 'today') {
    selectedDay.value = startOfDay(new Date())
    today.value = startOfDay(new Date())
  }
}

function moveMonth(delta) {
  anchor.value = addMonths(anchor.value, delta)
}

function goToday() {
  navigate('today')
}

function jumpToDate(date) {
  anchor.value = startOfDay(date)
  selectedDay.value = startOfDay(date)
}

function selectDay(day, event) {
  event?.stopPropagation?.()
  if (swipe.suppressClick) {
    swipe.suppressClick = false
    return
  }
  const target = startOfDay(day)
  selectedDay.value = target
  const width = 272
  const height = 320
  const pointerX = Number.isFinite(event?.clientX) ? event.clientX : window.innerWidth / 2
  const pointerY = Number.isFinite(event?.clientY) ? event.clientY : 140
  const x = Math.min(Math.max(12, pointerX + 8), Math.max(12, window.innerWidth - width - 12))
  const y = Math.min(Math.max(56, pointerY - 10), Math.max(56, window.innerHeight - height - 12))
  Object.assign(dayPopover, { open: true, date: target, x, y })
}

function closeDayPopover() {
  dayPopover.open = false
}

function loadAgendaMore(direction) {
  const step = AGENDA_STEP_DAYS
  if (direction === 'before') {
    if (agendaExtra.before >= AGENDA_HORIZON) return
    agendaExtra.before = Math.min(AGENDA_HORIZON, agendaExtra.before + step)
  } else {
    if (agendaExtra.after >= AGENDA_HORIZON) return
    agendaExtra.after = Math.min(AGENDA_HORIZON, agendaExtra.after + step)
  }
}

function updateTimePrefs(patch) {
  Object.assign(timePrefs, patch)
  savePreferences()
}

function createAt(day, minutes) {
  void openQuickAdd(day, minutes)
}

function createAllday(day) {
  void openQuickAdd(day)
}

function createRange(day, startMinutes, endMinutes) {
  void openQuickAdd(day, startMinutes, { endMinutes })
}

function createSpan(startKey, endKey) {
  const start = fromDateKey(startKey)
  const end = fromDateKey(endKey)
  void openQuickAdd(start, null, { spanEnd: end })
}

async function rescheduleBlock(event, startMinutes, endMinutes) {
  if (!event || event.kind !== 'task') return
  const task = tasksStore.tasks.find(item => item.publicId === event.id)
  if (!task) return
  const day = fromDateKey(event.startKey)
  const start = new Date(day.getFullYear(), day.getMonth(), day.getDate(), Math.floor(startMinutes / 60), startMinutes % 60, 0)
  const end = new Date(day.getFullYear(), day.getMonth(), day.getDate(), Math.floor(endMinutes / 60), endMinutes % 60, 0)
  try {
    await tasksStore.update(task, { startAt: start.toISOString(), dueAt: end.toISOString(), allDay: false })
    message.success(`已改到 ${minutesToLabel(startMinutes)} – ${minutesToLabel(endMinutes)}`)
  } catch (cause) {
    message.error(cause?.response?.data?.detail || cause?.message || '改期失败')
  }
}

function addFromPopover() {
  const target = dayPopover.date
  dayPopover.open = false
  void openQuickAdd(target)
}

// 月/多周视图左右滑动翻页：横向位移超过阈值且明显大于纵向才触发，避免与纵向滚动/点击冲突
const swipe = { startX: 0, startY: 0, active: false, suppressClick: false }

function onSwipeStart(event) {
  if (event.pointerType === 'mouse' && event.button !== 0) return
  if (view.value !== 'month' && view.value !== 'multiWeek') return
  // 在日期网格（含跨天泳道）上按下属于「拖选多天 / 点开浮层」，不能同时当成翻页手势
  if (event.target?.closest?.('.cal-week')) return
  // 在日期网格（含跨天泳道）上按下属于「拖选多天 / 点开浮层」，不能同时当成翻页手势
  if (event.target?.closest?.('.cal-week')) return
  swipe.startX = event.clientX
  swipe.startY = event.clientY
  swipe.active = true
}

function resetSwipe() {
  swipe.active = false
}

function onSwipeEnd(event) {
  if (!swipe.active) return
  swipe.active = false
  const dx = event.clientX - swipe.startX
  const dy = event.clientY - swipe.startY
  if (Math.abs(dx) < 64 || Math.abs(dx) < Math.abs(dy) * 1.6) return
  swipe.suppressClick = true
  window.setTimeout(() => { swipe.suppressClick = false }, 260)
  navigate(dx < 0 ? 'next' : 'prev')
}

function openMonthFromYear(month) {
  anchor.value = new Date(anchor.value.getFullYear(), month - 1, 1)
  view.value = 'month'
  savePreferences()
}

function openDayView() {
  const target = dayPopover.date
  closeDayPopover()
  anchor.value = startOfDay(target)
  selectedDay.value = startOfDay(target)
  view.value = 'day'
  savePreferences()
}

function openEvent(event, nativeEvent) {
  if (!event || event.kind === 'holiday') return
  if (event.kind === 'worktime') {
    void router.push({ path: '/punch', query: { date: event.startKey } })
    return
  }
  if (event.kind === 'ledger') {
    void router.push({ path: '/ledger/transactions', query: { from: event.startKey, to: event.startKey } })
    return
  }
  const pointerX = Number.isFinite(nativeEvent?.clientX) ? nativeEvent.clientX : window.innerWidth / 2
  const pointerY = Number.isFinite(nativeEvent?.clientY) ? nativeEvent.clientY : 180
  // 任务浮层是任务右栏的浮层版，比旧卡片高，按内容估个高度再做边界收拢
  const width = event.kind === 'task' ? 340 : 316
  const height = event.kind === 'task' ? 470 : 260
  eventCard.event = event
  eventCard.x = Math.min(Math.max(12, pointerX + 10), Math.max(12, window.innerWidth - width - 12))
  eventCard.y = Math.min(Math.max(52, pointerY - 10), Math.max(52, window.innerHeight - height - 12))
  eventCard.open = true
}

function openDetail() {
  const event = eventCard.event
  if (!event) return
  closeEventCard()
  if (event.kind === 'task') void router.push({ path: '/tasks/all', query: { task: event.id } })
  else if (event.kind === 'habit') void router.push('/tasks/habits')
  else if (event.kind === 'countdown') void router.push('/tasks/countdowns')
  else if (event.kind === 'focus') void router.push('/tasks/focus')
}

async function openQuickAdd(day, startMinutes = null, options = {}) {
  viewMenu.value = false
  moreOpen.value = false
  dayPopover.open = false
  quickAdd.date = startOfDay(day || selectedDay.value || anchor.value)
  quickAdd.startMinutes = Number.isFinite(startMinutes) ? startMinutes : null
  quickAdd.endMinutes = Number.isFinite(options.endMinutes) ? options.endMinutes : (quickAdd.startMinutes == null ? null : quickAdd.startMinutes + 30)
  quickAdd.spanEnd = options.spanEnd ? startOfDay(options.spanEnd) : null
  quickAdd.title = ''
  quickAdd.priority = 'NONE'
  quickAdd.listId = tasksStore.inbox?.publicId || calendarLists.value[0]?.publicId || ''
  quickAdd.open = true
  await nextTick()
  quickInputEl.value?.focus()
}

function closeQuickAdd() {
  quickAdd.open = false
}

async function saveQuickAdd() {
  const title = quickAdd.title.trim()
  if (!title || quickAdd.saving) return
  quickAdd.saving = true
  try {
    const day = startOfDay(quickAdd.date)
    let payload
    if (quickAdd.spanEnd) {
      const span = spanToInstants(toDateKey(day), toDateKey(quickAdd.spanEnd))
      payload = { allDay: true, startAt: span.startAt, dueAt: span.dueAt }
    } else if (quickAdd.startMinutes != null) {
      const startMinutes = snapMinutes(quickAdd.startMinutes, 15)
      const endMinutes = Math.max(startMinutes + 15, snapMinutes(quickAdd.endMinutes ?? startMinutes + 30, 15))
      const start = new Date(day.getFullYear(), day.getMonth(), day.getDate(), Math.floor(startMinutes / 60), startMinutes % 60, 0)
      const endDay = endMinutes >= 1440 ? new Date(day.getTime() + 86400000) : day
      const normalizedEnd = endMinutes >= 1440 ? endMinutes - 1440 : endMinutes
      const end = new Date(endDay.getFullYear(), endDay.getMonth(), endDay.getDate(), Math.floor(normalizedEnd / 60), normalizedEnd % 60, 0)
      payload = { allDay: false, startAt: start.toISOString(), dueAt: end.toISOString() }
    } else {
      payload = { allDay: true, startAt: day.toISOString(), dueAt: day.toISOString() }
    }
    await tasksStore.create({
      title, ...payload,
      priority: quickAdd.priority, listId: quickAdd.listId || tasksStore.inbox?.publicId || ''
    })
    quickAdd.open = false
    message.success('任务已创建')
  } catch (cause) {
    message.error(cause?.response?.data?.detail || cause?.message || '任务创建失败')
  } finally {
    quickAdd.saving = false
  }
}

function openSettings() {
  moreOpen.value = false
  settings.open = true
}

function printCalendar(mode = 'detail') {
  moreOpen.value = false
  document.body.classList.toggle('cal-print-compact', mode === 'compact')
  document.body.classList.toggle('cal-print-detail', mode === 'detail')
  window.setTimeout(() => {
    window.print()
    window.setTimeout(() => document.body.classList.remove('cal-print-compact', 'cal-print-detail'), 200)
  }, 60)
}

function openPlanPanel() {
  moreOpen.value = false
  planPanel.open = true
}

function updatePlanPanel(patch) {
  Object.assign(planPanel, patch)
  savePreferences()
}

async function scheduleTaskTo(task, dateKey, minutes) {
  if (!task) return
  const day = fromDateKey(dateKey)
  if (minutes == null) {
    const instant = new Date(day.getFullYear(), day.getMonth(), day.getDate(), 0, 0, 0)
    await tasksStore.update(task, { allDay: true, startAt: instant.toISOString(), dueAt: instant.toISOString() })
  } else {
    const start = new Date(day.getFullYear(), day.getMonth(), day.getDate(), Math.floor(minutes / 60), minutes % 60, 0)
    const end = new Date(start.getTime() + 30 * 60000)
    await tasksStore.update(task, { allDay: false, startAt: start.toISOString(), dueAt: end.toISOString() })
  }
  message.success(`已安排到 ${day.getMonth() + 1}月${day.getDate()}日`)
}

async function scheduleFromPanel(task) {
  const target = toDateKey(selectedDay.value || anchor.value)
  await scheduleTaskTo(task, target, null)
}

async function dropTask(dateKey, minutes, event) {
  const id = event?.dataTransfer?.getData('application/x-calendar-task')
    || event?.dataTransfer?.getData('text/plain')
  const task = tasksStore.tasks.find(item => item.publicId === id)
  if (!task) return
  try {
    await scheduleTaskTo(task, dateKey, minutes)
  } catch (cause) {
    message.error(cause?.response?.data?.detail || cause?.message || '排期失败')
  }
}

function applyLayers(next) {
  Object.assign(calendarLayers, next)
  savePreferences()
  void loadRange()
}

function applyColorSettings(next) {
  Object.assign(colorSettings, next)
  savePreferences()
}

async function updateListColor(list, color) {
  try {
    await tasksStore.updateList(list, { color })
    message.success('清单颜色已更新')
  } catch (cause) {
    message.error(cause?.response?.data?.detail || cause?.message || '清单颜色保存失败')
  }
}

async function updateHabitColor(habit, color) {
  try {
    await tasksStore.updateHabit(habit, { color })
    message.success('习惯颜色已更新')
  } catch (cause) {
    message.error(cause?.response?.data?.detail || cause?.message || '习惯颜色保存失败')
  }
}

async function updateCountdownColor(item, color) {
  try {
    await tasksStore.updateCountdown(item, { color })
    message.success('纪念日颜色已更新')
  } catch (cause) {
    message.error(cause?.response?.data?.detail || cause?.message || '纪念日颜色保存失败')
  }
}

let observer
function onKeydown(event) {
  if (event.key !== 'Escape') return
  if (quickAdd.open) closeQuickAdd()
  else if (eventCard.open) closeEventCard()
  else if (dayPopover.open) closeDayPopover()
  else if (settings.open) settings.open = false
  else closeMenus()
}

watch([() => range.value.start.getTime(), () => range.value.end.getTime()], () => { void loadRange() })
watch([view, () => anchor.value.getTime()], () => {
  if (agendaExtra.before || agendaExtra.after) {
    agendaExtra.before = 0
    agendaExtra.after = 0
  }
})
watch(preferenceKey, () => { readPreferences(); void loadRange() })

async function ensureStore() {
  // 登录切换会话时旧索引库可能正在关闭，init 会抛 InvalidStateError；此处只做一次补拉，避免把错误抛到全局错误边界
  try {
    await tasksStore.init({ waitForRemote: false })
  } catch { /* 会话切换中，交由下面的补拉处理 */ }
  if (!tasksStore.lists.length && tasksStore.online) {
    try {
      await tasksStore.refreshServer()
    } catch { /* 网络异常时保留空态并沿用接口错误提示 */ }
  }
}

onMounted(async () => {
  readPreferences()
  window.addEventListener('keydown', onKeydown)
  if (typeof ResizeObserver !== 'undefined' && shellEl.value) {
    observer = new ResizeObserver(() => measure())
    observer.observe(shellEl.value)
  }
  window.addEventListener('resize', measure)
  await ensureStore()
  if (scope.value.mode === 'filter' && scope.value.filterId) void loadFilterTasks(scope.value.filterId)
  await Promise.all([loadFilters(), loadRange()])
})

onBeforeUnmount(() => {
  window.removeEventListener('keydown', onKeydown)
  window.removeEventListener('resize', measure)
  observer?.disconnect()
})
</script>

<style scoped>
.cal-shell {
  position: relative;
  display: flex;
  height: calc(100dvh - 88px);
  margin: 0;
  background: var(--card);
  color: var(--ink);
  overflow: hidden;
}
.cal-shell.is-task-route {
  height: 100dvh;
}
.cal-main {
  display: flex;
  flex: 1 1 auto;
  flex-direction: column;
  min-width: 0;
  min-height: 0;
}
.cal-head {
  display: flex;
  flex: 0 0 auto;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  min-height: 46px;
  padding: 0 12px;
  border-bottom: 1px solid var(--line);
}
.cal-head-left,
.cal-head-right {
  display: flex;
  align-items: center;
  gap: 6px;
}
.cal-head-right {
  gap: 8px;
}
.cal-icon-btn {
  display: grid;
  place-items: center;
  width: 30px;
  height: 30px;
  border: 1px solid transparent;
  border-radius: 7px;
  background: transparent;
  color: var(--ink2);
}
.cal-icon-btn:hover {
  border-color: var(--line);
  background: var(--paper);
  color: var(--ink);
}
.cal-title {
  display: inline-flex;
  min-width: 0;
  align-items: center;
  gap: 6px;
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
  color: var(--ink);
  font-size: 15px;
  font-weight: 650;
  letter-spacing: .01em;
}
.cal-text-btn {
  display: inline-flex;
  align-items: center;
  white-space: nowrap;
  gap: 4px;
  height: 30px;
  padding: 0 9px;
  border: 1px solid var(--line);
  border-radius: 7px;
  background: transparent;
  color: var(--ink);
  font-size: 12.5px;
  font-weight: 550;
}
.cal-text-btn:hover {
  background: var(--paper);
}
.cal-nav {
  display: flex;
  align-items: stretch;
  height: 30px;
  border: 1px solid var(--line);
  border-radius: 7px;
  overflow: hidden;
}
.cal-nav button {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 28px;
  white-space: nowrap;
  padding: 0 7px;
  border: 0;
  border-right: 1px solid var(--line);
  background: transparent;
  color: var(--ink2);
  font-size: 12.5px;
}
.cal-nav button:last-child {
  border-right: 0;
}
.cal-nav button:hover {
  background: var(--paper);
  color: var(--ink);
}
.cal-nav .cal-nav-today {
  font-weight: 600;
}
.cal-menu-wrap {
  position: relative;
}
.cal-menu {
  position: absolute;
  z-index: 40;
  top: 36px;
  right: 0;
  min-width: 208px;
  padding: 6px;
  border: 1px solid var(--line);
  border-radius: 10px;
  background: var(--card);
  box-shadow: 0 16px 38px rgba(15, 23, 42, .16);
}
.cal-menu.is-more {
  min-width: 168px;
}
.cal-menu-row {
  display: flex;
  width: 100%;
  align-items: center;
  gap: 8px;
  min-height: 34px;
  padding: 0 10px;
  border: 0;
  border-radius: 7px;
  background: transparent;
  color: var(--ink);
  font-size: 13px;
  text-align: left;
}
.cal-menu-row:hover {
  background: var(--paper);
}
.cal-menu-row.active {
  color: var(--accent);
}
.cal-menu-label {
  min-width: 0;
  flex: 1;
}
.cal-menu-tail {
  color: var(--muted);
  font-size: 11px;
}
.cal-menu-row.is-print {
  cursor: pointer;
}
.cal-menu-sub {
  padding-left: 14px;
}
.cal-menu-sub .cal-menu-row {
  min-height: 30px;
  font-size: 12.5px;
  color: var(--ink2);
}
.cal-menu-row.is-print {
  cursor: pointer;
}
.cal-menu-sub {
  padding-left: 14px;
}
.cal-menu-sub .cal-menu-row {
  min-height: 30px;
  font-size: 12.5px;
  color: var(--ink2);
}
.cal-menu-divider {
  height: 1px;
  margin: 5px 8px;
  background: var(--line);
}
.cal-span-stepper {
  display: inline-flex;
  align-items: center;
  gap: 2px;
}
.cal-span-stepper button {
  display: grid;
  place-items: center;
  width: 20px;
  height: 20px;
  border: 1px solid var(--line);
  border-radius: 5px;
  background: transparent;
  color: var(--ink2);
  font-size: 12px;
  line-height: 1;
}
.cal-span-stepper button:disabled {
  opacity: .35;
}
.cal-span-stepper b {
  min-width: 16px;
  color: var(--ink);
  font-size: 12px;
  font-variant-numeric: tabular-nums;
  text-align: center;
}
.cal-span-stepper small {
  color: var(--muted);
  font-size: 10px;
}
.cal-menu-row.active .cal-menu-label,
.cal-menu-row.active .cal-span-stepper b {
  color: var(--accent);
}
.cal-error {
  margin: 0;
  padding: 8px 14px;
  border-bottom: 1px solid var(--line);
  background: color-mix(in srgb, var(--down) 8%, transparent);
  color: var(--down);
  font-size: 12px;
}
.cal-loading {
  position: relative;
  flex: 0 0 auto;
  height: 2px;
  overflow: hidden;
  background: color-mix(in srgb, var(--accent) 12%, transparent);
}
.cal-loading::after {
  position: absolute;
  inset: 0 auto 0 0;
  width: 34%;
  background: var(--accent);
  content: '';
  animation: cal-loading-slide 1.1s ease-in-out infinite;
}
@keyframes cal-loading-slide {
  0% { transform: translateX(-100%); }
  100% { transform: translateX(320%); }
}
.cal-body {
  flex: 1 1 auto;
  min-height: 0;
  overflow: auto;
}
.cal-year {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(240px, 1fr));
  gap: 18px 24px;
  padding: 18px;
}
.cal-year-month h3 {
  margin: 0 0 6px;
  font-size: 13px;
  font-weight: 650;
}
.cal-year-dow,
.cal-year-grid {
  display: grid;
  grid-template-columns: repeat(7, minmax(0, 1fr));
  text-align: center;
}
.cal-year-dow span {
  padding-bottom: 3px;
  color: var(--muted);
  font-size: 9.5px;
}
.cal-year-day {
  position: relative;
  display: grid;
  place-items: center;
  height: 24px;
  border: 0;
  border-radius: 4px;
  background: transparent;
  color: var(--ink);
  font-size: 11px;
  font-variant-numeric: tabular-nums;
}
.cal-year-day.has-event {
  background: color-mix(in srgb, var(--accent) 12%, transparent);
  color: var(--accent);
  font-weight: 600;
}
.cal-year-day.muted {
  color: var(--muted);
  opacity: .45;
}
.cal-year-day.today {
  background: var(--accent);
  color: #fff;
  font-weight: 700;
}
.cal-year-day i {
  position: absolute;
  bottom: 3px;
  width: 3px;
  height: 3px;
  border-radius: 50%;
  background: currentColor;
}
.cal-stack {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(180px, 1fr));
  gap: 0;
  height: 100%;
}
.cal-stack-day {
  display: flex;
  flex-direction: column;
  min-height: 0;
  padding: 8px 8px 12px;
  border-right: 1px solid var(--line);
  overflow: auto;
}
.cal-stack-day:last-child {
  border-right: 0;
}
.cal-stack.is-agenda {
  display: block;
  height: auto;
  padding: 6px 0 40px;
}
.cal-stack.is-agenda .cal-stack-day {
  border-right: 0;
  border-bottom: 1px solid var(--line);
}
.cal-stack-head {
  display: flex;
  align-items: baseline;
  gap: 6px;
  padding-bottom: 6px;
  color: var(--ink);
}
.cal-stack-head b {
  font-size: 17px;
  font-weight: 650;
  font-variant-numeric: tabular-nums;
}
.cal-stack-weekday {
  color: var(--ink2);
  font-size: 11px;
}
.cal-stack-head small {
  margin-left: auto;
  color: var(--muted);
  font-size: 10px;
}
.cal-stack-body {
  display: flex;
  flex-direction: column;
  gap: 3px;
}
.cal-stack-empty {
  margin: 2px 0;
  color: var(--muted);
  font-size: 11px;
}
.cal-row {
  display: flex;
  align-items: center;
  gap: 6px;
  min-height: 26px;
  padding: 0 8px;
  border: 1px solid transparent;
  border-radius: 6px;
  color: var(--ink);
  font-size: 12px;
  text-align: left;
}
.cal-row.is-static {
  cursor: default;
}
.cal-row-dot {
  flex: 0 0 auto;
  width: 9px;
  height: 9px;
  border: 1.5px solid currentColor;
  border-radius: 50%;
}
.cal-row-dot.is-done {
  background: currentColor;
  box-shadow: inset 0 0 0 1.5px var(--card);
}
.cal-row-title {
  min-width: 0;
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.cal-row.is-done .cal-row-title {
  color: var(--muted);
  text-decoration: line-through;
}
.cal-row-time,
.cal-row-extra {
  flex: 0 0 auto;
  color: var(--ink2);
  font-size: 10.5px;
  font-variant-numeric: tabular-nums;
}
.cal-quick {
  position: absolute;
  z-index: 50;
  top: 52px;
  right: 132px;
  display: flex;
  width: min(420px, calc(100vw - 32px));
  flex-direction: column;
  padding: 14px 14px 10px;
  border: 1px solid var(--line2);
  border-radius: 12px;
  background: var(--card);
  box-shadow: 0 22px 52px rgba(15, 23, 42, .22);
}
.cal-quick header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.cal-quick-date {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  color: var(--accent);
  font-size: 12.5px;
  font-weight: 600;
}
.cal-quick-flag {
  display: grid;
  place-items: center;
  width: 26px;
  height: 26px;
  border: 0;
  border-radius: 6px;
  background: transparent;
  color: var(--muted);
}
.cal-quick-flag.on {
  color: var(--priority-high, #e34d59);
}
.cal-quick-input {
  width: 100%;
  min-height: 74px;
  margin: 10px 0 12px;
  padding: 0;
  border: 0;
  background: transparent;
  color: var(--ink);
  font-size: 17px;
  line-height: 1.5;
  outline: none;
  box-shadow: none;
  resize: none;
}
.cal-quick footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
}
.cal-quick-list {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  color: var(--ink2);
  font-size: 12px;
}
.cal-quick-list select {
  max-width: 132px;
  border: 0;
  background: transparent;
  color: var(--ink2);
  font-size: 12px;
  outline: none;
}
.cal-quick-actions {
  display: flex;
  gap: 6px;
}
.cal-quick-actions button {
  height: 28px;
  padding: 0 12px;
  border: 1px solid var(--line);
  border-radius: 6px;
  background: transparent;
  color: var(--ink2);
  font-size: 12px;
}
.cal-quick-save {
  border-color: var(--accent) !important;
  background: var(--accent) !important;
  color: #fff !important;
}
.cal-quick-save:disabled {
  opacity: .5;
}
.cal-day-popover {
  position: fixed;
  z-index: 55;
  display: flex;
  width: 272px;
  max-height: min(340px, calc(100vh - 80px));
  flex-direction: column;
  overflow: hidden;
  border: 1px solid var(--line2);
  border-radius: 10px;
  background: var(--card);
  box-shadow: 0 18px 42px rgba(15, 23, 42, .2);
}
.cal-day-popover > header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  padding: 10px 12px;
  border-bottom: 1px solid var(--line);
}
.cal-day-popover > header b {
  display: block;
  font-size: 12.5px;
  font-weight: 650;
}
.cal-day-popover > header small {
  display: block;
  margin-top: 1px;
  color: var(--muted);
  font-size: 10px;
}
.cal-day-popover > header button {
  display: grid;
  flex: 0 0 auto;
  place-items: center;
  width: 24px;
  height: 24px;
  border: 0;
  border-radius: 6px;
  background: transparent;
  color: var(--muted);
}
.cal-day-popover > header button:hover {
  background: var(--paper);
  color: var(--ink);
}
.cal-popover-list {
  display: flex;
  flex: 1 1 auto;
  flex-direction: column;
  gap: 3px;
  min-height: 0;
  padding: 8px;
  overflow-y: auto;
}
.cal-popover-empty {
  margin: 6px 4px;
  color: var(--muted);
  font-size: 11.5px;
}
.cal-day-popover > footer {
  display: flex;
  gap: 6px;
  padding: 8px;
  border-top: 1px solid var(--line);
}
.cal-popover-day {
  flex: 0 0 auto;
  height: 30px;
  padding: 0 10px;
  border: 1px solid var(--line);
  border-radius: 7px;
  background: transparent;
  color: var(--ink2);
  font-size: 12px;
}
.cal-popover-day:hover {
  border-color: var(--accent);
  color: var(--accent);
}
.cal-popover-add {
  display: inline-flex;
  flex: 1 1 auto;
  width: auto;
  align-items: center;
  justify-content: center;
  gap: 6px;
  height: 30px;
  border: 1px dashed var(--line2);
  border-radius: 7px;
  background: transparent;
  color: var(--ink2);
  font-size: 12px;
}
.cal-popover-add:hover {
  border-color: var(--accent);
  color: var(--accent);
}
.cal-body.is-snap {
  scroll-snap-type: y proximity;
}
.cal-body.is-snap :deep(.cal-week) {
  scroll-snap-align: start;
}
.cal-body.is-snap :deep(.cal-week) {
  min-height: 96px;
}
@media (max-width: 1023px) {
  .cal-day-popover {
    right: 10px !important;
    left: 10px !important;
    width: auto;
  }
}

.cal-day-popover {
  position: fixed;
  z-index: 55;
  display: flex;
  width: 272px;
  max-height: min(340px, calc(100vh - 80px));
  flex-direction: column;
  overflow: hidden;
  border: 1px solid var(--line2);
  border-radius: 10px;
  background: var(--card);
  box-shadow: 0 18px 42px rgba(15, 23, 42, .2);
}
.cal-day-popover > header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  padding: 10px 12px;
  border-bottom: 1px solid var(--line);
}
.cal-day-popover > header b {
  display: block;
  font-size: 12.5px;
  font-weight: 650;
}
.cal-day-popover > header small {
  display: block;
  margin-top: 1px;
  color: var(--muted);
  font-size: 10px;
}
.cal-day-popover > header button {
  display: grid;
  flex: 0 0 auto;
  place-items: center;
  width: 24px;
  height: 24px;
  border: 0;
  border-radius: 6px;
  background: transparent;
  color: var(--muted);
}
.cal-day-popover > header button:hover {
  background: var(--paper);
  color: var(--ink);
}
.cal-popover-list {
  display: flex;
  flex: 1 1 auto;
  flex-direction: column;
  gap: 3px;
  min-height: 0;
  padding: 8px;
  overflow-y: auto;
}
.cal-popover-empty {
  margin: 6px 4px;
  color: var(--muted);
  font-size: 11.5px;
}
.cal-day-popover > footer {
  display: flex;
  gap: 6px;
  padding: 8px;
  border-top: 1px solid var(--line);
}
.cal-popover-day {
  flex: 0 0 auto;
  height: 30px;
  padding: 0 10px;
  border: 1px solid var(--line);
  border-radius: 7px;
  background: transparent;
  color: var(--ink2);
  font-size: 12px;
}
.cal-popover-day:hover {
  border-color: var(--accent);
  color: var(--accent);
}
.cal-popover-add {
  display: inline-flex;
  flex: 1 1 auto;
  width: auto;
  align-items: center;
  justify-content: center;
  gap: 6px;
  height: 30px;
  border: 1px dashed var(--line2);
  border-radius: 7px;
  background: transparent;
  color: var(--ink2);
  font-size: 12px;
}
.cal-popover-add:hover {
  border-color: var(--accent);
  color: var(--accent);
}
.cal-body.is-snap {
  scroll-snap-type: y proximity;
}
.cal-body.is-snap :deep(.cal-week) {
  scroll-snap-align: start;
}
.cal-body.is-snap :deep(.cal-week) {
  min-height: 96px;
}
@media (max-width: 1023px) {
  .cal-day-popover {
    right: 10px !important;
    left: 10px !important;
    width: auto;
  }
}

@media (max-width: 1023px) {
  .cal-shell,
  .cal-shell.is-task-route {
    height: calc(100dvh - 64px);
  }
  .cal-head {
    gap: 6px;
    padding: 0 8px;
  }
  .cal-head-right {
    gap: 4px;
  }
  .cal-nav .cal-nav-today {
    padding: 0 6px;
    font-size: 11.5px;
  }
  /* 手机宽度下月格保持可读列宽，横向滚动发生在日历容器内部而不是整页 */
  .cal-month {
    min-width: 560px;
  }
  .cal-quick {
    top: 50px;
    right: 10px;
    left: 10px;
    width: auto;
  }
  .cal-stack {
    grid-template-columns: repeat(auto-fit, minmax(150px, 1fr));
    overflow-x: auto;
  }
  .cal-year {
    grid-template-columns: repeat(2, minmax(0, 1fr));
    gap: 14px;
    padding: 12px;
  }
}
@media print {
  :global(body.cal-print-compact .cal-event .cal-ev-title),
  :global(body.cal-print-compact .cal-event .cal-ev-time),
  :global(body.cal-print-compact .cal-event .cal-ev-extra),
  :global(body.cal-print-compact .cal-lane .cal-ev-title),
  :global(body.cal-print-compact .cal-lane .cal-ev-extra),
  :global(body.cal-print-compact .cal-time .cal-block-title),
  :global(body.cal-print-compact .cal-time .cal-block-time),
  :global(body.cal-print-compact .cal-agenda-row .cal-agenda-title) {
    display: none !important;
  }
  :global(body.cal-print-compact .cal-event),
  :global(body.cal-print-compact .cal-lane) {
    height: 6px !important;
  }
  .cal-shell {
    height: auto;
  }
  .cal-head,
  .cal-sidebar,
  .cal-quick {
    display: none !important;
  }
  .cal-body {
    overflow: visible;
  }
}
</style>
