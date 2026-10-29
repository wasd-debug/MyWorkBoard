import assert from 'node:assert/strict'
import test from 'node:test'
import {
  COLOR_THEMES,
  DEFAULT_SETTINGS,
  MODULES,
  PANES,
  PANE_KEYS,
  SHORTCUT_GROUPS,
  SMART_LISTS,
  WIDGETS,
  mergeSettings,
  resetSettings
} from './settingsState.js'

test('设置页：分区顺序与数量对齐参考图', () => {
  assert.equal(PANES.length, 15)
  assert.deepEqual(PANE_KEYS.slice(0, 4), ['account', 'premium', 'modules', 'smartlist'])
  assert.equal(PANE_KEYS.at(-1), 'about')
  assert.equal(new Set(PANE_KEYS).size, PANE_KEYS.length)
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

test('设置页：功能模块与小部件的 key 都有开关默认值', () => {
  MODULES.forEach(item => assert.equal(typeof DEFAULT_SETTINGS[item.key], 'boolean'))
  assert.equal(WIDGETS.length, 5)
  assert.equal(MODULES.length, 4)
})

test('设置页：颜色主题 12 套且 key 唯一', () => {
  assert.equal(COLOR_THEMES.length, 12)
  assert.equal(new Set(COLOR_THEMES.map(item => item.key)).size, 12)
})

test('设置页：快捷键分组每项要么有按键、要么标记为待设置', () => {
  assert.ok(SHORTCUT_GROUPS.length >= 9)
  SHORTCUT_GROUPS.forEach(group => {
    assert.ok(group.title)
    assert.ok(group.items.length)
    group.items.forEach(item => {
      const hasKeys = Array.isArray(item.keys) && item.keys.length > 0
      const hasSequence = Array.isArray(item.sequences) && item.sequences.length > 0
      assert.ok(hasKeys || hasSequence || item.unset, `${item.label} 既没有按键也没有 unset 标记`)
    })
  })
})
