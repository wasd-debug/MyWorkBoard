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

const activeBookId = () => (typeof localStorage !== 'undefined' && localStorage.getItem('ledger-current-book')) || 'default'
const bookPath = (bookId, suffix = '') => `/v1/ledger/books/${bookId || activeBookId()}${suffix}`
const revisionHeaders = (revision, opId) => ({
  ...(revision !== undefined && revision !== null && revision !== '' ? { 'If-Match': String(revision) } : {}),
  ...(opId ? { 'Idempotency-Key': opId } : {})
})

export function apiListLedgerBooks() { return api.get('/v1/ledger/books').then(unwrap) }
export function apiCreateLedgerBook(payload) { return api.post('/v1/ledger/books', payload).then(unwrap) }
export function apiUpdateLedgerBook(bookId, payload, revision) { return api.patch(bookPath(bookId), payload, { headers: revisionHeaders(revision) }).then(unwrap) }
export function apiDeleteLedgerBook(bookId, revision) { return api.delete(bookPath(bookId), { headers: revisionHeaders(revision) }).then(unwrap) }

export function apiListLedgerAccounts(bookId, includeHidden = false) { return api.get(bookPath(bookId, '/accounts'), { params: { includeHidden } }).then(unwrap) }
export function apiCreateLedgerAccount(bookId, payload, opId) {
  if (typeof bookId === 'object') { opId = payload; payload = bookId; bookId = null }
  return api.post(bookPath(bookId, '/accounts'), payload, { headers: revisionHeaders(null, opId) }).then(unwrap)
}
export function apiUpdateLedgerAccount(bookId, id, payload, revision, opId) {
  if (typeof id === 'object') { opId = revision; revision = payload; payload = id; id = bookId; bookId = null }
  return api.patch(bookPath(bookId, `/accounts/${id}`), payload, { headers: revisionHeaders(revision, opId) }).then(unwrap)
}
export function apiDeleteLedgerAccount(bookId, id, revision, opId) {
  if (typeof id !== 'string' || !bookId || bookId === 'default' && !String(id).includes('-')) {
    opId = revision; revision = id; id = bookId; bookId = null
  }
  return api.delete(bookPath(bookId, `/accounts/${id}`), { headers: revisionHeaders(revision, opId) }).then(unwrap)
}
export function apiListLedgerCategories(bookId, includeHidden = false) { return api.get(bookPath(bookId, '/categories'), { params: { includeHidden } }).then(unwrap) }
export function apiCreateLedgerCategory(bookId, payload, opId) {
  if (typeof bookId === 'object') { opId = payload; payload = bookId; bookId = null }
  return api.post(bookPath(bookId, '/categories'), payload, { headers: revisionHeaders(null, opId) }).then(unwrap)
}
export function apiUpdateLedgerCategory(bookId, id, payload, revision, opId) {
  if (typeof id === 'object') { opId = revision; revision = payload; payload = id; id = bookId; bookId = null }
  return api.patch(bookPath(bookId, `/categories/${id}`), payload, { headers: revisionHeaders(revision, opId) }).then(unwrap)
}
export function apiDeleteLedgerCategory(bookId, id, revision, opId) {
  if (typeof id !== 'string' || !bookId || bookId === 'default' && !String(id).includes('-')) {
    opId = revision; revision = id; id = bookId; bookId = null
  }
  return api.delete(bookPath(bookId, `/categories/${id}`), { headers: revisionHeaders(revision, opId) }).then(unwrap)
}

export function apiListLedgerNamed(bookId, type, includeHidden = false) { return api.get(bookPath(bookId, `/${type}`), { params: { includeHidden } }).then(unwrap) }
export function apiCreateLedgerNamed(bookId, type, payload, opId) { return api.post(bookPath(bookId, `/${type}`), payload, { headers: revisionHeaders(null, opId) }).then(unwrap) }
export function apiUpdateLedgerNamed(bookId, type, id, payload, revision, opId) { return api.patch(bookPath(bookId, `/${type}/${id}`), payload, { headers: revisionHeaders(revision, opId) }).then(unwrap) }
export function apiDeleteLedgerNamed(bookId, type, id, revision, opId) { return api.delete(bookPath(bookId, `/${type}/${id}`), { headers: revisionHeaders(revision, opId) }).then(unwrap) }

export function apiListLedgerMembers(bookId) { return api.get(bookPath(bookId, '/members')).then(unwrap) }
export function apiCreateLedgerMember(bookId, payload, opId) { return api.post(bookPath(bookId, '/members'), payload, { headers: revisionHeaders(null, opId) }).then(unwrap) }
export function apiUpdateLedgerMember(bookId, id, payload, revision, opId) { return api.patch(bookPath(bookId, `/members/${id}`), payload, { headers: revisionHeaders(revision, opId) }).then(unwrap) }
export function apiDeleteLedgerMember(bookId, id, revision, opId) { return api.delete(bookPath(bookId, `/members/${id}`), { headers: revisionHeaders(revision, opId) }).then(unwrap) }
export function apiListLedgerRoles(bookId) { return api.get(bookPath(bookId, '/roles')).then(unwrap) }
export function apiCreateLedgerRole(bookId, payload, opId) { return api.post(bookPath(bookId, '/roles'), payload, { headers: revisionHeaders(null, opId) }).then(unwrap) }
export function apiUpdateLedgerRole(bookId, id, payload, revision, opId) { return api.patch(bookPath(bookId, `/roles/${id}`), payload, { headers: revisionHeaders(revision, opId) }).then(unwrap) }
export function apiDeleteLedgerRole(bookId, id, revision, opId) { return api.delete(bookPath(bookId, `/roles/${id}`), { headers: revisionHeaders(revision, opId) }).then(unwrap) }

export function apiListLedgerTransactions(bookId, params = {}) {
  const legacy = typeof bookId === 'object' || bookId == null
  if (legacy) { params = bookId || {}; bookId = null }
  return api.get(bookPath(bookId, '/transactions'), { params }).then(response => {
    const data = unwrap(response)
    return legacy ? (data.items || []) : data
  })
}
export function apiListRecentLedgerTransactions(bookId, limit = 10) { return api.get(bookPath(bookId, '/transactions/recent'), { params: { limit } }).then(unwrap) }
export function apiCreateLedgerTransaction(bookId, payload, opId) {
  if (typeof bookId === 'object') { opId = payload; payload = bookId; bookId = null }
  return api.post(bookPath(bookId, '/transactions'), payload, { headers: revisionHeaders(null, opId) }).then(unwrap)
}
export function apiUpdateLedgerTransaction(bookId, id, payload, revision, opId) {
  if (typeof id === 'object') { opId = revision; revision = payload; payload = id; id = bookId; bookId = null }
  return api.patch(bookPath(bookId, `/transactions/${id}`), payload, { headers: revisionHeaders(revision, opId) }).then(unwrap)
}
export function apiDeleteLedgerTransaction(bookId, id, revision, opId) {
  if (typeof id !== 'string' || !bookId || bookId === 'default' && !String(id).includes('-')) {
    opId = revision; revision = id; id = bookId; bookId = null
  }
  return api.delete(bookPath(bookId, `/transactions/${id}`), { headers: revisionHeaders(revision, opId) }).then(unwrap)
}
export function apiCopyLedgerTransaction(bookId, id, payload = {}) { return api.post(bookPath(bookId, `/transactions/${id}/copy`), payload).then(unwrap) }
export function apiGetLedgerTransactionHistory(bookId, id) { return api.get(bookPath(bookId, `/transactions/${id}/history`)).then(unwrap) }
export function apiGetLedgerReports(bookId, params = {}) {
  if (typeof bookId === 'object') { params = bookId; bookId = null }
  return api.get(bookPath(bookId, '/overview'), { params }).then(unwrap)
}
export function apiListLedgerBudgets(bookId, month) { return api.get(bookPath(bookId, '/budgets'), { params: month ? { month } : undefined }).then(unwrap) }
export function apiCreateLedgerBudget(bookId, payload, opId) { return api.put(bookPath(bookId, '/budgets'), payload, { headers: revisionHeaders(null, opId) }).then(unwrap) }
export function apiDeleteLedgerBudget(bookId, id, revision, opId) { return api.delete(bookPath(bookId, `/budgets/${id}`), { headers: revisionHeaders(revision, opId) }).then(unwrap) }

export function apiListLedgerRecycle(bookId, params = {}) { return api.get(bookPath(bookId, '/recycle'), { params }).then(unwrap) }
export function apiRestoreLedgerRecycle(bookId, type, id, opId) { return api.post(bookPath(bookId, `/recycle/${type}/${id}/restore`), {}, { headers: revisionHeaders(null, opId) }).then(unwrap) }
export function apiPurgeLedgerRecycle(bookId, type, id) { return api.delete(bookPath(bookId, `/recycle/${type}/${id}`)).then(unwrap) }
export function apiListLedgerAuditLogs(bookId, params = {}) { return api.get(bookPath(bookId, '/audit-logs'), { params }).then(unwrap) }
export function apiClearLedgerAuditLogs(bookId, ids = []) { return api.delete(bookPath(bookId, '/audit-logs'), { data: { ids } }).then(unwrap) }
export function apiMaterializeLedger(bookId, month) { return api.post(bookPath(bookId, '/materialize'), {}, { params: month ? { month } : undefined }).then(unwrap) }

export function apiLedgerAiPreview(bookId, text) {
  if (text === undefined) { text = bookId; bookId = null }
  return api.post(bookPath(bookId, '/ai/preview'), { text }).then(unwrap)
}
export function apiLedgerAiImagePreview(bookId, file) { const body = new FormData(); body.append('file', file); return api.post(bookPath(bookId, '/ai/image-preview'), body).then(unwrap) }
export function apiLedgerAiConfirm(bookId, draftId, transactions, opId) { return api.post(bookPath(bookId, `/ai/${draftId}/confirm`), { transactions }, { headers: revisionHeaders(null, opId) }).then(unwrap) }
export function apiPushLedgerSync(bookId, operations) { return api.post(bookPath(bookId, '/sync/push'), operations).then(unwrap) }
export function apiPullLedgerSync(bookId, cursor = 0) { return api.get(bookPath(bookId, '/sync/pull'), { params: { cursor } }).then(unwrap) }
export function apiImportLedgerPreview(bookId, file, template = 'AUTO') { const body = new FormData(); body.append('file', file); return api.post(bookPath(bookId, '/imports/preview'), body, { params: { template } }).then(unwrap) }
export function apiImportLedgerConfirm(bookId, batchId) { return api.post(bookPath(bookId, `/imports/${batchId}/confirm`)).then(unwrap) }
export function apiImportLedgerCsv(file) {
  return apiImportLedgerPreview(activeBookId(), file, 'AUTO').then(preview =>
    apiImportLedgerConfirm(activeBookId(), preview.batchId))
}
export function apiImportLedgerExcel(file) { return apiImportLedgerCsv(file) }
export function ledgerExportUrl(bookId, format = 'csv', params = {}) {
  const query = new URLSearchParams({ format, ...Object.fromEntries(Object.entries(params).filter(([, value]) => value)) })
  return `/api${bookPath(bookId, '/export')}?${query}`
}

export function apiGetData() { return apiGetWorktimeSnapshot() }
export function apiPutAll(payload) { return apiPutWorktimeSnapshot(payload) }
