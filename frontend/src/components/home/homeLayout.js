/**
 * 首页小组件画布的布局口径。
 *
 * 布局只描述「有哪些卡片、什么顺序、是否隐藏」，不关心卡片内容；
 * 同一个账号共用一份，换账号互不影响。顺序/显隐都存在 localStorage，
 * 因此即使后端不可用，画布也保持用户上次的排布。
 */

/** 卡片清单：key 同时是动态组件的标识，span 是 12 栏网格下的默认占宽 */
export const HOME_WIDGETS = [
  { key: 'tasks', title: '任务与习惯', span: 4 },
  { key: 'calendar', title: '日历', span: 4 },
  { key: 'ledger', title: '最近流水', span: 4 },
  { key: 'cashflow', title: '收支趋势', span: 5 },
  { key: 'budget', title: '预算执行', span: 3 },
  { key: 'category', title: '支出分类', span: 4 },
  { key: 'worktime', title: '工时与加班', span: 8 },
  { key: 'ai', title: 'AI 助手', span: 4 }
]

export const HOME_WIDGET_KEYS = HOME_WIDGETS.map(item => item.key)

export function defaultLayout() {
  return HOME_WIDGETS.map(item => ({ key: item.key, hidden: false }))
}

/**
 * 把存档整理成「全部已知卡片各一条」的布局：
 * - 未知 key（旧版本删掉的卡片）直接丢弃；
 * - 存档里缺的卡片补在末尾，避免新增卡片因为旧存档而不显示；
 * - 重复 key 只保留第一次出现的位置。
 */
export function normalizeLayout(saved) {
  const rows = Array.isArray(saved) ? saved : []
  const seen = new Set()
  const layout = []
  for (const row of rows) {
    const key = typeof row === 'string' ? row : row?.key
    if (!HOME_WIDGET_KEYS.includes(key) || seen.has(key)) continue
    seen.add(key)
    layout.push({ key, hidden: Boolean(typeof row === 'object' && row?.hidden) })
  }
  for (const key of HOME_WIDGET_KEYS) if (!seen.has(key)) layout.push({ key, hidden: false })
  return layout
}

/** 把 from 挪到 to 的位置；任一侧不存在时原样返回，避免拖拽越界打乱顺序 */
export function moveWidget(layout, from, to) {
  const rows = normalizeLayout(layout)
  const fromIndex = rows.findIndex(item => item.key === from)
  const toIndex = rows.findIndex(item => item.key === to)
  if (fromIndex < 0 || toIndex < 0 || fromIndex === toIndex) return rows
  const [moved] = rows.splice(fromIndex, 1)
  rows.splice(toIndex, 0, moved)
  return rows
}

export function toggleWidget(layout, key) {
  return normalizeLayout(layout).map(item => item.key === key ? { ...item, hidden: !item.hidden } : item)
}

export function visibleWidgets(layout) {
  return normalizeLayout(layout).filter(item => !item.hidden)
}

export function widgetSpan(key) {
  return HOME_WIDGETS.find(item => item.key === key)?.span || 4
}

export function layoutStorageKey(scope) {
  return `home_widget_layout_v1:${scope || 'local'}`
}

export function readLayout(storageKey, storage = typeof localStorage === 'undefined' ? null : localStorage) {
  try {
    const raw = storage?.getItem(storageKey)
    return raw ? normalizeLayout(JSON.parse(raw)) : defaultLayout()
  } catch {
    return defaultLayout()
  }
}

export function writeLayout(storageKey, layout, storage = typeof localStorage === 'undefined' ? null : localStorage) {
  try {
    storage?.setItem(storageKey, JSON.stringify(normalizeLayout(layout)))
  } catch {
    /* 隐私模式/配额满时忽略：布局降级为本次会话内有效 */
  }
}
