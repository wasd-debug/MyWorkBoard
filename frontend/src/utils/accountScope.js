const ACTIVE_SCOPE_KEY = 'salary-tracker-active-user-scope'

export function accountScopeFor(user) {
  if (user?.id !== undefined && user?.id !== null && String(user.id).trim()) return `id-${String(user.id).trim()}`
  const username = String(user?.username || '').trim().toLocaleLowerCase()
  return username ? `username-${encodeURIComponent(username)}` : ''
}

export function scopedStorageKey(base, scope) {
  return scope ? `${base}:${scope}` : ''
}

export function ledgerDatabaseName(scope) {
  return scope ? `salary-tracker-sync-v2:${scope}` : 'salary-tracker-sync-v2:anonymous'
}

export function setActiveAccountScope(scope) {
  if (typeof localStorage === 'undefined') return
  if (scope) localStorage.setItem(ACTIVE_SCOPE_KEY, scope)
  else localStorage.removeItem(ACTIVE_SCOPE_KEY)
}

export function activeAccountScope() {
  if (typeof localStorage === 'undefined') return ''
  return localStorage.getItem(ACTIVE_SCOPE_KEY) || ''
}

export function currentLedgerBookStorageKey(scope = activeAccountScope()) {
  return scopedStorageKey('ledger-current-book', scope)
}

export function readCurrentLedgerBookId() {
  const key = currentLedgerBookStorageKey()
  return key && typeof localStorage !== 'undefined' ? localStorage.getItem(key) || '' : ''
}
