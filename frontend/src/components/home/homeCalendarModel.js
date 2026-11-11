/**
 * 首页日历卡（月 / 周 / 日程）的取数口径（纯函数，无 DOM）。
 *
 * 与日历集成页 `/calendar` 共用 `views/calendar/calendarRange.js` 的日期工具，
 * 但只保留「一眼看清哪天有事」需要的信息：不做筛选、拖拽、多日跨度与跨月补位交互，
 * 组件只负责渲染。点开某天后需要的明细也在这里拼好，避免组件里再算一遍。
 */
import { addDays, fromDateKey, monthWeeks, startOfWeek, toDateKey } from '../../views/calendar/calendarRange.js'
import { taskRange } from '../../utils/taskDates.js'
import { CALC } from '../../utils/calc.js'

/** 首页只给三档：整月分布 / 本周安排 / 接下来要做什么。日视图在 1/3 屏宽里没有可读性 */
export const HOME_CALENDAR_VIEWS = ['month', 'week', 'agenda']
export const HOME_CALENDAR_VIEW_LABELS = { month: '月', week: '周', agenda: '日程' }
export const AGENDA_DAYS = 14

const IN_KINDS = ['INCOME', 'BORROW_IN', 'COLLECT_DEBT']
const OUT_KINDS = ['EXPENSE', 'LEND_OUT', 'REPAY_DEBT']
const DAY_KEYS = /^\d{4}-\d{2}-\d{2}$/
const pad = value => String(value).padStart(2, '0')

export function calendarViewStorageKey(scope) {
  return `home_calendar_view_v1:${scope || 'local'}`
}

export function readCalendarView(storageKey, storage = typeof localStorage === 'undefined' ? null : localStorage) {
  try {
    const stored = storage?.getItem(storageKey)
    return HOME_CALENDAR_VIEWS.includes(stored) ? stored : 'month'
  } catch {
    return 'month'
  }
}

export function writeCalendarView(storageKey, view, storage = typeof localStorage === 'undefined' ? null : localStorage) {
  try {
    storage?.setItem(storageKey, HOME_CALENDAR_VIEWS.includes(view) ? view : 'month')
  } catch {
    /* 隐私模式/配额满时忽略：视图选择降级为本次会话内有效 */
  }
}

/** 任务的时刻：跨天时间段任务在日历里按整天铺开，只标「全天」；否则取有意义的时钟时间 */
export function timeLabelOf(task) {
  if (taskRange(task)) return '全天'
  for (const value of [task?.dueAt, task?.startAt]) {
    if (!value) continue
    const date = new Date(value)
    if (Number.isNaN(date.getTime())) continue
    if (date.getHours() || date.getMinutes()) return `${pad(date.getHours())}:${pad(date.getMinutes())}`
  }
  return '全天'
}

function taskTone(task) {
  if (task?.status === 'COMPLETED') return 'done'
  if (task?.status === 'ABANDONED') return 'abandoned'
  return 'open'
}

const PRIORITY_WEIGHT = { HIGH: 0, MEDIUM: 1, LOW: 2, NONE: 3 }

function normalizeTask(task) {
  return {
    id: String(task.publicId || task.id || ''),
    title: task.title || task.name || '未命名任务',
    tone: taskTone(task),
    priority: task.priority || 'NONE',
    time: timeLabelOf(task)
  }
}

function normalizeFlow(row) {
  const incoming = IN_KINDS.includes(row.kind)
  return {
    id: String(row.publicId || row.id || ''),
    title: row.payee || row.note || (incoming ? '收入' : '支出'),
    amount: Number(row.amount) || 0,
    incoming,
    tone: incoming ? 'in' : OUT_KINDS.includes(row.kind) ? 'out' : 'move'
  }
}

/**
 * 把三个数据源摊平成「日期 -> 当天有什么」的索引：
 * - 任务：时间段任务铺到区间内每一天（与日历页口径一致），单日期任务落到到期日
 * - 流水：按 occurredOn 落点（转账也占位，明细里标灰）
 * - 工时：按 record.date 落点，净时长用同一套 `CALC.actualMin`
 */
export function buildDayIndex({ tasks = [], transactions = [], records = {}, settings = {} } = {}) {
  const index = new Map()
  const touch = key => {
    let bucket = index.get(key)
    if (!bucket) {
      bucket = { tasks: [], flows: [], work: null }
      index.set(key, bucket)
    }
    return bucket
  }
  for (const task of tasks) {
    if (!task || task.deleted) continue
    const status = task.status
    if (status !== 'OPEN' && status !== 'COMPLETED') continue
    const range = taskRange(task)
    if (range) {
      const start = new Date(range.start.getTime())
      start.setHours(0, 0, 0, 0)
      const end = new Date(range.end.getTime())
      end.setHours(0, 0, 0, 0)
      // 脏数据可能给出跨年的区间，逐日铺开时兜一个上限
      let cursor = start
      for (let guard = 0; cursor <= end && guard < 400; guard += 1) {
        touch(toDateKey(cursor)).tasks.push(task)
        cursor = addDays(cursor, 1)
      }
      continue
    }
    if (!task.dueAt) continue
    const due = new Date(task.dueAt)
    if (Number.isNaN(due.getTime())) continue
    touch(toDateKey(due)).tasks.push(task)
  }
  for (const row of transactions) {
    if (!row || row.deleted) continue
    const key = String(row.occurredOn || '').slice(0, 10)
    if (!DAY_KEYS.test(key)) continue
    touch(key).flows.push(row)
  }
  for (const record of Object.values(records || {})) {
    const key = String(record?.date || '').slice(0, 10)
    if (!DAY_KEYS.test(key)) continue
    touch(key).work = {
      minutes: CALC.actualMin(record, settings),
      start: record.start || '',
      end: record.end || ''
    }
  }
  return index
}

/** 单日明细：任务（未完成优先、紧急在前、时间早在前）、流水、工时 */
export function dayDetail(index, key, todayKey = '') {
  const bucket = index.get(key)
  const date = fromDateKey(key)
  const tasks = (bucket?.tasks || [])
    .map(normalizeTask)
    .sort((left, right) => (left.tone === 'done') - (right.tone === 'done')
      || (PRIORITY_WEIGHT[left.priority] ?? 3) - (PRIORITY_WEIGHT[right.priority] ?? 3)
      || (left.time === '全天' ? 1 : 0) - (right.time === '全天' ? 1 : 0)
      || left.time.localeCompare(right.time)
      || left.title.localeCompare(right.title))
  const flows = (bucket?.flows || [])
    .map(normalizeFlow)
    .sort((left, right) => Math.abs(right.amount) - Math.abs(left.amount))
  return {
    key,
    date,
    weekday: `周${'一二三四五六日'[(date.getDay() + 6) % 7]}`,
    today: key === todayKey,
    tasks,
    flows,
    work: bucket?.work || null,
    count: tasks.length + flows.length + (bucket?.work ? 1 : 0)
  }
}

function holidayLabelOf(holiday) {
  if (!holiday) return ''
  return holiday.off ? '休' : '班'
}

function isOffKey(key, holiday) {
  if (holiday) return Boolean(holiday.off)
  return CALC.isWeekendKey(key)
}

function cellOf(index, key, todayKey, holidays) {
  const detail = dayDetail(index, key, todayKey)
  const holiday = holidays?.[key]
  const date = detail.date
  return {
    key,
    day: date.getDate(),
    today: detail.today,
    off: isOffKey(key, holiday),
    holidayLabel: holidayLabelOf(holiday),
    holidayName: holiday?.name || '',
    taskCount: detail.tasks.length,
    flowCount: detail.flows.length,
    hasWork: Boolean(detail.work),
    count: detail.count
  }
}

/** 月视图格子：整周铺满（含上月/下月补位，灰显），点补位日期也能看当天 */
export function monthCells(anchorKey, index, { todayKey = '', holidays = {} } = {}) {
  const anchor = fromDateKey(anchorKey)
  const monthKey = String(anchorKey).slice(0, 7)
  return monthWeeks(anchor).map(week => week.map(date => {
    const key = toDateKey(date)
    return { ...cellOf(index, key, todayKey, holidays), inMonth: key.startsWith(monthKey) }
  }))
}

/** 周视图：周一起算的 7 天，带当天明细 */
export function weekDays(anchorKey, index, { todayKey = '', holidays = {} } = {}) {
  const start = startOfWeek(fromDateKey(anchorKey))
  return Array.from({ length: 7 }, (_, offset) => {
    const key = toDateKey(addDays(start, offset))
    const detail = dayDetail(index, key, todayKey)
    return { ...detail, ...cellOf(index, key, todayKey, holidays) }
  })
}

/** 日程视图：从 fromKey 起 N 天里**有内容**的那些天，空白天不占位置 */
export function agendaGroups(index, fromKey, days = AGENDA_DAYS, { todayKey = '', holidays = {} } = {}) {
  const start = fromDateKey(fromKey)
  const groups = []
  for (let offset = 0; offset < days; offset += 1) {
    const key = toDateKey(addDays(start, offset))
    const detail = dayDetail(index, key, todayKey)
    if (!detail.count) continue
    groups.push({ ...detail, ...cellOf(index, key, todayKey, holidays) })
  }
  return groups
}

function money(value) {
  return Number(value || 0).toLocaleString('zh-CN', { maximumFractionDigits: 2 })
}

/**
 * 周/日程视图里的一天要显示成一行摘要：任务 → 流水 → 工时，最多 limit 条。
 * 剩下的交给「点开当天」看全量，避免在 1/3 屏宽里堆满字。
 */
export function dayPreview(detail, limit = 2) {
  const rows = detail.tasks.map(task => ({
    id: `task-${task.id}`,
    tone: task.tone === 'done' ? 'done' : task.tone === 'abandoned' ? 'abandoned' : 'task',
    time: task.time,
    label: task.title
  }))
  for (const flow of detail.flows) {
    rows.push({
      id: `flow-${flow.id}`,
      tone: flow.tone === 'in' ? 'in' : flow.tone === 'out' ? 'out' : 'move',
      time: '',
      label: `${flow.incoming ? '+' : '−'}¥${money(flow.amount)}${flow.title ? ` ${flow.title}` : ''}`
    })
  }
  if (detail.work) {
    rows.push({
      id: `work-${detail.key}`,
      tone: 'work',
      time: '',
      label: `工时 ${detail.work.start || '—'}–${detail.work.end || '—'} · ${(detail.work.minutes / 60).toFixed(1)}h`
    })
  }
  return { rows: rows.slice(0, limit), more: Math.max(0, rows.length - limit) }
}

export function rangeTitle(view, anchorKey, days = AGENDA_DAYS) {  const anchor = fromDateKey(anchorKey)
  if (view === 'week') {
    const start = startOfWeek(anchor)
    const end = addDays(start, 6)
    return `${start.getMonth() + 1}/${start.getDate()} – ${end.getMonth() + 1}/${end.getDate()}`
  }
  if (view === 'agenda') return `${anchor.getMonth() + 1}/${anchor.getDate()} 起 ${days} 天`
  return `${anchor.getFullYear()} 年 ${anchor.getMonth() + 1} 月`
}

/** 本月已过的比例：照抄日历卡原来的「37% 已过」口径；翻到别的月份时给 0 / 100 */
export function monthProgress(anchorKey, todayKey) {
  const monthKey = String(anchorKey).slice(0, 7)
  const todayMonth = String(todayKey || '').slice(0, 7)
  if (todayMonth < monthKey) return 0
  if (todayMonth > monthKey) return 100
  const total = CALC.daysInMonth(monthKey)
  return Math.round((Number(todayKey.slice(8, 10)) / total) * 100)
}

/** 当月（或本周、未来 N 天）有多少天有安排：卡片 meta 的一行小字 */
export function rangeStats(cells) {
  const rows = cells.flat ? cells.flat() : cells
  const busy = rows.filter(cell => cell.count > 0).length
  const total = rows.reduce((sum, cell) => sum + cell.count, 0)
  return { busy, total, days: rows.length }
}
