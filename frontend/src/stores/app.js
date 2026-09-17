import { defineStore } from 'pinia'
import { ElMessage } from 'element-plus'
import { apiGetWorktimeSnapshot, apiPutWorktimeSnapshot, apiGetHolidays, apiLogout, apiRefresh, clearAccessToken } from '../api'
import { CALC } from '../utils/calc'
import { accountScopeFor, scopedStorageKey } from '../utils/accountScope.js'
import { useLedgerStore } from './ledger.js'

export const DEFAULTS = {
  workStart: '09:00', workEnd: '18:00', lunchMin: 90,
  daysPerMonth: 21.75, autoDays: true, salaryPre: 0, salaryPost: 0, basis: 'post',
  salaries: {}                      // 按月工资 { "YYYY-MM": {pre, post} }，每月工资可浮动
}

const LS_SET = 'st_settings'
const LS_REC = 'st_records'
const LS_THEME = 'st_theme'
const LS_ACCENT = 'st_accent'
let appSessionEpoch = 0

export const ACCENTS = {
  green: { light: '#0f5132', dark: '#7bd3a6', soft: '#e6efe8', darkSoft: '#203a2b' },
  blue: { light: '#3e667d', dark: '#83bdd7', soft: '#e4edf2', darkSoft: '#203541' },
  plum: { light: '#80536c', dark: '#d2a0be', soft: '#f0e6ec', darkSoft: '#432d3e' },
  rust: { light: '#a4573f', dark: '#e6a38d', soft: '#f4e7e1', darkSoft: '#452d27' }
}

export const useAppStore = defineStore('app', {
  state: () => ({
    settings: { ...DEFAULTS },
    records: {},                     // { "YYYY-MM-DD": {start, end, rest?} }
    holidays: {},                    // { "YYYY-MM-DD": {name, off} } 法定节假日/调休
    _holYears: {},                   // 已加载年份 -> 数据来源
    _holReq: {},                     // 进行中的年份请求（防重复）
    dbMode: false,                   // 本地服务（数据库）可用
    theme: 'light',
    accent: 'green',
    punchDate: '',
    recMonth: '',
    ready: false,                    // 初始化完成
    authUser: null,
    accountScope: '',
    authRequired: false,
    _putChain: Promise.resolve()
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

    /* ---------- 本地缓存 ---------- */
    loadLocal() {
      const settingsKey = scopedStorageKey(LS_SET, this.accountScope)
      const recordsKey = scopedStorageKey(LS_REC, this.accountScope)
      this.settings = { ...DEFAULTS }
      this.records = {}
      if (settingsKey) {
        try { this.settings = { ...DEFAULTS, ...JSON.parse(localStorage.getItem(settingsKey) || '{}') } } catch (e) { /* ignore */ }
      }
      if (recordsKey) {
        try { this.records = JSON.parse(localStorage.getItem(recordsKey) || '{}') || {} } catch (e) { this.records = {} }
      }
      const t = CALC.dateKey(new Date())
      this.punchDate = t
      this.recMonth = t.slice(0, 7)
    },
    saveLocal() {
      const settingsKey = scopedStorageKey(LS_SET, this.accountScope)
      const recordsKey = scopedStorageKey(LS_REC, this.accountScope)
      if (!settingsKey || !recordsKey) return
      localStorage.setItem(settingsKey, JSON.stringify(this.settings))
      localStorage.setItem(recordsKey, JSON.stringify(this.records))
    },

    /* ---------- 数据库同步 ---------- */
    async saveAll() {
      this.saveLocal()
      if (!this.dbMode) return
      const epoch = appSessionEpoch
      const scope = this.accountScope
      const payload = { settings: this.settings, records: this.records }
      // 保存请求串行队列，避免并发快照乱序
      this._putChain = this._putChain
        .then(() => {
          if (epoch !== appSessionEpoch || scope !== this.accountScope) return
          return apiPutWorktimeSnapshot(payload)
        })
        .catch(error => {
          if (epoch !== appSessionEpoch || scope !== this.accountScope) return
          if (error.response?.status === 401) this.authRequired = true
          this.dbMode = false
          ElMessage.warning(error.response?.status === 401 ? '登录已过期，请重新登录' : '数据库不可用，已切换本地模式')
        })
      return this._putChain
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
        const data = await apiGetWorktimeSnapshot()
        if (epoch !== appSessionEpoch || scope !== this.accountScope) return
        const dbEmpty = !Object.keys(data.records || {}).length && Number(data.settings?.revision || 0) === 0
        const settingsKey = scopedStorageKey(LS_SET, this.accountScope)
        const localHas = Object.keys(this.records).length > 0 || !!localStorage.getItem(settingsKey)
        if (dbEmpty && localHas) {
          await apiPutWorktimeSnapshot({ settings: this.settings, records: this.records })
          if (epoch !== appSessionEpoch || scope !== this.accountScope) return
        } else if (!dbEmpty) {
          this.settings = { ...DEFAULTS, ...(data.settings || {}) }
          this.records = data.records || {}
          this.saveLocal()                                                       // 服务端为准，本地留缓存
        }
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
      this.authUser = user || null
      this.accountScope = scope
      this.authRequired = false
      this.dbMode = false
      this._putChain = Promise.resolve()
      this.loadLocal()
      await useLedgerStore().switchUser(user)
      await this.connectDb()
    },

    async logout() {
      appSessionEpoch += 1
      try {
        await apiLogout()
      } catch (e) {
        // 服务端退出失败也必须清理本地认证态和用户数据。
      } finally {
        clearAccessToken()
        await useLedgerStore().clearSession()
        this.authUser = null
        this.accountScope = ''
        this.authRequired = true
        this.dbMode = false
        this._putChain = Promise.resolve()
        this.loadLocal()
      }
    },

    /* ---------- 节假日数据 ---------- */
    async ensureHolidays(year) {
      if (!year || this._holYears[year]) return
      if (!this._holReq[year]) {
        this._holReq[year] = apiGetHolidays(year)
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
        clearAccessToken()
        await useLedgerStore().clearSession()
        this.authUser = null
        this.accountScope = ''
        this.authRequired = true
        this.dbMode = false
        this.loadLocal()
      }
      // 节假日数据失败不阻塞主流程
      this.ensureHolidays(new Date().getFullYear())
      this.ready = true
    }
  }
})
