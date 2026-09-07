import { defineStore } from 'pinia'
import { ElMessage } from 'element-plus'
import { apiGetWorktimeSnapshot, apiPutWorktimeSnapshot, apiGetHolidays, apiLogout, apiRefresh, clearAccessToken } from '../api'
import { CALC } from '../utils/calc'

export const DEFAULTS = {
  workStart: '09:00', workEnd: '18:00', lunchMin: 90,
  daysPerMonth: 21.75, autoDays: true, salaryPre: 0, salaryPost: 0, basis: 'post',
  salaries: {}                      // 按月工资 { "YYYY-MM": {pre, post} }，每月工资可浮动
}

const LS_SET = 'st_settings'
const LS_REC = 'st_records'
const LS_THEME = 'st_theme'
const LS_ACCENT = 'st_accent'

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
      try { this.settings = { ...DEFAULTS, ...JSON.parse(localStorage.getItem(LS_SET) || '{}') } } catch (e) { /* ignore */ }
      try { this.records = JSON.parse(localStorage.getItem(LS_REC) || '{}') || {} } catch (e) { this.records = {} }
      const t = CALC.dateKey(new Date())
      this.punchDate = t
      this.recMonth = t.slice(0, 7)
    },
    saveLocal() {
      localStorage.setItem(LS_SET, JSON.stringify(this.settings))
      localStorage.setItem(LS_REC, JSON.stringify(this.records))
    },

    /* ---------- 数据库同步 ---------- */
    async saveAll() {
      this.saveLocal()
      if (!this.dbMode) return
      // 保存请求串行队列，避免并发快照乱序
      this._putChain = this._putChain
        .then(() => apiPutWorktimeSnapshot({ settings: this.settings, records: this.records }))
        .catch(error => {
          if (error.response?.status === 401) this.authRequired = true
          this.dbMode = false
          ElMessage.warning(error.response?.status === 401 ? '登录已过期，请重新登录' : '数据库不可用，已切换本地模式')
        })
      return this._putChain
    },

    async connectDb() {
      try {
        const data = await apiGetWorktimeSnapshot()
        const dbEmpty = !Object.keys(data.records || {}).length && Number(data.settings?.revision || 0) === 0
        const localHas = Object.keys(this.records).length > 0 || !!localStorage.getItem(LS_SET)
        if (dbEmpty && localHas) {
          await apiPutWorktimeSnapshot({ settings: this.settings, records: this.records })
        } else if (!dbEmpty) {
          this.settings = { ...DEFAULTS, ...(data.settings || {}) }
          this.records = data.records || {}
          this.saveLocal()                                                       // 服务端为准，本地留缓存
        }
        this.dbMode = true
        this.authRequired = false
      } catch (e) {
        this.authRequired = e.response?.status === 401
        this.dbMode = false
      }
    },

    async completeLogin(user) {
      this.authUser = user || null
      this.authRequired = false
      await this.connectDb()
    },

    async logout() {
      try { await apiLogout() } catch (e) { clearAccessToken() }
      this.authUser = null
      this.authRequired = true
      this.dbMode = false
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
      this.applyTheme()
      this.loadLocal()
      try {
        const session = await apiRefresh()
        this.authUser = session.user || null
      } catch (e) {
      }
      await this.connectDb()
      // 节假日数据失败不阻塞主流程
      this.ensureHolidays(new Date().getFullYear())
      this.ready = true
    }
  }
})
