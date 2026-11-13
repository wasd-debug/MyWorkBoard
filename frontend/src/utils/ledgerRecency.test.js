import test from 'node:test'
import assert from 'node:assert/strict'
import { recentLedgerTransactions } from './ledgerRecency.js'

test('recent transactions drop deleted rows and survive missing dates', () => {
  const rows = recentLedgerTransactions([
    { id: 'a', occurredOn: '2026-11-01' },
    { id: 'b', occurredOn: '2026-11-03', deleted: true },
    { id: 'c' },
    null,
    { id: 'd', occurredOn: '2026-10-01' }
  ])
  // 没有日期的记录排在有日期的之后，避免脏数据插到列表最前面
  assert.deepEqual(rows.map(item => item.id), ['a', 'd', 'c'])
})

test('recent transactions sort by date desc, then createdAt, then id', () => {
  const rows = recentLedgerTransactions([
    { id: '1', occurredOn: '2026-11-01', createdAt: '2026-11-01T10:00:00' },
    { id: '2', occurredOn: '2026-11-01', createdAt: '2026-11-01T12:00:00' },
    { id: '3', occurredOn: '2026-11-05' },
    { id: '4', occurredOn: '2026-10-31', createdAt: '2026-11-09T00:00:00' }
  ])
  assert.deepEqual(rows.map(item => item.id), ['3', '2', '1', '4'])
})

test('recent transactions honor the limit without touching the source array', () => {
  const source = Array.from({ length: 500 }, (_, index) => ({ id: String(index), occurredOn: '2026-11-01' }))
  const rows = recentLedgerTransactions(source, 200)
  assert.equal(rows.length, 200)
  // 源数组保持原样：这里只是挑出前 200 条，不能就地排序调用方的数据
  assert.equal(source.length, 500)
  assert.equal(source[0].id, '0')
  assert.equal(source[499].id, '499')
  assert.ok(rows.every(row => source.includes(row)))
})

test('yyyy-MM-dd strings order correctly without localeCompare', () => {
  const rows = recentLedgerTransactions([
    { id: 'sep', occurredOn: '2026-09-30' },
    { id: 'oct', occurredOn: '2026-10-01' },
    { id: 'dec', occurredOn: '2026-12-01' }
  ])
  assert.deepEqual(rows.map(item => item.id), ['dec', 'oct', 'sep'])
})
