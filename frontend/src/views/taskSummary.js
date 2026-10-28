const DAY = 24 * 60 * 60 * 1000
// 单组报告行数上限：避免超大时间范围（数千条）生成巨型表格导致渲染卡死/内存暴涨
export const SUMMARY_MAX_ROWS = 500

export const SUMMARY_FIELDS = [
  { key: 'status', label: '状态' },
  { key: 'progress', label: '进度' },
  { key: 'completedAt', label: '完成时间' },
  { key: 'taskTime', label: '任务时间' },
  { key: 'title', label: '任务名称' },
  { key: 'parent', label: '主任务' },
  { key: 'tags', label: '标签' },
  { key: 'focus', label: '专注数据' },
  { key: 'list', label: '所属清单' },
  { key: 'details', label: '详细内容' },
]

export const SUMMARY_GROUPS = [
  { key: 'status', label: '按完成状态' },
  { key: 'list', label: '按清单' },
  { key: 'completedAt', label: '按完成时间' },
  { key: 'taskTime', label: '按任务时间' },
  { key: 'tag', label: '按标签' },
  { key: 'priority', label: '按优先级' },
  { key: 'assignee', label: '按指派人' },
]

function startOfDay(value) {
  const date = new Date(value)
  date.setHours(0, 0, 0, 0)
  return date
}

function addDays(value, count) {
  const date = new Date(value)
  date.setDate(date.getDate() + count)
  return date
}

function weekStart(value) {
  const date = startOfDay(value)
  date.setDate(date.getDate() - ((date.getDay() + 6) % 7))
  return date
}

function monthStart(value, offset = 0) {
  const date = new Date(value)
  date.setDate(1)
  date.setHours(0, 0, 0, 0)
  date.setMonth(date.getMonth() + offset)
  return date
}

export function summaryDateRange(preset, now = new Date(), custom = {}) {
  const today = startOfDay(now)
  if (preset === 'ALL') return null
  if (preset === 'TODAY') return { from: today, to: addDays(today, 1) }
  if (preset === 'TOMORROW') return { from: addDays(today, 1), to: addDays(today, 2) }
  if (preset === 'YESTERDAY') return { from: addDays(today, -1), to: today }
  if (preset === 'THIS_WEEK') return { from: weekStart(today), to: addDays(weekStart(today), 7) }
  if (preset === 'NEXT_WEEK') return { from: addDays(weekStart(today), 7), to: addDays(weekStart(today), 14) }
  if (preset === 'LAST_WEEK') return { from: addDays(weekStart(today), -7), to: weekStart(today) }
  if (preset === 'THIS_MONTH') return { from: monthStart(today), to: monthStart(today, 1) }
  if (preset === 'LAST_MONTH') return { from: monthStart(today, -1), to: monthStart(today) }
  if (preset !== 'CUSTOM' || !custom.from || !custom.to) return null
  const from = startOfDay(`${custom.from}T00:00:00`)
  const to = addDays(startOfDay(`${custom.to}T00:00:00`), 1)
  return { from, to }
}

function dateForItem(item) {
  const value = (item.status === 'COMPLETED' || item.status === 'DONE') && item.completedAt
    ? item.completedAt
    : item.dueAt || item.startAt || item.date || item.createdAt
  return value ? new Date(value) : null
}

function inDateRange(item, range) {
  if (!range) return true
  const date = dateForItem(item)
  return Boolean(date && !Number.isNaN(date.getTime()) && date >= range.from && date < range.to)
}

function matchesStatus(item, status, now) {
  if (status === 'ALL') return true
  if (status === 'COMPLETED') return item.status === 'COMPLETED' || item.status === 'DONE'
  if (status === 'ABANDONED') return item.status === 'ABANDONED'
  if (status === 'INCOMPLETE') return item.status === 'OPEN' && (!item.startAt || new Date(item.startAt) > now)
  if (status === 'IN_PROGRESS') return item.status === 'OPEN' && item.startAt && new Date(item.startAt) <= now
  return true
}

export function filterSummaryItems(items, options, now = new Date()) {
  return items.filter(item => {
    if (item.deleted || !inDateRange(item, options.range)) return false
    if (options.listMode === 'LISTS' && !item.listId) return false
    if (options.listMode === 'SELECTED' && !options.listIds.includes(item.listId)) return false
    if (options.listMode === 'CHECKINS' && item.kind !== 'checkin') return false
    if (['LISTS', 'SELECTED'].includes(options.listMode) && item.kind === 'checkin') return false
    if (options.tagMode === 'HAS' && !(item.tagIds || []).length) return false
    if (options.tagMode === 'NONE' && (item.tagIds || []).length) return false
    if (options.tagMode === 'SELECTED' && !options.tagIds.some(id => (item.tagIds || []).includes(id))) return false
    if (options.status !== 'ALL' && !matchesStatus(item, options.status, now)) return false
    if (options.priorities.length && !options.priorities.includes(item.priority || 'NONE')) return false
    return true
  })
}

export function groupSummaryItems(items, groupBy, names = {}) {
  const groups = new Map()
  for (const item of items) {
    let keys
    if (groupBy === 'status') keys = [item.status || 'OPEN']
    else if (groupBy === 'list') keys = [item.listId || 'NO_LIST']
    else if (groupBy === 'completedAt') keys = [item.completedAt ? item.completedAt.slice(0, 10) : 'NO_COMPLETION_DATE']
    else if (groupBy === 'taskTime') keys = [item.dueAt || item.startAt ? (item.dueAt || item.startAt).slice(0, 10) : 'NO_TASK_DATE']
    else if (groupBy === 'tag') keys = item.tagIds?.length ? item.tagIds : ['NO_TAG']
    else if (groupBy === 'priority') keys = [item.priority || 'NONE']
    else keys = [item.assigneeName || 'UNASSIGNED']
    for (const key of keys) {
      if (!groups.has(key)) groups.set(key, [])
      groups.get(key).push(item)
    }
  }
  return [...groups].map(([key, groupedItems]) => ({
    key,
    label: names[key] || ({ OPEN: '未完成', COMPLETED: '已完成', DONE: '已完成', ABANDONED: '已放弃', NO_LIST: '无清单', NO_TAG: '无标签', NONE: '无优先级', NO_COMPLETION_DATE: '无完成时间', NO_TASK_DATE: '无任务时间', UNASSIGNED: '未指派' })[key] || key,
    items: groupedItems,
  }))
}

function escapeMarkdown(value) {
  return String(value ?? '').replaceAll('|', '\\|').replaceAll('\n', ' ')
}

function displayDate(value) {
  if (!value) return '—'
  if (/^\d{4}-\d{2}-\d{2}$/.test(value)) return value
  const date = new Date(value)
  return Number.isNaN(date.getTime()) ? String(value) : date.toLocaleString('zh-CN', { hour12: false })
}

const DETAIL_MAX = 200

function clampCell(value, max = DETAIL_MAX) {
  const text = String(value || '').replace(/\s+/g, ' ').trim()
  return text.length > max ? `${text.slice(0, max)}…` : text
}

export function buildSummaryMarkdown(groups, fields = SUMMARY_FIELDS.map(item => item.key), names = {}) {
  const selectedFields = SUMMARY_FIELDS.filter(field => fields.includes(field.key))
  if (!groups.length) return '当前筛选条件下没有匹配的记录。'
  return groups.map(group => {
    const header = `## ${group.label}（${group.items.length}）`
    if (!selectedFields.length) return `${header}\n\n共 ${group.items.length} 项。`
    const headings = selectedFields.map(field => field.label)
    // 单组行数上限：超大范围（数千条）时避免生成超大表格导致页面卡死/内存暴涨
    const items = group.items.slice(0, SUMMARY_MAX_ROWS)
    const rows = items.map(item => `| ${selectedFields.map(field => {
      if (field.key === 'status') return escapeMarkdown(names.status?.[item.status] || item.status || '未完成')
      if (field.key === 'progress') {
        const total = Number(item.totalSubtasks || item.checklist?.length || 0)
        const done = Number(item.completedSubtasks || item.checklist?.filter(entry => entry.completed).length || 0)
        return total ? `${done}/${total}` : (item.status === 'COMPLETED' || item.status === 'DONE' ? '100%' : '0%')
      }
      if (field.key === 'completedAt') return escapeMarkdown(displayDate(item.completedAt))
      if (field.key === 'taskTime') return escapeMarkdown(displayDate(item.dueAt || item.startAt || item.date))
      if (field.key === 'title') return escapeMarkdown(clampCell(item.title || item.name || '—', 120))
      if (field.key === 'parent') return escapeMarkdown(clampCell(item.parentTitle || '—', 120))
      if (field.key === 'tags') return escapeMarkdown(clampCell((item.tagNames || []).join(', ') || '—', 120))
      if (field.key === 'focus') return item.focusMinutes ? `${item.focusMinutes} 分钟` : '—'
      if (field.key === 'list') return escapeMarkdown(item.listName || '—')
      return escapeMarkdown(clampCell(item.description || '—'))
    }).join(' | ')} |`)
    const table = `${header}\n\n| ${headings.join(' | ')} |\n| ${headings.map(() => '---').join(' | ')} |\n${rows.join('\n')}`
    const truncated = group.items.length > items.length ? `\n\n> 其余 ${group.items.length - items.length} 项未展开（单组最多显示 ${SUMMARY_MAX_ROWS} 项）` : ''
    return `${table}${truncated}`
  }).join('\n\n')
}

export function summaryRangeLabel(range, locale = 'zh-CN') {
  if (!range) return '全部时间'
  const formatter = new Intl.DateTimeFormat(locale, { month: 'long', day: 'numeric' })
  return `${formatter.format(range.from)} - ${formatter.format(new Date(range.to.getTime() - DAY))}`
}
