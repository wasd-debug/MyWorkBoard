// 倒数纪念日：纯函数工具（便于 node --test）
// 约定：日期一律用本地时区的 yyyy-MM-dd 字符串，不用 toISOString（东八区早 8 点前会跨天）

export const pad = value => String(value).padStart(2, '0')

export function dateKey(date = new Date()) {
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`
}

export const todayKey = () => dateKey(new Date())

export function parseKey(key) {
  const [year, month, day] = String(key || '').slice(0, 10).split('-').map(Number)
  if (!year || !month || !day) return new Date(Number.NaN)
  return new Date(year, month - 1, day)
}

/** 四种类型；后端 kind 只认 COUNTDOWN / ANNIVERSARY，生日与节日按语义映射过去 */
export const COUNTDOWN_TYPES = [
  { key: 'ANNIVERSARY', label: '纪念日', icon: '🎉', kind: 'ANNIVERSARY' },
  { key: 'COUNTDOWN', label: '倒数日', icon: '⏳', kind: 'COUNTDOWN' },
  { key: 'BIRTHDAY', label: '生日', icon: '🎂', kind: 'ANNIVERSARY' },
  { key: 'FESTIVAL', label: '节日', icon: '🎊', kind: 'COUNTDOWN' }
]

export const TYPE_KEYS = COUNTDOWN_TYPES.map(item => item.key)

export function typeOf(item) {
  const key = String(item?.type || item?.kind || 'COUNTDOWN')
  return COUNTDOWN_TYPES.find(entry => entry.key === key) || COUNTDOWN_TYPES[1]
}

/** 卡片封面样式：没有图片资源，全部用 CSS 渐变 + 图案组合出来 */
export const COVER_STYLES = [
  { key: 'SOLID', label: '纯色' },
  { key: 'GRADIENT', label: '渐变' },
  { key: 'BOKEH', label: '光斑' },
  { key: 'STRIPE', label: '斜纹' },
  { key: 'GRID', label: '网格' },
  { key: 'MIST', label: '云雾' }
]

export const COVER_COLORS = [
  '#4d78db', '#3f8ee0', '#2fb39a', '#63b95e', '#a8c04a', '#e2b13c',
  '#ec8b4a', '#e2685f', '#d75f96', '#a568d4', '#6c7a8f', '#343a46'
]

/** 数字展示单位，点击卡片数字循环切换 */
export const DISPLAY_UNITS = [
  { key: 'DAY', label: '天' },
  { key: 'WEEK', label: '周+天' },
  { key: 'MONTH', label: '月+天' },
  { key: 'HOUR', label: '时分秒' },
  { key: 'MINUTE', label: '分秒' }
]

export const UNIT_KEYS = DISPLAY_UNITS.map(item => item.key)

export const REMIND_OPTIONS = [
  { value: 'NONE', label: '无' },
  { value: '09:00', label: '当天 09:00' },
  { value: '12:00', label: '当天 12:00' },
  { value: '18:00', label: '当天 18:00' },
  { value: '07:00', label: '提前 1 天 07:00' }
]

export const REPEAT_OPTIONS = [
  { value: 'NONE', label: '不重复' },
  { value: 'WEEKLY', label: '每周' },
  { value: 'MONTHLY', label: '每月' },
  { value: 'YEARLY', label: '每年' }
]

export const DISPLAY_OPTIONS = [
  { value: 'SMART', label: '在智能清单中当天显示' },
  { value: 'ALWAYS', label: '始终显示' },
  { value: 'NEVER', label: '不在智能清单中显示' }
]

/** 新建时的默认扩展字段（后端暂无这些列，先落在本地） */
const TYPE_COLORS = {
  ANNIVERSARY: '#4d78db',
  COUNTDOWN: '#2fb39a',
  BIRTHDAY: '#d75f96',
  FESTIVAL: '#ec8b4a'
}

export function defaultExtras(type = 'COUNTDOWN') {
  return {
    type,
    icon: typeOf({ type }).icon,
    cover: 'SOLID',
    color: TYPE_COLORS[type] || '#4d78db',
    archived: false,
    remind: 'NONE',
    repeat: 'NONE',
    countingMode: 'COUNTDOWN',
    display: 'SMART',
    unit: 'DAY'
  }
}

/** 每年重复的条目，把目标日期滚到今年的同一个日子（已过则明年） */
export function resolveTargetDate(item, today = todayKey()) {
  const raw = String(item?.targetDate || today).slice(0, 10)
  const source = parseKey(raw)
  if (Number.isNaN(source.getTime())) return raw
  const yearly = item?.repeatYearly || item?.repeat === 'YEARLY' || typeOf(item).key === 'BIRTHDAY'
  if (!yearly) return raw
  const base = parseKey(today)
  const thisYear = new Date(base.getFullYear(), source.getMonth(), source.getDate())
  if (thisYear < base) return dateKey(new Date(base.getFullYear() + 1, source.getMonth(), source.getDate()))
  return dateKey(thisYear)
}

/** 正数=还有几天，负数=已经过去几天，0=今天 */
export function daysUntil(item, today = todayKey()) {
  return Math.round((parseKey(resolveTargetDate(item, today)) - parseKey(today)) / 86400000)
}

export function dayBadge(days) {
  if (days === 0) return '今天'
  if (days === 1) return '明天'
  if (days === -1) return '昨天'
  return ''
}

/**
 * 数字按单位拆成若干段：天 → [{value:'5'}]；周+天 → [{value:'1',unit:'周'},{value:'3',unit:'天'}]
 * 兼容 backValue：已经过去的日期用绝对值展示，方向交给文案
 */
export function formatCount(item, unit = 'DAY', today = todayKey(), now = new Date()) {
  const days = daysUntil(item, today)
  const abs = Math.abs(days)
  if (unit === 'WEEK') return [{ value: String(Math.floor(abs / 7)), unit: '周' }, { value: String(abs % 7), unit: '天' }]
  if (unit === 'MONTH') return [{ value: String(Math.floor(abs / 30)), unit: '月' }, { value: String(abs % 30), unit: '天' }]
  if (unit === 'HOUR' || unit === 'MINUTE') {
    const target = parseKey(resolveTargetDate(item, today))
    const ms = Math.abs(target.getTime() - now.getTime())
    const totalSeconds = Math.floor(ms / 1000)
    const hours = Math.floor(totalSeconds / 3600)
    const minutes = Math.floor((totalSeconds % 3600) / 60)
    const seconds = totalSeconds % 60
    if (unit === 'HOUR') return [{ value: String(hours), unit: '时' }, { value: pad(minutes), unit: '分' }, { value: pad(seconds), unit: '秒' }]
    return [{ value: String(hours * 60 + minutes), unit: '分' }, { value: pad(seconds), unit: '秒' }]
  }
  return [{ value: String(abs), unit: '' }]
}

export function relativeText(item, today = todayKey()) {
  const days = daysUntil(item, today)
  if (days === 0) return '就是今天'
  return days > 0 ? '还有' : '已经'
}

export function targetText(item, today = todayKey()) {
  const key = resolveTargetDate(item, today)
  const date = parseKey(key)
  if (Number.isNaN(date.getTime())) return ''
  return `${date.getFullYear()}年${date.getMonth() + 1}月${date.getDate()}日`
}

export function cycleUnit(unit) {
  const index = UNIT_KEYS.indexOf(unit)
  return UNIT_KEYS[(index + 1) % UNIT_KEYS.length] || 'DAY'
}

export function sortItems(items, today = todayKey()) {
  return [...(items || [])].sort((a, b) => {
    // 注意：pin 是可选字段，Number(undefined) 会得到 NaN 让整个排序失效，必须先归一成 0/1
    if (Boolean(b.pinned) !== Boolean(a.pinned)) return (b.pinned ? 1 : 0) - (a.pinned ? 1 : 0)
    const gap = Math.abs(daysUntil(a, today)) - Math.abs(daysUntil(b, today))
    if (gap) return gap
    return String(a.title || '').localeCompare(String(b.title || ''), 'zh-Hans-CN')
  })
}

export function matchesTab(item, tab) {
  if (!tab || tab === 'ALL') return true
  return typeOf(item).key === tab
}

export function groupByTab(items) {
  const groups = new Map(TYPE_KEYS.map(key => [key, []]))
  for (const item of items || []) {
    const key = typeOf(item).key
    if (!groups.has(key)) groups.set(key, [])
    groups.get(key).push(item)
  }
  return groups
}

/** 卡片预览用的封面背景（样式 + 颜色 → CSS background） */
export function coverBackground(style, color = '#4d78db') {
  const tint = color || '#4d78db'
  switch (style) {
    case 'GRADIENT':
      return `linear-gradient(140deg, ${tint} 0%, color-mix(in srgb, ${tint} 45%, #ffffff) 100%)`
    case 'BOKEH':
      return `radial-gradient(circle at 22% 26%, color-mix(in srgb, ${tint} 55%, #ffffff) 0 18%, transparent 19%), radial-gradient(circle at 74% 34%, color-mix(in srgb, ${tint} 40%, #ffffff) 0 22%, transparent 23%), radial-gradient(circle at 46% 82%, color-mix(in srgb, ${tint} 35%, #ffffff) 0 26%, transparent 27%), ${tint}`
    case 'STRIPE':
      return `repeating-linear-gradient(135deg, ${tint} 0 10px, color-mix(in srgb, ${tint} 78%, #ffffff) 10px 20px)`
    case 'GRID':
      return `linear-gradient(${tint} 0 0) padding-box, repeating-linear-gradient(0deg, transparent 0 14px, color-mix(in srgb, ${tint} 70%, #ffffff) 14px 15px), repeating-linear-gradient(90deg, transparent 0 14px, color-mix(in srgb, ${tint} 70%, #ffffff) 14px 15px)`
    case 'MIST':
      return `radial-gradient(ellipse at 30% 20%, color-mix(in srgb, ${tint} 30%, #ffffff) 0%, transparent 60%), radial-gradient(ellipse at 80% 80%, color-mix(in srgb, ${tint} 60%, #ffffff) 0%, transparent 55%), ${tint}`
    default:
      return tint
  }
}
