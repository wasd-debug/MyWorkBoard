// 搜索：条件模型 + 任务/标签/清单/过滤器/习惯/倒数日 匹配（纯函数，供 node --test 直接调用）
// 日期一律按本地时区的 yyyy-MM-dd 判定，不用 toISOString（东八区早 8 点前会跨天）

import { dateKey, effectiveTarget, parseKey, todayKey } from '../countdowns/countdownUtils.js'

/** 弹窗顶部页签：点哪个就搜哪类对象 */
export const SEARCH_SCOPES = [
  { value: 'TASK', label: '任务' },
  { value: 'TAG', label: '标签' },
  { value: 'LIST', label: '清单' },
  { value: 'FILTER', label: '过滤器' }
]

/** 日期下拉：顺序与参考图一致（所有日期 / 本周 / 下周 / 上周 / 本月 / 上月 / 自定义） */
export const SEARCH_DATE_OPTIONS = [
  { value: 'ALL', label: '所有日期' },
  { value: 'THIS_WEEK', label: '本周' },
  { value: 'NEXT_WEEK', label: '下周' },
  { value: 'LAST_WEEK', label: '上周' },
  { value: 'THIS_MONTH', label: '本月' },
  { value: 'LAST_MONTH', label: '上月' },
  { value: 'CUSTOM', label: '自定义' }
]

export const SEARCH_STATUS_OPTIONS = [
  { value: 'ALL', label: '所有状态' },
  { value: 'OPEN', label: '未完成' },
  { value: 'COMPLETED', label: '已完成' },
  { value: 'ABANDONED', label: '已放弃' }
]

export const SEARCH_PRIORITY_OPTIONS = [
  { value: 'ALL', label: '所有优先级' },
  { value: 'HIGH', label: '高' },
  { value: 'MEDIUM', label: '中' },
  { value: 'LOW', label: '低' },
  { value: 'NONE', label: '无' }
]

/** 类型：任务/笔记按条目级类型分，习惯与倒数日单独一类 */
export const SEARCH_TYPE_OPTIONS = [
  { value: 'ALL', label: '所有类型' },
  { value: 'TASK', label: '任务' },
  { value: 'NOTE', label: '笔记' },
  { value: 'HABIT', label: '习惯' },
  { value: 'COUNTDOWN', label: '倒数日' }
]

const DATE_VALUES = SEARCH_DATE_OPTIONS.map(item => item.value)
const STATUS_VALUES = SEARCH_STATUS_OPTIONS.map(item => item.value)
const PRIORITY_VALUES = SEARCH_PRIORITY_OPTIONS.map(item => item.value)
const TYPE_VALUES = SEARCH_TYPE_OPTIONS.map(item => item.value)

const pad = value => String(value).padStart(2, '0')

function addDays(date, amount) {
  return new Date(date.getFullYear(), date.getMonth(), date.getDate() + amount)
}

function startOfWeek(date) {
  const weekday = (date.getDay() + 6) % 7 // 周一为一周开始
  return addDays(date, -weekday)
}

function startOfMonth(date) {
  return new Date(date.getFullYear(), date.getMonth(), 1)
}

export function emptySearchCriteria(overrides = {}) {
  return {
    q: '',
    date: 'ALL',
    start: '',
    end: '',
    listIds: [],
    tagIds: [],
    status: 'ALL',
    priority: 'ALL',
    type: 'ALL',
    ...overrides
  }
}

function csv(value) {
  if (Array.isArray(value)) return value.map(item => String(item)).filter(Boolean)
  return String(value ?? '').split(',').map(item => item.trim()).filter(Boolean)
}

/** route.query → 条件；非法值一律回落到默认，保证地址栏乱传也不会炸 */
export function searchCriteriaFromQuery(query = {}) {
  const date = DATE_VALUES.includes(String(query.date)) ? String(query.date) : 'ALL'
  return emptySearchCriteria({
    q: String(query.q ?? '').slice(0, 200),
    date,
    start: /^\d{4}-\d{2}-\d{2}$/.test(String(query.start || '')) ? String(query.start) : '',
    end: /^\d{4}-\d{2}-\d{2}$/.test(String(query.end || '')) ? String(query.end) : '',
    listIds: csv(query.lists),
    tagIds: csv(query.tags),
    status: STATUS_VALUES.includes(String(query.status)) ? String(query.status) : 'ALL',
    priority: PRIORITY_VALUES.includes(String(query.priority)) ? String(query.priority) : 'ALL',
    type: TYPE_VALUES.includes(String(query.type)) ? String(query.type) : 'ALL'
  })
}

/** 条件 → route.query；默认值不写进地址栏，链接更短 */
export function searchCriteriaToQuery(criteria = {}) {
  const value = emptySearchCriteria(criteria)
  const query = {}
  if (value.q) query.q = value.q
  if (value.date && value.date !== 'ALL') query.date = value.date
  if (value.date === 'CUSTOM') {
    if (value.start) query.start = value.start
    if (value.end) query.end = value.end
  }
  if (value.listIds.length) query.lists = value.listIds.join(',')
  if (value.tagIds.length) query.tags = value.tagIds.join(',')
  if (value.status && value.status !== 'ALL') query.status = value.status
  if (value.priority && value.priority !== 'ALL') query.priority = value.priority
  if (value.type && value.type !== 'ALL') query.type = value.type
  return query
}

/** 除关键词外还设了几个条件，用于「保存为过滤器」前的提示与徽标 */
export function searchCriteriaFilterCount(criteria = {}) {
  const value = emptySearchCriteria(criteria)
  return [value.date !== 'ALL', value.listIds.length > 0, value.tagIds.length > 0, value.status !== 'ALL', value.priority !== 'ALL', value.type !== 'ALL']
    .filter(Boolean).length
}

/** 日期下拉的区间；返回 null 表示不限日期 */
export function dateRangeFor(value, today = todayKey(), start = '', end = '') {
  if (!value || value === 'ALL') return null
  const base = parseKey(today)
  if (value === 'CUSTOM') {
    if (!/^\d{4}-\d{2}-\d{2}$/.test(String(start || '')) && !/^\d{4}-\d{2}-\d{2}$/.test(String(end || ''))) return null
    return { start: start || end, end: end || start }
  }
  if (value === 'THIS_WEEK') {
    const from = startOfWeek(base)
    return { start: dateKey(from), end: dateKey(addDays(from, 6)) }
  }
  if (value === 'NEXT_WEEK') {
    const from = addDays(startOfWeek(base), 7)
    return { start: dateKey(from), end: dateKey(addDays(from, 6)) }
  }
  if (value === 'LAST_WEEK') {
    const from = addDays(startOfWeek(base), -7)
    return { start: dateKey(from), end: dateKey(addDays(from, 6)) }
  }
  if (value === 'THIS_MONTH') {
    const from = startOfMonth(base)
    return { start: dateKey(from), end: dateKey(addDays(startOfMonth(addDays(from, 32)), -1)) }
  }
  if (value === 'LAST_MONTH') {
    const from = startOfMonth(addDays(startOfMonth(base), -1))
    return { start: dateKey(from), end: dateKey(addDays(startOfMonth(base), -1)) }
  }
  return null
}

/** 搜索框提示里显示的当前日期区间，如「本周 (11月2日-11月8日)」 */
export function dateRangeLabel(value, today = todayKey(), start = '', end = '') {
  const option = SEARCH_DATE_OPTIONS.find(item => item.value === value) || SEARCH_DATE_OPTIONS[0]
  const range = dateRangeFor(value, today, start, end)
  if (!range) return option.label
  const fmt = key => {
    const date = parseKey(key)
    return Number.isNaN(date.getTime()) ? '' : `${date.getMonth() + 1}月${date.getDate()}日`
  }
  const from = fmt(range.start)
  const to = fmt(range.end)
  if (!from || !to) return option.label
  return `${option.label} (${from}-${to})`
}

function normalizeText(value) {
  return String(value ?? '').toLowerCase()
}

export function searchTokens(q) {
  return normalizeText(q).trim().split(/\s+/).filter(Boolean)
}

function matchesTokens(haystack, tokens) {
  if (!tokens.length) return true
  return tokens.every(token => haystack.includes(token))
}

function listById(context, id) {
  return context?.listsById?.get?.(id) || null
}

function taskIsNote(task, context) {
  if (!task) return false
  if (task.itemKind === 'NOTE') return true
  if (task.itemKind === 'TASK') return false
  return listById(context, task.listId)?.objectType === 'NOTE'
}

function taskHaystack(task, context) {
  const list = listById(context, task.listId)
  const tagNames = (task.tagIds || []).map(id => context?.tagsById?.get?.(id)?.name).filter(Boolean)
  const checklist = (task.checklist || []).map(item => item?.title || item?.text || '').join(' ')
  return normalizeText([task.title, task.description, checklist, tagNames.join(' '), list?.name].join(' '))
}

function habitHaystack(habit) {
  return normalizeText([habit?.name, habit?.note, habit?.description].join(' '))
}

function countdownTitle(item) {
  return item?.name || item?.title || ''
}

function countdownHaystack(item) {
  return normalizeText([countdownTitle(item), item?.note, item?.remark].join(' '))
}

function dueDateKey(task) {
  if (!task?.dueAt) return ''
  const date = new Date(task.dueAt)
  return Number.isNaN(date.getTime()) ? '' : dateKey(date)
}

/** 任务是否命中条件 */
export function matchTask(task, criteria = {}, context = {}) {
  if (!task || task.deleted) return false
  const value = emptySearchCriteria(criteria)
  if (!matchesTokens(taskHaystack(task, context), searchTokens(value.q))) return false

  const isNote = taskIsNote(task, context)
  if (value.type !== 'ALL') {
    if (value.type === 'HABIT' || value.type === 'COUNTDOWN') return false
    if (value.type === 'NOTE' && !isNote) return false
    if (value.type === 'TASK' && isNote) return false
  }

  if (value.listIds.length && !value.listIds.includes(task.listId)) return false
  if (value.tagIds.length && !(task.tagIds || []).some(id => value.tagIds.includes(id))) return false
  if (value.priority !== 'ALL' && String(task.priority || 'NONE') !== value.priority) return false
  if (value.status === 'COMPLETED' && task.status !== 'COMPLETED') return false
  if (value.status === 'ABANDONED' && task.status !== 'ABANDONED') return false
  if (value.status === 'OPEN' && task.status !== 'OPEN' && task.status !== 'IN_PROGRESS') return false

  const range = dateRangeFor(value.date, context.today, value.start, value.end)
  if (range) {
    const key = dueDateKey(task)
    if (!key || key < range.start || key > range.end) return false
  }
  return true
}

/** 习惯是否命中条件：习惯没有标签/优先级/日期，设了这些条件就不再出现在结果里 */
export function matchHabit(habit, criteria = {}, context = {}) {
  if (!habit || habit.deleted) return false
  const value = emptySearchCriteria(criteria)
  if (!matchesTokens(habitHaystack(habit), searchTokens(value.q))) return false
  if (value.type !== 'ALL' && value.type !== 'HABIT') return false
  if (value.listIds.length || value.tagIds.length) return false
  if (value.priority !== 'ALL') return false
  if (value.status !== 'ALL' && value.status !== 'OPEN') return false
  if (dateRangeFor(value.date, context.today, value.start, value.end)) return false
  return true
}

/** 倒数日：显示规则只作用于「智能清单/今天」这类自动汇总，显式搜索不藏（NEVER 除外） */
export function matchCountdown(item, criteria = {}, context = {}) {
  if (!item || item.deleted || item.archived) return false
  const value = emptySearchCriteria(criteria)
  if (!matchesTokens(countdownHaystack(item), searchTokens(value.q))) return false
  if (value.type !== 'ALL' && value.type !== 'COUNTDOWN') return false
  if (value.listIds.length || value.tagIds.length) return false
  if (value.priority !== 'ALL') return false
  if (value.status !== 'ALL' && value.status !== 'OPEN') return false
  if ((item.displayRule || 'SMART') === 'NEVER') return false
  const range = dateRangeFor(value.date, context.today, value.start, value.end)
  if (range) {
    const target = effectiveTarget(item, context.today)
    if (!target || target < range.start || target > range.end) return false
  }
  return true
}

export function countdownDays(item, today = todayKey()) {
  const target = effectiveTarget(item, today)
  const from = parseKey(today)
  const to = parseKey(target)
  if (Number.isNaN(to.getTime()) || Number.isNaN(from.getTime())) return 0
  return Math.round((to - from) / 86400000)
}

/**
 * 倒数日的「显示」选项：在智能清单（今天页）里是否出现。
 * SMART=当天；D3/D7=提前 N 天起；ALWAYS=一直；NEVER=不显示。
 */
export function countdownVisible(item, today = todayKey()) {
  if (!item || item.deleted || item.archived) return false
  const rule = item.displayRule || 'SMART'
  if (rule === 'NEVER') return false
  const days = countdownDays(item, today)
  if (rule === 'ALWAYS') return true
  if (rule === 'D3') return days >= 0 && days <= 3
  if (rule === 'D7') return days >= 0 && days <= 7
  return days === 0
}

/** 顶部数量徽标里的总数 */
export function searchResultTotal(results = {}) {
  return (results.tasks?.length || 0) + (results.habits?.length || 0) + (results.countdowns?.length || 0)
}

/** 关键词命中位置，供高亮用 */
export function highlightParts(value, q) {
  const text = String(value ?? '')
  const needle = String(q ?? '').trim().split(/\s+/).filter(Boolean)[0]
  if (!needle) return [{ text, hit: false }]
  const parts = []
  const lower = text.toLowerCase()
  const target = needle.toLowerCase()
  let index = 0
  let from = lower.indexOf(target)
  while (from >= 0) {
    if (from > index) parts.push({ text: text.slice(index, from), hit: false })
    parts.push({ text: text.slice(from, from + target.length), hit: true })
    index = from + target.length
    from = lower.indexOf(target, index)
  }
  if (index < text.length) parts.push({ text: text.slice(index), hit: false })
  return parts.length ? parts : [{ text, hit: false }]
}

/**
 * 搜索整页/弹窗共用的结果装配。
 * 习惯与倒数日不参与任务的排序口径，单独返回分组，由调用方决定先渲染哪一段。
 */
export function filterSearchResults({ tasks = [], habits = [], countdowns = [], lists = [], tags = [], criteria = {}, today = todayKey() } = {}) {
  const value = emptySearchCriteria(criteria)
  const context = {
    today,
    listsById: new Map(lists.map(item => [item.publicId, item])),
    tagsById: new Map(tags.map(item => [item.publicId, item]))
  }
  return {
    tasks: tasks.filter(task => matchTask(task, value, context)),
    habits: value.type === 'TASK' || value.type === 'NOTE' ? [] : habits.filter(habit => matchHabit(habit, value, context)),
    countdowns: value.type === 'TASK' || value.type === 'NOTE' ? [] : countdowns.filter(item => matchCountdown(item, value, context))
  }
}

/** 弹窗里非任务页签（标签/清单/过滤器）的匹配 */
export function matchOrganizations(items = [], q = '') {
  const tokens = searchTokens(q)
  if (!tokens.length) return []
  return items.filter(item => matchesTokens(normalizeText(item?.name), tokens))
}
