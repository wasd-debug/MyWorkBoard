const MARKER_KIND = { "!": "priority", "#": "tag", "~": "list" }

export function quickAddTokenAtCaret(value, caret = value.length) {
  const text = String(value || "")
  const position = Math.max(0, Math.min(text.length, Number(caret) || 0))
  const before = text.slice(0, position)
  const match = before.match(/(?:^|\s)([!#~])([^\s]*)$/)
  if (!match) return null
  const markerIndex = position - match[0].length + (match[0][0] === " " ? 1 : 0)
  return {
    kind: MARKER_KIND[match[1]],
    query: match[2],
    start: markerIndex,
    end: position + (text.slice(position).match(/^\S*/)?.[0].length || 0),
  }
}

export function replaceQuickAddToken(value, token, replacement = "") {
  if (!token) return String(value || "")
  const text = String(value || "")
  const prefix = text.slice(0, token.start).trimEnd()
  const suffix = text.slice(token.end)
  const cleanSuffix = suffix.replace(/^\s+/, "")
  return [prefix, replacement, cleanSuffix].filter(Boolean).join(" ")
}
