import assert from 'node:assert/strict'
import test from 'node:test'
import { readFile } from 'node:fs/promises'

const view = await readFile(new URL('./TasksCalendarView.vue', import.meta.url), 'utf8')

test('calendar preferences only accept ranges supported by the active view', () => {
  assert.match(view, /view\.value === 'week' \? \[3, 5, 7\]/)
  assert.match(view, /view\.value === 'month' \? \[2, 4, 6\]/)
  assert.match(view, /if \(normalized\) savePreferences\(\)/)
})
