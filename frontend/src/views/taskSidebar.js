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
