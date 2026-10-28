import assert from 'node:assert/strict'
import test from 'node:test'
import { readFile } from 'node:fs/promises'
import { SPAN_CONFIG, clampSpan, resolveRange } from './calendar/calendarRange.js'

const view = await readFile(new URL('./TasksCalendarView.vue', import.meta.url), 'utf8')

test('calendar preferences clamp span views to the supported ranges', () => {
  assert.equal(clampSpan('multiDay', 20), SPAN_CONFIG.multiDay.max)
  assert.equal(clampSpan('multiWeek', 1), SPAN_CONFIG.multiWeek.min)
  assert.match(view, /spans\.multiDay = clampSpan\('multiDay', stored\.spans\.multiDay\)/)
  assert.match(view, /spans\.multiWeek = clampSpan\('multiWeek', stored\.spans\.multiWeek\)/)
})

test('calendar preferences persist view, spans, colors and scope per account', () => {
  assert.match(view, /const preferenceKey = computed\(\(\) => `calendar_view_v1:\$\{appStore\.authUser\?\.id \|\| 'local'\}`\)/)
  assert.match(view, /view: view\.value, spans: \{ \.\.\.spans \}, colorSettings: \{ \.\.\.colorSettings \}, scope: scope\.value/)
  assert.match(view, /if \(VIEW_KEYS\.includes\(stored\.view\) \|\| SPAN_KEYS\.includes\(stored\.view\)\) view\.value = stored\.view/)
})

test('span views keep their own remembered size when switching views', () => {
  const multiDay = resolveRange({ view: 'multiDay', span: 3, anchor: new Date(2026, 9, 19) })
  const multiWeek = resolveRange({ view: 'multiWeek', span: 6, anchor: new Date(2026, 9, 19) })
  assert.equal(multiDay.days.length, 3)
  assert.equal(multiWeek.days.length, 42)
  assert.match(view, /const range = computed\(\(\) => resolveRange\(\{ view: view\.value, span: spans\[view\.value\], anchor: anchor\.value \}\)\)/)
})
