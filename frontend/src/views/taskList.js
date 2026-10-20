export function sortTaskItems(tasks, { sortBy = 'time', direction = 'asc' } = {}) {
  if (sortBy === 'manual') return [...tasks]

  const directionFactor = direction === 'desc' ? -1 : 1
  return [...tasks].sort((left, right) => {
    if (sortBy === 'time' && !left.dueAt !== !right.dueAt) return left.dueAt ? -1 : 1
    let order = 0
    if (sortBy === 'title') order = String(left.title || '').localeCompare(String(right.title || ''), 'zh-CN')
    else if (sortBy === 'priority') order = priorityRank(left.priority) - priorityRank(right.priority)
    else order = dateValue(left.dueAt) - dateValue(right.dueAt)
    return order * directionFactor || Number(left.sortOrder || 0) - Number(right.sortOrder || 0)
  })
}

export function groupTaskItems(tasks, groupBy, lists, today = new Date()) {
  if (groupBy === 'list') {
    const buckets = new Map()
    for (const task of tasks) {
      const key = task.listId || 'no-list'
      if (!buckets.has(key)) buckets.set(key, [])
      buckets.get(key).push(task)
    }
    return [...buckets.entries()].map(([key, items]) => ({
      key: `list:${key}`,
      label: key === 'no-list' ? '未分配清单' : lists.find((list) => list.publicId === key)?.name || '已归档清单',
      tasks: items,
    }))
  }

  const start = new Date(today)
  start.setHours(0, 0, 0, 0)
  const tomorrow = new Date(start)
  tomorrow.setDate(tomorrow.getDate() + 1)
  const result = [
    { key: 'overdue', label: '逾期', tasks: [] },
    { key: 'today', label: '今天', tasks: [] },
    { key: 'later', label: '之后', tasks: [] },
    { key: 'none', label: '无日期', tasks: [] },
  ]

  for (const task of tasks) {
    if (!task.dueAt) result[3].tasks.push(task)
    else if (new Date(task.dueAt) < start) result[0].tasks.push(task)
    else if (new Date(task.dueAt) < tomorrow) result[1].tasks.push(task)
    else result[2].tasks.push(task)
  }
  return result.filter((group) => group.tasks.length)
}

function priorityRank(priority) {
  return ({ HIGH: 0, MEDIUM: 1, LOW: 2, NONE: 3 })[priority] ?? 4
}

function dateValue(value) {
  return value ? Date.parse(value) || 0 : Number.MAX_SAFE_INTEGER
}
