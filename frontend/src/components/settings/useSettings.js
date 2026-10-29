import { reactive, watch } from 'vue'
import { DEFAULT_SETTINGS, readSettings, resetSettings, writeSettings } from './settingsState.js'

// 设置页所有分区共用的单例状态；写回 localStorage 由这里统一做
const state = reactive(readSettings())
let bound = false

export function useSettings() {
  if (!bound) {
    bound = true
    watch(state, value => writeSettings(value), { deep: true })
  }
  const set = (key, value) => {
    state[key] = value
  }
  const toggle = key => {
    state[key] = !state[key]
  }
  const reset = keys => {
    Object.assign(state, resetSettings(Array.isArray(keys) ? keys : [keys], state, DEFAULT_SETTINGS))
  }
  return { settings: state, set, toggle, reset }
}
