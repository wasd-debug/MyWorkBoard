import test from 'node:test'
import assert from 'node:assert/strict'
import { resizeAutoGrowTextarea } from './autoGrowTextarea.js'

test('textarea grows with content until the configured maximum', () => {
  const element = { scrollHeight: 118, style: {} }
  resizeAutoGrowTextarea(element, 160)
  assert.equal(element.style.height, '118px')
  assert.equal(element.style.overflowY, 'hidden')
})

test('textarea switches to internal scrolling after reaching the maximum', () => {
  const element = { scrollHeight: 260, style: {} }
  resizeAutoGrowTextarea(element, 144)
  assert.equal(element.style.height, '144px')
  assert.equal(element.style.overflowY, 'auto')
})
