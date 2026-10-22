export function localDateKey(date) {
  return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')}`
}

export function localDateTime(date, time = '09:00') {
  return `${localDateKey(date)}T${time}`
}

export function nextWholeHour(date = new Date()) {
  const rounded = new Date(date)
  rounded.setMinutes(0, 0, 0)
  rounded.setHours(rounded.getHours() + 1)
  return localDateTime(rounded, `${String(rounded.getHours()).padStart(2, '0')}:00`)
}

export function calendarMonthDays(month, weekStart = 1) {
  const first = new Date(month.getFullYear(), month.getMonth(), 1)
  const offset = (first.getDay() - weekStart + 7) % 7
  const start = new Date(first.getFullYear(), first.getMonth(), 1 - offset)
  return Array.from({ length: 42 }, (_, index) => {
    const date = new Date(start)
    date.setDate(start.getDate() + index)
    return { date, key: localDateKey(date), inMonth: date.getMonth() === month.getMonth() }
  })
}

export function quickReminderOptions(allDay) {
  return allDay
    ? [
      { value: 'TODAY', label: '当天' },
      { value: '1_DAY', label: '提前 1 天' },
      { value: '2_DAY', label: '提前 2 天' },
      { value: '3_DAY', label: '提前 3 天' },
      { value: '1_WEEK', label: '提前 1 周' },
      { value: 'CUSTOM', label: '自定义' },
    ]
    : [
      { value: 'ON_TIME', label: '准时' },
      { value: '5_MIN', label: '提前 5 分钟' },
      { value: '30_MIN', label: '提前 30 分钟' },
      { value: '1_HOUR', label: '提前 1 小时' },
      { value: '1_DAY', label: '提前 1 天' },
      { value: 'CUSTOM', label: '自定义' },
    ]
}

const TIMED_OFFSETS = { ON_TIME: 0, '5_MIN': 5, '30_MIN': 30, '1_HOUR': 60, '1_DAY': 1440 }
const DATE_OFFSETS = { TODAY: 0, '1_DAY': 1, '2_DAY': 2, '3_DAY': 3, '1_WEEK': 7 }

export function quickReminderPayloads({ dueAt, allDay, config }) {
  if (!config?.enabled || !dueAt || !config.presets?.length) return []
  return config.presets.map(preset => {
    if (!allDay) {
      const offsetMinutes = preset === 'CUSTOM'
        ? Number(config.amount || 1) * ({ MINUTE: 1, HOUR: 60, DAY: 1440, WEEK: 10080 }[config.unit] || 1)
        : TIMED_OFFSETS[preset]
      if (!Number.isFinite(offsetMinutes) || offsetMinutes < 0 || offsetMinutes > 10080) throw new RangeError('自定义提醒范围为 0 到 10080 分钟')
      return { kind: 'RELATIVE', offsetMinutes, remindAt: null, channel: 'IN_APP', dailyUntilDone: Boolean(config.dailyUntilDone) }
    }

    const days = preset === 'CUSTOM'
      ? Number(config.amount || 1) * (config.unit === 'WEEK' ? 7 : 1)
      : DATE_OFFSETS[preset]
    if (!Number.isFinite(days) || days < 0 || days > 365) throw new RangeError('自定义提醒范围为 0 到 365 天')
    const target = new Date(dueAt)
    target.setDate(target.getDate() - days)
    const [hours, minutes] = String(config.time || '09:00').split(':').map(Number)
    target.setHours(hours || 0, minutes || 0, 0, 0)
    return { kind: 'ABSOLUTE', offsetMinutes: null, remindAt: target.toISOString(), channel: 'IN_APP', dailyUntilDone: Boolean(config.dailyUntilDone) }
  })
}
