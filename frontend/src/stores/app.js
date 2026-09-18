import { defineStore } from 'pinia'
import { apiLogout, apiRefresh, clearAccessToken, getHolidays } from '../../packages/api-client/src/index.js'
import { accountScopeFor } from '../utils/accountScope.js'
import { useLedgerStore } from './ledger.js'
import { useWorktimeStore } from './worktime.js'

const LS_THEME = 'st_theme'
const LS_ACCENT = 'st_accent'
const LS_OFFLINE_USER = 'st_offline_user_v1'
let appSessionEpoch = 0

function cacheOfflineUser(user) {
  if (typeof localStorage === 'undefined') return
  const scope = accountScopeFor(user)
  if (!scope) return
  localStorage.setItem(LS_OFFLINE_USER, JSON.stringify({
    id: user.id,
    username: user.username,
    nickname: user.nickname || '',
    authorities: Array.isArray(user.authorities) ? user.authorities : []
  }))
}

function readOfflineUser() {
  if (typeof localStorage === 'undefined') return null
  try {
    const user = JSON.parse(localStorage.getItem(LS_OFFLINE_USER) || 'null')
    return accountScopeFor(user) ? user : null
  } catch {
    localStorage.removeItem(LS_OFFLINE_USER)
    return null
  }
}

function clearOfflineUser() {
  if (typeof localStorage !== 'undefined') localStorage.removeItem(LS_OFFLINE_USER)
}

export const ACCENTS = {
  green: { light: '#0f5132', dark: '#7bd3a6', soft: '#e6efe8', darkSoft: '#203a2b' },
  blue: { light: '#3e667d', dark: '#83bdd7', soft: '#e4edf2', darkSoft: '#203541' },
  plum: { light: '#80536c', dark: '#d2a0be', soft: '#f0e6ec', darkSoft: '#432d3e' },
  rust: { light: '#a4573f', dark: '#e6a38d', soft: '#f4e7e1', darkSoft: '#452d27' }
}

export const useAppStore = defineStore('app', {
  state: () => ({
    holidays: {},                    // { "YYYY-MM-DD": {name, off} } 法定节假日/调休
    _holYears: {},                   // 已加载年份 -> 数据来源
    _holReq: {},                     // 进行中的年份请求（防重复）
    dbMode: false,                   // 本地服务（数据库）可用
    theme: 'light',
    accent: 'green',
    ready: false,                    // 初始化完成
    authUser: null,
    accountScope: '',
    authRequired: false,
    offlineSession: false
  }),

  actions: {
    /* ---------- 主题 ---------- */
    applyTheme() {
      const storedTheme = localStorage.getItem(LS_THEME)
      const isMobile = window.matchMedia?.('(max-width: 640px)').matches || window.matchMedia?.('(pointer: coarse)').matches
      const systemDark = window.matchMedia?.('(prefers-color-scheme: dark)').matches
      const theme = storedTheme === 'dark' || storedTheme === 'light'
        ? storedTheme
        : (isMobile && systemDark ? 'dark' : 'light')
      const accent = ACCENTS[localStorage.getItem(LS_ACCENT)] ? localStorage.getItem(LS_ACCENT) : 'green'
      this.theme = theme
      this.accent = accent
      const root = document.documentElement
      root.classList.toggle('dark', theme === 'dark')
      root.classList.toggle('light', theme !== 'dark')
      const palette = ACCENTS[accent]
      const darkMode = theme === 'dark'
      root.style.setProperty('--accent', darkMode ? palette.dark : palette.light)
      root.style.setProperty('--accent-soft', darkMode ? palette.darkSoft : palette.soft)
      root.style.setProperty('--up', darkMode ? palette.dark : palette.light)
      root.style.setProperty('--secondary', darkMode ? palette.darkSoft : palette.soft)
      const mc = document.querySelector('meta[name="theme-color"]')
      if (mc) mc.content = darkMode ? '#14171c' : '#f7f5f1'
      localStorage.setItem(LS_THEME, theme)
      localStorage.setItem(LS_ACCENT, accent)
    },
    toggleTheme() {
      this.theme = this.theme === 'dark' ? 'light' : 'dark'
      localStorage.setItem(LS_THEME, this.theme)
      this.applyTheme()
    },
    setAccent(name) {
      if (!ACCENTS[name]) return
      this.accent = name
      localStorage.setItem(LS_ACCENT, name)
      this.applyTheme()
    },

    async connectDb() {
      if (!this.authUser || !this.accountScope) {
        this.authRequired = true
        this.dbMode = false
        return
      }
      const epoch = appSessionEpoch
      const scope = this.accountScope
      try {
        await useWorktimeStore().fetch()
        if (epoch !== appSessionEpoch || scope !== this.accountScope) return
        this.dbMode = true
        this.authRequired = false
      } catch (e) {
        if (epoch !== appSessionEpoch || scope !== this.accountScope) return
        this.authRequired = e.response?.status === 401
        this.dbMode = false
      }
    },

    async completeLogin(user) {
      const scope = accountScopeFor(user)
      if (!scope) throw new Error('登录响应缺少用户身份信息')
      if (scope !== this.accountScope) appSessionEpoch += 1
      cacheOfflineUser(user)
      this.authUser = user || null
      this.accountScope = scope
      this.authRequired = false
      this.offlineSession = false
      this.dbMode = false
      useWorktimeStore().reset()
      await useLedgerStore().switchUser(user)
      await this.connectDb()
    },

    async restoreOfflineSession() {
      const user = readOfflineUser()
      if (!user) return false
      const scope = accountScopeFor(user)
      if (scope !== this.accountScope) appSessionEpoch += 1
      this.authUser = user
      this.accountScope = scope
      this.authRequired = false
      this.offlineSession = true
      this.dbMode = false
      useWorktimeStore().reset()
      await useLedgerStore().switchUser(user)
      return true
    },

    async logout() {
      appSessionEpoch += 1
      try {
        await apiLogout()
      } catch (e) {
        // 服务端退出失败也必须清理本地认证态和用户数据。
      } finally {
        clearAccessToken()
        clearOfflineUser()
        await useLedgerStore().clearSession()
        useWorktimeStore().reset()
        this.authUser = null
        this.accountScope = ''
        this.authRequired = true
        this.offlineSession = false
        this.dbMode = false
      }
    },

    /* ---------- 节假日数据 ---------- */
    async ensureHolidays(year) {
      if (!year || this._holYears[year]) return
      if (!this._holReq[year]) {
        this._holReq[year] = getHolidays(year)
          .then(res => {
            Object.assign(this.holidays, res.days || {})
            this._holYears[year] = res.source || 'ok'
          })
          .catch(() => { /* 加载失败允许下次重试 */ })
          .finally(() => { delete this._holReq[year] })
      }
      return this._holReq[year]
    },

    async init() {
      if (this.ready) return
      this.applyTheme()
      try {
        const session = await apiRefresh()
        await this.completeLogin(session.user)
      } catch (e) {
        if (typeof navigator !== 'undefined' && navigator.onLine === false && await this.restoreOfflineSession()) {
          this.ensureHolidays(new Date().getFullYear())
          this.ready = true
          return
        }
        clearAccessToken()
        clearOfflineUser()
        await useLedgerStore().clearSession()
        useWorktimeStore().reset()
        this.authUser = null
        this.accountScope = ''
        this.authRequired = true
        this.offlineSession = false
        this.dbMode = false
      }
      // 节假日数据失败不阻塞主流程
      this.ensureHolidays(new Date().getFullYear())
      this.ready = true
    }
  }
})
