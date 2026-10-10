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
          <button class="top-text-button" type="button" :aria-label="store.theme === 'dark' ? '切换为亮色模式' : '切换为暗色模式'" :title="store.theme === 'dark' ? '切换为亮色模式' : '切换为暗色模式'" @click="store.toggleTheme()"><Sun v-if="store.theme === 'dark'" aria-hidden="true" /><Moon v-else aria-hidden="true" /></button>
          <router-link class="top-text-button notification-link" to="/tasks/inbox-notify" aria-label="任务提醒" title="任务提醒"><Bell aria-hidden="true" /><span>提醒</span><b v-if="taskInbox.unread" class="notification-badge">{{ taskInbox.unread > 99 ? '99+' : taskInbox.unread }}</b></router-link>
          <router-link class="top-text-button" to="/approvals" aria-label="审批中心" title="审批中心"><ShieldCheck aria-hidden="true" /><span>审批</span></router-link>
          <router-link class="top-text-button" to="/settings" aria-label="设置" title="设置"><Setting aria-hidden="true" /><span>设置</span></router-link>
          <span class="workspace-avatar" aria-label="当前用户">{{ userInitial }}</span>
        </div>
      </header>
      <main class="app-main">
        <div v-if="showBasis" class="page-toolbar"><div class="basis-seg" aria-label="工资口径"><button :class="{ on: basis === 'pre' }" type="button" @click="setBasis('pre')">税前</button><button :class="{ on: basis === 'post' }" type="button" @click="setBasis('post')">税后</button></div></div>
        <div class="page-content"><router-view v-slot="{ Component }"><transition name="page" mode="out-in"><component :is="Component" :key="route.fullPath" /></transition></router-view></div>
      </main>
      <BottomNav v-if="!isHome && !isExternalFlow" />
      <FloatingAgentAssistant v-if="!isHome && store.authUser && !store.offlineSession" />
    </template>
  </div>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { ArrowLeft, House, Setting } from './icons.js'
import { Bell, Moon, ShieldCheck, Sun } from 'lucide-vue-next'
import { ledgerNavigation, navigationItemIsActive, taskNavigation, worktimeNavigation } from './config/moduleNavigation.js'
import { useAppStore } from './stores/app'
import { useWorktimeStore } from './stores/worktime.js'
import { useTaskInboxStore } from './stores/taskInbox.js'
import BottomNav from './components/BottomNav.vue'
import FloatingAgentAssistant from './components/FloatingAgentAssistant.vue'
import LoadingOverlay from './components/ledger/LoadingOverlay.vue'
import ToastViewport from './components/ui/ToastViewport.vue'
import LoginView from './views/LoginView.vue'
import router from './router'

const store = useAppStore()
const worktimeStore = useWorktimeStore()
const taskInbox = useTaskInboxStore()
const route = useRoute()
const isHome = computed(() => route.path === '/')
const navigationLoading = ref(false)
let navigationFinishTimer
let navigationSafetyTimer
const userInitial = computed(() => String(store.authUser?.nickname || store.authUser?.username || '我').slice(0, 1))
const isLedger = computed(() => route.path.startsWith('/ledger'))
const isTasks = computed(() => route.path.startsWith('/tasks'))
const isExternalFlow = computed(() => route.path === '/oauth/consent' || route.path === '/mcp/actions/confirm')
const moduleLabel = computed(() => route.path === '/calendar' ? '日历' : isLedger.value ? '账本' : isTasks.value ? '任务' : route.path.startsWith('/approvals') ? '审批中心' : route.path === '/settings' ? '设置' : isExternalFlow.value ? '外部授权' : '工时')
const moduleNav = computed(() => route.path === '/calendar' ? [] : isLedger.value ? ledgerNavigation : isTasks.value ? taskNavigation : route.path === '/settings' || route.path.startsWith('/approvals') || isExternalFlow.value ? [] : worktimeNavigation)
function isNavActive(item) { return navigationItemIsActive(route, item) }
function clearNavigationTimers() { window.clearTimeout(navigationFinishTimer); window.clearTimeout(navigationSafetyTimer) }
function finishNavigationLoading(delay = 0) { clearNavigationTimers(); if (delay) navigationFinishTimer = window.setTimeout(() => { navigationLoading.value = false }, delay); else navigationLoading.value = false }
function startNavigationLoading() { clearNavigationTimers(); navigationLoading.value = true; navigationSafetyTimer = window.setTimeout(() => { navigationLoading.value = false }, 5000) }
const removeBeforeGuard = router.beforeEach((to, from) => { if (to.fullPath !== from.fullPath) startNavigationLoading() })
const removeAfterGuard = router.afterEach(() => finishNavigationLoading(80))
const removeErrorGuard = router.onError(() => finishNavigationLoading())
const basis = computed(() => worktimeStore.settings.basis)
const showBasis = computed(() => ['/punch', '/records', '/stats'].includes(route.path))
async function setBasis(value) { if (worktimeStore.settings.basis !== value) await worktimeStore.saveSettings({ basis: value }) }
onMounted(async () => { await store.init(); if (store.authUser && !store.offlineSession) { await taskInbox.refresh(); taskInbox.connect() } })
watch(() => [store.authUser?.id, store.offlineSession], ([userId, offline]) => {
  if (userId && !offline) { taskInbox.refresh(); taskInbox.connect() } else taskInbox.disconnect()
})
onBeforeUnmount(() => { taskInbox.disconnect(); finishNavigationLoading(); removeBeforeGuard(); removeAfterGuard(); removeErrorGuard() })
</script>

<style scoped>
.page-enter-active,.page-leave-active { transition: opacity .22s ease, transform .22s ease }.page-enter-from { opacity:0; transform:translateY(8px) }.page-leave-to { opacity:0; transform:translateY(-4px) }
@media (prefers-reduced-motion:reduce) { .page-enter-active,.page-leave-active { transition:none } }
</style>
