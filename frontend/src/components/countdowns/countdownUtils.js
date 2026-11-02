// 倒数纪念日：领域纯函数（供 node --test 直接调用）
// 约定：日期一律用本地时区的 yyyy-MM-dd 字符串，不用 toISOString（东八区早 8 点前会跨天）

import { builtinTargetDate, dateKey, nextLunarOccurrence, parseKey } from './lunar.js'

export { dateKey, parseKey }

const pad = value => String(value).padStart(2, '0')

export const todayKey = (date = new Date()) => dateKey(date)

/** 四种类型；kind 与后端列一一对应 */
export const COUNTDOWN_TYPES = [
  { key: 'ANNIVERSARY', label: '纪念日', icon: '🎉', color: '#e2685f' },
  { key: 'COUNTDOWN', label: '倒数日', icon: '⏳', color: '#4d78db' },
  { key: 'BIRTHDAY', label: '生日', icon: '🎂', color: '#ef7f96' },
  { key: 'FESTIVAL', label: '节日', icon: '🎊', color: '#f2994a' }
]

export const TYPE_KEYS = COUNTDOWN_TYPES.map(item => item.key)

export function typeOf(item) {
  const key = String(item?.kind || item?.type || 'COUNTDOWN')
  return COUNTDOWN_TYPES.find(entry => entry.key === key) || COUNTDOWN_TYPES[1]
}

/** 顶部页签：生日归入纪念日，节日单列（与参考图一致） */
export const TABS = [
  { key: 'ALL', label: '所有' },
  { key: 'ANNIVERSARY', label: '纪念日' },
  { key: 'COUNTDOWN', label: '倒数日' },
  { key: 'FESTIVAL', label: '节日' }
]

export function matchesTab(item, tab) {
  if (!tab || tab === 'ALL') return true
  const key = typeOf(item).key
  if (tab === 'ANNIVERSARY') return key === 'ANNIVERSARY' || key === 'BIRTHDAY'
  return key === tab
}

/** 卡片数字的显示单位，点击卡片按此顺序循环 */
export const DISPLAY_UNITS = [
  { key: 'DAY', label: '天' },
  { key: 'WEEK', label: '周 + 天' },
  { key: 'MONTH', label: '月 + 天' },
  { key: 'SECOND', label: '秒' },
  { key: 'MINUTE', label: '分 + 秒' },
  { key: 'HOUR', label: '时 + 分 + 秒' },
  { key: 'YEAR', label: '年 + 月 + 天' }
]

export const UNIT_KEYS = DISPLAY_UNITS.map(item => item.key)

export function unitLabel(key) {
  return DISPLAY_UNITS.find(item => item.key === key)?.label || '天'
}

export function cycleUnit(unit) {
  const index = UNIT_KEYS.indexOf(unit)
  return UNIT_KEYS[(index + 1) % UNIT_KEYS.length] || 'DAY'
}

/** 时间类单位需要按秒刷新 */
export function isTimeUnit(unit) {
  return unit === 'SECOND' || unit === 'MINUTE' || unit === 'HOUR'
}

/** 卡片封面样式：图片 / 浅色 / 深色 / 彩色 / 渐变 */
export const COVER_PRESETS = [
  { key: 'IMAGE', label: '图片', tone: 'dark' },
  { key: 'LIGHT', label: '浅色', tone: 'light' },
  { key: 'DEEP', label: '深色', tone: 'dark' },
  { key: 'TINT', label: '彩色', tone: 'dark' },
  { key: 'GRADIENT', label: '渐变', tone: 'dark' }
]

export const PRESET_COLORS = [
  '#5b6ee1', '#6f8ff0', '#63b3ed', '#4fbdb4', '#68c98d', '#a3cf5f',
  '#f2c94c', '#f2a35e', '#eb7f6b', '#ef7f96', '#f0a3c8', '#b98de0',
  '#8f9df0', '#5d6b80', '#2f3542'
]

export const REMIND_PRESETS = [
  { value: 0, label: '当天', time: '09:00' },
  { value: 1, label: '提前 1 天', time: '09:00' },
  { value: 2, label: '提前 2 天', time: '09:00' },
  { value: 3, label: '提前 3 天', time: '09:00' },
  { value: 7, label: '提前 1 周', time: '09:00' }
]

export const REPEAT_RULES = [
  { key: 'NONE', label: '无' },
  { key: 'DAILY', label: '每天' },
  { key: 'WEEKLY', label: '每周' },
  { key: 'MONTHLY', label: '每月' },
  { key: 'YEARLY', label: '每年' },
  { key: 'CUSTOM', label: '自定义' }
]

export const DISPLAY_RULES = [
  { key: 'SMART', label: '在智能清单中当天显示' },
  { key: 'D3', label: '提前 3 天显示' },
  { key: 'D7', label: '提前 7 天显示' },
  { key: 'ALWAYS', label: '一直显示' },
  { key: 'NEVER', label: '不显示' }
]

const WEEKDAY_LABELS = ['周日', '周一', '周二', '周三', '周四', '周五', '周六']

export function repeatText(item) {
  const rule = item?.repeatRule || (item?.repeatYearly ? 'YEARLY' : 'NONE')
  const date = parseKey(item?.targetDate)
  if (String(rule).startsWith('CUSTOM:')) return `每隔 ${String(rule).split(':')[1]} 天`
  if (rule === 'WEEKLY') return `每周（${WEEKDAY_LABELS[date.getDay()] || '周一'}）`
  if (rule === 'MONTHLY') return `每月（${date.getDate() || 1}日）`
  if (rule === 'YEARLY') return `每年（${date.getMonth() + 1}月${date.getDate()}日）`
  return REPEAT_RULES.find(entry => entry.key === rule)?.label || '无'
}

export function displayText(item) {
  return DISPLAY_RULES.find(entry => entry.key === (item?.displayRule || 'SMART'))?.label || DISPLAY_RULES[0].label
}

export function reminderList(item) {
  return String(item?.remind || '')
    .split(',')
    .map(value => value.trim())
    .filter(value => value !== '')
    .map(value => Number(value))
    .filter(value => Number.isFinite(value))
    .sort((a, b) => a - b)
}

export function reminderText(item) {
  const list = reminderList(item)
  if (!list.length) return '无'
  return list.map(value => REMIND_PRESETS.find(entry => entry.value === value)?.label || `提前 ${value} 天`).join(', ')
}

/** 每年重复 / 生日 / 农历 / 内置倒数日的实际目标日期 */
export function effectiveTarget(item, today = todayKey()) {
  if (!item) return today
  const base = parseKey(today)
  if (item.builtinKey) {
    const computed = builtinTargetDate(item.builtinKey, base)
    if (computed) return computed
  }
  const rule = item.repeatRule || (item.repeatYearly ? 'YEARLY' : 'NONE')
  const yearly = rule === 'YEARLY' || typeOf(item).key === 'BIRTHDAY'
  if (String(rule).startsWith('CUSTOM:')) {
    const every = Number(String(rule).split(':')[1]) || 1
    const source = parseKey(item.targetDate)
    if (Number.isNaN(source.getTime())) return item.targetDate
    if (source >= base) return dateKey(source)
    const gap = Math.ceil((base - source) / 86400000 / every) * every
    return dateKey(new Date(source.getFullYear(), source.getMonth(), source.getDate() + gap))
  }
  if (item.calendarType === 'LUNAR' && item.lunarDate && yearly) {
    const next = nextLunarOccurrence(item.lunarDate, base)
    if (next) return dateKey(next)
  }
  if (yearly && item.targetDate) {
    const source = parseKey(item.targetDate)
    if (Number.isNaN(source.getTime())) return item.targetDate
    let candidate = new Date(base.getFullYear(), source.getMonth(), source.getDate())
    if (candidate < base) candidate = new Date(base.getFullYear() + 1, source.getMonth(), source.getDate())
    return dateKey(candidate)
  }
  return String(item.targetDate || today).slice(0, 10)
}

/** 正数=还有几天，负数=已经过去几天，0=今天 */
export function daysUntil(item, today = todayKey()) {
  return Math.round((parseKey(effectiveTarget(item, today)) - parseKey(today)) / 86400000)
}

export function directionText(item, today = todayKey()) {
  const days = daysUntil(item, today)
  if (days === 0) return '就是今天'
  return days > 0 ? '还有' : '已经'
}

export function footerText(item, today = todayKey()) {
  const days = daysUntil(item, today)
  const target = effectiveTarget(item, today)
  if (days === 0) return `今天 ${target}`
  return `距离 ${target} ${days > 0 ? '还有' : '已经'}`
}

function calendarGap(from, to) {
  let years = to.getFullYear() - from.getFullYear()
  let months = to.getMonth() - from.getMonth()
  let days = to.getDate() - from.getDate()
  if (days < 0) {
    months -= 1
    days += new Date(to.getFullYear(), to.getMonth(), 0).getDate()
  }
  if (months < 0) {
    years -= 1
    months += 12
  }
  return { years, months, days }
}

/**
 * 把目标日期拆成若干数字段。
 * @returns {{value: string, unit: string}[]}
 */
export function countParts(item, today = todayKey(), now = new Date()) {
  const unit = item?.unit || 'DAY'
  const days = daysUntil(item, today)
  const abs = Math.abs(days)
  const target = parseKey(effectiveTarget(item, today))
  const from = days >= 0 ? parseKey(today) : target
  const to = days >= 0 ? target : parseKey(today)

  if (unit === 'WEEK') {
    const weeks = Math.floor(abs / 7)
    const rest = abs % 7
    return rest ? [{ value: String(weeks), unit: '周' }, { value: String(rest), unit: '天' }] : [{ value: String(weeks), unit: '周' }]
  }
  if (unit === 'MONTH') {
    const gap = calendarGap(from, to)
    const months = gap.years * 12 + gap.months
    return gap.days ? [{ value: String(months), unit: '月' }, { value: String(gap.days), unit: '天' }] : [{ value: String(months), unit: '月' }]
  }
  if (unit === 'YEAR') {
    const gap = calendarGap(from, to)
    const parts = []
    if (gap.years) parts.push({ value: String(gap.years), unit: '年' })
    if (gap.months) parts.push({ value: String(gap.months), unit: '月' })
    parts.push({ value: String(gap.days), unit: '天' })
    return parts
  }
  if (isTimeUnit(unit)) {
    const ms = Math.abs(target.getTime() - now.getTime())
    const totalSeconds = Math.floor(ms / 1000)
    if (unit === 'SECOND') return [{ value: String(totalSeconds), unit: '秒' }]
    const hours = Math.floor(totalSeconds / 3600)
    const minutes = Math.floor((totalSeconds % 3600) / 60)
    const seconds = totalSeconds % 60
    if (unit === 'MINUTE') return [{ value: String(hours * 60 + minutes), unit: '分' }, { value: pad(seconds), unit: '秒' }]
    return [{ value: String(hours), unit: '时' }, { value: pad(minutes), unit: '分' }, { value: pad(seconds), unit: '秒' }]
  }
  return [{ value: String(abs), unit: '' }]
}

export function sortItems(items, today = todayKey()) {
  return [...(items || [])].sort((a, b) => {
    const pinned = (b.pinned ? 1 : 0) - (a.pinned ? 1 : 0)
    if (pinned) return pinned
    const gap = Math.abs(daysUntil(a, today)) - Math.abs(daysUntil(b, today))
    if (gap) return gap
    return String(a.title || '').localeCompare(String(b.title || ''), 'zh-Hans-CN')
  })
}

/** 卡片主题：封面样式 + 主色 → 内联样式与色调 */
export function cardTheme(item) {
  const color = item?.color || '#4d78db'
  const preset = COVER_PRESETS.find(entry => entry.key === (item?.cover || 'IMAGE')) || COVER_PRESETS[0]
  if (preset.key === 'LIGHT') {
    return {
      tone: 'light',
      style: {
        background: `color-mix(in srgb, ${color} 12%, #ffffff)`,
        color: '#2b3240',
        '--num': `color-mix(in srgb, ${color} 88%, #1b2230)`,
        '--sub': 'rgba(43,50,64,.55)'
      }
    }
  }
  if (preset.key === 'DEEP') {
    return {
      tone: 'dark',
      style: {
        background: `color-mix(in srgb, ${color} 32%, #333c4d)`,
        color: '#f7efe0',
        '--num': '#f7efe0',
        '--sub': 'rgba(247,239,224,.66)'
      }
    }
  }
  if (preset.key === 'TINT') {
    return {
      tone: 'dark',
      style: {
        background: color,
        color: '#ffffff',
        '--num': '#ffffff',
        '--sub': 'rgba(255,255,255,.72)'
      }
    }
  }
  if (preset.key === 'GRADIENT') {
    return {
      tone: 'dark',
      style: {
        background: `linear-gradient(150deg, ${color} 0%, color-mix(in srgb, ${color} 45%, #ffffff) 100%)`,
        color: '#ffffff',
        '--num': '#ffffff',
        '--sub': 'rgba(255,255,255,.78)'
      }
    }
  }
  const image = item?.coverImage
  // 没有上传图片时用多层渐变模拟柔光照片，避免出现死板的纯色块
  const art = image
    ? `url("${image}") center/cover no-repeat`
    : `radial-gradient(58% 48% at 22% 16%, rgba(255,255,255,.3) 0%, rgba(255,255,255,0) 64%),` +
      `radial-gradient(44% 40% at 80% 10%, color-mix(in srgb, ${color} 34%, #ffffff) 0%, transparent 68%),` +
      `radial-gradient(72% 62% at 62% 50%, color-mix(in srgb, ${color} 46%, #ffffff) 0%, transparent 74%),` +
      `radial-gradient(92% 78% at 50% 114%, rgba(0,0,0,.52) 0%, transparent 70%),` +
      `linear-gradient(150deg, color-mix(in srgb, ${color} 58%, #39404f) 0%, color-mix(in srgb, ${color} 42%, #141922) 100%)`
  return {
    tone: 'dark',
    style: {
      background: art,
      color: '#ffffff',
      '--num': '#ffffff',
      '--sub': 'rgba(255,255,255,.8)'
    }
  }
}

/** 新建时的默认值 */
export function defaultCountdown(type = 'ANNIVERSARY') {
  const meta = COUNTDOWN_TYPES.find(entry => entry.key === type) || COUNTDOWN_TYPES[0]
  return {
    title: '',
    targetDate: todayKey(),
    kind: meta.key,
    repeatYearly: false,
    pinned: false,
    color: meta.color,
    note: '',
    iconType: 'EMOJI',
    iconValue: meta.icon,
    iconColor: meta.color,
    cover: 'IMAGE',
    coverImage: null,
    unit: 'DAY',
    preciseSeconds: false,
    remind: '',
    repeatRule: type === 'BIRTHDAY' ? 'YEARLY' : 'NONE',
    displayRule: 'SMART',
    archived: false,
    calendarType: 'SOLAR',
    lunarDate: null,
    sortOrder: 0
  }
}
