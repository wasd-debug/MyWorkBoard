import test from 'node:test'
import assert from 'node:assert/strict'
import { api, apiLedgerAiConfirm, apiUpdateLedgerAiCategoryRule } from '../../../packages/api-client/src/index.js'

test('rule toggle uses PATCH and carries revision without broadening learned match', async t => {
  const original = api.defaults.adapter
  t.after(() => { api.defaults.adapter = original })
  api.defaults.adapter = async config => {
    assert.equal(config.method, 'patch')
    assert.match(config.url, /\/books\/book%2Fone\/ai\/category-rules\/rule%2Fone$/)
    assert.equal(String(config.headers.get('If-Match')), '7')
    assert.deepEqual(JSON.parse(config.data), { enabled: false })
    return { data: { data: { revision: 8 } }, status: 200, statusText: 'OK', headers: {}, config }
  }
  assert.deepEqual(await apiUpdateLedgerAiCategoryRule('book/one', 'rule/one', { enabled: false }, 7), { revision: 8 })
})

test('confirm sends stable draft IDs and per-row reviews with idempotency header', async t => {
  const original = api.defaults.adapter
  t.after(() => { api.defaults.adapter = original })
  const transactions = [{ id: 'row-two' }, { id: 'row-one' }]
  const reviews = [{ draftId: 'row-one', confirmed: true, rememberKeyword: '瑞幸' }]
  api.defaults.adapter = async config => {
    assert.equal(config.method, 'post')
    assert.match(config.url, /\/ai\/batch%2Fid\/confirm$/)
    assert.equal(config.headers.get('Idempotency-Key'), 'confirm-once')
    assert.deepEqual(JSON.parse(config.data), { transactions, reviews })
    return { data: { data: { createdCount: 2 } }, status: 200, statusText: 'OK', headers: {}, config }
  }
  assert.deepEqual(await apiLedgerAiConfirm('book', 'batch/id', transactions, 'confirm-once', reviews), { createdCount: 2 })
})
