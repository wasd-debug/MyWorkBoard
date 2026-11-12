<template>
  <div>
    <section class="tool-card">
      <header><h2>相差天数</h2><small>含节假日数据时同时给出工作日</small></header>
      <div class="tool-grid">
        <label class="tool-field"><span>开始日期</span><input v-model="diff.from" type="date" /></label>
        <label class="tool-field"><span>结束日期</span><input v-model="diff.to" type="date" /></label>
      </div>
      <p v-if="diffResult.error" class="tool-error" role="alert">{{ diffResult.error }}</p>
      <div v-else class="tool-stats">
        <div class="tool-stat is-accent"><b>{{ diffResult.days }}</b><span>相差天数（不含首日）</span></div>
        <div class="tool-stat"><b>{{ diffResult.inclusive }}</b><span>含首尾共（天）</span></div>
        <div class="tool-stat"><b>{{ diffResult.weeks }}</b><span>折合周数</span></div>
        <div class="tool-stat"><b>{{ diffResult.businessDays }}</b><span>其中工作日</span></div>
      </div>
      <p class="tool-hint">工作日按周一至周五计算，并排除法定节假日、计入调休补班（数据来自工作台节假日接口）。</p>
    </section>

    <section class="tool-card">
      <header><h2>日期推算</h2><small>月末自动夹取，1/31 加 1 月得到 2/28</small></header>
      <div class="tool-grid">
        <label class="tool-field"><span>基准日期</span><input v-model="shift.base" type="date" /></label>
        <label class="tool-field"><span>加/减 年</span><input v-model.number="shift.years" type="number" step="1" /></label>
        <label class="tool-field"><span>加/减 月</span><input v-model.number="shift.months" type="number" step="1" /></label>
        <label class="tool-field"><span>加/减 天</span><input v-model.number="shift.days" type="number" step="1" /></label>
      </div>
      <p v-if="shiftResult.error" class="tool-error" role="alert">{{ shiftResult.error }}</p>
      <div v-else class="tool-stats">
        <div class="tool-stat is-accent"><b>{{ shiftResult.result }}</b><span>{{ shiftResult.weekday }}</span></div>
        <div class="tool-stat"><b>{{ shiftResult.fromToday }}</b><span>距今天数</span></div>
      </div>
    </section>

    <section class="tool-card">
      <header><h2>工作日推算</h2><small>跳过周末、节假日，遇到调休补班算工作日</small></header>
      <div class="tool-grid">
        <label class="tool-field"><span>基准日期</span><input v-model="business.base" type="date" /></label>
        <label class="tool-field"><span>往后（负数往前）N 个工作日</span><input v-model.number="business.offset" type="number" step="1" /></label>
      </div>
      <p v-if="businessResult.error" class="tool-error" role="alert">{{ businessResult.error }}</p>
      <div v-else class="tool-stats">
        <div class="tool-stat is-accent"><b>{{ businessResult.result }}</b><span>{{ businessResult.weekday }}</span></div>
        <div class="tool-stat"><b>{{ businessResult.span }}</b><span>自然日跨度</span></div>
      </div>
    </section>
  </div>
</template>

<script setup>
import { computed, reactive } from 'vue'
import { useAppStore } from '../../stores/app.js'
import {
  addDate, businessDaysBetween, diffDays, inclusiveDays, shiftBusinessDays, todayKey
} from './dateMath.js'

const store = useAppStore()
const today = todayKey()

const diff = reactive({ from: today, to: addDate(today, { months: 1 }) })
const shift = reactive({ base: today, years: 0, months: 0, days: 30 })
const business = reactive({ base: today, offset: 5 })

const holidays = computed(() => store.holidays || {})

const diffResult = computed(() => {
  try {
    const days = diffDays(diff.from, diff.to)
    const businessDays = businessDaysBetween(diff.from, diff.to, holidays.value)
    return {
      days,
      inclusive: inclusiveDays(diff.from, diff.to),
      weeks: (Math.abs(days) / 7).toFixed(1),
      businessDays,
      error: ''
    }
  } catch (error) {
    return { error: error.message }
  }
})

const shiftResult = computed(() => {
  try {
    const result = addDate(shift.base, {
      years: Number(shift.years) || 0,
      months: Number(shift.months) || 0,
      days: Number(shift.days) || 0
    })
    return { result, weekday: weekdayLabel(result), fromToday: diffDays(today, result), error: '' }
  } catch (error) {
    return { error: error.message }
  }
})

const businessResult = computed(() => {
  try {
    const result = shiftBusinessDays(business.base, Number(business.offset) || 0, holidays.value)
    return {
      result,
      weekday: weekdayLabel(result),
      span: diffDays(business.base, result),
      error: ''
    }
  } catch (error) {
    return { error: error.message }
  }
})

function weekdayLabel(value) {
  const date = new Date(`${value}T00:00:00`)
  if (Number.isNaN(date.getTime())) return ''
  return ['周日', '周一', '周二', '周三', '周四', '周五', '周六'][date.getDay()]
}
</script>
