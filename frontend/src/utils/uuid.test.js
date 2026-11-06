import assert from 'node:assert/strict'
import { test } from 'node:test'
import { uuid } from './uuid.js'

const V4 = /^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/

/** 临时替换全局 crypto，跑完恢复 */
function withCrypto(fake, run) {
  const original = globalThis.crypto
  Object.defineProperty(globalThis, 'crypto', { value: fake, configurable: true, writable: true })
  try {
    return run()
  } finally {
    Object.defineProperty(globalThis, 'crypto', { value: original, configurable: true, writable: true })
  }
}

test('安全上下文：直接用 crypto.randomUUID', () => {
  const calls = []
  const value = withCrypto(
    { randomUUID: () => { calls.push('randomUUID'); return 'fixed-uuid' } },
    () => uuid()
  )
  assert.equal(value, 'fixed-uuid')
  assert.deepEqual(calls, ['randomUUID'])
})

test('非安全上下文（HTTP）：randomUUID 缺失时用 getRandomValues 兜底', () => {
  // 线上 http:// 就是这种形态：crypto 在，randomUUID 与 subtle 都不在
  const fake = { getRandomValues: bytes => { bytes.fill(0xab); return bytes } }
  const value = withCrypto(fake, () => uuid())
  assert.match(value, V4)
  // 版本位与变体位必须被正确覆写，否则不是合法 v4
  assert.equal(value[14], '4')
  assert.ok(['8', '9', 'a', 'b'].includes(value[19]))
})

test('完全没有 crypto 时仍能生成合法 v4', () => {
  const value = withCrypto(undefined, () => uuid())
  assert.match(value, V4)
})

test('连续生成不重复（getRandomValues 路径也走真实随机）', () => {
  const fake = { getRandomValues: bytes => globalThis.__realGetRandomValues(bytes) }
  const original = globalThis.crypto
  Object.defineProperty(globalThis, '__realGetRandomValues', {
    value: bytes => original.getRandomValues(bytes),
    configurable: true,
    writable: true
  })
  try {
    const set = withCrypto(fake, () => new Set(Array.from({ length: 500 }, () => uuid())))
    assert.equal(set.size, 500)
  } finally {
    delete globalThis.__realGetRandomValues
  }
})
