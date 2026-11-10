import test from 'node:test'
import assert from 'node:assert/strict'
import {
  defaultLayout, HOME_WIDGET_KEYS, layoutStorageKey, moveWidget, normalizeLayout, readLayout,
  toggleWidget, visibleWidgets, widgetSpan, writeLayout
} from './homeLayout.js'
import {
  budgetExecutions, cashflowSeries, categoryBreakdown, compactMoney, hoursLabel, overdueDays,
  periodSummary, taskBuckets, worktimeStats
} from './homeMetrics.js'

function memoryStorage(initial = {}) {
  const map = new Map(Object.entries(initial))
  return {
    getItem: key => (map.has(key) ? map.get(key) : null),
    setItem: (key, value) => { map.set(key, String(value)) },
    dump: () => Object.fromEntries(map)
  }
}

test('layout keeps every known widget once and appends new ones', () => {
  const layout = normalizeLayout([
    { key: 'cashflow' },
    { key: 'removed-widget', hidden: false },
    { key: 'tasks', hidden: true },
    { key: 'cashflow', hidden: true }
  ])
  assert.equal(layout.length, HOME_WIDGET_KEYS.length)
  assert.deepEqual(layout.slice(0, 2), [{ key: 'cashflow', hidden: false }, { key: 'tasks', hidden: true }])
  assert.deepEqual(new Set(layout.map(item => item.key)), new Set(HOME_WIDGET_KEYS))
  assert.deepEqual(defaultLayout().map(item => item.key), HOME_WIDGET_KEYS)
})

test('drag reorder moves the widget to the target slot and keeps the rest stable', () => {
  const layout = defaultLayout()
  const moved = moveWidget(layout, 'ai', 'tasks')
  assert.deepEqual(moved.slice(0, 3).map(item => item.key), ['ai', 'tasks', 'calendar'])
  assert.equal(moved.length, layout.length)
  // 越界/自身不改变顺序，避免拖拽误触发后顺序发散
  assert.deepEqual(moveWidget(layout, 'ai', 'missing').map(item => item.key), layout.map(item => item.key))
  assert.deepEqual(moveWidget(layout, 'tasks', 'tasks').map(item => item.key), layout.map(item => item.key))
})

test('hiding a widget removes it from the canvas but keeps it restorable', () => {
  const hidden = toggleWidget(defaultLayout(), 'budget')
  assert.deepEqual(visibleWidgets(hidden).map(item => item.key).includes('budget'), false)
  assert.equal(hidden.length, HOME_WIDGET_KEYS.length)
  assert.deepEqual(visibleWidgets(toggleWidget(hidden, 'budget')).map(item => item.key), HOME_WIDGET_KEYS)
})

test('layout survives a broken storage payload and is scoped per account', () => {
  const storage = memoryStorage({ 'home_widget_layout_v1:u1': '{not-json' })
  assert.deepEqual(readLayout(layoutStorageKey('u1'), storage).map(item => item.key), HOME_WIDGET_KEYS)
  const key = layoutStorageKey('u1')
  writeLayout(key, toggleWidget(defaultLayout(), 'ai'), storage)
  assert.deepEqual(readLayout(key, storage).filter(item => item.hidden).map(item => item.key), ['ai'])
  assert.deepEqual(readLayout(layoutStorageKey('u2'), storage).map(item => item.key), HOME_WIDGET_KEYS)
  assert.equal(widgetSpan('cashflow'), 5)
  assert.equal(widgetSpan('unknown'), 4)
})

test('cashflow series sums only income and expense and ignores transfers', () => {
  const transactions = [
    { kind: 'EXPENSE', amount: 30, occurredOn: '2026-11-01' },
    { kind: 'INCOME', amount: 100, occurredOn: '2026-11-01' },
    { kind: 'TRANSFER', amount: 500, occurredOn: '2026-11-02' },
    { kind: 'EXPENSE', amount: 20, occurredOn: '2026-10-31', deleted: true },
    { kind: 'EXPENSE', amount: 45, occurredOn: '2026-03-08' }
  ]
  assert.deepEqual(periodSummary(transactions, '2026-11-01', '2026-11-30'), {
    income: 100, expense: 30, net: 70, incomeCount: 1, expenseCount: 1
  })
  const days = cashflowSeries(transactions, { mode: 'day', today: '2026-11-10' })
  assert.equal(days.length, 30)
  assert.equal(days[0].expense, 30)
  assert.equal(days[0].income, 100)
  assert.equal(days[1].expense, 0)
  const months = cashflowSeries(transactions, { mode: 'month', today: '2026-11-10' })
  assert.equal(months.length, 12)
  assert.equal(months[10].expense, 30)
  assert.equal(months[2].expense, 45)
})

test('category breakdown aggregates to the requested level and range', () => {
  const categories = [
    { id: 'food', name: '餐饮' },
    { id: 'lunch', name: '午餐', parentId: 'food' },
    { id: 'dinner', name: '晚餐', parentId: 'food' },
    { id: 'tech', name: '数码' }
  ]
  const transactions = [
    { kind: 'EXPENSE', amount: 20, occurredOn: '2026-11-02', categoryId: 'lunch' },
    { kind: 'EXPENSE', amount: 50, occurredOn: '2026-11-03', categoryId: 'dinner' },
    { kind: 'EXPENSE', amount: 2000, occurredOn: '2026-11-04', categoryId: 'tech' },
    { kind: 'EXPENSE', amount: 80, occurredOn: '2026-01-04', categoryId: 'lunch' },
    { kind: 'INCOME', amount: 900, occurredOn: '2026-11-05', categoryId: 'tech' }
  ]
  const primary = categoryBreakdown(transactions, categories, { scope: 'month', level: 'primary', today: '2026-11-10' })
  assert.deepEqual(primary.map(item => [item.name, item.amount]), [['数码', 2000], ['餐饮', 70]])
  assert.equal(Math.round(primary[0].share * 100), 97)
  const secondary = categoryBreakdown(transactions, categories, { scope: 'month', level: 'secondary', today: '2026-11-10' })
  assert.deepEqual(secondary.map(item => [item.name, item.amount]), [['数码', 2000], ['晚餐', 50], ['午餐', 20]])
  const year = categoryBreakdown(transactions, categories, { scope: 'year', level: 'primary', today: '2026-11-10' })
  assert.deepEqual(year.map(item => [item.name, item.amount]), [['数码', 2000], ['餐饮', 150]])
  assert.equal(categoryBreakdown(transactions, categories, { scope: 'month', level: 'primary', today: '2026-11-10', limit: 1 }).length, 1)
})

test('budget execution counts child categories for a parent budget', () => {
  const categories = [
    { id: 'food', name: '餐饮' },
    { id: 'lunch', name: '午餐', parentId: 'food' },
    { id: 'tech', name: '数码' }
  ]
  const budgets = [
    { id: 'b1', monthKey: '2026-11', categoryId: 'food', amount: 500 },
    { id: 'b2', monthKey: '2026-11', categoryId: null, amount: 3000 },
    { id: 'b3', monthKey: '2026-10', categoryId: null, amount: 1000 }
  ]
  const transactions = [
    { kind: 'EXPENSE', amount: 120, occurredOn: '2026-11-02', categoryId: 'lunch' },
    { kind: 'EXPENSE', amount: 800, occurredOn: '2026-11-03', categoryId: 'tech' },
    { kind: 'EXPENSE', amount: 60, occurredOn: '2026-10-03', categoryId: 'tech' }
  ]
  const rows = budgetExecutions(budgets, transactions, categories, '2026-11')
  assert.equal(rows.length, 2)
  assert.equal(rows[0].total, true)
  assert.equal(rows[0].spent, 920)
  assert.equal(rows[0].remaining, 2080)
  const food = rows.find(item => item.id === 'b1')
  assert.equal(food.spent, 120)
  assert.equal(food.name, '餐饮')
  assert.equal(Math.round(food.ratio * 100), 24)
})

test('worktime stats use the standard day, overtime snapshot and hourly rate', () => {
  const settings = {
    workStart: '09:00', workEnd: '18:00', lunchMin: 90, daysPerMonth: 21.75,
    autoDays: false, salaryPre: 0, salaryPost: 10000, basis: 'post', salaries: {}
  }
  const records = {
    '2026-11-09': { id: 'r1', date: '2026-11-09', start: '08:30', end: '18:00', overtimeMin: 30 },
    '2026-11-10': { id: 'r2', date: '2026-11-10', start: '09:00', end: '18:00', overtimeMin: 0 },
    '2026-10-01': { id: 'r3', date: '2026-10-01', start: '09:00', end: '18:00', overtimeMin: 60 }
  }
  const month = worktimeStats({ records, settings, range: 'month', today: '2026-11-10' })
  assert.equal(month.label, '本月')
  assert.equal(month.days, 2)
  assert.equal(month.totalMin, 930)
  assert.equal(month.overtimeMin, 30)
  assert.equal(Math.round(month.rate), 59)
  // 趋势只铺到「今天」为止，未来的空日期不占横轴
  assert.equal(month.series.length, 10)
  assert.equal(Math.round(month.series[8].value), 57)

  const week = worktimeStats({ records, settings, range: 'week', today: '2026-11-10' })
  assert.equal(week.label, '本周')
  assert.equal(week.granularity, 'day')
  assert.deepEqual(week.keys, ['2026-11-09', '2026-11-10'])
  assert.equal(week.totalMin, 930)

  const year = worktimeStats({ records, settings, range: 'year', today: '2026-11-10' })
  assert.equal(year.granularity, 'month')
  assert.equal(year.series.length, 12)
  assert.equal(year.series[10].minutes, 930)
  assert.equal(year.series[9].minutes, 450)
  assert.equal(year.series[0].value, null)
})

test('task buckets separate today, overdue and range tasks', () => {
  const tasks = [
    { publicId: 'a', status: 'OPEN', priority: 'LOW', dueAt: '2026-11-08T10:00:00' },
    { publicId: 'b', status: 'OPEN', priority: 'HIGH', dueAt: '2026-11-10T18:00:00' },
    { publicId: 'c', status: 'OPEN', priority: 'MEDIUM', startAt: '2026-11-09T09:00:00', dueAt: '2026-11-12T18:00:00' },
    { publicId: 'd', status: 'OPEN', priority: 'HIGH', dueAt: '2026-11-15T10:00:00' },
    { publicId: 'e', status: 'COMPLETED', priority: 'HIGH', dueAt: '2026-11-10T10:00:00' },
    { publicId: 'f', status: 'OPEN', priority: 'HIGH', dueAt: '2026-11-09T23:59:00' }
  ]
  const { today, overdue } = taskBuckets(tasks, '2026-11-10')
  assert.deepEqual(today.map(item => item.publicId), ['b', 'c'])
  // 高优先级在前，其次按结束时间
  assert.deepEqual(overdue.map(item => item.publicId), ['f', 'a'])
  assert.equal(overdueDays(tasks[0], '2026-11-10'), 2)
  // 当天到期不算逾期（与任务页标红口径一致）
  assert.equal(overdueDays(tasks[1], '2026-11-10'), 0)
  assert.equal(overdueDays(tasks[3], '2026-11-10'), 0)
})

test('compact labels stay short enough for tiles', () => {
  assert.equal(compactMoney(350), '350')
  assert.equal(compactMoney(1234), '1,234')
  assert.equal(compactMoney(12345), '1.2万')
  assert.equal(compactMoney(123456), '12万')
  assert.equal(hoursLabel(0), '0')
  assert.equal(hoursLabel(450), '7.5')
  assert.equal(hoursLabel(6000), '100')
})
