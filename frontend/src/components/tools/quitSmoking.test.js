import assert from 'node:assert/strict'
import { test } from 'node:test'
import {
  CIGARETTE_TYPES, DAY_STATUS, MILESTONE_CATEGORIES, cigaretteType, computeLongestStreak, computeStreak,
  dayStatus, estimateSaved, estimateSubstance, formatMg, formatRemaining, groupMilestones, timeline, triggerLabel
} from './quitSmoking.js'

test('三种日状态判定', () => {
  assert.equal(dayStatus(0), DAY_STATUS.CLEAN)
  assert.equal(dayStatus(3), DAY_STATUS.RELAPSE)
  assert.equal(dayStatus(null), DAY_STATUS.UNLOGGED)
  assert.equal(dayStatus(undefined), DAY_STATUS.UNLOGGED)
})

test('热力条按时间升序生成最近 N 天', () => {
  const days = {
    '2026-11-05': { date: '2026-11-05', smokedCount: 2 },
    '2026-11-04': { date: '2026-11-04', smokedCount: 0 }
  }
  const rows = timeline(days, '2026-11-05', 3)
  assert.deepEqual(rows.map(row => row.date), ['2026-11-03', '2026-11-04', '2026-11-05'])
  assert.deepEqual(rows.map(row => row.status),
    [DAY_STATUS.UNLOGGED, DAY_STATUS.CLEAN, DAY_STATUS.RELAPSE])
})

test('连续天数把未记录的日期也算作无烟', () => {
  assert.equal(computeStreak({}, '2026-11-05', { start: '2026-11-05' }), 1)
  assert.equal(computeStreak({ '2026-11-05': { smokedCount: 0 } }, '2026-11-05', { start: '2026-11-03' }), 3)
  // 没有起点信息时按最大回溯范围封顶，不会无限增长
  assert.equal(computeStreak({}, '2026-11-05', { maxDays: 7 }), 7)
})

test('今天吸烟时连续天数为 0', () => {
  const days = { '2026-11-05': { smokedCount: 1 } }
  assert.equal(computeStreak(days, '2026-11-05'), 0)
})

test('昨天吸烟时今天仍算连续 1 天', () => {
  const days = { '2026-11-04': { smokedCount: 1 } }
  assert.equal(computeStreak(days, '2026-11-05'), 1)
})

test('最长连续取区间内的最大值', () => {
  const days = {
    '2026-11-03': { smokedCount: 1 },
    '2026-11-06': { smokedCount: 1 }
  }
  // 11-01..11-02 = 2 天，11-04..11-05 = 2 天，11-07 = 1 天
  assert.equal(computeLongestStreak(days, '2026-11-01', '2026-11-07'), 2)
})

test('省下金额为正负两种情形', () => {
  assert.equal(estimateSaved({
    elapsedDays: 5, baselinePerDay: 20, pricePerPack: 20, cigarettesPerPack: 20, totalSmoked: 5
  }), 95)
  assert.equal(estimateSaved({
    elapsedDays: 5, baselinePerDay: 5, pricePerPack: 30, cigarettesPerPack: 20, totalSmoked: 40
  }), -22.5)
  assert.equal(estimateSaved({ elapsedDays: 5, baselinePerDay: 20, pricePerPack: 0 }), 0)
})

test('触发场景有中文标签兜底', () => {
  assert.equal(triggerLabel('after-meal'), '饭后')
  assert.equal(triggerLabel(''), '其他')
})

test('剩余时间按分钟/小时/天三档格式化', () => {
  assert.equal(formatRemaining(0), '已达成')
  assert.equal(formatRemaining(-5), '已达成')
  assert.equal(formatRemaining(45), '45 分钟')
  assert.equal(formatRemaining(120), '2 小时')
  assert.equal(formatRemaining(125), '2 小时 5 分')
  assert.equal(formatRemaining(1440), '1 天')
  assert.equal(formatRemaining(1500), '1 天 1 小时')
  assert.equal(formatRemaining(525600), '365 天')
  assert.equal(formatRemaining(Number.NaN), '—')
  // 进位边界：1440 分钟余数凑满 24 小时后必须并进天数，不能出现「10 天 24 小时」
  assert.equal(formatRemaining(10 * 1440 + 1439), '11 天')
  assert.equal(formatRemaining(119), '1 小时 59 分')
  assert.equal(formatRemaining(150), '2 小时 30 分')
})

test('毫克数超过 1000 换成克', () => {
  assert.equal(formatMg(0), '0.0 mg')
  assert.equal(formatMg(24), '24.0 mg')
  assert.equal(formatMg(999.9), '999.9 mg')
  assert.equal(formatMg(12340), '12.34 g')
  assert.equal(formatMg(-165), '-165.0 mg')
  assert.equal(formatMg(undefined), '0 mg')
})

test('香烟种类预设与后端保持一致', () => {
  // 与 backend CigaretteTypes 一一对应；改一处必须同步另一处
  const expected = {
    regular: [11, 12],
    fine: [8, 8],
    medium: [9, 9],
    thick: [13, 14],
    'hand-rolled': [12, 14],
    cigar: [15, 15],
    ecig: [0, 0]
  }
  for (const [key, [tar, co]] of Object.entries(expected)) {
    const preset = CIGARETTE_TYPES.find(item => item.key === key)
    assert.ok(preset, `缺少预设 ${key}`)
    assert.equal(preset.tarMg, tar, `${key} 焦油量不一致`)
    assert.equal(preset.coMg, co, `${key} 一氧化碳量不一致`)
  }
  assert.equal(cigaretteType('other').tarMg, null, '自定义不带预设数值')
  assert.equal(cigaretteType('unknown').key, 'regular', '未知种类回落到普通卷烟')
})

test('里程碑按四个维度分组且保持后端顺序之外的稳定顺序', () => {
  const groups = groupMilestones([
    { key: 'lung-72h', category: 'lung' },
    { key: 'vascular-20min', category: 'vascular' },
    { key: 'physiological-24h', category: 'physiological' },
    { key: 'psych-3m', category: 'psychological' },
    { key: 'unknown-1', category: 'other' }
  ])
  assert.deepEqual(groups.map(group => group.key),
    ['physiological', 'psychological', 'vascular', 'lung', 'other'])
  assert.equal(groups[0].label, '生理依赖')
  assert.equal(groups[4].label, 'other')
  assert.deepEqual(groupMilestones([]), [])
  assert.equal(MILESTONE_CATEGORIES.length, 4)
})

test('乐观更新的焦油量按当日支数与每支含量计算', () => {
  const result = estimateSubstance({
    todaySmoked: 3, elapsedDays: 5, baselinePerDay: 20, totalSmoked: 3,
    tarPerCigarette: 8, coPerCigarette: 8
  })
  assert.equal(result.todayTarMg, 24)
  assert.equal(result.todayCoMg, 24)
  // 基线 100 支 − 实际 3 支 = 97 支
  assert.equal(result.avoidedTarMg, 776)
  assert.equal(result.avoidedCoMg, 776)
  assert.deepEqual(estimateSubstance(), { todayTarMg: 0, todayCoMg: 0, avoidedTarMg: 0, avoidedCoMg: 0 })
})
