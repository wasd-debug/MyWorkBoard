<template>
  <section>
    <!-- 月份/周期导航 -->
    <div class="month-nav">
      <el-button circle size="large" @click="shift(-1)">‹</el-button>
      <div class="m num">{{ navLabel }}</div>
      <el-button circle size="large" @click="shift(1)">›</el-button>
    </div>

    <!-- 视图切换 -->
    <div class="view-tabs">
      <button v-for="t in TABS" :key="t.v" class="vtab" :class="{ on: viewMode === t.v }" @click="viewMode = t.v">{{ t.l }}</button>
    </div>

    <!-- 月度汇总 -->
    <div class="month-sum">
      <template v-if="keys.length">
        <div class="cell"><div class="v num">{{ st.days }}</div><div class="l">打卡天数</div></div>
        <div class="cell"><div class="v num">{{ hours(st.totalMin) }}h</div><div class="l">总工时</div></div>
        <div class="cell"><div class="v num" :class="st.otMin >= 0 ? 'ot' : 'up'">{{ signed(st.otMin) }}</div><div class="l">加班</div></div>
        <div class="cell"><div class="v num" style="color:var(--gold)">{{ st.realRate > 0 ? st.realRate.toFixed(1) : '—' }}</div><div class="l">实际时薪</div></div>
      </template>
      <template v-else>
        <div class="cell" style="flex:1"><div class="l">本月暂无记录</div></div>
      </template>
    </div>

    <!-- 工作日信息条 -->
    <div class="workdays-bar">
      <span>本月法定工作日 <b class="num">{{ statutory }}</b> 天</span>
      <span v-if="offWorked > 0" class="ot num">假期加班 <b class="num">{{ offWorked }}</b> 天</span>
      <span>有效排班 <b class="num">{{ effDays }}</b> 天</span>
    </div>

    <!-- 本月工资（仅月视图，每月工资可浮动调整） -->
    <div v-if="viewMode === 'month'" class="month-salary">
      <div class="ms-head">
        <div class="ms-title">本月工资<span class="ms-ym num">{{ recMonth }}</span></div>
        <span class="ms-tip">每月工资可在此单独调整，将用于本月时薪与工资统计</span>
      </div>
      <div class="ms-inputs">
        <div class="ms-item">
          <span class="ms-name">税前月薪</span>
          <el-input-number v-model="monthPre" :min="0" :step="100" :controls="false" placeholder="未设置" @change="saveMonthSalary" />
        </div>
        <div class="ms-item">
          <span class="ms-name">税后到手</span>
          <el-input-number v-model="monthPost" :min="0" :step="100" :controls="false" placeholder="未设置" @change="saveMonthSalary" />
        </div>
      </div>
    </div>

    <!-- ============ 日历视图 ============ -->
    <div v-if="viewMode !== 'list'" class="cal-card">
      <!-- 星期表头 -->
      <div class="cal-week-row">
        <div v-for="(w, i) in CALC.WEEK_CN.slice(1).concat(CALC.WEEK_CN[0])" :key="i" class="cal-wd">{{ w }}</div>
      </div>

      <!-- 月视图 -->
      <div v-if="viewMode === 'month'" class="cal-grid">
        <div v-for="n in leadBlanks" :key="'b' + n" class="cal-cell blank"></div>
        <div v-for="d in monthCells" :key="d.k" class="cal-cell" :class="cellCls(d)" @click="edit(d.k)">
          <div class="cal-head">
            <span class="cal-d num">{{ Number(d.k.slice(8)) }}</span>
            <span v-if="d.holName" class="cal-hol" :title="d.holName">{{ d.holName }}</span>
          </div>
          <div v-if="d.min > 0" class="cal-body">
            <span class="cal-h num">{{ hours(d.min) }}h</span>
            <span class="cal-o num" :class="d.ot >= 0 ? 'ot' : 'up'">{{ signed(d.ot) }}</span>
          </div>
        </div>
      </div>

      <!-- 周 / 两周视图（周几由表头行标注，单元格内不再重复，避免窄屏溢出） -->
      <div v-else class="cal-grid">
        <div v-for="d in weekCells" :key="d.k" class="cal-cell wk" :class="cellCls(d)" @click="edit(d.k)">
          <div class="cal-head">
            <span class="cal-d num">{{ d.date.getMonth() + 1 }}/{{ d.date.getDate() }}</span>
          </div>
          <div v-if="d.holName" class="cal-hol big" :title="d.holName">{{ d.holName }}</div>
          <div v-else-if="d.off" class="cal-hol dim">休息</div>
          <div v-if="d.min > 0" class="cal-body">
            <span class="cal-t num">{{ recOf(d.k).start }} – {{ recOf(d.k).end }}</span>
            <span class="cal-h num">{{ hours(d.min) }}h <b :class="d.ot >= 0 ? 'ot' : 'up'">{{ signed(d.ot) }}</b></span>
            <span v-if="restOf(d.k) > 0" class="cal-r num">休 {{ hours(restOf(d.k)) }}h</span>
            <span v-if="d.off" class="cal-off">假期加班</span>
          </div>
          <div v-else class="cal-body none">—</div>
        </div>
      </div>
    </div>

    <!-- ============ 列表视图（原有） ============ -->
    <div v-else class="rec-list">
      <el-empty v-if="!keys.length" description="这个月还没有打卡记录" :image-size="70" />
      <div v-for="k in keys" :key="k" class="rec" @click="edit(k)">
        <div class="date">
          <div class="d num">{{ Number(k.slice(8)) }}</div>
          <div class="w" :class="{ off: CALC.dayType(k, store.holidays) === 'off' }">
            {{ CALC.holidayName(k, store.holidays) || CALC.WEEK_CN[new Date(k + 'T00:00:00').getDay()] }}
          </div>
        </div>
        <div class="mid">
          <div class="t num">{{ store.records[k].start }} – {{ store.records[k].end }}</div>
          <div class="h num">
            {{ hours(actual(k)) }}h
            <small v-if="Number(store.records[k].rest) > 0" class="rest-tag">休 {{ hours(Number(store.records[k].rest)) }}h</small>
            <small v-if="CALC.dayType(k, store.holidays) === 'off'" class="hol-tag">假期加班</small>
          </div>
        </div>
        <div class="right">
          <div class="r num">{{ rateOf(k) > 0 ? rateOf(k).toFixed(1) : '—' }}</div>
          <div class="o num" :class="otOf(k) >= 0 ? 'ot' : 'up'">{{ signed(otOf(k)) }}</div>
        </div>
        <el-popconfirm title="确定删除这条打卡记录？" width="220" @confirm="remove(k)" @click.stop>
          <template #reference>
            <button class="del-btn" @click.stop title="删除">✕</button>
          </template>
        </el-popconfirm>
      </div>
    </div>
  </section>
</template>

<script setup>
import { ref, computed, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useAppStore } from '../stores/app'
import { CALC } from '../utils/calc'

const TABS = [
  { v: 'month', l: '月' },
  { v: '2week', l: '两周' },
  { v: 'week', l: '周' },
  { v: 'list', l: '列表' }
]

const store = useAppStore()
const router = useRouter()
const viewMode = ref('month')
const cursor = ref(new Date(store.recMonth + '-01T00:00:00'))

/* 导航标签：月视图显示年月，周视图显示日期区间 */
const navLabel = computed(() => {
  const c = cursor.value
  if (viewMode.value === 'month') return `${c.getFullYear()} 年 ${c.getMonth() + 1} 月`
  const span = viewMode.value === 'week' ? 7 : 14
  const s = CALC.monday(c)
  const e = CALC.addDays(s, span - 1)
  const f = d => `${d.getMonth() + 1}/${d.getDate()}`
  return `${f(s)} – ${f(e)}`
})

/* 当前期号所在月（汇总与列表按此过滤） */
const recMonth = computed(() => {
  const c = cursor.value
  return `${c.getFullYear()}-${String(c.getMonth() + 1).padStart(2, '0')}`
})
watch(recMonth, ym => {
  store.recMonth = ym
  store.ensureHolidays(Number(ym.slice(0, 4)))
  // 两周视图可能跨到下一年
  if (viewMode.value !== 'month') store.ensureHolidays(Number(ym.slice(0, 4)) + 1)
}, { immediate: true })

/* ---------- 当月工资（按月浮动） ---------- */
const ctx = computed(() => CALC.monthCtx(store.settings.salaries, store.settings, recMonth.value))

/* ---------- 汇总 ---------- */
const keys = computed(() =>
  Object.keys(store.records)
    .filter(k => k.startsWith(recMonth.value) && CALC.actualMin(store.records[k], ctx.value) > 0)
    .sort().reverse())
const effDays = computed(() => CALC.effDaysPerMonth(ctx.value, recMonth.value, store.holidays, store.records))
const st = computed(() =>
  CALC.periodStats(keys.value, store.records, ctx.value, store.settings.basis, effDays.value, store.holidays))
const statutory = computed(() => CALC.statutoryWorkdays(recMonth.value, store.holidays))
const offWorked = computed(() => CALC.offDaysWorked(recMonth.value, store.holidays, store.records, ctx.value))

/* ---------- 日历数据 ---------- */
function cellData(k, date) {
  const rec = store.records[k]
  const min = CALC.actualMin(rec, ctx.value)
  const off = CALC.dayType(k, store.holidays) === 'off'
  const std = CALC.stdWorkMin(ctx.value)
  const ot = off ? min : min - std          // 休息日上班全额计加班
  return { k, date, min, ot, off, holName: CALC.holidayName(k, store.holidays) }
}
const monthCells = computed(() => {
  const total = CALC.daysInMonth(recMonth.value)
  const out = []
  for (let i = 1; i <= total; i++) {
    const k = `${recMonth.value}-${String(i).padStart(2, '0')}`
    out.push(cellData(k, new Date(k + 'T00:00:00')))
  }
  return out
})
const leadBlanks = computed(() => (new Date(recMonth.value + '-01T00:00:00').getDay() + 6) % 7)
const weekCells = computed(() => {
  const span = viewMode.value === 'week' ? 7 : 14
  const s = CALC.monday(cursor.value)
  const out = []
  for (let i = 0; i < span; i++) {
    const d = CALC.addDays(s, i)
    out.push(cellData(CALC.dateKey(d), d))
  }
  return out
})

const todayKey = CALC.dateKey(new Date())
function cellCls(d) {
  return {
    off: d.off && d.min <= 0,
    'off-worked': d.off && d.min > 0,
    today: d.k === todayKey
  }
}
const recOf = k => store.records[k] || {}
const restOf = k => Number((store.records[k] || {}).rest) || 0

/* ---------- 导航 ---------- */
function shift(n) {
  const c = cursor.value
  if (viewMode.value === 'month') {
    cursor.value = new Date(c.getFullYear(), c.getMonth() + n, 1)
  } else {
    cursor.value = CALC.addDays(c, n * (viewMode.value === 'week' ? 7 : 14))
  }
}

const hours = CALC.fmtHours
const signed = CALC.fmtSigned
const actual = k => CALC.actualMin(store.records[k], ctx.value)
const otOf = k => {
  const m = CALC.actualMin(store.records[k], ctx.value)
  return CALC.dayType(k, store.holidays) === 'off' ? m : m - CALC.stdWorkMin(ctx.value)
}
const rateOf = k => CALC.dayRate(store.records[k], ctx.value, store.settings.basis, effDays.value)

/* ---------- 本月工资编辑（月视图） ---------- */
const monthPre = ref(0)
const monthPost = ref(0)
function syncMonthSalary() {
  const m = store.settings.salaries[recMonth.value] || {}
  monthPre.value = Number(m.pre) > 0 ? Number(m.pre) : store.settings.salaryPre
  monthPost.value = Number(m.post) > 0 ? Number(m.post) : store.settings.salaryPost
}
watch([recMonth, () => store.settings], syncMonthSalary, { immediate: true })
function saveMonthSalary() {
  const ym = recMonth.value
  store.settings.salaries[ym] = {
    pre: Number(monthPre.value) > 0 ? Number(monthPre.value) : 0,
    post: Number(monthPost.value) > 0 ? Number(monthPost.value) : 0
  }
  store.saveAll()
  ElMessage.success('本月工资已保存')
}

function edit(k) {
  store.punchDate = k
  router.push('/punch')
  window.scrollTo(0, 0)
}
function remove(k) {
  delete store.records[k]
  store.saveAll()
  ElMessage.success('已删除')
}
</script>
