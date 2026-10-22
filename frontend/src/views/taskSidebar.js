export function moveItem(items, itemId, targetId, placement = 'before') {
  const from = items.findIndex((item) => item.publicId === itemId)
  const to = items.findIndex((item) => item.publicId === targetId)
  if (from < 0 || to < 0 || from === to) return items

  const next = [...items]
  const [item] = next.splice(from, 1)
  const adjustedTarget = from < to ? to - 1 : to
  next.splice(adjustedTarget + (placement === 'after' ? 1 : 0), 0, item)
  return next.map((entry, sortOrder) => ({ ...entry, sortOrder }))
}

export function reorderListDrop(targetItems, sourceItem, targetId, placement = 'before') {
  const next = targetItems.filter((item) => item.publicId !== sourceItem.publicId)
  const targetIndex = next.findIndex((item) => item.publicId === targetId)
  const insertionIndex = targetIndex < 0
    ? next.length
    : targetIndex + (placement === 'after' ? 1 : 0)
  next.splice(insertionIndex, 0, sourceItem)

  const previous = next[insertionIndex - 1]
  const following = next[insertionIndex + 1]
  const previousOrder = previous ? Number(previous.sortOrder) : null
  const followingOrder = following ? Number(following.sortOrder) : null
  let sortOrder

  if (previousOrder === null && followingOrder === null) sortOrder = 1024
  else if (previousOrder === null) sortOrder = followingOrder - 1024
  else if (followingOrder === null) sortOrder = previousOrder + 1024
  else if (followingOrder - previousOrder > 1) sortOrder = Math.floor((previousOrder + followingOrder) / 2)
  else return next.map((entry, index) => ({ ...entry, sortOrder: (index + 1) * 1024 }))

  return next.map((entry, index) => index === insertionIndex ? { ...entry, sortOrder } : entry)
}

export function getListDropPlacement(clientY, rect, mergeThreshold = 0.35) {
  if (!rect || rect.height <= 0) return 'before'
  const offset = (clientY - rect.top) / rect.height
  if (offset < mergeThreshold) return 'before'
  if (offset > 1 - mergeThreshold) return 'after'
  return 'merge'
}
