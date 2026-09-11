import { defineStore } from 'pinia'
import { createLedgerSyncEngine } from '../../packages/sync-engine/src/index.js'
import {
  apiCreateLedgerBook,
  apiListLedgerAccounts,
  apiListLedgerBooks,
  apiListLedgerBudgets,
  apiListLedgerCategories,
  apiListLedgerMembers,
  apiListLedgerNamed,
  apiListLedgerRoles,
  apiListLedgerTransactions,
  apiPullLedgerSync,
  apiPushLedgerSync
} from '../api'

const engine = createLedgerSyncEngine({
  push: (bookId, operations) => apiPushLedgerSync(bookId, operations),
  pull: (bookId, cursor) => apiPullLedgerSync(bookId, cursor)
})

let listenersInstalled = false
let syncTimer

export const useLedgerStore = defineStore('ledger', {
  state: () => ({
    ready: false,
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
    visibleCategories: state => state.categories.filter(item => !item.hidden && !item.deleted),
    visibleMerchants: state => state.merchants.filter(item => !item.hidden && !item.deleted),
    visibleProjects: state => state.projects.filter(item => !item.hidden && !item.deleted),
    activeMembers: state => state.members.filter(item => !item.deleted),
    secondaryCategories: state => state.categories.filter(item => item.parentId && !item.hidden && !item.deleted)
  },
  actions: {
    async init() {
      if (this.ready) return
      this.loading = true
      this.installListeners()
      try {
        this.books = await engine.list('book')
        this.currentBookId = localStorage.getItem('ledger-current-book') || this.books[0]?.id || ''
        if (this.currentBookId) await this.hydrate()
        if (this.online) await this.refreshServer()
      } finally {
        this.loading = false
        this.ready = true
      }
    },

    installListeners() {
      if (listenersInstalled || typeof window === 'undefined') return
      listenersInstalled = true
      window.addEventListener('online', () => {
        this.online = true
        this.syncNow()
      })
      window.addEventListener('offline', () => { this.online = false })
      document.addEventListener('visibilitychange', () => {
        if (document.visibilityState === 'visible' && navigator.onLine) this.syncNow()
      })
    },

    async selectBook(bookId) {
      if (!bookId || String(bookId) === String(this.currentBookId)) return
      this.currentBookId = String(bookId)
      localStorage.setItem('ledger-current-book', this.currentBookId)
      await this.hydrate()
      if (this.online) await this.refreshCurrentBook()
    },

    async createBook(payload) {
      if (!this.online) throw new Error('创建账本需要联网')
      const book = await apiCreateLedgerBook(payload)
      await engine.put('book', book, { bookId: book.id, recordOp: false })
      this.books = await engine.list('book')
      await this.selectBook(book.id)
      return book
    },

    async refreshServer() {
      try {
        const remoteBooks = await apiListLedgerBooks()
        for (const book of remoteBooks) {
          await engine.put('book', book, { bookId: book.id, recordOp: false })
        }
        this.books = await engine.list('book')
        if (!this.books.length) return
        if (!this.books.some(book => String(book.id) === String(this.currentBookId))) {
          this.currentBookId = String(this.books[0].id)
          localStorage.setItem('ledger-current-book', this.currentBookId)
        }
        await this.refreshCurrentBook()
      } catch (error) {
        this.syncError = error?.response?.data?.detail || error?.message || '账本加载失败'
      }
    },

    async refreshCurrentBook(month = new Date().toISOString().slice(0, 7)) {
      const bookId = this.currentBookId
      if (!bookId || !this.online) return
      const [accounts, categories, merchants, members, projects, roles, budgets] = await Promise.all([
        apiListLedgerAccounts(bookId, true),
        apiListLedgerCategories(bookId, true),
        apiListLedgerNamed(bookId, 'merchants', true),
        apiListLedgerMembers(bookId),
        apiListLedgerNamed(bookId, 'projects', true),
        apiListLedgerRoles(bookId),
        apiListLedgerBudgets(bookId, month)
      ])
      await Promise.all([
        engine.replace('account', accounts, { bookId }),
        engine.replace('category', categories, { bookId }),
        engine.replace('merchant', merchants, { bookId }),
        engine.replace('member', members, { bookId }),
        engine.replace('project', projects, { bookId }),
        engine.replace('role', roles, { bookId }),
        engine.replace('budget', budgets, { bookId })
      ])
      const transactions = []
      let page = 1
      while (true) {
        const response = await apiListLedgerTransactions(bookId, { page, pageSize: 100 })
        transactions.push(...(response.items || []))
        if (transactions.length >= Number(response.total || 0)) break
        page++
      }
      await engine.replace('transaction', transactions, { bookId })
      await this.hydrate()
      await this.syncNow()
    },

    async hydrate() {
      const bookId = this.currentBookId
      if (!bookId) return
      const [accounts, categories, merchants, members, projects, roles, budgets, transactions, conflicts, rejected, pending] = await Promise.all([
        engine.list('account', { bookId }),
        engine.list('category', { bookId }),
        engine.list('merchant', { bookId }),
        engine.list('member', { bookId }),
        engine.list('project', { bookId }),
        engine.list('role', { bookId }),
        engine.list('budget', { bookId }),
        engine.list('transaction', { bookId }),
        engine.conflicts(bookId),
        engine.rejected(bookId),
        engine.pendingOperations(bookId)
      ])
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
      const id = value.id || crypto.randomUUID()
      const record = await engine.put(type, { ...value, id }, {
        bookId: this.currentBookId,
        baseRevision: value.revision || 0
      })
      await this.hydrate()
      this.scheduleSync()
      return record
    },

    async remove(type, value) {
      const record = await engine.remove(type, value.id, {
        bookId: this.currentBookId,
        baseRevision: value.revision || 0
      })
      await this.hydrate()
      this.scheduleSync()
      return record
    },

    scheduleSync() {
      clearTimeout(syncTimer)
      syncTimer = setTimeout(() => this.syncNow(), 250)
    },

    async syncNow() {
      if (!this.online || !this.currentBookId || this.syncing) return
      this.syncing = true
      this.syncError = ''
      try {
        const result = await engine.sync(this.currentBookId)
        if (result.resetRequired) await this.refreshCurrentBook()
        else await this.hydrate()
        return result
      } catch (error) {
        this.syncError = error?.response?.data?.detail || error?.message || '同步失败'
      } finally {
        this.syncing = false
      }
    },

    async resolveConflict(opId, strategy, mergedPayload) {
      await engine.resolveConflict(opId, strategy, mergedPayload)
      await this.hydrate()
      this.scheduleSync()
    },

    async exportRejected() {
      return engine.exportRejected(this.currentBookId)
    }
  }
})
