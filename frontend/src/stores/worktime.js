import { defineStore } from 'pinia'
import { apiGetWorktimeSnapshot, apiPutWorktimeSnapshot } from '../api/worktime'

export const useWorktimeStore = defineStore('worktime', {
  state: () => ({ settings: {}, records: {}, revision: 0, loading: false }),
  actions: {
    async fetch() {
      this.loading = true
      try {
        const snapshot = await apiGetWorktimeSnapshot()
        this.settings = snapshot.settings || {}
        this.records = snapshot.records || {}
        this.revision = Number(this.settings.revision || 0)
        return snapshot
      } finally { this.loading = false }
    },
    async replace(snapshot) {
      const result = await apiPutWorktimeSnapshot(snapshot)
      this.settings = result.settings || snapshot.settings || {}
      this.records = result.records || snapshot.records || {}
      this.revision = Number(this.settings.revision || 0)
      return result
    }
  }
})
