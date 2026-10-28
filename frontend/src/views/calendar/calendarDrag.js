export const DRAG_THRESHOLD_PX = 6

/** 把指针位置换算成 15 分钟吸附的分钟数，并限制在可见窗口内。 */
export function pointerToMinutes(offsetY, hourHeight, windowStart, step = 15) {
  const min = Math.ceil((Number(windowStart) || 0) / step) * step
  const raw = windowStart + ((Number(offsetY) || 0) / hourHeight) * 60
  return clamp(Math.round(raw / step) * step, min, 1440)
}

/** 拖动建块：起点与终点自动排序，最小 15 分钟，最长不超过当天剩余时间。 */
export function dragRange(startMinutes, endMinutes, step = 15) {
  let from = clamp(Math.round((Number(startMinutes) || 0) / step) * step, 0, 1440)
  let to = clamp(Math.round((Number(endMinutes) || 0) / step) * step, 0, 1440)
  if (to < from) [from, to] = [to, from]
  if (to === from) to = Math.min(1440, from + step)
  if (to - from < step) to = from + step
  return { startMinutes: from, endMinutes: Math.min(1440, to), durationMinutes: Math.min(1440, to) - from }
}

function clamp(value, min, max) {
  return Math.min(max, Math.max(min, value))
}

/** 拖动时间块移动：保持时长，只改起止，超出当天时向边界回弹。 */
export function moveBlock(startMinutes, durationMinutes, deltaMinutes, step = 15) {
  const snapped = Math.round(deltaMinutes / step) * step
  const duration = Math.max(step, Number(durationMinutes) || step)
  const nextStart = clamp((Number(startMinutes) || 0) + snapped, 0, 1440 - duration)
  return { startMinutes: nextStart, endMinutes: nextStart + duration }
}

/** 拖动时间块上下沿改时长，最小 15 分钟。 */
export function resizeBlock(startMinutes, endMinutes, edge, deltaMinutes, step = 15) {
  const snapped = Math.round(deltaMinutes / step) * step
  if (edge === 'start') {
    const nextStart = clamp((Number(startMinutes) || 0) + snapped, 0, Number(endMinutes) - step)
    return { startMinutes: nextStart, endMinutes: Number(endMinutes) }
  }
  const nextEnd = clamp((Number(endMinutes) || 0) + snapped, Number(startMinutes) + step, 1440)
  return { startMinutes: Number(startMinutes), endMinutes: nextEnd }
}

/** 月格拖选：返回按日期排序的 [startKey, endKey]。 */
export function normalizeDayRange(fromKey, toKey) {
  if (!fromKey || !toKey) return null
  return fromKey <= toKey ? { startKey: fromKey, endKey: toKey } : { startKey: toKey, endKey: fromKey }
}

/** 跨天拖选需要生成 allDay 任务的起止时刻（本地 00:00 起，末日 00:00 止，末日按含当天处理）。 */
export function spanToInstants(startKey, endKey) {
  const [sy, sm, sd] = String(startKey).split('-').map(Number)
  const [ey, em, ed] = String(endKey).split('-').map(Number)
  const start = new Date(sy, (sm || 1) - 1, sd || 1, 0, 0, 0)
  const end = new Date(ey || sy, (em || sm || 1) - 1, ed || sd || 1, 0, 0, 0)
  return { startAt: start.toISOString(), dueAt: end.toISOString() }
}

export function formatDuration(minutes) {
  const total = Math.max(0, Number(minutes) || 0)
  const hours = Math.floor(total / 60)
  const rest = total % 60
  if (!hours) return `${rest} 分钟`
  if (!rest) return `${hours} 小时`
  return `${hours} 小时 ${rest} 分钟`
}
