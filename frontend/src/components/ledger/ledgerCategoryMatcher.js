const normalize = value => String(value || '').trim().toLocaleLowerCase()

export function ledgerCategoryParent(categories, category) {
  if (!category?.parentId) return null
  return (categories || []).find(item => String(item.id) === String(category.parentId)) || null
}

export function ledgerCategoryPath(categories, category) {
  const parent = ledgerCategoryParent(categories, category)
  return parent ? `${parent.name} / ${category.name}` : String(category?.name || '')
}

export function filterLedgerPrimaryCategories(categories, kind, query = '') {
  const keyword = normalize(query)
  return (categories || []).filter(item => !item.parentId && item.kind === kind && (!keyword || normalize(item.name).includes(keyword)))
}

export function filterLedgerSecondaryCategories(categories, kind, query = '', parentId = '') {
  const keyword = normalize(query)
  return (categories || []).filter(item => {
    if (!item.parentId || (kind && item.kind !== kind)) return false
    if (parentId && String(item.parentId) !== String(parentId)) return false
    return !keyword || normalize(item.name).includes(keyword) || normalize(ledgerCategoryPath(categories, item)).includes(keyword)
  })
}

export function exactLedgerSecondaryCategory(categories, kind, query = '', parentId = '') {
  const keyword = normalize(query)
  if (!keyword) return null
  const matches = filterLedgerSecondaryCategories(categories, kind, '', parentId).filter(item =>
    normalize(item.name) === keyword || normalize(ledgerCategoryPath(categories, item)) === keyword)
  return matches.length === 1 ? matches[0] : null
}
