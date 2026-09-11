import assert from 'node:assert/strict'
import test from 'node:test'
import { SyncEngine } from './index.js'

test('断网新增在确认后只推送一次', async () => {
  const batches = []
  const engine = new SyncEngine({
    transport: {
      push: async (bookId, operations) => {
        batches.push({ bookId, operations })
        return {
          results: operations.map(operation => ({
            opId: operation.opId,
            status: 'APPLIED',
            entity: { ...operation.payload, revision: 1 }
          }))
        }
      },
      pull: async (_bookId, cursor) => ({ cursor, operations: [] })
    }
  })
  await engine.put('transaction', { id: 'local-1', amount: 32 }, { bookId: 'book-a' })
  const first = await engine.sync('book-a')
  const second = await engine.sync('book-a')
  assert.equal(first.pushed, 1)
  assert.equal(second.pushed, 0)
  assert.equal(batches.length, 1)
  assert.equal((await engine.list('transaction', { bookId: 'book-a' })).length, 1)
})

test('不同账本使用独立游标和存储范围', async () => {
  const engine = new SyncEngine()
  await engine.put('account', { id: 'same', name: 'A' }, { bookId: 'book-a', recordOp: false })
  await engine.put('account', { id: 'same', name: 'B' }, { bookId: 'book-b', recordOp: false })
  assert.equal((await engine.list('account', { bookId: 'book-a' }))[0].name, 'A')
  assert.equal((await engine.list('account', { bookId: 'book-b' }))[0].name, 'B')
})

test('较新的服务端版本覆盖本地版本并保留墓碑', async () => {
  const engine = new SyncEngine()
  await engine.put('transaction', { id: '1', amount: 10, revision: 1 }, { bookId: 'book-a', recordOp: false })
  await engine.merge({
    entityType: 'transaction',
    entityId: '1',
    operation: 'DELETE',
    payload: { id: '1', amount: 15, revision: 2 }
  }, 'book-a')
  const row = await engine.get('transaction', '1', { bookId: 'book-a' })
  assert.equal(row.amount, 15)
  assert.equal(row.deleted, true)
  assert.equal((await engine.list('transaction', { bookId: 'book-a' })).length, 0)
})

test('冲突保留待处理操作，权限撤销写入可导出队列', async () => {
  let round = 0
  const engine = new SyncEngine({
    transport: {
      push: async (_bookId, operations) => ({
        results: operations.map(operation => ({
          opId: operation.opId,
          status: round++ === 0 ? 'CONFLICT' : 'FORBIDDEN',
          serverRevision: 2,
          serverEntity: { id: operation.entityId, amount: 50, revision: 2 }
        }))
      }),
      pull: async (_bookId, cursor) => ({ cursor, operations: [] })
    }
  })
  await engine.put('transaction', { id: 'a', amount: 40, revision: 1 }, { bookId: 'book-a', opId: 'op-a' })
  await engine.sync('book-a')
  assert.equal((await engine.conflicts('book-a')).length, 1)
  await engine.resolveConflict('op-a', 'local')
  await engine.sync('book-a')
  assert.equal((await engine.rejected('book-a')).length, 1)
  assert.match(await engine.exportRejected('book-a'), /op-a/)
})
