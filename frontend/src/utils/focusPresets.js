const KEY = 'task_focus_presets_v1'

/** 常用专注存在本机（按账号隔离），跟其他本地视图偏好一致 */
export function loadFocusPresets(scope) {
  if (typeof localStorage === 'undefined') return []
  try {
    const raw = JSON.parse(localStorage.getItem(`${KEY}:${scope || 'anonymous'}`) || '[]')
    return Array.isArray(raw) ? raw.filter(item => item && item.name) : []
  } catch {
    return []
  }
}

export function saveFocusPresets(scope, list) {
  if (typeof localStorage === 'undefined') return
  localStorage.setItem(`${KEY}:${scope || 'anonymous'}`, JSON.stringify(list || []))
}

export function createFocusPreset({ name, icon = '🍅', mode = 'POMODORO', minutes = 120 }) {
  return {
    id: `fp-${Date.now()}-${Math.random().toString(16).slice(2, 6)}`,
    name: String(name || '').trim() || '专注',
    icon: icon || '🍅',
    mode: mode === 'STOPWATCH' ? 'STOPWATCH' : 'POMODORO',
    minutes: Math.min(180, Math.max(1, Number(minutes) || 25))
  }
}
