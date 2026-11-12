import assert from 'node:assert/strict'
import { test } from 'node:test'
import {
  annualTax, bonusTaxSeparate, compareBonus, monthlyWithholding, socialInsuranceEstimate
} from './tax.js'

test('年度综合所得税率表在级距边界上连续', () => {
  assert.equal(annualTax(0), 0)
  assert.equal(annualTax(36000), 1080)
  assert.equal(annualTax(144000), 11880)
  assert.equal(annualTax(300000), 43080)
  assert.equal(annualTax(420000), 73080)
  assert.equal(annualTax(660000), 145080)
  assert.equal(annualTax(960000), 250080)
  // 边界两侧税额只差该级距的税率增量，不会跳变
  assert.ok(Math.abs(annualTax(36001) - annualTax(36000)) < 20)
})

test('负数或非法应税额返回 0', () => {
  assert.equal(annualTax(-100), 0)
  assert.equal(annualTax(Number.NaN), 0)
})

test('年终奖单独计税按除以 12 查月度税率表', () => {
  assert.equal(bonusTaxSeparate(36000), 1080)
  assert.equal(bonusTaxSeparate(0), 0)
  // 36001 / 12 = 3000.08 → 进入 10% 档
  assert.equal(bonusTaxSeparate(36001), 3390.1)
})

test('累计预扣法逐月递增，年度合计等于年度税额', () => {
  const result = monthlyWithholding({
    monthlyIncome: 30000,
    socialInsuranceMonthly: 5000,
    specialAdditionalMonthly: 3000,
    months: 12
  })
  assert.equal(result.rows.length, 12)
  assert.ok(result.rows[0].tax < result.rows[11].tax)
  // (30000 - 5000 - 5000 - 3000) × 12 = 204000 → 20% 档
  assert.equal(result.totalTax, annualTax(204000))
  assert.equal(result.rows[0].net, 30000 - 5000 - result.rows[0].tax)
})

test('零收入不产生税款', () => {
  const result = monthlyWithholding({ monthlyIncome: 0, months: 6 })
  assert.equal(result.totalTax, 0)
  assert.equal(result.rows.length, 6)
})

test('低于起征点时税额为 0', () => {
  const result = monthlyWithholding({ monthlyIncome: 4000, months: 12 })
  assert.equal(result.totalTax, 0)
})

test('年终奖单独计税与并入综合所得择优', () => {
  const comparison = compareBonus({ annualTaxable: 204000, bonus: 60000 })
  // 60000 / 12 = 5000 → 10% 档，速算扣除 210
  assert.equal(comparison.separate.bonusTax, 5790)
  assert.equal(comparison.separate.salaryTax, 23880)
  assert.equal(comparison.combined.total, 35880)
  assert.equal(comparison.better, 'SEPARATE')
  assert.equal(comparison.delta, 6210)
  assert.ok(comparison.delta > 0)
})

test('无年终奖时两种口径相同', () => {
  const comparison = compareBonus({ annualTaxable: 100000, bonus: 0 })
  assert.equal(comparison.better, 'SAME')
  assert.equal(comparison.delta, 0)
})

test('五险一金按比例估算', () => {
  const estimate = socialInsuranceEstimate({ salaryBase: 20000 })
  assert.equal(estimate.pension, 1600)
  assert.equal(estimate.medical, 403)
  assert.equal(estimate.unemployment, 100)
  assert.equal(estimate.housingFund, 2400)
  assert.equal(estimate.total, 4503)
})

test('非法月份数量抛 RangeError', () => {
  assert.throws(() => monthlyWithholding({ monthlyIncome: 1000, months: 0 }), RangeError)
  assert.throws(() => monthlyWithholding({ monthlyIncome: 1000, months: 13 }), RangeError)
})
