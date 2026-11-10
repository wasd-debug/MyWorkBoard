<template>
  <HomeWidgetFrame title="日历" kicker="CALENDAR" :meta="`${year} 年 ${month} 月`" :editing="editing">
    <div class="hc-cal">
      <div class="hc-week"><span v-for="label in WEEKDAYS" :key="label">{{ label }}</span></div>
      <div class="hc-grid">
        <div
          v-for="cell in cells"
          :key="cell.key"
          class="hc-cell"
          :class="{ 'is-today': cell.today, 'is-off': cell.off, 'is-blank': !cell.day }"
          :title="cell.title"
        >
          <b v-if="cell.day">{{ cell.day }}</b>
          <span v-if="cell.holidayLabel" class="hc-holiday">{{ cell.holidayLabel }}</span>
          <span v-if="cell.day" class="hc-dots">
            <i v-if="cell.tasks" class="hc-dot is-task"></i>
            <i v-if="cell.flow" class="hc-dot is-flow"></i>
            <i v-if="cell.work" class="hc-dot is-work"></i>
          </span>
        </div>
      </div>
    </div>
    <template #footer>
      <span class="hc-legend"><i class="hc-dot is-task" />任务 <i class="hc-dot is-flow" />流水 <i class="hc-dot is-work" />工时</span>
      <span>{{ monthProgress }}% 已过</span>
    </template>
  </HomeWidgetFrame>
</template>

<script setup>
import { computed } from 'vue'
import HomeWidgetFrame from './HomeWidgetFrame.vue'
import { CALC } from '../../utils/calc.js'
import { useTasksStore } from '../../stores/tasks.js'
import { useLedgerStore } from '../../stores/ledger.js'
import { useWorktimeStore } from '../../stores/worktime.js'
import { useAppStore } from '../../stores/app.js'
import { dateKeyOf, monthKeyOf } from './homeMetrics.js'
import { taskRange } from '../../utils/taskDates.js'

defineProps({ editing: Boolean })

const WEEKDAYS = ['一', '二', '三', '四', '五', '六', '日']
const tasksStore = useTasksStore()
const ledgerStore = useLedgerStore()
const worktimeStore = useWorktimeStore()
const appStore = useAppStore()

const today = new Date()
const todayKey = dateKeyOf(today)
const monthKey = monthKeyOf(today)
const year = today.getFullYear()
const month = today.getMonth() + 1

const monthProgress = computed(() => Math.round((today.getDate() / CALC.daysInMonth(monthKey)) * 100))

const taskDays = computed(() => {
  const counts = new Map()
  for (const task of tasksStore.tasks) {
    if (!task || task.deleted || task.status !== 'OPEN') continue
    const range = taskRange(task)
    if (range) {
      const cursor = new Date(range.start.getTime())
      cursor.setHours(0, 0, 0, 0)
      while (cursor <= range.end) {
        const key = dateKeyOf(cursor)
        if (key.startsWith(monthKey)) counts.set(key, (counts.get(key) || 0) + 1)
        cursor.setDate(cursor.getDate() + 1)
      }
      continue
    }
    if (!task.dueAt) continue
    const key = dateKeyOf(new Date(task.dueAt))
    if (key.startsWith(monthKey)) counts.set(key, (counts.get(key) || 0) + 1)
  }
  return counts
})

const flowDays = computed(() => {
  const counts = new Map()
  for (const row of ledgerStore.transactions) {
    if (!row || row.deleted || !['INCOME', 'EXPENSE'].includes(row.kind)) continue
    const key = String(row.occurredOn || '')
    if (!key.startsWith(monthKey)) continue
    counts.set(key, (counts.get(key) || 0) + 1)
  }
  return counts
})

const cells = computed(() => {
  const days = CALC.daysInMonth(monthKey)
  const offset = (new Date(`${monthKey}-01T00:00:00`).getDay() + 6) % 7
  const list = Array.from({ length: offset }, (_, index) => ({ key: `blank-${index}`, day: 0 }))
  for (let index = 1; index <= days; index += 1) {
    const key = `${monthKey}-${String(index).padStart(2, '0')}`
    const holiday = appStore.holidays[key]
    const tasks = taskDays.value.get(key) || 0
    const flow = flowDays.value.get(key) || 0
    const work = worktimeStore.records[key]?.id ? 1 : 0
    const holidayLabel = holiday ? (holiday.off ? '休' : '班') : ''
    const parts = []
    if (tasks) parts.push(`${tasks} 个任务`)
    if (flow) parts.push(`${flow} 笔流水`)
    if (work) parts.push('有工时记录')
    list.push({
      key,
      day: index,
      today: key === todayKey,
      off: holiday ? Boolean(holiday.off) : CALC.isWeekendKey(key),
      holidayLabel,
      tasks,
      flow,
      work,
      title: [key.replaceAll('-', '/'), holiday?.name, ...parts].filter(Boolean).join(' · ')
    })
  }
  return list
})
</script>

<style scoped>
.hc-cal { display: flex; height: 100%; flex-direction: column; gap: 5px; }
.hc-week, .hc-grid { display: grid; grid-template-columns: repeat(7, minmax(0, 1fr)); gap: 2px; }
.hc-grid { flex: 1; grid-auto-rows: minmax(30px, 52px); align-content: center; }
.hc-week span { padding-bottom: 4px; color: var(--muted); font-size: 10px; text-align: center; }
.hc-cell {
  position: relative;
  display: flex;
  min-height: 34px;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 3px;
  border-radius: 10px;
  color: var(--ink2);
  font-variant-numeric: tabular-nums;
}
.hc-cell b { position: relative; z-index: 1; font-size: 12.5px; font-weight: 620; line-height: 1.1; }
.hc-cell.is-off { color: var(--muted); }
.hc-cell.is-blank { visibility: hidden; }
.hc-cell.is-today { color: var(--accent); }
.hc-cell.is-today b {
  display: inline-grid;
  width: 24px;
  height: 24px;
  place-items: center;
  border-radius: 50%;
  background: var(--accent);
  color: var(--paper);
  font-size: 12px;
  font-weight: 780;
}
.hc-holiday { position: absolute; top: 0; right: 5px; color: var(--muted); font-size: 8px; }
.hc-cell.is-today .hc-holiday { color: var(--accent); font-weight: 700; }
.hc-dots { display: flex; height: 4px; align-items: center; gap: 2px; }
.hc-dot { width: 4px; height: 4px; border-radius: 50%; background: var(--muted); }
.hc-dot.is-task { background: #4d78db; }
.hc-dot.is-flow { background: var(--down); }
.hc-dot.is-work { background: #2f9e44; }
.hc-legend { display: inline-flex; align-items: center; gap: 5px; }
.hc-legend .hc-dot { display: inline-block; margin-left: 6px; }
.hc-legend .hc-dot:first-child { margin-left: 0; }
</style>
