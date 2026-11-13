import assert from 'node:assert/strict'
import { test } from 'node:test'
import { newId } from './id.js'

const V4 = /^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/

function withCrypto(fake, run) {
  const original = globalThis.crypto
  Object.defineProperty(globalThis, 'crypto', { value: fake, configurable: true, writable: true })
  try {
    return run()
  } finally {
    Object.defineProperty(globalThis, 'crypto', { value: original, configurable: true, writable: true })
  }
}

test('安全上下文：优先使用 crypto.randomUUID', () => {
  const value = withCrypto({ randomUUID: () => 'fixed-uuid' }, () => newId())
  assert.equal(value, 'fixed-uuid')
})

test('纯 HTTP（线上形态）：randomUUID 缺失时用 getRandomValues 兜底', () => {
  // 线上 http://jsn1024.cn 就是这种形态：crypto 在，randomUUID 不在
  const fake = { getRandomValues: bytes => { bytes.fill(0xcd); return bytes } }
  const value = withCrypto(fake, () => newId())

  assert.match(value, V4)
  assert.equal(value[14], '4')
  assert.ok(['8', '9', 'a', 'b'].includes(value[19]))
})

test('完全没有 crypto 时仍能生成合法 v4', () => {
  assert.match(withCrypto(undefined, () => newId()), V4)
})

test('连续生成不重复', () => {
  const set = new Set(Array.from({ length: 500 }, () => newId()))
  assert.equal(set.size, 500)
})
