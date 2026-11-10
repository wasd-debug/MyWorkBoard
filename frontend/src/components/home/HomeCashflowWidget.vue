<template>
  <HomeWidgetFrame title="收支趋势" kicker="CASH FLOW" :meta="metaLabel" :editing="editing">
    <template #actions>
      <div class="hw-seg">
        <button type="button" :class="{ active: mode === 'day' }" @click="mode = 'day'">本月</button>
        <button type="button" :class="{ active: mode === 'month' }" @click="mode = 'month'">本年</button>
      </div>
    </template>

    <div class="cf-body">
      <div class="cf-totals">
        <span><small>收入</small><b class="is-in">¥{{ money(summary.income) }}</b></span>
        <span><small>支出</small><b class="is-out">¥{{ money(summary.expense) }}</b></span>
        <span><small>结余</small><b :class="summary.net >= 0 ? 'is-in' : 'is-out'">{{ summary.net >= 0 ? '+' : '−' }}¥{{ money(Math.abs(summary.net)) }}</b></span>
      </div>

      <div class="cf-chart">
        <LedgerReportChart :option="option" :aria-label="ariaLabel" height="100%" :show-table="false" />
      </div>
    </div>

    <template #footer>
      <span>{{ activeDays }} 天有记录 · 转账不计入</span>
      <router-link class="cf-more" to="/ledger/reports">报表 →</router-link>
    </template>
  </HomeWidgetFrame>
</template>

<script setup>
import { computed, ref } from 'vue'
import HomeWidgetFrame from './HomeWidgetFrame.vue'
import LedgerReportChart from '../ledger/LedgerReportChart.vue'
import { useLedgerStore } from '../../stores/ledger.js'
import { cashflowSeries, periodSummary } from './homeMetrics.js'

defineProps({ editing: Boolean })

const ledgerStore = useLedgerStore()
const mode = ref('day')
const rows = computed(() => cashflowSeries(ledgerStore.transactions, { mode: mode.value }))
const summary = computed(() => mode.value === 'month'
  ? periodSummary(ledgerStore.transactions, `${new Date().getFullYear()}-01-01`, `${new Date().getFullYear()}-12-31`)
  : periodSummary(ledgerStore.transactions, monthStart.value, monthEnd.value))
const monthStart = computed(() => { const now = new Date(); return `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}-01` })
const monthEnd = computed(() => { const now = new Date(); return `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}-31` })
const metaLabel = computed(() => mode.value === 'month' ? `${new Date().getFullYear()} 年按月` : '本月按日')
const ariaLabel = computed(() => `${metaLabel.value}收入与支出趋势`)
const activeDays = computed(() => rows.value.filter(row => row.income || row.expense).length)

const option = computed(() => ({
  grid: { left: 4, right: 10, top: 26, bottom: 2, containLabel: true },
  legend: { show: true, top: 0, right: 0, itemWidth: 8, itemHeight: 8, itemGap: 12, textStyle: { color: '$muted', fontSize: 10 } },
  tooltip: {
    trigger: 'axis',
    valueFormatter: value => `¥${Number(value || 0).toFixed(2)}`,
    textStyle: { fontSize: 11 }
  },
  xAxis: {
    type: 'category',
    boundaryGap: false,
    data: rows.value.map(row => row.label),
    axisLine: { lineStyle: { color: '$line' } },
    axisTick: { show: false },
    axisLabel: { color: '$muted', fontSize: 9, interval: mode.value === 'month' ? 0 : 4 }
  },
  yAxis: {
    type: 'value',
    axisLabel: { color: '$muted', fontSize: 9, formatter: value => (Math.abs(value) >= 10000 ? `${value / 10000}万` : value) },
    splitLine: { lineStyle: { color: '$line', type: 'dashed' } }
  },
  series: [
    { name: '收入', type: 'line', smooth: true, showSymbol: false, data: rows.value.map(row => row.income), lineStyle: { color: '$income', width: 2 }, itemStyle: { color: '$income' }, areaStyle: { color: '$income', opacity: .1 } },
    { name: '支出', type: 'line', smooth: true, showSymbol: false, data: rows.value.map(row => row.expense), lineStyle: { color: '$expense', width: 2 }, itemStyle: { color: '$expense' }, areaStyle: { color: '$expense', opacity: .1 } }
  ]
}))

function money(value) {
  return Number(value || 0).toLocaleString('zh-CN', { minimumFractionDigits: 0, maximumFractionDigits: 0 })
}
</script>

<style scoped>
.cf-body { display: flex; height: 100%; min-height: 0; flex-direction: column; gap: 6px; }
.cf-totals { display: flex; flex: none; gap: 14px; }
.cf-totals span { display: flex; min-width: 0; flex-direction: column; gap: 1px; }
.cf-totals small { color: var(--muted); font-size: 10px; }
.cf-totals b { font-size: 15px; font-weight: 720; font-variant-numeric: tabular-nums; letter-spacing: -.02em; }
.cf-totals .is-in { color: var(--down); }
.cf-totals .is-out { color: var(--up); }
.cf-chart { display: flex; min-height: 90px; flex: 1; flex-direction: column; }
.cf-chart :deep(.ledger-report-chart-wrap) { display: flex; min-height: 0; flex: 1; flex-direction: column; }
.cf-chart :deep(.ledger-report-chart) { flex: 1; min-height: 0; }
.cf-more { color: var(--accent); font-weight: 650; }
</style>
