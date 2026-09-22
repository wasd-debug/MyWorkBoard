import { AuthenticationApi } from './generated/api/authentication-api.ts'
import { AuditApi } from './generated/api/audit-api.ts'
import { HolidaysApi } from './generated/api/holidays-api.ts'
import { LedgerApi } from './generated/api/ledger-api.ts'
import { WorktimeApi } from './generated/api/worktime-api.ts'
import { api, clearAccessToken, generatedConfiguration, getAccessToken, setAccessToken } from './transport.js'

const auth = new AuthenticationApi(generatedConfiguration, '', api)
const audit = new AuditApi(generatedConfiguration, '', api)
const holidays = new HolidaysApi(generatedConfiguration, '', api)
const ledger = new LedgerApi(generatedConfiguration, '', api)
const worktime = new WorktimeApi(generatedConfiguration, '', api)
const data = response => response?.data?.data
const revision = value => value === undefined || value === null || value === '' ? undefined : String(value)

export { api, clearAccessToken, getAccessToken, setAccessToken }
export * from './generated/models/index.ts'

export async function apiChatWithAssistant(message, sessionId) {
  return data(await api.post('/api/v1/ai/chat', { sessionId, message }, { timeout: 60_000 }))
}

export async function apiLogin(credentials) { const result = data(await auth.login({ credentials })); setAccessToken(result?.accessToken); return result }
export async function apiRegister(credentials) { const result = data(await auth.register({ credentials })); setAccessToken(result?.accessToken); return result }
export async function apiRefresh() { const result = data(await auth.refreshAccessToken({ refreshRequest: {} })); setAccessToken(result?.accessToken); return result }
export async function apiLogout() { try { return data(await auth.logout()) } finally { clearAccessToken() } }

export const getWorktimeSettings = async () => data(await worktime.getWorktimeSettings())
export const apiPutWorktimeSettings = async (payload, currentRevision) => data(await worktime.updateWorktimeSettings({ worktimeSettingsUpdate: payload, ifMatch: revision(currentRevision) }))
export const apiListWorktimeRecords = async (params = {}) => data(await worktime.listWorktimeRecords(params))
export const apiCreateWorktimeRecord = async (payload, idempotencyKey) => data(await worktime.createWorktimeRecord({ worktimeRecordCommand: payload, idempotencyKey }))
export const apiUpdateWorktimeRecord = async (id, payload, currentRevision) => data(await worktime.updateWorktimeRecord({ id, worktimeRecordCommand: payload, ifMatch: revision(currentRevision) }))
export const apiDeleteWorktimeRecord = async (id, currentRevision) => data(await worktime.deleteWorktimeRecord({ id, ifMatch: revision(currentRevision) }))
export const apiListAuditLogs = async (limit = 50) => data(await audit.listAuditLogs({ limit }))
export const getHolidays = async year => (await holidays.getHolidays({ year: String(year) }, { timeout: 20_000 })).data

export const apiListLedgerBooks = async () => data(await ledger.listLedgerBooks())
export const apiCreateLedgerBook = async payload => data(await ledger.createLedgerBook({ bookCommand: payload }))
export const apiUpdateLedgerBook = async (bookId, payload, currentRevision) => data(await ledger.updateLedgerBook({ bookId, bookCommand: payload, ifMatch: revision(currentRevision) }))
export const apiDeleteLedgerBook = async (bookId, currentRevision) => data(await ledger.deleteLedgerBook({ bookId, ifMatch: revision(currentRevision) }))
export const apiListLedgerAccounts = async (bookId, includeHidden = false) => data(await ledger.listLedgerAccounts({ bookId, includeHidden }))
export const apiCreateLedgerAccount = async (bookId, payload, idempotencyKey) => data(await ledger.createLedgerAccount({ bookId, accountCommand: payload, idempotencyKey }))
export const apiUpdateLedgerAccount = async (bookId, id, payload, currentRevision, idempotencyKey) => data(await ledger.updateLedgerAccount({ bookId, id, accountCommand: payload, ifMatch: revision(currentRevision), idempotencyKey }))
export const apiDeleteLedgerAccount = async (bookId, id, currentRevision, idempotencyKey) => data(await ledger.deleteLedgerAccount({ bookId, id, ifMatch: revision(currentRevision), idempotencyKey }))
export const apiListLedgerCategories = async (bookId, includeHidden = false) => data(await ledger.listLedgerCategories({ bookId, includeHidden }))
export const apiCreateLedgerCategory = async (bookId, payload, idempotencyKey) => data(await ledger.createLedgerCategory({ bookId, categoryCommand: payload, idempotencyKey }))
export const apiUpdateLedgerCategory = async (bookId, id, payload, currentRevision, idempotencyKey) => data(await ledger.updateLedgerCategory({ bookId, id, categoryCommand: payload, ifMatch: revision(currentRevision), idempotencyKey }))
export const apiDeleteLedgerCategory = async (bookId, id, currentRevision, idempotencyKey) => data(await ledger.deleteLedgerCategory({ bookId, id, ifMatch: revision(currentRevision), idempotencyKey }))
export const apiListLedgerNamed = async (bookId, type, includeHidden = false) => data(await ledger.listLedgerNamedResources({ bookId, type, includeHidden }))
export const apiCreateLedgerNamed = async (bookId, type, payload, idempotencyKey) => data(await ledger.createLedgerNamedResource({ bookId, type, namedResourceCommand: payload, idempotencyKey }))
export const apiUpdateLedgerNamed = async (bookId, type, id, payload, currentRevision, idempotencyKey) => data(await ledger.updateLedgerNamedResource({ bookId, type, id, namedResourceCommand: payload, ifMatch: revision(currentRevision), idempotencyKey }))
export const apiDeleteLedgerNamed = async (bookId, type, id, currentRevision, idempotencyKey) => data(await ledger.deleteLedgerNamedResource({ bookId, type, id, ifMatch: revision(currentRevision), idempotencyKey }))
export const apiListLedgerMembers = async bookId => data(await ledger.listLedgerMembers({ bookId }))
export const apiCreateLedgerMember = async (bookId, payload, idempotencyKey) => data(await ledger.createLedgerMember({ bookId, memberCommand: payload, idempotencyKey }))
export const apiUpdateLedgerMember = async (bookId, id, payload, currentRevision, idempotencyKey) => data(await ledger.updateLedgerMember({ bookId, id, memberCommand: payload, ifMatch: revision(currentRevision), idempotencyKey }))
export const apiDeleteLedgerMember = async (bookId, id, currentRevision, idempotencyKey) => data(await ledger.deleteLedgerMember({ bookId, id, ifMatch: revision(currentRevision), idempotencyKey }))
export const apiListLedgerRoles = async bookId => data(await ledger.listLedgerRoles({ bookId }))
export const apiCreateLedgerRole = async (bookId, payload, idempotencyKey) => data(await ledger.createLedgerRole({ bookId, roleCommand: payload, idempotencyKey }))
export const apiUpdateLedgerRole = async (bookId, id, payload, currentRevision, idempotencyKey) => data(await ledger.updateLedgerRole({ bookId, id, roleCommand: payload, ifMatch: revision(currentRevision), idempotencyKey }))
export const apiDeleteLedgerRole = async (bookId, id, currentRevision, idempotencyKey) => data(await ledger.deleteLedgerRole({ bookId, id, ifMatch: revision(currentRevision), idempotencyKey }))

export const apiListLedgerTransactions = async (bookId, params = {}) => data(await ledger.listLedgerTransactions({ bookId, ...params }))
export const apiListRecentLedgerTransactions = async (bookId, limit = 10) => data(await ledger.listRecentLedgerTransactions({ bookId, limit }))
export const apiCreateLedgerTransaction = async (bookId, payload, idempotencyKey) => data(await ledger.createLedgerTransaction({ bookId, transactionCommand: payload, idempotencyKey }))
export const apiUpdateLedgerTransaction = async (bookId, id, payload, currentRevision, idempotencyKey) => data(await ledger.updateLedgerTransaction({ bookId, id, transactionCommand: payload, ifMatch: revision(currentRevision), idempotencyKey }))
export const apiDeleteLedgerTransaction = async (bookId, id, currentRevision, idempotencyKey) => data(await ledger.deleteLedgerTransaction({ bookId, id, ifMatch: revision(currentRevision), idempotencyKey }))
export const apiCopyLedgerTransaction = async (bookId, id, payload = {}) => data(await ledger.copyLedgerTransaction({ bookId, id, copyTransactionCommand: payload }))
export const getLedgerTransactionHistory = async (bookId, id) => data(await ledger.getLedgerTransactionHistory({ bookId, id }))
export const getLedgerReports = async (bookId, params = {}) => data(await ledger.getLedgerOverview({ bookId, ...params }))
export const apiListLedgerBudgets = async (bookId, month) => data(await ledger.listLedgerBudgets({ bookId, month }))
export const apiCreateLedgerBudget = async (bookId, payload, idempotencyKey) => data(await ledger.upsertLedgerBudget({ bookId, budgetCommand: payload, idempotencyKey }))
export const apiDeleteLedgerBudget = async (bookId, id, currentRevision, idempotencyKey) => data(await ledger.deleteLedgerBudget({ bookId, id, ifMatch: revision(currentRevision), idempotencyKey }))
export const apiListLedgerRecycle = async (bookId, params = {}) => data(await ledger.listLedgerRecycle({ bookId, ...params }))
export const apiRestoreLedgerRecycle = async (bookId, type, id, idempotencyKey) => data(await ledger.restoreLedgerRecycleItem({ bookId, type, id, idempotencyKey }))
export const apiPurgeLedgerRecycle = async (bookId, type, id) => data(await ledger.purgeLedgerRecycleItem({ bookId, type, id }))
export const apiListLedgerAuditLogs = async (bookId, params = {}) => data(await ledger.listLedgerAuditLogs({ bookId, ...params }))
export const apiClearLedgerAuditLogs = async (bookId, ids = []) => data(await ledger.clearLedgerAuditLogs({ bookId, auditClearCommand: { ids } }))
export const apiMaterializeLedger = async (bookId, month) => data(await ledger.materializeLedgerBalances({ bookId, month }))
export const apiListLedgerScheduledTasks = async (bookId, params = {}) => data(await ledger.listLedgerScheduledTasks({ bookId, ...params }))
export const apiCreateLedgerScheduledTask = async (bookId, payload) => data(await ledger.createLedgerScheduledTask({ bookId, scheduledTaskCommand: payload }))
export const apiUpdateLedgerScheduledTask = async (bookId, id, payload, currentRevision) => data(await ledger.updateLedgerScheduledTask({ bookId, id, scheduledTaskCommand: payload, ifMatch: revision(currentRevision) }))
export const apiDeleteLedgerScheduledTask = async (bookId, id) => data(await ledger.deleteLedgerScheduledTask({ bookId, id }))
export const apiRunLedgerScheduledTask = async (bookId, id) => data(await ledger.runLedgerScheduledTask({ bookId, id }))
export const apiLedgerAiPreview = async (bookId, text) => data(await ledger.previewLedgerAiText({ bookId, aiPreviewCommand: { text } }))
export const apiLedgerMonthlyAnalysis = async (bookId, payload) => data(await ledger.analyzeLedgerMonth({ bookId, monthlyAnalysisCommand: payload }, { timeout: 30_000 }))
export const apiLedgerAiImagePreview = async (bookId, file) => data(await ledger.previewLedgerAiImage({ bookId, file }))
export const apiLedgerAiConfirm = async (bookId, draftId, transactions, idempotencyKey) => data(await ledger.confirmLedgerAiDraft({ bookId, draftId, aiConfirmCommand: { transactions }, idempotencyKey }))
export const apiPushLedgerSync = async (bookId, operations) => data(await ledger.pushLedgerSync({ bookId, syncOperationRequest: operations }))
export const apiPullLedgerSync = async (bookId, cursor = 0) => data(await ledger.pullLedgerSync({ bookId, cursor }))

export async function apiImportLedgerPreview(bookId, file, template = 'AUTO', onUploadProgress) {
  return data(await ledger.previewLedgerImport({ bookId, file, template }, {
    timeout: 900_000,
    onUploadProgress: event => {
      if (!onUploadProgress) return
      const total = Number(event.total || file.size || 0)
      onUploadProgress(total ? Math.min(100, Math.round(Number(event.loaded || 0) / total * 100)) : 0)
    }
  }))
}

export const apiImportLedgerConfirm = async (bookId, batchId) => data(await ledger.confirmLedgerImport({ bookId, batchId }, { timeout: 600_000 }))
export async function apiDownloadLedgerExport(bookId, format = 'xlsx', params = {}) {
  return (await ledger.exportLedgerTransactions({ bookId, format, ...params }, { responseType: 'blob', timeout: 600_000 })).data
}
