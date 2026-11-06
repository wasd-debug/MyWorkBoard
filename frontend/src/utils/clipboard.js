/**
 * 复制文本到剪贴板。
 *
 * `navigator.clipboard` 只在**安全上下文**（HTTPS / localhost）存在，
 * 线上是纯 HTTP（http://jsn1024.cn），该对象为 undefined，直接调用会抛
 * `TypeError: Cannot read properties of undefined (reading 'writeText')`，
 * 表现为点了复制没反应或提示「复制失败」。
 *
 * 兜底走 `document.execCommand('copy')`：已废弃但不受安全上下文限制，
 * 是所有浏览器在 http 下唯一还能用的方案。
 *
 * 失败时**抛错**（而不是返回 false），这样调用方原有的 try/catch 语义不变。
 */
export async function copyText(text) {
  const value = String(text ?? '')

  if (typeof navigator !== 'undefined' && navigator.clipboard?.writeText) {
    try {
      await navigator.clipboard.writeText(value)
      return true
    } catch {
      // 权限被拒 / 非聚焦文档 → 交给下面的兜底
    }
  }

  if (legacyCopy(value)) return true
  throw new Error('复制失败')
}

function legacyCopy(value) {
  if (typeof document === 'undefined' || !document.body) return false

  const area = document.createElement('textarea')
  area.value = value
  area.setAttribute('readonly', '')
  // 不能用 display:none / visibility:hidden，那样选不中内容
  area.style.cssText = 'position:fixed;top:0;left:-9999px;opacity:0;pointer-events:none'
  document.body.appendChild(area)

  const selection = typeof document.getSelection === 'function' ? document.getSelection() : null
  const savedRange = selection && selection.rangeCount > 0 ? selection.getRangeAt(0) : null

  let ok = false
  try {
    area.select()
    area.setSelectionRange(0, value.length)
    ok = document.execCommand('copy')
  } catch {
    ok = false
  }

  area.remove()
  // 还原用户原来的选区，避免复制动作把光标位置弄丢
  if (savedRange && selection) {
    selection.removeAllRanges()
    selection.addRange(savedRange)
  }
  return ok
}
