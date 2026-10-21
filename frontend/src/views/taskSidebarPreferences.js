const ARRAY_FIELDS = [
  'hiddenSystemViews', 'pinnedSystemViews', 'conditionalSystemViews', 'hiddenListIds', 'pinnedListIds',
  'hiddenTagIds', 'pinnedTagIds', 'hiddenFilterIds', 'pinnedFilterIds',
]

export function mergeSidebarPreferenceChanges(server, before, current) {
  const merged = { ...server }
  for (const field of ARRAY_FIELDS) {
    const beforeValues = new Set(before[field] || [])
    const currentValues = new Set(current[field] || [])
    const serverValues = new Set(server[field] || [])
    for (const value of new Set([...beforeValues, ...currentValues])) {
      if (beforeValues.has(value) === currentValues.has(value)) continue
      if (currentValues.has(value)) serverValues.add(value)
      else serverValues.delete(value)
    }
    merged[field] = [...serverValues]
  }

  const beforeSections = before.collapsedSections || {}
  const currentSections = current.collapsedSections || {}
  const collapsedSections = { ...(server.collapsedSections || {}) }
  for (const key of new Set([...Object.keys(beforeSections), ...Object.keys(currentSections)])) {
    if (beforeSections[key] !== currentSections[key]) collapsedSections[key] = currentSections[key]
  }
  merged.collapsedSections = collapsedSections
  return merged
}
