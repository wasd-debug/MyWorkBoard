export const DEFAULT_TASK_COLOR = '#4d96ff'
export const THEME_COLOR = 'var(--accent)'

export const PRIORITY_COLORS = { HIGH: '#e34d59', MEDIUM: '#e69b2d', LOW: '#4d78db', NONE: '#8b92a3' }

export const TASK_COLOR_MODES = [
  { value: 'list', label: '按清单颜色', hint: '任务的颜色将按照清单的颜色显示。（为确保文字可读，会做弱化处理）' },
  { value: 'tag', label: '按标签颜色', hint: '任务的颜色将按照第一个标签的颜色显示。' },
  { value: 'priority', label: '按优先级颜色', hint: '任务的颜色将按照优先级显示：高、中、低、无。' }
]

export const HABIT_COLOR_MODES = [
  { value: 'theme', label: '跟随主题色', hint: '所有习惯使用当前主题色。' },
  { value: 'fixed', label: '按固定颜色', hint: '所有习惯使用同一个自选颜色。' },
  { value: 'habit', label: '按习惯颜色', hint: '按每个习惯自己设置的颜色显示。' }
]

export const COUNTDOWN_COLOR_MODES = [
  { value: 'fixed', label: '按固定颜色', hint: '所有纪念日使用同一个自选颜色。（为确保文字可读，会做弱化处理）' },
  { value: 'countdown', label: '按纪念日颜色', hint: '按每个纪念日自己设置的颜色显示。' }
]

export const DEFAULT_COLOR_SETTINGS = {
  taskColorMode: 'list',
  habitColorMode: 'theme',
  habitFixedColor: '#f0b429',
  countdownColorMode: 'fixed',
  countdownFixedColor: '#e8994a'
}

export const COLOR_GROUPS = [
  { key: 'recommended', label: '推荐', colors: ['', '#ff6b6b', '#ff9f43', '#ffd93d', '#6bcb77', '#4d96ff', '#9b7ede'] },
  { key: 'macaron', label: '马卡龙色系', colors: ['#f4a6a6', '#f6c1a8', '#f7dca4', '#c4e7c9', '#a8d8e8', '#a9bcf0', '#d4b3e8', '#f2a8c8'] },
  { key: 'morandi', label: '莫兰迪色系', colors: ['#b08b7d', '#c9ad8f', '#d8cba0', '#b9c9b6', '#a8c0c0', '#a9b4c9', '#b3aec9', '#c9a8b3'] },
  { key: 'rococo', label: '洛可可色系', colors: ['#d9a3a3', '#e0b48f', '#e6cba0', '#c8d2a8', '#a8c8b8', '#a9bfd8', '#b3b3cf', '#c9b3c9'] },
  { key: 'classic', label: '经典色系', colors: ['#e94b4b', '#f08c3a', '#f5c33b', '#5fbf6f', '#3d8bfd', '#8b5cf6', '#d84f9a'] },
  { key: 'memphis', label: '孟菲斯色系', colors: ['#ff2d55', '#ff8a3d', '#ffd23f', '#2ee6a8', '#2ec5ff', '#7a5cff', '#ff5ce1'] }
]

const COLOR_PATTERN = /^(#[0-9a-f]{3,8}|rgba?\(|hsla?\(|var\(--)/i

export function normalizeColor(value, fallback = DEFAULT_TASK_COLOR) {
  const text = typeof value === 'string' ? value.trim() : ''
  return COLOR_PATTERN.test(text) ? text : fallback
}

export function mergedColorSettings(value) {
  return { ...DEFAULT_COLOR_SETTINGS, ...(value || {}) }
}

export function resolveTaskColor(task, { mode = 'list', lists = [], tags = [] } = {}) {
  if (!task) return DEFAULT_TASK_COLOR
  if (mode === 'priority') return PRIORITY_COLORS[task.priority] || PRIORITY_COLORS.NONE
  if (mode === 'tag') {
    // 逐个标签取第一个合法颜色，避免首个标签没设色就把整条任务压成默认色
    const color = (task.tagIds || [])
      .map(id => tags.find(item => item.publicId === id))
      .filter(Boolean)
      .map(item => String(item.color || '').trim())
      .find(value => COLOR_PATTERN.test(value))
    return color || DEFAULT_TASK_COLOR
  }
  const list = lists.find(item => item.publicId === task.listId)
  return normalizeColor(list?.color, DEFAULT_TASK_COLOR)
}

export function resolveHabitColor(habit, settings = {}) {
  const merged = mergedColorSettings(settings)
  if (merged.habitColorMode === 'fixed') return normalizeColor(merged.habitFixedColor, '#f0b429')
  if (merged.habitColorMode === 'habit') return normalizeColor(habit?.color, '#f0b429')
  return THEME_COLOR
}

export function resolveCountdownColor(countdown, settings = {}) {
  const merged = mergedColorSettings(settings)
  if (merged.countdownColorMode === 'countdown') return normalizeColor(countdown?.color, '#e8994a')
  return normalizeColor(merged.countdownFixedColor, '#e8994a')
}

export function tintStyle(color, strength = 14) {
  return { background: `color-mix(in srgb, ${color} ${strength}%, transparent)`, borderColor: `color-mix(in srgb, ${color} ${Math.min(34, strength + 4)}%, transparent)` }
}
