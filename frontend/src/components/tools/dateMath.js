/**
 * 日期计算工具。
 *
 * 约定：对外一律使用 'YYYY-MM-DD' 字符串。内部解析为「本地零点的 Date」，
 * 并且跨天差值用 UTC 归一化后相减——避免 DST 与
 * `new Date('2026-11-05')`（按 UTC 解析）这两类经典差一天问题。
 */

const DAY_MS = 86_400_000
const MAX_SPAN_DAYS = 20000

export function parseDate(value) {
  if (value instanceof Date) return new Date(value.getFullYear(), value.getMonth(), value.getDate())
  const match = /^(\d{4})-(\d{1,2})-(\d{1,2})$/.exec(String(value ?? '').trim())
  if (!match) throw new RangeError('日期格式需要为 YYYY-MM-DD')
  return new Date(Number(match[1]), Number(match[2]) - 1, Number(match[3]))
}

export function formatDate(value) {
  const date = value instanceof Date ? value : parseDate(value)
  const year = date.getFullYear()
  const month = String(date.getMonth() + 1).padStart(2, '0')
  const day = String(date.getDate()).padStart(2, '0')
  return `${year}-${month}-${day}`
}

export function todayKey(now = new Date()) {
  return formatDate(now)
}

function utcDays(value) {
  const date = parseDate(value)
  return Date.UTC(date.getFullYear(), date.getMonth(), date.getDate()) / DAY_MS
}

/** 两个日期相差的天数；to 晚于 from 时为正。inclusive 含两端。 */
export function diffDays(from, to, { inclusive = false } = {}) {
  const days = Math.round(utcDays(to) - utcDays(from))
  if (!inclusive || days === 0) return days
  return days + Math.sign(days)
}

/** 日期加减。月末夹取：1/31 加 1 月得到 2/28（闰年 2/29），而不是溢出到 3 月。 */
export function addDate(value, { years = 0, months = 0, days = 0 } = {}) {
  const date = parseDate(value)
  const originalDay = date.getDate()
  if (years || months) {
    date.setDate(1)
    date.setFullYear(date.getFullYear() + Number(years), date.getMonth() + Number(months), 1)
    const lastDay = new Date(date.getFullYear(), date.getMonth() + 1, 0).getDate()
    date.setDate(Math.min(originalDay, lastDay))
  }
  if (days) date.setDate(date.getDate() + Number(days))
  return formatDate(date)
}

/**
 * 是否工作日。节假日数据形如 { 'YYYY-MM-DD': { name, off } }：
 * off=true 放假，off=false 调休补班。未命中时按周一至周五判断。
 */
export function isWorkday(value, holidays = {}) {
  const key = typeof value === 'string' ? value : formatDate(value)
  const entry = holidays?.[key]
  if (entry && entry.off === true) return false
  if (entry && entry.off === false) return true
  const weekday = parseDate(key).getDay()
  return weekday !== 0 && weekday !== 6
}

/** 区间内的工作日天数（含两端）。to 早于 from 时返回负数，便于「反向」显示。 */
export function businessDaysBetween(from, to, holidays = {}) {
  const start = parseDate(from)
  const end = parseDate(to)
  const forward = end.getTime() >= start.getTime()
  const cursor = forward ? start : end
  const limit = forward ? end : start
  let count = 0
  let guard = 0
  while (cursor.getTime() <= limit.getTime()) {
    if (guard++ > MAX_SPAN_DAYS) throw new RangeError('日期区间过大')
    if (isWorkday(cursor, holidays)) count += 1
    cursor.setDate(cursor.getDate() + 1)
  }
  return forward ? count : -count
}

/** 从某日往前/往后推 N 个工作日，落点一定是工作日。 */
export function shiftBusinessDays(value, offset, holidays = {}) {
  const date = parseDate(value)
  const target = Math.round(Number(offset) || 0)
  if (target === 0) return formatDate(date)
  const step = target > 0 ? 1 : -1
  let remaining = Math.abs(target)
  let guard = 0
  while (remaining > 0) {
    if (guard++ > MAX_SPAN_DAYS) throw new RangeError('工作日推算超出范围')
    date.setDate(date.getDate() + step)
    if (isWorkday(date, holidays)) remaining -= 1
  }
  return formatDate(date)
}

/** 自然日数量（含两端），用于「共 N 天」这类展示。 */
export function inclusiveDays(from, to) {
  return diffDays(from, to, { inclusive: true })
}
