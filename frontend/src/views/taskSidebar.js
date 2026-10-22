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

export function insertListDropPlaceholder(items, item, targetId, placement = 'before') {
  const targetIndex = items.findIndex((entry) => entry.publicId === targetId)
  if (targetId && targetIndex < 0) return items

  const insertionIndex = targetId
    ? targetIndex + (placement === 'after' ? 1 : 0)
    : items.length
  const placeholder = {
    ...item,
    publicId: `__drop-preview__:${item.publicId}`,
    dropPlaceholder: true
  }
  return [...items.slice(0, insertionIndex), placeholder, ...items.slice(insertionIndex)]
}

export function reorderListDrop(targetItems, sourceItem, targetId, placement = 'before') {
  const next = targetItems.filter((item) => item.publicId !== sourceItem.publicId)
  const targetIndex = next.findIndex((item) => item.publicId === targetId)
  const insertionIndex = targetIndex < 0
    ? next.length
    : targetIndex + (placement === 'after' ? 1 : 0)
  next.splice(insertionIndex, 0, sourceItem)
  return next.map((entry, sortOrder) => ({ ...entry, sortOrder }))
}
