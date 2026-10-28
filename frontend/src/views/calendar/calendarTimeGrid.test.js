import test from 'node:test'
import assert from 'node:assert/strict'
import {
  ALL_DAY_HEIGHT, blockGeometry, clampAllDayHeight, hourMarks, layoutColumns, minutesToLabel, snapMinutes, visibleWindow
} from './calendarTimeGrid.js'

test('visibleWindow 默认收起 00:00–07:00 与 23:00–24:00', () => {
  const window = visibleWindow()
  assert.equal(window.start, 420)
  assert.equal(window.end, 1380)
  assert.equal(window.minutes, 960)
})

test('visibleWindow 保证至少两小时可见并夹取越界值', () => {
  const narrow = visibleWindow(700, 760)
  assert.ok(narrow.minutes >= 120)
  assert.equal(narrow.start, 700)
  assert.equal(narrow.end, 820)
  const negative = visibleWindow(-100, 2000)
  assert.equal(negative.start, 0)
  assert.equal(negative.end, 1440)
})

test('snapMinutes 吸附到 15 分钟并限制在一天内', () => {
  assert.equal(snapMinutes(487), 480)
  assert.equal(snapMinutes(488), 495)
  assert.equal(snapMinutes(-30), 0)
  assert.equal(snapMinutes(1600), 1440)
})

test('blockGeometry 按可见窗口换算位置', () => {
  const window = visibleWindow(420, 1380)
  const block = blockGeometry(540, 630, window, 48)
  assert.equal(block.top, 96)
  assert.equal(block.height, 70)
  assert.equal(block.clippedStart, false)
})

test('blockGeometry 裁掉窗口外的时间并保证最小高度', () => {
  const window = visibleWindow(420, 1380)
  const early = blockGeometry(360, 480, window, 48)
  assert.equal(early.top, 0)
  assert.equal(early.clippedStart, true)
  const tiny = blockGeometry(600, 602, window, 48)
  assert.ok(tiny.height >= 16)
})

test('layoutColumns 把重叠事件并排分列', () => {
  const layout = layoutColumns([
    { id: 'a', startMinutes: 540, endMinutes: 600 },
    { id: 'b', startMinutes: 570, endMinutes: 660 }
  ])
  assert.equal(layout.get('a').columns, 2)
  assert.equal(layout.get('b').columns, 2)
  assert.notEqual(layout.get('a').column, layout.get('b').column)
})

test('layoutColumns 对完全不重叠的事件各占满宽', () => {
  const layout = layoutColumns([
    { id: 'a', startMinutes: 540, endMinutes: 600 },
    { id: 'b', startMinutes: 600, endMinutes: 660 }
  ])
  assert.equal(layout.get('a').columns, 1)
  assert.equal(layout.get('b').columns, 1)
})

test('layoutColumns 三事件重叠时按列错开且簇内列数一致', () => {
  const layout = layoutColumns([
    { id: 'a', startMinutes: 540, endMinutes: 720 },
    { id: 'b', startMinutes: 550, endMinutes: 600 },
    { id: 'c', startMinutes: 560, endMinutes: 610 }
  ])
  const columns = ['a', 'b', 'c'].map(id => layout.get(id).column)
  assert.equal(new Set(columns).size, 3)
  for (const id of ['a', 'b', 'c']) assert.equal(layout.get(id).columns, 3)
})

test('layoutColumns 对不重叠的后续事件重开一簇占满宽度', () => {
  const layout = layoutColumns([
    { id: 'a', startMinutes: 540, endMinutes: 570 },
    { id: 'b', startMinutes: 540, endMinutes: 570 },
    { id: 'c', startMinutes: 575, endMinutes: 600 }
  ])
  assert.equal(layout.get('c').columns, 1)
  assert.equal(layout.get('c').column, 0)
})

test('minutesToLabel 处理正午与补零', () => {
  assert.equal(minutesToLabel(0), '00:00')
  assert.equal(minutesToLabel(720), '正午')
  assert.equal(minutesToLabel(750), '12:30')
  assert.equal(minutesToLabel(1380), '23:00')
})

test('hourMarks 从可见窗口起点开始逐小时标记', () => {
  const marks = hourMarks(visibleWindow(420, 1380))
  assert.equal(marks[0].minutes, 420)
  assert.equal(marks[0].label, '07:00')
  assert.equal(marks.at(-1).minutes, 1380)
  assert.equal(marks.length, 17)
})

test('clampAllDayHeight 限制全天区高度', () => {
  assert.equal(clampAllDayHeight(10), ALL_DAY_HEIGHT.min)
  assert.equal(clampAllDayHeight(400), ALL_DAY_HEIGHT.max)
  assert.equal(clampAllDayHeight(undefined), ALL_DAY_HEIGHT.default)
})
