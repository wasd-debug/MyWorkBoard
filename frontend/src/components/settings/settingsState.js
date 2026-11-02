// 设置页：分区定义 + 默认值 + 本地持久化（纯逻辑部分便于单测）

export const PANES = [
  { key: 'modules', label: '功能模块' },
  { key: 'smartlist', label: '智能清单' },
  { key: 'notify', label: '提醒与通知' },
  { key: 'datetime', label: '日期与时间' },
  { key: 'appearance', label: '外观' },
  { key: 'advanced', label: '更多设置' },
  { key: 'import', label: '关联与导入' },
  { key: 'collab', label: '共享协作' },
  { key: 'shortcut', label: '快捷键' }
]

export const PANE_KEYS = PANES.map(pane => pane.key)

export const SHOW_OPTIONS = [
  { value: 'SHOW', label: '显示' },
  { value: 'AUTO', label: '有内容时显示' },
  { value: 'HIDE', label: '隐藏' }
]

export const SMART_LISTS = [
  { group: 'main', items: [
    { key: 'smartAll', label: '所有', icon: 'inbox', value: 'SHOW' },
    { key: 'smartToday', label: '今天', icon: 'calendar', value: 'SHOW' },
    { key: 'smartTomorrow', label: '明天', icon: 'sunrise', value: 'AUTO' },
    { key: 'smartNext7', label: '最近 7 天', icon: 'calendarDays', value: 'SHOW' },
    { key: 'smartAssigned', label: '指派给我', icon: 'user', value: 'AUTO' },
    { key: 'smartInbox', label: '收集箱', icon: 'tray', value: 'SHOW' },
    { key: 'smartSummary', label: '摘要', icon: 'summary', value: 'SHOW' }
  ] },
  { group: 'org', items: [
    { key: 'navTags', label: '标签', icon: 'tag', value: 'SHOW' },
    { key: 'navFilters', label: '过滤器', icon: 'filter', value: 'SHOW' }
  ] },
  { group: 'status', items: [
    { key: 'navCompleted', label: '已完成', icon: 'checkCircle', value: 'SHOW' },
    { key: 'navAbandoned', label: '已放弃', icon: 'xCircle', value: 'AUTO' },
    { key: 'navTrash', label: '垃圾桶', icon: 'trash', value: 'SHOW' }
  ] }
]

export const COLOR_THEMES = [
  { key: 'DEFAULT', label: '默认', color: '#f5f3ef' },
  { key: 'AZURE', label: '晴蓝', color: '#4d78db' },
  { key: 'TURQUOISE', label: '松石', color: '#2fb39a' },
  { key: 'TEAL', label: '秘青', color: '#3d8f96' },
  { key: 'FERN', label: '蕨蕤', color: '#a9c48c' },
  { key: 'APRICOT', label: '杏黄', color: '#f0b661' },
  { key: 'PEACH', label: '桃夭', color: '#ef9aa0' },
  { key: 'TWILIGHT', label: '暮山紫', color: '#b3aee0' },
  { key: 'SANDAL', label: '沉香', color: '#b79b84' },
  { key: 'NAVY', label: '藏蓝', color: '#313f60' },
  { key: 'GRAY', label: '灰色', color: '#6b7280' },
  { key: 'NIGHT', label: '暗夜', color: '#22262f' }
]

export const SEASON_THEMES = [
  { key: 'SPRING', label: '春季', color: '#cfe3b4', art: 'linear-gradient(160deg,#dff0c0 0%,#c7e5a4 48%,#9ed08a 100%)' },
  { key: 'SUMMER', label: '夏季', color: '#a9d8ea', art: 'linear-gradient(160deg,#d9f2fb 0%,#a9dcf0 46%,#63b9dd 100%)' },
  { key: 'AUTUMN', label: '秋季', color: '#f3c3a5', art: 'linear-gradient(160deg,#ffe6cd 0%,#fbc9a4 46%,#ef9f74 100%)' },
  { key: 'WINTER', label: '冬季', color: '#bcd0e6', art: 'linear-gradient(160deg,#eef1fb 0%,#d6d9f3 46%,#bcc4ea 100%)' }
]

export const CITY_THEMES = [
  { key: 'BEIJING', label: '北京', color: '#7fb2e5', art: 'linear-gradient(160deg,#cfe6fb 0%,#8fbde8 52%,#4f7fc4 100%)' },
  { key: 'GUANGZHOU', label: '广州', color: '#5fc9be', art: 'linear-gradient(160deg,#cdeeee 0%,#7fd0c6 50%,#319c9c 100%)' },
  { key: 'HANGZHOU', label: '杭州', color: '#7ec8a6', art: 'linear-gradient(160deg,#d8f0dd 0%,#96d6b0 50%,#4e9c7f 100%)' },
  { key: 'LONDON', label: '伦敦', color: '#b3a4e0', art: 'linear-gradient(160deg,#dcd6f6 0%,#b2a4e2 52%,#7d6ec0 100%)' },
  { key: 'MOSCOW', label: '莫斯科', color: '#6fa8d8', art: 'linear-gradient(160deg,#cfe2f6 0%,#7fb0dd 48%,#41618f 100%)' },
  { key: 'NEWYORK', label: '纽约', color: '#f2b06a', art: 'linear-gradient(160deg,#ffe2b8 0%,#f5b978 48%,#c9763f 100%)' },
  { key: 'SANFRANCISCO', label: '旧金山', color: '#ef8f70', art: 'linear-gradient(160deg,#ffd9c7 0%,#f6a382 48%,#d3654a 100%)' },
  { key: 'SEOUL', label: '首尔', color: '#6fc7bd', art: 'linear-gradient(160deg,#d3f0ea 0%,#8fd3c8 50%,#3c9c95 100%)' },
  { key: 'SHANGHAI', label: '上海', color: '#7f8fe0', art: 'linear-gradient(160deg,#c9cff8 0%,#8b93e6 50%,#4a4f9f 100%)' },
  { key: 'SHENZHEN', label: '深圳', color: '#63c9b6', art: 'linear-gradient(160deg,#c8f0e2 0%,#77d3bd 50%,#2c9b86 100%)' },
  { key: 'SYDNEY', label: '悉尼', color: '#5d90c8', art: 'linear-gradient(160deg,#bcd6f0 0%,#6d9fd4 48%,#2f4f80 100%)' },
  { key: 'TOKYO', label: '东京', color: '#f0a8c4', art: 'linear-gradient(160deg,#fbd9e6 0%,#f3aec8 50%,#cf6f95 100%)' }
]

export const PHOTO_THEMES = [
  { key: 'TOWER', label: '高楼大厦', color: '#9aa4b2', art: 'linear-gradient(155deg,#e3e7ee 0%,#9aa6b6 55%,#4d5561 100%)' },
  { key: 'OCEAN', label: '浩若烟海', color: '#4a6378', art: 'linear-gradient(155deg,#7d94a8 0%,#3c5064 55%,#161f28 100%)' },
  { key: 'DESERT', label: '无垠广漠', color: '#d7a97c', art: 'linear-gradient(155deg,#f2dcc4 0%,#dcb089 55%,#ab7a52 100%)' },
  { key: 'LEAVES', label: '绿叶成荫', color: '#1f4a37', art: 'linear-gradient(155deg,#4c7a5c 0%,#21503c 55%,#0d2419 100%)' },
  { key: 'FREEDOM', label: '自由', color: '#5c8378', art: 'linear-gradient(155deg,#a8c6bd 0%,#5f8d80 55%,#2b4a43 100%)' },
  { key: 'DAWN', label: '拂晓', color: '#1d2733', art: 'linear-gradient(155deg,#3d4f63 0%,#1b2733 55%,#080c12 100%)' },
  { key: 'SPRINGBREEZE', label: '忽尔春风', color: '#f2e6ea', art: 'linear-gradient(155deg,#ffffff 0%,#f6e9ee 55%,#e0cfd6 100%)' },
  { key: 'FROST', label: '玄序有时', color: '#d9e4ee', art: 'linear-gradient(155deg,#ffffff 0%,#e2ecf5 55%,#bfd0e0 100%)' },
  { key: 'FIELD', label: '山野千里', color: '#a8bf87', art: 'linear-gradient(155deg,#e6edd6 0%,#c3d3a4 55%,#7d9260 100%)' },
  { key: 'RIDGE', label: '静默如谜', color: '#7d8fa0', art: 'linear-gradient(155deg,#cfd9e2 0%,#8f9fb0 55%,#4c5a68 100%)' }
]

export const SHORTCUT_GROUPS = [
  { title: '通用', items: [
    { label: '同步', keys: ['⌘', 'S'] },
    { label: '取消', keys: ['Esc'] },
    { label: '撤销', keys: ['⌘', 'Z'] },
    { label: '重做', keys: ['⇧', '⌘', 'Z'] },
    { label: '打印', keys: ['⌘', 'P'], recordable: true }
  ] },
  { title: '任务', items: [
    { label: '添加任务', keys: ['⌘', 'N'], recordable: true },
    { label: '向下添加任务', keys: ['↩'] },
    { label: '添加子任务', keys: ['⇧', '↩'] },
    { label: '展开/折叠所有分组', recordable: true },
    { label: '展开/折叠所有子任务', recordable: true }
  ] },
  { title: '编辑任务', desc: '光标位于任务时这些快捷键才会生效。', items: [
    { label: '完成任务', keys: ['⇧', '⌘', 'M'], recordable: true },
    { label: '置顶任务', recordable: true },
    { label: '删除任务', keys: ['⌘', '⌫'] },
    { label: '设置日期', keys: ['⌘', 'D'], recordable: true },
    { label: '清除日期', keys: ['⌘', '0'], recordable: true },
    { label: '设为今天', keys: ['⌘', '1'], recordable: true },
    { label: '设为明天', keys: ['⌘', '2'], recordable: true },
    { label: '设为下周', keys: ['⌘', '3'], recordable: true },
    { label: '设置为“无优先级”', keys: ['^', '0'], recordable: true },
    { label: '设置为“低优先级”', keys: ['^', '1'], recordable: true },
    { label: '设置为“中优先级”', keys: ['^', '2'], recordable: true },
    { label: '设置为“高优先级”', keys: ['^', '3'], recordable: true }
  ] },
  { title: '切换视图', desc: '在清单、日历中可方便的切换视图。', items: [
    { label: '列表视图', sequences: [['V', 'L']] },
    { label: '看板视图', sequences: [['V', 'K']] },
    { label: '时间线视图', sequences: [['V', 'T']] },
    { label: '日视图', keys: ['D / 1'] },
    { label: '周视图', keys: ['W / 2'] },
    { label: '月视图', keys: ['M / 3'] },
    { label: '回到今天', keys: ['T'] }
  ] }
]

export const MODULES = [
  { key: 'moduleHabit', label: '习惯打卡', desc: '养成习惯，让自律成为日常。', link: '/tasks/habits', linkLabel: '打卡设置' },
  { key: 'moduleFocus', label: '番茄专注', desc: '使用番茄计时，保持专注。', link: '/tasks/focus', linkLabel: '专注设置' },
  { key: 'moduleCountdown', label: '倒数纪念日', desc: '记录每一个重要的日子。', link: '/tasks/countdowns', linkLabel: '纪念日设置' },
  { key: 'moduleAi', label: 'AI', desc: '用 AI 轻松管理任务。', link: '/', linkLabel: 'AI 设置' }
]

export const DEFAULT_SETTINGS = {
  // 功能模块
  moduleHabit: true,
  moduleFocus: true,
  moduleCountdown: true,
  moduleAi: true,
  // 智能清单
  smartAll: 'SHOW',
  smartToday: 'SHOW',
  smartTomorrow: 'AUTO',
  smartNext7: 'SHOW',
  smartAssigned: 'AUTO',
  smartInbox: 'SHOW',
  smartSummary: 'SHOW',
  navTags: 'SHOW',
  navFilters: 'SHOW',
  navCompleted: 'SHOW',
  navAbandoned: 'AUTO',
  navTrash: 'SHOW',
  // 提醒与通知
  remindMethod: 'IN_APP',
  notifyDetail: 'UNLOCK',
  persistentRemind: false,
  remindSound: 'DEFAULT',
  completeSound: 'WATER',
  remindVolume: 70,
  // 日期与时间
  timeFormat: 'SYSTEM',
  weekStartDay: 'MONDAY',
  showLunar: true,
  showWeekNumber: true,
  showHoliday: true,
  timezonePick: true,
  // 外观
  appearanceTab: 'THEME',
  interfaceStyle: 'CARD',
  followSystemDark: false,
  colorTheme: 'DEFAULT',
  seasonTheme: 'NONE',
  cityTheme: 'NONE',
  photoTheme: 'NONE',
  customTheme: 'NONE',
  shortcutOverrides: {},
  fontSize: 'NORMAL',
  listColor: 'SHOW',
  sidebarCount: 'ALL',
  completedStyle: 'STANDARD',
  // 更多设置
  defaultDate: 'TODAY',
  defaultDateMode: 'DATE',
  defaultDuration: 'PT1H',
  defaultRemindTimed: 'M5,M30',
  defaultRemindAllDay: 'NONE',
  defaultPriority: 'HIGH',
  defaultTag: 'NONE',
  defaultList: 'INBOX',
  defaultAppend: 'BOTTOM',
  overduePosition: 'TOP',
  miniCalendar: true,
  // 共享协作
  autoAcceptShare: true,
  shareNotifyComplete: true,
  shareNotifyAdd: true,
  shareNotifyDelete: true,
  // 已读过的版本提示
  seenIntro: false
}

export const SETTINGS_STORAGE_KEY = 'app_settings_v1'

/** 合并：只认默认值里有的 key，未存过的用默认值，避免旧版本脏数据影响 */
export function mergeSettings(stored, defaults = DEFAULT_SETTINGS) {
  const next = { ...defaults }
  if (!stored || typeof stored !== 'object') return next
  for (const key of Object.keys(defaults)) {
    if (stored[key] !== undefined && stored[key] !== null) next[key] = stored[key]
  }
  return next
}

export function resetSettings(keys, current, defaults = DEFAULT_SETTINGS) {
  const next = { ...current }
  for (const key of keys) {
    if (key in defaults) next[key] = defaults[key]
  }
  return next
}

export function readSettings() {
  if (typeof localStorage === 'undefined') return { ...DEFAULT_SETTINGS }
  try {
    return mergeSettings(JSON.parse(localStorage.getItem(SETTINGS_STORAGE_KEY) || '{}'))
  } catch {
    return { ...DEFAULT_SETTINGS }
  }
}

export function writeSettings(settings) {
  if (typeof localStorage === 'undefined') return
  try {
    localStorage.setItem(SETTINGS_STORAGE_KEY, JSON.stringify(settings))
  } catch {
    // 存储不可用时忽略
  }
}
