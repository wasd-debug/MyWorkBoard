<template>
  <main ref="pageEl" class="aps-page" :style="{ height: `${pageHeight}px` }">
    <div class="aps-sheet">
      <aside class="aps-nav" aria-label="设置分类">
        <button
          v-for="pane in PANES"
          :key="pane.key"
          type="button"
          :class="{ active: active === pane.key }"
          @click="selectPane(pane.key)"
        >{{ pane.label }}</button>
        <button type="button" class="aps-nav-extra" @click="openTaskSettings">任务设置</button>
      </aside>

      <section class="aps-body">
        <header class="aps-head">
          <h1>{{ activeLabel }}</h1>
          <button type="button" class="aps-close" aria-label="返回" title="返回" @click="goBack">×</button>
        </header>
        <div class="aps-content">
          <component :is="paneComponent" :pane="active" />
        </div>
      </section>
    </div>
  </main>
</template>

<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { PANES, PANE_KEYS } from '../components/settings/settingsState.js'
import { useSettings } from '../components/settings/useSettings.js'
import SettingsAccountPanes from '../components/settings/SettingsAccountPanes.vue'
import SettingsDataPanes from '../components/settings/SettingsDataPanes.vue'
import SettingsListPanes from '../components/settings/SettingsListPanes.vue'
import SettingsLookPanes from '../components/settings/SettingsLookPanes.vue'
import SettingsModulePanes from '../components/settings/SettingsModulePanes.vue'
import SettingsTimePanes from '../components/settings/SettingsTimePanes.vue'

const route = useRoute()
const router = useRouter()
const { settings } = useSettings()

const pageEl = ref(null)
const pageHeight = ref(640)
const active = ref(PANE_KEYS.includes(String(route.query.pane)) ? String(route.query.pane) : 'account')

const activeLabel = computed(() => PANES.find(pane => pane.key === active.value)?.label || '设置')
const paneComponent = computed(() => {
  if (['account', 'premium', 'about'].includes(active.value)) return SettingsAccountPanes
  if (['modules', 'widget'].includes(active.value)) return SettingsModulePanes
  if (['smartlist', 'advanced', 'collab'].includes(active.value)) return SettingsListPanes
  if (['notify', 'datetime'].includes(active.value)) return SettingsTimePanes
  if (['appearance', 'sticky', 'shortcut'].includes(active.value)) return SettingsLookPanes
  return SettingsDataPanes
})

function selectPane(key) {
  if (!PANE_KEYS.includes(key)) return
  active.value = key
  router.replace({ query: { ...route.query, pane: key } }).catch(() => {})
}

function openTaskSettings() {
  router.push('/tasks/settings')
}
function goBack() {
  if (window.history.length > 1) router.back()
  else router.push('/tasks/today')
}

function syncPageHeight() {
  const el = pageEl.value
  if (!el) return
  const offset = el.getBoundingClientRect().top + window.scrollY
  const parent = el.parentElement
  const padBottom = parent ? Number.parseFloat(getComputedStyle(parent).paddingBottom) || 0 : 0
  pageHeight.value = Math.max(420, Math.round(window.innerHeight - offset - padBottom))
}

// 「跟随系统切换暗色主题」直接作用在 html.dark 上，和顶栏的主题开关共用同一套变量
let mediaQuery = null
function applyTheme() {
  if (typeof document === 'undefined') return
  const root = document.documentElement
  if (!settings.followSystemDark) {
    if (mediaQuery) root.classList.toggle('dark', mediaQuery.matches)
    return
  }
  if (typeof window === 'undefined' || !window.matchMedia) return
  root.classList.toggle('dark', window.matchMedia('(prefers-color-scheme: dark)').matches)
}
function watchSystemTheme(event) {
  if (settings.followSystemDark) document.documentElement.classList.toggle('dark', event.matches)
}

watch(() => settings.followSystemDark, applyTheme)

let resizeObserver = null
onMounted(async () => {
  await nextTick()
  syncPageHeight()
  window.addEventListener('resize', syncPageHeight)
  if (typeof window !== 'undefined' && window.matchMedia) {
    mediaQuery = window.matchMedia('(prefers-color-scheme: dark)')
    mediaQuery.addEventListener?.('change', watchSystemTheme)
  }
  applyTheme()
  resizeObserver = new ResizeObserver(syncPageHeight)
  if (pageEl.value?.parentElement) resizeObserver.observe(pageEl.value.parentElement)
})
onBeforeUnmount(() => {
  window.removeEventListener('resize', syncPageHeight)
  mediaQuery?.removeEventListener?.('change', watchSystemTheme)
  resizeObserver?.disconnect()
})
</script>

<style scoped>
.aps-page{display:flex;justify-content:center;overflow:hidden}
.aps-sheet{display:grid;grid-template-columns:186px minmax(0,1fr);width:100%;max-width:1000px;overflow:hidden;border:1px solid var(--line);border-radius:12px;background:var(--card);box-shadow:0 10px 30px rgba(15,23,42,.06)}
.aps-nav{display:flex;flex-direction:column;gap:1px;padding:12px 8px;overflow-y:auto;border-right:1px solid var(--line);background:color-mix(in srgb,var(--panel) 70%,var(--card))}
.aps-nav button{padding:7px 12px;border:0;border-radius:7px;background:transparent;color:var(--ink2);font-size:.79rem;text-align:left;cursor:pointer}
.aps-nav button:hover{background:var(--panel2);color:var(--ink)}
.aps-nav button.active{background:var(--card);color:var(--ink);font-weight:600;box-shadow:0 1px 3px rgba(15,23,42,.1)}
.aps-nav-extra{margin-top:auto;color:var(--muted)!important;font-size:.75rem!important}
.aps-body{display:flex;flex-direction:column;min-width:0;overflow:hidden}
.aps-head{display:flex;align-items:center;justify-content:space-between;gap:12px;padding:16px 22px 10px}
.aps-head h1{margin:0;font-size:.98rem;font-weight:600}
.aps-close{display:grid;width:26px;height:26px;place-items:center;border:0;border-radius:6px;background:transparent;color:var(--muted);font-size:1.05rem;line-height:1;cursor:pointer}
.aps-close:hover{background:var(--panel2);color:var(--ink)}
.aps-content{flex:1;min-height:0;padding:2px 22px 24px;overflow-y:auto;scrollbar-gutter:stable}
@media(max-width:820px){
  .aps-page{height:auto!important;overflow:visible}
  .aps-sheet{grid-template-columns:1fr;max-width:100%}
  .aps-nav{flex-direction:row;gap:4px;padding:8px;overflow-x:auto;overflow-y:hidden;border-right:0;border-bottom:1px solid var(--line)}
  .aps-nav button{white-space:nowrap}
  .aps-nav-extra{margin-top:0}
  .aps-content{overflow:visible}
}
</style>
