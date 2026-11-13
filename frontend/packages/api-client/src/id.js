/**
 * 生成 UUID v4，供 api-client 内的幂等键使用。
 *
 * 不要直接调 `crypto.randomUUID()`：它只在**安全上下文**（HTTPS 或 localhost）暴露。
 * 线上是纯 HTTP（http://jsn1024.cn），Chrome 会把 `crypto.randomUUID` 置为 undefined，
 * 于是 `POST /tasks/focus/sessions`（手动新增专注）在第一行就抛
 * `TypeError: crypto.randomUUID is not a function`，整页被全局 errorHandler 接住。
 *
 * 这里是 `src/utils/uuid.js` 的同一套兜底逻辑：`crypto.getRandomValues` 不受安全上下文限制。
 * api-client 是独立包，不能反向依赖 `src/`，所以只能在包内保留一份实现；
 * `src/utils/platformApi.test.js` 会扫描本目录，禁止出现裸调 `crypto.randomUUID`。
 */
export function newId() {
  const cryptoApi = globalThis.crypto
  if (typeof cryptoApi?.randomUUID === 'function') return cryptoApi.randomUUID()

  const bytes = new Uint8Array(16)
  if (typeof cryptoApi?.getRandomValues === 'function') {
    cryptoApi.getRandomValues(bytes)
  } else {
    // 极老浏览器兜底：不具密码学强度，但幂等键只要不重复即可
    for (let index = 0; index < bytes.length; index += 1) bytes[index] = Math.floor(Math.random() * 256)
  }

  bytes[6] = (bytes[6] & 0x0f) | 0x40 // version 4
  bytes[8] = (bytes[8] & 0x3f) | 0x80 // variant 10xx
  const hex = Array.from(bytes, byte => byte.toString(16).padStart(2, '0')).join('')
  return `${hex.slice(0, 8)}-${hex.slice(8, 12)}-${hex.slice(12, 16)}-${hex.slice(16, 20)}-${hex.slice(20)}`
}
