export function moveItem(items, itemId, targetId) {
  const from = items.findIndex((item) => item.publicId === itemId)
  const to = items.findIndex((item) => item.publicId === targetId)
  if (from < 0 || to < 0 || from === to) return items

  const next = [...items]
  const [item] = next.splice(from, 1)
  next.splice(to, 0, item)
  return next.map((entry, sortOrder) => ({ ...entry, sortOrder }))
}
