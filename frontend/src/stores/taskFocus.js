import { defineStore } from 'pinia'
import {
  apiDeleteFocus, apiFinishFocus, apiGetCurrentFocus, apiGetFocusSettings, apiGetFocusStats,
  apiListFocusSessions, apiStartFocus, apiUpdateFocusSession, apiUpdateFocusSettings
} from '../../packages/api-client/src/index.js'
import { useAppStore } from './app.js'
import { loadFocusPresets, saveFocusPresets } from '../utils/focusPresets.js'

let ticker

function dateKey(date) {
  return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')}`
}

function startOfWeek(date) {
  const value = new Date(date.getFullYear(), date.getMonth(), date.getDate())
  const offset = (value.getDay() + 6) % 7
  value.setDate(value.getDate() - offset)
  return value
}

export const useTaskFocusStore = defineStore('task-focus', {
  state: () => ({
    current: null,
    settings: null,
    stats: null,
    sessions: [],
    presets: [],
    // 记录的取数窗口：默认从本周一到明天，加载更多时往前推
    rangeStart: '',
    rangeEnd: '',
    hasMore: true,
    loadingSessions: false,
    // 列表分页：默认渲染 50 条，滚动到底部再放下一批（取数窗口不够时才去请求更早的周）
    pageSize: 50,
    visibleCount: 50,
    now: Date.now(),
    loading: false,
    error: ''
  }),
  getters: {
    // plannedMinutes = 0 表示正计时：没有计划结束时间，只累计已用时长
    isCountUp: state => Boolean(state.current && !Number(state.current.plannedMinutes)),
    elapsedSeconds: state => (state.current ? Math.max(0, Math.floor((state.now - new Date(state.current.startedAt).getTime()) / 1000)) : 0),
    elapsedLabel() {
      const seconds = this.elapsedSeconds
      return `${String(Math.floor(seconds / 60)).padStart(2, '0')}:${String(seconds % 60).padStart(2, '0')}`
    },
    remainingSeconds: state => (state.current && state.current.plannedEndAt ? Math.max(0, Math.ceil((new Date(state.current.plannedEndAt).getTime() - state.now) / 1000)) : 0),
    remainingLabel() {
      const seconds = this.remainingSeconds
      const hours = Math.floor(seconds / 3600)
      const minutes = Math.floor((seconds % 3600) / 60)
      const rest = seconds % 60
      if (hours) return `${hours}:${String(minutes).padStart(2, '0')}:${String(rest).padStart(2, '0')}`
      return `${String(minutes).padStart(2, '0')}:${String(rest).padStart(2, '0')}`
    },
    displayLabel() { return this.isCountUp ? this.elapsedLabel : this.remainingLabel },
    // 只渲染已展开的条数，滚动到底再追加
    visibleSessions: state => state.sessions.slice(0, state.visibleCount),
    /** 还有没展开的已取回记录，或者还能往前取更早的周 */
    canLoadMore: state => state.visibleCount < state.sessions.length || state.hasMore,
    // 记录按日期分组（本地时区）
    groupedSessions: state => {
      const groups = []
      let currentKey = ''
      for (const session of state.sessions.slice(0, state.visibleCount)) {
        const date = new Date(session.startedAt)
        const key = dateKey(date)
        if (key !== currentKey) {
          currentKey = key
          groups.push({ key, date, items: [] })
        }
        groups[groups.length - 1].items.push(session)
      }
      return groups
    }
  },
  actions: {
    installTicker() {
      if (ticker || typeof window === 'undefined') return
      ticker = window.setInterval(() => {
        this.now = Date.now()
        // 正计时不会自动结束，番茄计时到点自动完成
        if (this.current && !this.isCountUp && this.remainingSeconds === 0) this.complete()
      }, 1000)
    },
    scopeKey() {
      const app = useAppStore()
      return app.accountScope || 'anonymous'
    },
    loadPresets() { this.presets = loadFocusPresets(this.scopeKey()) },
    savePresetList(list) {
      this.presets = list
      saveFocusPresets(this.scopeKey(), list)
    },
    reset() {
      this.current = null
      this.settings = null
      this.stats = null
      this.sessions = []
      this.error = ''
    },
    async refresh() {
      const app = useAppStore()
      if (!app.authUser || app.offlineSession) return
      this.installTicker()
      this.error = ''
      this.loadPresets()
      try {
        const [current, settings, stats] = await Promise.all([apiGetCurrentFocus(), apiGetFocusSettings(), apiGetFocusStats()])
        this.current = current
        this.settings = settings
        this.stats = stats
        this.now = Date.now()
        await this.reloadSessions()
      } catch (error) {
        this.error = error?.response?.data?.detail || error?.message || '专注数据加载失败'
      }
    },
    /** 重置到「本周」窗口重新拉记录 */
    async reloadSessions() {
      const today = new Date()
      this.rangeStart = dateKey(startOfWeek(today))
      const tomorrow = new Date(today.getFullYear(), today.getMonth(), today.getDate() + 1)
      this.rangeEnd = dateKey(tomorrow)
      this.hasMore = true
      this.visibleCount = this.pageSize
      await this.loadSessions({ from: this.rangeStart, to: this.rangeEnd })
    },
    async loadSessions({ from, to, append = false }) {
      this.loadingSessions = true
      try {
        const list = await apiListFocusSessions({ from, to })
        this.sessions = append ? [...this.sessions, ...(list || [])] : (list || [])
      } finally {
        this.loadingSessions = false
      }
    },
    /**
     * 列表滚动到底部时调用：先放出一批已取回的记录；
     * 已经全部展开就再去取更早的周，取到新数据才继续放出。
     */
    async loadMoreVisible() {
      if (this.loadingSessions) return
      if (this.visibleCount < this.sessions.length) {
        this.visibleCount += this.pageSize
        return
      }
      if (!this.hasMore) return
      const before = this.sessions.length
      await this.loadMore()
      if (this.sessions.length > before) this.visibleCount += this.pageSize
    },
    /** 往前再取 7 天；这一周没数据就继续往前，最多回溯 8 周 */
    async loadMore(days = 7) {
      if (this.loadingSessions || !this.hasMore) return
      let cursor = new Date(`${this.rangeStart}T00:00:00`)
      for (let attempt = 0; attempt < 8; attempt += 1) {
        const to = new Date(cursor)
        const from = new Date(cursor.getFullYear(), cursor.getMonth(), cursor.getDate() - days)
        const before = this.sessions.length
        await this.loadSessions({ from: dateKey(from), to: dateKey(to), append: true })
        this.rangeStart = dateKey(from)
        if (this.sessions.length > before) return
        cursor = from
      }
      this.hasMore = false
    },
    async start({ taskId, plannedMinutes, deviceLabel = 'Web' } = {}) {
      this.loading = true
      this.error = ''
      try {
        this.current = await apiStartFocus({
          plannedMinutes: plannedMinutes ?? (this.settings?.focusMinutes || 25),
          deviceLabel,
          ...(taskId ? { taskId } : {})
        })
        this.now = Date.now()
        await this.reloadSessions()
        return this.current
      } catch (error) {
        this.error = error?.response?.data?.detail || error?.message || '无法开始专注'
        throw error
      } finally {
        this.loading = false
      }
    },
    async finish(status) {
      if (!this.current) return
      this.loading = true
      try {
        await apiFinishFocus(this.current.publicId, status, this.current.revision)
        this.current = null
        this.stats = await apiGetFocusStats()
        await this.reloadSessions()
      } finally {
        this.loading = false
      }
    },
    async complete() { if (this.current && !this.loading) await this.finish('COMPLETED') },
    async abort() { await this.finish('ABORTED') },
    /** 补记：给起止时间 + 任务 + 笔记 */
    async createRecord({ taskId, startedAt, endedAt, note }) {
      this.loading = true
      this.error = ''
      try {
        const created = await apiStartFocus({
          ...(taskId ? { taskId } : {}),
          startedAt,
          endedAt,
          note: note || '',
          deviceLabel: 'Web'
        })
        this.stats = await apiGetFocusStats()
        await this.reloadSessions()
        return created
      } catch (error) {
        this.error = error?.response?.data?.detail || error?.message || '保存专注记录失败'
        throw error
      } finally {
        this.loading = false
      }
    },
    async updateNote(session, note) {
      const updated = await apiUpdateFocusSession(session.publicId, { note }, session.revision)
      const index = this.sessions.findIndex(item => item.publicId === session.publicId)
      if (index >= 0) this.sessions[index] = updated
      return updated
    },
    async remove(session) {
      await apiDeleteFocus(session.publicId, session.revision)
      this.sessions = this.sessions.filter(item => item.publicId !== session.publicId)
      this.stats = await apiGetFocusStats()
    },
    async removeMany(list) {
      for (const session of list) {
        try { await apiDeleteFocus(session.publicId, session.revision) } catch { /* 单条失败不影响其它 */ }
      }
      this.stats = await apiGetFocusStats()
      await this.reloadSessions()
    },
    async saveSettings(payload) {
      this.settings = await apiUpdateFocusSettings(payload, this.settings.revision)
      return this.settings
    }
  }
})
