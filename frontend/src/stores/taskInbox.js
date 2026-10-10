import { defineStore } from 'pinia'
import { apiClearTaskInbox, apiGetTaskInboxUnread, apiListTaskInbox, apiReadTaskInbox, apiStreamTaskInbox } from '../../packages/api-client/src/index.js'

let pollingTimer
let streamController
let reconnectTimer

export const useTaskInboxStore = defineStore('taskInbox', {
  state: () => ({ messages: [], unread: 0, overdue: 0, total: 0, loading: false, connected: false, error: '' }),
  actions: {
    async refresh(unreadOnly = false) {
      this.loading = true
      try {
        const [page, count] = await Promise.all([apiListTaskInbox({ unread: unreadOnly, page: 0, size: 100 }), apiGetTaskInboxUnread()])
        this.messages = page?.items || []
        this.total = page?.total || 0
        this.unread = count?.unread || 0
        this.overdue = count?.overdue || 0
        this.error = ''
      } catch (error) { this.error = error?.response?.data?.detail || error?.message || '提醒加载失败' }
      finally { this.loading = false }
    },
    connect() {
      if (typeof window === 'undefined' || streamController) return
      streamController = new AbortController()
      apiStreamTaskInbox(message => { this.messages.unshift(message); this.unread += 1 }, () => {
        this.connected = true; clearInterval(pollingTimer); pollingTimer = undefined
      }, streamController.signal).catch(() => {
        if (streamController?.signal.aborted) return
        this.connected = false
        if (!pollingTimer) pollingTimer = window.setInterval(() => this.refresh(), 30_000)
        streamController = undefined
        reconnectTimer = window.setTimeout(() => this.connect(), 30_000)
      })
    },
    disconnect() { streamController?.abort(); streamController = undefined; clearInterval(pollingTimer); clearTimeout(reconnectTimer); pollingTimer = undefined; reconnectTimer = undefined; this.connected = false },
    async markRead(ids) { const count = await apiReadTaskInbox({ ids, all: false }); await this.refresh(); return count },
    async markAllRead() { await apiReadTaskInbox({ ids: [], all: true }); await this.refresh() },
    async clearRead() { await apiClearTaskInbox(); await this.refresh() }
  }
})
