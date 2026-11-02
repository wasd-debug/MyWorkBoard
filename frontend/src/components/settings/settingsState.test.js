import assert from 'node:assert/strict'
import test from 'node:test'
import {
  CITY_THEMES,
  COLOR_THEMES,
  DEFAULT_SETTINGS,
  MODULES,
  PANES,
  PANE_KEYS,
  PHOTO_THEMES,
  SEASON_THEMES,
  SHORTCUT_GROUPS,
  SMART_LISTS,
  mergeSettings,
  resetSettings
} from './settingsState.js'

test('设置页：分区顺序与数量（账号/高级会员/AI/便签/桌面小部件/关于 已删）', () => {
  assert.equal(PANES.length, 9)
  assert.deepEqual(PANE_KEYS, ['modules', 'smartlist', 'notify', 'datetime', 'appearance', 'advanced', 'import', 'collab', 'shortcut'])
  assert.equal(new Set(PANE_KEYS).size, PANE_KEYS.length)
  for (const removed of ['account', 'premium', 'ai', 'sticky', 'widget', 'about']) {
    assert.equal(PANE_KEYS.includes(removed), false, `${removed} 分区应已删除`)
  }
})

test('设置页：合并只保留默认值里存在的 key', () => {
  const merged = mergeSettings({ smartToday: 'HIDE', unknownKey: 'x' })
  assert.equal(merged.smartToday, 'HIDE')
  assert.equal('unknownKey' in merged, false)
  assert.equal(merged.smartAll, DEFAULT_SETTINGS.smartAll)
})

test('设置页：空值与脏数据回落到默认值', () => {
  assert.deepEqual(mergeSettings(null), { ...DEFAULT_SETTINGS })
  const merged = mergeSettings({ persistentRemind: null })
  assert.equal(merged.persistentRemind, DEFAULT_SETTINGS.persistentRemind)
})

test('设置页：重置只影响传入的键', () => {
  const current = { ...DEFAULT_SETTINGS, fontSize: 'HUGE', colorTheme: 'NIGHT' }
  const next = resetSettings(['fontSize'], current)
  assert.equal(next.fontSize, DEFAULT_SETTINGS.fontSize)
  assert.equal(next.colorTheme, 'NIGHT')
})

test('设置页：智能清单每一项都能落到默认值里', () => {
  const keys = SMART_LISTS.flatMap(group => group.items.map(item => item.key))
  assert.equal(SMART_LISTS.length, 3)
  assert.equal(keys.length, 12)
  keys.forEach(key => assert.ok(key in DEFAULT_SETTINGS, `${key} 缺少默认值`))
})

test('设置页：功能模块的 key 都有开关默认值', () => {
  MODULES.forEach(item => assert.equal(typeof DEFAULT_SETTINGS[item.key], 'boolean'))
  assert.equal(MODULES.length, 4)
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

test('设置页：快捷键分组每项要么有按键、要么可录制', () => {
  assert.ok(SHORTCUT_GROUPS.length >= 4)
  SHORTCUT_GROUPS.forEach(group => {
    assert.ok(group.title)
    assert.ok(group.items.length)
    group.items.forEach(item => {
      const hasKeys = Array.isArray(item.keys) && item.keys.length > 0
      const hasSequence = Array.isArray(item.sequences) && item.sequences.length > 0
      assert.ok(hasKeys || hasSequence || item.recordable, `${item.label} 既没有按键也不能录制`)
    })
  })
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
