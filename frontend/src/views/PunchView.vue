<template>
  <section>
    <div class="page-heading">
      <div>
        <div class="label">WORKTIME / DAILY ENTRY</div>
        <h1>打卡</h1>
      </div>
      <div class="punch-date-nav">
        <Button variant="icon" size="sm" @click="shiftPunch(-1)">‹</Button>
        <Input v-model="punchDate" type="date" @change="onDateChange" />
        <Button variant="icon" size="sm" @click="shiftPunch(1)">›</Button>
        <Button size="sm" variant="ghost" @click="goToday">今天</Button>
      </div>
    </div>

    <div v-if="isOffDay" class="punch-offday">
      <span class="chip warn">{{ dayLabel }}</span>
      <span v-if="m > 0">假期加班 · 计入本月工作日</span>
      <span v-else>休息日打卡将按假期加班计入</span>
    </div>

    <div class="punch-grid">
      <div class="punch-main">
        <div class="label">今日实际时薪 · {{ basisName }}</div>
        <div class="rate-line">
          <template v-if="m > 0 && hasSalary">
            <div class="big-rate num">{{ rate.toFixed(2) }}<span class="unit">¥/h</span></div>
          </template>
          <template v-else>
            <div class="big-rate hint">{{ m > 0 ? '请设置本月工资' : '填写时间自动计算' }}</div>
          </template>
        </div>
        <div v-if="m > 0 && hasSalary" class="rate-sub">
          人民币 / 小时 · {{ basisName === '税前' ? '税后口径' : '税前口径' }} <span class="num">{{ money(otherRate) }}/h</span>
        </div>
        <div v-if="m > 0 && hasSalary" class="rate-chip-row">
          <span class="chip num" :class="diff >= 0 ? '' : 'warn'">{{ diff >= 0 ? '▲' : '▼' }} {{ Math.abs(diff).toFixed(1) }}%</span>
          <span class="muted">{{ diff >= 0 ? '高于' : '低于' }}基准 {{ money(base) }}/h</span>
        </div>

        <div class="punch-times">
          <div class="time-entry">
            <div class="label">实际上班 · IN</div>
            <Input v-model="recStart" type="time" @change="saveRec" />
            <button class="now-btn" type="button" @click="nowStart">记录现在 →</button>
          </div>
          <div class="time-entry">
            <div class="label">实际下班 · OUT</div>
            <Input v-model="recEnd" type="time" @change="saveRec" />
            <button class="now-btn" type="button" @click="nowEnd">记录现在 →</button>
          </div>
        </div>

        <div class="rest-input-row">
          <span class="lbl">自定义休息 BREAK</span>
          <Input v-model="recRest" type="number" min="0" max="600" step="5" @change="saveRec" />
          <span class="hint">分钟 · 摸鱼 / 晚饭 / 健身</span>
        </div>
      </div>

      <div class="punch-breakdown">
        <div class="label">今日明细 BREAKDOWN</div>
        <dl class="breakdown-dl">
          <div class="hrow"><dt>标准工时</dt><dd class="num">{{ hours(std) }}h</dd></div>
          <div class="hrow"><dt>实际工时</dt><dd class="num">{{ hours(m) }}h</dd></div>
          <div class="hrow"><dt>{{ otLabel }}</dt><dd class="num" :class="ot >= 0 ? 'warn' : 'up'">{{ signed(ot) }}</dd></div>
          <div class="hrow"><dt>今日日薪</dt><dd class="num">{{ money(dayPay) }}</dd></div>
          <div v-if="rest > 0" class="hrow"><dt>自定义休息</dt><dd class="num">{{ hours(rest) }}h</dd></div>
          <div class="hrow"><dt>时薪达成率</dt><dd class="num" :class="diff >= 0 ? 'up' : 'down'">{{ m > 0 && base > 0 ? (rate / base * 100).toFixed(1) + '%' : '—' }}</dd></div>
        </dl>

        <div class="label" style="margin-top: 22px">本周 WEEK</div>
        <div class="mini-week">
          <div v-for="day in miniWeek" :key="day.k" class="bar-wrap">
            <div class="bar" :class="{ today: day.k === punchDate }" :style="{ height: day.pct + '%' }" :data-h="day.h + 'h'"></div>
            <span class="day">{{ day.label }}</span>
          </div>
        </div>
        <div class="punch-summary">
          <span>{{ wk.days }} 天 · {{ hours(wk.totalMin) }}h · 加班 <b :class="wk.otMin >= 0 ? 'warn' : 'up'">{{ signed(wk.otMin) }}</b></span>
          <span v-if="wk.realRate > 0">AVG <b class="num">{{ money(wk.realRate) }}/h</b></span>
        </div>
      </div>
    </div>
  </section>
</template>

<script setup>
import { ref, computed, watch } from 'vue'
import Button from '../components/ui/Button.vue'
import Input from '../components/ui/Input.vue'
import { useAppStore } from '../stores/app'
import { CALC } from '../utils/calc'

const store = useAppStore()
const punchDate = ref(store.punchDate)
const recStart = ref('')
const recEnd = ref('')
const recRest = ref(0)

const basisName = computed(() => store.settings.basis === 'pre' ? '税前' : '税后')
const ctx = computed(() => CALC.monthCtx(store.settings.salaries, store.settings, punchDate.value.slice(0, 7)))
const rec = computed(() => {
  const record = store.records[punchDate.value]
  if (record && (record.start || record.end)) return record
  if (recStart.value && recEnd.value) return { start: recStart.value, end: recEnd.value, rest: recRest.value }
  return {}
})
const m = computed(() => CALC.actualMin(rec.value, ctx.value))
const std = computed(() => CALC.stdWorkMin(ctx.value))
const ot = computed(() => isOffDay.value ? m.value : m.value - std.value)
const otLabel = computed(() => isOffDay.value ? '加班 OT' : (ot.value >= 0 ? '加班 OT' : '早退 EARLY'))
const effDays = computed(() => CALC.effDaysPerMonth(ctx.value, punchDate.value.slice(0, 7), store.holidays, store.records))
const rate = computed(() => CALC.dayRate(rec.value, ctx.value, store.settings.basis, effDays.value))
const otherRate = computed(() => CALC.dayRate(rec.value, ctx.value, store.settings.basis === 'pre' ? 'post' : 'pre', effDays.value))
const base = computed(() => CALC.baseRate(ctx.value, store.settings.basis, effDays.value))
const diff = computed(() => base.value > 0 ? (rate.value - base.value) / base.value * 100 : 0)
const dayPay = computed(() => CALC.dayPay(ctx.value, store.settings.basis, effDays.value))
const hasSalary = computed(() => CALC.salary(ctx.value, store.settings.basis) > 0)
const rest = computed(() => Number(rec.value.rest) || 0)
const wk = computed(() => CALC.periodStats(CALC.weekKeysTo(new Date(punchDate.value + 'T00:00:00')), store.records, ctx.value, store.settings.basis, undefined, store.holidays, ym => CALC.monthSalary(store.settings.salaries, store.settings, store.settings.basis, ym)))
const isOffDay = computed(() => CALC.dayType(punchDate.value, store.holidays) === 'off')
const dayLabel = computed(() => { const name = CALC.holidayName(punchDate.value, store.holidays); return name ? `${name} · 法定假日` : '周末休息日' })

const hours = CALC.fmtHours
const signed = CALC.fmtSigned
const money = CALC.fmtMoney

const miniWeek = computed(() => {
  const keys = CALC.weekKeysTo(new Date(punchDate.value + 'T00:00:00'))
  const max = Math.max(1, ...keys.map(k => CALC.actualMin(store.records[k], ctx.value) / 60))
  return keys.map(k => {
    const date = new Date(k + 'T00:00:00')
    const h = CALC.actualMin(store.records[k], ctx.value) / 60
    return { k, label: CALC.WEEK_CN[date.getDay()].slice(-1), h: Number(h.toFixed(1)), pct: Math.max(4, (h / max) * 100) }
  })
})

function nowStr() { const date = new Date(); return `${String(date.getHours()).padStart(2, '0')}:${String(date.getMinutes()).padStart(2, '0')}` }
function syncInputs() {
  const record = store.records[punchDate.value]
  if (record && (record.start || record.end)) { recStart.value = record.start || ''; recEnd.value = record.end || ''; recRest.value = Number(record.rest) > 0 ? Number(record.rest) : 0; return }
  const key = punchDate.value
  store.ensureHolidays(Number(key.slice(0, 4))).then(() => {
    if (punchDate.value !== key || store.records[key]) return
    if (CALC.dayType(key, store.holidays) !== 'work') { recStart.value = ''; recEnd.value = ''; recRest.value = 0; return }
    recStart.value = store.settings.workStart || '08:30'; recEnd.value = store.settings.workEnd || '17:30'; recRest.value = 0
    if (key === CALC.dateKey(new Date())) saveRec()
  })
}
function onDateChange() { if (!punchDate.value) punchDate.value = CALC.dateKey(new Date()); store.punchDate = punchDate.value; store.ensureHolidays(Number(punchDate.value.slice(0, 4))); syncInputs() }
function shiftPunch(amount) { const date = new Date(punchDate.value + 'T00:00:00'); punchDate.value = CALC.dateKey(CALC.addDays(date, amount)); onDateChange() }
function goToday() { punchDate.value = CALC.dateKey(new Date()); onDateChange() }
function saveRec() {
  const key = punchDate.value; const restValue = Number(recRest.value) || 0
  if (!recStart.value && !recEnd.value) delete store.records[key]
  else { store.records[key] = { start: recStart.value, end: recEnd.value }; if (restValue > 0) store.records[key].rest = restValue }
  store.saveAll()
}
function nowStart() { recStart.value = nowStr(); saveRec() }
function nowEnd() { recEnd.value = nowStr(); saveRec() }
watch(() => store.ready, value => { if (value) { punchDate.value = store.punchDate; syncInputs() } })
</script>
