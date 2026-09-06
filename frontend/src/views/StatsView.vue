<template>
  <section>
    <div class="page-heading">
      <div>
        <div class="label">WORKTIME / ANALYTICS</div>
        <h1>统计</h1>
      </div>
    </div>

    <div class="card stats-filter">
      <div class="stats-filter-head">
        <div>
          <h3 style="margin:0 0 3px;font-size:15px">自定义统计</h3>
          <p class="muted">按任意时间段查看工时、加班、工资与时薪</p>
        </div>
        <div class="stats-filter-controls">
          <select :value="activeQuick" class="ui-select range-select" aria-label="选择统计范围" @change="onSelectRange($event.target.value)">
            <option v-for="item in rangeOptions" :key="item.key" :value="item.key">{{ item.label }}</option>
          </select>
          <select v-model="granularity" class="ui-select range-select" aria-label="选择分组粒度">
            <option value="day">按日</option>
            <option value="month">按月</option>
          </select>
        </div>
      </div>
      <div class="stats-filter-range">
        <span class="filter-range num">{{ formatDate(startDate) }} – {{ formatDate(endDate) }}</span>
        <Button size="sm" variant="ghost" @click="openCustom">自定义范围</Button>
      </div>
    </div>

    <Dialog v-model:open="customDlg" title="自定义统计范围">
      <div class="custom-dates">
        <label>开始日期<Input v-model="customStart" type="date" /></label>
        <label>结束日期<Input v-model="customEnd" type="date" /></label>
      </div>
      <template #footer>
        <Button variant="ghost" @click="customDlg = false">取消</Button>
        <Button @click="applyCustom">确定</Button>
      </template>
    </Dialog>

    <div class="card period-card">
      <div class="period-head">
        <div class="t">统计结果</div>
        <div class="range num">{{ formatDate(startDate) }} – {{ formatDate(endDate) }}</div>
      </div>
      <div class="rate-pair">
        <div class="rate-box">
          <div class="v num" style="color:var(--muted)">{{ stats.baseRate > 0 ? stats.baseRate.toFixed(2) : '—' }}</div>
          <div class="l">基准时薪 BASE</div>
        </div>
        <div class="rate-box">
          <div class="v num" style="color:var(--accent)">{{ stats.realRate > 0 ? stats.realRate.toFixed(2) : '—' }}</div>
          <div class="l">实际时薪 REAL</div>
        </div>
      </div>
      <div class="grid4">
        <div class="stat"><div class="v num">{{ stats.days }}</div><div class="l">上班天数 DAYS</div></div>
        <div class="stat"><div class="v num">{{ hours(stats.totalMin) }}h</div><div class="l">工作时长 HOURS</div></div>
        <div class="stat"><div class="v num" :class="stats.otMin >= 0 ? 'warn' : 'up'">{{ signed(stats.otMin) }}</div><div class="l">{{ stats.otMin >= 0 ? '加班 OT' : '少于标准' }}</div></div>
        <div class="stat"><div class="v num">{{ stats.earned > 0 ? money(Math.round(stats.earned)) : '—' }}</div><div class="l">工资 PAY</div></div>
      </div>
    </div>

    <div class="card chart-card">
      <div class="chart-title">
        <div>
          <h3>时长趋势</h3>
          <p class="muted">{{ chartGranularity === 'month' ? '每月' : '每日' }}总工时与加班时长</p>
        </div>
        <div class="chart-legend">
          <span><span class="dot total"></span>总工时</span>
          <span><span class="dot ot"></span>加班</span>
        </div>
      </div>
      <div class="chart-scroll"><div ref="chartRef" class="echart" aria-label="工时趋势图"></div></div>
      <div v-if="selected" class="selected-detail">
        <div class="d-head">SELECTED // {{ selected.label }}</div>
        <div class="d-grid">
          <div class="d-item"><div class="k">总工时</div><div class="v num">{{ selected.total.toFixed(1) }}h</div></div>
          <div class="d-item"><div class="k">加班</div><div class="v num" :class="selected.ot > 0 ? 'warn' : ''">{{ selected.ot.toFixed(1) }}h</div></div>
          <div class="d-item"><div class="k">上班时间</div><div class="v num">{{ selected.start || '—' }}</div></div>
          <div class="d-item"><div class="k">下班时间</div><div class="v num">{{ selected.end || '—' }}</div></div>
        </div>
      </div>
    </div>

    <div class="hint" v-html="periodTip"></div>
  </section>
</template>

<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import * as echarts from 'echarts'
import Button from '../components/ui/Button.vue'
import Input from '../components/ui/Input.vue'
import Dialog from '../components/ui/Dialog.vue'
import { useAppStore } from '../stores/app'
import { CALC } from '../utils/calc'

const store = useAppStore()
const now = new Date()
const todayKey = CALC.dateKey(now)
const startDate = ref(CALC.dateKey(CALC.monday(now)))
const endDate = ref(todayKey)
const activeQuick = ref('week')
const granularity = ref('day')
const chartRef = ref(null)
let chart
const selected = ref(null)

const hours = CALC.fmtHours
const money = CALC.fmtMoney
const signed = CALC.fmtSigned

const rangeOptions = [
  { key: 'week', label: '本周' },
  { key: 'month', label: '本月' },
  { key: 'year', label: '本年' },
  { key: 'lastWeek', label: '上周' },
  { key: 'lastMonth', label: '上月' },
  { key: 'lastYear', label: '去年' },
  { key: 'custom', label: '自定义范围…' }
]

const customDlg = ref(false)
const customStart = ref('')
const customEnd = ref('')
const toInputDate = date => CALC.dateKey(date)

function applyPreset(key) {
  const current = new Date(now)
  let start, end
  if (key === 'week' || key === 'lastWeek') {
    const monday = CALC.monday(current)
    start = key === 'week' ? monday : CALC.addDays(monday, -7)
    end = key === 'week' ? current : CALC.addDays(monday, -1)
  } else if (key === 'month' || key === 'lastMonth') {
    const first = CALC.monthFirst(current)
    start = key === 'month' ? first : new Date(current.getFullYear(), current.getMonth() - 1, 1)
    end = key === 'month' ? current : new Date(current.getFullYear(), current.getMonth(), 0)
  } else {
    const year = current.getFullYear() - (key === 'lastYear' ? 1 : 0)
    start = new Date(year, 0, 1)
    end = key === 'year' ? current : new Date(year, 11, 31)
  }
  startDate.value = toInputDate(start)
  endDate.value = toInputDate(end)
  activeQuick.value = key
  selected.value = null
}

function onSelectRange(key) {
  if (key === 'custom') { openCustom(); return }
  applyPreset(key)
}

function openCustom() {
  customStart.value = startDate.value
  customEnd.value = endDate.value
  customDlg.value = true
}

function applyCustom() {
  if (!customStart.value || !customEnd.value || customStart.value > customEnd.value) {
    ElMessage.warning('请选择有效的日期范围')
    return
  }
  startDate.value = customStart.value
  endDate.value = customEnd.value
  activeQuick.value = 'custom'
  customDlg.value = false
  selected.value = null
}

function formatDate(value) { return value ? value.replaceAll('-', '/') : '—' }

const rangeDates = computed(() => CALC.rangeKeys(startDate.value, endDate.value))
const chartGranularity = computed(() => granularity.value)
const annualRange = computed(() => ['year', 'lastYear'].includes(activeQuick.value) || rangeDates.value.length >= 300)
const stats = computed(() => CALC.periodStats(
  rangeDates.value,
  store.records,
  store.settings,
  store.settings.basis,
  0,
  store.holidays,
  ym => CALC.monthSalary(store.settings.salaries, store.settings, store.settings.basis, ym)
))

const chartData = computed(() => {
  if (chartGranularity.value === 'month') {
    const months = {}
    for (const key of rangeDates.value) {
      const ym = key.slice(0, 7)
      const minutes = CALC.actualMin(store.records[key], store.settings)
      const off = store.holidays && CALC.dayType(key, store.holidays) === 'off'
      const ot = minutes > 0 ? (off ? minutes : Math.max(0, minutes - CALC.stdWorkMin(store.settings))) : 0
      if (!months[ym]) months[ym] = { key: ym, label: annualRange.value ? `${Number(ym.slice(5))}月` : ym.replace('-', '/'), total: 0, ot: 0, start: '', end: '' }
      months[ym].total += minutes / 60
      months[ym].ot += ot / 60
    }
    return Object.values(months)
  }
  return rangeDates.value.map(key => {
    const minutes = CALC.actualMin(store.records[key], store.settings)
    const off = store.holidays && CALC.dayType(key, store.holidays) === 'off'
    const ot = minutes > 0 ? (off ? minutes : Math.max(0, minutes - CALC.stdWorkMin(store.settings))) : 0
    const rec = store.records[key] || {}
    const day = Number(key.slice(8))
    return { key, label: annualRange.value ? (day === 1 ? `${Number(key.slice(5, 7))}月` : '') : key.slice(5), total: minutes / 60, ot: ot / 60, start: rec.start || '', end: rec.end || '' }
  })
})

const periodTip = computed(() => stats.value.days === 0
  ? '<span class="chip">统计提示</span><br>当前时间段暂无打卡记录。'
  : `<span class="chip">统计提示</span><br>共打卡 <b>${stats.value.days}</b> 天，累计工作 <b>${hours(stats.value.totalMin)}h</b>，累计加班 <b>${hours(Math.max(0, stats.value.otMin))}h</b>，应付工资 <b>${money(Math.round(stats.value.earned))}</b>。`)

function renderChart() {
  if (!chartRef.value) return
  const minWidth = annualRange.value ? (chartGranularity.value === 'month' ? 760 : 1500) : null
  chartRef.value.style.width = minWidth ? `${minWidth}px` : '100%'
  if (!chart) {
    chart = echarts.init(chartRef.value)
    chart.on('click', params => {
      const item = chartData.value[params.dataIndex]
      if (item) selected.value = item
    })
  }
  const data = chartData.value
  const isSelected = selected.value ? data.findIndex(d => d.key === selected.value.key) : -1
  const accent = varColor('--accent')
  const accentSoft = varColor('--accent-soft')
  const warn = varColor('--warn')

  chart.setOption({
    color: [accent, warn],
    tooltip: {
      trigger: 'axis',
      backgroundColor: varColor('--ink'),
      borderColor: varColor('--line2'),
      textStyle: { color: varColor('--card'), fontFamily: 'inherit' },
      formatter: params => `${params[0]?.axisValue}<br>${params.map(point => `${point.marker}${point.seriesName}：${Number(point.value).toFixed(1)}h`).join('<br>')}`
    },
    legend: { show: false },
    grid: { left: 42, right: 20, top: 18, bottom: 38 },
    xAxis: {
      type: 'category',
      boundaryGap: false,
      data: data.map(item => item.label),
      axisLine: { lineStyle: { color: varColor('--line2') } },
      axisTick: { lineStyle: { color: varColor('--line2') } },
      axisLabel: { color: varColor('--ink2'), interval: annualRange.value ? 0 : Math.max(0, Math.ceil(data.length / 10) - 1), hideOverlap: true }
    },
    yAxis: {
      type: 'value',
      name: '小时',
      nameTextStyle: { color: varColor('--ink2') },
      axisLabel: { color: varColor('--ink2') },
      splitLine: { lineStyle: { color: varColor('--line') } }
    },
    series: [
      {
        name: '总工时',
        type: 'line',
        smooth: true,
        symbol: 'circle',
        symbolSize: 8,
        lineStyle: { width: 3, color: accent },
        itemStyle: { color: accent, borderColor: varColor('--card'), borderWidth: 2 },
        data: data.map((item, idx) => ({
          value: Number(item.total.toFixed(2)),
          itemStyle: {
            color: idx === isSelected ? accent : accent,
            shadowBlur: idx === isSelected ? 8 : 0,
            shadowColor: accent
          }
        })),
        animationDuration: 300
      },
      {
        name: '加班',
        type: 'line',
        smooth: true,
        symbol: 'circle',
        symbolSize: 6,
        lineStyle: { width: 2, type: 'dashed', color: warn },
        itemStyle: { color: warn, borderColor: varColor('--card'), borderWidth: 1 },
        data: data.map((item, idx) => ({
          value: Number(item.ot.toFixed(2)),
          itemStyle: {
            color: warn,
            shadowBlur: idx === isSelected ? 6 : 0,
            shadowColor: warn
          }
        })),
        animationDuration: 300
      }
    ]
  }, true)
}

function varColor(name) {
  return getComputedStyle(document.documentElement).getPropertyValue(name).trim() || '#0f5132'
}

watch([rangeDates, chartGranularity, () => store.records], async () => { await nextTick(); renderChart() }, { deep: true })
watch(selected, () => { renderChart() })
onMounted(() => { renderChart(); window.addEventListener('resize', renderChart) })
onBeforeUnmount(() => { window.removeEventListener('resize', renderChart); chart?.dispose() })
</script>
