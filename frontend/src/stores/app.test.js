import test from 'node:test'
import assert from 'node:assert/strict'
import { createPinia, setActivePinia } from 'pinia'
import { api } from '../../packages/api-client/src/index.js'
import { useAppStore } from './app.js'
import { useLedgerStore } from './ledger.js'

function installBrowserGlobals(online) {
  const values = new Map()
  globalThis.localStorage = {
    getItem: key => values.get(key) ?? null,
    setItem: (key, value) => values.set(key, String(value)),
    removeItem: key => values.delete(key)
  }
  globalThis.window = {
    matchMedia: () => ({ matches: false }),
    addEventListener: () => {},
    dispatchEvent: () => {}
  }
  globalThis.document = {
    documentElement: {
      classList: { toggle: () => {} },
      style: { setProperty: () => {} }
    },
    querySelector: () => null,
    addEventListener: () => {},
    visibilityState: 'visible'
  }
  Object.defineProperty(globalThis, 'navigator', {
    configurable: true,
    value: { onLine: online }
  })
}

test('offline refresh restores the last local account scope instead of clearing ledger session', async t => {
  installBrowserGlobals(false)
  const originalAdapter = api.defaults.adapter
  api.defaults.adapter = async config => {
    const error = new Error(`offline: ${config.url}`)
    error.code = 'ERR_NETWORK'
    error.config = config
    throw error
  }
  t.after(async () => {
    api.defaults.adapter = originalAdapter
    await useLedgerStore().clearSession()
  })

  setActivePinia(createPinia())
  const firstSession = useAppStore()
  await firstSession.completeLogin({
    id: 91,
    username: 'offline-user',
    nickname: 'Offline User',
    authorities: ['ledger:write']
  })

  setActivePinia(createPinia())
  const restoredSession = useAppStore()
  await restoredSession.init()

  assert.equal(restoredSession.authRequired, false)
  assert.equal(restoredSession.accountScope, 'id-91')
  assert.equal(restoredSession.authUser?.username, 'offline-user')
  assert.equal(useLedgerStore().accountScope, 'id-91')
})

test('online refresh rejection clears the cached identity before a later offline start', async t => {
  installBrowserGlobals(true)
  const originalAdapter = api.defaults.adapter
  api.defaults.adapter = async config => {
    const error = new Error(`unauthorized: ${config.url}`)
    error.config = config
    error.response = { status: 401, data: { detail: 'refresh token expired' } }
    throw error
  }
  t.after(async () => {
    api.defaults.adapter = originalAdapter
    await useLedgerStore().clearSession()
  })

  setActivePinia(createPinia())
  await useAppStore().completeLogin({ id: 92, username: 'expired-user' })

  setActivePinia(createPinia())
  const rejectedSession = useAppStore()
  await rejectedSession.init()
  assert.equal(rejectedSession.authRequired, true)

  Object.defineProperty(globalThis, 'navigator', {
    configurable: true,
    value: { onLine: false }
  })
  setActivePinia(createPinia())
  const offlineRestart = useAppStore()
  await offlineRestart.init()

  assert.equal(offlineRestart.authRequired, true)
  assert.equal(offlineRestart.accountScope, '')
})
