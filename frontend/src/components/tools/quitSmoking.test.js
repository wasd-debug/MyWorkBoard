import assert from 'node:assert/strict'
import { test } from 'node:test'
import {
  DAY_STATUS, computeLongestStreak, computeStreak, dayStatus, estimateSaved, timeline, triggerLabel
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
