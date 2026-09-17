import { execFileSync } from 'node:child_process'
import { existsSync } from 'node:fs'
import { fileURLToPath } from 'node:url'
import path from 'node:path'

const frontendDir = path.dirname(path.dirname(fileURLToPath(import.meta.url)))
const projectDir = path.dirname(frontendDir)
const composeFile = path.join(projectDir, 'deploy', 'docker-compose.e2e.yml')
const composeArgs = ['compose', '-p', 'salary-sync-e2e', '-f', composeFile]
const backendJar = path.join(projectDir, 'backend', 'target', 'salary-tracker-backend.jar')

async function waitForBackend() {
  const deadline = Date.now() + 120_000
  while (Date.now() < deadline) {
    try {
      const response = await fetch('http://127.0.0.1:18080/api/health')
      if (response.ok) return
    } catch {
      // The container is still starting.
    }
    await new Promise(resolve => setTimeout(resolve, 1_000))
  }
  throw new Error('E2E backend did not become healthy within 120 seconds')
}

export default async function globalSetup() {
  if (process.env.SALARY_E2E_SKIP_COMPOSE !== '1') {
    if (existsSync(backendJar) && process.env.SALARY_E2E_SOURCE_BUILD !== '1') {
      execFileSync('docker', [
        'build', '-f', 'deploy/Dockerfile.backend.runtime',
        '-t', 'salary-sync-e2e-backend:latest', '.'
      ], { cwd: projectDir, stdio: 'inherit' })
      execFileSync('docker', [...composeArgs, 'up', '-d', '--no-build'], { cwd: projectDir, stdio: 'inherit' })
    } else {
      execFileSync('docker', [...composeArgs, 'up', '-d', '--build'], { cwd: projectDir, stdio: 'inherit' })
    }
  }
  try {
    await waitForBackend()
  } catch (error) {
    if (process.env.SALARY_E2E_SKIP_COMPOSE !== '1') {
      execFileSync('docker', [...composeArgs, 'down'], { cwd: projectDir, stdio: 'inherit' })
    }
    throw error
  }
}
