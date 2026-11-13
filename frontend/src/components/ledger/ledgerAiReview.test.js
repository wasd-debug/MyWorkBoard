import test from 'node:test'
import assert from 'node:assert/strict'
import { aiCategoryNeedsReview, aiCategoryReviewPayload, aiDraftCanConfirm, aiRuleTogglePayload, validRememberKeyword } from './ledgerAiReview.js'

const draft = overrides => ({ id: 'draft-1', kind: 'EXPENSE', amount: 28, occurredOn: '2026-11-13', accountId: 'a1', memberId: 'm1', categoryId: 'c1', warnings: [], ...overrides })

test('matched low confidence category still requires explicit review', () => {
  const item = draft({ categoryMatchStatus: 'matched', categoryReviewRequired: true })
  assert.equal(aiDraftCanConfirm(item), false)
  assert.equal(aiDraftCanConfirm({ ...item, categoryReviewed: true }), true)
})

test('editing a category invalidates readiness until reviewed', () => {
  assert.equal(aiDraftCanConfirm(draft({ categoryEdited: true })), false)
  assert.equal(aiDraftCanConfirm(draft({ categoryEdited: true, categoryReviewed: true })), true)
  assert.equal(aiDraftCanConfirm(draft({ categoryId: '', categoryReviewed: true })), false)
})

test('non categorized transactions do not retain hidden review or keyword requirements', () => {
  const item = draft({ kind: 'TRANSFER', categoryId: '', targetAccountId: 'a2', categoryReviewRequired: true, rememberCategory: true })
  assert.equal(aiCategoryNeedsReview(item), false)
  assert.equal(aiDraftCanConfirm(item), true)
  assert.equal(aiCategoryReviewPayload([item])[0].rememberKeyword, null)
  assert.equal(aiDraftCanConfirm({ ...item, targetAccountId: 'a1' }), false)
})

test('remember keywords must occur in original fields after normalization', () => {
  const item = draft({ originalNote: ' ＣＯＦＦＥＥ   瑞幸 ', originalMerchantName: '门店', note: '改过的备注', rememberCategory: true, rememberKeyword: 'coffee 瑞幸' })
  assert.equal(validRememberKeyword(item), true)
  assert.equal(aiDraftCanConfirm(item), true)
  assert.equal(validRememberKeyword({ ...item, rememberKeyword: '改过的备注' }), false)
  assert.equal(aiDraftCanConfirm({ ...item, rememberKeyword: '不存在' }), false)
  assert.equal(validRememberKeyword({ ...item, rememberKeyword: 'x' }), false)
  assert.equal(validRememberKeyword({ ...item, rememberKeyword: 'x'.repeat(81) }), false)
})

test('review payload retains stable ids when rows are reordered', () => {
  assert.deepEqual(aiCategoryReviewPayload([
    draft({ id: 'second', categoryReviewed: true }),
    draft({ id: 'first', rememberCategory: true, rememberKeyword: ' 瑞幸 ' })
  ]), [
    { draftId: 'second', confirmed: true, rememberKeyword: null },
    { draftId: 'first', confirmed: false, rememberKeyword: '瑞幸' }
  ])
})

test('toggling learned preferences never submits keyword or changes matching scope', () => {
  assert.deepEqual(aiRuleTogglePayload({ source: 'LEARNED', keyword: '长原始备注', enabled: true }), { enabled: false })
})
