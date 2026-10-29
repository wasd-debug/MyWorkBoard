<template>
  <main class="focus-stats">
    <header class="focus-stats-head">
      <h1>统计</h1>
      <div class="focus-stats-tabs" role="tablist" aria-label="统计范围">
        <button type="button" role="tab" :class="{ on: tab === 'OVERVIEW' }" :aria-selected="tab === 'OVERVIEW'" @click="tab = 'OVERVIEW'">总览</button>
        <button type="button" role="tab" :class="{ on: tab === 'TASK' }" :aria-selected="tab === 'TASK'" @click="tab = 'TASK'">任务</button>
        <button type="button" role="tab" :class="{ on: tab === 'FOCUS' }" :aria-selected="tab === 'FOCUS'" @click="tab = 'FOCUS'">专注</button>
      </div>
    </header>

    <p v-if="focus.error" class="focus-stats-error" role="alert">{{ focus.error }}</p>

    <template v-if="tab === 'OVERVIEW'">
      <section class="fs-strip">
        <span><b>{{ totals.tasks }}</b> 任务</span>
        <span><b>{{ totals.done }}</b> 已完成</span>
        <span><b>{{ totals.lists }}</b> 清单</span>
        <span><b>{{ totals.days }}</b> 使用天数</span>
        <em>{{ motivation }}</em>
      </section>

      <section class="fs-card is-wide">
        <h2>概览</h2>
        <div class="fs-numbers">
          <div><b>{{ dayStats.done }}</b><span>今日已完成</span></div>
          <div><b>{{ focus.stats?.todayCount || 0 }}</b><span>今日番茄</span></div>
          <div><b>{{ duration(focus.stats?.todayMinutes) }}</b><span>今日专注时长</span></div>
          <div><b>{{ totals.done }}</b><span>总已完成</span></div>
          <div><b>{{ focus.stats?.totalCount || 0 }}</b><span>总番茄</span></div>
          <div><b>{{ duration(focus.stats?.totalMinutes) }}</b><span>总专注时长</span></div>
        </div>
      </section>

      <section class="fs-card">
        <h2>最近番茄数趋势</h2>
        <LedgerReportChart :option="pomodoroTrendOption" aria-label="最近番茄数趋势" height="220px" />
      </section>

      <section class="fs-card">
        <h2>最近专注时长趋势</h2>
        <LedgerReportChart :option="focusMinutesTrendOption" aria-label="最近专注时长趋势" height="220px" />
      </section>

      <section class="fs-card">
        <h2>最近完成数趋势</h2>
        <LedgerReportChart :option="doneTrendOption" aria-label="最近完成数趋势" height="220px" />
      </section>

      <section class="fs-card">
        <h2>本周打卡进展</h2>
        <div class="fs-week">
          <div v-for="day in weekProgress" :key="day.key" :class="{ on: day.minutes > 0 }">
            <span>{{ day.label }}</span>
            <i><b :style="{ height: `${day.ratio}%` }"></b></i>
            <em>{{ day.minutes ? shortMinutes(day.minutes) : '—' }}</em>
          </div>
        </div>
      </section>
    </template>

    <template v-else-if="tab === 'TASK'">
      <section class="fs-toolbar">
        <button type="button" @click="shiftDay(-1)"><ChevronLeft :size="15" /></button>
        <strong>{{ dayLabel }}</strong>
        <button type="button" @click="shiftDay(1)"><ChevronRight :size="15" /></button>
      </section>

      <section class="fs-card">
        <h2>概览</h2>
        <div class="fs-numbers is-two">
          <div><b>{{ dayStats.done }}</b><span>完成数</span></div>
          <div><b>{{ dayStats.rate }}%</b><span>完成率</span></div>
        </div>
      </section>

      <section class="fs-card">
        <h2>完成率分布</h2>
        <LedgerReportChart :option="ratePieOption" aria-label="完成率分布" height="240px" />
      </section>

      <section class="fs-card">
        <h2>已完成分类统计</h2>
        <LedgerReportChart v-if="listPieOption" :option="listPieOption" aria-label="已完成分类统计" height="260px" />
        <p v-else class="fs-empty">暂无数据</p>
      </section>
    </template>

    <template v-else>
      <section class="fs-card">
        <h2>概览</h2>
        <div class="fs-numbers">
          <div><b>{{ focus.stats?.todayCount || 0 }}</b><span>今日番茄</span></div>
          <div><b>{{ focus.stats?.todayMinutes || 0 }}</b><span>今日专注(分)</span></div>
          <div><b>{{ focus.stats?.totalCount || 0 }}</b><span>总番茄</span></div>
          <div><b>{{ duration(focus.stats?.totalMinutes) }}</b><span>总专注时长</span></div>
        </div>
      </section>

      <section class="fs-card">
        <h2>专注趋势（本周）</h2>
        <LedgerReportChart :option="weekFocusOption" aria-label="专注趋势" height="240px" />
      </section>

      <section class="fs-card">
        <h2>专注记录（本周）</h2>
        <p v-if="!focus.sessions.length" class="fs-empty">本周还没有专注记录</p>
        <ul v-else class="fs-records">
          <li v-for="session in focus.sessions.slice(0, 20)" :key="session.publicId">
            <span>{{ formatDate(session.startedAt) }}</span>
            <b>{{ session.taskTitle || '未关联任务' }}</b>
            <em>{{ shortMinutes(session.actualMinutes) }}</em>
          </li>
        </ul>
      </section>
    </template>
  </main>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { ChevronLeft, ChevronRight } from 'lucide-vue-next'
import LedgerReportChart from '../components/ledger/LedgerReportChart.vue'
import { useTaskFocusStore } from '../stores/taskFocus.js'
import { useTasksStore } from '../stores/tasks.js'

const focus = useTaskFocusStore()
const tasksStore = useTasksStore()
const tab = ref('OVERVIEW')
const selectedDay = ref(new Date())

const allTasks = computed(() => tasksStore.tasks.filter(task => !task.deleted))
const dayKey = computed(() => keyOf(selectedDay.value))

const totals = computed(() => {
  const tasks = allTasks.value
  const done = tasks.filter(task => task.status === 'COMPLETED').length
  const earliest = tasks.reduce((acc, task) => {
    const time = new Date(task.createdAt || Date.now()).getTime()
    return time && time < acc ? time : acc
  }, Date.now())
  const days = Math.max(1, Math.ceil((Date.now() - earliest) / 86400000))
  return { tasks: Math.max(tasks.length, tasksStore.total || 0), done, lists: tasksStore.lists.length, days }
})

const motivation = computed(() => {
  const rate = totals.value.tasks ? totals.value.done / totals.value.tasks : 0
  const percent = Math.min(99, Math.round(60 + rate * 39))
  return `你比 ${percent}% 的用户更勤奋`
})

const dayStats = computed(() => {
  const tasks = allTasks.value
  const day = dayKey.value
  const completedToday = tasks.filter(task => task.completedAt && keyOf(new Date(task.completedAt)) === day)
  // 完成率分母：当天完成 + 当天到期但未完成
  const dueToday = tasks.filter(task => task.dueAt && keyOf(new Date(task.dueAt)) === day && task.status !== 'COMPLETED')
  const denominator = completedToday.length + dueToday.length
  const rate = denominator ? Math.round((completedToday.length / denominator) * 100) : 0
  return { done: completedToday.length, overdue: dueToday.length, rate, completedToday }
})

const dayLabel = computed(() => {
  const date = selectedDay.value
  const same = keyOf(date) === keyOf(new Date())
  return `${date.getMonth() + 1}月${date.getDate()}日${same ? '（今天）' : ''}`
})

const recentDays = computed(() => focus.stats?.recentDays || [])

const recentLabels = computed(() => recentDays.value.map(item => `${Number(item.date.slice(5, 7))}/${Number(item.date.slice(8, 10))}`))

const pomodoroTrendOption = computed(() => lineOption(recentLabels.value, recentDays.value.map(item => item.count), '番茄数'))
const focusMinutesTrendOption = computed(() => barOption(recentLabels.value, recentDays.value.map(item => item.minutes), '专注分钟'))
const doneTrendOption = computed(() => {
  const labels = []
  const values = []
  for (let offset = 6; offset >= 0; offset -= 1) {
    const date = new Date()
    date.setDate(date.getDate() - offset)
    labels.push(`${date.getMonth() + 1}/${date.getDate()}`)
    values.push(allTasks.value.filter(task => task.completedAt && keyOf(new Date(task.completedAt)) === keyOf(date)).length)
  }
  return lineOption(labels, values, '完成数')
})

const weekProgress = computed(() => {
  const monday = new Date()
  const offset = (monday.getDay() + 6) % 7
  monday.setDate(monday.getDate() - offset)
  const labels = ['一', '二', '三', '四', '五', '六', '日']
  const rows = labels.map((label, index) => {
    const date = new Date(monday.getFullYear(), monday.getMonth(), monday.getDate() + index)
    const minutes = focus.sessions
      .filter(session => session.status === 'COMPLETED' && keyOf(new Date(session.startedAt)) === keyOf(date))
      .reduce((sum, session) => sum + Number(session.actualMinutes || 0), 0)
    return { key: keyOf(date), label, minutes }
  })
  const max = Math.max(1, ...rows.map(row => row.minutes))
  return rows.map(row => ({ ...row, ratio: row.minutes ? Math.max(8, Math.round((row.minutes / max) * 100)) : 0 }))
})

const ratePieOption = computed(() => {
  const done = dayStats.value.done
  const overdue = dayStats.value.overdue
  const rest = Math.max(0, totals.value.tasks - done - overdue)
  return {
    tooltip: { trigger: 'item' },
    legend: { bottom: 0, textStyle: { color: '#8b93a3' } },
    series: [{
      type: 'pie',
      radius: ['52%', '72%'],
      center: ['50%', '44%'],
      label: { show: false },
      data: [
        { name: '已完成', value: done, itemStyle: { color: '#4d78db' } },
        { name: '今日逾期未完成', value: overdue, itemStyle: { color: '#e34d59' } },
        { name: '未完成', value: rest, itemStyle: { color: '#c7ccd6' } }
      ]
    }]
  }
})

const listPieOption = computed(() => {
  const day = dayKey.value
  const completed = allTasks.value.filter(task => task.completedAt && keyOf(new Date(task.completedAt)) === day)
  if (!completed.length) return null
  const counts = new Map()
  for (const task of completed) {
    const list = tasksStore.lists.find(item => item.publicId === task.listId)
    const name = list?.name || '收集箱'
    counts.set(name, (counts.get(name) || 0) + 1)
  }
  return {
    tooltip: { trigger: 'item' },
    legend: { bottom: 0, textStyle: { color: '#8b93a3' } },
    series: [{
      type: 'pie',
      radius: ['46%', '70%'],
      center: ['50%', '44%'],
      label: { show: false },
      data: [...counts.entries()].map(([name, value]) => ({ name, value }))
    }]
  }
})

const weekFocusOption = computed(() => {
  const labels = weekProgress.value.map(row => row.label)
  const values = weekProgress.value.map(row => row.minutes)
  return barOption(labels, values, '专注分钟')
})

function axisTheme() {
  return {
    axisLine: { lineStyle: { color: '#dfe3ea' } },
    axisLabel: { color: '#8b93a3', fontSize: 11 },
    splitLine: { lineStyle: { color: 'rgba(148,163,184,.18)' } }
  }
}

function lineOption(labels, values, name) {
  return {
    tooltip: { trigger: 'axis' },
    grid: { left: 40, right: 16, top: 18, bottom: 28 },
    xAxis: { type: 'category', data: labels, boundaryGap: false, ...axisTheme() },
    yAxis: { type: 'value', minInterval: 1, ...axisTheme() },
    series: [{
      name,
      type: 'line',
      smooth: true,
      symbolSize: 5,
      data: values,
      itemStyle: { color: '#4d78db' },
      lineStyle: { width: 2, color: '#4d78db' },
      areaStyle: { color: 'rgba(77,120,219,.16)' }
    }]
  }
}

function barOption(labels, values, name) {
  return {
    tooltip: { trigger: 'axis' },
    grid: { left: 40, right: 16, top: 18, bottom: 28 },
    xAxis: { type: 'category', data: labels, ...axisTheme() },
    yAxis: { type: 'value', minInterval: 1, ...axisTheme() },
    series: [{
      name,
      type: 'bar',
      data: values,
      barMaxWidth: 18,
      itemStyle: { color: '#4d78db', borderRadius: [4, 4, 0, 0] }
    }]
  }
}

function keyOf(date) {
  return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')}`
}

function duration(minutes) {
  const value = Number(minutes || 0)
  const hours = Math.floor(value / 60)
  const rest = value % 60
  return hours ? `${hours}h${rest ? `${rest}m` : ''}` : `${rest}m`
}

function shortMinutes(minutes) {
  return duration(minutes)
}

function formatDate(value) {
  const date = new Date(value)
  return `${date.getMonth() + 1}/${date.getDate()} ${String(date.getHours()).padStart(2, '0')}:${String(date.getMinutes()).padStart(2, '0')}`
}

function shiftDay(offset) {
  const date = new Date(selectedDay.value)
  date.setDate(date.getDate() + offset)
  if (date > new Date()) return
  selectedDay.value = date
}

onMounted(async () => {
  await tasksStore.init({ waitForRemote: false })
  await focus.refresh()
})
</script>

<style scoped>
.focus-stats { display: grid; gap: 14px; padding: 18px 22px 60px; color: var(--ink); }
.focus-stats-head { display: flex; align-items: center; gap: 16px; }
.focus-stats-head h1 { margin: 0; font-size: 1.16rem; font-weight: 700; }
.focus-stats-tabs { display: flex; gap: 2px; margin: 0 auto; padding: 3px; border-radius: 9px; background: var(--paper); }
.focus-stats-tabs button { min-height: 26px; padding: 0 14px; border: 0; border-radius: 7px; background: transparent; color: var(--muted); font: inherit; font-size: 12.5px; cursor: pointer; }
.focus-stats-tabs button.on { background: var(--card); color: var(--ink); box-shadow: 0 1px 3px rgb(15 23 42 / 12%); }

.fs-strip { display: flex; align-items: center; gap: 18px; padding: 12px 16px; border: 1px solid var(--line); border-radius: 10px; background: var(--paper); color: var(--ink2); font-size: 12.5px; }
.fs-strip b { font-size: 1rem; }
.fs-strip em { margin-left: auto; color: var(--muted); font-style: normal; }

.fs-card { padding: 14px 16px; border: 1px solid var(--line); border-radius: 12px; background: var(--card); }
.fs-card h2 { margin: 0 0 10px; font-size: 13px; font-weight: 700; }
.fs-numbers { display: grid; grid-template-columns: repeat(3, 1fr); gap: 12px; }
.fs-numbers.is-two { grid-template-columns: repeat(2, 1fr); }
.fs-numbers > div { display: grid; gap: 4px; }
.fs-numbers b { color: var(--accent); font-size: 1.4rem; font-weight: 700; }
.fs-numbers span { color: var(--muted); font-size: 11.5px; }

.fs-toolbar { display: flex; align-items: center; gap: 8px; }
.fs-toolbar button { display: grid; width: 26px; height: 26px; place-items: center; border: 1px solid var(--line2); border-radius: 7px; background: transparent; color: var(--ink2); cursor: pointer; }
.fs-toolbar strong { font-size: 12.5px; }

.fs-week { display: grid; grid-template-columns: repeat(7, 1fr); gap: 10px; }
.fs-week > div { display: grid; justify-items: center; gap: 6px; color: var(--muted); font-size: 11.5px; }
.fs-week i { display: flex; align-items: flex-end; width: 100%; height: 60px; border-radius: 6px; background: color-mix(in srgb, var(--ink) 6%, transparent); }
.fs-week i b { width: 100%; border-radius: 6px; background: color-mix(in srgb, var(--accent) 60%, transparent); }
.fs-week > div.on em { color: var(--accent); }
.fs-week em { font-style: normal; font-size: 10.5px; }

.fs-records { display: grid; gap: 2px; margin: 0; padding: 0; list-style: none; }
.fs-records li { display: flex; align-items: center; gap: 12px; padding: 7px 4px; border-bottom: 1px solid var(--line); font-size: 12.5px; }
.fs-records li span { flex: 0 0 92px; color: var(--muted); font-variant-numeric: tabular-nums; }
.fs-records li b { flex: 1; min-width: 0; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; font-weight: 500; }
.fs-records li em { color: var(--ink2); font-style: normal; }
.fs-empty { margin: 0; padding: 18px 0; color: var(--muted); font-size: 12.5px; }
.focus-stats-error { margin: 0; padding: 8px 10px; border-left: 3px solid var(--down, #e34d59); background: color-mix(in srgb, var(--down, #e34d59) 10%, transparent); font-size: 12.5px; }

@media (min-width: 1080px) {
  .focus-stats { grid-template-columns: 1fr 1fr; }
  .focus-stats-head, .fs-strip, .focus-stats-error, .fs-card.is-wide, .fs-toolbar { grid-column: 1 / -1; }
}
</style>
