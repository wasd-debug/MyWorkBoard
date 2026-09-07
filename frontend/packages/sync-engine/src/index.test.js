import assert from 'node:assert/strict'
import test from 'node:test'
import { SyncEngine } from './index.js'

test('offline operation is pushed once and acknowledged', async () => {
  const batches = []
  const engine = new SyncEngine({
    transport: {
      push: async operations => { batches.push(operations); return { accepted: operations.length } },
      pull: async cursor => ({ cursor, operations: [] })
    }
  })
  await engine.put('transaction', { id: 'local-1', amount: 32, revision: 1 })
  const first = await engine.sync()
  const second = await engine.sync()
  assert.equal(first.pushed, 1)
  assert.equal(second.pushed, 0)
  assert.equal(batches.length, 1)
  assert.equal((await engine.list('transaction')).length, 1)
})

test('newer server revision wins without dropping local entities', async () => {
  const engine = new SyncEngine()
  await engine.put('transaction', { id: '1', amount: 10, revision: 1 }, { recordOp: false })
  await engine.put('transaction', { id: '2', amount: 20, revision: 1 }, { recordOp: false })
  await engine.merge({ entityType: 'transaction', entityId: '1', operation: 'UPSERT', payload: { id: '1', amount: 15, revision: 2 } })
  const rows = await engine.list('transaction')
  assert.equal(rows.length, 2)
  assert.equal(rows.find(row => row.id === '1').amount, 15)
})
