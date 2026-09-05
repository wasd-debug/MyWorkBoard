<template>
  <section>
    <div class="page-heading">
      <div>
        <div class="label">WORKTIME / ARCHIVE</div>
        <h1>记录</h1>
      </div>
    </div>

    <div class="records-head">
      <div class="month-nav">
        <Button variant="icon" size="sm" @click="shift(-1)">‹</Button>
        <div class="m num">{{ navLabel }}</div>
        <Button variant="icon" size="sm" @click="shift(1)">›</Button>
      </div>
      <div class="view-tabs" role="tablist" aria-label="记录视图">
        <button v-for="tab in TABS" :key="tab.v" :class="['vtab', { on: viewMode === tab.v }]" role="tab" :aria-selected="viewMode === tab.v" type="button" @click="viewMode = tab.v">{{ tab.l }}</button>
      </div>
    </div>

    <div class="month-sum">
      <template v-if="keys.length">
        <div class="cell"><div class="v num">{{ st.days }}</div><div class="l">打卡天数</div></div>
        <div class="cell"><div class="v num">{{ hours(st.totalMin) }}h</div><div class="l">总工时</div></div>
        <div class="cell"><div class="v num" :class="st.otMin >= 0 ? 'warn' : 'up'">{{ signed(st.otMin) }}</div><div class="l">加班</div></div>
        <div class="cell"><div class="v num" style="color:var(--accent)">{{ st.realRate > 0 ? st.realRate.toFixed(1) : '—' }}</div><div class="l">实际时薪</div></div>
      </template>
        <div v-else class="cell" style="grid-column:1/-1"><div class="l">{{ periodLabel }}暂无记录</div></div>
    </div>

    <div class="workdays-bar">
      <span>{{ periodLabel }}法定工作日 <b class="num">{{ statutory }}</b> 天</span>
      <span v-if="offWorked > 0" class="warn num">假期加班 <b class="num">{{ offWorked }}</b> 天</span>
      <span>有效排班 <b class="num">{{ periodEffDays }}</b> 天</span>
    </div>

    <div v-if="viewMode === 'month'" class="month-salary">
      <div class="ms-head">
        <div class="ms-title">本月工资<span class="ms-ym num">{{ recMonth }}</span></div>
        <span class="ms-tip">每月工资可在此单独调整，将用于本月时薪与工资统计</span>
      </div>
      <div class="ms-inputs">
        <label class="ms-item"><span class="ms-name">税前月薪</span><Input type="number" min="0" step="100" :model-value="monthPre || ''" placeholder="未设置" @change="value => updateMonthSalary('pre', value)" /></label>
        <label class="ms-item"><span class="ms-name">税后到手</span><Input type="number" min="0" step="100" :model-value="monthPost || ''" placeholder="未设置" @change="value => updateMonthSalary('post', value)" /></label>
      </div>
    </div>

    <div v-if="viewMode !== 'list'" class="cal-card">
      <div class="cal-week-row">
        <div v-for="(weekDay, index) in CALC.WEEK_CN.slice(1).concat(CALC.WEEK_CN[0])" :key="index" class="cal-wd">{{ weekDay }}</div>
      </div>
      <div v-if="viewMode === 'month'" class="cal-grid">
        <div v-for="n in leadBlanks" :key="'b' + n" class="cal-cell blank"></div>
        <button v-for="day in monthCells" :key="day.k" class="cal-cell" :class="cellCls(day)" type="button" @click="edit(day.k)">
          <div class="cal-head"><span class="cal-d num">{{ Number(day.k.slice(8)) }}</span><span v-if="day.holName" class="cal-hol" :title="day.holName">{{ day.holName }}</span></div>
          <div v-if="day.min > 0" class="cal-body"><span class="cal-h num">{{ hours(day.min) }}h</span><span class="cal-o num" :class="day.ot >= 0 ? 'warn' : 'up'">{{ signed(day.ot) }}</span></div>
        </button>
      </div>
      <div v-else class="cal-grid">
        <button v-for="day in weekCells" :key="day.k" class="cal-cell wk" :class="cellCls(day)" type="button" @click="edit(day.k)">
          <div class="cal-head"><span class="cal-d num">{{ day.date.getMonth() + 1 }}/{{ day.date.getDate() }}</span></div>
          <div v-if="day.holName" class="cal-hol big" :title="day.holName">{{ day.holName }}</div>
          <div v-else-if="day.off" class="cal-hol dim">休息</div>
          <div v-if="day.min > 0" class="cal-body">
            <span class="cal-t num">{{ recOf(day.k).start }} – {{ recOf(day.k).end }}</span>
            <span class="cal-h num">{{ hours(day.min) }}h <b :class="day.ot >= 0 ? 'warn' : 'up'">{{ signed(day.ot) }}</b></span>
            <span v-if="restOf(day.k) > 0" class="cal-r num">休 {{ hours(restOf(day.k)) }}h</span>
            <span v-if="day.off" class="cal-off">假期加班</span>
          </div>
          <div v-else class="cal-body none">—</div>
        </button>
      </div>
    </div>

    <div v-else class="rec-list">
      <Empty v-if="!keys.length" description="这个月还没有打卡记录" />
      <article v-for="key in keys" :key="key" class="rec" @click="edit(key)">
        <div class="date"><div class="d num">{{ Number(key.slice(8)) }}</div><div class="w" :class="{ off: CALC.dayType(key, store.holidays) === 'off' }">{{ CALC.holidayName(key, store.holidays) || CALC.WEEK_CN[new Date(key + 'T00:00:00').getDay()] }}</div></div>
        <div class="mid">
          <div class="t num">{{ recOf(key).start || '—' }} – {{ recOf(key).end || '—' }}</div>
          <div class="h num">
            {{ hours(actual(key)) }}h
            <small v-if="restOf(key) > 0" class="rest-tag">休 {{ hours(restOf(key)) }}h</small>
            <small v-if="CALC.dayType(key, store.holidays) === 'off'" class="hol-tag">假期加班</small>
          </div>
        </div>
        <div class="right">
          <div class="r num">{{ rateOf(key) > 0 ? rateOf(key).toFixed(1) : '—' }}</div>
          <div class="o num" :class="otOf(key) >= 0 ? 'warn' : 'up'">{{ signed(otOf(key)) }}</div>
        </div>
        <button class="del-btn" type="button" @click.stop="remove(key)">×</button>
      </article>
    </div>
  </section>
</template>

<script setup>
import { ref, computed, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { useRouter } from 'vue-router'
import Input from '../components/ui/Input.vue'
import Empty from '../components/ui/Empty.vue'
import Button from '../components/ui/Button.vue'
import { useAppStore } from '../stores/app'
import { CALC } from '../utils/calc'

const TABS = [{ v: 'month', l: '月' }, { v: '2week', l: '两周' }, { v: 'week', l: '周' }, { v: 'list', l: '列表' }]
const store = useAppStore()
const router = useRouter()
const viewMode = ref('month')
const cursor = ref(new Date(store.recMonth + '-01T00:00:00'))

const navLabel = computed(() => {
  const date = cursor.value
  if (viewMode.value === 'month' || viewMode.value === 'list') return `${date.getFullYear()} 年 ${date.getMonth() + 1} 月`
  const span = viewMode.value === 'week' ? 7 : 14
  const start = CALC.monday(date)
  const end = CALC.addDays(start, span - 1)
  const format = value => `${value.getMonth() + 1}/${value.getDate()}`
  return `${format(start)} – ${format(end)}`
})
const recMonth = computed(() => `${cursor.value.getFullYear()}-${String(cursor.value.getMonth() + 1).padStart(2, '0')}`)
watch(recMonth, month => { store.recMonth = month; store.ensureHolidays(Number(month.slice(0, 4))); if (viewMode.value !== 'month') store.ensureHolidays(Number(month.slice(0, 4)) + 1) }, { immediate: true })
const ctx = computed(() => CALC.monthCtx(store.settings.salaries, store.settings, recMonth.value))
const monthKeys = computed(() => Object.keys(store.records).filter(key => key.startsWith(recMonth.value) && CALC.actualMin(store.records[key], ctx.value) > 0).sort().reverse())
const periodDateKeys = computed(() => {
  if (viewMode.value === 'month' || viewMode.value === 'list') return CALC.rangeKeys(`${recMonth.value}-01`, `${recMonth.value}-${String(CALC.daysInMonth(recMonth.value)).padStart(2, '0')}`)
  const span = viewMode.value === 'week' ? 7 : 14
  const start = CALC.monday(cursor.value)
  return CALC.rangeKeys(CALC.dateKey(start), CALC.dateKey(CALC.addDays(start, span - 1)))
})
const periodKeys = computed(() => periodDateKeys.value.filter(key => CALC.actualMin(store.records[key], ctx.value) > 0))
const keys = computed(() => viewMode.value === 'list' ? monthKeys.value : periodKeys.value)
const effDays = computed(() => CALC.effDaysPerMonth(ctx.value, recMonth.value, store.holidays, store.records))
const statutory = computed(() => periodDateKeys.value.filter(key => CALC.dayType(key, store.holidays) === 'work').length)
const offWorked = computed(() => periodDateKeys.value.filter(key => CALC.dayType(key, store.holidays) === 'off' && CALC.actualMin(store.records[key], ctx.value) > 0).length)
const periodEffDays = computed(() => statutory.value + offWorked.value)
const periodLabel = computed(() => viewMode.value === 'month' || viewMode.value === 'list' ? '本月' : viewMode.value === 'week' ? '本周' : '本周期')
const st = computed(() => CALC.periodStats(periodKeys.value, store.records, ctx.value, store.settings.basis, periodEffDays.value, store.holidays,
  ym => CALC.monthSalary(store.settings.salaries, store.settings, store.settings.basis, ym)))

function cellData(key, date) {
  const record = store.records[key]
  const min = CALC.actualMin(record, ctx.value)
  const off = CALC.dayType(key, store.holidays) === 'off'
  const ot = off ? min : min - CALC.stdWorkMin(ctx.value)
  return { k: key, date, min, ot, off, holName: CALC.holidayName(key, store.holidays) }
}
const monthCells = computed(() => {
  const total = CALC.daysInMonth(recMonth.value)
  return Array.from({ length: total }, (_, index) => {
    const key = `${recMonth.value}-${String(index + 1).padStart(2, '0')}`
    return cellData(key, new Date(key + 'T00:00:00'))
  })
})
const leadBlanks = computed(() => (new Date(recMonth.value + '-01T00:00:00').getDay() + 6) % 7)
const weekCells = computed(() => {
  const span = viewMode.value === 'week' ? 7 : 14
  const start = CALC.monday(cursor.value)
  return Array.from({ length: span }, (_, index) => {
    const date = CALC.addDays(start, index)
    return cellData(CALC.dateKey(date), date)
  })
})
const todayKey = CALC.dateKey(new Date())
const cellCls = day => ({ off: day.off && day.min <= 0, 'off-worked': day.off && day.min > 0, today: day.k === todayKey })
const recOf = key => store.records[key] || {}
const restOf = key => Number((store.records[key] || {}).rest) || 0

function shift(amount) {
  if (viewMode.value === 'month' || viewMode.value === 'list') {
    const date = cursor.value
    cursor.value = new Date(date.getFullYear(), date.getMonth() + amount, 1)
  } else {
    cursor.value = CALC.addDays(cursor.value, amount * (viewMode.value === 'week' ? 7 : 14))
  }
}
const hours = CALC.fmtHours
const signed = CALC.fmtSigned
const actual = key => CALC.actualMin(store.records[key], ctx.value)
const otOf = key => { const minutes = actual(key); return CALC.dayType(key, store.holidays) === 'off' ? minutes : minutes - CALC.stdWorkMin(ctx.value) }
const rateOf = key => CALC.dayRate(store.records[key], ctx.value, store.settings.basis, effDays.value)

const monthPre = ref(0)
const monthPost = ref(0)
function syncMonthSalary() {
  const salary = store.settings.salaries[recMonth.value] || {}
  monthPre.value = Number(salary.pre) > 0 ? Number(salary.pre) : store.settings.salaryPre
  monthPost.value = Number(salary.post) > 0 ? Number(salary.post) : store.settings.salaryPost
}
watch([recMonth, () => store.settings], syncMonthSalary, { immediate: true })
function updateMonthSalary(key, value) {
  if (key === 'pre') monthPre.value = Number(value) || 0
  else monthPost.value = Number(value) || 0
  saveMonthSalary()
}
function saveMonthSalary() {
  store.settings.salaries[recMonth.value] = {
    pre: Number(monthPre.value) > 0 ? Number(monthPre.value) : 0,
    post: Number(monthPost.value) > 0 ? Number(monthPost.value) : 0
  }
  store.saveAll()
  ElMessage.success('本月工资已保存')
}
function edit(key) { store.punchDate = key; router.push('/punch'); window.scrollTo(0, 0) }
function remove(key) {
  if (!window.confirm('确定删除这条打卡记录？')) return
  delete store.records[key]
  store.saveAll()
  ElMessage.success('已删除')
}
</script>
