import test from 'node:test'
import assert from 'node:assert/strict'
import { currentMemberName, ledgerTransactionDraft, rankLedgerOptions, recentLedgerTransactions, selectedResourceId } from './ledgerPreferences.js'
import { accountBalancesAt, totalLedgerAssets } from './ledgerAccounting.js'

test('recent frequent accounts and categories lead without removing unused choices', () => {
  const rows = recentLedgerTransactions([
    { id: '1', occurredOn: '2026-09-10', accountId: 'bank', categoryId: 'food' },
    { id: '2', occurredOn: '2026-09-11', accountId: 'cash', categoryId: 'rent' },
    { id: '3', occurredOn: '2026-09-12', accountId: 'bank', categoryId: 'food' }
  ])
  assert.deepEqual(rankLedgerOptions([{ id: 'cash' }, { id: 'other' }, { id: 'bank' }],
    rows, row => [row.accountId]).map(item => item.id), ['bank', 'cash', 'other'])
  assert.deepEqual(rankLedgerOptions([{ id: 'rent' }, { id: 'food' }],
    rows, row => [row.categoryId]).map(item => item.id), ['food', 'rent'])
})

test('default member resolves to the logged-in user and selected names resolve to IDs', () => {
  const members = [{ id: 'member-1', userId: 3, username: 'demo', displayName: '我' }]
  assert.equal(currentMemberName(members, { id: 3 }), '我')
  assert.equal(selectedResourceId(members, '我', 'displayName'), 'member-1')
  assert.equal(selectedResourceId(members, 'member-1', 'displayName'), 'member-1')
})

test('borrowed cash remains in assets and lent principal remains a receivable until settled', () => {
  const accounts = [{ id: 'cash', accountType: 'cash', balance: 130 }]
  const rows = [
    { accountId: 'cash', kind: 'BORROW_IN', amount: 40, occurredOn: '2026-09-01' },
    { accountId: 'cash', kind: 'LEND_OUT', amount: 20, occurredOn: '2026-09-02' },
    { accountId: 'cash', kind: 'REPAY_DEBT', amount: 10, occurredOn: '2026-09-03' },
    { accountId: 'cash', kind: 'COLLECT_DEBT', amount: 5, occurredOn: '2026-09-04' }
  ]
  assert.equal(totalLedgerAssets(accounts, rows), 145)
  assert.equal(totalLedgerAssets([{ ...accounts[0], balance: 150 }], rows, '2026-09-02'), 170)
  assert.equal(totalLedgerAssets([{ id: 'loan', accountType: 'loan', balance: -50 }, ...accounts], rows), 145)
})

test('account balances use live account projections and count a transfer exactly once per side', () => {
  const accounts = [
    { id: 'source', accountType: 'cash', openingBalance: 100, balance: 80 },
    { id: 'target', accountType: 'bank', openingBalance: 20, balance: 40 }
  ]
  const rows = [
    { accountId: 'source', targetAccountId: 'target', storedKind: 'TRANSFER_OUT', kind: 'TRANSFER', amount: 20, occurredOn: '2026-09-10' },
    { accountId: 'target', targetAccountId: 'source', storedKind: 'TRANSFER_IN', kind: 'TRANSFER', amount: 20, occurredOn: '2026-09-10' }
  ]
  const current = accountBalancesAt(accounts, rows, '2026-09-30', { today: '2026-09-13' })
  assert.deepEqual(current.map(item => item.balance), [80, 40])
  const historical = accountBalancesAt(accounts.map(({ balance, ...account }) => account), rows, '2026-09-09', { today: '2026-09-13' })
  assert.deepEqual(historical.map(item => item.balance), [100, 20])
  const orphan = accountBalancesAt([{ id: 'target', accountType: 'bank', openingBalance: 20 }], [
    { accountId: 'target', storedKind: 'TRANSFER_IN', kind: 'TRANSFER', amount: 20, occurredOn: '2026-09-10' }
  ], '2026-09-12', { today: '2026-09-13' })
  assert.equal(orphan[0].balance, 40)
})

test('draft requires two-level categories only for income/expense and maps selected resources', () => {
  const resources = {
    categories: [{ id: 'primary', kind: 'EXPENSE' }, { id: 'food', parentId: 'primary', kind: 'EXPENSE' }],
    merchants: [{ id: 'shop', name: '商店' }],
    members: [{ id: 'member', displayName: '我' }],
    projects: []
  }
  const form = { kind: 'EXPENSE', amount: '20', accountId: 'cash', categoryId: 'food', payee: '商店', member: '我', project: '' }
  assert.deepEqual(
    (({ categoryId, merchantId, memberId }) => ({ categoryId, merchantId, memberId }))(ledgerTransactionDraft(form, resources)),
    { categoryId: 'food', merchantId: 'shop', memberId: 'member' })
  assert.throws(() => ledgerTransactionDraft({ ...form, categoryId: 'primary' }, resources), /二级分类/)
  assert.equal(ledgerTransactionDraft({ ...form, kind: 'TRANSFER', categoryId: '' }, resources).categoryId, null)
  assert.throws(() => ledgerTransactionDraft({ ...form, payee: '未知商家' }, resources), /商家/)
})
