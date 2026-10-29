// 设置页：分区定义 + 默认值 + 本地持久化（纯逻辑部分便于单测）

export const PANES = [
  { key: 'account', label: '账号' },
  { key: 'premium', label: '高级会员' },
  { key: 'modules', label: '功能模块' },
  { key: 'smartlist', label: '智能清单' },
  { key: 'notify', label: '提醒与通知' },
  { key: 'datetime', label: '日期与时间' },
  { key: 'appearance', label: '外观' },
  { key: 'ai', label: 'AI 功能' },
  { key: 'advanced', label: '更多设置' },
  { key: 'import', label: '关联与导入' },
  { key: 'collab', label: '共享协作' },
  { key: 'sticky', label: '便签' },
  { key: 'widget', label: '桌面小部件' },
  { key: 'shortcut', label: '快捷键' },
  { key: 'about', label: '关于' }
]

export const PANE_KEYS = PANES.map(pane => pane.key)

export const SHOW_OPTIONS = [
  { value: 'SHOW', label: '显示' },
  { value: 'AUTO', label: '有内容时显示' },
  { value: 'HIDE', label: '隐藏' }
]

export const SMART_LISTS = [
  { group: '智能清单', items: [
    { key: 'smartAll', label: '所有', icon: '🗂️', value: 'SHOW' },
    { key: 'smartToday', label: '今天', icon: '📅', value: 'SHOW' },
    { key: 'smartTomorrow', label: '明天', icon: '🌅', value: 'AUTO' },
    { key: 'smartNext7', label: '最近 7 天', icon: '7️⃣', value: 'SHOW' },
    { key: 'smartAssigned', label: '指派给我', icon: '👤', value: 'SHOW' },
    { key: 'smartInbox', label: '收集箱', icon: '📥', value: 'SHOW' },
    { key: 'smartSummary', label: '摘要', icon: '📄', value: 'SHOW' }
  ] },
  { group: '组织', items: [
    { key: 'navTags', label: '标签', icon: '🏷️', value: 'SHOW' },
    { key: 'navFilters', label: '过滤器', icon: '🔽', value: 'SHOW' }
  ] },
  { group: '状态', items: [
    { key: 'navCompleted', label: '已完成', icon: '✅', value: 'SHOW' },
    { key: 'navAbandoned', label: '已放弃', icon: '🚫', value: 'SHOW' },
    { key: 'navTrash', label: '垃圾桶', icon: '🗑️', value: 'SHOW' }
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
  { key: 'SPRING', label: '春季', color: '#cfe3b4' },
  { key: 'SUMMER', label: '夏季', color: '#a9d8ea' },
  { key: 'AUTUMN', label: '秋季', color: '#f3c3a5' },
  { key: 'WINTER', label: '冬季', color: '#bcd0e6' }
]

export const CITY_THEMES = [
  { key: 'SHANGHAI', label: '上海', color: '#cbb6d6' },
  { key: 'QINGDAO', label: '青岛', color: '#8fd4d2' },
  { key: 'CHENGDU', label: '成都', color: '#c3a7d6' },
  { key: 'XIAN', label: '西安', color: '#7fbfc6' }
]

export const SHORTCUT_GROUPS = [
  { title: '通用', items: [
    { label: '同步任务', keys: ['Ctrl', 'S'] },
    { label: '取消', keys: ['Esc'] },
    { label: '撤销', keys: ['Ctrl', 'Z'] },
    { label: '重做', keys: ['Ctrl', 'Shift', 'Z'] },
    { label: '打印', keys: ['Ctrl', 'P'] },
    { label: '打印详细', keys: ['Ctrl', 'Alt', 'P'] },
    { label: '打开指令菜单', keys: ['Ctrl', 'K'] },
    { label: '快捷键', keys: ['?'] }
  ] },
  { title: '全局操作', desc: '应用即使在后台，也能响应的快捷键。', items: [
    { label: '全局添加', keys: ['Ctrl', 'Shift', 'A'] },
    { label: '显示/隐藏应用', keys: ['Ctrl', 'Shift', 'O'] },
    { label: '开启/关闭专注弹窗', keys: ['Ctrl', 'Shift', 'T'] },
    { label: '置顶/取消置顶应用', unset: true },
    { label: '锁定/解锁应用', keys: ['Ctrl', 'Shift', 'L'] },
    { label: '添加便签', unset: true },
    { label: '显示/隐藏所有便签', unset: true }
  ] },
  { title: '任务', items: [
    { label: '添加任务', keys: ['Ctrl', 'N'] },
    { label: '向下添加任务', keys: ['Enter'] },
    { label: '添加子任务', keys: ['Shift', 'Enter'] },
    { label: '展开/折叠所有分组', unset: true },
    { label: '展开/折叠所有子任务', unset: true },
    { label: '列表视图', sequences: [['V', 'L']] },
    { label: '看板视图', sequences: [['V', 'K']] },
    { label: '时间线视图', sequences: [['V', 'T']] }
  ] },
  { title: '快速添加任务设置', desc: '创建或编辑任务时，可输入这些快捷键快速添加细节，如设置日期、标签、清单等。', items: [
    { label: '设置日期', keys: ['*'] },
    { label: '设置优先级', keys: ['!'] },
    { label: '设置标签', keys: ['#'] },
    { label: '添加/移动任务到指定清单', keys: ['-', '/^'] },
    { label: '指派任务给共享成员', keys: ['@'] }
  ] },
  { title: '编辑任务', desc: '光标位于任务时这些快捷键才会生效。', items: [
    { label: '完成任务', keys: ['Ctrl', 'M'] },
    { label: '置顶任务', unset: true },
    { label: '删除任务', keys: ['Alt', 'Delete'] },
    { label: '设置日期', keys: ['Ctrl', 'D'] },
    { label: '清除日期', keys: ['Ctrl', '0'] },
    { label: '设为今天', keys: ['Ctrl', '1'] },
    { label: '设为明天', keys: ['Ctrl', '2'] },
    { label: '设为下周', keys: ['Ctrl', '3'] },
    { label: '设置为“无优先级”', keys: ['Alt', '0'] },
    { label: '设置为“低优先级”', keys: ['Alt', '1'] },
    { label: '设置为“中优先级”', keys: ['Alt', '2'] },
    { label: '设置为“高优先级”', keys: ['Alt', '3'] },
    { label: '以便签形式打开任务', unset: true }
  ] },
  { title: '导航', desc: '可方便的前往功能模块、访问你的设置以及其他等等。', items: [
    { label: '打开搜索', keys: ['Ctrl', 'F'] },
    { label: '前往“设置”', sequences: [['G', 'S']] },
    { label: '前往“所有”', sequences: [['G', 'A']] },
    { label: '前往“今天”', sequences: [['G', 'T']], keys: ['Ctrl', 'Alt', '2'] },
    { label: '前往“明天”', sequences: [['G', 'R']] },
    { label: '前往“最近 7 天”', sequences: [['G', 'N']] },
    { label: '前往“指派给我”', sequences: [['G', 'M']] },
    { label: '前往“收集箱”', sequences: [['G', 'I']], keys: ['Ctrl', 'Alt', '1'] },
    { label: '前往“已完成”', sequences: [['G', 'C']] },
    { label: '前往“已放弃”', sequences: [['G', 'W']] },
    { label: '前往“垃圾桶”', sequences: [['G', 'G']] },
    { label: '前往“摘要”', sequences: [['G', 'B']] }
  ] },
  { title: '日历', items: [
    { label: '日视图', keys: ['D', '1'] },
    { label: '周视图', keys: ['W', '2'] },
    { label: '月视图', keys: ['M', '3'] },
    { label: '年视图', keys: ['Y', '4'] },
    { label: '日程视图', keys: ['A', '5'] },
    { label: '回到今天', keys: ['T'] }
  ] },
  { title: '专注', items: [
    { label: '继续/暂停专注', unset: true },
    { label: '开始/结束专注', unset: true }
  ] },
  { title: '便签', items: [
    { label: '颜色', keys: ['Ctrl', 'Shift', 'C'] },
    { label: '置顶', keys: ['F12'] },
    { label: '展开/折叠所有', keys: ['Ctrl', 'E'] },
    { label: '顶部排列', keys: ['Alt', '↑'] },
    { label: '居左排列', keys: ['Alt', '←'] },
    { label: '居下排列', keys: ['Alt', '↓'] }
  ] }
]

export const WIDGETS = [
  { key: 'task', label: '任务', desc: '直观聚焦任务列表' },
  { key: 'calendar', label: '日历', desc: '轻松查看所有日程' },
  { key: 'quadrant', label: '四象限', desc: '轻重缓急一目了然' },
  { key: 'habit', label: '习惯', desc: '轻松打卡养成好习惯' },
  { key: 'countdown', label: '倒数纪念日', desc: '记录每一个重要的日子' }
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
  smartAssigned: 'SHOW',
  smartInbox: 'SHOW',
  smartSummary: 'SHOW',
  navTags: 'SHOW',
  navFilters: 'SHOW',
  navCompleted: 'SHOW',
  navAbandoned: 'SHOW',
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
  fontSize: 'NORMAL',
  listColor: 'SHOW',
  sidebarCount: 'ALL',
  completedStyle: 'STANDARD',
  // AI
  aiEnabled: true,
  aiSuggest: true,
  aiWeekly: false,
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
  // 便签
  stickyColor: '#ffe8a3',
  stickyOpacity: 88,
  stickyFont: 'NORMAL',
  stickyTop: true,
  stickyHideTaskbar: false,
  stickyRestoreSize: true,
  stickyGap: 'NORMAL',
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
