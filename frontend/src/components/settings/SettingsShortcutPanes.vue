<template>
  <section class="set-pane">
    <div v-for="group in groups" :key="group.title" class="set-sc-group">
      <header class="set-sc-head">
        <h4>{{ group.title }}</h4>
        <p v-if="group.desc">{{ group.desc }}</p>
      </header>
      <div class="set-sc-list">
        <div v-for="item in group.items" :key="item.label" class="set-sc-row">
          <span class="set-sc-label">{{ item.label }}</span>
          <span v-if="recording === item.label" class="set-sc-keys is-recording">请按下快捷键…</span>
          <span v-else-if="shownKeys(item)" class="set-sc-keys" :class="{ 'is-button': item.recordable }">
            <kbd v-for="key in shownKeys(item)" :key="key">{{ key }}</kbd>
            <button v-if="item.recordable" type="button" class="set-sc-clear" :aria-label="`清除${item.label}的快捷键`" @click="clearBinding(item)"><X :size="12" /></button>
          </span>
          <span v-else-if="item.sequences" class="set-sc-keys">
            <kbd>{{ item.sequences[0][0] }}</kbd><em>然后</em><kbd>{{ item.sequences[0][1] }}</kbd>
          </span>
          <button v-else type="button" class="set-sc-record" @click="startRecord(item)">记录快捷键</button>
        </div>
      </div>
    </div>
  </section>
</template>

<script setup>
import { onBeforeUnmount, ref } from 'vue'
import { X } from 'lucide-vue-next'
import { SHORTCUT_GROUPS } from './settingsState.js'
import { useSettings } from './useSettings.js'

defineProps({ pane: { type: String, required: true } })

const { settings, set } = useSettings()
const groups = SHORTCUT_GROUPS
const recording = ref('')

function overrideOf(item) {
  const overrides = settings.shortcutOverrides || {}
  return Object.prototype.hasOwnProperty.call(overrides, item.label) ? overrides[item.label] : null
}
/** 用户改过就用用户的；显式清空（空数组）返回 null，回落到「记录快捷键」 */
function shownKeys(item) {
  const override = overrideOf(item)
  if (override !== null) return override.length ? override : null
  return item.keys || null
}
function startRecord(item) {
  recording.value = item.label
  window.addEventListener('keydown', onRecordKey, { once: true, capture: true })
}
function onRecordKey(event) {
  event.preventDefault()
  event.stopPropagation()
  const label = recording.value
  recording.value = ''
  if (event.key === 'Escape') return
  const keys = []
  if (event.metaKey) keys.push('⌘')
  if (event.ctrlKey) keys.push('Ctrl')
  if (event.altKey) keys.push('⌥')
  if (event.shiftKey) keys.push('⇧')
  const key = event.key === ' ' ? 'Space' : event.key.length === 1 ? event.key.toUpperCase() : event.key
  keys.push(key)
  set('shortcutOverrides', { ...(settings.shortcutOverrides || {}), [label]: keys })
}
function clearBinding(item) {
  const next = { ...(settings.shortcutOverrides || {}) }
  if (item.keys) next[item.label] = []
  else delete next[item.label]
  set('shortcutOverrides', next)
}
onBeforeUnmount(() => window.removeEventListener('keydown', onRecordKey, { capture: true }))
</script>

<style scoped>
.set-pane{display:grid;gap:20px}
.set-sc-head{display:grid;gap:3px;margin-bottom:8px}
.set-sc-head h4{margin:0;color:var(--ink);font-size:.86rem;font-weight:600}
.set-sc-head p{margin:0;color:var(--muted);font-size:.72rem;line-height:1.5}
.set-sc-list{padding:2px 14px;border-radius:10px;background:var(--set-group-bg,color-mix(in srgb,var(--paper) 62%,var(--card)))}
.set-sc-row{display:flex;align-items:center;justify-content:space-between;gap:14px;min-height:46px;padding:7px 0}
.set-sc-row + .set-sc-row{border-top:1px solid var(--set-row-line,color-mix(in srgb,var(--line) 76%,transparent))}
.set-sc-label{color:var(--ink);font-size:.8rem}
.set-sc-keys{display:inline-flex;align-items:center;gap:5px;color:var(--muted);font-size:.72rem}
.set-sc-keys kbd{min-width:18px;color:var(--muted);font-family:inherit;font-size:.72rem;text-align:center}
.set-sc-keys em{color:var(--muted);font-size:.66rem;font-style:normal}
.set-sc-keys.is-button{padding:4px 30px 4px 14px;border:1px solid var(--line2);border-radius:7px;background:var(--card)}
.set-sc-keys.is-button kbd{color:var(--ink2)}
.set-sc-clear{position:absolute;top:50%;right:7px;display:grid;width:16px;height:16px;place-items:center;transform:translateY(-50%);border:0;border-radius:4px;background:transparent;color:var(--muted);cursor:pointer}
.set-sc-keys.is-button{position:relative}
.set-sc-clear:hover{background:var(--set-group-bg);color:var(--ink)}
.set-sc-keys.is-recording{padding:4px 12px;border:1px dashed #3b82f6;border-radius:7px;color:#3b82f6}
.set-sc-record{padding:4px 12px;border:1px solid var(--line2);border-radius:7px;background:var(--card);color:var(--ink2);font-size:.72rem;cursor:pointer}
.set-sc-record:hover{border-color:var(--accent);color:var(--ink)}
</style>
