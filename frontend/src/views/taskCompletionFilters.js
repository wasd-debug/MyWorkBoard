function localDay(value) {
  const date = value instanceof Date ? new Date(value) : new Date(value)
  if (Number.isNaN(date.getTime())) return null
  date.setHours(0, 0, 0, 0)
  return date
}

function dayKey(date) {
  return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')}`
}

export function toggleCompletionListSelection(listIds, allListIds, listId, checked) {
  const selected = new Set(listIds === null ? allListIds : listIds)
  if (checked) selected.add(listId)
  else selected.delete(listId)
  return [...selected]
}

export function normalizeCompletionListSelection(listIds, allListIds) {
  if (listIds === null) return null
  const available = new Set(allListIds)
  const selected = [...new Set(listIds)].filter(id => available.has(id))
  return selected.length === available.size ? null : selected
}

function weekStart(date) {
  const start = localDay(date)
  start.setDate(start.getDate() - ((start.getDay() + 6) % 7))
  return start
}

export function completionDateRange(range, now = new Date(), customStart = '', customEnd = '') {
  const today = localDay(now)
  if (range === 'THIS_WEEK') {
    const start = weekStart(today)
    const end = new Date(start)
    end.setDate(end.getDate() + 7)
    return [dayKey(start), dayKey(end)]
  }
  if (range === 'LAST_WEEK') {
    const end = weekStart(today)
    const start = new Date(end)
    start.setDate(start.getDate() - 7)
    return [dayKey(start), dayKey(end)]
  }
  if (range === 'THIS_MONTH') {
    const start = new Date(today.getFullYear(), today.getMonth(), 1)
    const end = new Date(today.getFullYear(), today.getMonth() + 1, 1)
    return [dayKey(start), dayKey(end)]
  }
  if (range === 'CUSTOM') {
    if (!customStart || !customEnd || customEnd < customStart) return null
    const end = new Date(`${customEnd}T00:00:00`)
    end.setDate(end.getDate() + 1)
    return [customStart, dayKey(end)]
  }
  return null
}

export function filterCompletionTasks(tasks, { status, dateRange = 'ALL', customStart = '', customEnd = '', listIds = null }, now = new Date()) {
  const range = completionDateRange(dateRange, now, customStart, customEnd)
  return tasks.filter(task => {
    if (task.deleted || task.status !== status) return false
    if (listIds !== null && !listIds.includes(task.listId)) return false
    if (dateRange === 'CUSTOM' && !range) return false
    if (!range) return true
    const timestamp = status === 'COMPLETED' ? task.completedAt : task.abandonedAt
    if (!timestamp) return false
    const completedDay = localDay(timestamp)
    if (!completedDay) return false
    const key = dayKey(completedDay)
    return key >= range[0] && key < range[1]
  })
}
