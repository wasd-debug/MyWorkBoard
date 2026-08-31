<template>
  <section>
    <!-- 日期导航 -->
    <div class="date-nav">
      <el-button circle size="large" @click="shiftPunch(-1)">‹</el-button>
      <el-input type="date" v-model="punchDate" @change="onDateChange" />
      <el-button circle size="large" @click="shiftPunch(1)">›</el-button>
      <el-button size="small" @click="goToday">今天</el-button>
    </div>

    <!-- 休息日/节假日标识 -->
    <div v-if="isOffDay" class="offday-bar" :class="{ worked: m > 0 }">
      <span class="off-tag">{{ dayLabel }}</span>
      <span v-if="m > 0" class="ot-tag">假期加班 · 计入本月工作日</span>
      <span v-else class="off-hint">休息日打卡将按假期加班计入</span>
    </div>

    <!-- 上下班时间 -->
    <div class="time-row">
      <div class="time-box">
        <div class="l"><span>实际上班</span><button class="now-btn" @click="nowStart">现在</button></div>
        <el-input type="time" v-model="recStart" @change="saveRec" />
      </div>
      <div class="time-box">
        <div class="l"><span>实际下班</span><button class="now-btn" @click="nowEnd">现在</button></div>
        <el-input type="time" v-model="recEnd" @change="saveRec" />
      </div>
    </div>

    <!-- 工作日默认工时预填提示 -->
    <div v-if="isDefaultFilled" class="prefill-hint">
      工作日已按默认工时预填（{{ store.settings.workStart }}–{{ store.settings.workEnd }}），修改后自动保存
    </div>

    <!-- 自定义休息 -->
    <div class="rest-row">
      <span class="rest-label">自定义休息</span>
      <el-input-number v-model="recRest" :min="0" :max="600" :step="5" :controls="false" style="width:88px" @change="saveRec" />
      <span class="rest-unit">分钟</span>
      <span class="rest-hint">摸鱼 · 晚饭 · 健身</span>
    </div>

    <!-- 今日时薪 -->
    <div class="hero">
      <template v-if="m > 0 && hasSalary">
        <div class="label">今日实际时薪（{{ basisName }}）</div>
        <div class="big num">{{ rate.toFixed(2) }}<small> 元/小时</small></div>
        <div class="sub">按 {{ otherName }}口径 {{ otherRate > 0 ? otherRate.toFixed(2) + ' 元/小时' : '未填写' }}</div>
        <div v-if="base > 0" class="vs" :class="diff >= 0 ? 'up' : 'down'">
          {{ diff >= 0 ? '▲' : '▼' }} {{ Math.abs(diff).toFixed(1) }}%（基准 {{ money(base) }}/h）
        </div>
      </template>
      <template v-else-if="m > 0">
        <div class="label">今日实际时薪</div>
        <div class="big hint">请先到「记录」页设置本月工资</div>
      </template>
      <template v-else>
        <div class="label">今日实际时薪</div>
        <div class="big hint">填完上下班时间自动计算</div>
      </template>
    </div>

    <!-- 本周摘要 -->
    <div class="week-bar">
      <template v-if="wk.days > 0">
        <span>本周 <b class="num">{{ wk.days }}</b> 天 · <b class="num">{{ hours(wk.totalMin) }}h</b> · 加班 <b class="num" :class="wk.otMin >= 0 ? 'ot' : 'up'">{{ signed(wk.otMin) }}</b></span>
        <span class="rate num">{{ wk.realRate > 0 ? money(wk.realRate) + '/h' : '' }}</span>
      </template>
      <template v-else>
        <span>本周还没有打卡记录</span><span class="rate">—</span>
      </template>
    </div>

    <!-- 今日明细 -->
    <div class="card">
      <h3>今日明细</h3>
      <div class="grid4">
        <div class="stat"><div class="v num">{{ hours(std) }}h</div><div class="l">标准工时</div></div>
        <div class="stat"><div class="v num">{{ hours(m) }}h</div><div class="l">实际工时</div></div>
        <div class="stat"><div class="v num" :class="ot >= 0 ? 'ot' : 'up'">{{ signed(ot) }}</div><div class="l">{{ otLabel }}</div></div>
        <div class="stat"><div class="v num">{{ money(dayPay) }}</div><div class="l">今日日薪</div></div>
        <div v-if="rest > 0" class="stat"><div class="v num">{{ hours(rest) }}h</div><div class="l">自定义休息</div></div>
      </div>
    </div>
  </section>
</template>

<script setup>
import { ref, computed, watch } from 'vue'
import { useAppStore } from '../stores/app'
import { CALC } from '../utils/calc'

const store = useAppStore()

const punchDate = ref(store.punchDate)
const recStart = ref('')
const recEnd = ref('')
const recRest = ref(0)

const basisName = computed(() => store.settings.basis === 'pre' ? '税前' : '税后')
const otherName = computed(() => store.settings.basis === 'pre' ? '税后' : '税前')

/* 当月工资上下文（工资按月浮动） */
const ctx = computed(() => CALC.monthCtx(store.settings.salaries, store.settings, punchDate.value.slice(0, 7)))

/* 是否处于「默认工时预填但未落库」状态（工作日且无记录） */
const isDefaultFilled = computed(() => {
  const r = store.records[punchDate.value]
  return !(r && (r.start || r.end)) && recStart.value && recEnd.value
})

/* 有效记录：已落库的优先，否则用预填的默认工时（保证页面展示自洽） */
const rec = computed(() => {
  const r = store.records[punchDate.value]
  if (r && (r.start || r.end)) return r
  if (recStart.value && recEnd.value) return { start: recStart.value, end: recEnd.value, rest: recRest.value }
  return {}
})
const m = computed(() => CALC.actualMin(rec.value, ctx.value))
const std = computed(() => CALC.stdWorkMin(ctx.value))
const ot = computed(() => isOffDay.value ? m.value : m.value - std.value)
const otLabel = computed(() => isOffDay.value ? '加班' : (ot.value >= 0 ? '加班' : '早退'))
/* 当月有效排班天数（自动模式下含假期加班天数） */
const effDays = computed(() => CALC.effDaysPerMonth(ctx.value, punchDate.value.slice(0, 7), store.holidays, store.records))
const rate = computed(() => CALC.dayRate(rec.value, ctx.value, store.settings.basis, effDays.value))
const otherRate = computed(() => CALC.dayRate(rec.value, ctx.value, store.settings.basis === 'pre' ? 'post' : 'pre', effDays.value))
const base = computed(() => CALC.baseRate(ctx.value, store.settings.basis, effDays.value))
const diff = computed(() => base.value > 0 ? (rate.value - base.value) / base.value * 100 : 0)
const dayPay = computed(() => CALC.dayPay(ctx.value, store.settings.basis, effDays.value))
const hasSalary = computed(() => CALC.salary(ctx.value, store.settings.basis) > 0)
const rest = computed(() => Number(rec.value.rest) || 0)
/* 本周摘要：跨月时按各月工资分别封顶 */
const wk = computed(() => CALC.periodStats(CALC.weekKeysTo(new Date()), store.records, ctx.value, store.settings.basis, undefined, store.holidays,
  ym => CALC.monthSalary(store.settings.salaries, store.settings, store.settings.basis, ym)))
/* 休息日/节假日标识 */
const isOffDay = computed(() => CALC.dayType(punchDate.value, store.holidays) === 'off')
const dayLabel = computed(() => {
  const name = CALC.holidayName(punchDate.value, store.holidays)
  return name ? `${name} · 法定假日` : '周末休息日'
})

const hours = CALC.fmtHours
const signed = CALC.fmtSigned
const money = CALC.fmtMoney

function nowStr() {
  const d = new Date()
  return `${String(d.getHours()).padStart(2, '0')}:${String(d.getMinutes()).padStart(2, '0')}`
}
function syncInputs() {
  const r = store.records[punchDate.value]
  if (r && (r.start || r.end)) {
    recStart.value = r.start || ''
    recEnd.value = r.end || ''
    recRest.value = Number(r.rest) > 0 ? Number(r.rest) : 0
    return
  }
  // 无记录：先等节假日数据到位再判断（避免把节假日误判为工作日而预填）
  const k = punchDate.value
  store.ensureHolidays(Number(k.slice(0, 4))).then(() => {
    if (punchDate.value !== k) return            // 等待期间已切换日期，放弃
    if (store.records[k]) return                 // 等待期间已有记录
    if (CALC.dayType(k, store.holidays) !== 'work') {
      recStart.value = ''
      recEnd.value = ''
      recRest.value = 0
      return
    }
    // 工作日：预填默认上下班时间（设置页可改，默认 08:30 / 17:30）
    recStart.value = store.settings.workStart || '08:30'
    recEnd.value = store.settings.workEnd || '17:30'
    recRest.value = 0
    // 只有「今天」直接落库一条默认打卡，免除每天手动设置；其他日期仅预填展示，修改后才保存
    if (k === CALC.dateKey(new Date())) saveRec()
  })
}
function onDateChange() {
  if (!punchDate.value) punchDate.value = CALC.dateKey(new Date())
  store.punchDate = punchDate.value
  store.ensureHolidays(Number(punchDate.value.slice(0, 4)))
  syncInputs()
}
function shiftPunch(n) {
  const d = new Date(punchDate.value + 'T00:00:00')
  punchDate.value = CALC.dateKey(CALC.addDays(d, n))
  onDateChange()
}
function goToday() { punchDate.value = CALC.dateKey(new Date()); onDateChange() }

function saveRec() {
  const k = punchDate.value
  const st = recStart.value, en = recEnd.value
  const restVal = recRest.value || 0
  if (!st && !en) {
    delete store.records[k]
  } else {
    store.records[k] = { start: st, end: en }
    if (restVal > 0) store.records[k].rest = restVal
    else delete store.records[k].rest
  }
  store.saveAll()
}
function nowStart() { recStart.value = nowStr(); saveRec() }
function nowEnd() { recEnd.value = nowStr(); saveRec() }

watch(() => store.ready, v => { if (v) { punchDate.value = store.punchDate; syncInputs() } })
</script>
