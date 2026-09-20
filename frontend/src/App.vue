<template>
  <ToastViewport />
  <LoadingOverlay :open="!store.ready || navigationLoading" :label="!store.ready ? '正在初始化工作台…' : '正在切换页面…'" />
  <div v-if="store.ready" class="app-shell" :class="{ 'is-home': isHome }">
    <LoginView v-if="store.authRequired" />
    <template v-else>
      <header class="workspace-topbar">
        <router-link class="workspace-brand" to="/" aria-label="返回个人工作台"><span class="workspace-brand-mark"><House aria-hidden="true" /></span><b>个人工作台</b></router-link>
        <template v-if="!isHome">
          <span class="workspace-nav-divider" aria-hidden="true"></span>
          <router-link class="workspace-return" to="/"><ArrowLeft aria-hidden="true" />返回</router-link>
          <span class="workspace-module-label">{{ moduleLabel }}</span>
          <nav class="workspace-module-nav" :aria-label="`${moduleLabel}二级导航`">
            <router-link v-for="item in moduleNav" :key="item.key" :to="item.to" :class="{ active: isNavActive(item) }">{{ item.label }}</router-link>
          </nav>
        </template>
        <div class="workspace-top-actions">
          <div class="theme-picker">
            <button class="top-text-button theme-trigger" type="button" aria-label="切换配色风格" title="切换配色风格" :aria-expanded="themeMenuOpen" @click="themeMenuOpen = !themeMenuOpen"><span class="theme-dot" :style="{ background: currentPalette.color }"></span><span>配色</span></button>
            <div v-if="themeMenuOpen" class="theme-popover">
              <button v-for="item in palettes" :key="item.key" type="button" :class="{ active: store.accent === item.key }" @click="selectPalette(item.key)"><span :style="{ background: item.color }"></span>{{ item.label }}</button>
            </div>
          </div>
          <button class="top-text-button" type="button" aria-label="帮助中心" title="同步状态" @click="onSyncClick"><Connection aria-hidden="true" /><span>帮助中心</span></button>
          <router-link class="top-text-button" to="/settings" aria-label="设置" title="设置"><Setting aria-hidden="true" /><span>设置</span></router-link>
          <button class="workspace-avatar" type="button" title="退出登录" aria-label="退出登录" @click="store.logout()">{{ userInitial }}</button>
        </div>
      </header>
      <main class="app-main">
        <div v-if="showBasis" class="page-toolbar"><div class="basis-seg" aria-label="工资口径"><button :class="{ on: basis === 'pre' }" type="button" @click="setBasis('pre')">税前</button><button :class="{ on: basis === 'post' }" type="button" @click="setBasis('post')">税后</button></div></div>
        <div class="page-content"><router-view v-slot="{ Component }"><transition name="page" mode="out-in"><component :is="Component" :key="route.fullPath" /></transition></router-view></div>
      </main>
      <BottomNav v-if="!isHome" />
    </template>
  </div>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { ArrowLeft, Connection, House, Setting } from './icons.js'
import { message } from './services/message.js'
import { useAppStore } from './stores/app'
import { useWorktimeStore } from './stores/worktime.js'
import BottomNav from './components/BottomNav.vue'
import LoadingOverlay from './components/ledger/LoadingOverlay.vue'
import ToastViewport from './components/ui/ToastViewport.vue'
import LoginView from './views/LoginView.vue'
import router from './router'

const store = useAppStore()
const worktimeStore = useWorktimeStore()
const route = useRoute()
const isHome = computed(() => route.path === '/')
const themeMenuOpen = ref(false)
const navigationLoading = ref(false)
let navigationFinishTimer
let navigationSafetyTimer
const palettes = [
  { key: 'sun', label: '日光', color: '#ffd22e' }, { key: 'ocean', label: '海洋', color: '#68d5cf' },
  { key: 'forest', label: '森林', color: '#91bd58' }, { key: 'berry', label: '莓果', color: '#c85f8c' },
  { key: 'night', label: '暗夜', color: '#242933' }
]
const currentPalette = computed(() => palettes.find(item => item.key === store.accent) || palettes[0])
const userInitial = computed(() => String(store.authUser?.nickname || store.authUser?.username || '我').slice(0, 1))
const workNav = [{ key: 'punch', to: '/punch', label: '打卡' }, { key: 'records', to: '/records', label: '记录' }, { key: 'stats', to: '/stats', label: '统计' }]
const ledgerNav = [
  { key: 'overview', to: '/ledger', label: '总览' }, { key: 'transactions', to: '/ledger/transactions', label: '流水' },
  { key: 'accounts', to: { path: '/ledger/manage', query: { view: 'accounts' } }, label: '账户' }, { key: 'reports', to: '/ledger/reports', label: '报表' },
  { key: 'scheduled', to: '/ledger/scheduled-tasks', label: '定时任务' },
  { key: 'manage', to: { path: '/ledger/manage', query: { view: 'categories' } }, label: '管理', views: ['categories', 'merchants', 'projects', 'books'] },
  { key: 'members', to: { path: '/ledger/manage', query: { view: 'members' } }, label: '成员与权限' },
  { key: 'recycle', to: { path: '/ledger/manage', query: { view: 'recycle' } }, label: '回收站' },
  { key: 'audit', to: { path: '/ledger/manage', query: { view: 'audit' } }, label: '操作日志' }
]
const isLedger = computed(() => route.path.startsWith('/ledger'))
const moduleLabel = computed(() => isLedger.value ? '账本' : route.path === '/settings' ? '设置' : '工时')
const moduleNav = computed(() => isLedger.value ? ledgerNav : route.path === '/settings' ? [] : workNav)
function isNavActive(item) {
  const target = typeof item.to === 'string' ? { path: item.to } : item.to
  if (route.path !== target.path) return false
  if (item.views) return item.views.includes(String(route.query.view || 'categories'))
  return target.query?.view ? route.query.view === target.query.view : true
}
function selectPalette(key) { store.setAccent(key); themeMenuOpen.value = false }
function clearNavigationTimers() { window.clearTimeout(navigationFinishTimer); window.clearTimeout(navigationSafetyTimer) }
function finishNavigationLoading(delay = 0) { clearNavigationTimers(); if (delay) navigationFinishTimer = window.setTimeout(() => { navigationLoading.value = false }, delay); else navigationLoading.value = false }
function startNavigationLoading() { clearNavigationTimers(); navigationLoading.value = true; navigationSafetyTimer = window.setTimeout(() => { navigationLoading.value = false }, 5000) }
const removeBeforeGuard = router.beforeEach((to, from) => { if (to.fullPath !== from.fullPath) startNavigationLoading() })
const removeAfterGuard = router.afterEach(() => finishNavigationLoading(80))
const removeErrorGuard = router.onError(() => finishNavigationLoading())
const basis = computed(() => worktimeStore.settings.basis)
const showBasis = computed(() => ['/punch', '/records', '/stats'].includes(route.path))
async function setBasis(value) { if (worktimeStore.settings.basis !== value) await worktimeStore.saveSettings({ basis: value }) }
function onSyncClick() { if (store.dbMode) message.success('数据已安全同步到数据库'); else store.connectDb().then(() => store.dbMode ? message.success('已连接数据库') : message.warning(store.authRequired ? '请先登录' : '数据库仍不可用')) }
onMounted(() => store.init())
onBeforeUnmount(() => { finishNavigationLoading(); removeBeforeGuard(); removeAfterGuard(); removeErrorGuard() })
</script>

<style scoped>
.page-enter-active,.page-leave-active { transition: opacity .22s ease, transform .22s ease }.page-enter-from { opacity:0; transform:translateY(8px) }.page-leave-to { opacity:0; transform:translateY(-4px) }
@media (prefers-reduced-motion:reduce) { .page-enter-active,.page-leave-active { transition:none } }
</style>
