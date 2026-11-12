import { defineStore } from 'pinia'
import {
  apiCreateQuitSmokingCraving,
  apiDeleteQuitSmokingCraving,
  apiGetQuitSmokingSettings,
  apiGetQuitSmokingSummary,
  apiListQuitSmokingCravings,
  apiListQuitSmokingDays,
  apiPutQuitSmokingDay,
  apiUpdateQuitSmokingCraving,
  apiUpdateQuitSmokingSettings
} from '../../packages/api-client/src/index.js'
import { message } from '../services/message.js'
import { uuid } from '../utils/uuid.js'
import { addDate, todayKey } from '../components/tools/dateMath.js'
import { computeStreak, estimateSaved, estimateSubstance } from '../components/tools/quitSmoking.js'

/** 统一从 RFC7807 错误体里取可读文案。 */
export function errorText(error, fallback = '操作失败') {
  return error?.problem?.detail || error?.response?.data?.detail || error?.message || fallback
}

export const useQuitSmokingStore = defineStore('quitSmoking', {
  state: () => ({
    settings: null,
    summary: null,
    days: {},
    cravings: [],
    loading: false,
    saving: false,
    error: '',
    loaded: false
  }),
  getters: {
    todayDate: state => state.summary?.todayDate || todayKey(),
    configured: state => Boolean(state.settings?.configured),
    currentStreak: state => state.summary?.currentStreak ?? 0,
    todaySmoked: state => state.summary?.todaySmoked ?? 0,
    savedAmount: state => state.summary?.savedAmount ?? 0,
    substance: state => state.summary?.substance || null,
    recovery: state => state.summary?.recovery || null,
    milestones: state => state.summary?.recovery?.milestones || [],
    unitPrice: state => {
      const price = Number(state.settings?.pricePerPack || 0)
      const perPack = Number(state.settings?.cigarettesPerPack || 0)
      return perPack > 0 ? price / perPack : 0
    }
  },
  actions: {
    async fetchAll() {
      this.loading = true
      this.error = ''
      try {
        const today = todayKey()
        const [settings, summary, days, cravings] = await Promise.all([
          apiGetQuitSmokingSettings(),
          apiGetQuitSmokingSummary(),
          apiListQuitSmokingDays({ from: addDate(today, { days: -89 }), to: today }),
          apiListQuitSmokingCravings({ from: addDate(today, { days: -29 }), to: today })
        ])
        this.settings = settings
        this.summary = summary
        this.days = Object.fromEntries((days || []).map(day => [day.date, day]))
        this.cravings = cravings || []
        this.loaded = true
      } catch (error) {
        this.error = errorText(error, '戒烟数据加载失败')
      } finally {
        this.loading = false
      }
    },

    async saveSettings(payload) {
      this.saving = true
      try {
        this.settings = await apiUpdateQuitSmokingSettings(payload, this.settings?.revision)
        this.summary = await apiGetQuitSmokingSummary()
        message.success('已保存戒烟设置')
      } catch (error) {
        message.error(errorText(error, '保存设置失败'))
        throw error
      } finally {
        this.saving = false
      }
    },

    async setTodaySmoked(nextCount) {
      const today = this.todayDate
      const target = Math.max(0, Math.min(200, Math.round(Number(nextCount) || 0)))
      const previousDay = this.days[today]
      const previousSummary = this.summary
      const previousDays = this.days
      const revision = previousDay?.revision ?? this.summary?.todayRevision

      const optimisticDay = {
        date: today,
        smokedCount: target,
        note: previousDay?.note || '',
        revision: revision || 0
      }
      const nextDays = { ...this.days, [today]: optimisticDay }
      const totalSmoked = Math.max(0,
        (this.summary?.totalSmoked || 0) - (previousDay?.smokedCount || 0) + target)
      const previousSubstance = previousSummary?.substance
      const substance = {
        ...(previousSubstance || {}),
        ...estimateSubstance({
          todaySmoked: target,
          elapsedDays: previousSummary?.elapsedDays || 0,
          baselinePerDay: this.settings?.baselinePerDay || 0,
          totalSmoked,
          tarPerCigarette: previousSubstance?.tarPerCigarette ?? this.settings?.tarPerCigarette ?? 0,
          coPerCigarette: previousSubstance?.coPerCigarette ?? this.settings?.coPerCigarette ?? 0
        })
      }
      this.days = nextDays
      this.summary = {
        ...this.summary,
        todayDate: today,
        todaySmoked: target,
        todayLogged: true,
        totalSmoked,
        substance,
        currentStreak: computeStreak(nextDays, today, {
          maxDays: Math.max(1, this.summary?.elapsedDays || 1)
        }),
        savedAmount: estimateSaved({
          elapsedDays: this.summary?.elapsedDays || 0,
          baselinePerDay: this.settings?.baselinePerDay || 0,
          pricePerPack: this.settings?.pricePerPack || 0,
          cigarettesPerPack: this.settings?.cigarettesPerPack || 1,
          totalSmoked
        })
      }

      try {
        const saved = await apiPutQuitSmokingDay(today, { smokedCount: target, note: optimisticDay.note }, revision)
        this.days = { ...this.days, [today]: saved }
        this.summary = await apiGetQuitSmokingSummary()
      } catch (error) {
        this.days = previousDays
        this.summary = previousSummary
        if (error?.problem?.code === 'REVISION_CONFLICT') {
          message.warning('这一天已在别处更新，已为你刷新')
          await this.fetchAll()
        } else {
          message.error(errorText(error, '打卡失败'))
        }
      }
    },

    async addCraving({ intensity = 3, resisted = true, trigger = '', note = '', occurredAt = null } = {}) {
      const tempId = `temp-${uuid()}`
      const optimistic = {
        id: tempId,
        occurredAt: occurredAt || new Date().toISOString(),
        date: this.todayDate,
        intensity,
        resisted,
        trigger,
        note,
        revision: 0
      }
      const previousCravings = this.cravings
      const previousSummary = this.summary
      this.cravings = [optimistic, ...this.cravings]
      this.summary = {
        ...this.summary,
        todayCravings: (this.summary?.todayCravings || 0) + 1,
        todayResisted: (this.summary?.todayResisted || 0) + (resisted ? 1 : 0),
        todayCravingItems: [optimistic, ...(this.summary?.todayCravingItems || [])]
      }

      try {
        const saved = await apiCreateQuitSmokingCraving(
          { occurredAt: optimistic.occurredAt, intensity, resisted, trigger, note }, uuid())
        this.cravings = this.cravings.map(item => (item.id === tempId ? saved : item))
        this.summary = await apiGetQuitSmokingSummary()
        return saved
      } catch (error) {
        this.cravings = previousCravings
        this.summary = previousSummary
        message.error(errorText(error, '记录渴望失败'))
        throw error
      }
    },

    async toggleResisted(craving) {
      const previous = this.cravings
      const nextResisted = !craving.resisted
      this.cravings = this.cravings.map(item => (item.id === craving.id ? { ...item, resisted: nextResisted } : item))
      try {
        const saved = await apiUpdateQuitSmokingCraving(craving.id, { resisted: nextResisted }, craving.revision)
        this.cravings = this.cravings.map(item => (item.id === craving.id ? saved : item))
        this.summary = await apiGetQuitSmokingSummary()
      } catch (error) {
        this.cravings = previous
        message.error(errorText(error, '更新失败'))
      }
    },

    async removeCraving(craving) {
      const previous = this.cravings
      const previousSummary = this.summary
      this.cravings = this.cravings.filter(item => item.id !== craving.id)
      this.summary = {
        ...this.summary,
        todayCravings: Math.max(0, (this.summary?.todayCravings || 0) - 1),
        todayResisted: Math.max(0, (this.summary?.todayResisted || 0) - (craving.resisted ? 1 : 0)),
        todayCravingItems: (this.summary?.todayCravingItems || []).filter(item => item.id !== craving.id)
      }
      try {
        await apiDeleteQuitSmokingCraving(craving.id, craving.revision)
        this.summary = await apiGetQuitSmokingSummary()
      } catch (error) {
        this.cravings = previous
        this.summary = previousSummary
        message.error(errorText(error, '删除失败'))
      }
    }
  }
})
