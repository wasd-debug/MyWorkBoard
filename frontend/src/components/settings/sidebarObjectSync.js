// 智能清单显示选项：设置页与任务页侧栏右键菜单共用同一份数据，保证两边看到的是同一个状态。
// 1) 系统视图（所有 / 今天 / … / 垃圾桶）存在 `task_sidebar_objects:<scope>`，并通过
//    `/api/v1/tasks/sidebar-preferences` 跨设备同步（隐藏 = hiddenSystem，有内容时显示 = conditionalSystem）；
// 2) 标签 / 过滤器两个分组存在 `task_sidebar_section_display:<scope>`，任务页本来就只存本地。
// 注意：这里只写「用户这一次改动」，写服务端前先拉最新再叠加，避免把别的设备置顶/隐藏冲掉。
import {
  apiGetTaskSidebarPreferences,
  apiUpdateTaskSidebarPreferences
} from '../../../packages/api-client/src/index.js'

const NO_CONDITIONAL = ['summary', 'completed', 'trash']

export function emptyPrefs() {
  return {
    pinnedSystem: [],
    hiddenSystem: [],
    conditionalSystem: [],
    pinnedLists: [],
    hiddenLists: [],
    pinnedTags: [],
    hiddenTags: [],
    pinnedFilters: [],
    hiddenFilters: [],
    displayModes: {}
  }
}

export function prefsKey(scope) {
  return `task_sidebar_objects:${scope || 'anonymous'}`
}

export function sectionsKey(scope) {
  return `task_sidebar_section_display:${scope || 'anonymous'}`
}

export function readLocalPrefs(scope) {
  const empty = emptyPrefs()
  try {
    const stored = JSON.parse(localStorage.getItem(prefsKey(scope)) || '{}')
    return { ...empty, ...stored, displayModes: stored.displayModes || {} }
  } catch {
    return empty
  }
}

export function writeLocalPrefs(scope, prefs) {
  try {
    localStorage.setItem(prefsKey(scope), JSON.stringify(prefs))
  } catch {}
}

export function readLocalSections(scope) {
  try {
    return { filters: 'SHOW', tags: 'SHOW', ...JSON.parse(localStorage.getItem(sectionsKey(scope)) || '{}') }
  } catch {
    return { filters: 'SHOW', tags: 'SHOW' }
  }
}

export function writeLocalSections(scope, sections) {
  try {
    localStorage.setItem(sectionsKey(scope), JSON.stringify(sections))
  } catch {}
}

/** 与任务页 `displayOptionsFor()` 保持一致的可用选项 */
export function systemOptions(key) {
  const options = [{ value: 'SHOW', label: '显示' }]
  if (!NO_CONDITIONAL.includes(key)) options.push({ value: 'WHEN_HAS_CONTENT', label: '有内容时显示' })
  options.push({ value: 'HIDE', label: '隐藏' })
  return options
}

/** 与任务页 `sectionDisplayOptions()` 保持一致的可用选项 */
export function sectionOptions(section) {
  return section === 'tags'
    ? [
        { value: 'SHOW', label: '显示' },
        { value: 'WHEN_HAS_CONTENT', label: '有内容时显示' },
        { value: 'HIDE', label: '隐藏' }
      ]
    : [
        { value: 'SHOW', label: '显示' },
        { value: 'HIDE', label: '隐藏' }
      ]
}

export function systemMode(prefs, key) {
  if (prefs.conditionalSystem?.includes(key)) return 'WHEN_HAS_CONTENT'
  if (prefs.hiddenSystem?.includes(key)) return 'HIDE'
  return 'SHOW'
}

export function setSystemMode(prefs, key, mode) {
  const value = systemOptions(key).some(option => option.value === mode) ? mode : 'SHOW'
  prefs.conditionalSystem = value === 'WHEN_HAS_CONTENT'
    ? [...new Set([...(prefs.conditionalSystem || []), key])]
    : (prefs.conditionalSystem || []).filter(item => item !== key)
  prefs.hiddenSystem = value === 'HIDE'
    ? [...new Set([...(prefs.hiddenSystem || []), key])]
    : (prefs.hiddenSystem || []).filter(item => item !== key)
  // 隐藏 / 有内容时显示与置顶互斥，与任务页行为一致
  if (value !== 'SHOW') prefs.pinnedSystem = (prefs.pinnedSystem || []).filter(item => item !== key)
}

export function sectionMode(sections, section) {
  const mode = sections?.[section]
  return ['SHOW', 'WHEN_HAS_CONTENT', 'HIDE'].includes(mode) ? mode : 'SHOW'
}

export function setSectionMode(sections, section, mode) {
  sections[section] = sectionOptions(section).some(option => option.value === mode) ? mode : 'SHOW'
}

export function prefsPayload(prefs, collapsedSections = {}) {
  return {
    hiddenSystemViews: [...(prefs.hiddenSystem || [])],
    pinnedSystemViews: [...(prefs.pinnedSystem || [])],
    conditionalSystemViews: [...(prefs.conditionalSystem || [])],
    hiddenListIds: [...(prefs.hiddenLists || [])],
    pinnedListIds: [...(prefs.pinnedLists || [])],
    hiddenTagIds: [...(prefs.hiddenTags || [])],
    pinnedTagIds: [...(prefs.pinnedTags || [])],
    hiddenFilterIds: [...(prefs.hiddenFilters || [])],
    pinnedFilterIds: [...(prefs.pinnedFilters || [])],
    collapsedSections
  }
}

export function applyServerPrefs(prefs, server) {
  if (!server) return prefs
  Object.assign(prefs, {
    pinnedSystem: [...(server.pinnedSystemViews || [])],
    hiddenSystem: [...(server.hiddenSystemViews || [])],
    conditionalSystem: [...(server.conditionalSystemViews || [])],
    pinnedLists: [...(server.pinnedListIds || [])],
    hiddenLists: [...(server.hiddenListIds || [])],
    pinnedTags: [...(server.pinnedTagIds || [])],
    hiddenTags: [...(server.hiddenTagIds || [])],
    pinnedFilters: [...(server.pinnedFilterIds || [])],
    hiddenFilters: [...(server.hiddenFilterIds || [])],
    displayModes: {}
  })
  return prefs
}

/** 拉一次服务端状态，失败返回 null（离线时继续用本地缓存） */
export async function fetchServerPrefs() {
  try {
    return await apiGetTaskSidebarPreferences()
  } catch {
    return null
  }
}

/**
 * 把 changes（{ 系统视图 key: 显示模式 }）写到服务端。
 * 先拉最新快照，只在快照上叠加本次改动，避免覆盖其它设备上的置顶/隐藏。
 */
export async function pushSystemChanges(changes) {
  const latest = await apiGetTaskSidebarPreferences()
  const merged = applyServerPrefs(emptyPrefs(), latest)
  for (const [key, mode] of Object.entries(changes)) setSystemMode(merged, key, mode)
  const saved = await apiUpdateTaskSidebarPreferences(
    prefsPayload(merged, latest?.collapsedSections || {}),
    Number(latest?.revision || 0)
  )
  return saved
}
