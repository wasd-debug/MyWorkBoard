import { toDateKey } from './calendarRange.js'

/**
 * 把一个周行里的跨天全天事件压成泳道，供月/多周视图画成连续横条。
 * 输入是 7 个 Date，输出每条泳道上的片段（起始列、结束列、是否在周外还有延续）。
 */
export function packWeekLanes(weekDays = [], byDay = new Map()) {
  const keys = weekDays.map(toDateKey)
  if (!keys.length) return { lanes: [], segments: [], laneIds: new Set(), laneHeight: 0 }
  const candidates = new Map()
  for (const key of keys) {
    const bucket = byDay.get(key)
    if (!bucket) continue
    for (const event of bucket.allDay) {
      if (!event || event.startKey === event.endKey) continue
      if (!candidates.has(event.id)) candidates.set(event.id, event)
    }
  }
  const segments = []
  for (const event of candidates.values()) {
    const startIndex = keys.indexOf(event.startKey)
    const endIndex = keys.indexOf(event.endKey)
    const startCol = startIndex === -1 ? 0 : startIndex
    const endCol = endIndex === -1 ? keys.length - 1 : endIndex
    if (endCol < 0 || startCol > keys.length - 1 || endCol < startCol) continue
    segments.push({
      id: event.id,
      event,
      startCol,
      endCol,
      span: endCol - startCol + 1,
      continuesBefore: event.startKey < keys[0],
      continuesAfter: event.endKey > keys[keys.length - 1]
    })
  }
  segments.sort((left, right) => left.startCol - right.startCol
    || right.span - left.span
    || String(left.event.title).localeCompare(String(right.event.title), 'zh-Hans-CN'))
  const lanes = []
  for (const segment of segments) {
    let target = lanes.find(lane => lane.every(item => item.endCol < segment.startCol || item.startCol > segment.endCol))
    if (!target) {
      target = []
      lanes.push(target)
    }
    target.push(segment)
  }
  return {
    lanes,
    segments,
    laneIds: new Set(segments.map(item => item.id)),
    laneHeight: lanes.length * LANE_ROW_HEIGHT
  }
}

export const LANE_ROW_HEIGHT = 20

/** 单元格里还能放下多少条事件：扣掉日头、泳道占位与内边距。 */
export function visibleRowCount(cellHeight, laneRows = 0, { headHeight = 22, rowHeight = 20, padding = 6 } = {}) {
  const usable = Number(cellHeight) - headHeight - laneRows * LANE_ROW_HEIGHT - padding
  return Math.max(1, Math.floor(usable / rowHeight))
}

export function splitWeekEvents(bucket, laneIds) {
  if (!bucket) return { allDay: [], timed: [] }
  return {
    allDay: bucket.allDay.filter(item => !laneIds.has(item.id)),
    timed: bucket.timed
  }
}
