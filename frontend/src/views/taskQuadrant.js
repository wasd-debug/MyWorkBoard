/** 四象限：规则匹配、分组、排序与偏好持久化（纯函数，便于单测） */

export const QUADRANT_DEFS = [
  { key: 'IMPORTANT_URGENT', name: '重要且紧急', icon: 'alert', color: '#e34d59' },
  { key: 'IMPORTANT_NOT_URGENT', name: '重要不紧急', icon: 'star', color: '#e6a23c' },
  { key: 'NOT_IMPORTANT_URGENT', name: '不重要但紧急', icon: 'bolt', color: '#4d78db' },
  { key: 'NOT_IMPORTANT_NOT_URGENT', name: '不重要不紧急', icon: 'leaf', color: '#3fae7a' }
]

export const DATE_BUCKETS = [
  { value: 'OVERDUE', label: '已过期' },
  { value: 'TODAY', label: '今天' },
  { value: 'TOMORROW', label: '明天' },
  { value: 'THIS_WEEK', label: '本周' },
  { value: 'LATER', label: '以后' },
  { value: 'NONE', label: '无日期' }
]

export const PRIORITY_OPTIONS = [
  { value: 'HIGH', label: '高' },
  { value: 'MEDIUM', label: '中' },
  { value: 'LOW', label: '低' },
  { value: 'NONE', label: '无' }
]

export const ITEM_KIND_OPTIONS = [
  { value: 'ALL', label: '所有' },
  { value: 'TASK', label: '任务' },
  { value: 'NOTE', label: '笔记' }
]

export const GROUP_OPTIONS = [
  { value: 'list', label: '清单' },
  { value: 'dueAt', label: '时间' },
  { value: 'createdAt', label: '创建时间' },
  { value: 'tag', label: '标签' },
  { value: 'priority', label: '优先级' },
  { value: 'none', label: '无' }
]

export const SORT_OPTIONS = [
  { value: 'dueAt', label: '时间' },
  { value: 'createdAt', label: '创建时间' },
  { value: 'updatedAt', label: '修改时间' },
  { value: 'title', label: '标题' },
  { value: 'tag', label: '标签' },
  { value: 'priority', label: '优先级' }
]

export const ORDER_OPTIONS = [
  { value: 'asc', label: '从旧到新' },
  { value: 'desc', label: '从新到旧' }
]

const PRIORITY_RANK = { HIGH: 0, MEDIUM: 1, LOW: 2, NONE: 3 }
const DATE_LATER_KEYS = ['TOMORROW', 'THIS_WEEK', 'LATER', 'NONE']

function emptyRule(dates, priorities) {
  return { listIds: [], tagIds: [], dates: [...dates], priorities: [...priorities], itemKind: 'ALL' }
}

export function defaultQuadrants() {
  return [
    { ...QUADRANT_DEFS[0], rule: emptyRule(['OVERDUE', 'TODAY'], ['HIGH']), groupBy: 'list', sortBy: 'dueAt', order: 'asc' },
    { ...QUADRANT_DEFS[1], rule: emptyRule(DATE_LATER_KEYS, ['HIGH']), groupBy: 'list', sortBy: 'dueAt', order: 'asc' },
    { ...QUADRANT_DEFS[2], rule: emptyRule(['OVERDUE', 'TODAY'], ['MEDIUM', 'LOW', 'NONE']), groupBy: 'list', sortBy: 'dueAt', order: 'asc' },
    { ...QUADRANT_DEFS[3], rule: emptyRule(DATE_LATER_KEYS, ['MEDIUM', 'LOW', 'NONE']), groupBy: 'list', sortBy: 'dueAt', order: 'asc' }
  ]
}

export function startOfDay(value) {
  const date = value instanceof Date ? new Date(value) : new Date(value)
  date.setHours(0, 0, 0, 0)
  return date
}

function toDate(value) {
  if (!value) return null
  const date = new Date(value)
  return Number.isNaN(date.getTime()) ? null : date
}

/** 日期桶：已过期 / 今天 / 明天 / 本周 / 以后 / 无日期 */
export function dateBucket(task, now = new Date()) {
  const due = toDate(task?.dueAt)
  if (!due) return 'NONE'
  const today = startOfDay(now)
  const tomorrow = new Date(today.getTime() + 86400000)
  const weekEnd = new Date(today.getTime() + (7 - ((today.getDay() + 6) % 7)) * 86400000)
  if (due < today) return 'OVERDUE'
  if (due >= today && due < tomorrow) return 'TODAY'
  if (due >= tomorrow && due < new Date(tomorrow.getTime() + 86400000)) return 'TOMORROW'
  if (due < weekEnd) return 'THIS_WEEK'
  return 'LATER'
}

/** 规则是否命中：各条件为「空即全部」，条件之间是「与」 */
export function ruleMatches(task, rule, now = new Date()) {
  if (!task || task.deleted) return false
  const current = rule || {}
  const kind = current.itemKind || 'ALL'
  if (kind !== 'ALL') {
    const taskKind = task.itemKind === 'NOTE' ? 'NOTE' : 'TASK'
    if (taskKind !== kind) return false
  }
  if (current.listIds?.length && !current.listIds.includes(task.listId)) return false
  if (current.tagIds?.length) {
    const tags = task.tagIds || []
    if (!tags.some(id => current.tagIds.includes(id))) return false
  }
  if (current.priorities?.length && !current.priorities.includes(task.priority || 'NONE')) return false
  if (current.dates?.length && !current.dates.includes(dateBucket(task, now))) return false
  return true
}

export function splitByQuadrant(tasks, quadrants, { now = new Date(), showCompleted = false } = {}) {
  const result = {}
  for (const quadrant of quadrants) result[quadrant.key] = []
  for (const task of tasks || []) {
    if (!task || task.deleted || task.status === 'ABANDONED') continue
    const done = task.status === 'COMPLETED'
    if (done && !showCompleted) continue
    for (const quadrant of quadrants) {
      if (ruleMatches(task, quadrant.rule, now)) {
        result[quadrant.key].push(task)
        break
      }
    }
  }
  return result
}

function timestamp(value) {
  const date = toDate(value)
  return date ? date.getTime() : null
}

function compareNullable(left, right, multiplier) {
  if (left == null && right == null) return 0
  if (left == null) return 1
  if (right == null) return -1
  return (left - right) * multiplier
}

export function sortTasks(tasks, sortBy = 'dueAt', order = 'asc', { tags = [] } = {}) {
  const direction = order === 'desc' ? -1 : 1
  const tagName = task => {
    const first = (task.tagIds || [])[0]
    return tags.find(item => item.publicId === first)?.name || ''
  }
  return [...tasks].sort((left, right) => {
    switch (sortBy) {
      case 'createdAt':
        return compareNullable(timestamp(left.createdAt), timestamp(right.createdAt), direction)
      case 'updatedAt':
        return compareNullable(timestamp(left.updatedAt), timestamp(right.updatedAt), direction)
      case 'title':
        return String(left.title || '').localeCompare(String(right.title || ''), 'zh-Hans-CN') * direction
      case 'tag':
        return tagName(left).localeCompare(tagName(right), 'zh-Hans-CN') * direction
      case 'priority': {
        const rank = (PRIORITY_RANK[left.priority] ?? 3) - (PRIORITY_RANK[right.priority] ?? 3)
        if (rank) return rank * direction
        return String(left.title || '').localeCompare(String(right.title || ''), 'zh-Hans-CN')
      }
      case 'dueAt':
      default:
        // 无日期的任务固定排在最后，不随顺序翻转
        return compareNullable(timestamp(left.dueAt), timestamp(right.dueAt), direction)
    }
  })
}

function formatDay(date, now) {
  const today = startOfDay(now)
  const diff = Math.round((startOfDay(date).getTime() - today.getTime()) / 86400000)
  if (diff === 0) return '今天'
  if (diff === -1) return '昨天'
  if (diff === 1) return '明天'
  return `${date.getMonth() + 1}月${date.getDate()}日`
}

export function groupTasks(tasks, groupBy = 'list', { lists = [], tags = [], now = new Date() } = {}) {
  if (groupBy === 'none') {
    return [{ key: 'all', label: '', icon: '', color: '', tasks }]
  }
  const groups = new Map()
  const push = (key, label, icon, color, task) => {
    if (!groups.has(key)) groups.set(key, { key, label, icon, color, tasks: [] })
    groups.get(key).tasks.push(task)
  }
  for (const task of tasks) {
    if (groupBy === 'list') {
      const list = lists.find(item => item.publicId === task.listId)
      push(task.listId || 'none', list?.name || '收集箱', list?.icon || '', list?.color || '', task)
    } else if (groupBy === 'dueAt') {
      const bucket = dateBucket(task, now)
      const label = DATE_BUCKETS.find(item => item.value === bucket)?.label || '无日期'
      push(`due-${bucket}`, label, '', '', task)
    } else if (groupBy === 'createdAt') {
      const date = toDate(task.createdAt)
      if (!date) {
        push('created-none', '无创建时间', '', '', task)
        continue
      }
      const key = `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')}`
      push(key, formatDay(date, now), '', '', task)
    } else if (groupBy === 'tag') {
      const first = (task.tagIds || [])[0]
      const tag = tags.find(item => item.publicId === first)
      if (!tag) push('tag-none', '无标签', '', '', task)
      else push(tag.publicId, tag.name, '', tag.color, task)
    } else if (groupBy === 'priority') {
      const value = task.priority || 'NONE'
      push(`priority-${value}`, PRIORITY_OPTIONS.find(item => item.value === value)?.label || '无', '', '', task)
    }
  }
  const list = [...groups.values()]
  if (groupBy === 'dueAt') {
    const order = DATE_BUCKETS.map(item => `due-${item.value}`)
    list.sort((left, right) => order.indexOf(left.key) - order.indexOf(right.key))
  } else if (groupBy === 'priority') {
    const order = PRIORITY_OPTIONS.map(item => `priority-${item.value}`)
    list.sort((left, right) => order.indexOf(left.key) - order.indexOf(right.key))
  } else if (groupBy === 'createdAt') {
    list.sort((left, right) => (left.key < right.key ? 1 : left.key > right.key ? -1 : 0))
  } else if (groupBy === 'list') {
    list.sort((left, right) => {
      const leftIndex = lists.findIndex(item => item.publicId === left.key)
      const rightIndex = lists.findIndex(item => item.publicId === right.key)
      return (leftIndex < 0 ? 999 : leftIndex) - (rightIndex < 0 ? 999 : rightIndex)
    })
  } else {
    list.sort((left, right) => left.label.localeCompare(right.label, 'zh-Hans-CN'))
  }
  return list
}

export function buildQuadrantGroups(tasks, quadrant, { lists = [], tags = [], now = new Date() } = {}) {
  const sorted = sortTasks(tasks, quadrant.sortBy, quadrant.order, { tags })
  return groupTasks(sorted, quadrant.groupBy, { lists, tags, now })
}

export function summarizeRule(rule, { lists = [], tags = [] } = {}) {
  const current = rule || {}
  const listLabel = () => {
    if (!current.listIds?.length) return '所有'
    if (current.listIds.length === 1) return lists.find(item => item.publicId === current.listIds[0])?.name || '1 个清单'
    const first = lists.find(item => item.publicId === current.listIds[0])?.name || ''
    return `${first} 和其他${current.listIds.length - 1}个清单`
  }
  const tagLabel = () => {
    if (!current.tagIds?.length) return '所有'
    if (current.tagIds.length === 1) return tags.find(item => item.publicId === current.tagIds[0])?.name || '1 个标签'
    return `${current.tagIds.length} 个标签`
  }
  const dateLabel = () => {
    if (!current.dates?.length) return '所有'
    return current.dates.map(value => DATE_BUCKETS.find(item => item.value === value)?.label || value).join(',')
  }
  const priorityLabel = () => {
    if (!current.priorities?.length) return '所有'
    return current.priorities.map(value => PRIORITY_OPTIONS.find(item => item.value === value)?.label || value).join('、')
  }
  return {
    list: listLabel(),
    tag: tagLabel(),
    date: dateLabel(),
    priority: priorityLabel(),
    itemKind: ITEM_KIND_OPTIONS.find(item => item.value === (current.itemKind || 'ALL'))?.label || '所有'
  }
}

export function describeRule(rule, { lists = [], tags = [] } = {}) {
  const summary = summarizeRule(rule, { lists, tags })
  const parts = []
  if (summary.list !== '所有') parts.push(`清单为 ${summary.list}`)
  if (summary.tag !== '所有') parts.push(`标签为 ${summary.tag}`)
  if (summary.date !== '所有') parts.push(`日期为 ${summary.date}`)
  if (summary.priority !== '所有') parts.push(`优先级为 ${summary.priority}`)
  if (summary.itemKind !== '所有') parts.push(`类型为 ${summary.itemKind}`)
  return parts.length ? parts.join('，') : '全部未完成任务'
}

export function preferenceKey(scope) {
  return `task_quadrant_v1:${scope || 'anonymous'}`
}

export function normalizeState(raw) {
  const defaults = defaultQuadrants()
  if (!raw || typeof raw !== 'object') return { quadrants: defaults, showCompleted: false, collapsed: {} }
  const quadrants = defaults.map((fallback, index) => {
    const stored = Array.isArray(raw.quadrants) ? raw.quadrants.find(item => item?.key === fallback.key) || raw.quadrants[index] : null
    if (!stored) return fallback
    return {
      ...fallback,
      name: String(stored.name || fallback.name),
      icon: String(stored.icon || fallback.icon),
      color: String(stored.color || fallback.color),
      groupBy: GROUP_OPTIONS.some(item => item.value === stored.groupBy) ? stored.groupBy : fallback.groupBy,
      sortBy: SORT_OPTIONS.some(item => item.value === stored.sortBy) ? stored.sortBy : fallback.sortBy,
      order: ORDER_OPTIONS.some(item => item.value === stored.order) ? stored.order : fallback.order,
      rule: {
        listIds: Array.isArray(stored.rule?.listIds) ? stored.rule.listIds.filter(Boolean) : fallback.rule.listIds,
        tagIds: Array.isArray(stored.rule?.tagIds) ? stored.rule.tagIds.filter(Boolean) : fallback.rule.tagIds,
        dates: Array.isArray(stored.rule?.dates) ? stored.rule.dates.filter(value => DATE_BUCKETS.some(item => item.value === value)) : fallback.rule.dates,
        priorities: Array.isArray(stored.rule?.priorities) ? stored.rule.priorities.filter(value => PRIORITY_OPTIONS.some(item => item.value === value)) : fallback.rule.priorities,
        itemKind: ITEM_KIND_OPTIONS.some(item => item.value === stored.rule?.itemKind) ? stored.rule.itemKind : fallback.rule.itemKind
      }
    }
  })
  return {
    quadrants,
    showCompleted: Boolean(raw.showCompleted),
    collapsed: raw.collapsed && typeof raw.collapsed === 'object' ? { ...raw.collapsed } : {}
  }
}

export function loadState(scope) {
  try {
    const raw = localStorage.getItem(preferenceKey(scope))
    return normalizeState(raw ? JSON.parse(raw) : null)
  } catch {
    return normalizeState(null)
  }
}

export function saveState(scope, state) {
  try {
    localStorage.setItem(preferenceKey(scope), JSON.stringify(state))
  } catch { /* 忽略存储配额错误 */ }
}
