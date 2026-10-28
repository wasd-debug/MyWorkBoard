const DAY_MS = 86400000

export const WEEKDAY_LABELS = ['一', '二', '三', '四', '五', '六', '日']
export const WEEKDAY_LONG_LABELS = ['周一', '周二', '周三', '周四', '周五', '周六', '周日']

export const VIEW_KEYS = ['day', 'week', 'month', 'year', 'agenda']
export const VIEW_LABELS = { day: '日', week: '周', month: '月', year: '年', agenda: '日程' }
export const VIEW_SHORTCUTS = { day: 'D/1', week: 'W/2', month: 'M/3', year: 'Y/4', agenda: 'A/5' }
export const SPAN_CONFIG = {
  multiDay: { label: '多日', min: 1, max: 14, defaultSpan: 5, unit: '天' },
  multiWeek: { label: '多周', min: 2, max: 6, defaultSpan: 6, unit: '周' }
}
export const SPAN_KEYS = ['multiDay', 'multiWeek']

export const AGENDA_HORIZON_DAYS = 30

export function pad2(value) {
  return String(value).padStart(2, '0')
}

export function toDateKey(value) {
  const date = value instanceof Date ? value : new Date(value)
  return `${date.getFullYear()}-${pad2(date.getMonth() + 1)}-${pad2(date.getDate())}`
}

export function fromDateKey(key) {
  const [year, month, day] = String(key || '').split('-').map(Number)
  return new Date(year || 1970, (month || 1) - 1, day || 1)
}

export function startOfDay(value) {
  const date = value instanceof Date ? value : new Date(value)
  return new Date(date.getFullYear(), date.getMonth(), date.getDate())
}

export function addDays(value, amount) {
  const date = startOfDay(value)
  date.setDate(date.getDate() + amount)
  return date
}

export function daysInMonth(value) {
  const date = value instanceof Date ? value : new Date(value)
  return new Date(date.getFullYear(), date.getMonth() + 1, 0).getDate()
}

export function addMonths(value, amount) {
  const date = startOfDay(value)
  const anchor = new Date(date.getFullYear(), date.getMonth() + amount, 1)
  return new Date(anchor.getFullYear(), anchor.getMonth(), Math.min(date.getDate(), daysInMonth(anchor)))
}

export function startOfWeek(value) {
  const date = startOfDay(value)
  const offset = (date.getDay() + 6) % 7
  return addDays(date, -offset)
}

export function diffDays(from, to) {
  return Math.round((startOfDay(to) - startOfDay(from)) / DAY_MS)
}

export function isSameDay(left, right) {
  return toDateKey(left) === toDateKey(right)
}

export function isoWeekNumber(value) {
  const date = startOfDay(value)
  const target = new Date(date.getFullYear(), date.getMonth(), date.getDate())
  const weekday = (target.getDay() + 6) % 7
  target.setDate(target.getDate() - weekday + 3)
  const firstThursday = new Date(target.getFullYear(), 0, 4)
  const firstWeekday = (firstThursday.getDay() + 6) % 7
  firstThursday.setDate(firstThursday.getDate() - firstWeekday + 3)
  return 1 + Math.round((target - firstThursday) / (7 * DAY_MS))
}

export function isSpanView(view) {
  return SPAN_KEYS.includes(view)
}

export function clampSpan(view, span) {
  const config = SPAN_CONFIG[view]
  if (!config) return null
  const numeric = Number.isFinite(Number(span)) ? Math.round(Number(span)) : config.defaultSpan
  return Math.min(config.max, Math.max(config.min, numeric))
}

export function viewLabel(view, span) {
  if (isSpanView(view)) {
    const config = SPAN_CONFIG[view]
    return `${clampSpan(view, span)} ${config.unit}`
  }
  return VIEW_LABELS[view] || VIEW_LABELS.month
}

export function chunkWeeks(days) {
  const weeks = []
  for (let index = 0; index < days.length; index += 7) weeks.push(days.slice(index, index + 7))
  return weeks
}

export function eachDay(start, endExclusive) {
  const days = []
  for (let cursor = startOfDay(start); cursor < startOfDay(endExclusive); cursor = addDays(cursor, 1)) days.push(cursor)
  return days
}

export function resolveRange({ view = 'month', span, anchor = new Date(), agendaBefore = 0, agendaAfter = 0 } = {}) {
  const base = startOfDay(anchor)
  let start = base
  let end = addDays(base, 1)
  if (view === 'week') {
    start = startOfWeek(base)
    end = addDays(start, 7)
  } else if (view === 'multiDay') {
    const size = clampSpan('multiDay', span)
    start = base
    end = addDays(base, size)
  } else if (view === 'multiWeek') {
    const size = clampSpan('multiWeek', span)
    start = startOfWeek(base)
    end = addDays(start, size * 7)
  } else if (view === 'month') {
    start = startOfWeek(new Date(base.getFullYear(), base.getMonth(), 1))
    end = addDays(startOfWeek(new Date(base.getFullYear(), base.getMonth() + 1, 0)), 7)
  } else if (view === 'year') {
    start = new Date(base.getFullYear(), 0, 1)
    end = new Date(base.getFullYear() + 1, 0, 1)
  } else if (view === 'agenda') {
    const before = Math.max(0, Math.round(Number(agendaBefore) || 0))
    const after = Math.max(0, Math.round(Number(agendaAfter) || 0))
    start = addDays(base, -before)
    end = addDays(base, AGENDA_HORIZON_DAYS + after)
  }
  const days = eachDay(start, end)
  return {
    view, span: isSpanView(view) ? clampSpan(view, span) : null, start, end, days, weeks: chunkWeeks(days),
    title: rangeTitle(view, base), agendaBefore, agendaAfter
  }
}

export function rangeTitle(view, anchor) {
  const base = startOfDay(anchor)
  if (view === 'year') return `${base.getFullYear()}年`
  return `${base.getFullYear()}年${base.getMonth() + 1}月`
}

export function shiftAnchor(anchor, { view = 'month', span, direction = 'next' } = {}) {
  const base = startOfDay(anchor)
  const step = direction === 'prev' ? -1 : direction === 'today' ? 0 : 1
  if (step === 0) return startOfDay(new Date())
  if (view === 'day') return addDays(base, step)
  if (view === 'agenda') return addDays(base, step * 7)
  if (view === 'week') return addDays(base, step * 7)
  if (view === 'multiDay') return addDays(base, step * clampSpan('multiDay', span))
  if (view === 'multiWeek') return addDays(base, step * clampSpan('multiWeek', span) * 7)
  if (view === 'year') return new Date(base.getFullYear() + step, 0, 1)
  return addMonths(base, step)
}

export function monthWeeks(anchor) {
  const base = startOfDay(anchor)
  const start = startOfWeek(new Date(base.getFullYear(), base.getMonth(), 1))
  const end = addDays(startOfWeek(new Date(base.getFullYear(), base.getMonth() + 1, 0)), 7)
  return chunkWeeks(eachDay(start, end))
}

export function yearMonths(anchor) {
  const base = startOfDay(anchor)
  const year = base.getFullYear()
  return Array.from({ length: 12 }, (_, index) => {
    const start = startOfWeek(new Date(year, index, 1))
    const end = addDays(startOfWeek(new Date(year, index + 1, 0)), 7)
    return { month: index + 1, label: `${index + 1}月`, weeks: chunkWeeks(eachDay(start, end)) }
  })
}

export function formatDayHeading(date) {
  const base = startOfDay(date)
  return `${base.getMonth() + 1}月${base.getDate()}日`
}

export function formatWeekday(date) {
  return WEEKDAY_LONG_LABELS[(startOfDay(date).getDay() + 6) % 7]
}

export function formatRangeText(range) {
  const lastDay = addDays(range.end, -1)
  if (isSameDay(range.start, lastDay)) return `${formatDayHeading(range.start)} ${formatWeekday(range.start)}`
  if (range.start.getFullYear() === lastDay.getFullYear()) {
    return `${formatDayHeading(range.start)} – ${formatDayHeading(lastDay)}`
  }
  return `${range.start.getFullYear()}年${formatDayHeading(range.start)} – ${lastDay.getFullYear()}年${formatDayHeading(lastDay)}`
}

export function monthOffset(date, anchor) {
  return (startOfDay(date).getFullYear() - startOfDay(anchor).getFullYear()) * 12 + (startOfDay(date).getMonth() - startOfDay(anchor).getMonth())
}
