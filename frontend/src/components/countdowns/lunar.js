// 农历与法定节假日换算。
// 农历数据为 1900-2100 年的压缩表（由 lunar-javascript 生成后固化，运行时不依赖该库），
// 位布局：bit16-19 闰月月份（0 表示无闰月）；bit4-15 为 1-12 月大小（1=30 天）；bit0 为闰月大小。

const LUNAR_TABLE = [
  0x84bd0, 0x04ae0, 0x0a570, 0x554d0, 0x0d260, 0x0d950, 0x46551, 0x056a0, 0x09ad0, 0x255d0,
  0x04ae0, 0x6a5b0, 0x0a4d0, 0x0d250, 0x5d251, 0x0b540, 0x0d6a0, 0x2ada0, 0x095b0, 0x74971,
  0x04970, 0x0a4b0, 0x5b4b0, 0x06a50, 0x06d40, 0x4ab51, 0x02b60, 0x09570, 0x252f0, 0x04970,
  0x66560, 0x0d4a0, 0x0ea50, 0x56a91, 0x05ad0, 0x02b60, 0x386e1, 0x092e0, 0x7c8d1, 0x0c950,
  0x0d4a0, 0x6d8a1, 0x0b550, 0x056a0, 0x4a5b1, 0x025d0, 0x092d0, 0x2d2b0, 0x0a950, 0x7b550,
  0x06ca0, 0x0b550, 0x55351, 0x04da0, 0x0a5b0, 0x34571, 0x052b0, 0x8a9a0, 0x0e950, 0x06aa0,
  0x6aea0, 0x0ab50, 0x04b60, 0x4aae0, 0x0a570, 0x05260, 0x3f260, 0x0d950, 0x75b50, 0x056a0,
  0x096d0, 0x54dd0, 0x04ad0, 0x0a4d0, 0x4d4d0, 0x0d250, 0x8d550, 0x0b540, 0x0b6a0, 0x695a1,
  0x095b0, 0x049b0, 0x4a970, 0x0a4b0, 0xab270, 0x06a50, 0x06d40, 0x6af40, 0x0ab60, 0x09570,
  0x54af0, 0x04970, 0x064b0, 0x374a0, 0x0ea50, 0x86b50, 0x05ac0, 0x0ab60, 0x596d0, 0x092e0,
  0x0c960, 0x4d950, 0x0d4a0, 0x0da50, 0x27550, 0x056a0, 0x7abb0, 0x025d0, 0x092d0, 0x5cab0,
  0x0a950, 0x0b4a0, 0x4baa0, 0x0ad50, 0x955d0, 0x04ba0, 0x0a5b0, 0x65171, 0x052b0, 0x0a930,
  0x47950, 0x06aa0, 0x0ad50, 0x25b50, 0x04b60, 0x6a6e0, 0x0a4e0, 0x0d260, 0x5ea60, 0x0d530,
  0x05aa0, 0x376a0, 0x096d0, 0xb4af0, 0x04ad0, 0x0a4d0, 0x6d0b1, 0x0d250, 0x0d520, 0x5dd40,
  0x0b5a0, 0x056d0, 0x255b0, 0x049b0, 0x7a570, 0x0a4b0, 0x0aa50, 0x5b251, 0x06d20, 0x0ada0,
  0x34b61, 0x09370, 0x849f0, 0x04970, 0x064b0, 0x668a1, 0x0ea50, 0x06b20, 0x4a6c1, 0x0aae0,
  0x092e0, 0x3d2e0, 0x0c960, 0x7d550, 0x0d4a0, 0x0da50, 0x55d50, 0x056a0, 0x0a6d0, 0x455d0,
  0x052d0, 0x8a9b0, 0x0a950, 0x0b4a0, 0x6b6a0, 0x0ad50, 0x055a0, 0x4aba0, 0x0a5b0, 0x052b0,
  0x3b270, 0x06930, 0x77330, 0x06aa0, 0x0ad50, 0x54b51, 0x04b60, 0x0a570, 0x454e0, 0x0d160,
  0x8e960, 0x0d520, 0x0daa0, 0x66aa1, 0x056d0, 0x04ae0, 0x4a9d0, 0x0a2d0, 0x0d150, 0x2f250,
  0x0d520
]

const MONTH_LABELS = ['正月', '二月', '三月', '四月', '五月', '六月', '七月', '八月', '九月', '十月', '冬月', '腊月']
const DAY_LABELS = [
  '初一', '初二', '初三', '初四', '初五', '初六', '初七', '初八', '初九', '初十',
  '十一', '十二', '十三', '十四', '十五', '十六', '十七', '十八', '十九', '二十',
  '廿一', '廿二', '廿三', '廿四', '廿五', '廿六', '廿七', '廿八', '廿九', '三十'
]
const STEMS = ['甲', '乙', '丙', '丁', '戊', '己', '庚', '辛', '壬', '癸']
const BRANCHES = ['子', '丑', '寅', '卯', '辰', '巳', '午', '未', '申', '酉', '戌', '亥']

const MIN_YEAR = 1900
const MAX_YEAR = 2100
const BASE_DATE = new Date(1900, 0, 31)

const pad = value => String(value).padStart(2, '0')

export function dateKey(date = new Date()) {
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`
}

export function parseKey(key) {
  const [year, month, day] = String(key || '').slice(0, 10).split('-').map(Number)
  if (!year || !month || !day) return new Date(Number.NaN)
  return new Date(year, month - 1, day)
}

const toDate = input => (typeof input === 'string' ? parseKey(input) : new Date(input.getFullYear(), input.getMonth(), input.getDate()))
const info = year => LUNAR_TABLE[year - MIN_YEAR] || 0

export function leapMonth(year) {
  return info(year) >> 16 & 0xf
}

export function leapDays(year) {
  return leapMonth(year) ? (info(year) & 1 ? 30 : 29) : 0
}

export function monthDays(year, month) {
  return (info(year) >> (16 - month)) & 1 ? 30 : 29
}

export function lunarYearDays(year) {
  let total = leapDays(year)
  for (let month = 1; month <= 12; month += 1) total += monthDays(year, month)
  return total
}

export function solarToLunar(input) {
  const date = toDate(input)
  if (Number.isNaN(date.getTime())) return null
  let offset = Math.round((date - BASE_DATE) / 86400000)
  if (offset < 0) return null
  let year = MIN_YEAR
  while (year <= MAX_YEAR && offset >= lunarYearDays(year)) {
    offset -= lunarYearDays(year)
    year += 1
  }
  if (year > MAX_YEAR) return null
  const leap = leapMonth(year)
  let month = 1
  let isLeap = false
  while (true) {
    const days = isLeap ? leapDays(year) : monthDays(year, month)
    if (offset < days) break
    offset -= days
    if (leap === month && !isLeap) {
      isLeap = true
    } else {
      isLeap = false
      month += 1
    }
  }
  return { year, month, day: offset + 1, leap: isLeap }
}

export function lunarToSolar(year, month, day, leap = false) {
  const targetYear = Number(year)
  if (targetYear < MIN_YEAR || targetYear > MAX_YEAR) return null
  const leapOfYear = leapMonth(targetYear)
  if (leap && leapOfYear !== Number(month)) return null
  let offset = 0
  for (let cursor = MIN_YEAR; cursor < targetYear; cursor += 1) offset += lunarYearDays(cursor)
  for (let cursor = 1; cursor < Number(month); cursor += 1) {
    offset += monthDays(targetYear, cursor)
    if (leapOfYear === cursor) offset += leapDays(targetYear)
  }
  if (leap) offset += monthDays(targetYear, Number(month))
  offset += Number(day) - 1
  return new Date(BASE_DATE.getFullYear(), BASE_DATE.getMonth(), BASE_DATE.getDate() + offset)
}

export function monthLabel(month, leap = false) {
  return `${leap ? '闰' : ''}${MONTH_LABELS[Number(month) - 1] || ''}`
}

export function dayLabel(day) {
  return DAY_LABELS[Number(day) - 1] || ''
}

export function yearLabel(year) {
  const value = Number(year)
  return `${STEMS[((value - 4) % 10 + 10) % 10]}${BRANCHES[((value - 4) % 12 + 12) % 12]}（${value}）`
}

export function lunarText(input) {
  const lunar = input && typeof input === 'object' ? input : solarToLunar(input)
  if (!lunar) return ''
  return `${monthLabel(lunar.month, lunar.leap)}${dayLabel(lunar.day)}`
}

/** 该农历年的月份选项（闰月紧跟在同月之后） */
export function lunarMonthOptions(year) {
  const leap = leapMonth(Number(year))
  const options = []
  for (let month = 1; month <= 12; month += 1) {
    options.push({ value: month, leap: false, label: monthLabel(month) })
    if (leap === month) options.push({ value: month, leap: true, label: monthLabel(month, true) })
  }
  return options
}

export function lunarDayOptions(year, month, leap = false) {
  const days = leap ? leapDays(Number(year)) : monthDays(Number(year), Number(month))
  return Array.from({ length: days || 30 }, (_, index) => ({ value: index + 1, label: dayLabel(index + 1) }))
}

/** 农历月日编码：「9-24」/ 闰月「6L-8」 */
export function encodeLunar(month, day, leap = false) {
  return `${Number(month)}${leap ? 'L' : ''}-${Number(day)}`
}

export function decodeLunar(value) {
  const matched = /^(\d{1,2})(L?)-(\d{1,2})$/.exec(String(value || ''))
  if (!matched) return null
  return { month: Number(matched[1]), leap: matched[2] === 'L', day: Number(matched[3]) }
}

/** 返回今年（或之后最近一次）该农历月日对应的公历日期 */
export function nextLunarOccurrence(value, today = new Date()) {
  const lunar = decodeLunar(value)
  const base = toDate(today)
  if (!lunar) return null
  const currentYear = solarToLunar(base)?.year || base.getFullYear()
  for (let year = currentYear; year <= currentYear + 2; year += 1) {
    const date = lunarToSolar(year, lunar.month, lunar.day, lunar.leap)
    if (date && date >= base) return date
  }
  return null
}

const FIXED_HOLIDAYS = [
  { month: 1, day: 1, name: '元旦' },
  { month: 5, day: 1, name: '劳动节' },
  { month: 10, day: 1, name: '国庆节' }
]

// 清明是节气，按年份查表；表外年份用 4 月 5 日兜底
const QINGMING = { 2024: 4, 2025: 4, 2026: 5, 2027: 5, 2028: 4, 2029: 4, 2030: 5 }

function holidaysOfYear(year) {
  const list = FIXED_HOLIDAYS.map(item => ({ date: new Date(year, item.month - 1, item.day), name: item.name }))
  const lunarHolidays = [
    { month: 1, day: 1, name: '春节' },
    { month: 5, day: 5, name: '端午节' },
    { month: 8, day: 15, name: '中秋节' }
  ]
  for (const item of lunarHolidays) {
    const date = lunarToSolar(year, item.month, item.day, false)
    if (date) list.push({ date, name: item.name })
  }
  list.push({ date: new Date(year, 3, QINGMING[year] || 5), name: '清明节' })
  return list.sort((a, b) => a.date - b.date)
}

/** 下一个法定节假日（含今天） */
export function nextHoliday(today = new Date()) {
  const base = toDate(today)
  const pool = [...holidaysOfYear(base.getFullYear()), ...holidaysOfYear(base.getFullYear() + 1)]
  return pool.find(item => item.date >= base) || pool[pool.length - 1]
}

export function nextWeekend(today = new Date()) {
  const base = toDate(today)
  return new Date(base.getFullYear(), base.getMonth(), base.getDate() + (6 - base.getDay() + 7) % 7)
}

export function monthEnd(today = new Date()) {
  const base = toDate(today)
  return new Date(base.getFullYear(), base.getMonth() + 1, 0)
}

export function yearEnd(today = new Date()) {
  const base = toDate(today)
  return new Date(base.getFullYear(), 11, 31)
}

/** 系统内置倒数日的目标日期 */
export function builtinTargetDate(key, today = new Date()) {
  const base = toDate(today)
  if (key === 'weekend') return dateKey(nextWeekend(base))
  if (key === 'holiday') return dateKey(nextHoliday(base).date)
  if (key === 'month-end') return dateKey(monthEnd(base))
  if (key === 'year-end') return dateKey(yearEnd(base))
  return ''
}

export const BUILTIN_COUNTDOWNS = [
  { key: 'weekend', title: '距离周末' },
  { key: 'holiday', title: '距离下一个法定节假日' },
  { key: 'month-end', title: '距离本月结束' },
  { key: 'year-end', title: '距离本年结束' }
]
