/**
 * 习惯页的纯逻辑：日期key、分组、当天状态、连续天数、月历网格。
 * 这里刻意不依赖 Vue，方便 node --test 直接断言。
 */

export const GROUPS = [
  { key: 'morning', label: '上午' },
  { key: 'afternoon', label: '下午' },
  { key: 'evening', label: '晚上' },
  { key: 'other', label: '其他' }
]

export const WEEKDAY_LABELS = ['一', '二', '三', '四', '五', '六', '日']

export const GOAL_PRESETS = [
  { value: 0, label: '永远' },
  { value: 7, label: '7 天' },
  { value: 21, label: '21 天' },
  { value: 30, label: '30 天' },
  { value: 100, label: '100 天' },
  { value: 365, label: '365 天' }
]

export const REMIND_PRESETS = ['07:00', '08:00', '09:00', '12:00', '18:00', '20:00', '20:30', '21:00', '21:30', '22:00', '22:30', '23:00']

export function pad(value) {
  return String(value).padStart(2, '0')
}

/** 本地时区的 yyyy-mm-dd，不用 toISOString（那是 UTC，会跨天） */
export function dateKey(date) {
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`
}

export function todayKey() {
  return dateKey(new Date())
}

export function parseKey(key) {
  const [year, month, day] = String(key).split('-').map(Number)
  return new Date(year, (month || 1) - 1, day || 1)
}

export function shiftDays(key, delta) {
  const date = parseKey(key)
  date.setDate(date.getDate() + delta)
  return dateKey(date)
}

/** 最近 7 天（含今天），返回 [{ key, month, day, weekday, isToday }] */
export function recentWeek(today = todayKey()) {
  return Array.from({ length: 7 }, (_, index) => {
    const date = parseKey(shiftDays(today, index - 6))
    return {
      key: dateKey(date),
      month: date.getMonth() + 1,
      day: date.getDate(),
      weekday: WEEKDAY_LABELS[(date.getDay() + 6) % 7],
      isToday: dateKey(date) === today
    }
  })
}

/** 某个习惯在某天是否有「打卡任务」 */
export function scheduledOn(habit, key) {
  if (!habit) return false
  if (habit.startDate && key < String(habit.startDate).slice(0, 10)) return false
  if (habit.frequency === 'CUSTOM') {
    const weekday = parseKey(key).getDay() || 7
    return (habit.customDays || []).map(Number).includes(weekday)
  }
  return true
}

export function findCheckin(checkins, habitId, key) {
  return (checkins || []).find(item => item.habitId === habitId && String(item.date).slice(0, 10) === key)
}

export function doneTarget(habit) {
  const times = Number(habit?.targetType === 'QUANTITY' ? habit?.targetCount : 1)
  return Math.max(1, times || 1)
}

/**
 * 当天格子状态：
 * done 已完成 / skip 主动跳过 / missed 该打没打（过期） / future 未来 / todo 今天还没打 / none 不在计划内
 */
export function dayState(habit, checkins, key, today = todayKey()) {
  if (!scheduledOn(habit, key)) return 'none'
  const checkin = findCheckin(checkins, habit.publicId, key)
  if (checkin && checkin.status === 'SKIP') return 'skip'
  if (checkin && Number(checkin.count) >= doneTarget(habit)) return 'done'
  if (key > today) return 'future'
  if (key < today) return 'missed'
  return 'todo'
}

/** 当前连续天数：从今天（或最近一次该打的日子）往前数 */
export function currentStreak(habit, checkins, today = todayKey()) {
  if (!habit) return 0
  let cursor = today
  if (dayState(habit, checkins, cursor, today) === 'todo') cursor = shiftDays(cursor, -1)
  let count = 0
  let guard = 0
  while (guard < 3660) {
    guard += 1
    const state = dayState(habit, checkins, cursor, today)
    if (state === 'none' || state === 'skip') {
      cursor = shiftDays(cursor, -1)
      continue
    }
    if (state === 'done') {
      count += 1
      cursor = shiftDays(cursor, -1)
      continue
    }
    break
  }
  return count
}

/** 累计完成天数 */
export function totalDone(habit, checkins) {
  if (!habit) return 0
  return (checkins || []).filter(item => item.habitId === habit.publicId
    && item.status === 'DONE' && Number(item.count) >= doneTarget(habit)).length
}

/** 某个月的打卡记录（用于日志区） */
export function monthLogs(habit, checkins, monthKey) {
  return (checkins || [])
    .filter(item => item.habitId === habit?.publicId && String(item.date).startsWith(monthKey))
    .sort((a, b) => (a.date < b.date ? 1 : -1))
}

/** 月历网格：按周一起始补齐 6 行，返回 [{ key, day, inMonth, isToday }] */
export function monthMatrix(year, month, today = todayKey()) {
  const first = new Date(year, month - 1, 1)
  const offset = (first.getDay() + 6) % 7
  const start = new Date(year, month - 1, 1 - offset)
  return Array.from({ length: 42 }, (_, index) => {
    const date = new Date(start)
    date.setDate(start.getDate() + index)
    return {
      key: dateKey(date),
      day: date.getDate(),
      inMonth: date.getMonth() + 1 === month,
      isToday: dateKey(date) === today
    }
  })
}

/** 「今天」「10月3日」这类短标签 */
export function dayLabel(key, today = todayKey()) {
  if (key === today) return '今天'
  const date = parseKey(key)
  return `${date.getMonth() + 1}月${date.getDate()}日`
}

/** 坚持天数文案：有目标显示 目标天数/已坚持，否则只显示已坚持 */
export function goalLabel(habit, total) {
  const goal = Number(habit?.goalDays || 0)
  if (!goal) return `已坚持 ${total} 天`
  return `${total}/${goal} 天`
}

export function frequencyLabel(habit) {
  if (!habit) return ''
  if (habit.frequency === 'WEEKLY_N') return `每周 ${habit.targetCount} 次`
  if (habit.frequency === 'MONTHLY_N') return `每月 ${habit.targetCount} 次`
  if (habit.frequency === 'CUSTOM') {
    const days = (habit.customDays || []).map(Number).sort()
    if (!days.length) return '自定义'
    return `每周${days.map(day => WEEKDAY_LABELS[day - 1]).join('、')}`
  }
  return '每天'
}

export function groupOf(habit) {
  return (habit?.groupKey || 'other')
}

/** 按分组归档：只返回有内容的分组，顺序固定 */
export function groupHabits(habits) {
  return GROUPS
    .map(group => ({ ...group, items: (habits || []).filter(habit => groupOf(habit) === group.key) }))
    .filter(group => group.items.length)
}

/** 一周的完成度（用于顶部小圆环）：该打的数量与已完成数量 */
export function weekProgress(habits, checkins, week, today = todayKey()) {
  let done = 0
  let total = 0
  for (const day of week) {
    for (const habit of habits || []) {
      if (!scheduledOn(habit, day.key)) continue
      if (day.key > today) continue
      total += 1
      if (dayState(habit, checkins, day.key, today) === 'done') done += 1
    }
  }
  return { done, total, percent: total ? Math.round((done * 100) / total) : 0 }
}
