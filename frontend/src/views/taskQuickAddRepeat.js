const DAY_CODES = ['MO', 'TU', 'WE', 'TH', 'FR', 'SA', 'SU']

export const quickRepeatPresets = [
  { value: '', label: '不重复' },
  { value: 'DAILY', label: '每天' },
  { value: 'WEEKLY', label: '每周' },
  { value: 'MONTHLY', label: '每月' },
  { value: 'YEARLY', label: '每年' },
  { value: 'WORKDAY', label: '工作日' },
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
    monthDay: 1,
    month: 1,
    ordinal: 1,
    weekday: 'MO',
    workdayPosition: 1,
    endMode: 'NEVER',
    until: '',
    count: 10,
    skipWeekends: false,
  }
}

export function buildQuickRepeatRule(state) {
  if (!state?.preset) return ''
  if (state.preset === 'WORKDAY') return 'FREQ=WEEKLY;BYDAY=MO,TU,WE,TH,FR'
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
    const monthDay = Math.max(1, Math.min(31, Number(state.monthDay) || 1))
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
  if (state.endMode === 'UNTIL' && state.until) parts.push(`UNTIL=${String(state.until).replaceAll('-', '')}T235959Z`)
  if (state.endMode === 'COUNT') parts.push(`COUNT=${Math.max(1, Number(state.count) || 1)}`)
  return parts.join(';')
}

export function quickRepeatLabel(state) {
  return quickRepeatPresets.find(item => item.value === state?.preset)?.label || '重复'
}

export function quickRepeatValidation(state) {
  if (!state?.preset) return ''
  if (state.preset === 'CUSTOM' && state.frequency === 'YEARLY' && state.monthMode === 'WORKDAY') return '年度重复仅支持按日期或按星期'
  if (state.preset === 'CUSTOM' && state.frequency === 'WEEKLY') {
    const weekdays = state.skipWeekends ? state.weekdays.filter(day => !['SA', 'SU'].includes(day)) : state.weekdays
    if (!weekdays.length) return '每周重复至少选择一个有效日期'
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
