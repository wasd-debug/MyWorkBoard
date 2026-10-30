/**
 * 习惯页的纯逻辑：日期key、分组、频率、当天状态、连续天数、月历网格、日志。
 * 这里刻意不依赖 Vue，方便 node --test 直接断言。
 */

/** 四个内置分组，自定义分组存 localStorage 后由调用方传进来 */
export const GROUPS = [
  { key: 'morning', label: '上午' },
  { key: 'afternoon', label: '下午' },
  { key: 'evening', label: '晚上' },
  { key: 'other', label: '其他' }
]

export const BUILTIN_GROUP_KEYS = GROUPS.map(group => group.key)

export const WEEKDAY_LABELS = ['一', '二', '三', '四', '五', '六', '日']

/** 频率弹层的三个页签 */
export const FREQUENCY_TABS = [
  { key: 'DAY', label: '按天' },
  { key: 'WEEK', label: '按周' },
  { key: 'INTERVAL', label: '按时间间隔' }
]

/** 完成量习惯的打卡方式 */
export const CHECK_MODES = [
  { key: 'AUTO', label: '自动' },
  { key: 'MANUAL', label: '手动' },
  { key: 'ALL', label: '完成全部' }
]

export const GOAL_PRESETS = [
  { value: 0, label: '永远' },
  { value: 7, label: '7 天' },
  { value: 21, label: '21 天' },
  { value: 30, label: '30 天' },
  { value: 100, label: '100 天' },
  { value: 365, label: '365 天' }
]

export const GOAL_MAX = 999

export const REMIND_PRESETS = ['06:00', '07:00', '08:00', '09:00', '12:00', '14:30', '15:00', '15:30', '16:00', '16:30', '17:00', '17:30', '18:00', '19:00', '20:00', '20:30', '21:00', '21:30', '22:00', '22:30', '23:00']

/** 打卡日志的心情，从差到好 */
export const MOODS = ['😭', '😞', '😐', '🙂', '🥰']

export const CHECK_STYLES = [
  { key: 'CHECK', label: '对勾' },
  { key: 'ICON', label: '习惯图标' }
]

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

/** 两个日期相差多少天（key2 - key1） */
export function daysBetween(fromKey, toKey) {
  const from = parseKey(fromKey)
  const to = parseKey(toKey)
  return Math.round((to - from) / 86400000)
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

/** 频率归类：按天（DAILY/CUSTOM）/按周（WEEKLY_N）/按时间间隔（INTERVAL） */
export function frequencyMode(habit) {
  if (!habit) return 'DAY'
  if (habit.frequency === 'WEEKLY_N' || habit.frequency === 'MONTHLY_N') return 'WEEK'
  if (habit.frequency === 'INTERVAL') return 'INTERVAL'
  return 'DAY'
}

/** 某个习惯在某天是否有「打卡任务」 */
export function scheduledOn(habit, key) {
  if (!habit) return false
  const startDate = String(habit.startDate || '').slice(0, 10)
  if (startDate && key < startDate) return false
  if (habit.frequency === 'CUSTOM') {
    const weekday = parseKey(key).getDay() || 7
    return (habit.customDays || []).map(Number).includes(weekday)
  }
  if (habit.frequency === 'INTERVAL') {
    const interval = Math.max(1, Number(habit.intervalDays) || 2)
    return daysBetween(startDate || key, key) % interval === 0
  }
  return true
}

export function findCheckin(checkins, habitId, key) {
  return (checkins || []).find(item => item.habitId === habitId && String(item.date).slice(0, 10) === key)
}

/** 完成量习惯的每天目标量；普通习惯恒为 1 */
export function targetAmount(habit) {
  if (!habit || habit.targetType !== 'QUANTITY') return 1
  return Math.max(1, Number(habit.dailyTimes) || 1)
}

/** 兼容旧字段名：目标量（完成量习惯）或 1（普通习惯） */
export function doneTarget(habit) {
  return targetAmount(habit)
}

export function checkinAmount(checkin) {
  return Number(checkin?.amount || 0)
}

/** 一条打卡是否「达标」 */
export function isComplete(habit, checkin) {
  if (!habit || !checkin || checkin.status !== 'DONE') return false
  if (habit.targetType === 'QUANTITY') return checkinAmount(checkin) >= targetAmount(habit)
  return Number(checkin.count || 0) >= 1
}

/** 当天进度 0~1，用于完成量习惯的环形/部分填充 */
export function progressOf(habit, checkin) {
  if (isComplete(habit, checkin)) return 1
  if (!checkin || checkin.status !== 'DONE' || habit?.targetType !== 'QUANTITY') return 0
  return Math.min(1, checkinAmount(checkin) / targetAmount(habit))
}

/**
 * 当天格子状态：
 * done 已完成 / partial 打了但没满 / skip 主动跳过（不算中断） / missed 该打没打
 * future 未来 / todo 今天还没打 / none 不在计划内
 */
export function dayState(habit, checkins, key, today = todayKey()) {
  if (!scheduledOn(habit, key)) return 'none'
  const raw = findCheckin(checkins, habit.publicId, key)
  // CLEAR 是「取消打卡」留下的墓碑记录，对上层等价于没有记录
  const checkin = raw && raw.status === 'CLEAR' ? null : raw
  if (checkin && checkin.status === 'SKIP') return 'skip'
  if (isComplete(habit, checkin)) return 'done'
  if (checkin && checkin.status === 'DONE') return 'partial'
  // 显式标记「未完成」和过期没打一样算中断
  if (checkin && checkin.status === 'MISS') return 'missed'
  if (key > today) return 'future'
  if (key < today) return 'missed'
  return 'todo'
}

/** 当前连续天数：从今天（或最近一次该打的日子）往前数 */
export function currentStreak(habit, checkins, today = todayKey()) {
  if (!habit) return 0
  let cursor = today
  const todayState = dayState(habit, checkins, cursor, today)
  if (todayState === 'todo' || todayState === 'partial' || todayState === 'none') cursor = shiftDays(cursor, -1)
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

/** 最高连续天数：从开始日期正向扫一遍 */
export function longestStreak(habit, checkins, today = todayKey()) {
  if (!habit) return 0
  const start = String(habit.startDate || today).slice(0, 10)
  let cursor = start
  let best = 0
  let run = 0
  let guard = 0
  while (cursor <= today && guard < 4000) {
    guard += 1
    const state = dayState(habit, checkins, cursor, today)
    if (state === 'done') {
      run += 1
      best = Math.max(best, run)
    } else if (state === 'none' || state === 'skip') {
      // 不在计划内或主动跳过不打断
    } else {
      run = 0
    }
    cursor = shiftDays(cursor, 1)
  }
  return best
}

/** 累计完成天数 */
export function totalDone(habit, checkins) {
  if (!habit) return 0
  return (checkins || []).filter(item => item.habitId === habit.publicId && isComplete(habit, item)).length
}

/** 某个月的打卡记录（用于日志区），按日期倒序；取消打卡留下的空记录不显示 */
export function monthLogs(habit, checkins, monthKey) {
  return (checkins || [])
    .filter(item => item.habitId === habit?.publicId && String(item.date).startsWith(monthKey))
    .filter(item => item.status !== 'CLEAR')
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

/** 月统计：完成天数 / 应完成天数 / 完成率 / 完成量合计 */
export function monthStats(habit, checkins, year, month, today = todayKey()) {
  const cells = monthMatrix(year, month, today).filter(cell => cell.inMonth)
  let done = 0
  let target = 0
  let amount = 0
  let partial = 0
  for (const cell of cells) {
    if (!scheduledOn(habit, cell.key)) continue
    if (cell.key > today) continue
    const state = dayState(habit, checkins, cell.key, today)
    // 主动跳过的日子不计入应完成天数，否则完成率会被拉低
    if (state === 'skip') continue
    target += 1
    if (state === 'done') done += 1
    if (state === 'partial') partial += 1
  }
  const prefix = `${year}-${pad(month)}`
  for (const item of checkins || []) {
    if (item.habitId !== habit?.publicId) continue
    if (String(item.date).slice(0, 7) !== prefix) continue
    amount += checkinAmount(item)
  }
  return { done, target, partial, rate: target ? Math.round((done * 100) / target) : 0, amount }
}

/** 「今天」「10月3日」这类短标签 */
export function dayLabel(key, today = todayKey()) {
  if (key === today) return '今天'
  const date = parseKey(key)
  return `${date.getMonth() + 1}月${date.getDate()}日`
}

/** 日志里的完整日期：10月29日 */
export function logDateLabel(key) {
  const date = parseKey(key)
  return `${date.getMonth() + 1}月${date.getDate()}日`
}

export function weekdayLabel(key) {
  const date = parseKey(key)
  return `周${WEEKDAY_LABELS[(date.getDay() + 6) % 7]}`
}

/** 坚持天数文案：有目标显示 目标天数/已坚持，否则只显示已坚持 */
export function goalLabel(habit, total) {
  const goal = Number(habit?.goalDays || 0)
  if (!goal) return `已坚持 ${total} 天`
  return `${total}/${goal} 天`
}

export function frequencyLabel(habit) {
  if (!habit) return ''
  if (habit.frequency === 'INTERVAL') return `每 ${Math.max(1, Number(habit.intervalDays) || 2)} 天`
  if (habit.frequency === 'WEEKLY_N') return `每周 ${habit.targetCount} 天`
  if (habit.frequency === 'MONTHLY_N') return `每月 ${habit.targetCount} 次`
  if (habit.frequency === 'CUSTOM') {
    const days = (habit.customDays || []).map(Number).sort()
    if (!days.length) return '按天'
    if (days.length === 7) return '每天'
    return `每周${days.map(day => WEEKDAY_LABELS[day - 1]).join('、')}`
  }
  return '每天'
}

/** 完成量目标文案：1 公里/天 */
export function targetLabel(habit) {
  if (habit?.targetType !== 'QUANTITY') return '当天完成打卡'
  const unit = habit.amountUnit || ''
  return `${targetAmount(habit)} ${unit}/天`.replace(/\s+/g, ' ').trim()
}

export function checkModeLabel(mode) {
  return CHECK_MODES.find(item => item.key === mode)?.label || '自动'
}

/** 提醒时间：库里存逗号分隔的多个时间 */
export function remindTimes(habit) {
  const raw = typeof habit === 'string' ? habit : habit?.remindAt
  return String(raw || '').split(',').map(item => item.trim()).filter(Boolean)
}

export function groupOf(habit) {
  return (habit?.groupKey || 'other')
}

/** 分组名：内置用固定文案，自定义按传进来的列表 */
export function groupLabel(key, customGroups = []) {
  return GROUPS.find(group => group.key === key)?.label
    || customGroups.find(group => group.key === key)?.label
    || key
}

/** 按分组归档：内置四组在前，自定义组按传入顺序，只返回有内容的分组 */
export function groupHabits(habits, customGroups = []) {
  const list = habits || []
  const order = [...GROUPS, ...customGroups.filter(group => !BUILTIN_GROUP_KEYS.includes(group.key))]
  const used = new Set()
  const result = []
  for (const group of order) {
    const items = list.filter(habit => groupOf(habit) === group.key)
    if (items.length) result.push({ ...group, items })
    used.add(group.key)
  }
  // 没有登记过的分组 key 也不要丢
  for (const habit of list) {
    const key = groupOf(habit)
    if (used.has(key)) continue
    used.add(key)
    result.push({ key, label: key, items: list.filter(item => groupOf(item) === key) })
  }
  return result
}

/** 一周的完成度（用于顶部小圆环）：该打的数量与已完成数量 */
export function weekProgress(habits, checkins, week, today = todayKey()) {
  let done = 0
  let total = 0
  for (const day of week) {
    for (const habit of habits || []) {
      if (!scheduledOn(habit, day.key)) continue
      if (day.key > today) continue
      // 跳过的那天不计入分母
      if (dayState(habit, checkins, day.key, today) === 'skip') continue
      total += 1
      if (dayState(habit, checkins, day.key, today) === 'done') done += 1
    }
  }
  return { done, total, percent: total ? Math.round((done * 100) / total) : 0 }
}

/** 新建习惯的默认值 */
export function blankHabit() {
  return {
    name: '', icon: '📖', color: '#4d78db', frequency: 'DAILY', targetCount: 1, customDays: [],
    startDate: todayKey(), remindAt: '', groupKey: 'other', goalDays: 0, checkStyle: 'CHECK',
    targetType: 'CHECK', dailyTimes: 1, autoPopupLog: false, amountUnit: '', intervalDays: 2,
    checkMode: 'AUTO', perAmount: 1, showInToday: true
  }
}

/** 正整数输入兜底：非法返回 null，超过上限截断 */
export function positiveInt(raw, max = GOAL_MAX) {
  const value = Number(String(raw ?? '').replace(/[^\d]/g, ''))
  if (!Number.isFinite(value) || value < 1) return null
  return Math.min(Math.floor(value), max)
}
