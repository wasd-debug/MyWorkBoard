import test from 'node:test'
import assert from 'node:assert/strict'
import { LANE_ROW_HEIGHT, packWeekLanes, splitWeekEvents, visibleRowCount } from './calendarLanes.js'
import { buildCalendarEvents } from './calendarEvents.js'
import { eachDay, startOfWeek, toDateKey } from './calendarRange.js'

const weekStart = startOfWeek(new Date(2026, 9, 19))
const week = eachDay(weekStart, new Date(2026, 9, 26))

function eventsFor(tasks) {
  return buildCalendarEvents({ days: week, tasks, lists: [], tags: [] })
}

test('跨天任务在本周压成一条连续泳道片段', () => {
  const data = eventsFor([
    { publicId: 'a', title: '个人工作台', startAt: '2026-10-01T00:00:00', dueAt: '2026-10-31T00:00:00', allDay: true, status: 'OPEN' }
  ])
  const packed = packWeekLanes(week, data.byDay)
  assert.equal(packed.segments.length, 1)
  assert.equal(packed.segments[0].startCol, 0)
  assert.equal(packed.segments[0].endCol, 6)
  assert.equal(packed.segments[0].continuesBefore, true)
  assert.equal(packed.segments[0].continuesAfter, true)
  assert.equal(packed.laneHeight, LANE_ROW_HEIGHT)
})

test('单天事件不进入泳道', () => {
  const data = eventsFor([
    { publicId: 'b', title: '单词', startAt: '2026-10-20T00:00:00', dueAt: '2026-10-20T00:00:00', allDay: true, status: 'OPEN' }
  ])
  const packed = packWeekLanes(week, data.byDay)
  assert.equal(packed.segments.length, 0)
  assert.equal(packed.laneIds.size, 0)
})

test('重叠的跨天任务分配到不同泳道，不重叠的复用同一泳道', () => {
  const data = eventsFor([
    { publicId: 'a', title: 'A', startAt: '2026-10-19T00:00:00', dueAt: '2026-10-21T00:00:00', allDay: true, status: 'OPEN' },
    { publicId: 'b', title: 'B', startAt: '2026-10-20T00:00:00', dueAt: '2026-10-23T00:00:00', allDay: true, status: 'OPEN' },
    { publicId: 'c', title: 'C', startAt: '2026-10-24T00:00:00', dueAt: '2026-10-25T00:00:00', allDay: true, status: 'OPEN' }
  ])
  const packed = packWeekLanes(week, data.byDay)
  assert.equal(packed.lanes.length, 2)
  const laneOf = id => packed.lanes.findIndex(lane => lane.some(item => item.id === id))
  assert.notEqual(laneOf('a'), laneOf('b'))
  assert.equal(laneOf('c'), laneOf('a'))
  assert.equal(packed.laneHeight, LANE_ROW_HEIGHT * 2)
})

test('跨周任务只裁到本周的列范围', () => {
  const data = eventsFor([
    { publicId: 'a', title: 'A', startAt: '2026-10-23T00:00:00', dueAt: '2026-10-27T00:00:00', allDay: true, status: 'OPEN' }
  ])
  const packed = packWeekLanes(week, data.byDay)
  assert.equal(packed.segments[0].startCol, 4)
  assert.equal(packed.segments[0].endCol, 6)
  assert.equal(packed.segments[0].continuesAfter, true)
  assert.equal(packed.segments[0].continuesBefore, false)
})

test('splitWeekEvents 把泳道事件从单元格列表里剔除', () => {
  const data = eventsFor([
    { publicId: 'a', title: 'A', startAt: '2026-10-19T00:00:00', dueAt: '2026-10-21T00:00:00', allDay: true, status: 'OPEN' },
    { publicId: 'b', title: 'B', startAt: '2026-10-19T00:00:00', dueAt: '2026-10-19T00:00:00', allDay: true, status: 'OPEN' }
  ])
  const packed = packWeekLanes(week, data.byDay)
  const split = splitWeekEvents(data.byDay.get('2026-10-19'), packed.laneIds)
  assert.deepEqual(split.allDay.map(item => item.title), ['B'])
})

test('visibleRowCount 随泳道数量减少', () => {
  assert.equal(visibleRowCount(140, 0), 5)
  assert.equal(visibleRowCount(140, 1), 4)
  assert.equal(visibleRowCount(140, 5), 1)
  assert.equal(visibleRowCount(0, 0), 1)
})
