<template>
  <main ref="pageEl" class="aps-page" :style="{ height: `${pageHeight}px` }">
    <div class="aps-sheet">
      <aside class="aps-nav" aria-label="设置分类">
        <h2 class="aps-nav-title">设置</h2>
        <button
          v-for="pane in PANES"
          :key="pane.key"
          type="button"
          :class="{ active: active === pane.key }"
          @click="selectPane(pane.key)"
        >
          <component :is="ICONS[pane.key]" :size="17" aria-hidden="true" />
          <span>{{ pane.label }}</span>
        </button>
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
import { computed, nextTick, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAppStore } from '../stores/app.js'
import { Bell, Clock, Filter, FolderInput, Layers, Paintbrush, Users } from 'lucide-vue-next'
import { PANES, PANE_KEYS } from '../components/settings/settingsState.js'
import SettingsDataPanes from '../components/settings/SettingsDataPanes.vue'
import SettingsListPanes from '../components/settings/SettingsListPanes.vue'
import SettingsLookPanes from '../components/settings/SettingsLookPanes.vue'
import SettingsTimePanes from '../components/settings/SettingsTimePanes.vue'

const ICONS = {
  smartlist: Filter,
  notify: Bell,
  datetime: Clock,
  appearance: Paintbrush,
  advanced: Layers,
  collab: Users,
  data: FolderInput
}

const appStore = useAppStore()
const route = useRoute()
const router = useRouter()

const pageEl = ref(null)
const pageHeight = ref(640)
const active = ref(PANE_KEYS.includes(String(route.query.pane)) ? String(route.query.pane) : PANE_KEYS[0])

const activeLabel = computed(() => PANES.find(pane => pane.key === active.value)?.label || '设置')
const paneComponent = computed(() => {
  if (active.value === 'data') return SettingsDataPanes
  if (active.value === 'appearance') return SettingsLookPanes
  if (['notify', 'datetime'].includes(active.value)) return SettingsTimePanes
  return SettingsListPanes
})

function selectPane(key) {
  if (!PANE_KEYS.includes(key)) return
  active.value = key
  router.replace({ query: { ...route.query, pane: key } }).catch(() => {})
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

let resizeObserver = null
onMounted(async () => {
  await nextTick()
  appStore.applyTheme()
  syncPageHeight()
  window.addEventListener('resize', syncPageHeight)
  resizeObserver = new ResizeObserver(syncPageHeight)
  if (pageEl.value?.parentElement) resizeObserver.observe(pageEl.value.parentElement)
})
onBeforeUnmount(() => {
  window.removeEventListener('resize', syncPageHeight)
  appStore.applyTheme()
  resizeObserver?.disconnect()
})
</script>

<style scoped>
.aps-page{display:flex;justify-content:center;overflow:hidden}
.aps-sheet{display:grid;grid-template-columns:212px minmax(0,1fr);width:100%;max-width:1040px;overflow:hidden;border:1px solid var(--line);border-radius:12px;background:var(--card);box-shadow:0 10px 30px rgba(15,23,42,.06);
  --set-group-bg:color-mix(in srgb,var(--paper) 62%,var(--card));
  --set-row-line:color-mix(in srgb,var(--line) 76%,transparent)}
.aps-nav{display:flex;flex-direction:column;gap:2px;padding:16px 12px 14px;overflow-y:auto;border-right:1px solid var(--line);background:color-mix(in srgb,var(--paper) 46%,var(--card))}
.aps-nav-title{margin:2px 0 14px;padding:0 10px;color:var(--ink);font-size:.95rem;font-weight:700;letter-spacing:.01em}
.aps-nav button{display:flex;align-items:center;gap:11px;min-height:38px;padding:0 11px;border:0;border-radius:8px;background:transparent;color:var(--ink2);font-size:.8rem;text-align:left;cursor:pointer;transition:background .14s ease,color .14s ease}
.aps-nav button svg{flex:none;color:var(--muted)}
.aps-nav button:hover{background:color-mix(in srgb,var(--line) 42%,transparent);color:var(--ink)}
.aps-nav button.active{background:color-mix(in srgb,var(--ink2) 14%,transparent);color:var(--ink);font-weight:600}
.aps-nav button.active svg{color:var(--ink)}
.aps-body{display:flex;flex-direction:column;min-width:0;overflow:hidden}
.aps-head{display:flex;align-items:center;justify-content:space-between;gap:12px;padding:22px 30px 12px}
.aps-head h1{margin:0;font-size:1rem;font-weight:700}
.aps-close{display:grid;width:26px;height:26px;place-items:center;border:0;border-radius:6px;background:transparent;color:var(--muted);font-size:1.05rem;line-height:1;cursor:pointer}
.aps-close:hover{background:var(--set-group-bg);color:var(--ink)}
.aps-content{display:grid;align-content:start;gap:14px;flex:1;min-height:0;padding:2px 30px 26px;overflow-y:auto;scrollbar-gutter:stable}
@media(max-width:820px){
  .aps-page{height:auto!important;overflow:visible}
  .aps-sheet{grid-template-columns:1fr;max-width:100%}
  .aps-nav{flex-direction:row;gap:4px;padding:8px;overflow-x:auto;overflow-y:hidden;border-right:0;border-bottom:1px solid var(--line)}
  .aps-nav-title{display:none}
  .aps-nav button{min-height:32px;padding:0 10px;white-space:nowrap}
  .aps-nav button svg{display:none}
  .aps-head{padding:16px 16px 8px}
  .aps-content{padding:2px 16px 20px;overflow:visible}
}
</style>
