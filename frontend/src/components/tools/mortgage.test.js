import assert from 'node:assert/strict'
import { test } from 'node:test'
import {
  METHOD, STRATEGY, amortize, amortizeCombined, fromCents, prepaymentPlan, toCents
} from './mortgage.js'

const cents = value => Math.round(value * 100)

test('等额本息月供与常见房贷计算器一致', () => {
  const plan = amortize({ principal: 1_000_000, annualRatePct: 4.2, months: 360 })
  assert.ok(Math.abs(plan.monthlyPayment - 4890.17) < 2, `月供 ${plan.monthlyPayment}`)
  assert.equal(plan.months, 360)
  assert.equal(plan.schedule.length, 360)
})

test('零利率时总利息为 0 且月供等于本金均摊', () => {
  const plan = amortize({ principal: 120_000, annualRatePct: 0, months: 120 })
  assert.equal(plan.monthlyPayment, 1000)
  assert.equal(plan.totalInterest, 0)
  assert.equal(plan.totalPayment, 120_000)
})

test('等额本金首月高于末月，且本金合计等于贷款额', () => {
  const plan = amortize({ principal: 1_000_000, annualRatePct: 4.2, months: 360, method: METHOD.EQUAL_PRINCIPAL })
  assert.ok(plan.firstPayment > plan.lastPayment)
  const principalCents = plan.schedule.reduce((sum, row) => sum + cents(row.principal), 0)
  assert.equal(principalCents, 100_000_000)
  assert.equal(plan.schedule[plan.schedule.length - 1].balance, 0)
})

test('等额本息本金合计等于贷款额且末月余额归零', () => {
  const plan = amortize({ principal: 833_333.33, annualRatePct: 3.85, months: 300 })
  const principalCents = plan.schedule.reduce((sum, row) => sum + cents(row.principal), 0)
  assert.equal(principalCents, 83_333_333)
  assert.equal(plan.schedule[plan.schedule.length - 1].balance, 0)
})

test('总还款额等于本金加总利息', () => {
  const plan = amortize({ principal: 500_000, annualRatePct: 3.1, months: 240 })
  assert.equal(cents(plan.totalPayment), cents(plan.principal) + cents(plan.totalInterest))
})

test('组合贷按月相加，且短期限段结束后月供下降', () => {
  const combined = amortizeCombined([
    { label: '商贷', principal: 800_000, annualRatePct: 3.85, months: 360 },
    { label: '公积金', principal: 400_000, annualRatePct: 2.85, months: 120 }
  ])
  assert.equal(combined.principal, 1_200_000)
  assert.equal(combined.months, 360)
  assert.ok(combined.monthlyPayment > combined.schedule[200].payment)
  const principalCents = combined.schedule.reduce((sum, row) => sum + cents(row.principal), 0)
  assert.equal(principalCents, 120_000_000)
})

test('提前还款缩年限比降月供省更多利息', () => {
  const base = { principal: 1_000_000, annualRatePct: 4.2, months: 360, paidPeriods: 60, amount: 200_000 }
  const shorten = prepaymentPlan({ ...base, strategy: STRATEGY.SHORTEN_TERM })
  const reduce = prepaymentPlan({ ...base, strategy: STRATEGY.REDUCE_PAYMENT })

  assert.equal(shorten.payoff, false)
  assert.ok(shorten.interestSaved > 0)
  assert.ok(reduce.interestSaved > 0)
  assert.ok(shorten.interestSaved >= reduce.interestSaved)
  assert.ok(shorten.monthsAfter < shorten.monthsBefore)
  assert.equal(reduce.monthsAfter, reduce.monthsBefore)
  assert.ok(reduce.monthlyPaymentAfter < reduce.monthlyPaymentBefore)
})

test('等额本金提前还款缩年限时月供结构保持不变', () => {
  const plan = prepaymentPlan({
    principal: 600_000, annualRatePct: 3.5, months: 240, method: METHOD.EQUAL_PRINCIPAL,
    paidPeriods: 24, amount: 100_000, strategy: STRATEGY.SHORTEN_TERM
  })
  assert.ok(plan.monthsAfter < plan.monthsBefore)
  assert.ok(plan.interestSaved > 0)
})

test('提前还款金额覆盖剩余本金时直接结清', () => {
  const plan = prepaymentPlan({
    principal: 300_000, annualRatePct: 4.0, months: 120, paidPeriods: 100,
    amount: 500_000, strategy: STRATEGY.SHORTEN_TERM
  })
  assert.equal(plan.payoff, true)
  assert.equal(plan.balanceAfter, 0)
  assert.equal(plan.interestAfter, 0)
  assert.equal(plan.monthsAfter, 0)
})

test('放款即还（k=0）不会抛错', () => {
  const plan = prepaymentPlan({
    principal: 500_000, annualRatePct: 4.0, months: 240, paidPeriods: 0,
    amount: 50_000, strategy: STRATEGY.REDUCE_PAYMENT
  })
  assert.equal(plan.payoff, false)
  assert.equal(plan.monthsBefore, 240)
})

test('非法入参抛 RangeError', () => {
  assert.throws(() => amortize({ principal: 0, annualRatePct: 4, months: 120 }), RangeError)
  assert.throws(() => amortize({ principal: 100_000, annualRatePct: 4, months: 0 }), RangeError)
  assert.throws(() => amortize({ principal: 100_000, annualRatePct: -1, months: 120 }), RangeError)
  assert.throws(() => amortize({ principal: 100_000, annualRatePct: 4, months: 120, method: 'X' }), RangeError)
  assert.throws(() => prepaymentPlan({
    principal: 100_000, annualRatePct: 4, months: 120, paidPeriods: 130, amount: 1000
  }), RangeError)
})

test('分与元的互转保持两位精度', () => {
  assert.equal(toCents(0.1 + 0.2), 30)
  assert.equal(fromCents(489017), 4890.17)
})
