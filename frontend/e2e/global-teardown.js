import { execFileSync } from 'node:child_process'
import { fileURLToPath } from 'node:url'
import path from 'node:path'

export default async function globalTeardown() {
  if (process.env.SALARY_E2E_SKIP_COMPOSE === '1') return
  const frontendDir = path.dirname(path.dirname(fileURLToPath(import.meta.url)))
  const projectDir = path.dirname(frontendDir)
  const composeFile = path.join(projectDir, 'deploy', 'docker-compose.e2e.yml')
  execFileSync('docker', ['compose', '-p', 'salary-sync-e2e', '-f', composeFile, 'down'], {
    cwd: projectDir,
    stdio: 'inherit'
  })
}
