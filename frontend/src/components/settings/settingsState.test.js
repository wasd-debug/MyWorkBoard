import assert from 'node:assert/strict'
import test from 'node:test'
import {
  CITY_THEMES,
  COLOR_THEMES,
  DEFAULT_SETTINGS,
  PANES,
  PANE_KEYS,
  PHOTO_THEMES,
  SEASON_THEMES,
  SMART_LISTS,
  mergeSettings,
  resetSettings
} from './settingsState.js'

test('设置页：分区只剩 6 个（功能模块/关联与导入/快捷键 已删）', () => {
  assert.deepEqual(PANE_KEYS, ['smartlist', 'notify', 'datetime', 'appearance', 'advanced', 'collab'])
  assert.equal(PANES.length, 6)
  assert.equal(PANE_KEYS[0], 'smartlist')
  for (const removed of ['modules', 'import', 'shortcut', 'account', 'premium', 'ai', 'sticky', 'widget', 'about']) {
    assert.equal(PANE_KEYS.includes(removed), false, `${removed} 分区应已删除`)
  }
})

test('设置页：合并只保留默认值里存在的 key', () => {
  const merged = mergeSettings({ colorTheme: 'NIGHT', unknownKey: 'x' })
  assert.equal(merged.colorTheme, 'NIGHT')
  assert.equal('unknownKey' in merged, false)
  assert.equal(merged.seasonTheme, DEFAULT_SETTINGS.seasonTheme)
})

test('设置页：空值与脏数据回落到默认值', () => {
  assert.deepEqual(mergeSettings(null), { ...DEFAULT_SETTINGS })
  const merged = mergeSettings({ persistentRemind: null })
  assert.equal(merged.persistentRemind, DEFAULT_SETTINGS.persistentRemind)
})

test('设置页：重置只影响传入的键', () => {
  const current = { ...DEFAULT_SETTINGS, defaultPriority: 'LOW', colorTheme: 'NIGHT' }
  const next = resetSettings(['defaultPriority'], current)
  assert.equal(next.defaultPriority, DEFAULT_SETTINGS.defaultPriority)
  assert.equal(next.colorTheme, 'NIGHT')
})

test('设置页：智能清单用的是任务页侧栏的系统视图 key', () => {
  const keys = SMART_LISTS.flatMap(group => group.items.map(item => item.key))
  assert.equal(SMART_LISTS.length, 3)
  assert.equal(keys.length, 12)
  // 必须与 TaskWorkspaceView 的 primaryViews / 侧栏分组一一对应，否则同步不上
  assert.deepEqual(keys, ['all', 'today', 'tomorrow', 'next7', 'assigned', 'inbox', 'summary', 'tags', 'filters', 'completed', 'abandoned', 'trash'])
  const sections = SMART_LISTS.flatMap(group => group.items.filter(item => item.section).map(item => item.key))
  assert.deepEqual(sections, ['tags', 'filters'])
})

test('设置页：智能清单不再单独存一份默认值（改由任务页侧栏状态提供）', () => {
  for (const key of ['smartAll', 'smartToday', 'navTags', 'navFilters', 'navTrash']) {
    assert.equal(key in DEFAULT_SETTINGS, false, `${key} 不应再单独存默认值`)
  }
  for (const key of ['moduleHabit', 'moduleAi', 'shortcutOverrides', 'appearanceTab', 'fontSize', 'listColor', 'sidebarCount', 'completedStyle']) {
    assert.equal(key in DEFAULT_SETTINGS, false, `${key} 不应再存在`)
  }
})

test('设置页：AI 与便签的默认值已随分区一起删除', () => {
  for (const key of ['aiEnabled', 'aiSuggest', 'aiWeekly', 'stickyColor', 'stickyOpacity', 'stickyFont', 'stickyTop', 'stickyHideTaskbar', 'stickyRestoreSize', 'stickyGap']) {
    assert.equal(key in DEFAULT_SETTINGS, false, `${key} 不应再存在`)
  }
})

test('设置页：颜色主题 12 套且 key 唯一', () => {
  assert.equal(COLOR_THEMES.length, 12)
  assert.equal(new Set(COLOR_THEMES.map(item => item.key)).size, 12)
})

test('设置页：主题系列条目齐全且 key 唯一', () => {
  assert.equal(SEASON_THEMES.length, 4)
  assert.equal(CITY_THEMES.length, 12)
  assert.equal(PHOTO_THEMES.length, 10)
  for (const list of [SEASON_THEMES, CITY_THEMES, PHOTO_THEMES]) {
    assert.equal(new Set(list.map(item => item.key)).size, list.length)
    list.forEach(item => {
      assert.ok(item.label)
      assert.ok(item.art || item.image, `${item.label} 缺少缩略图数据`)
    })
  }
})
