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

test('工厂允许为不同用户指定独立数据库名', async () => {
  const { createLedgerSyncEngine } = await import('./index.js')
  const alice = createLedgerSyncEngine({ dbName: 'ledger:id-1', transport: {} })
  const bob = createLedgerSyncEngine({ dbName: 'ledger:id-2', transport: {} })
  assert.equal(alice.dbName, 'ledger:id-1')
  assert.equal(bob.dbName, 'ledger:id-2')
  assert.notEqual(alice.dbName, bob.dbName)
})

test('写入前清理嵌套展示对象中的不可克隆值', async () => {
  const engine = new SyncEngine()
  const row = await engine.put('category', {
    id: 'category-1',
    name: '餐饮',
    children: [{ id: 'child-1', render: () => '仅用于界面' }]
  }, { bookId: 'book-a', recordOp: false })
  assert.equal(row.children[0].id, 'child-1')
  assert.equal('render' in row.children[0], false)
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

test('分页拉取批量合并并保留最后版本', async () => {
  let page = 0
  const engine = new SyncEngine({
    transport: {
      pull: async (_bookId, cursor) => {
        page++
        if (page === 1) {
          return {
            cursor: 2,
            hasMore: true,
            operations: [
              { cursor: 1, entityType: 'transaction', entityId: 'a', operation: 'UPSERT', payload: { amount: 10, revision: 1 } },
              { cursor: 2, entityType: 'transaction', entityId: 'b', operation: 'UPSERT', payload: { amount: 20, revision: 1 } }
            ]
          }
        }
        assert.equal(cursor, 2)
        return {
          cursor: 4,
          hasMore: false,
          operations: [
            { cursor: 3, entityType: 'transaction', entityId: 'a', operation: 'UPSERT', payload: { amount: 15, revision: 2 } },
            { cursor: 4, entityType: 'transaction', entityId: 'b', operation: 'DELETE', payload: { amount: 20, revision: 2 } }
          ]
        }
      }
    }
  })
  const result = await engine.sync('book-a')
  assert.equal(result.pulled, 4)
  assert.equal(result.cursor, 4)
  assert.equal(page, 2)
  assert.equal((await engine.get('transaction', 'a', { bookId: 'book-a' })).amount, 15)
  assert.equal((await engine.get('transaction', 'b', { bookId: 'book-a' })).deleted, true)
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

test('服务端校验拒绝移出待同步队列并保留可定位信息', async () => {
  const engine = new SyncEngine({
    transport: {
      push: async (_bookId, operations) => ({
        results: operations.map(operation => ({
          opId: operation.opId,
          status: 'REJECTED',
          message: '预算金额必须大于 0'
        }))
      }),
      pull: async (_bookId, cursor) => ({ cursor, operations: [] })
    }
  })
  await engine.put('budget', { id: 'budget-a', amount: 0 }, { bookId: 'book-a', opId: 'invalid-budget' })

  await engine.sync('book-a')

  assert.equal((await engine.pendingOperations('book-a')).length, 0)
  const rejected = await engine.rejected('book-a')
  assert.equal(rejected.length, 1)
  assert.equal(rejected[0].operation.opId, 'invalid-budget')
  assert.equal(rejected[0].result.message, '预算金额必须大于 0')
  const exported = JSON.parse(await engine.exportRejected('book-a'))
  assert.equal(exported.operations[0].opId, 'invalid-budget')
  assert.equal(exported.rejections[0].status, 'REJECTED')
  assert.equal(exported.rejections[0].message, '预算金额必须大于 0')
})
