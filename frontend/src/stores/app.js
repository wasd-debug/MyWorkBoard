import { defineStore } from 'pinia'
import { ElMessage } from 'element-plus'
import { apiGetData, apiPutAll, apiGetHolidays, setAccessCode } from '../api'
import { CALC } from '../utils/calc'

export const DEFAULTS = {
  workStart: '09:00', workEnd: '18:00', lunchMin: 90,
  daysPerMonth: 21.75, autoDays: true, salaryPre: 0, salaryPost: 0, basis: 'post',
  salaries: {}                      // 按月工资 { "YYYY-MM": {pre, post} }，每月工资可浮动
}

const LS_SET = 'st_settings'
const LS_REC = 'st_records'
const LS_THEME = 'st_theme'

export const useAppStore = defineStore('app', {
  state: () => ({
    settings: { ...DEFAULTS },
    records: {},                     // { "YYYY-MM-DD": {start, end, rest?} }
    holidays: {},                    // { "YYYY-MM-DD": {name, off} } 法定节假日/调休
    _holYears: {},                   // 已加载年份 -> 数据来源
    _holReq: {},                     // 进行中的年份请求（防重复）
    dbMode: false,                   // 本地服务（数据库）可用
    theme: 'dark',
    punchDate: '',
    recMonth: '',
    ready: false,                    // 初始化完成
    needCode: false,                 // 是否弹出访问口令对话框
    _resolveCode: null,
    _putChain: Promise.resolve()
  }),

  actions: {
    /* ---------- 主题 ---------- */
    applyTheme(t) {
      this.theme = t
      document.documentElement.className = t
      const mc = document.querySelector('meta[name="theme-color"]')
      if (mc) mc.content = t === 'light' ? '#f3f5f9' : '#0e1013'
    },
    toggleTheme() {
      const t = this.theme === 'light' ? 'dark' : 'light'
      localStorage.setItem(LS_THEME, t)
      this.applyTheme(t)
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
        .then(() => apiPutAll({ settings: this.settings, records: this.records }))
        .catch(() => { this.dbMode = false; ElMessage.warning('数据库不可用，已切换本地模式') })
      return this._putChain
    },

    askAccessCode() {
      return new Promise(resolve => {
        this.needCode = true
        this._resolveCode = resolve
      })
    },
    submitAccessCode(code) {
      this.needCode = false
      if (this._resolveCode) { this._resolveCode(code); this._resolveCode = null }
    },
    cancelAccessCode() {
      this.needCode = false
      if (this._resolveCode) { this._resolveCode(null); this._resolveCode = null }
    },

    async connectDb() {
      try {
        let data
        try {
          data = await apiGetData()
        } catch (e) {
          if (e.response && e.response.status === 401) {
            const code = await this.askAccessCode()
            if (!code) throw e
            setAccessCode(code.trim())
            data = await apiGetData()
          } else throw e
        }
        const dbEmpty = !Object.keys(data.records || {}).length && !Object.keys(data.settings || {}).length
        const localHas = Object.keys(this.records).length > 0 || !!localStorage.getItem(LS_SET)
        if (dbEmpty && localHas) {
          await apiPutAll({ settings: this.settings, records: this.records })   // 首次使用：本地数据迁移到服务端
        } else if (!dbEmpty) {
          this.settings = { ...DEFAULTS, ...(data.settings || {}) }
          this.records = data.records || {}
          this.saveLocal()                                                       // 服务端为准，本地留缓存
        }
        this.dbMode = true
      } catch (e) {
        this.dbMode = false
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
      this.applyTheme(localStorage.getItem(LS_THEME) ||
        (window.matchMedia && matchMedia('(prefers-color-scheme: light)').matches ? 'light' : 'dark'))
      this.loadLocal()
      await this.connectDb()
      // 节假日数据失败不阻塞主流程
      this.ensureHolidays(new Date().getFullYear())
      this.ready = true
    }
  }
})
