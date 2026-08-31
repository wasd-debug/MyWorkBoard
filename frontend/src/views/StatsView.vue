<template>
  <section>
    <div class="stats-filter card">
      <div class="filter-head">
        <div><h3>自定义统计</h3><p class="muted">按任意时间段查看工时、加班、工资与时薪</p></div>
        <el-select v-model="activeQuick" class="range-select" placeholder="选择统计范围" @change="onSelectRange">
          <el-option v-for="item in rangeOptions" :key="item.key" :label="item.label" :value="item.key" />
        </el-select>
      </div>
      <div class="date-filter-row">
        <span class="filter-range num">{{ formatDate(startDate) }} – {{ formatDate(endDate) }}</span>
        <el-button size="small" text type="primary" @click="openCustom">自定义范围</el-button>
      </div>
    </div>

    <el-dialog v-model="customDlg" title="自定义统计范围" width="320px" :close-on-click-modal="false">
      <div class="custom-dates">
        <label>开始日期<input v-model="customStart" type="date"></label>
        <label>结束日期<input v-model="customEnd" type="date"></label>
      </div>
      <template #footer>
        <el-button @click="customDlg = false">取消</el-button>
        <el-button type="primary" @click="applyCustom">确定</el-button>
      </template>
    </el-dialog>

    <div class="pcard custom-period-card">
      <div class="ph"><div class="t">统计结果</div><div class="range num">{{ formatDate(startDate) }} – {{ formatDate(endDate) }}</div></div>
      <PeriodBody :st="stats" />
    </div>

    <div class="chart-card card">
      <div class="chart-title"><div><h3>时长趋势</h3><p class="muted">每日总工时与加班时长</p></div><div class="legend"><span class="legend-total"></span>总工时 <span class="legend-ot"></span>加班</div></div>
      <div ref="chartRef" class="echart"></div>
    </div>

    <div class="tip" v-html="periodTip"></div>
  </section>
</template>

<script setup>
import { computed, h, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import * as echarts from 'echarts'
import { useAppStore } from '../stores/app'
import { CALC } from '../utils/calc'

const store = useAppStore()
const now = new Date()
const todayKey = CALC.dateKey(now)
const startDate = ref(CALC.dateKey(CALC.monday(now)))
const endDate = ref(todayKey)
const activeQuick = ref('week')
const chartRef = ref(null)
let chart
const hours = CALC.fmtHours
const signed = CALC.fmtSigned
const money = CALC.fmtMoney

const rangeOptions = [
  { key: 'week', label: '本周' }, { key: 'month', label: '本月' }, { key: 'year', label: '本年' },
  { key: 'lastWeek', label: '上周' }, { key: 'lastMonth', label: '上月' }, { key: 'lastYear', label: '去年' },
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
  } else if (key === 'year' || key === 'lastYear') {
    const year = current.getFullYear() - (key === 'lastYear' ? 1 : 0)
    start = new Date(year, 0, 1)
    end = key === 'year' ? current : new Date(year, 11, 31)
  }
  startDate.value = toInputDate(start); endDate.value = toInputDate(end); activeQuick.value = key
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
}
function formatDate(value) { return value ? value.replaceAll('-', '/') : '—' }
const rangeDates = computed(() => CALC.rangeKeys(startDate.value, endDate.value))
/* 跨月统计：各月按各自月薪封顶（工资按月浮动） */
const stats = computed(() => CALC.periodStats(rangeDates.value, store.records, store.settings, store.settings.basis, 0, store.holidays,
  ym => CALC.monthSalary(store.settings.salaries, store.settings, store.settings.basis, ym)))
const chartData = computed(() => rangeDates.value.map(k => {
  const m = CALC.actualMin(store.records[k], store.settings)
  const off = store.holidays && CALC.dayType(k, store.holidays) === 'off'
  const ot = m > 0 ? (off ? m : Math.max(0, m - CALC.stdWorkMin(store.settings))) : 0
  return { key: k, total: m / 60, ot: ot / 60 }
}))
const periodTip = computed(() => stats.value.days === 0
  ? `<span class="tag">统计提示</span><br>当前时间段暂无打卡记录。`
  : `<span class="tag">统计提示</span><br>共打卡 <b>${stats.value.days}</b> 天，累计工作 <b>${hours(stats.value.totalMin)}h</b>，累计加班 <b>${hours(Math.max(0, stats.value.otMin))}h</b>，应付工资 <b>${money(Math.round(stats.value.earned))}</b>。`)

function renderChart() {
  if (!chartRef.value) return
  if (!chart) chart = echarts.init(chartRef.value)
  const data = chartData.value
  chart.setOption({
    color: ['#6c8cff', '#ffbd63'],
    tooltip: { trigger: 'axis', formatter: params => `${params[0]?.axisValue}<br>${params.map(p => `${p.marker}${p.seriesName}：${Number(p.value).toFixed(1)}h`).join('<br>')}` },
    legend: { show: false },
    grid: { left: 42, right: 20, top: 18, bottom: 38 },
    xAxis: { type: 'category', boundaryGap: false, data: data.map(x => x.key.slice(5)), axisLine: { lineStyle: { color: '#dfe4ef' } }, axisLabel: { color: '#8b93a7', interval: Math.max(0, Math.ceil(data.length / 10) - 1) } },
    yAxis: { type: 'value', name: '小时', nameTextStyle: { color: '#8b93a7' }, axisLabel: { color: '#8b93a7' }, splitLine: { lineStyle: { color: '#edf0f6' } } },
    series: [
      { name: '总工时', type: 'line', smooth: true, symbol: 'circle', symbolSize: 6, data: data.map(x => Number(x.total.toFixed(2))), areaStyle: { opacity: .12 }, lineStyle: { width: 3 } },
      { name: '加班', type: 'bar', barMaxWidth: 12, data: data.map(x => Number(x.ot.toFixed(2))), itemStyle: { borderRadius: [4, 4, 0, 0] } }
    ]
  }, true)
}
watch([rangeDates, () => store.records], async () => { await nextTick(); renderChart() }, { deep: true })
onMounted(() => { renderChart(); window.addEventListener('resize', renderChart) })
onBeforeUnmount(() => { window.removeEventListener('resize', renderChart); chart?.dispose() })

const PeriodBody = {
  props: { st: { type: Object, required: true } },
  setup(props) {
    return () => { const st = props.st; const otCls = st.otMin >= 0 ? 'ot' : 'up'; return h('div', {}, [
      h('div', { class: 'rate-pair' }, [h('div', { class: 'rate-box' }, [h('div', { class: 'v num', style: 'color:var(--muted)' }, st.baseRate > 0 ? st.baseRate.toFixed(2) : '—'), h('div', { class: 'l' }, '基准时薪')]), h('div', { class: 'rate-box' }, [h('div', { class: 'v num', style: 'color:var(--gold)' }, st.realRate > 0 ? st.realRate.toFixed(2) : '—'), h('div', { class: 'l' }, '实际时薪')])]),
      h('div', { class: 'grid4' }, [h('div', { class: 'stat' }, [h('div', { class: 'v num' }, st.days), h('div', { class: 'l' }, '上班天数')]), h('div', { class: 'stat' }, [h('div', { class: 'v num' }, hours(st.totalMin) + 'h'), h('div', { class: 'l' }, '工作时长')]), h('div', { class: 'stat' }, [h('div', { class: 'v num ' + otCls }, signed(st.otMin)), h('div', { class: 'l' }, st.otMin >= 0 ? '加班时长' : '少于标准')]), h('div', { class: 'stat' }, [h('div', { class: 'v num' }, st.earned > 0 ? money(Math.round(st.earned)) : '—'), h('div', { class: 'l' }, '工资')])])
    ]) }
  }
}
</script>
