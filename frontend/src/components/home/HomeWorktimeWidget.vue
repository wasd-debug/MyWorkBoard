<template>
  <HomeWidgetFrame title="工时与加班" kicker="WORKTIME" :meta="`${stats.label} · ${stats.days} 天有记录`" :editing="editing">
    <template #actions>
      <div class="hw-seg">
        <button type="button" :class="{ active: range === 'week' }" @click="range = 'week'">本周</button>
        <button type="button" :class="{ active: range === 'month' }" @click="range = 'month'">本月</button>
        <button type="button" :class="{ active: range === 'year' }" @click="range = 'year'">本年</button>
      </div>
      <button class="hw-btn" type="button" @click="togglePunch"><Timer :size="13" />{{ punchLabel }}</button>    </template>

    <div class="wt-stats">
      <div><small>工时</small><b>{{ hours(stats.totalMin) }}<i>h</i></b></div>
      <div><small>加班</small><b :class="{ 'is-plus': stats.overtimeMin > 0 }">{{ hours(stats.overtimeMin) }}<i>h</i></b></div>
      <div><small>平均时薪</small><b>{{ stats.rate > 0 ? `¥${stats.rate.toFixed(1)}` : '—' }}</b></div>
      <div><small>{{ stats.label }}工资</small><b>{{ stats.earned > 0 ? `¥${money(stats.earned)}` : '—' }}</b></div>
    </div>

    <div class="wt-chart">
      <div class="wt-chart-head">
        <span>时薪趋势</span>
        <span>{{ stats.granularity === 'day' ? '按日' : '按月' }} · {{ hasSalary ? `基准 ¥${stats.baseRate.toFixed(1)}/h` : '未设置月薪' }}</span>
      </div>
      <div v-if="hasSalary" class="wt-chart-plot">
        <LedgerReportChart :option="chartOption" aria-label="时薪趋势" height="100%" :show-table="false" />
      </div>
      <div v-else class="wt-empty">
        <b>还没有月薪设置</b>
        <span>在设置页填写月薪后，这里会显示每日 / 每月时薪趋势</span>
        <router-link class="hw-btn" to="/worktime/settings">去设置</router-link>
      </div>
    </div>

    <Transition name="wt-punch">
      <form v-if="punchOpen" class="wt-punch" @submit.prevent="savePunch">
        <label>日期<input v-model="punch.date" type="date" required @change="loadPunch" /></label>
        <label>上班<input v-model="punch.start" type="time" required /></label>
        <label>下班<input v-model="punch.end" type="time" required /></label>
        <label>休息<input v-model="punch.rest" type="number" min="0" max="600" step="5" /><small>分钟</small></label>
        <span v-if="punchError" class="wt-error">{{ punchError }}</span>
        <button class="hw-btn" type="button" @click="punchOpen = false">取消</button>
        <button class="hw-btn primary" type="submit" :disabled="saving">{{ saving ? '保存中…' : '保存' }}</button>
      </form>
    </Transition>

    <template #footer>
      <span>{{ stats.days }} 天有记录 · 加班 {{ hours(stats.overtimeMin) }}h</span>
      <router-link class="wt-more" to="/stats">工时统计 →</router-link>
    </template>
  </HomeWidgetFrame>
</template>

<script setup>
import { computed, reactive, ref } from 'vue'
import { Timer } from 'lucide-vue-next'
import HomeWidgetFrame from './HomeWidgetFrame.vue'
import LedgerReportChart from '../ledger/LedgerReportChart.vue'
import { useWorktimeStore } from '../../stores/worktime.js'
import { useAppStore } from '../../stores/app.js'
import { message } from '../../services/message.js'
import { dateKeyOf, hoursLabel, worktimeStats } from './homeMetrics.js'
import { punchUpdate } from './homeWidgetEdits.js'

defineProps({ editing: Boolean })

const worktimeStore = useWorktimeStore()
const appStore = useAppStore()
const range = ref('month')
const punchOpen = ref(false)
const saving = ref(false)
const punchError = ref('')
const todayKey = dateKeyOf(new Date())
const hasToday = computed(() => Boolean(worktimeStore.records[todayKey]?.id))
const punch = reactive({ date: todayKey, start: '', end: '', rest: 0 })
const punchLabel = computed(() => {
  if (punchOpen.value) return '收起工时编辑'
  const target = punch.date || todayKey
  if (target === todayKey) return hasToday.value ? '修改本日工时' : '记录本日工时'
  return worktimeStore.records[target]?.id ? `修改 ${target.slice(5)} 工时` : `补记 ${target.slice(5)} 工时`
})

const stats = computed(() => worktimeStats({
  records: worktimeStore.records,
  settings: worktimeStore.settings,
  holidays: appStore.holidays,
  range: range.value,
  today: new Date()
}))

const hasSalary = computed(() => stats.value.baseRate > 0)

const chartOption = computed(() => {
  const rows = stats.value.series
  return {
    grid: { left: 6, right: 14, top: 12, bottom: 2, containLabel: true },
    tooltip: {
      trigger: 'axis',
      valueFormatter: value => (value == null ? '无记录' : `¥${Number(value).toFixed(2)}/h`),
      textStyle: { fontSize: 11 }
    },
    xAxis: {
      type: 'category',
      boundaryGap: false,
      data: rows.map(row => row.label),
      axisLine: { lineStyle: { color: '$line' } },
      axisTick: { show: false },
      axisLabel: { color: '$muted', fontSize: 9, interval: rows.length > 16 ? Math.ceil(rows.length / 8) : 0 }
    },
    yAxis: {
      type: 'value',
      scale: true,
      axisLabel: { color: '$muted', fontSize: 9, formatter: value => `¥${value}` },
      splitLine: { lineStyle: { color: '$line', type: 'dashed' } }
    },
    series: [{
      name: '时薪', type: 'line', smooth: true, connectNulls: true, showSymbol: false,
      data: rows.map(row => row.value),
      lineStyle: { color: '$accent', width: 2 },
      itemStyle: { color: '$accent' },
      areaStyle: { color: '$accent', opacity: .12 }
    }]
  }
})

function hours(minutes) { return hoursLabel(minutes) }
function money(value) { return Number(value || 0).toLocaleString('zh-CN', { maximumFractionDigits: 0 }) }

function togglePunch() {
  if (punchOpen.value) { punchOpen.value = false; return }
  // 默认仍是今天；之前补记过别的日期就沿用上次选的日子，省一次点选
  punch.date = punch.date || todayKey
  loadPunch()
  punchError.value = ''
  punchOpen.value = true
}

/** 切日期时把那天已有的记录读进表单，没记录就按设置里的上下班时间给默认值 */
function loadPunch() {
  const record = worktimeStore.records[punch.date] || {}
  punch.start = record.start || worktimeStore.settings.workStart || '09:00'
  punch.end = record.end || worktimeStore.settings.workEnd || '18:00'
  punch.rest = Number(record.rest || 0)
  punchError.value = ''
}

async function savePunch() {
  const payload = punchUpdate(punch.date, punch)
  if (!payload) { punchError.value = '请填写有效的日期与上下班时间'; return }
  saving.value = true
  punchError.value = ''
  try {
    await worktimeStore.saveRecord(payload)
    message.success(payload.date === todayKey ? '本日工时已保存' : `${payload.date.slice(5)} 工时已保存`)
    punchOpen.value = false
  } catch (error) {
    punchError.value = error?.response?.data?.detail || error?.message || '保存失败'
  } finally {
    saving.value = false
  }
}
</script>

<style scoped>
.wt-stats { display: grid; flex: none; grid-template-columns: repeat(4, minmax(0, 1fr)); gap: 8px; margin-bottom: 10px; }
.wt-stats > div { min-width: 0; padding: 9px 10px; border: 1px solid var(--line); border-radius: 12px; background: var(--paper); }
.wt-stats small { display: block; color: var(--muted); font-size: 10px; }
.wt-stats b { display: block; margin-top: 2px; overflow: hidden; color: var(--ink); font-size: 18px; font-weight: 720; letter-spacing: -.03em; text-overflow: ellipsis; white-space: nowrap; font-variant-numeric: tabular-nums; }
.wt-stats b i { margin-left: 2px; color: var(--muted); font-size: 10px; font-style: normal; font-weight: 600; }
.wt-stats b.is-plus { color: var(--down); }
.wt-chart { display: flex; min-height: 0; flex: 1; flex-direction: column; gap: 4px; }
.wt-chart-head { display: flex; flex: none; align-items: center; justify-content: space-between; gap: 10px; color: var(--muted); font-size: 10.5px; }
.wt-chart-head span:first-child { color: var(--ink2); font-weight: 650; }
.wt-chart-plot { display: flex; min-height: 60px; flex: 1; flex-direction: column; }
.wt-chart-plot :deep(.ledger-report-chart-wrap) { display: flex; min-height: 0; flex: 1; flex-direction: column; }
.wt-chart-plot :deep(.ledger-report-chart) { flex: 1; min-height: 0; }
.wt-empty { display: flex; flex: 1; flex-direction: column; align-items: center; justify-content: center; gap: 5px; min-height: 60px; color: var(--muted); font-size: 11.5px; text-align: center; }
.wt-empty b { color: var(--ink2); font-size: 12.5px; }
.wt-empty .hw-btn { margin-top: 4px; text-decoration: none; }
.wt-punch { display: flex; flex: none; flex-wrap: wrap; align-items: flex-end; gap: 8px; margin-top: 10px; padding: 10px 12px; border: 1px solid color-mix(in srgb, var(--accent) 30%, var(--line)); border-radius: 12px; background: color-mix(in srgb, var(--accent-soft) 34%, var(--card)); }
.wt-punch label { display: flex; align-items: center; gap: 5px; color: var(--ink2); font-size: 11px; }
.wt-punch input { height: 30px; padding: 0 7px; border: 1px solid var(--line2); border-radius: 7px; background: var(--card); color: var(--ink); font-size: 12px; }
.wt-punch input[type="number"] { width: 62px; }
/* 日期输入给固定宽度，否则 iOS/Safari 会把整行撑开 */
.wt-punch input[type="date"] { width: 140px; }
.wt-punch label small { color: var(--muted); font-size: 10px; }
.wt-punch .hw-btn { margin-left: auto; }
.wt-punch .hw-btn + .hw-btn { margin-left: 0; }
.wt-error { flex-basis: 100%; color: var(--down); font-size: 11px; }
.wt-more { color: var(--accent); font-weight: 650; }
.wt-punch-enter-active, .wt-punch-leave-active { transition: opacity .16s ease, transform .16s ease; }
.wt-punch-enter-from, .wt-punch-leave-to { opacity: 0; transform: translateY(-4px); }
@media (max-width: 720px) { .wt-stats { grid-template-columns: repeat(2, minmax(0, 1fr)); } }
</style>
