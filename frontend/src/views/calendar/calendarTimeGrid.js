export const MINUTES_PER_DAY = 1440
export const DEFAULT_HIDDEN_TOP = 420
export const DEFAULT_HIDDEN_BOTTOM = 1380
export const MIN_VISIBLE_MINUTES = 120

function clamp(value, min, max) {
  return Math.min(max, Math.max(min, value))
}

/** 首尾可隐藏时段：上沿最多隐藏到正午，下沿最晚从 13:00 开始隐藏，且保证可见区间不小于 2 小时。 */
export function visibleWindow(hiddenTop = DEFAULT_HIDDEN_TOP, hiddenBottom = DEFAULT_HIDDEN_BOTTOM) {
  const top = clamp(Number(hiddenTop) || 0, 0, 720)
  const rawBottom = Number.isFinite(Number(hiddenBottom)) ? Number(hiddenBottom) : MINUTES_PER_DAY
  let bottom = clamp(rawBottom, 0, MINUTES_PER_DAY)
  if (bottom < 720) bottom = 720
  if (bottom - top < MIN_VISIBLE_MINUTES) {
    if (top + MIN_VISIBLE_MINUTES <= MINUTES_PER_DAY) bottom = top + MIN_VISIBLE_MINUTES
    else {
      bottom = MINUTES_PER_DAY
      return { start: MINUTES_PER_DAY - MIN_VISIBLE_MINUTES, end: MINUTES_PER_DAY, minutes: MIN_VISIBLE_MINUTES, topClipped: true }
    }
  }
  return { start: top, end: bottom, minutes: bottom - top, topClipped: false }
}

export function snapMinutes(value, step = 15) {
  const numeric = Number(value)
  if (!Number.isFinite(numeric)) return 0
  return clamp(Math.round(numeric / step) * step, 0, MINUTES_PER_DAY)
}

export function blockGeometry(startMinutes, endMinutes, window, hourHeight = 48) {
  const start = clamp(Number(startMinutes) || 0, window.start, window.end)
  const rawEnd = clamp(Number(endMinutes) || start + 30, window.start, window.end)
  const end = Math.max(rawEnd, start + 10)
  const top = ((start - window.start) / 60) * hourHeight
  const height = Math.max(16, ((end - start) / 60) * hourHeight - 2)
  return { top, height, start, end, clippedStart: startMinutes < window.start, clippedEnd: endMinutes > window.end }
}

/**
 * 把同一天的事件按重叠关系分簇，簇内贪心分配列，返回每条事件占用的列与簇内总列数。
 */
export function layoutColumns(events = []) {
  const sorted = [...events]
    .filter(item => item && Number.isFinite(Number(item.startMinutes)))
    .sort((left, right) => left.startMinutes - right.startMinutes
      || (right.endMinutes || 0) - (left.endMinutes || 0)
      || String(left.id).localeCompare(String(right.id)))
  const result = new Map()
  let cluster = []
  let clusterEnd = -1

  const flush = () => {
    if (!cluster.length) return
    const columnEnds = []
    for (const item of cluster) {
      let column = columnEnds.findIndex(end => end <= item.startMinutes)
      if (column === -1) {
        column = columnEnds.length
        columnEnds.push(item.endMinutes)
      } else {
        columnEnds[column] = item.endMinutes
      }
      result.set(item.id, { column, columns: 0 })
    }
    const columns = columnEnds.length
    for (const item of cluster) {
      const entry = result.get(item.id)
      result.set(item.id, { column: entry.column, columns })
    }
    cluster = []
    clusterEnd = -1
  }

  for (const item of sorted) {
    const end = Math.max(Number(item.endMinutes) || item.startMinutes + 30, item.startMinutes + 1)
    if (cluster.length && item.startMinutes >= clusterEnd) flush()
    cluster.push({ ...item, endMinutes: end })
    clusterEnd = Math.max(clusterEnd, end)
  }
  flush()
  return result
}

export function minutesToLabel(minutes) {
  const total = clamp(Number(minutes) || 0, 0, MINUTES_PER_DAY)
  if (total === 12 * 60) return '正午'
  const hour = Math.floor(total / 60)
  const minute = total % 60
  return `${String(hour).padStart(2, '0')}:${String(minute).padStart(2, '0')}`
}

export function hourMarks(window, stepMinutes = 60) {
  const marks = []
  const first = Math.ceil(window.start / stepMinutes) * stepMinutes
  for (let cursor = first; cursor <= window.end; cursor += stepMinutes) {
    marks.push({ minutes: cursor, label: minutesToLabel(cursor) })
  }
  return marks
}

/** 全天区默认高度与拖拽边界（与截图中的可上提/下拉区域一致）。 */
export const ALL_DAY_HEIGHT = { min: 32, max: 280, default: 120 }

export function clampAllDayHeight(value) {
  return clamp(Number(value) || ALL_DAY_HEIGHT.default, ALL_DAY_HEIGHT.min, ALL_DAY_HEIGHT.max)
}
