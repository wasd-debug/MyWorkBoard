/**
 * 首页画布的取数口径（纯函数，无 DOM）。
 *
 * 小组件只负责渲染：趋势曲线、分类排行、预算执行、工时汇总、当天/逾期任务
 * 都在这里算，避免同一口径在多个组件里各写一遍（也让它们能被单测锁住）。
 */
import { CALC } from '../../utils/calc.js'
import { taskHitsDay, taskRange } from '../../utils/taskDates.js'
import { categoryColor } from '../ledger/chartPalette.js'

const pad = value => String(value).padStart(2, '0')

export function dateKeyOf(date) {
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`
}

export function toDate(value) {
  if (value instanceof Date) return value
  if (!value) return new Date()
  return new Date(`${String(value).slice(0, 10)}T00:00:00`)
}

export const monthKeyOf = date => dateKeyOf(toDate(date)).slice(0, 7)
export const yearKeyOf = date => dateKeyOf(toDate(date)).slice(0, 4)

function liveTransactions(transactions = []) {
  return transactions.filter(item => item && !item.deleted)
}

/** 期间收支合计；只认 INCOME / EXPENSE，转账不算收也不算支 */
export function periodSummary(transactions = [], fromKey = '', toKey = '') {
  let income = 0, expense = 0, incomeCount = 0, expenseCount = 0
  for (const row of liveTransactions(transactions)) {
    const occurredOn = String(row.occurredOn || '')
    if (fromKey && occurredOn < fromKey) continue
    if (toKey && occurredOn > toKey) continue
    const amount = Number(row.amount) || 0
    if (row.kind === 'INCOME') { income += amount; incomeCount += 1 }
    else if (row.kind === 'EXPENSE') { expense += amount; expenseCount += 1 }
  }
  return { income, expense, net: income - expense, incomeCount, expenseCount }
}

/**
 * 收支趋势：
 * - mode='day'：当月每天（整月铺满，未来日期留 0，曲线不会缩在一角）
 * - mode='month'：当年 12 个月
 */
export function cashflowSeries(transactions = [], { mode = 'day', today = new Date() } = {}) {
  const todayDate = toDate(today)
  const rows = liveTransactions(transactions)
  if (mode === 'month') {
    const year = yearKeyOf(todayDate)
    return Array.from({ length: 12 }, (_, index) => {
      const key = `${year}-${pad(index + 1)}`
      const summary = periodSummary(rows, `${key}-01`, `${key}-31`)
      return { key, label: `${index + 1}月`, ...summary }
    })
  }
  const monthKey = monthKeyOf(todayDate)
  const days = CALC.daysInMonth(monthKey)
  return Array.from({ length: days }, (_, index) => {
    const key = `${monthKey}-${pad(index + 1)}`
    const summary = periodSummary(rows, key, key)
    return { key, label: String(index + 1), ...summary }
  })
}

/**
 * 支出分类排行。scope 决定时间范围，level 决定聚合到一级还是保留二级。
 * 只统计支出，转账与收入不参与（与账本首页的分类卡口径一致）。
 */
export function categoryBreakdown(transactions = [], categories = [], { scope = 'month', level = 'primary', today = new Date(), limit = 0 } = {}) {
  const todayDate = toDate(today)
  const monthKey = monthKeyOf(todayDate)
  const year = yearKeyOf(todayDate)
  const byId = new Map(categories.map(item => [String(item.id), item]))
  const bucket = new Map()
  for (const row of liveTransactions(transactions)) {
    if (row.kind !== 'EXPENSE') continue
    const occurredOn = String(row.occurredOn || '')
    if (scope === 'year' ? !occurredOn.startsWith(year) : !occurredOn.startsWith(monthKey)) continue
    const category = byId.get(String(row.categoryId))
    const parent = category?.parentId ? byId.get(String(category.parentId)) : null
    const target = level === 'primary' ? (parent || category) : category
    const name = target?.name || row.categoryName || '未分类'
    const current = bucket.get(name) || { name, amount: 0, color: target?.color, parentId: target?.parentId, id: target?.id }
    current.amount += Number(row.amount) || 0
    bucket.set(name, current)
  }
  const sorted = [...bucket.values()].sort((left, right) => right.amount - left.amount)
  const total = sorted.reduce((sum, item) => sum + item.amount, 0)
  const sliced = limit > 0 ? sorted.slice(0, limit) : sorted
  return sliced.map((item, index) => ({
    ...item,
    share: total > 0 ? item.amount / total : 0,
    color: categoryColor(item, index)
  }))
}

const budgetMonthOf = budget => String(budget.monthKey || budget.month || '').slice(0, 7)

/** 预算执行：categoryId 缺省视为月度总预算；一级分类预算要连二级分类一起累计 */
export function budgetExecutions(budgets = [], transactions = [], categories = [], monthKey = '') {
  const byId = new Map(categories.map(item => [String(item.id), item]))
  const expenses = liveTransactions(transactions)
    .filter(row => row.kind === 'EXPENSE' && String(row.occurredOn || '').startsWith(monthKey))
  const rows = budgets
    .filter(budget => !budget.deleted && (!monthKey || budgetMonthOf(budget) === monthKey))
    .map(budget => {
      const categoryId = budget.categoryId ? String(budget.categoryId) : ''
      const category = categoryId ? byId.get(categoryId) : null
      const parent = category?.parentId ? byId.get(String(category.parentId)) : null
      const spent = categoryId
        ? expenses.reduce((sum, row) => {
          const rowCategoryId = String(row.categoryId)
          const rowCategory = byId.get(rowCategoryId)
          const hit = rowCategoryId === categoryId || (rowCategory?.parentId && String(rowCategory.parentId) === categoryId)
          return hit ? sum + (Number(row.amount) || 0) : sum
        }, 0)
        : expenses.reduce((sum, row) => sum + (Number(row.amount) || 0), 0)
      const amount = Number(budget.amount ?? budget.budget ?? 0)
      return {
        id: budget.id || budget.publicId,
        total: !categoryId,
        name: category ? (parent ? `${parent.name} / ${category.name}` : category.name) : '月度总预算',
        amount,
        spent,
        remaining: amount - spent,
        ratio: amount > 0 ? spent / amount : 0,
        color: category?.color
      }
    })
  return rows.sort((left, right) => Number(right.total) - Number(left.total) || right.ratio - left.ratio)
}

/** 期间工时汇总 + 时薪趋势（本周/本月按天，本年按月） */
export function worktimeStats({ records = {}, settings = {}, holidays = {}, range = 'month', today = new Date() } = {}) {
  const todayDate = toDate(today)
  const todayKey = dateKeyOf(todayDate)
  const basis = settings.basis === 'pre' ? 'pre' : 'post'
  const monthKey = monthKeyOf(todayDate)
  let keys = []
  let label = '本月'
  let granularity = 'day'
  if (range === 'week') {
    keys = CALC.weekKeysTo(todayDate).filter(key => key <= todayKey)
    label = '本周'
  } else if (range === 'year') {
    keys = CALC.rangeKeys(new Date(todayDate.getFullYear(), 0, 1), todayDate)
    label = '本年'
    granularity = 'month'
  } else {
    keys = CALC.monthKeysTo(todayDate)
  }
  const salaryOf = (ym, mode) => CALC.monthSalary(settings.salaries, settings, mode, ym)
  const days = CALC.effDaysPerMonth(settings, monthKey, holidays, records)
  const stats = CALC.periodStats(keys, records, settings, basis, days, holidays, salaryOf)

  const rateOfDay = key => {
    const ctx = CALC.monthCtx(settings.salaries, settings, key.slice(0, 7))
    const dayCount = CALC.effDaysPerMonth(ctx, key.slice(0, 7), holidays, records)
    const rate = CALC.dayRate(records[key], ctx, basis, dayCount)
    return rate > 0 ? Math.round(rate * 100) / 100 : null
  }
  const series = granularity === 'month'
    ? Array.from({ length: 12 }, (_, index) => {
      const ym = `${todayDate.getFullYear()}-${pad(index + 1)}`
      const monthKeys = keys.filter(key => key.startsWith(ym))
      const bucketStats = monthKeys.length
        ? CALC.periodStats(monthKeys, records, settings, basis,
          CALC.effDaysPerMonth(settings, ym, holidays, records), holidays, salaryOf)
        : { totalMin: 0, realRate: 0 }
      return {
        key: ym,
        label: `${index + 1}月`,
        value: bucketStats.totalMin > 0 ? Math.round(bucketStats.realRate * 100) / 100 : null,
        minutes: bucketStats.totalMin
      }
    })
    : keys.map(key => ({ key, label: key.slice(5).replace('-', '/'), value: rateOfDay(key), minutes: CALC.actualMin(records[key], settings) }))

  return {
    label,
    keys,
    granularity,
    totalMin: stats.totalMin,
    overtimeMin: stats.otMin,
    days: stats.days,
    rate: stats.realRate,
    baseRate: stats.baseRate,
    earned: stats.earned,
    series
  }
}

function taskEndAt(task) {
  const range = taskRange(task)
  if (range) return range.end
  return task?.dueAt ? new Date(task.dueAt) : null
}

/** 今天要做的任务与已逾期任务；时间段任务按「区间内每一天」命中 */
export function taskBuckets(tasks = [], today = new Date()) {
  const todayDate = toDate(today)
  const dayStart = new Date(todayDate.getTime())
  dayStart.setHours(0, 0, 0, 0)
  const dayEnd = new Date(dayStart.getTime() + 24 * 60 * 60 * 1000)
  const todayTasks = []
  const overdue = []
  for (const task of tasks) {
    if (!task || task.deleted || task.status !== 'OPEN') continue
    const end = taskEndAt(task)
    if (!end) continue
    if (end < dayStart) overdue.push(task)
    else if (taskHitsDay(task, dayStart, dayEnd)) todayTasks.push(task)
  }
  const weight = { HIGH: 0, MEDIUM: 1, LOW: 2, NONE: 3 }
  const order = (left, right) => (weight[left.priority] ?? 3) - (weight[right.priority] ?? 3)
    || (taskEndAt(left)?.getTime() || 0) - (taskEndAt(right)?.getTime() || 0)
  return { today: todayTasks.sort(order), overdue: overdue.sort(order) }
}

/** 逾期天数（按结束日计，当天到期不算逾期） */
export function overdueDays(task, today = new Date()) {
  const end = taskEndAt(task)
  if (!end) return 0
  const endDay = new Date(end.getTime())
  endDay.setHours(0, 0, 0, 0)
  const todayDay = new Date(toDate(today).getTime())
  todayDay.setHours(0, 0, 0, 0)
  const diff = Math.round((todayDay - endDay) / (24 * 60 * 60 * 1000))
  return diff > 0 ? diff : 0
}

/** 金额缩写：¥1.2万 / ¥350 */
export function compactMoney(value) {
  const amount = Number(value) || 0
  if (Math.abs(amount) >= 10000) return `${(amount / 10000).toFixed(amount >= 100000 ? 0 : 1)}万`
  return amount.toLocaleString('zh-CN', { maximumFractionDigits: amount >= 100 ? 0 : 2 })
}

export function hoursLabel(minutes) {
  const value = (Number(minutes) || 0) / 60
  if (!value) return '0'
  return Math.abs(value) >= 100 ? value.toFixed(0) : (Math.round(value * 10) / 10).toFixed(1)
}
