<template>
  <div>
    <section class="tool-card">
      <header>
        <h2>贷款信息</h2>
        <small>商贷与公积金可分开测算，也可按组合贷合并</small>
      </header>

      <div class="tool-segmented" role="tablist" aria-label="贷款类型">
        <button type="button" role="tab" :aria-selected="mode === 'single'" :class="{ on: mode === 'single' }" @click="mode = 'single'">单一贷款</button>
        <button type="button" role="tab" :aria-selected="mode === 'combined'" :class="{ on: mode === 'combined' }" @click="mode = 'combined'">组合贷</button>
      </div>

      <template v-if="mode === 'single'">
        <div class="tool-grid">
          <label class="tool-field"><span>贷款金额（元）</span><input v-model.number="single.principal" type="number" min="0" step="10000" inputmode="decimal" /></label>
          <label class="tool-field"><span>年利率（%）</span><input v-model.number="single.annualRatePct" type="number" min="0" step="0.05" inputmode="decimal" /></label>
          <label class="tool-field"><span>贷款期限（年）</span><input v-model.number="single.years" type="number" min="1" max="50" step="1" /></label>
          <label class="tool-field">
            <span>还款方式</span>
            <select v-model="single.method">
              <option :value="METHOD.EQUAL_INSTALLMENT">等额本息</option>
              <option :value="METHOD.EQUAL_PRINCIPAL">等额本金</option>
            </select>
          </label>
        </div>
      </template>

      <template v-else>
        <div class="tool-grid">
          <label class="tool-field"><span>商贷金额（元）</span><input v-model.number="combo.commercialPrincipal" type="number" min="0" step="10000" /></label>
          <label class="tool-field"><span>商贷年利率（%）</span><input v-model.number="combo.commercialRate" type="number" min="0" step="0.05" /></label>
          <label class="tool-field"><span>商贷期限（年）</span><input v-model.number="combo.commercialYears" type="number" min="1" max="50" /></label>
          <label class="tool-field"><span>公积金金额（元）</span><input v-model.number="combo.fundPrincipal" type="number" min="0" step="10000" /></label>
          <label class="tool-field"><span>公积金年利率（%）</span><input v-model.number="combo.fundRate" type="number" min="0" step="0.05" /></label>
          <label class="tool-field"><span>公积金期限（年）</span><input v-model.number="combo.fundYears" type="number" min="1" max="50" /></label>
        </div>
        <p class="tool-hint">组合贷默认两段都按等额本息计算；期限较短的公积金段还清后，总月供会自然下降。</p>
      </template>

      <p v-if="result.error" class="tool-error" role="alert">{{ result.error }}</p>
    </section>

    <section v-if="result.plan" class="tool-card">
      <header><h2>测算结果</h2><small>{{ result.plan.months }} 期</small></header>
      <div class="tool-stats">
        <div class="tool-stat is-accent"><b>{{ money(result.plan.monthlyPayment) }}</b><span>首月月供（元）</span></div>
        <div class="tool-stat"><b>{{ money(result.plan.totalInterest) }}</b><span>总利息（元）</span></div>
        <div class="tool-stat"><b>{{ money(result.plan.totalPayment) }}</b><span>总还款（元）</span></div>
        <div class="tool-stat"><b>{{ money(result.plan.lastPayment) }}</b><span>末月月供（元）</span></div>
      </div>

      <div v-if="result.plan.segments" class="tool-table-wrap">
        <table class="tool-table">
          <thead><tr><th>分项</th><th>本金</th><th>期限</th><th>年利率</th><th>月供</th><th>利息合计</th></tr></thead>
          <tbody>
            <tr v-for="segment in result.plan.segments" :key="segment.label">
              <td>{{ segment.label || '贷款' }}</td>
              <td>{{ money(segment.principal) }}</td>
              <td>{{ segment.months }} 期</td>
              <td>{{ segment.annualRatePct }}%</td>
              <td>{{ money(segment.monthlyPayment) }}</td>
              <td>{{ money(segment.totalInterest) }}</td>
            </tr>
          </tbody>
        </table>
      </div>

      <div class="tool-row">
        <button type="button" class="tool-btn" @click="showAll = !showAll">
          {{ showAll ? '只看前 12 期' : '显示全部还款计划' }}
        </button>
      </div>
      <div class="tool-table-wrap">
        <table class="tool-table">
          <thead><tr><th>期数</th><th>月供</th><th>本金</th><th>利息</th><th>剩余本金</th></tr></thead>
          <tbody>
            <tr v-for="row in visibleSchedule" :key="row.period">
              <td>{{ row.period }}</td>
              <td>{{ money(row.payment) }}</td>
              <td>{{ money(row.principal) }}</td>
              <td>{{ money(row.interest) }}</td>
              <td>{{ money(row.balance) }}</td>
            </tr>
          </tbody>
        </table>
      </div>
      <p class="tool-disclaimer">不含评估费、保险费、提前还款违约金；利率调整后需按新的利率重新测算。</p>
    </section>

    <section class="tool-card">
      <header>
        <h2>提前还款</h2>
        <small>缩期限与降月供的差异</small>
      </header>
      <label class="tool-row" style="gap:8px">
        <input v-model="prepayEnabled" type="checkbox" style="width:auto" />
        <span class="tool-hint">测算提前还款</span>
      </label>

      <template v-if="prepayEnabled">
        <div class="tool-grid">
          <label class="tool-field"><span>已还期数</span><input v-model.number="prepay.paidMonths" type="number" min="0" step="1" /></label>
          <label class="tool-field"><span>提前还款金额（元）</span><input v-model.number="prepay.amount" type="number" min="0" step="10000" /></label>
        </div>
        <p v-if="mode === 'combined'" class="tool-hint">组合贷请在「单一贷款」里分别测算商贷与公积金两段。</p>
        <p v-else-if="prepayment?.error" class="tool-error" role="alert">{{ prepayment.error }}</p>
        <template v-else-if="prepayment">
          <div class="tool-table-wrap">
            <table class="tool-table">
              <thead><tr><th>方案</th><th>新月供</th><th>剩余期数</th><th>节省利息</th><th>缩短期数</th></tr></thead>
              <tbody>
                <tr v-for="option in prepaymentOptions" :key="option.key">
                  <td>{{ option.label }}</td>
                  <td>{{ option.plan.payoff ? '—' : money(option.plan.monthlyPaymentAfter) }}</td>
                  <td>{{ option.plan.payoff ? '已结清' : `${option.plan.monthsAfter} 期` }}</td>
                  <td>{{ money(option.plan.interestSaved) }}</td>
                  <td>{{ option.plan.monthsSaved }} 期</td>
                </tr>
              </tbody>
            </table>
          </div>
          <p class="tool-hint">
            提前还款 {{ money(prepayment.shorten.amount) }} 元后剩余本金
            {{ money(prepayment.shorten.balanceAfter) }} 元；原计划剩余利息 {{ money(prepayment.shorten.interestBefore) }} 元。
          </p>
        </template>
      </template>
      <p v-else class="tool-hint">填入已还期数与提前还款金额后，会同时给出「缩短期限」与「降低月供」两种结果。</p>
    </section>
  </div>
</template>

<script setup>
import { computed, reactive, ref } from 'vue'
import { METHOD, STRATEGY, amortize, amortizeCombined, prepaymentPlan } from './mortgage.js'

const mode = ref('single')
const single = reactive({ principal: 1_000_000, annualRatePct: 4.2, years: 30, method: METHOD.EQUAL_INSTALLMENT })
const combo = reactive({
  commercialPrincipal: 800_000, commercialRate: 3.85, commercialYears: 30,
  fundPrincipal: 400_000, fundRate: 2.85, fundYears: 20
})
const prepayEnabled = ref(false)
const prepay = reactive({ paidMonths: 60, amount: 200_000 })
const showAll = ref(false)

const result = computed(() => {
  try {
    if (mode.value === 'single') {
      return {
        plan: amortize({
          principal: Number(single.principal),
          annualRatePct: Number(single.annualRatePct),
          months: Math.round(Number(single.years) * 12),
          method: single.method
        }),
        error: ''
      }
    }
    return {
      plan: amortizeCombined([
        {
          label: '商贷', principal: Number(combo.commercialPrincipal),
          annualRatePct: Number(combo.commercialRate), months: Math.round(Number(combo.commercialYears) * 12)
        },
        {
          label: '公积金', principal: Number(combo.fundPrincipal),
          annualRatePct: Number(combo.fundRate), months: Math.round(Number(combo.fundYears) * 12)
        }
      ]),
      error: ''
    }
  } catch (error) {
    return { plan: null, error: error.message }
  }
})

const visibleSchedule = computed(() => {
  const schedule = result.value.plan?.schedule || []
  return showAll.value ? schedule : schedule.slice(0, 12)
})

const prepayment = computed(() => {
  if (!prepayEnabled.value || mode.value !== 'single') return null
  try {
    const base = {
      principal: Number(single.principal),
      annualRatePct: Number(single.annualRatePct),
      months: Math.round(Number(single.years) * 12),
      method: single.method,
      paidPeriods: Math.round(Number(prepay.paidMonths)),
      amount: Number(prepay.amount)
    }
    return {
      shorten: prepaymentPlan({ ...base, strategy: STRATEGY.SHORTEN_TERM }),
      reduce: prepaymentPlan({ ...base, strategy: STRATEGY.REDUCE_PAYMENT })
    }
  } catch (error) {
    return { error: error.message }
  }
})

const prepaymentOptions = computed(() => {
  const value = prepayment.value
  if (!value || value.error) return []
  return [
    { key: 'shorten', label: '缩短期限（月供不变）', plan: value.shorten },
    { key: 'reduce', label: '降低月供（期限不变）', plan: value.reduce }
  ]
})

function money(value) {
  const number = Number(value)
  if (!Number.isFinite(number)) return '—'
  return number.toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
}
</script>

<style scoped>
label.tool-row { cursor: pointer; }
</style>
