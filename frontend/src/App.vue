<template>
  <ToastViewport />
  <LoadingOverlay :open="!store.ready || navigationLoading" :label="!store.ready ? '正在初始化工作台…' : '正在切换页面…'" />
  <div v-if="store.ready" class="app-shell">
    <LoginView v-if="store.authRequired" />
    <template v-else>
      <aside class="app-sidebar" aria-label="主导航">
        <div class="sidebar-brand">
          <div class="title serif">个人工作台</div>
          <div class="label">PERSONAL WORKSPACE</div>
        </div>
        <nav class="sidebar-nav">
          <section v-for="group in navGroups" :key="group.key" class="nav-group" :class="{ open: isGroupOpen(group) }">
            <button v-if="group.items.length" class="nav-group-head" type="button" :aria-expanded="isGroupOpen(group)" @click="toggleGroup(group.key)">
              <span class="nav-group-title">{{ group.label }}</span><ArrowDown class="nav-group-chevron" aria-hidden="true" />
            </button>
            <Transition name="nav-collapse">
            <div v-show="isGroupOpen(group)" class="nav-group-items">
              <router-link
                v-for="item in group.items"
                :key="item.key"
                :to="item.to"
                :class="{ 'nav-item-disabled': item.disabled, 'nav-item-current': isNavItemActive(item) }"
                :aria-disabled="item.disabled || undefined"
                @click="item.disabled && $event.preventDefault()"
              >
                <component :is="item.icon" class="nav-item-icon" aria-hidden="true" />
                <span>{{ item.label }}</span>
                <span v-if="item.disabled" class="nav-item-soon">即将开放</span>
              </router-link>
            </div>
            </Transition>
          </section>
          <router-link class="nav-settings-link" to="/settings"><Setting class="nav-item-icon" aria-hidden="true" /><span>设置</span></router-link>
        </nav>
        <div class="sidebar-foot">
          <div class="sidebar-status">
            <span class="status-dot" :class="{ online: store.dbMode }"></span>
            <span>{{ store.dbMode ? '数据库已同步' : '本地离线' }}</span>
          </div>
          <div class="sidebar-actions">
            <button class="sidebar-action sidebar-theme-action" type="button" :aria-label="store.theme === 'dark' ? '切换日间模式' : '切换暗夜模式'" :title="store.theme === 'dark' ? '切换日间模式' : '切换暗夜模式'" @click="store.toggleTheme()">
              <Sunny v-if="store.theme === 'dark'" aria-hidden="true" /><Moon v-else aria-hidden="true" />
            </button>
            <button class="sidebar-action" type="button" aria-label="同步状态" :title="store.dbMode ? '数据库已连接' : '本地离线'" @click="onSyncClick">
              <Connection v-if="store.dbMode" aria-hidden="true" /><Refresh v-else aria-hidden="true" />
            </button>
            <button class="sidebar-action" type="button" aria-label="退出登录" title="退出登录" @click="store.logout()"><SwitchButton aria-hidden="true" /></button>
          </div>
        </div>
      </aside>

      <main class="app-main">
        <div v-if="showBasis" class="page-toolbar">
          <div class="basis-seg" aria-label="工资口径">
            <button :class="{ on: basis === 'pre' }" type="button" @click="setBasis('pre')">税前</button>
            <button :class="{ on: basis === 'post' }" type="button" @click="setBasis('post')">税后</button>
          </div>
        </div>
        <div class="page-content">
          <router-view v-slot="{ Component }">
            <transition name="page" mode="out-in">
              <component :is="Component" :key="route.path" />
            </transition>
          </router-view>
        </div>
      </main>
      <BottomNav />
    </template>
  </div>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { ElMessage } from './services/message.js'
import { useAppStore } from './stores/app'
import { useWorktimeStore } from './stores/worktime.js'
import { useRoute } from 'vue-router'
import { ArrowDown, Calendar, Connection, CreditCard, DataAnalysis, DeleteFilled, Document, List, Management, Moon, Notebook, Refresh, Setting, Sunny, SwitchButton, Tickets, Timer, UserFilled, Wallet } from './icons.js'
import BottomNav from './components/BottomNav.vue'
import LoadingOverlay from './components/ledger/LoadingOverlay.vue'
import ToastViewport from './components/ui/ToastViewport.vue'
import LoginView from './views/LoginView.vue'
import router from './router'

const store = useAppStore()
const worktimeStore = useWorktimeStore()
const route = useRoute()
const navigationLoading = ref(false)
let navigationFinishTimer
let navigationSafetyTimer

function clearNavigationTimers() {
  window.clearTimeout(navigationFinishTimer)
  window.clearTimeout(navigationSafetyTimer)
}

function finishNavigationLoading(delay = 0) {
  clearNavigationTimers()
  if (delay > 0) {
    navigationFinishTimer = window.setTimeout(() => {
      navigationLoading.value = false
    }, delay)
    return
  }
  navigationLoading.value = false
}

function startNavigationLoading() {
  clearNavigationTimers()
  navigationLoading.value = true
  navigationSafetyTimer = window.setTimeout(() => {
    navigationLoading.value = false
  }, 5000)
}

const removeBeforeGuard = router.beforeEach((to, from) => {
  if (to.path === from.path) return
  startNavigationLoading()
})
const removeAfterGuard = router.afterEach(() => {
  finishNavigationLoading(80)
})
const removeErrorGuard = router.onError(() => {
  finishNavigationLoading()
})

const navGroups = [
  { key: 'work', label: '工时记录', items: [{ key: 'punch', to: '/punch', label: '打卡', icon: Timer }, { key: 'records', to: '/records', label: '记录', icon: Calendar }, { key: 'stats', to: '/stats', label: '统计', icon: DataAnalysis }] },
  { key: 'ledger', label: '个人账本', items: [
    { key: 'overview', to: '/ledger', label: '总览', icon: Wallet },
    { key: 'details', to: '/ledger/transactions', label: '流水', icon: Tickets },
    { key: 'accounts', to: { path: '/ledger/manage', query: { view: 'accounts' } }, label: '账户', icon: CreditCard },
    { key: 'reports', to: '/ledger/reports', label: '报表', icon: DataAnalysis },
    { key: 'scheduled-tasks', to: '/ledger/scheduled-tasks', label: '定时任务', icon: Timer },
    { key: 'management', to: { path: '/ledger/manage', query: { view: 'categories' } }, views: ['categories', 'merchants', 'projects', 'books'], label: '管理', icon: Management },
    { key: 'members', to: { path: '/ledger/manage', query: { view: 'members' } }, label: '成员与角色权限', icon: UserFilled },
    { key: 'recycle', to: { path: '/ledger/manage', query: { view: 'recycle' } }, label: '回收站', icon: DeleteFilled },
    { key: 'audit', to: { path: '/ledger/manage', query: { view: 'audit' } }, label: '操作日志', icon: Document }
  ] },
  { key: 'knowledge', label: '个人知识库', items: [{ key: 'knowledge-home', to: '/knowledge', label: '知识库', icon: Notebook, disabled: true }] },
  { key: 'tasks', label: '任务', items: [{ key: 'task-home', to: '/tasks', label: '任务清单', icon: List, disabled: true }] }
]
const openGroups = ref(new Set())

function groupHasRoute(group) {
  return group.items.some(item => isNavItemActive(item))
}
function isNavItemActive(item) {
  if (item.disabled) return false
  const target = typeof item.to === 'string' ? { path: item.to } : item.to
  if (route.path !== target.path) return false
  if (item.views?.length) return item.views.includes(String(route.query.view))
  return target.query?.view ? route.query.view === target.query.view : !route.query.view
}
function isGroupOpen(group) { return openGroups.value.has(group.key) || groupHasRoute(group) }
function toggleGroup(key) {
  const next = new Set(openGroups.value)
  next.has(key) ? next.delete(key) : next.add(key)
  openGroups.value = next
}

const basis = computed(() => worktimeStore.settings.basis)
const showBasis = computed(() => ['/punch', '/records', '/stats'].includes(route.path))

async function setBasis(value) {
  if (worktimeStore.settings.basis === value) return
  await worktimeStore.saveSettings({ basis: value })
}

function onSyncClick() {
  if (store.dbMode) ElMessage.success('数据已安全同步到数据库')
  else store.connectDb().then(() => store.dbMode ? ElMessage.success('已连接数据库') : ElMessage.warning(store.authRequired ? '请先登录' : '数据库仍不可用'))
}

onMounted(() => { store.init() })
onBeforeUnmount(() => {
  finishNavigationLoading()
  removeBeforeGuard()
  removeAfterGuard()
  removeErrorGuard()
})
</script>

<style scoped>
.page-enter-active,
.page-leave-active { transition: opacity .22s ease, transform .22s ease, filter .22s ease }
.page-enter-from { opacity: 0; transform: translateY(8px); filter: blur(2px) }
.page-leave-to { opacity: 0; transform: translateY(-4px); filter: blur(1px) }
.nav-collapse-enter-active,
.nav-collapse-leave-active {
  overflow: hidden;
  transition: max-height .2s ease, opacity .16s ease, transform .2s ease;
}
.nav-collapse-enter-from,
.nav-collapse-leave-to { max-height: 0; opacity: 0; transform: translateY(-4px) }
.nav-collapse-enter-to,
.nav-collapse-leave-from { max-height: 520px; opacity: 1; transform: translateY(0) }
@media (prefers-reduced-motion: reduce) {
  .page-enter-active,
  .page-leave-active,
  .nav-collapse-enter-active,
  .nav-collapse-leave-active { transition: none }
}
</style>
