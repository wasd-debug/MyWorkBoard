<template>
  <main class="ts-page">
    <header class="ts-head">
      <h1>统计</h1>
      <div class="ts-tabs" role="tablist" aria-label="统计范围">
        <button v-for="item in tabs" :key="item.key" type="button" role="tab"
                :class="{ on: tab === item.key }" :aria-selected="tab === item.key"
                @click="switchTab(item.key)">{{ item.label }}</button>
      </div>
      <button class="ts-done" type="button" @click="close">完成</button>
    </header>

    <p v-if="error" class="ts-error" role="alert">{{ error }}</p>

    <!-- ------------------------------------------------------------ 总览 -->
    <template v-if="tab === 'OVERVIEW'">
      <div class="ts-loading" v-if="!overview">正在统计…</div>
      <template v-else>
        <section class="ts-summary">
          <span><b>{{ num(overview.totalTasks) }}</b>任务</span>
          <span><b>{{ num(overview.completedTasks) }}</b>已完成</span>
          <span><b>{{ num(overview.totalLists) }}</b>清单</span>
          <span><b>{{ num(overview.useDays) }}</b>使用天数</span>
          <em>你比 {{ overview.percentile }}% 的用户更勤奋</em>
        </section>

        <div class="ts-grid">
          <section class="ts-card">
            <h2>概览</h2>
            <div class="ts-kpis is-3">
              <div><b>{{ num(overview.todayCompleted) }}</b><span>今日已完成</span></div>
              <div><b>{{ num(overview.todayPomodoro) }}</b><span>今日番茄</span></div>
              <div><b class="is-long">{{ duration(overview.todayFocusMinutes) }}</b><span>今日专注时长</span></div>
              <div><b>{{ num(overview.allCompleted) }}</b><span>总已完成</span></div>
              <div><b>{{ num(overview.allPomodoro) }}</b><span>总番茄</span></div>
              <div><b class="is-long">{{ duration(overview.allFocusMinutes) }}</b><span>总专注时长</span></div>
            </div>
          </section>

          <section class="ts-card">
            <h2 class="ts-card-head-line">
              <span>我的成就值</span>
              <span class="ts-achv">{{ num(overview.achievement) }}<i>🔥</i></span>
            </h2>
            <ReportChart :option="achievementOption" aria-label="我的成就值趋势" height="188px" :show-table="false" />
          </section>

          <section class="ts-card">
            <h2 class="ts-card-head-line">
              <span>最近已完成趋势</span>
              <span class="ts-pill"><CalendarIcon :size="12" />日</span>
            </h2>
            <ReportChart v-if="nonZero(overview.completedTrend)" :option="completedOption" aria-label="最近已完成趋势" height="168px" :show-table="false" />
            <p v-else class="ts-chart-empty">暂无数据</p>
          </section>

          <section class="ts-card">
            <h2 class="ts-card-head-line">
              <span>最近完成率趋势</span>
              <span class="ts-pill"><CalendarIcon :size="12" />日</span>
            </h2>
            <ReportChart v-if="nonZero(overview.rateTrend)" :option="rateOption" aria-label="最近完成率趋势" height="168px" :show-table="false" />
            <p v-else class="ts-chart-empty">暂无数据</p>
          </section>

          <section class="ts-card">
            <h2 class="ts-card-head-line">
              <span>最近番茄趋势</span>
              <span class="ts-pill"><CalendarIcon :size="12" />日</span>
            </h2>
            <ReportChart v-if="nonZero(overview.pomodoroTrend)" :option="pomodoroOption" aria-label="最近番茄趋势" height="168px" :show-table="false" />
            <p v-else class="ts-chart-empty">暂无数据</p>
          </section>

          <section class="ts-card">
            <h2 class="ts-card-head-line">
              <span>最近专注时长趋势</span>
              <span class="ts-pill"><CalendarIcon :size="12" />日</span>
            </h2>
            <ReportChart v-if="nonZero(overview.focusTrend)" :option="focusTrendOption" aria-label="最近专注时长趋势" height="168px" :show-table="false" />
            <p v-else class="ts-chart-empty">暂无数据</p>
          </section>

          <section class="ts-card is-full">
            <h2>本周打卡进展</h2>
            <div class="ts-rings">
              <div v-for="ring in overview.weekRings" :key="ring.date" class="ts-ring" :class="{ today: ring.today }">
                <svg viewBox="0 0 40 40" aria-hidden="true">
                  <circle cx="20" cy="20" r="17" class="track" />
                  <circle v-if="ring.percent > 0" cx="20" cy="20" r="17" class="value"
                          :style="{ strokeDasharray: `${ring.percent * 1.068} 200` }" />
                </svg>
                <small>{{ ring.label }}</small>
              </div>
            </div>
          </section>
        </div>
      </template>
    </template>

    <!-- ------------------------------------------------------------ 任务 -->
    <template v-else-if="tab === 'TASK'">
      <div class="ts-toolbar">
        <div class="ts-seg">
          <button v-for="item in taskModes" :key="item.key" type="button"
                  :class="{ on: taskMode === item.key }" @click="setTaskMode(item.key)">{{ item.label }}</button>
        </div>
        <div class="ts-stepper">
          <button type="button" aria-label="上一段" @click="shiftTask(-1)"><ChevronLeft :size="14" /></button>
          <strong>{{ task?.label || '今天' }}</strong>
          <button type="button" aria-label="下一段" @click="shiftTask(1)"><ChevronRight :size="14" /></button>
        </div>
      </div>

      <div class="ts-loading" v-if="!task">正在统计…</div>
      <div v-else class="ts-grid">
        <section class="ts-card is-full">
          <h2>概览</h2>
          <div class="ts-kpis is-2">
            <div>
              <b>{{ num(task.completed) }}</b><span>完成数</span>
              <em :class="deltaClass(task.completed - task.prevCompleted)">
                {{ deltaText(task.completed - task.prevCompleted, '个') }}
              </em>
            </div>
            <div>
              <b>{{ task.rate }}%</b><span>完成率</span>
              <em :class="deltaClass(task.rate - task.prevRate)">
                {{ deltaText(task.rate - task.prevRate, '%') }}
              </em>
            </div>
          </div>
        </section>

        <section class="ts-card is-center">
          <h2>完成率分布</h2>
          <div class="ts-donut-row">
            <div class="ts-donut">
              <ReportChart :option="rateDonutOption" aria-label="完成率分布" height="210px" :show-table="false" />
              <div class="ts-donut-center">
                <b>{{ task.rate }}%</b><span>完成率</span>
              </div>
            </div>
            <ul class="ts-legend">
              <li><i style="background:#e5678a" />逾期完成<em>{{ num(task.overdueCompleted) }}</em></li>
              <li><i style="background:#4d78db" />按时完成<em>{{ num(task.onTimeCompleted) }}</em></li>
              <li><i style="background:#f0c94a" />无日期任务<em>{{ num(task.noDueCompleted) }}</em></li>
              <li><i style="background:#c9ced8" />未完成<em>{{ num(task.undone) }}</em></li>
            </ul>
          </div>
        </section>

        <section class="ts-card is-center">
          <h2 class="ts-card-head-line">
            <span>已完成分类统计</span>
            <span class="ts-pill is-select">{{ groupLabel(task.groupMode) }}<ChevronDown :size="12" /></span>
          </h2>
          <div v-if="!task.groups.length" class="ts-donut-row is-empty">
            <div class="ts-donut">
              <ReportChart :option="EMPTY_DONUT" aria-label="已完成分类统计" height="210px" :show-table="false" />
              <div class="ts-donut-center">
                <b>0</b><span>完成数量</span>
              </div>
            </div>
            <ul class="ts-legend"><li class="is-empty">暂无数据</li></ul>
          </div>
          <div v-else class="ts-donut-row">
            <div class="ts-donut">
              <ReportChart :option="groupDonutOption" aria-label="已完成分类统计" height="210px" :show-table="false" />
              <div class="ts-donut-center">
                <b>{{ num(task.completed) }}</b><span>完成数量</span>
              </div>
            </div>
            <ul class="ts-legend">
              <li v-for="slice in task.groups" :key="slice.id + slice.name">
                <i :style="{ background: slice.color }" />{{ slice.name }}<em>{{ num(slice.value) }}</em>
              </li>
            </ul>
          </div>
        </section>
      </div>
    </template>

    <!-- ------------------------------------------------------------ 专注 -->
    <template v-else>
      <div class="ts-loading" v-if="!focusPage">正在统计…</div>
      <template v-else>
        <section class="ts-card is-full">
          <h2>概览</h2>
          <div class="ts-kpis is-4">
            <div><b>{{ num(focusPage.todayPomodoro) }}</b><span>今日番茄</span></div>
            <div><b>{{ num(focusPage.totalPomodoro) }}</b><span>总番茄</span></div>
            <div><b class="is-long">{{ duration(focusPage.todayMinutes) }}</b><span>今日专注时长</span></div>
            <div><b class="is-long">{{ duration(focusPage.totalMinutes) }}</b><span>总专注时长</span></div>
          </div>
        </section>

        <div class="ts-grid">
          <section class="ts-card is-center">
            <h2 class="ts-card-head-line">
              <span>专注详情</span>
              <span class="ts-controls">
                <span class="ts-pill is-select">{{ groupLabel(focusPage.groupMode) }}<ChevronDown :size="12" /></span>
                <span class="ts-pill is-select">日<ChevronDown :size="12" /></span>
                <span class="ts-stepper is-mini">
                  <button type="button" aria-label="前一天" @click="shiftDetail(-1)"><ChevronLeft :size="12" /></button>
                  <strong>{{ focusPage.detailLabel }}</strong>
                  <button type="button" aria-label="后一天" @click="shiftDetail(1)"><ChevronRight :size="12" /></button>
                </span>
              </span>
            </h2>
            <div v-if="!focusPage.groups.length" class="ts-donut-row is-empty">
              <div class="ts-donut">
                <ReportChart :option="EMPTY_DONUT" aria-label="专注详情" height="210px" :show-table="false" />
                <div class="ts-donut-center">
                  <b>0</b><span>专注次数</span>
                </div>
              </div>
              <ul class="ts-legend"><li class="is-empty">暂无数据</li></ul>
            </div>
            <div v-else class="ts-donut-row">
              <div class="ts-donut">
                <ReportChart :option="focusDonutOption" aria-label="专注详情" height="210px" :show-table="false" />
                <div class="ts-donut-center">
                  <b>{{ num(focusTotal) }}</b><span>专注次数</span>
                </div>
              </div>
              <ul class="ts-legend">
                <li v-for="slice in focusPage.groups" :key="slice.id + slice.name">
                  <i :style="{ background: slice.color }" />{{ slice.name }}<em>{{ num(slice.value) }}</em>
                </li>
              </ul>
            </div>
          </section>

          <section class="ts-card">
            <h2 class="ts-card-head-line">
              <span>专注记录</span>
              <span class="ts-icon-actions">
                <button type="button" aria-label="新增专注记录" title="新增专注记录" @click="goFocus"><Plus :size="14" /></button>
                <button type="button" aria-label="更多" title="更多" @click="goFocus"><MoreHorizontal :size="14" /></button>
              </span>
            </h2>
            <p v-if="!recordGroups.length" class="ts-empty">暂无专注记录</p>
            <ul v-else class="ts-records">
              <template v-for="group in recordGroups" :key="group.date">
                <li class="ts-rec-date">{{ group.label }}</li>
                <li v-for="record in group.items" :key="record.publicId">
                  <span class="ts-rec-time">{{ record.startTime }} - {{ record.endTime }}</span>
                  <span class="ts-rec-dot" :class="record.targetType === 'HABIT' ? 'is-habit' : ''"></span>
                  <b>{{ record.title }}</b>
                  <em>{{ duration(record.minutes) }}</em>
                </li>
              </template>
            </ul>
          </section>

          <section class="ts-card is-fill">
            <h2 class="ts-card-head-line">
              <span>专注趋势</span>
              <span class="ts-controls">
                <span class="ts-pill is-select">{{ trendMode === 'MONTH' ? '月' : '周' }}<ChevronDown :size="12" /></span>
                <span class="ts-stepper is-mini">
                  <button type="button" aria-label="上一段" @click="shiftTrend(-1)"><ChevronLeft :size="12" /></button>
                  <strong>{{ focusPage.trendLabel }}</strong>
                  <button type="button" aria-label="下一段" @click="shiftTrend(1)"><ChevronRight :size="12" /></button>
                </span>
              </span>
            </h2>
            <p class="ts-sub">每日平均：{{ duration(focusAverage) }}</p>
            <div class="ts-chart-fill">
              <ReportChart v-if="nonZero(focusPage.trend)" :option="focusTrendChart" aria-label="专注趋势" height="100%" :show-table="false" />
              <p v-else class="ts-chart-empty">暂无数据</p>
            </div>
          </section>

          <section class="ts-card">
            <h2 class="ts-card-head-line">
              <span>专注时间线</span>
              <span class="ts-stepper is-mini">
                <button type="button" aria-label="上一周" @click="shiftTimeline(-1)"><ChevronLeft :size="12" /></button>
                <strong>{{ focusPage.timelineLabel }}</strong>
                <button type="button" aria-label="下一周" @click="shiftTimeline(1)"><ChevronRight :size="12" /></button>
              </span>
            </h2>
            <div class="ts-timeline">
              <div v-for="hour in 24" :key="hour" class="ts-timeline-row">
                <small>{{ hourLabel(hour - 1) }}</small>
                <i v-for="day in focusPage.timeline" :key="day.date"
                   :style="{ background: heatColor(day.hours[hour - 1]) }"
                   :title="`${day.date} ${hourLabel(hour - 1)} · ${duration(day.hours[hour - 1])}`"></i>
              </div>
              <div class="ts-timeline-days">
                <small></small>
                <b v-for="day in focusPage.timeline" :key="day.date" :class="{ today: day.today }">{{ day.label }}</b>
              </div>
            </div>
          </section>

          <section class="ts-card is-full">
            <h2 class="ts-card-head-line">
              <span>最佳专注时间</span>
              <span class="ts-stepper is-mini">
                <button type="button" aria-label="上一月" @click="shiftMonth(-1)"><ChevronLeft :size="12" /></button>
                <strong>{{ focusPage.monthLabel }}</strong>
                <button type="button" aria-label="下一月" @click="shiftMonth(1)"><ChevronRight :size="12" /></button>
              </span>
            </h2>
            <ReportChart v-if="nonZero(focusPage.bestHours)" :option="bestHoursOption" aria-label="最佳专注时间" height="172px" :show-table="false" />
            <p v-else class="ts-chart-empty is-tall">暂无数据</p>
          </section>

          <section class="ts-card is-full">
            <h2 class="ts-card-head-line">
              <span>年度热力图</span>
              <span class="ts-stepper is-mini">
                <button type="button" aria-label="上一年" @click="shiftYear(-1)"><ChevronLeft :size="12" /></button>
                <strong>{{ focusPage.year }}</strong>
                <button type="button" aria-label="下一年" @click="shiftYear(1)"><ChevronRight :size="12" /></button>
              </span>
            </h2>
            <div class="ts-heat">
              <div class="ts-heat-months" :style="{ gridTemplateColumns: `repeat(${heatWeeks.length || 1}, 13px)` }">
                <b v-for="cell in heatMonths" :key="cell.column" :style="{ gridColumn: cell.column + 1 }">{{ cell.label }}</b>
              </div>
              <div class="ts-heat-body">
                <div v-for="(week, index) in heatWeeks" :key="index" class="ts-heat-week">
                  <i v-for="cell in week" :key="cell.date || index + '-' + cell.minutes"
                     :class="{ blank: !cell.date }"
                     :style="cell.date ? { background: heatColor(cell.minutes) } : {}"
                     :title="cell.date ? `${cell.date} · ${duration(cell.minutes)}` : ''"></i>
                </div>
              </div>
              <div class="ts-heat-legend">
                <span>0m</span><i style="background:var(--line)" /><span>0-1h</span><i style="background:#cddffb" />
                <span>1-3h</span><i style="background:#9dc0f5" /><span>3-5h</span><i style="background:#6b99e8" />
                <span>5h+</span><i style="background:#3f6fd1" />
              </div>
            </div>
          </section>
        </div>
      </template>
    </template>
  </main>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Calendar as CalendarIcon, ChevronDown, ChevronLeft, ChevronRight, MoreHorizontal, Plus } from 'lucide-vue-next'
import ReportChart from '../components/ledger/LedgerReportChart.vue'
import { apiGetStatsOverview, apiGetStatsTaskPage, apiGetStatsFocusPage } from '../../packages/api-client/src/index.js'

const router = useRouter()
const route = useRoute()
const tabs = [
  { key: 'OVERVIEW', label: '总览' },
  { key: 'TASK', label: '任务' },
  { key: 'FOCUS', label: '专注' }
]
const taskModes = [
  { key: 'DAY', label: '按日' },
  { key: 'WEEK', label: '按周' },
  { key: 'MONTH', label: '按月' }
]
const initialTab = String(route.query.tab || 'OVERVIEW').toUpperCase()
const tab = ref(['OVERVIEW', 'TASK', 'FOCUS'].includes(initialTab) ? initialTab : 'OVERVIEW')
const error = ref('')
const overview = ref(null)
const task = ref(null)
const focusPage = ref(null)
const taskMode = ref('DAY')
const taskDate = ref(todayKey())
const focusDetailDate = ref(todayKey())
const trendMode = ref('WEEK')
const trendDate = ref(todayKey())
const timelineDate = ref(todayKey())
const monthDate = ref(todayKey())
const yearValue = ref(String(new Date().getFullYear()))

const BLUE = '#4d78db'

// 环形图无数据时的占位：画一整圈浅灰，避免空白画布看起来像渲染失败
const EMPTY_DONUT = {
  tooltip: { show: false },
  series: [{
    type: 'pie', radius: ['62%', '82%'], center: ['50%', '50%'], silent: true,
    label: { show: false }, labelLine: { show: false },
    data: [{ name: '暂无数据', value: 1, itemStyle: { color: '$line' } }]
  }]
}

function withPlaceholder(list) {
  const positive = list.filter(item => item.value > 0)
  return positive.length ? positive : [{ name: '暂无数据', value: 1, itemStyle: { color: '$line' } }]
}

onMounted(() => {
  if (tab.value === 'TASK') void loadTask()
  else if (tab.value === 'FOCUS') void loadFocus()
  else void loadOverview()
})

async function loadOverview() {
  error.value = ''
  try {
    overview.value = await apiGetStatsOverview()
  } catch (e) {
    error.value = message(e)
  }
}

async function loadTask() {
  error.value = ''
  try {
    task.value = await apiGetStatsTaskPage({ mode: taskMode.value, date: taskDate.value, groupMode: 'LIST' })
  } catch (e) {
    error.value = message(e)
  }
}

async function loadFocus() {
  error.value = ''
  try {
    focusPage.value = await apiGetStatsFocusPage({
      date: focusDetailDate.value,
      groupMode: 'LIST',
      trendMode: trendMode.value,
      trendDate: trendDate.value,
      timelineDate: timelineDate.value,
      month: monthDate.value,
      year: yearValue.value
    })
  } catch (e) {
    error.value = message(e)
  }
}

function switchTab(key) {
  tab.value = key
  // 把页签写进地址栏，刷新后还停在同一个页签
  if (route.query.tab !== key) router.replace({ query: { ...route.query, tab: key } })
  if (key === 'OVERVIEW' && !overview.value) void loadOverview()
  if (key === 'TASK' && !task.value) void loadTask()
  if (key === 'FOCUS' && !focusPage.value) void loadFocus()
}

function setTaskMode(key) {
  taskMode.value = key
  task.value = null
  void loadTask()
}

function shiftTask(offset) {
  taskDate.value = shiftKey(taskDate.value, taskMode.value === 'MONTH' ? offset * 30 : offset * (taskMode.value === 'WEEK' ? 7 : 1))
  task.value = null
  void loadTask()
}

function shiftDetail(offset) {
  focusDetailDate.value = shiftKey(focusDetailDate.value, offset)
  void loadFocus()
}

function shiftTrend(offset) {
  trendDate.value = shiftKey(trendDate.value, trendMode.value === 'MONTH' ? offset * 30 : offset * 7)
  void loadFocus()
}

function shiftTimeline(offset) {
  timelineDate.value = shiftKey(timelineDate.value, offset * 7)
  void loadFocus()
}

function shiftMonth(offset) {
  monthDate.value = shiftMonthKey(monthDate.value, offset)
  void loadFocus()
}

function shiftYear(offset) {
  yearValue.value = String(Number(yearValue.value) + offset)
  void loadFocus()
}

function close() {
  if (window.history.length > 1) router.back()
  else router.push('/tasks/today')
}

function goFocus() {
  router.push('/tasks/focus')
}

// ---------------------------------------------------------------- 图表配置

function axisLabels(list) {
  return list.map(point => point.label || '')
}

function smoothLine(labels, values, { area = true, integer = true, name = '' } = {}) {
  return {
    tooltip: { trigger: 'axis' },
    grid: { left: 34, right: 12, top: 14, bottom: 24 },
    xAxis: {
      type: 'category', data: labels, boundaryGap: false,
      axisLine: { lineStyle: { color: '$line' } },
      axisTick: { show: false },
      axisLabel: { color: '$muted', fontSize: 11, margin: 10 }
    },
    yAxis: {
      type: 'value', minInterval: integer ? 1 : undefined, splitNumber: 4,
      axisLine: { show: false }, axisTick: { show: false },
      axisLabel: { color: '$muted', fontSize: 11 },
      splitLine: { lineStyle: { color: '$line', type: 'dashed' } }
    },
    series: [{
      name, type: 'line', smooth: true, showSymbol: true, symbolSize: 4,
      data: values,
      lineStyle: { width: 2, color: BLUE },
      itemStyle: { color: BLUE, borderWidth: 2, borderColor: '#fff' },
      areaStyle: area ? { color: 'rgba(77,120,219,.14)' } : undefined
    }]
  }
}

const achievementOption = computed(() => {
  const list = overview.value?.achievementTrend || []
  const values = list.map(point => point.value)
  const min = values.length ? Math.min(...values) : 0
  const max = values.length ? Math.max(...values) : 0
  // 成就值基数很大（几万），直接按 min/max 取轴会得到 82,829.5 这种刻度，先把步长取整
  const step = niceStep(max - min)
  const lo = Math.floor(min / step) * step
  let hi = Math.ceil(max / step) * step
  if (hi <= lo) hi = lo + step * 4
  else while ((hi - lo) / step < 2) hi += step
  return {
    tooltip: { trigger: 'axis' },
    grid: { left: 56, right: 14, top: 14, bottom: 24 },
    xAxis: {
      type: 'category', data: axisLabels(list), boundaryGap: false,
      axisLine: { lineStyle: { color: '$line' } }, axisTick: { show: false },
      axisLabel: { color: '$muted', fontSize: 11, margin: 10 }
    },
    yAxis: {
      type: 'value', min: lo, max: hi, interval: step, splitNumber: 4,
      axisLine: { show: false }, axisTick: { show: false },
      axisLabel: { color: '$muted', fontSize: 10, formatter: value => num(value) },
      splitLine: { lineStyle: { color: '$line', type: 'dashed' } }
    },
    series: [{
      name: '成就值', type: 'line', smooth: true, showSymbol: true, symbolSize: 4,
      data: values,
      lineStyle: { width: 2, color: BLUE },
      itemStyle: { color: BLUE, borderWidth: 2, borderColor: '#fff' },
      areaStyle: { color: 'rgba(77,120,219,.16)' }
    }]
  }
})

const completedOption = computed(() => {
  const list = overview.value?.completedTrend || []
  return smoothLine(axisLabels(list), list.map(point => point.value), { name: '完成数' })
})

const pomodoroOption = computed(() => {
  const list = overview.value?.pomodoroTrend || []
  return smoothLine(axisLabels(list), list.map(point => point.value), { name: '番茄数' })
})

const focusTrendOption = computed(() => {
  const list = overview.value?.focusTrend || []
  const values = list.map(point => point.value)
  const axis = minuteAxis(values)
  return {
    ...smoothLine(axisLabels(list), values, { name: '专注分钟' }),
    yAxis: {
      type: 'value', min: 0, max: axis.max, interval: axis.interval,
      axisLine: { show: false }, axisTick: { show: false },
      axisLabel: { color: '$muted', fontSize: 11, formatter: axisMinutes },
      splitLine: { lineStyle: { color: '$line', type: 'dashed' } }
    }
  }
})

const rateOption = computed(() => {
  const list = overview.value?.rateTrend || []
  return {
    tooltip: { trigger: 'axis', valueFormatter: value => `${value}%` },
    grid: { left: 40, right: 14, top: 14, bottom: 24 },
    xAxis: {
      type: 'category', data: axisLabels(list), boundaryGap: false,
      axisLine: { lineStyle: { color: '$line' } }, axisTick: { show: false },
      axisLabel: { color: '$muted', fontSize: 11, margin: 10 }
    },
    yAxis: {
      type: 'value', min: 0, max: 100, interval: 25,
      axisLine: { show: false }, axisTick: { show: false },
      axisLabel: { color: '$muted', fontSize: 11, formatter: value => `${value}%` },
      splitLine: { lineStyle: { color: '$line', type: 'dashed' } }
    },
    series: [{
      name: '完成率', type: 'line', smooth: true, showSymbol: true, symbolSize: 4,
      data: list.map(point => point.value),
      lineStyle: { width: 2, color: BLUE },
      itemStyle: { color: BLUE, borderWidth: 2, borderColor: '#fff' },
      areaStyle: { color: 'rgba(77,120,219,.14)' }
    }]
  }
})

const rateDonutOption = computed(() => {
  const data = task.value
  if (!data) return EMPTY_DONUT
  return {
    tooltip: { trigger: 'item', valueFormatter: value => `${value}` },
    series: [{
      type: 'pie', radius: ['62%', '82%'], center: ['50%', '50%'],
      avoidLabelOverlap: true, label: { show: false }, labelLine: { show: false },
      itemStyle: { borderColor: '#fff', borderWidth: 2 },
      data: withPlaceholder([
        { name: '逾期完成', value: data.overdueCompleted, itemStyle: { color: '#e5678a' } },
        { name: '按时完成', value: data.onTimeCompleted, itemStyle: { color: BLUE } },
        { name: '无日期任务', value: data.noDueCompleted, itemStyle: { color: '#f0c94a' } },
        { name: '未完成', value: data.undone, itemStyle: { color: '#c9ced8' } }
      ])
    }]
  }
})

const groupDonutOption = computed(() => {
  const groups = task.value?.groups || []
  return {
    tooltip: { trigger: 'item' },
    series: [{
      type: 'pie', radius: ['62%', '82%'], center: ['50%', '50%'],
      label: { show: false }, labelLine: { show: false },
      itemStyle: { borderColor: '#fff', borderWidth: 2 },
      data: groups.map(slice => ({ name: slice.name, value: slice.value, itemStyle: { color: slice.color } }))
    }]
  }
})

const focusDonutOption = computed(() => {
  const groups = focusPage.value?.groups || []
  return {
    tooltip: { trigger: 'item' },
    series: [{
      type: 'pie', radius: ['62%', '82%'], center: ['50%', '50%'],
      label: { show: false }, labelLine: { show: false },
      itemStyle: { borderColor: '#fff', borderWidth: 2 },
      data: groups.map(slice => ({ name: slice.name, value: slice.value, itemStyle: { color: slice.color } }))
    }]
  }
})

const focusTrendChart = computed(() => {
  const list = focusPage.value?.trend || []
  const values = list.map(point => point.value)
  const axis = minuteAxis(values)
  return {
    ...smoothLine(axisLabels(list), values, { name: '专注分钟' }),
    yAxis: {
      type: 'value', min: 0, max: axis.max, interval: axis.interval,
      axisLine: { show: false }, axisTick: { show: false },
      axisLabel: { color: '$muted', fontSize: 11, formatter: axisMinutes },
      splitLine: { lineStyle: { color: '$line', type: 'dashed' } }
    }
  }
})

const bestHoursOption = computed(() => {
  const list = focusPage.value?.bestHours || []
  const axis = minuteAxis(list.map(point => point.value))
  return {
    tooltip: { trigger: 'axis', valueFormatter: value => duration(value) },
    grid: { left: 34, right: 10, top: 14, bottom: 24 },
    xAxis: {
      type: 'category', data: axisLabels(list),
      axisLine: { lineStyle: { color: '$line' } }, axisTick: { show: false },
      axisLabel: {
        color: '$muted', fontSize: 10, interval: 3,
        formatter: value => String(value).slice(0, 2)
      }
    },
    yAxis: {
      type: 'value', min: 0, max: axis.max, interval: axis.interval,
      axisLine: { show: false }, axisTick: { show: false },
      axisLabel: { color: '$muted', fontSize: 10, formatter: axisMinutes },
      splitLine: { lineStyle: { color: '$line', type: 'dashed' } }
    },
    series: [{
      name: '专注时长', type: 'bar', barWidth: 10,
      data: list.map(point => point.value),
      itemStyle: { color: '#c9d9f5', borderRadius: [2, 2, 0, 0] }
    }]
  }
})

// ---------------------------------------------------------------- 派生数据

const focusTotal = computed(() => (focusPage.value?.groups || []).reduce((sum, slice) => sum + slice.value, 0))
const focusAverage = computed(() => {
  const trend = focusPage.value?.trend || []
  if (!trend.length) return 0
  const total = trend.reduce((sum, point) => sum + point.value, 0)
  return Math.round(total / trend.length)
})
const recordGroups = computed(() => {
  const groups = new Map()
  for (const record of focusPage.value?.records || []) {
    const key = record.date || ''
    if (!groups.has(key)) groups.set(key, [])
    groups.get(key).push(record)
  }
  return [...groups.entries()].map(([date, items]) => ({ date, label: recordDateLabel(date), items }))
})

const heatLead = computed(() => {
  const days = focusPage.value?.heatmap || []
  if (!days.length) return 0
  const first = new Date(`${days[0].date}T00:00:00`)
  return (first.getDay() + 6) % 7
})

const heatWeeks = computed(() => {
  const days = focusPage.value?.heatmap || []
  if (!days.length) return []
  const weeks = []
  let current = []
  const lead = heatLead.value
  for (let i = 0; i < lead; i += 1) current.push({ date: '', minutes: 0 })
  for (const day of days) {
    current.push({ date: day.date, minutes: day.minutes })
    if (current.length === 7) {
      weeks.push(current)
      current = []
    }
  }
  if (current.length) {
    while (current.length < 7) current.push({ date: '', minutes: 0 })
    weeks.push(current)
  }
  return weeks
})

// 每个月 1 号落在第几列，用于热力图顶部的月份刻度
const heatMonths = computed(() => {
  const days = focusPage.value?.heatmap || []
  const lead = heatLead.value
  const marks = []
  days.forEach((day, index) => {
    if (!day.date.endsWith('-01')) return
    const column = Math.floor((index + lead) / 7)
    if (!marks.length || column > marks[marks.length - 1].column) {
      marks.push({ column, label: `${Number(day.date.slice(5, 7))}月` })
    }
  })
  return marks
})

// ---------------------------------------------------------------- 工具

function todayKey() {
  const now = new Date()
  return key(now)
}

function key(date) {
  return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')}`
}

function shiftMonthKey(value, months) {
  const date = new Date(`${value}T00:00:00`)
  const day = date.getDate()
  date.setDate(1)
  date.setMonth(date.getMonth() + months)
  const last = new Date(date.getFullYear(), date.getMonth() + 1, 0).getDate()
  date.setDate(Math.min(day, last))
  return key(date)
}

function recordDateLabel(value) {
  if (!value) return ''
  const date = new Date(`${value}T00:00:00`)
  return `${date.getMonth() + 1}月${date.getDate()}日`
}

function shiftKey(value, days) {
  const date = new Date(`${value}T00:00:00`)
  date.setDate(date.getDate() + days)
  return key(date)
}

function num(value) {
  return Number(value || 0).toLocaleString('zh-CN')
}

// 全 0 的趋势图会画出一条贴底的假折线和一个 "1" 的刻度，直接换空状态更诚实
function nonZero(list) {
  return Array.isArray(list) && list.some(point => Number(point?.value || 0) > 0)
}

function duration(minutes) {
  const value = Math.round(Number(minutes || 0))
  const hours = Math.floor(value / 60)
  const rest = value % 60
  if (!hours) return `${rest}分钟`
  return rest ? `${num(hours)}小时${rest}分` : `${num(hours)}小时`
}

// 纵轴：不足 1 小时用分钟，超过就折算成小时，避免出现 "12323m" 这种读不出来的刻度
function axisMinutes(value) {
  const amount = Number(value || 0)
  if (amount >= 60) {
    const hours = amount / 60
    return `${Number.isInteger(hours) ? hours : hours.toFixed(1)}h`
  }
  return `${amount}m`
}

// 取 1/2/5×10^n 的「好看步长」，让坐标轴刻度都是整数
function niceStep(span) {
  const raw = Math.max(1, Number(span) || 0) / 4
  const magnitude = 10 ** Math.floor(Math.log10(raw))
  const norm = raw / magnitude
  const mult = norm <= 1 ? 1 : norm <= 2 ? 2 : norm <= 5 ? 5 : 10
  return mult * magnitude
}

// 分钟轴的整数刻度：0/20h/40h/60h 这种，而不是 16.7h
function minuteAxis(values) {
  const max = Math.max(1, ...values.map(value => Number(value || 0)))
  const steps = [5, 10, 15, 20, 30, 60, 120, 180, 240, 300, 600, 900, 1200, 1800, 2400, 3600]
  const step = steps.find(candidate => max / candidate <= 5) || Math.ceil(max / 4 / 60) * 60
  return { max: Math.ceil(max / step) * step, interval: step }
}

function hourLabel(hour) {
  return `${String(hour).padStart(2, '0')}:00`
}

function heatColor(minutes) {
  const value = Number(minutes || 0)
  if (value <= 0) return 'var(--line)'
  if (value < 60) return '#cddffb'
  if (value < 180) return '#9dc0f5'
  if (value < 300) return '#6b99e8'
  return '#3f6fd1'
}

function groupLabel(mode) {
  return { LIST: '按清单', TAG: '按标签', PRIORITY: '按优先级', TASK: '按任务' }[mode] || '按清单'
}

function deltaText(delta, unit) {
  if (delta === 0) return `与之前持平`
  return `比之前${delta > 0 ? '多' : '少'}${Math.abs(delta)}${unit}`
}

function deltaClass(delta) {
  return delta > 0 ? 'is-up' : delta < 0 ? 'is-down' : 'is-flat'
}

function message(error) {
  return error?.response?.data?.message || error?.message || '统计加载失败'
}
</script>

<style scoped>
.ts-page { display: grid; align-content: start; gap: 14px; padding: 20px 30px 60px; color: var(--ink); }

.ts-head { display: grid; grid-template-columns: 1fr auto 1fr; align-items: center; gap: 12px; min-height: 38px; }
.ts-head h1 { margin: 0; font-size: 1.16rem; font-weight: 700; letter-spacing: .01em; }
.ts-tabs { display: inline-flex; padding: 3px; border-radius: 9px; background: color-mix(in srgb, var(--ink) 7%, transparent); }
.ts-tabs button { min-height: 26px; padding: 0 16px; border: 0; border-radius: 7px; background: transparent; color: var(--ink2); font: inherit; font-size: 13px; cursor: pointer; transition: background .15s, color .15s; }
.ts-tabs button.on { background: var(--card); color: var(--ink); box-shadow: 0 1px 4px rgb(15 23 42 / 12%); }
.ts-done { justify-self: end; min-height: 32px; padding: 0 22px; border: 0; border-radius: 16px; background: #3f7ae0; color: #fff; font: inherit; font-size: 13px; font-weight: 600; cursor: pointer; }
.ts-done:hover { background: #3568c6; }

.ts-error { margin: 0; padding: 8px 10px; border-left: 3px solid #e5678a; background: color-mix(in srgb, #e5678a 10%, transparent); font-size: 12.5px; }
.ts-loading { padding: 40px 0; color: var(--muted); font-size: 13px; text-align: center; }

.ts-summary { display: flex; align-items: baseline; gap: 22px; padding: 13px 18px; border-radius: 10px; background: var(--card); font-size: 12.5px; color: var(--ink2); }
.ts-summary span { display: inline-flex; align-items: baseline; gap: 5px; }
.ts-summary b { color: var(--ink); font-size: 15px; font-weight: 700; }
.ts-summary em { margin-left: auto; color: #b9bfc9; font-style: normal; font-size: 12.5px; }

.ts-grid { display: grid; grid-template-columns: minmax(0, 1fr) minmax(0, 1fr); gap: 14px; }
.ts-card { display: grid; align-content: start; gap: 8px; padding: 14px 16px 12px; border-radius: 10px; background: var(--card); min-width: 0; }
.ts-card.is-center { align-content: center; }
.ts-card.is-fill { grid-template-rows: auto auto minmax(0, 1fr); }
.ts-chart-fill { display: grid; min-height: 130px; }
.ts-chart-fill :deep(.ledger-report-chart-wrap) { height: 100%; }
.ts-chart-fill .ts-chart-empty { height: 100%; }
.ts-card.is-full { grid-column: 1 / -1; }
.ts-card h2 { margin: 0; font-size: 13.5px; font-weight: 700; letter-spacing: .01em; }
.ts-card-head-line { display: flex; align-items: center; justify-content: space-between; gap: 10px; min-height: 22px; }
.ts-achv { display: inline-flex; align-items: center; gap: 3px; color: #3f7ae0; font-size: 13.5px; font-weight: 700; }
.ts-achv i { font-style: normal; font-size: 14px; }

.ts-kpis { display: grid; gap: 20px 12px; padding: 14px 0 10px; }
.ts-kpis.is-2 { grid-template-columns: repeat(2, minmax(0, 1fr)); width: 100%; max-width: 720px; margin-inline: auto; }
.ts-kpis.is-3 { grid-template-columns: repeat(3, minmax(0, 1fr)); }
.ts-kpis.is-4 { grid-template-columns: repeat(4, minmax(0, 1fr)); }
.ts-kpis > div { display: grid; justify-items: center; gap: 6px; min-width: 0; }
.ts-kpis b { max-width: 100%; overflow: hidden; color: #3f7ae0; font-size: 26px; font-weight: 700; line-height: 1.1; text-overflow: ellipsis; white-space: nowrap; font-variant-numeric: tabular-nums; }
.ts-kpis b.is-long { font-size: 22px; }
.ts-kpis span { color: var(--muted); font-size: 12px; }
.ts-kpis em { color: var(--muted); font-style: normal; font-size: 11.5px; }
.ts-kpis em.is-up { color: #2fa27a; }
.ts-kpis em.is-down { color: #e5678a; }

.ts-pill { display: inline-flex; align-items: center; gap: 3px; min-height: 22px; padding: 0 8px; border-radius: 11px; background: color-mix(in srgb, var(--ink) 5%, transparent); color: var(--ink2); font-size: 11.5px; font-weight: 400; white-space: nowrap; }
.ts-pill.is-select { cursor: pointer; }
.ts-controls { display: inline-flex; align-items: center; gap: 6px; font-weight: 400; }

.ts-toolbar { display: flex; align-items: center; gap: 10px; }
.ts-seg { display: inline-flex; padding: 3px; border-radius: 9px; background: color-mix(in srgb, var(--ink) 6%, transparent); }
.ts-seg button { min-height: 26px; padding: 0 14px; border: 0; border-radius: 7px; background: transparent; color: var(--ink2); font: inherit; font-size: 12.5px; cursor: pointer; }
.ts-seg button.on { background: var(--card); color: var(--ink); box-shadow: 0 1px 4px rgb(15 23 42 / 12%); }
.ts-stepper { display: inline-flex; align-items: center; gap: 2px; padding: 2px 4px; border-radius: 13px; background: color-mix(in srgb, var(--ink) 5%, transparent); font-weight: 400; }
.ts-stepper button { display: grid; width: 20px; height: 20px; place-items: center; border: 0; border-radius: 50%; background: transparent; color: var(--ink2); cursor: pointer; }
.ts-stepper button:hover { background: color-mix(in srgb, var(--ink) 8%, transparent); }
.ts-stepper strong { padding: 0 4px; color: var(--ink2); font-size: 11.5px; font-weight: 500; white-space: nowrap; }
.ts-stepper.is-mini strong { font-size: 11px; }

.ts-donut-row { display: grid; grid-template-columns: 210px 148px; align-items: center; justify-content: center; gap: 18px; padding: 6px 0; }
.ts-donut { position: relative; min-width: 0; }
.ts-donut-center { position: absolute; inset: 0; display: grid; align-content: center; justify-items: center; gap: 2px; pointer-events: none; }
.ts-donut-center b { color: var(--ink); font-size: 20px; font-weight: 700; }
.ts-donut-center span { color: var(--muted); font-size: 11px; }
.ts-legend { display: grid; gap: 9px; margin: 0; padding: 0; list-style: none; font-size: 12px; color: var(--ink2); }
.ts-legend li { display: flex; align-items: center; gap: 7px; min-width: 0; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.ts-legend em { margin-left: auto; padding-left: 8px; color: var(--muted); font-style: normal; font-variant-numeric: tabular-nums; }
.ts-legend i { flex: 0 0 auto; width: 8px; height: 8px; border-radius: 50%; }

.ts-legend li.is-empty { color: var(--muted); }
.ts-legend li.is-empty::before { content: ''; flex: 0 0 auto; width: 8px; height: 8px; border-radius: 50%; background: var(--line); }

.ts-records { display: grid; gap: 2px; margin: 0; padding: 0; list-style: none; }
.ts-records li { display: grid; grid-template-columns: 96px 8px minmax(0, 1fr) auto; align-items: center; gap: 8px; padding: 8px 0; border-bottom: 1px solid color-mix(in srgb, var(--ink) 5%, transparent); font-size: 12px; }
.ts-records li:last-child { border-bottom: 0; }
.ts-records li.ts-rec-date { display: block; padding: 10px 0 4px; border-bottom: 0; color: var(--muted); font-size: 11.5px; }
.ts-rec-time { color: var(--muted); font-variant-numeric: tabular-nums; }
.ts-rec-dot { width: 6px; height: 6px; border-radius: 50%; background: #3f7ae0; }
.ts-rec-dot.is-habit { background: #8b7cf0; }
.ts-records b { overflow: hidden; color: var(--ink2); font-weight: 400; text-overflow: ellipsis; white-space: nowrap; }
.ts-records em { color: var(--ink2); font-style: normal; font-variant-numeric: tabular-nums; }
.ts-icon-actions { display: inline-flex; gap: 6px; }
.ts-icon-actions button { display: grid; width: 22px; height: 22px; place-items: center; border: 0; border-radius: 6px; background: transparent; color: var(--muted); cursor: pointer; }
.ts-icon-actions button:hover { background: color-mix(in srgb, var(--ink) 7%, transparent); color: var(--ink2); }

.ts-sub { margin: -2px 0 0; color: var(--muted); font-size: 11.5px; }
.ts-empty { display: grid; min-height: 150px; margin: 0; place-items: center; color: var(--muted); font-size: 12.5px; text-align: center; }
.ts-chart-empty { display: grid; height: 168px; margin: 0; place-items: center; color: var(--muted); font-size: 12.5px; }
.ts-chart-empty.is-tall { height: 172px; }

.ts-rings { display: grid; grid-template-columns: repeat(7, 1fr); gap: 8px; padding: 16px 6px 10px; }
.ts-ring { display: grid; justify-items: center; gap: 7px; }
.ts-ring svg { width: 46px; height: 46px; transform: rotate(-90deg); }
.ts-ring circle { fill: none; stroke-width: 4; }
.ts-ring .track { stroke: var(--line); }
.ts-ring .value { stroke: #3f7ae0; stroke-linecap: round; transition: stroke-dasharray .3s; }
.ts-ring small { color: var(--muted); font-size: 11px; }
.ts-ring.today small { color: #3f7ae0; font-weight: 600; }

.ts-timeline { display: grid; gap: 2px; padding: 4px 0 0; }
.ts-timeline-row { display: grid; grid-template-columns: 34px repeat(7, minmax(0, 1fr)); align-items: center; gap: 3px; }
.ts-timeline-row small { color: var(--muted); font-size: 9px; line-height: 8px; text-align: right; font-variant-numeric: tabular-nums; }
.ts-timeline-row i { height: 7px; border-radius: 2px; }
.ts-timeline-days { display: grid; grid-template-columns: 34px repeat(7, minmax(0, 1fr)); gap: 3px; padding-top: 4px; }
.ts-timeline-days b { color: var(--muted); font-size: 10px; font-weight: 500; text-align: center; }
.ts-timeline-days b.today { color: #3f7ae0; font-weight: 600; }

.ts-heat { display: grid; gap: 8px; padding: 6px 0 0; }
.ts-heat-months { display: grid; gap: 3px; padding-bottom: 2px; }
.ts-heat-months b { justify-self: start; color: var(--muted); font-size: 10px; font-weight: 400; white-space: nowrap; }
.ts-heat-body { display: flex; gap: 3px; overflow: hidden; }
.ts-heat-week { display: grid; grid-template-rows: repeat(7, 1fr); gap: 3px; }
.ts-heat-week i { width: 13px; height: 13px; border-radius: 2px; background: #eef1f5; }
.ts-heat-week i.blank { background: transparent; }
.ts-heat-legend { display: flex; align-items: center; justify-content: flex-end; gap: 4px; color: var(--muted); font-size: 10px; }
.ts-heat-legend i { width: 10px; height: 10px; border-radius: 2px; }

@media (max-width: 900px) {
  .ts-grid { grid-template-columns: minmax(0, 1fr); }
  .ts-kpis.is-4 { grid-template-columns: repeat(2, minmax(0, 1fr)); }
  .ts-summary { flex-wrap: wrap; gap: 10px 18px; }
  .ts-summary em { margin-left: 0; width: 100%; }
}
</style>
