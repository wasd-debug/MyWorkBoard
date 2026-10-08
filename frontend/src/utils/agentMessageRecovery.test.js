import test from 'node:test'
import assert from 'node:assert/strict'
import { parseAgentMetadata, normalizeAgentActions, recoverAgentMetadata, missingActionCard } from './agentMessageRecovery.js'

const action = { actionId: 'action-1', status: 'NEEDS_CONFIRMATION', structuredContent: { input: { amount: 28.59 } } }

test('restores structured cards and isolates editing input from saved metadata', () => {
  const metadata = recoverAgentMetadata(JSON.stringify({ actions: [action] }))
  const cards = normalizeAgentActions(metadata.actions)
  assert.equal(cards[0].actionId, action.actionId)
  cards[0].form.amount = 49
  assert.equal(metadata.actions[0].structuredContent.input.amount, 28.59)
})

test('retains matched live cards when legacy history has no actions field, but respects explicit replacement', () => {
  assert.deepEqual(recoverAgentMetadata(null, { actions: [action] }).actions, [action])
  assert.deepEqual(recoverAgentMetadata({ actions: [] }, { actions: [action] }).actions, [])
  const replacement = { ...action, actionId: 'replacement' }
  assert.equal(recoverAgentMetadata({ actions: [replacement] }, { actions: [action] }).actions[0].actionId, 'replacement')
})

test('malformed legacy metadata and actions never prevent the text reply from loading', () => {
  for (const value of ['{broken', 'null', '[]', '42']) assert.deepEqual(parseAgentMetadata(value), {})
  for (const value of [null, {}, 'invalid']) assert.deepEqual(normalizeAgentActions(value), [])
  assert.deepEqual(normalizeAgentActions([null, {}, action]).map(row => row.actionId), ['action-1'])
})

test('missing cards get a recovery notice instead of a fabricated approval', () => {
  const message = { role: 'assistant', content: '已生成 2 笔支出的待确认操作，请在站内操作卡片中确认。' }
  assert.equal(missingActionCard(message), true)
  assert.deepEqual(normalizeAgentActions(message.actions), [])
  assert.equal(missingActionCard({ ...message, actions: [action] }), false)
  assert.equal(missingActionCard({ ...message, typing: true }), false)
  assert.equal(missingActionCard({ ...message, role: 'user' }), false)
  assert.equal(missingActionCard({ role: 'assistant', content: '你好' }), false)
  assert.equal(missingActionCard({ role: 'assistant', content: '你好', toolExecutions: {} }), false)
  assert.equal(missingActionCard({ role: 'assistant', toolExecutions: [null] }), false)
  assert.equal(missingActionCard({ role: 'assistant', toolExecutions: [{ status: 'NEEDS_INPUT' }] }), true)
})
