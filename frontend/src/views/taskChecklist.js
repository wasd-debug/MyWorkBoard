export function moveChecklistItem(items, from, to) {
  if (!Array.isArray(items) || from < 0 || from >= items.length || to < 0 || to >= items.length || from === to) {
    return items
  }
  const result = [...items]
  const [item] = result.splice(from, 1)
  result.splice(to, 0, item)
  return result
}
