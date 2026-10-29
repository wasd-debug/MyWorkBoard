import { defaultExtras, typeOf } from './countdownUtils.js'

// 后端 countdown 表当前只有 title/target_date/kind/repeat_yearly/pinned/color/note，
// 卡片封面、图标、归档、提醒、重复、计时方式、展示单位这些还没有落库，
// 先按 publicId 存本地，接口补齐后再迁到 sync payload。
const STORAGE_KEY = 'countdown_extras_v1'

export function readAll() {
  if (typeof localStorage === 'undefined') return {}
  try {
    const parsed = JSON.parse(localStorage.getItem(STORAGE_KEY) || '{}')
    return parsed && typeof parsed === 'object' ? parsed : {}
  } catch {
    return {}
  }
}

export function writeAll(map) {
  if (typeof localStorage === 'undefined') return
  try {
    localStorage.setItem(STORAGE_KEY, JSON.stringify(map))
  } catch {
    // 本地存储写满或被禁用时忽略，不影响页面操作
  }
}

export function extrasFor(item, map = readAll()) {
  const base = defaultExtras(typeOf(item).key)
  return { ...base, ...(map[item?.publicId] || {}) }
}

export function mergeExtras(items, map = readAll()) {
  return (items || []).map(item => {
    const extras = extrasFor(item, map)
    return { ...item, ...extras, type: extras.type || typeOf(item).key }
  })
}

export function saveExtras(publicId, patch) {
  if (!publicId) return
  const map = readAll()
  map[publicId] = { ...(map[publicId] || {}), ...patch }
  writeAll(map)
}

export function dropExtras(publicId) {
  const map = readAll()
  delete map[publicId]
  writeAll(map)
}
