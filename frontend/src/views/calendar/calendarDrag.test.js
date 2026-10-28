import test from 'node:test'
import assert from 'node:assert/strict'
import {
  dragRange, formatDuration, moveBlock, normalizeDayRange, pointerToMinutes, resizeBlock, spanToInstants
} from './calendarDrag.js'

test('pointerToMinutes 从可见窗口起点换算并吸附 15 分钟', () => {
  assert.equal(pointerToMinutes(0, 48, 420), 420)
  assert.equal(pointerToMinutes(48, 48, 420), 480)
  assert.equal(pointerToMinutes(50, 48, 420), 480)
  assert.equal(pointerToMinutes(-100, 48, 420), 420)
})

test('dragRange 自动排序、补最小 15 分钟并封顶当天', () => {
  assert.deepEqual(dragRange(600, 660), { startMinutes: 600, endMinutes: 660, durationMinutes: 60 })
  assert.deepEqual(dragRange(660, 600), { startMinutes: 600, endMinutes: 660, durationMinutes: 60 })
  assert.deepEqual(dragRange(600, 600), { startMinutes: 600, endMinutes: 615, durationMinutes: 15 })
  assert.equal(dragRange(1400, 1600).endMinutes, 1440)
})

test('moveBlock 保持时长并向边界回弹', () => {
  assert.deepEqual(moveBlock(600, 60, 30), { startMinutes: 630, endMinutes: 690 })
  assert.deepEqual(moveBlock(600, 60, -700), { startMinutes: 0, endMinutes: 60 })
  assert.deepEqual(moveBlock(1380, 60, 600), { startMinutes: 1380, endMinutes: 1440 })
})

test('resizeBlock 上下沿改变时长且不小于 15 分钟', () => {
  assert.deepEqual(resizeBlock(600, 660, 'end', 30), { startMinutes: 600, endMinutes: 690 })
  assert.deepEqual(resizeBlock(600, 660, 'start', -30), { startMinutes: 570, endMinutes: 660 })
  assert.deepEqual(resizeBlock(600, 660, 'start', 600), { startMinutes: 645, endMinutes: 660 })
  assert.deepEqual(resizeBlock(600, 660, 'end', -600), { startMinutes: 600, endMinutes: 615 })
})

test('normalizeDayRange 处理反向拖选', () => {
  assert.deepEqual(normalizeDayRange('2026-10-19', '2026-10-21'), { startKey: '2026-10-19', endKey: '2026-10-21' })
  assert.deepEqual(normalizeDayRange('2026-10-21', '2026-10-19'), { startKey: '2026-10-19', endKey: '2026-10-21' })
  assert.equal(normalizeDayRange('', '2026-10-19'), null)
})

test('spanToInstants 生成按本地零点对齐的起止时刻', () => {
  const span = spanToInstants('2026-10-19', '2026-10-21')
  const start = new Date(span.startAt)
  const end = new Date(span.dueAt)
  assert.equal(start.getFullYear(), 2026)
  assert.equal(start.getMonth(), 9)
  assert.equal(start.getDate(), 19)
  assert.equal(start.getHours(), 0)
  assert.equal(end.getDate(), 21)
})

test('formatDuration 输出中文时长', () => {
  assert.equal(formatDuration(15), '15 分钟')
  assert.equal(formatDuration(60), '1 小时')
  assert.equal(formatDuration(90), '1 小时 30 分钟')
})
