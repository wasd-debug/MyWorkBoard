import assert from 'node:assert/strict'
import { test } from 'node:test'
import { readdirSync, readFileSync } from 'node:fs'
import { dirname, join, relative } from 'node:path'
import { fileURLToPath } from 'node:url'

const SRC = join(dirname(fileURLToPath(import.meta.url)), '..')

// 这两个模块内部必须调用受限 API（并在不可用时兜底），属于唯一合法出口
const ALLOWED = new Set(['utils/uuid.js', 'utils/clipboard.js'])

const BANNED = [
  {
    name: 'crypto.randomUUID',
    pattern: /crypto\.randomUUID\s*\(/,
    hint: '该 API 只在安全上下文（HTTPS/localhost）存在，线上纯 HTTP 下为 undefined，请改用 utils/uuid.js 的 uuid()'
  },
  {
    name: 'navigator.clipboard',
    pattern: /navigator\.clipboard/,
    hint: '该 API 只在安全上下文存在，线上纯 HTTP 下为 undefined，请改用 utils/clipboard.js 的 copyText()'
  }
]

function walk(dir, out = []) {
  for (const entry of readdirSync(dir, { withFileTypes: true })) {
    const full = join(dir, entry.name)
    if (entry.isDirectory()) walk(full, out)
    else if (/\.(js|vue)$/.test(entry.name) && !entry.name.includes('.test.')) out.push(full)
  }
  return out
}

test('业务代码不得直接使用只在安全上下文可用的浏览器 API', () => {
  const violations = []
  const files = walk(SRC)

  // 防止扫描路径写错导致"零文件通过"的假绿灯
  assert.ok(files.length > 100, `扫描到的源文件只有 ${files.length} 个，路径可能不对`)

  for (const file of files) {
    const key = relative(SRC, file).replaceAll('\\', '/')
    if (ALLOWED.has(key)) continue

    const source = readFileSync(file, 'utf8')
    for (const rule of BANNED) {
      if (!rule.pattern.test(source)) continue
      const line = source.slice(0, source.search(rule.pattern)).split('\n').length
      violations.push(`${key}:${line} 使用了 ${rule.name} —— ${rule.hint}`)
    }
  }

  assert.deepEqual(violations, [], `发现 ${violations.length} 处直接调用了安全上下文限定的 API：\n${violations.join('\n')}`)
})
