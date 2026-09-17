import { defineStore } from 'pinia'
import {
  apiGetWorktimeSettings,
  apiPutWorktimeSettings,
  apiListWorktimeRecords,
  apiCreateWorktimeRecord,
  apiUpdateWorktimeRecord,
  apiDeleteWorktimeRecord
} from '../api/worktime.js'
import { createClientId } from '../utils/clientId.js'

export const DEFAULT_WORKTIME_SETTINGS = {
  workStart: '09:00',
  workEnd: '18:00',
  lunchMin: 90,
  daysPerMonth: 21.75,
  autoDays: true,
  salaryPre: 0,
  salaryPost: 0,
  basis: 'post',
  salaries: {}
}

function todayKey() {
  const date = new Date()
  return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')}`
}

function indexRecords(records = []) {
  return records.reduce((result, record) => {
    if (record?.date) result[record.date] = { ...record }
    return result
  }, {})
}

async function fetchAllRecords() {
  const limit = 200
  const records = []
  for (let offset = 0; ; offset += limit) {
    const page = await apiListWorktimeRecords({ limit, offset })
    records.push(...page)
    if (page.length < limit) return records
  }
}

export const useWorktimeStore = defineStore('worktime', {
  state: () => {
    const today = todayKey()
    return {
      settings: { ...DEFAULT_WORKTIME_SETTINGS },
      records: {},
      revision: 0,
      loading: false,
      punchDate: today,
      recMonth: today.slice(0, 7)
    }
  },
  actions: {
    async fetch() {
      this.loading = true
      try {
        const [settings, records] = await Promise.all([
          apiGetWorktimeSettings(),
          fetchAllRecords()
        ])
        this.settings = { ...DEFAULT_WORKTIME_SETTINGS, ...(settings || {}) }
        this.records = indexRecords(records)
        this.revision = Number(this.settings.revision || 0)
        return { settings: this.settings, records: this.records }
      } finally { this.loading = false }
    },
    async saveSettings(payload) {
      const submitted = { ...this.settings, ...(payload || {}) }
      const result = await apiPutWorktimeSettings(submitted, this.settings.revision)
      this.settings = result || {}
      this.revision = Number(this.settings.revision || 0)
      return result
    },
    async saveRecord(payload) {
      const current = this.records[payload?.date]
      const result = current
        ? await apiUpdateWorktimeRecord(current.id, payload, current.revision)
        : await apiCreateWorktimeRecord(payload, createClientId())
      if (result?.date) this.records[result.date] = { ...result }
      return result
    },
    async deleteRecord(date) {
      const current = this.records[date]
      if (!current?.id) return
      const result = await apiDeleteWorktimeRecord(current.id, current.revision)
      delete this.records[date]
      return result
    },
    async clearResources(defaultSettings = {}) {
      for (const date of Object.keys(this.records)) await this.deleteRecord(date)
      await this.saveSettings({ ...DEFAULT_WORKTIME_SETTINGS, ...defaultSettings })
      return { settings: this.settings, records: this.records }
    },
    async importResources(snapshot = {}) {
      await this.clearResources(snapshot.settings || {})
      for (const [date, record] of Object.entries(snapshot.records || {})) {
        await this.saveRecord({ ...record, date })
      }
      return { settings: this.settings, records: this.records }
    },
    reset() {
      const today = todayKey()
      this.settings = { ...DEFAULT_WORKTIME_SETTINGS }
      this.records = {}
      this.revision = 0
      this.loading = false
      this.punchDate = today
      this.recMonth = today.slice(0, 7)
    }
  }
})
