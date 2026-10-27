const DAY_CODES = ['MO', 'TU', 'WE', 'TH', 'FR', 'SA', 'SU']

export const quickRepeatPresets = [
  { value: '', label: '不重复' },
  { value: 'DAILY', label: '每天' },
  { value: 'WEEKLY', label: '每周' },
  { value: 'MONTHLY', label: '每月' },
  { value: 'YEARLY', label: '每年' },
  { value: 'WORKDAY', label: '每周工作日' },
  { value: 'LEGAL_WORKDAY', label: '法定工作日' },
  { value: 'WEEKEND', label: '每周末' },
  { value: 'LEGAL_HOLIDAY', label: '法定节假日' },
  { value: 'LUNAR_MONTHLY', label: '每月农历日' },
  { value: 'LUNAR_YEARLY', label: '每年农历日' },
  { value: 'CUSTOM', label: '自定义' },
]

export function createQuickRepeatState() {
  return {
    preset: '',
    mode: 'DUE_DATE',
    frequency: 'WEEKLY',
    interval: 1,
    weekdays: ['MO'],
    monthMode: 'DATE',
    monthDays: [],
    lastDay: false,
    month: 1,
    ordinal: 1,
    weekday: 'MO',
    workdayPosition: 1,
    endMode: 'NEVER',
    until: '',
    count: 10,
    skipWeekends: false,
    skipLegalHolidays: false,
    lunarMonth: 1,
    lunarDay: 1,
  }
}

export function buildQuickRepeatRule(state) {
  if (!state?.preset) return ''
  if (state.preset === 'WORKDAY') return withEnd('FREQ=WEEKLY;BYDAY=MO,TU,WE,TH,FR', state)
  if (state.preset === 'LEGAL_WORKDAY') return withEnd('FREQ=DAILY;X-TASK-KIND=LEGAL_WORKDAY', state)
  if (state.preset === 'WEEKEND') return withEnd('FREQ=WEEKLY;BYDAY=SA,SU', state)
  if (state.preset === 'LEGAL_HOLIDAY') return withEnd('FREQ=DAILY;X-TASK-KIND=LEGAL_HOLIDAY', state)
  if (state.preset === 'LUNAR_MONTHLY') return withEnd(`FREQ=MONTHLY;X-TASK-KIND=LUNAR_MONTHLY;X-LUNAR-DAY=${Math.max(1, Math.min(30, Number(state.lunarDay) || 1))}`, state)
  if (state.preset === 'LUNAR_YEARLY') return withEnd(`FREQ=YEARLY;X-TASK-KIND=LUNAR_YEARLY;X-LUNAR-MONTH=${Math.max(1, Math.min(12, Number(state.lunarMonth) || 1))};X-LUNAR-DAY=${Math.max(1, Math.min(30, Number(state.lunarDay) || 1))}`, state)
  const frequency = state.preset === 'CUSTOM' ? state.frequency : state.preset
  const interval = Math.max(1, Number(state.interval) || 1)
  const parts = [`FREQ=${frequency}`]
  if (interval > 1) parts.push(`INTERVAL=${interval}`)
  if (state.preset === 'CUSTOM' && frequency === 'WEEKLY') {
    const weekdays = state.skipWeekends ? state.weekdays.filter(day => !['SA', 'SU'].includes(day)) : state.weekdays
    if (weekdays.length) parts.push(`BYDAY=${weekdays.join(',')}`)
  }
  if (state.preset === 'CUSTOM' && frequency === 'DAILY' && state.skipWeekends) {
    parts.push('BYDAY=MO,TU,WE,TH,FR')
  }
  if (state.preset === 'CUSTOM' && ['MONTHLY', 'YEARLY'].includes(frequency)) {
    const monthDays = (Array.isArray(state.monthDays) ? state.monthDays : []).map(day => Math.max(1, Math.min(31, Number(day) || 1)))
    const monthDay = state.lastDay ? -1 : monthDays.length ? monthDays.sort((a, b) => a - b) : Math.max(1, Math.min(31, Number(state.monthDay) || 1))
    if (frequency === 'YEARLY') parts.push(`BYMONTH=${Math.max(1, Math.min(12, Number(state.month) || 1))}`)
    if (state.monthMode === 'DATE') {
      parts.push(`BYMONTHDAY=${monthDay}`)
      if (state.skipWeekends) parts.push('BYDAY=MO,TU,WE,TH,FR')
    } else if (state.monthMode === 'WEEKDAY') {
      const rawOrdinal = Number(state.ordinal) || 1
      const ordinal = rawOrdinal === -1 ? -1 : Math.max(1, Math.min(5, rawOrdinal))
      parts.push(`BYDAY=${ordinal}${state.weekday || 'MO'}`)
    } else {
      parts.push('BYDAY=MO,TU,WE,TH,FR')
      parts.push(`BYSETPOS=${Number(state.workdayPosition) < 0 ? -1 : 1}`)
    }
  }
  if (state.preset === 'CUSTOM' && state.skipLegalHolidays) parts.push('X-SKIP-HOLIDAYS=TRUE')
  if (state.preset === 'CUSTOM' && state.skipWeekends && !['DAILY', 'WEEKLY'].includes(frequency)) parts.push('X-SKIP-WEEKENDS=TRUE')
  return withEnd(parts.join(';'), state)
}

function withEnd(rule, state) {
  if (state.endMode === 'UNTIL' && state.until) return `${rule};UNTIL=${String(state.until).replaceAll('-', '')}T235959Z`
  if (state.endMode === 'COUNT') return `${rule};COUNT=${Math.max(1, Number(state.count) || 1)}`
  return rule
}

export function quickRepeatLabel(state) {
  return quickRepeatPresets.find(item => item.value === state?.preset)?.label || '重复'
}

const LUNAR_MONTH_NAMES = { 正: 1, 一: 1, 二: 2, 三: 3, 四: 4, 五: 5, 六: 6, 七: 7, 八: 8, 九: 9, 十: 10, 十一: 11, 十二: 12, 冬: 11, 腊: 12 }
const LUNAR_DIGIT_NAMES = { 一: 1, 二: 2, 三: 3, 四: 4, 五: 5, 六: 6, 七: 7, 八: 8, 九: 9, 十: 10 }

export function parseLunarLabel(label) {
  const match = /^(闰)?(.+?)月(.+)$/.exec(String(label || '').trim())
  if (!match) return null
  const month = LUNAR_MONTH_NAMES[match[2]]
  const dayText = match[3]
  let day = null
  if (dayText === '二十' || dayText === '三十' || dayText === '初十') day = { 二十: 20, 三十: 30, 初十: 10 }[dayText]
  else if (/^初/.test(dayText)) day = LUNAR_DIGIT_NAMES[dayText.slice(1)]
  else if (/^十/.test(dayText)) day = 10 + (LUNAR_DIGIT_NAMES[dayText.slice(1)] || 0)
  else if (/^二十/.test(dayText)) day = 20 + (LUNAR_DIGIT_NAMES[dayText.slice(2)] || 0)
  else if (/^廿/.test(dayText)) day = 20 + (LUNAR_DIGIT_NAMES[dayText.slice(1)] || 0)
  else if (/^卅/.test(dayText)) day = 30 + (LUNAR_DIGIT_NAMES[dayText.slice(1)] || 0)
  return month && day ? { month, day } : null
}

const DAY_CODE_BY_MONDAY_INDEX = ['MO', 'TU', 'WE', 'TH', 'FR', 'SA', 'SU']

function mondayOf(date) {
  const copy = new Date(date.getFullYear(), date.getMonth(), date.getDate())
  copy.setDate(copy.getDate() - (copy.getDay() + 6) % 7)
  return copy
}

function monthWorkingDays(date, holidays) {
  const total = new Date(date.getFullYear(), date.getMonth() + 1, 0).getDate()
  const days = []
  for (let value = 1; value <= total; value += 1) {
    const probe = new Date(date.getFullYear(), date.getMonth(), value)
    const key = dateKeyOf(probe)
    const weekday = (probe.getDay() + 6) % 7
    const working = holidays[key] ? !holidays[key].off : weekday < 5
    if (working) days.push(value)
  }
  return days
}

function dateKeyOf(date) {
  return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')}`
}

/* 判断某个日期是否命中快速重复规则；context = { holidays: {key:{off}}, lunar: Map(key→{month,day}), anchor: Date } */
export function quickRepeatMatchesDate(state, date, context = {}) {
  if (!state?.preset) return false
  const key = dateKeyOf(date)
  const weekday = (date.getDay() + 6) % 7
  const day = date.getDate()
  const month = date.getMonth() + 1
  const holidays = context.holidays || {}
  const lunar = context.lunar instanceof Map ? context.lunar.get(key) : context.lunar?.[key]
  switch (state.preset) {
    case 'WORKDAY': return weekday < 5
    case 'LEGAL_WORKDAY': return holidays[key] ? !holidays[key].off : weekday < 5
    case 'WEEKEND': return weekday >= 5
    case 'LEGAL_HOLIDAY': return holidays[key] ? Boolean(holidays[key].off) : weekday >= 5
    case 'LUNAR_MONTHLY': return lunar ? lunar.day === Math.max(1, Math.min(30, Number(state.lunarDay) || 1)) : false
    case 'LUNAR_YEARLY': return lunar ? lunar.month === Math.max(1, Math.min(12, Number(state.lunarMonth) || 1)) && lunar.day === Math.max(1, Math.min(30, Number(state.lunarDay) || 1)) : false
    default: break
  }
  const frequency = state.preset === 'CUSTOM' ? state.frequency : state.preset
  const interval = Math.max(1, Number(state.interval) || 1)
  const anchor = context.anchor instanceof Date && !Number.isNaN(context.anchor.getTime()) ? context.anchor : null
  if (anchor && interval > 1) {
    if (frequency === 'DAILY') {
      const diff = Math.round((date - new Date(anchor.getFullYear(), anchor.getMonth(), anchor.getDate())) / 86400000)
      if (diff < 0 || diff % interval) return false
    } else if (frequency === 'WEEKLY') {
      const diffWeeks = Math.round((mondayOf(date) - mondayOf(anchor)) / 604800000)
      if (diffWeeks < 0 || diffWeeks % interval) return false
    } else if (frequency === 'MONTHLY') {
      const diffMonths = (date.getFullYear() - anchor.getFullYear()) * 12 + date.getMonth() - anchor.getMonth()
      if (diffMonths < 0 || diffMonths % interval) return false
    } else if (frequency === 'YEARLY') {
      const diffYears = date.getFullYear() - anchor.getFullYear()
      if (diffYears < 0 || diffYears % interval) return false
    }
  }
  if (frequency === 'DAILY') return state.preset === 'CUSTOM' && state.skipWeekends ? weekday < 5 : true
  if (frequency === 'WEEKLY') {
    const weekdays = state.preset === 'CUSTOM' && state.skipWeekends ? (state.weekdays || []).filter(code => !['SA', 'SU'].includes(code)) : state.weekdays || []
    return weekdays.includes(DAY_CODE_BY_MONDAY_INDEX[weekday])
  }
  if (frequency === 'MONTHLY' || frequency === 'YEARLY') {
    const monthMode = state.monthMode || 'DATE'
    if (frequency === 'YEARLY' && month !== Math.max(1, Math.min(12, Number(state.month) || 1))) return false
    if (monthMode === 'DATE') {
      if (state.lastDay) return day === new Date(date.getFullYear(), date.getMonth() + 1, 0).getDate()
      const days = Array.isArray(state.monthDays) && state.monthDays.length ? state.monthDays.map(Number) : [Math.max(1, Math.min(31, Number(state.monthDay) || 1))]
      return days.includes(day)
    }
    if (monthMode === 'WEEKDAY') {
      if (DAY_CODE_BY_MONDAY_INDEX[weekday] !== state.weekday) return false
      const ordinal = Number(state.ordinal) || 1
      if (ordinal === -1) return day + 7 > new Date(date.getFullYear(), date.getMonth() + 1, 0).getDate()
      return Math.ceil(day / 7) === ordinal
    }
    const workingDays = monthWorkingDays(date, holidays)
    if (!workingDays.length) return false
    return Number(state.workdayPosition) < 0 ? day === workingDays[workingDays.length - 1] : day === workingDays[0]
  }
  if (frequency === 'CUSTOM') {
    /* 兜底：未知频率按每天处理 */
    return true
  }
  return false
}

export function quickRepeatValidation(state) {
  if (!state?.preset) return ''
  if (state.preset === 'CUSTOM' && state.frequency === 'YEARLY' && state.monthMode === 'WORKDAY') return '年度重复仅支持按日期或按星期'
  if (state.preset === 'CUSTOM' && state.frequency === 'WEEKLY') {
    const weekdays = state.skipWeekends ? state.weekdays.filter(day => !['SA', 'SU'].includes(day)) : state.weekdays
    if (!weekdays.length) return '每周重复至少选择一个有效日期'
  }
  if (state.preset === 'CUSTOM' && ['MONTHLY', 'YEARLY'].includes(state.frequency) && state.monthMode === 'DATE' && !state.lastDay) {
    const monthDays = (Array.isArray(state.monthDays) ? state.monthDays : []).filter(day => Number(day) >= 1 && Number(day) <= 31)
    if (!monthDays.length) return '按日期重复至少选择一个日期'
  }
  if (state.preset === 'CUSTOM' && ['MONTHLY', 'YEARLY'].includes(state.frequency)
    && state.monthMode === 'WEEKDAY' && state.skipWeekends && ['SA', 'SU'].includes(state.weekday)) {
    return '跳过双休日时不能选择周六或周日'
  }
  if (state.endMode === 'UNTIL' && !state.until) return '请选择重复结束日期'
  if (state.endMode === 'COUNT' && (!Number.isInteger(Number(state.count)) || Number(state.count) < 1)) return '重复次数必须大于 0'
  return ''
}

export { DAY_CODES }
