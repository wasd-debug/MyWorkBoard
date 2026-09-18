import { readdir, readFile, writeFile } from 'node:fs/promises'
import { extname, join } from 'node:path'

const root = process.argv[2]
if (!root) throw new Error('generated client directory is required')

async function normalize(directory) {
  for (const entry of await readdir(directory, { withFileTypes: true })) {
    const path = join(directory, entry.name)
    if (entry.isDirectory()) {
      await normalize(path)
      continue
    }
    if (extname(entry.name) !== '.ts') continue

    const source = await readFile(path, 'utf8')
    const normalized = `${source.replace(/[ \t]+$/gm, '').trimEnd()}\n`
    if (normalized !== source) await writeFile(path, normalized)
  }
}

await normalize(root)
