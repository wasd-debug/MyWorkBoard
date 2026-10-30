/**
 * 自定义分组：后端只存 groupKey 字符串，分组名称列表落在本地。
 * 用分组名本身当 key，改名会影响已归入的习惯，所以加分组只增不改。
 */

const STORAGE_KEY = 'habit_groups_v1'
const MAX_LENGTH = 16

export function loadCustomGroups() {
  try {
    const raw = localStorage.getItem(STORAGE_KEY)
    if (!raw) return []
    const list = JSON.parse(raw)
    if (!Array.isArray(list)) return []
    return list
      .filter(item => item && typeof item.key === 'string' && item.key)
      .map(item => ({ key: item.key, label: String(item.label || item.key) }))
  } catch {
    return []
  }
}

export function saveCustomGroups(list) {
  try {
    localStorage.setItem(STORAGE_KEY, JSON.stringify(list || []))
  } catch {
    /* 隐私模式下写不进去就算了，不影响使用 */
  }
}

/** 由名称生成合法 key：去掉不支持的字符、限长 16、重名加序号 */
export function groupKeyFromLabel(label, existing = []) {
  const base = String(label || '').replace(/[^\p{L}\p{N}_-]/gu, '').slice(0, MAX_LENGTH)
  if (!base) return ''
  let key = base
  let index = 2
  while (existing.some(group => group.key === key)) {
    const suffix = String(index)
    key = `${base.slice(0, MAX_LENGTH - suffix.length)}${suffix}`
    index += 1
  }
  return key
}
