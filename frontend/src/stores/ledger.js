import { defineStore } from 'pinia'
import { createLedgerSyncEngine } from '../../packages/sync-engine/src/index.js'
import { accountScopeFor, currentLedgerBookStorageKey, ledgerDatabaseName, setActiveAccountScope } from '../utils/accountScope.js'
import { createClientId } from '../utils/clientId.js'
import {
  apiCreateLedgerBook,
  apiCreateLedgerMember,
  apiCreateLedgerRole,
  apiCreateLedgerScheduledTask,
  apiDeleteLedgerBook,
  apiDeleteLedgerMember,
  apiDeleteLedgerRole,
  apiDeleteLedgerScheduledTask,
  apiImportLedgerConfirm,
  apiImportLedgerPreview,
  apiLedgerAiConfirm,
  apiListLedgerAccounts,
  apiListLedgerBooks,
  apiListLedgerBudgets,
  apiListLedgerCategories,
  apiListLedgerMembers,
  apiListLedgerNamed,
  apiListLedgerRoles,
  apiListLedgerTransactions,
  apiPurgeLedgerRecycle,
  apiPullLedgerSync,
  apiPushLedgerSync,
  apiRestoreLedgerRecycle,
  apiRunLedgerScheduledTask,
  apiUpdateLedgerBook,
  apiUpdateLedgerMember,
  apiUpdateLedgerRole,
  apiUpdateLedgerScheduledTask
} from '../api/index.js'

const syncTransport = {
  push: (bookId, operations) => apiPushLedgerSync(bookId, operations),
  pull: (bookId, cursor) => apiPullLedgerSync(bookId, cursor)
}

let engine = createLedgerSyncEngine({ dbName: ledgerDatabaseName(''), transport: syncTransport })

let listenersInstalled = false
let syncTimer
let activeSyncPromise
let sessionEpoch = 0
const OFFLINE_RESOURCE_TYPES = new Set(['account', 'category', 'merchant', 'project', 'budget'])

function onlineRequired(message) {
  const error = new Error(message)
  error.code = 'ONLINE_REQUIRED'
  return error
}

function clearLedgerState(store) {
  Object.assign(store, {
    ready: false,
    loading: false,
    syncing: false,
    books: [],
    currentBookId: '',
    accounts: [],
    categories: [],
    merchants: [],
    members: [],
    projects: [],
    roles: [],
    budgets: [],
    transactions: [],
    conflicts: [],
    rejected: [],
    pending: 0,
    syncError: ''
  })
}

export const useLedgerStore = defineStore('ledger', {
  state: () => ({
    ready: false,
    accountScope: '',
    loading: false,
    syncing: false,
    online: typeof navigator === 'undefined' ? true : navigator.onLine,
    books: [],
    currentBookId: '',
    accounts: [],
    categories: [],
    merchants: [],
    members: [],
    projects: [],
    roles: [],
    budgets: [],
    transactions: [],
    conflicts: [],
    rejected: [],
    pending: 0,
    syncError: ''
  }),
  getters: {
    currentBook: state => state.books.find(book => String(book.id) === String(state.currentBookId)),
    visibleAccounts: state => state.accounts.filter(item => !item.hidden && !item.deleted),
    visibleCategories: state => state.categories.filter(item => {
      if (item.hidden || item.deleted) return false
      if (!item.parentId) return true
      const parent = state.categories.find(parentItem => String(parentItem.id) === String(item.parentId))
      return Boolean(parent && !parent.hidden && !parent.deleted)
    }),
    visibleMerchants: state => state.merchants.filter(item => !item.hidden && !item.deleted),
    visibleProjects: state => state.projects.filter(item => !item.hidden && !item.deleted),
    activeMembers: state => state.members.filter(item => !item.deleted),
    secondaryCategories: state => state.categories.filter(item => {
      if (!item.parentId || item.hidden || item.deleted) return false
      const parent = state.categories.find(parentItem => String(parentItem.id) === String(item.parentId))
      return Boolean(parent && !parent.hidden && !parent.deleted)
    })
  },
  actions: {
    async switchUser(user) {
      const nextScope = accountScopeFor(user)
      if (nextScope && nextScope === this.accountScope) return
      const previousEngine = engine
      sessionEpoch += 1
      clearTimeout(syncTimer)
      syncTimer = undefined
      activeSyncPromise = null
      clearLedgerState(this)
      this.accountScope = nextScope
      setActiveAccountScope(nextScope)
      if (typeof localStorage !== 'undefined') localStorage.removeItem('ledger-current-book')
      engine = createLedgerSyncEngine({ dbName: ledgerDatabaseName(nextScope), transport: syncTransport })
      await previousEngine.close()
    },

    async clearSession() {
      await this.switchUser(null)
    },

    async init({ waitForRemote = true } = {}) {
      if (this.ready) return
      if (!this.accountScope) {
        clearLedgerState(this)
        this.ready = true
        return
      }
      const epoch = sessionEpoch
      const scopedEngine = engine
      this.loading = true
      this.installListeners()
      try {
        const books = await scopedEngine.list('book')
        if (epoch !== sessionEpoch || scopedEngine !== engine) return
        this.books = books
        const storageKey = currentLedgerBookStorageKey(this.accountScope)
        this.currentBookId = (storageKey && localStorage.getItem(storageKey)) || this.books[0]?.id || ''
        if (this.currentBookId) await this.hydrate()
        if (this.online) {
          const refresh = this.refreshServer()
          if (waitForRemote) await refresh
        }
      } finally {
        if (epoch === sessionEpoch && scopedEngine === engine) {
          this.loading = false
          this.ready = true
        }
      }
    },

    installListeners() {
      if (listenersInstalled || typeof window === 'undefined') return
      listenersInstalled = true
      window.addEventListener('online', () => {
        this.online = true
        this.refreshServer()
      })
      window.addEventListener('offline', () => { this.online = false })
      document.addEventListener('visibilitychange', () => {
        if (document.visibilityState === 'visible' && navigator.onLine) this.refreshServer()
      })
    },

    async selectBook(bookId) {
      if (!bookId || String(bookId) === String(this.currentBookId)) return
      if (!this.books.some(book => String(book.id) === String(bookId))) throw new Error('该账本已不可访问，请刷新列表')
      const previousBookId = this.currentBookId
      this.currentBookId = String(bookId)
      const storageKey = currentLedgerBookStorageKey(this.accountScope)
      if (storageKey) localStorage.setItem(storageKey, this.currentBookId)
      try {
        await this.hydrate()
        if (this.online) {
          await this.refreshResources()
          await this.syncNow()
        }
      } catch (error) {
        this.currentBookId = previousBookId
        if (storageKey) localStorage.setItem(storageKey, previousBookId)
        await this.hydrate()
        throw error
      }
    },

    async createBook(payload) {
      if (!this.online) throw new Error('创建账本需要联网')
      const epoch = sessionEpoch
      const scopedEngine = engine
      const book = await apiCreateLedgerBook(payload)
      if (epoch !== sessionEpoch || scopedEngine !== engine) return book
      await scopedEngine.put('book', book, { bookId: book.id, recordOp: false })
      this.books = await scopedEngine.list('book')
      await this.selectBook(book.id)
      return book
    },

    async refreshServer() {
      if (!this.accountScope || !this.online) return
      const epoch = sessionEpoch
      try {
        await this.refreshBooks()
        if (epoch !== sessionEpoch) return
        if (!this.books.length) {
          this.currentBookId = ''
          return
        }
        let bookChanged = false
        if (!this.books.some(book => String(book.id) === String(this.currentBookId))) {
          this.currentBookId = String(this.books[0].id)
          bookChanged = true
          const storageKey = currentLedgerBookStorageKey(this.accountScope)
          if (storageKey) localStorage.setItem(storageKey, this.currentBookId)
          await this.hydrate()
        }
        await this.refreshResources()
        await this.syncNow()
        if (bookChanged && epoch === sessionEpoch && typeof window !== 'undefined') {
          window.dispatchEvent(new CustomEvent('ledger-book-changed'))
        }
      } catch (error) {
        if (epoch === sessionEpoch) this.syncError = error?.response?.data?.detail || error?.message || '账本加载失败'
      }
    },

    async refreshBooks() {
      if (!this.online) return
      const epoch = sessionEpoch
      const scopedEngine = engine
      const remoteBooks = await apiListLedgerBooks()
      if (epoch !== sessionEpoch || scopedEngine !== engine) return
      await scopedEngine.replace('book', remoteBooks)
      if (epoch !== sessionEpoch || scopedEngine !== engine) return
      this.books = await scopedEngine.list('book')
    },

    async refreshResources(month = new Date().toISOString().slice(0, 7)) {
      const bookId = this.currentBookId
      if (!bookId || !this.online) return
      const epoch = sessionEpoch
      const scopedEngine = engine
      const [accounts, categories, merchants, members, projects, roles, budgets] = await Promise.all([
        apiListLedgerAccounts(bookId, true),
        apiListLedgerCategories(bookId, true),
        apiListLedgerNamed(bookId, 'merchants', true),
        apiListLedgerMembers(bookId),
        apiListLedgerNamed(bookId, 'projects', true),
        apiListLedgerRoles(bookId),
        apiListLedgerBudgets(bookId, month)
      ])
      if (epoch !== sessionEpoch || scopedEngine !== engine || String(bookId) !== String(this.currentBookId)) return
      await Promise.all([
        scopedEngine.replace('account', accounts, { bookId }),
        scopedEngine.replace('category', categories, { bookId }),
        scopedEngine.replace('merchant', merchants, { bookId }),
        scopedEngine.replace('member', members, { bookId }),
        scopedEngine.replace('project', projects, { bookId }),
        scopedEngine.replace('role', roles, { bookId }),
        scopedEngine.replace('budget', budgets, { bookId })
      ])
      if (epoch === sessionEpoch && scopedEngine === engine) await this.hydrate()
    },

    async refreshAccounts() {
      const bookId = this.currentBookId
      if (!bookId || !this.online) return
      const epoch = sessionEpoch
      const scopedEngine = engine
      const accounts = await apiListLedgerAccounts(bookId, true)
      if (epoch !== sessionEpoch || scopedEngine !== engine || String(bookId) !== String(this.currentBookId)) return
      await scopedEngine.replace('account', accounts, { bookId })
      if (epoch === sessionEpoch && scopedEngine === engine) this.accounts = await scopedEngine.list('account', { bookId })
    },

    async refreshCurrentBook(month = new Date().toISOString().slice(0, 7), { sync = true } = {}) {
      const bookId = this.currentBookId
      if (!bookId || !this.online) return
      const epoch = sessionEpoch
      const scopedEngine = engine
      await this.refreshResources(month)
      if (epoch !== sessionEpoch || scopedEngine !== engine || String(bookId) !== String(this.currentBookId)) return
      const transactions = []
      let page = 1
      while (true) {
        const response = await apiListLedgerTransactions(bookId, { page, pageSize: 100 })
        if (epoch !== sessionEpoch || scopedEngine !== engine || String(bookId) !== String(this.currentBookId)) return
        transactions.push(...(response.items || []))
        if (transactions.length >= Number(response.total || 0)) break
        page++
      }
      await scopedEngine.replace('transaction', transactions, { bookId })
      await this.hydrate()
      await this.refreshBooks()
      if (sync) await this.syncNow()
    },

    async hydrate() {
      const bookId = this.currentBookId
      if (!bookId) return
      const epoch = sessionEpoch
      const scopedEngine = engine
      const [accounts, categories, merchants, members, projects, roles, budgets, transactions, conflicts, rejected, pending] = await Promise.all([
        scopedEngine.list('account', { bookId }),
        scopedEngine.list('category', { bookId }),
        scopedEngine.list('merchant', { bookId }),
        scopedEngine.list('member', { bookId }),
        scopedEngine.list('project', { bookId }),
        scopedEngine.list('role', { bookId }),
        scopedEngine.list('budget', { bookId }),
        scopedEngine.list('transaction', { bookId }),
        scopedEngine.conflicts(bookId),
        scopedEngine.rejected(bookId),
        scopedEngine.pendingOperations(bookId)
      ])
      if (epoch !== sessionEpoch || scopedEngine !== engine || String(bookId) !== String(this.currentBookId)) return
      Object.assign(this, {
        accounts,
        categories,
        merchants,
        members,
        projects,
        roles,
        budgets,
        transactions,
        conflicts,
        rejected,
        pending: pending.length
      })
    },

    async put(type, value) {
      const epoch = sessionEpoch
      const scopedEngine = engine
      const id = value.id || createClientId()
      const record = await scopedEngine.put(type, { ...value, id }, {
        bookId: this.currentBookId,
        baseRevision: value.revision || 0
      })
      if (epoch === sessionEpoch && scopedEngine === engine) {
        await this.hydrate()
        this.scheduleSync()
      }
      return record
    },

    async remove(type, value) {
      const epoch = sessionEpoch
      const scopedEngine = engine
      const record = await scopedEngine.remove(type, value.id, {
        bookId: this.currentBookId,
        baseRevision: value.revision || 0
      })
      if (epoch === sessionEpoch && scopedEngine === engine) {
        await this.hydrate()
        this.scheduleSync()
      }
      return record
    },

    async saveTransaction(payload) {
      if (!this.currentBookId) throw new Error('请先选择账本')
      return this.put('transaction', payload)
    },

    async deleteTransaction(transaction) {
      if (!this.currentBookId) throw new Error('请先选择账本')
      return this.remove('transaction', transaction)
    },

    async saveResource(type, payload) {
      if (!OFFLINE_RESOURCE_TYPES.has(type)) {
        throw onlineRequired(`${type} 需要联网操作`)
      }
      if (!this.currentBookId) throw new Error('请先选择账本')
      return this.put(type, payload)
    },

    async deleteResource(type, resource) {
      if (!OFFLINE_RESOURCE_TYPES.has(type)) {
        throw onlineRequired(`${type} 需要联网操作`)
      }
      if (!this.currentBookId) throw new Error('请先选择账本')
      return this.remove(type, resource)
    },

    requireOnline(message = '此操作需要联网') {
      if (!this.online) throw onlineRequired(message)
    },

    async saveMember(payload) {
      this.requireOnline('成员管理需要联网')
      const result = payload.id
        ? await apiUpdateLedgerMember(this.currentBookId, payload.id, payload, payload.revision, createClientId())
        : await apiCreateLedgerMember(this.currentBookId, payload, createClientId())
      await this.refreshResources()
      return result
    },

    async deleteMember(member) {
      this.requireOnline('成员管理需要联网')
      const result = await apiDeleteLedgerMember(this.currentBookId, member.id, member.revision, createClientId())
      await this.refreshResources()
      return result
    },

    async saveRole(payload) {
      this.requireOnline('角色管理需要联网')
      const result = payload.id
        ? await apiUpdateLedgerRole(this.currentBookId, payload.id, payload, payload.revision, createClientId())
        : await apiCreateLedgerRole(this.currentBookId, payload, createClientId())
      await this.refreshResources()
      return result
    },

    async deleteRole(role) {
      this.requireOnline('角色管理需要联网')
      const result = await apiDeleteLedgerRole(this.currentBookId, role.id, role.revision, createClientId())
      await this.refreshResources()
      return result
    },

    async updateBook(book, payload) {
      this.requireOnline('账本管理需要联网')
      const result = await apiUpdateLedgerBook(book.id, payload, book.revision)
      await this.refreshBooks()
      return result
    },

    async deleteBook(book) {
      this.requireOnline('删除账本需要联网')
      const result = await apiDeleteLedgerBook(book.id, book.revision)
      await this.refreshServer()
      return result
    },

    async restoreRecycle(item) {
      this.requireOnline('恢复资源需要联网')
      const result = await apiRestoreLedgerRecycle(this.currentBookId, item.type, item.id, createClientId())
      await this.refreshCurrentBook()
      return result
    },

    async purgeRecycle(item) {
      this.requireOnline('永久删除需要联网')
      const result = await apiPurgeLedgerRecycle(this.currentBookId, item.type, item.id)
      await this.refreshCurrentBook()
      return result
    },

    async createScheduledTask(payload) {
      this.requireOnline('定时任务管理需要联网')
      return apiCreateLedgerScheduledTask(this.currentBookId, payload)
    },

    async updateScheduledTask(task, payload) {
      this.requireOnline('定时任务管理需要联网')
      return apiUpdateLedgerScheduledTask(this.currentBookId, task.id, payload, task.revision)
    },

    async deleteScheduledTask(task) {
      this.requireOnline('定时任务管理需要联网')
      return apiDeleteLedgerScheduledTask(this.currentBookId, task.id)
    },

    async runScheduledTask(task) {
      this.requireOnline('执行定时任务需要联网')
      const result = await apiRunLedgerScheduledTask(this.currentBookId, task.id)
      await this.refreshCurrentBook()
      return result
    },

    async confirmAi(draftId, transactions) {
      this.requireOnline('AI 记账确认需要联网')
      const result = await apiLedgerAiConfirm(this.currentBookId, draftId, transactions, createClientId())
      await this.refreshCurrentBook()
      return result
    },

    async importFile(file, progress = {}) {
      this.requireOnline('导入账单需要联网')
      progress.onStage?.('upload')
      const preview = await apiImportLedgerPreview(this.currentBookId, file, 'AUTO', progress.onUploadProgress)
      progress.onPreview?.(preview)
      progress.onStage?.('confirm')
      const result = await apiImportLedgerConfirm(this.currentBookId, preview.batchId)
      await this.refreshCurrentBook()
      return result
    },

    scheduleSync() {
      clearTimeout(syncTimer)
      const epoch = sessionEpoch
      syncTimer = setTimeout(() => {
        if (epoch === sessionEpoch) this.syncNow()
      }, 250)
    },

    async syncNow() {
      if (!this.online || !this.currentBookId) return
      const epoch = sessionEpoch
      const scopedEngine = engine
      const bookId = this.currentBookId
      if (activeSyncPromise?.epoch === epoch && activeSyncPromise.bookId === bookId) return activeSyncPromise.promise
      this.syncing = true
      this.syncError = ''
      const promise = (async () => {
        try {
          const result = await scopedEngine.sync(bookId)
          if (epoch !== sessionEpoch || scopedEngine !== engine || String(bookId) !== String(this.currentBookId)) return result
          if (result.resetRequired) await this.refreshCurrentBook(undefined, { sync: false })
          else await this.hydrate()
          return result
        } catch (error) {
          if (epoch === sessionEpoch) this.syncError = error?.response?.data?.detail || error?.message || '同步失败'
        } finally {
          if (epoch === sessionEpoch && activeSyncPromise?.promise === promise) {
            this.syncing = false
            activeSyncPromise = null
          }
        }
      })()
      activeSyncPromise = { epoch, bookId, promise }
      return promise
    },

    async resolveConflict(opId, strategy, mergedPayload) {
      const epoch = sessionEpoch
      const scopedEngine = engine
      await scopedEngine.resolveConflict(opId, strategy, mergedPayload)
      if (epoch === sessionEpoch && scopedEngine === engine) {
        await this.hydrate()
        this.scheduleSync()
      }
    },

    async exportRejected() {
      return engine.exportRejected(this.currentBookId)
    }
  }
})
