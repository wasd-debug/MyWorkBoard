import axios from 'axios'

let accessToken = ''

export const api = axios.create({
  baseURL: '/api',
  timeout: 10000,
  withCredentials: true
})

export function setAccessToken(token) { accessToken = token || '' }
export function getAccessToken() { return accessToken }
export function clearAccessToken() { accessToken = '' }

api.interceptors.request.use(config => {
  if (accessToken) config.headers.Authorization = `Bearer ${accessToken}`
  return config
})

let refreshPromise = null
api.interceptors.response.use(response => response, async error => {
  const config = error.config
  if (error.response?.status !== 401 || config?._retry || config?.url?.includes('/v1/auth/')) throw error
  config._retry = true
  try {
    refreshPromise ||= api.post('/v1/auth/refresh', {}).then(response => {
      const data = unwrap(response)
      setAccessToken(data.accessToken)
      return data.accessToken
    }).finally(() => { refreshPromise = null })
    await refreshPromise
    return api(config)
  } catch (refreshError) {
    clearAccessToken()
    throw refreshError
  }
})

function unwrap(response) { return response?.data?.data ?? response?.data ?? {} }

export function apiLogin(payload) {
  return api.post('/v1/auth/login', payload).then(response => { const data = unwrap(response); setAccessToken(data.accessToken); return data })
}
export function apiRegister(payload) {
  return api.post('/v1/auth/register', payload).then(response => { const data = unwrap(response); setAccessToken(data.accessToken); return data })
}
export function apiRefresh() {
  return api.post('/v1/auth/refresh', {}).then(response => { const data = unwrap(response); setAccessToken(data.accessToken); return data })
}
export function apiLogout() { return api.post('/v1/auth/logout', {}).then(unwrap).finally(clearAccessToken) }

export function apiGetWorktimeSnapshot() { return api.get('/v1/worktime/snapshot').then(unwrap) }
export function apiPutWorktimeSnapshot(payload) { return api.put('/v1/worktime/snapshot', payload, { timeout: 15000 }).then(unwrap) }
export function apiGetWorktimeSettings() { return api.get('/v1/worktime/settings').then(unwrap) }
export function apiPutWorktimeSettings(payload, revision) {
  return api.put('/v1/worktime/settings', payload, { headers: revision ? { 'If-Match': String(revision) } : undefined }).then(unwrap)
}
export function apiListWorktimeRecords(params = {}) { return api.get('/v1/worktime/records', { params }).then(unwrap) }
export function apiCreateWorktimeRecord(payload, idempotencyKey) {
  return api.post('/v1/worktime/records', payload, { headers: idempotencyKey ? { 'Idempotency-Key': idempotencyKey } : undefined }).then(unwrap)
}
export function apiUpdateWorktimeRecord(id, payload, revision) {
  return api.patch(`/v1/worktime/records/${id}`, payload, { headers: revision ? { 'If-Match': String(revision) } : undefined }).then(unwrap)
}
export function apiDeleteWorktimeRecord(id, revision) {
  return api.delete(`/v1/worktime/records/${id}`, { headers: revision ? { 'If-Match': String(revision) } : undefined }).then(unwrap)
}
export function apiListAuditLogs(limit = 50) { return api.get('/v1/audit/logs', { params: { limit } }).then(unwrap) }
export function apiGetHolidays(year) { return api.get('/holidays', { params: { year }, timeout: 20000 }).then(response => response.data) }

export function apiListLedgerAccounts() { return api.get('/v1/ledger/accounts').then(unwrap) }
export function apiCreateLedgerAccount(payload) { return api.post('/v1/ledger/accounts', payload).then(unwrap) }
export function apiUpdateLedgerAccount(id, payload, revision) { return api.patch(`/v1/ledger/accounts/${id}`, payload, { headers: revision ? { 'If-Match': String(revision) } : undefined }).then(unwrap) }
export function apiDeleteLedgerAccount(id, revision) { return api.delete(`/v1/ledger/accounts/${id}`, { headers: revision ? { 'If-Match': String(revision) } : undefined }).then(unwrap) }
export function apiListLedgerCategories() { return api.get('/v1/ledger/categories').then(unwrap) }
export function apiCreateLedgerCategory(payload) { return api.post('/v1/ledger/categories', payload).then(unwrap) }
export function apiUpdateLedgerCategory(id, payload, revision) { return api.patch(`/v1/ledger/categories/${id}`, payload, { headers: revision ? { 'If-Match': String(revision) } : undefined }).then(unwrap) }
export function apiDeleteLedgerCategory(id, revision) { return api.delete(`/v1/ledger/categories/${id}`, { headers: revision ? { 'If-Match': String(revision) } : undefined }).then(unwrap) }
export function apiListLedgerTransactions(params = {}) { return api.get('/v1/ledger/transactions', { params }).then(unwrap) }
export function apiCreateLedgerTransaction(payload, idempotencyKey) { return api.post('/v1/ledger/transactions', payload, { headers: idempotencyKey ? { 'Idempotency-Key': idempotencyKey } : undefined }).then(unwrap) }
export function apiUpdateLedgerTransaction(id, payload, revision) { return api.patch(`/v1/ledger/transactions/${id}`, payload, { headers: revision ? { 'If-Match': String(revision) } : undefined }).then(unwrap) }
export function apiDeleteLedgerTransaction(id, revision) { return api.delete(`/v1/ledger/transactions/${id}`, { headers: revision ? { 'If-Match': String(revision) } : undefined }).then(unwrap) }
export function apiGetLedgerReports(params = {}) { return api.get('/v1/ledger/reports', { params }).then(unwrap) }
export function apiListLedgerBudgets(month) { return api.get('/v1/ledger/budgets', { params: month ? { month } : undefined }).then(unwrap) }
export function apiCreateLedgerBudget(payload) { return api.post('/v1/ledger/budgets', payload).then(unwrap) }
export function apiDeleteLedgerBudget(id) { return api.delete(`/v1/ledger/budgets/${id}`).then(unwrap) }
export function apiListRecurringBills() { return api.get('/v1/ledger/recurring').then(unwrap) }
export function apiCreateRecurringBill(payload) { return api.post('/v1/ledger/recurring', payload).then(unwrap) }
export function apiUpdateRecurringBill(id, payload) { return api.put(`/v1/ledger/recurring/${id}`, payload).then(unwrap) }
export function apiDeleteRecurringBill(id) { return api.delete(`/v1/ledger/recurring/${id}`).then(unwrap) }
export function apiRunRecurringBills() { return api.post('/v1/ledger/recurring/run').then(unwrap) }
export function apiLedgerAiPreview(text) { return api.post('/v1/ledger/ai/preview', { text }).then(unwrap) }
export function apiLedgerAiConfirm(payload) { return api.post('/v1/ledger/ai/confirm', payload).then(unwrap) }
export function apiPushLedgerSync(operations) { return api.post('/v1/ledger/sync/push', operations).then(unwrap) }
export function apiPullLedgerSync(cursor = 0) { return api.get('/v1/ledger/sync/pull', { params: { cursor } }).then(unwrap) }
export function apiImportLedgerCsv(file) { const body = new FormData(); body.append('file', file); return api.post('/v1/ledger/import', body).then(unwrap) }
export function apiImportLedgerExcel(file) { const body = new FormData(); body.append('file', file); return api.post('/v1/ledger/import/excel', body).then(unwrap) }

export function apiGetData() { return apiGetWorktimeSnapshot() }
export function apiPutAll(payload) { return apiPutWorktimeSnapshot(payload) }
