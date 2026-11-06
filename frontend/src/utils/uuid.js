/**
 * 生成 UUID v4。
 *
 * 不要直接调 `crypto.randomUUID()`：它只在**安全上下文**（HTTPS 或 localhost）暴露。
 * 线上是纯 HTTP（http://jsn1024.cn），Chrome 会把 `crypto.randomUUID` 置为 undefined，
 * 打卡、上传附件、发评论这些动作会在生成 id 的第一行抛
 * `TypeError: crypto.randomUUID is not a function`，整页被全局 errorHandler 接住显示
 * 「页面渲染出错了」，表现为"点了没反应/总是报错"。
 *
 * `crypto.getRandomValues` 不受安全上下文限制，所以优先用它手写 v4 兜底。
 */
export function uuid() {
  if (typeof crypto !== 'undefined' && typeof crypto.randomUUID === 'function') {
    return crypto.randomUUID()
  }

  const bytes = new Uint8Array(16)
  if (typeof crypto !== 'undefined' && typeof crypto.getRandomValues === 'function') {
    crypto.getRandomValues(bytes)
  } else {
    // 极老浏览器兜底：不具备密码学强度，但 id 只要不重复即可
    for (let i = 0; i < 16; i += 1) bytes[i] = Math.floor(Math.random() * 256)
  }

  bytes[6] = (bytes[6] & 0x0f) | 0x40 // version 4
  bytes[8] = (bytes[8] & 0x3f) | 0x80 // variant 10xx
  const hex = Array.from(bytes, byte => byte.toString(16).padStart(2, '0')).join('')
  return `${hex.slice(0, 8)}-${hex.slice(8, 12)}-${hex.slice(12, 16)}-${hex.slice(16, 20)}-${hex.slice(20)}`
}
