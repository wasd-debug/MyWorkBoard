const PRIORITY_ORDER = { HIGH: 0, MEDIUM: 1, LOW: 2, NONE: 3 }
const PRIORITY_LABEL = { HIGH: '高优先级', MEDIUM: '中优先级', LOW: '低优先级', NONE: '无优先级' }
export const PLAN_GROUP_MODES = [
  { value: 'list', label: '按清单分组' },
  { value: 'tag', label: '按标签分组' },
  { value: 'priority', label: '按优先级分组' }
]

function taskDate(task) {
  const raw = task?.dueAt || task?.startAt
  if (!raw) return null
  const date = new Date(raw)
  return Number.isNaN(date.getTime()) ? null : date
}

export function isUndated(task) {
  return !task?.startAt && !task?.dueAt
}

export function isOverdue(task, now = new Date()) {
  const date = taskDate(task)
  if (!date) return false
  const startOfToday = new Date(now.getFullYear(), now.getMonth(), now.getDate())
  // 全天任务（或零点对齐）按日期比较：今天到期不算过期，否则排到今天的任务会一直留在「已过期」里
  const allDay = Boolean(task?.allDay) || (date.getHours() === 0 && date.getMinutes() === 0)
  return allDay ? date.getTime() < startOfToday.getTime() : date.getTime() < now.getTime()
}

/**
 * 安排任务面板的候选池：未完成、可排期的任务。
 * 已过期与无日期是两个可独立开关的筛选桶；都不开时返回全部。
 */
export function planCandidates(tasks = [], options = {}) {
  const {
    lists = [], tags = [], now = new Date(),
    includeOverdue = false, includeUndated = true, hideNotes = false, hideHierarchy = false
  } = options
  const listIds = new Set(lists.map(item => item.publicId))
  return tasks.filter(task => {
    if (!task || task.deleted) return false
    if (task.status && task.status !== 'OPEN') return false
    if (hideNotes && task.itemKind === 'NOTE') return false
    if (!listIds.size || !listIds.has(task.listId)) return false
    if (hideHierarchy && task.parentId) return false
    const overdue = isOverdue(task, now)
    const undated = isUndated(task)
    if (!includeOverdue && !includeUndated) return true
    if (includeUndated && undated) return true
    if (includeOverdue && overdue) return true
    return false
  })
}

export function groupPlanTasks(tasks = [], options = {}) {
  const { groupBy = 'list', lists = [], tags = [] } = options
  const groups = new Map()
  const push = (key, label, color, task) => {
    if (!groups.has(key)) groups.set(key, { key, label, color, items: [] })
    groups.get(key).items.push(task)
  }

  for (const task of tasks) {
    if (groupBy === 'priority') {
      const priority = task.priority || 'NONE'
      push(priority, PRIORITY_LABEL[priority] || '无优先级', null, task)
      continue
    }
    if (groupBy === 'tag') {
      const tag = (task.tagIds || []).map(id => tags.find(item => item.publicId === id)).find(Boolean)
      if (tag) push(tag.publicId, tag.name, tag.color, task)
      else push('__none__', '无标签', null, task)
      continue
    }
    const list = lists.find(item => item.publicId === task.listId)
    push(list?.publicId || '__none__', list?.name || '未分组', list?.color || null, task)
  }

  const rank = (group) => {
    if (groupBy === 'priority') return PRIORITY_ORDER[group.key] ?? 9
    if (group.key === '__none__') return 1e6
    const list = lists.find(item => item.publicId === group.key)
    return list ? Number(list.sortOrder || 0) : 5e5
  }

  return [...groups.values()]
    .map(group => ({
      ...group,
      items: group.items.sort((left, right) => (PRIORITY_ORDER[left.priority] ?? 3) - (PRIORITY_ORDER[right.priority] ?? 3)
        || Number(left.sortOrder || 0) - Number(right.sortOrder || 0))
    }))
    .sort((left, right) => rank(left) - rank(right) || left.label.localeCompare(right.label, 'zh-Hans-CN'))
}
