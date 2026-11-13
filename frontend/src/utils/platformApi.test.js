import assert from 'node:assert/strict'
import { test } from 'node:test'
import { readdirSync, readFileSync } from 'node:fs'
import { dirname, join, relative } from 'node:path'
import { fileURLToPath } from 'node:url'

const FRONTEND = join(dirname(fileURLToPath(import.meta.url)), '..', '..')
// 除了 src，还要扫 packages：api-client 曾经在这里裸调 crypto.randomUUID，
// 导致「手动新增专注」在纯 HTTP 站点上直接抛 TypeError。
const ROOTS = ['src', 'packages']
const SKIP_DIRS = new Set(['node_modules', 'generated', 'dist'])

// 这几个模块内部必须调用受限 API（并在不可用时兜底），属于唯一合法出口
const ALLOWED = new Set([
  'src/utils/uuid.js',
  'src/utils/clipboard.js',
  'packages/api-client/src/id.js'
])

const BANNED = [
  {
    name: 'crypto.randomUUID',
    pattern: /crypto\.randomUUID\s*\(/,
    hint: '该 API 只在安全上下文（HTTPS/localhost）存在，线上纯 HTTP 下为 undefined，请改用 utils/uuid.js 的 uuid() 或 api-client 的 newId()'
  },
  {
    name: 'navigator.clipboard',
    pattern: /navigator\.clipboard/,
    hint: '该 API 只在安全上下文存在，线上纯 HTTP 下为 undefined，请改用 utils/clipboard.js 的 copyText()'
  }
]

function walk(dir, out = []) {
  for (const entry of readdirSync(dir, { withFileTypes: true })) {
    if (entry.isDirectory()) {
      if (SKIP_DIRS.has(entry.name)) continue
      walk(join(dir, entry.name), out)
    } else if (/\.(js|vue)$/.test(entry.name) && !entry.name.includes('.test.')) {
      out.push(join(dir, entry.name))
    }
  }
  return out
}

test('业务代码不得直接使用只在安全上下文可用的浏览器 API', () => {
  const violations = []
  const files = ROOTS.flatMap(root => walk(join(FRONTEND, root)))

  // 防止扫描路径写错导致"零文件通过"的假绿灯
  assert.ok(files.length > 100, `扫描到的源文件只有 ${files.length} 个，路径可能不对`)

  for (const file of files) {
    const key = relative(FRONTEND, file).replaceAll('\\', '/')
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
