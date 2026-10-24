export function sortTaskItems(tasks, { sortBy = 'time', direction = 'asc' } = {}) {
  if (sortBy === 'manual') return [...tasks]

  const directionFactor = direction === 'desc' ? -1 : 1
  return [...tasks].sort((left, right) => {
    if (['time', 'created', 'updated'].includes(sortBy)) {
      const leftValue = sortValue(left, sortBy)
      const rightValue = sortValue(right, sortBy)
      if (!leftValue !== !rightValue) return leftValue ? -1 : 1
    }
    let order = 0
    if (sortBy === 'title') order = String(left.title || '').localeCompare(String(right.title || ''), 'zh-CN')
    else if (sortBy === 'tag') order = String(left.tagIds?.[0] || '').localeCompare(String(right.tagIds?.[0] || ''), 'zh-CN')
    else if (sortBy === 'priority') order = priorityRank(left.priority) - priorityRank(right.priority)
    else order = dateValue(sortValue(left, sortBy)) - dateValue(sortValue(right, sortBy))
    return order * directionFactor || Number(left.sortOrder || 0) - Number(right.sortOrder || 0)
  })
}

export function groupTaskItems(tasks, groupBy, lists, today = new Date(), tags = [], { includeEmptyLists = false } = {}) {
  if (groupBy === 'list') {
    const buckets = new Map()
    for (const task of tasks) {
      const key = task.listId || 'no-list'
      if (!buckets.has(key)) buckets.set(key, [])
      buckets.get(key).push(task)
    }
    const entries = includeEmptyLists
      ? [
          ...lists.filter((list) => !list.archived && list.objectType !== 'NOTE').map((list) => [list.publicId, buckets.get(list.publicId) || []]),
          ...[...buckets.entries()].filter(([key]) => key === 'no-list' || !lists.some((list) => list.publicId === key && !list.archived && list.objectType !== 'NOTE')),
        ]
      : [...buckets.entries()]
    return entries.map(([key, items]) => ({
      key: `list:${key}`,
      label: key === 'no-list' ? '未分配清单' : lists.find((list) => list.publicId === key)?.name || '已归档清单',
      tasks: items,
    }))
  }

  if (groupBy === 'tag' || groupBy === 'priority') {
    const buckets = new Map()
    for (const task of tasks) {
      const value = groupBy === 'tag' ? task.tagIds?.[0] || 'no-tag' : task.priority || 'NONE'
      if (!buckets.has(value)) buckets.set(value, [])
      buckets.get(value).push(task)
    }
    const priorityLabels = { HIGH: '高优先级', MEDIUM: '中优先级', LOW: '低优先级', NONE: '无优先级' }
    const entries = [...buckets.entries()]
    if (groupBy === 'priority') entries.sort(([left], [right]) => priorityRank(left) - priorityRank(right))
    return entries.map(([key, items]) => ({
      key: `${groupBy}:${key}`,
      label: groupBy === 'tag' ? key === 'no-tag' ? '无标签' : tags.find((tag) => tag.publicId === key)?.name || '标签' : priorityLabels[key] || '无优先级',
      tasks: items,
    }))
  }

  if (groupBy === 'created') {
    const buckets = new Map()
    for (const task of tasks) {
      const value = task.createdAt ? new Date(task.createdAt).toISOString().slice(0, 10) : 'unknown'
      if (!buckets.has(value)) buckets.set(value, [])
      buckets.get(value).push(task)
    }
    return [...buckets.entries()].sort(([left], [right]) => left === 'unknown' ? 1 : right === 'unknown' ? -1 : right.localeCompare(left)).map(([key, items]) => ({
      key: `created:${key}`,
      label: key === 'unknown' ? '创建时间未知' : key,
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

export function taskGroupDropPatch(task, groupBy, groupKey, now = new Date()) {
  if (!task || !groupKey) return null
  if (groupBy === 'list' && groupKey.startsWith('list:')) {
    const listId = groupKey.slice(5)
    return listId === 'no-list' || listId === task.listId ? null : { listId }
  }
  if (groupBy === 'tag' && groupKey.startsWith('tag:')) {
    const tagId = groupKey.slice(4)
    if (tagId === 'no-tag') return task.tagIds?.length ? { tagIds: [] } : null
    if (task.tagIds?.[0] === tagId) return null
    return { tagIds: [tagId, ...(task.tagIds || []).filter(id => id !== tagId)] }
  }
  if (groupBy === 'priority' && groupKey.startsWith('priority:')) {
    const priority = groupKey.slice(9)
    return priority === (task.priority || 'NONE') ? null : { priority }
  }
  if (groupBy === 'time') {
    const current = task.dueAt ? new Date(task.dueAt) : null
    const start = new Date(now)
    start.setHours(0, 0, 0, 0)
    const dueAt = groupKey === 'none'
      ? null
      : groupKey === 'overdue'
        ? new Date(start.getTime() - 86400000 + (current ? current.getHours() * 3600000 + current.getMinutes() * 60000 : 17 * 3600000)).toISOString()
        : groupKey === 'today'
          ? new Date(start.getTime() + (current && current >= start && current < start.getTime() + 86400000 ? current.getTime() - start.getTime() : 17 * 3600000)).toISOString()
          : groupKey === 'later'
            ? new Date(start.getTime() + 86400000 + (current && current >= start.getTime() + 86400000 ? current.getHours() * 3600000 + current.getMinutes() * 60000 : 9 * 3600000)).toISOString()
            : undefined
    if (dueAt === undefined || (dueAt === null && !task.dueAt) || (dueAt && current?.getTime() === Date.parse(dueAt))) return null
    return { dueAt }
  }
  return null
}

function priorityRank(priority) {
  return ({ HIGH: 0, MEDIUM: 1, LOW: 2, NONE: 3 })[priority] ?? 4
}

function dateValue(value) {
  return value ? Date.parse(value) || 0 : Number.MAX_SAFE_INTEGER
}

function sortValue(task, sortBy) {
  if (sortBy === 'created') return task.createdAt
  if (sortBy === 'updated') return task.updatedAt
  return task.dueAt
}
