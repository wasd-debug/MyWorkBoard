import { defineStore } from 'pinia'
import { apiLogin, apiRegister, apiLogout } from '../api/auth'

export const useAuthStore = defineStore('auth', {
  state: () => ({ user: null, loading: false }),
  getters: { isAuthenticated: state => Boolean(state.user) },
  actions: {
    async login(credentials) {
      this.loading = true
      try { const data = await apiLogin(credentials); this.user = data.user; return data }
      finally { this.loading = false }
    },
    async register(payload) {
      this.loading = true
      try { const data = await apiRegister(payload); this.user = data.user; return data }
      finally { this.loading = false }
    },
    async logout() {
      try { await apiLogout() } finally { this.user = null }
    }
  }
})
