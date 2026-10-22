const DAY_CODES = ['MO', 'TU', 'WE', 'TH', 'FR', 'SA', 'SU']

export const quickRepeatPresets = [
  { value: '', label: '不重复' },
  { value: 'DAILY', label: '每天' },
  { value: 'WEEKLY', label: '每周' },
  { value: 'MONTHLY', label: '每月' },
  { value: 'YEARLY', label: '每年' },
  { value: 'WORKDAY', label: '工作日' },
  { value: 'HOLIDAY', label: '节假日' },
  { value: 'LUNAR', label: '农历重复' },
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
    endMode: 'NEVER',
    until: '',
    count: 10,
    skipHolidays: false,
    skipWeekends: false,
  }
}

export function buildQuickRepeatRule(state) {
  if (!state?.preset) return ''
  if (state.preset === 'HOLIDAY' || state.preset === 'LUNAR') return ''
  if (state.preset === 'WORKDAY') return 'FREQ=WEEKLY;BYDAY=MO,TU,WE,TH,FR'
  const frequency = state.preset === 'CUSTOM' ? state.frequency : state.preset
  const interval = Math.max(1, Number(state.interval) || 1)
  const parts = [`FREQ=${frequency}`]
  if (interval > 1) parts.push(`INTERVAL=${interval}`)
  if (frequency === 'WEEKLY' && state.weekdays?.length) parts.push(`BYDAY=${state.weekdays.join(',')}`)
  if (frequency === 'MONTHLY' && state.monthMode === 'DATE') parts.push(`BYMONTHDAY=${Math.max(1, Math.min(31, Number(state.monthDay) || 1))}`)
  if (frequency === 'YEARLY' && state.monthMode === 'DATE') parts.push(`BYMONTHDAY=${Math.max(1, Math.min(31, Number(state.monthDay) || 1))}`)
  if (state.endMode === 'UNTIL' && state.until) parts.push(`UNTIL=${String(state.until).replaceAll('-', '')}T235959Z`)
  if (state.endMode === 'COUNT') parts.push(`COUNT=${Math.max(1, Number(state.count) || 1)}`)
  return parts.join(';')
}

export function quickRepeatLabel(state) {
  return quickRepeatPresets.find(item => item.value === state?.preset)?.label || '重复'
}

export function quickRepeatValidation(state) {
  if (!state?.preset || state.preset === 'HOLIDAY' || state.preset === 'LUNAR') return ''
  if (state.preset === 'CUSTOM' && state.frequency === 'WEEKLY' && !state.weekdays?.length) return '每周重复至少选择一天'
  if (state.endMode === 'UNTIL' && !state.until) return '请选择重复结束日期'
  if (state.endMode === 'COUNT' && (!Number.isInteger(Number(state.count)) || Number(state.count) < 1)) return '重复次数必须大于 0'
  return ''
}

export { DAY_CODES }
