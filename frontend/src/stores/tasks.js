import { defineStore } from 'pinia'
import {
  apiCompleteTask,
  apiCreateTask,
  apiDeleteTask,
  apiListTaskLists,
  apiListTasks,
  apiReopenTask,
  apiUpdateTask
} from '../../packages/api-client/src/index.js'
import { createClientId } from '../utils/clientId.js'
import { useAppStore } from './app.js'

function online() { return typeof navigator === 'undefined' ? true : navigator.onLine }

export const useTasksStore = defineStore('tasks', {
  state: () => ({ ready: false, loading: false, saving: false, online: online(), lists: [], tasks: [], total: 0, error: '' }),
  getters: {
    inbox: state => state.lists.find(item => item.systemKey === 'INBOX') || state.lists[0],
    openTasks: state => state.tasks.filter(item => item.status !== 'COMPLETED'),
    completedTasks: state => state.tasks.filter(item => item.status === 'COMPLETED')
  },
  actions: {
    installListeners() {
      if (typeof window === 'undefined' || this._listenersInstalled) return
      this._listenersInstalled = true
      window.addEventListener('online', () => { this.online = true; this.fetch() })
      window.addEventListener('offline', () => { this.online = false })
    },
    async fetch() {
      this.installListeners()
      this.loading = true
      this.error = ''
      try {
        const [lists, page] = await Promise.all([apiListTaskLists(), apiListTasks({ page: 0, size: 200 })])
        this.lists = lists || []
        this.tasks = page?.items || []
        this.total = Number(page?.total || this.tasks.length)
        this.ready = true
        return this.tasks
      } catch (error) {
        this.error = error?.response?.data?.detail || error?.message || '任务加载失败'
        throw error
      } finally { this.loading = false }
    },
    assertWritable() {
      if (!this.online || useAppStore().offlineSession) {
        const error = new Error('当前离线，任务写入需要联网；本增量不会伪造离线保存')
        error.code = 'ONLINE_REQUIRED'
        throw error
      }
    },
    replace(item) {
      const index = this.tasks.findIndex(row => row.publicId === item.publicId)
      if (index >= 0) this.tasks.splice(index, 1, item)
      else this.tasks.unshift(item)
    },
    async create(payload) {
      this.assertWritable(); this.saving = true
      try { const item = await apiCreateTask({ listId: payload.listId || this.inbox?.publicId, ...payload }, createClientId()); this.replace(item); this.total += 1; return item }
      finally { this.saving = false }
    },
    async update(item, payload) {
      this.assertWritable(); this.saving = true
      try { const next = await apiUpdateTask(item.publicId, payload, item.revision); this.replace(next); return next }
      finally { this.saving = false }
    },
    async complete(item) {
      this.assertWritable(); const next = await apiCompleteTask(item.publicId, item.revision); this.replace(next); return next
    },
    async reopen(item) {
      this.assertWritable(); const next = await apiReopenTask(item.publicId, item.revision); this.replace(next); return next
    },
    async remove(item) {
      this.assertWritable(); await apiDeleteTask(item.publicId, item.revision); this.tasks = this.tasks.filter(row => row.publicId !== item.publicId); this.total = Math.max(0, this.total - 1)
    }
  }
})
