<template>
  <section class="set-pane">
    <div class="set-module-grid">
      <article v-for="item in MODULES" :key="item.key" class="set-module-card" :class="{ 'is-off': !settings[item.key] }">
        <div class="set-module-art" :class="`is-${item.key}`">
          <span>{{ artOf(item.key) }}</span>
        </div>
        <div class="set-module-body">
          <b>{{ item.label }}</b>
          <small>{{ item.desc }}</small>
        </div>
        <footer class="set-module-foot">
          <RouterLink :to="item.link" class="set-module-link">{{ item.linkLabel }}</RouterLink>
          <SettingSwitch :model-value="settings[item.key]" :label="item.label" @update:model-value="set(item.key, $event)" />
        </footer>
      </article>
    </div>
    <p class="set-hint">关闭模块后，左侧导航入口会隐藏，历史数据保留。</p>
  </section>
</template>

<script setup>
import { MODULES } from './settingsState.js'
import { useSettings } from './useSettings.js'
import SettingSwitch from './SettingSwitch.vue'

defineProps({ pane: { type: String, required: true } })

const { settings, set } = useSettings()

const ART = {
  moduleHabit: '✓ ✓ ✓',
  moduleFocus: '25:00',
  moduleCountdown: '99 天',
  moduleAi: 'AI ✦'
}
function artOf(key) {
  return ART[key] || ''
}
</script>

<style scoped>
.set-pane{display:grid;gap:10px}
.set-module-grid{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:12px}
.set-module-card{display:flex;flex-direction:column;overflow:hidden;border:1px solid var(--line);border-radius:11px;background:var(--card)}
.set-module-card.is-off{opacity:.62}
.set-module-art{display:grid;height:78px;place-items:center;color:#fff;font-size:.82rem;font-weight:700;letter-spacing:.12em}
.set-module-art.is-moduleHabit{background:linear-gradient(135deg,#6fc9a8,#3f9d7c)}
.set-module-art.is-moduleFocus{background:linear-gradient(135deg,#f0a08a,#d9694e);font-size:1.3rem;letter-spacing:.02em}
.set-module-art.is-moduleCountdown{background:linear-gradient(135deg,#8fb4ec,#4d78db);font-size:1.3rem;letter-spacing:.02em}
.set-module-art.is-moduleAi{background:linear-gradient(135deg,#b3aee0,#7f76c9);font-size:1.2rem}
.set-module-body{padding:10px 12px 4px}
.set-module-body b{display:block;font-size:.82rem}
.set-module-body small{color:var(--muted);font-size:.7rem}
.set-module-foot{display:flex;align-items:center;justify-content:space-between;gap:8px;padding:8px 12px 11px}
.set-module-link{color:var(--accent);font-size:.72rem;text-decoration:none}
.set-module-link:hover{text-decoration:underline}
.set-hint{margin:2px 0 0;color:var(--muted);font-size:.7rem}
@media(max-width:900px){.set-module-grid{grid-template-columns:1fr}}
</style>
