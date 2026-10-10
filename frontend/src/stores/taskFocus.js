import { defineStore } from 'pinia'
import { apiFinishFocus, apiGetCurrentFocus, apiGetFocusSettings, apiGetFocusStats, apiListFocusSessions, apiStartFocus, apiUpdateFocusSettings } from '../../packages/api-client/src/index.js'
import { useAppStore } from './app.js'

let ticker

export const useTaskFocusStore = defineStore('task-focus', {
  state: () => ({ current: null, settings: null, stats: null, sessions: [], now: Date.now(), loading: false, error: '' }),
  getters: {
    remainingSeconds: state => state.current ? Math.max(0, Math.ceil((new Date(state.current.plannedEndAt).getTime() - state.now) / 1000)) : 0,
    remainingLabel() { const seconds = this.remainingSeconds; return `${String(Math.floor(seconds / 60)).padStart(2, '0')}:${String(seconds % 60).padStart(2, '0')}` }
  },
  actions: {
    installTicker() {
      if (ticker || typeof window === 'undefined') return
      ticker = window.setInterval(() => { this.now = Date.now(); if (this.current && this.remainingSeconds === 0) this.complete() }, 1000)
    },
    reset() { this.current = null; this.settings = null; this.stats = null; this.sessions = []; this.error = '' },
    async refresh() {
      if (!useAppStore().authUser || useAppStore().offlineSession) return
      this.installTicker(); this.error = ''
      try {
        const [current, settings, stats, sessions] = await Promise.all([apiGetCurrentFocus(), apiGetFocusSettings(), apiGetFocusStats(), apiListFocusSessions()])
        this.current = current; this.settings = settings; this.stats = stats; this.sessions = sessions || []; this.now = Date.now()
      } catch (error) { this.error = error?.response?.data?.detail || error?.message || '专注数据加载失败' }
    },
    async start(payload = {}) {
      this.loading = true; this.error = ''
      try { this.current = await apiStartFocus({ plannedMinutes: this.settings?.focusMinutes || 25, deviceLabel: 'Web', ...payload }); this.now = Date.now(); await this.refresh(); return this.current }
      catch (error) { this.error = error?.response?.data?.detail || error?.message || '无法开始专注'; throw error }
      finally { this.loading = false }
    },
    async finish(status) {
      if (!this.current) return
      this.loading = true
      try { await apiFinishFocus(this.current.publicId, status, this.current.revision); this.current = null; await this.refresh() }
      finally { this.loading = false }
    },
    async complete() { if (this.current && !this.loading) await this.finish('COMPLETED') },
    async abort() { await this.finish('ABORTED') },
    async saveSettings(payload) { this.settings = await apiUpdateFocusSettings(payload, this.settings.revision); return this.settings }
  }
})
