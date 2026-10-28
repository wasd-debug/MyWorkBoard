import { addDays, fromDateKey, startOfDay, toDateKey } from './calendarRange.js'
import { mergedColorSettings, resolveCountdownColor, resolveHabitColor, resolveTaskColor } from './calendarColors.js'

export const EVENT_KINDS = { TASK: 'task', HABIT: 'habit', COUNTDOWN: 'countdown', FOCUS: 'focus', HOLIDAY: 'holiday', WORKTIME: 'worktime', LEDGER: 'ledger' }

const PRIORITY_ORDER = { HIGH: 0, MEDIUM: 1, LOW: 2, NONE: 3 }
const DEFAULT_TIMED_MINUTES = 30

export function emptyDayBucket() {
  return { allDay: [], timed: [] }
}

export function habitScheduledOn(habit, date) {
  if (!habit || habit.deleted || habit.archived) return false
  const key = toDateKey(date)
  const startDate = habit.startDate ? String(habit.startDate).slice(0, 10) : ''
  if (startDate && key < startDate) return false
  if (habit.frequency === 'CUSTOM') {
    const isoWeekday = ((startOfDay(date).getDay() + 6) % 7) + 1
    return (habit.customDays || []).map(Number).includes(isoWeekday)
  }
  return true
}

/** 整日对齐（00:00 起、00:00 或 23:59 止）且跨度接近整天的有刻任务，实际是跨天任务，应放进全天区。 */
export function spansWholeDays(window) {
  if (!window || window.allDay) return false
  // 起止都是 00:00（没有时长）的条目等价于「当天全天」，不能画成 15 分钟的时间块
  if (window.midnightZero) return true
  const { start, end } = window
  if (start.getHours() !== 0 || start.getMinutes() !== 0) return false
  const duration = end.getTime() - start.getTime()
  if (duration >= 23 * 3600 * 1000) return true
  const endsAtMidnight = end.getHours() === 0 && end.getMinutes() === 0
  return endsAtMidnight && toDateKey(start) !== toDateKey(end)
}

export function taskWindow(task) {
  const startRaw = task?.startAt || task?.dueAt
  if (!startRaw) return null
  const start = new Date(startRaw)
  if (Number.isNaN(start.getTime())) return null
  const allDay = Boolean(task.allDay)
  let end = task.dueAt ? new Date(task.dueAt) : new Date(start.getTime())
  if (Number.isNaN(end.getTime()) || end.getTime() < start.getTime()) end = new Date(start.getTime())
  const duration = Number(task.durationMinutes) || 0
  const zeroLength = end.getTime() === start.getTime()
  const midnightZero = !allDay && zeroLength && !duration
    && start.getHours() === 0 && start.getMinutes() === 0
  if (!allDay && duration && zeroLength) end = new Date(start.getTime() + duration * 60000)
  if (!allDay && !midnightZero && end.getTime() === start.getTime()) end = new Date(start.getTime() + DEFAULT_TIMED_MINUTES * 60000)
  return { start, end, allDay, midnightZero }
}

function enumerateKeys(startKey, endKey) {
  const keys = []
  let cursor = fromDateKey(startKey)
  const end = fromDateKey(endKey)
  while (cursor.getTime() <= end.getTime()) {
    keys.push(toDateKey(cursor))
    cursor = addDays(cursor, 1)
  }
  return keys
}

function minutesOfDay(date) {
  return date.getHours() * 60 + date.getMinutes()
}

function isRecurring(task) {
  return Boolean(task?.rrule || task?.seriesId)
}


function minutesFromClock(value) {
  const [hour, minute] = String(value || '').split(':').map(Number)
  if (!Number.isFinite(hour)) return 0
  return hour * 60 + (Number.isFinite(minute) ? minute : 0)
}

function formatMoney(value) {
  return String(Math.round(Number(value) * 100) / 100)
}

function sortAllDay(items) {
  const kindOrder = { holiday: 0, task: 1, ledger: 2, habit: 3, countdown: 4 }
  return items.sort((left, right) => {
    const byKind = (kindOrder[left.kind] ?? 9) - (kindOrder[right.kind] ?? 9)
    if (byKind) return byKind
    if (Number(left.done) !== Number(right.done)) return Number(left.done) - Number(right.done)
    const priority = (PRIORITY_ORDER[left.priority] ?? 3) - (PRIORITY_ORDER[right.priority] ?? 3)
    if (priority) return priority
    return String(left.title).localeCompare(String(right.title), 'zh-Hans-CN')
  })
}

export function buildCalendarEvents(options = {}) {
  const {
    days = [], tasks = [], habits = [], habitCheckins = [], countdowns = [], focusSessions = [], holidays = [],
    worktime = [], ledger = [],
    lists = [], tags = [], colorSettings = {}, taskFilter = null,
    includeHolidays = true, includeHabits = true, includeCountdowns = true, includeFocus = true
  } = options
  const settings = mergedColorSettings(colorSettings)
  const byDay = new Map()
  const dayKeys = days.map(day => toDateKey(day))
  for (const key of dayKeys) byDay.set(key, emptyDayBucket())
  if (!dayKeys.length) return { byDay, dayKeys, firstKey: '', lastKey: '' }
  const firstKey = dayKeys[0]
  const lastKey = dayKeys[dayKeys.length - 1]
  const inRange = key => key >= firstKey && key <= lastKey && byDay.has(key)

  for (const task of tasks) {
    if (!task || task.deleted || task.itemKind === 'NOTE') continue
    if (task.status === 'ABANDONED') continue
    if (taskFilter && !taskFilter(task)) continue
    const window = taskWindow(task)
    if (!window) continue
    const startKey = toDateKey(window.start)
    const endKey = toDateKey(window.end)
    if (endKey < firstKey || startKey > lastKey) continue
    const color = resolveTaskColor(task, { mode: settings.taskColorMode, lists, tags })
    const openChecklist = (task.checklist || []).filter(item => !item.completed).length
    const base = {
      id: task.publicId, kind: EVENT_KINDS.TASK, title: task.title || '(无标题)', color,
      priority: task.priority || 'NONE', listId: task.listId, tagIds: task.tagIds || [],
      done: task.status === 'COMPLETED', recurring: isRecurring(task), openChecklist, task,
      startKey, endKey, allDay: window.allDay, promoted: spansWholeDays(window)
    }
    if (window.allDay || spansWholeDays(window)) {
      for (const key of enumerateKeys(startKey, endKey)) {
        if (!inRange(key)) continue
        byDay.get(key).allDay.push({ ...base, spanStart: key === startKey, spanEnd: key === endKey })
      }
      continue
    }
    for (const key of enumerateKeys(startKey, endKey)) {
      if (!inRange(key)) continue
      const dayStart = fromDateKey(key)
      const dayEnd = addDays(dayStart, 1)
      const segmentStart = window.start > dayStart ? window.start : dayStart
      const segmentEnd = window.end < dayEnd ? window.end : dayEnd
      if (segmentEnd.getTime() <= segmentStart.getTime()) continue
      byDay.get(key).timed.push({
        ...base,
        startMinutes: minutesOfDay(segmentStart),
        endMinutes: Math.max(minutesOfDay(segmentEnd), minutesOfDay(segmentStart) + 15),
        startLabel: `${String(segmentStart.getHours()).padStart(2, '0')}:${String(segmentStart.getMinutes()).padStart(2, '0')}`,
        endLabel: `${String(segmentEnd.getHours()).padStart(2, '0')}:${String(segmentEnd.getMinutes()).padStart(2, '0')}`,
        continuesBefore: key !== startKey, continuesAfter: key !== endKey,
        spanStart: key === startKey, spanEnd: key === endKey
      })
    }
  }

  if (worktime.length) {
    for (const entry of worktime) {
      const key = String(entry?.date || '').slice(0, 10)
      if (!inRange(key) || !entry.start) continue
      const startMinutes = minutesFromClock(entry.start)
      const endMinutes = entry.end ? minutesFromClock(entry.end) : startMinutes
      const overtime = Number(entry.overtimeMinutes) || 0
      byDay.get(key).timed.push({
        id: `worktime:${key}`, kind: EVENT_KINDS.WORKTIME,
        title: overtime > 0 ? `工时 · 加班 ${Math.round(overtime / 6) / 10}h` : '工时',
        color: '#7c6cf0', done: true, worktime: entry, startKey: key, endKey: key, allDay: false, recurring: false,
        startMinutes, endMinutes: Math.max(endMinutes, startMinutes + 15),
        startLabel: entry.start, endLabel: entry.end || '',
        spanStart: true, spanEnd: true, continuesBefore: false, continuesAfter: false
      })
    }
  }

  if (ledger.length) {
    for (const entry of ledger) {
      const key = String(entry?.date || '').slice(0, 10)
      if (!inRange(key)) continue
      const income = Number(entry.income) || 0
      const expense = Number(entry.expense) || 0
      if (!income && !expense) continue
      const parts = []
      if (income) parts.push(`收 ¥${formatMoney(income)}`)
      if (expense) parts.push(`支 ¥${formatMoney(expense)}`)
      byDay.get(key).allDay.push({
        id: `ledger:${key}`, kind: EVENT_KINDS.LEDGER, title: parts.join(' / '),
        color: '#5aa9a3', done: false, ledger: entry, startKey: key, endKey: key,
        allDay: true, spanStart: true, spanEnd: true, recurring: false
      })
    }
  }

  if (includeHolidays) {
    for (const holiday of holidays) {
      const key = String(holiday?.date || '').slice(0, 10)
      if (!inRange(key)) continue
      byDay.get(key).allDay.push({
        id: `holiday:${key}`, kind: EVENT_KINDS.HOLIDAY, title: holiday.name || (holiday.off ? '休' : '班'),
        color: holiday.off ? '#e8994a' : '#8b92a3', done: false, holiday, off: Boolean(holiday.off),
        startKey: key, endKey: key, allDay: true, spanStart: true, spanEnd: true, recurring: false
      })
    }
  }

  if (includeHabits) {
    const doneByHabitDay = new Map()
    for (const checkin of habitCheckins) {
      if (!checkin || checkin.deleted) continue
      const target = habits.find(item => item.publicId === checkin.habitId)
      const reached = checkin.status === 'DONE' && Number(checkin.count || 0) >= Number(target?.targetCount || 1)
      doneByHabitDay.set(`${checkin.habitId}:${checkin.date}`, reached)
    }
    for (const key of dayKeys) {
      const date = fromDateKey(key)
      for (const habit of habits) {
        if (!habitScheduledOn(habit, date)) continue
        byDay.get(key).allDay.push({
          id: `habit:${habit.publicId}:${key}`, kind: EVENT_KINDS.HABIT, title: habit.name || '习惯',
          color: resolveHabitColor(habit, settings), done: Boolean(doneByHabitDay.get(`${habit.publicId}:${key}`)),
          habit, startKey: key, endKey: key, allDay: true, spanStart: true, spanEnd: true, recurring: true
        })
      }
    }
  }

  if (includeCountdowns) {
    for (const countdown of countdowns) {
      if (!countdown || countdown.deleted || !countdown.targetDate) continue
      const targetKey = String(countdown.targetDate).slice(0, 10)
      const occurrences = []
      if (countdown.repeatYearly) {
        const monthDay = targetKey.slice(5)
        for (const key of dayKeys) if (key.slice(5) === monthDay) occurrences.push(key)
      } else if (inRange(targetKey)) occurrences.push(targetKey)
      for (const key of occurrences) {
        byDay.get(key).allDay.push({
          id: `countdown:${countdown.publicId}:${key}`, kind: EVENT_KINDS.COUNTDOWN,
          title: countdown.title || '纪念日', color: resolveCountdownColor(countdown, settings),
          done: false, countdown, startKey: key, endKey: key, allDay: true, spanStart: true, spanEnd: true
        })
      }
    }
  }

  if (includeFocus) {
    for (const session of focusSessions) {
      if (!session || session.status === 'ABORTED') continue
      const startRaw = session.startedAt
      if (!startRaw) continue
      const start = new Date(startRaw)
      const minutes = Number(session.actualMinutes) || Number(session.plannedMinutes) || 0
      const end = session.endedAt ? new Date(session.endedAt) : new Date(start.getTime() + Math.max(minutes, 1) * 60000)
      const startKey = toDateKey(start)
      const endKey = toDateKey(end)
      if (endKey < firstKey || startKey > lastKey) continue
      for (const key of enumerateKeys(startKey, endKey)) {
        if (!inRange(key)) continue
        const dayStart = fromDateKey(key)
        const dayEnd = addDays(dayStart, 1)
        const segmentStart = start > dayStart ? start : dayStart
        const segmentEnd = end < dayEnd ? end : dayEnd
        if (segmentEnd.getTime() <= segmentStart.getTime()) continue
        byDay.get(key).timed.push({
          id: `focus:${session.publicId}:${key}`, kind: EVENT_KINDS.FOCUS,
          title: session.taskTitle || '专注', color: 'var(--accent)', done: session.status === 'COMPLETED',
          session, startKey: key, endKey: key, allDay: false, recurring: false,
          startMinutes: minutesOfDay(segmentStart),
          endMinutes: Math.max(minutesOfDay(segmentEnd), minutesOfDay(segmentStart) + 10),
          startLabel: `${String(segmentStart.getHours()).padStart(2, '0')}:${String(segmentStart.getMinutes()).padStart(2, '0')}`,
          endLabel: `${String(segmentEnd.getHours()).padStart(2, '0')}:${String(segmentEnd.getMinutes()).padStart(2, '0')}`,
          spanStart: true, spanEnd: true, continuesBefore: false, continuesAfter: false
        })
      }
    }
  }

  for (const bucket of byDay.values()) {
    sortAllDay(bucket.allDay)
    bucket.timed.sort((left, right) => left.startMinutes - right.startMinutes)
  }
  return { byDay, dayKeys, firstKey, lastKey }
}

export function daySummary(bucket) {
  if (!bucket) return { total: 0, done: 0, timed: 0, allDay: 0 }
  const list = [...bucket.allDay, ...bucket.timed]
  return {
    total: list.length,
    done: list.filter(item => item.done).length,
    timed: bucket.timed.length,
    allDay: bucket.allDay.length
  }
}
