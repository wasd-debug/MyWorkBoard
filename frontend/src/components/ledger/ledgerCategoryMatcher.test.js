import test from 'node:test'
import assert from 'node:assert/strict'
import { exactLedgerSecondaryCategory, filterLedgerPrimaryCategories, filterLedgerSecondaryCategories, ledgerCategoryParent } from './ledgerCategoryMatcher.js'

const categories = [
  { id: 'expense-food', name: '餐饮', kind: 'EXPENSE', parentId: null },
  { id: 'expense-home', name: '居家', kind: 'EXPENSE', parentId: null },
  { id: 'breakfast', name: '早餐', kind: 'EXPENSE', parentId: 'expense-food' },
  { id: 'rent', name: '房租', kind: 'EXPENSE', parentId: 'expense-home' },
  { id: 'income-work', name: '职业收入', kind: 'INCOME', parentId: null },
  { id: 'salary', name: '工资', kind: 'INCOME', parentId: 'income-work' }
]

test('category matcher filters primary and secondary names while isolating transaction kind', () => {
  assert.deepEqual(filterLedgerPrimaryCategories(categories, 'EXPENSE', '餐').map(item => item.id), ['expense-food'])
  assert.deepEqual(filterLedgerSecondaryCategories(categories, 'EXPENSE', '早').map(item => item.id), ['breakfast'])
  assert.deepEqual(filterLedgerSecondaryCategories(categories, 'INCOME', '工').map(item => item.id), ['salary'])
})

test('category matcher accepts full paths and resolves the selected parent', () => {
  const selected = exactLedgerSecondaryCategory(categories, 'EXPENSE', '餐饮 / 早餐')
  assert.equal(selected?.id, 'breakfast')
  assert.equal(ledgerCategoryParent(categories, selected)?.id, 'expense-food')
  assert.equal(exactLedgerSecondaryCategory(categories, 'INCOME', '早餐'), null)
})

test('secondary matches can be constrained to the selected primary category', () => {
  assert.deepEqual(filterLedgerSecondaryCategories(categories, 'EXPENSE', '', 'expense-home').map(item => item.id), ['rent'])
})
