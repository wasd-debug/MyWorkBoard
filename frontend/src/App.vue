<template>
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
            <div v-show="isGroupOpen(group)" class="nav-group-items">
              <router-link v-for="item in group.items" :key="item.key" :to="item.to" active-class="nav-route-active" :class="{ 'nav-item-disabled': item.disabled, 'nav-item-current': isNavItemActive(item) }" :aria-disabled="item.disabled || undefined" @click="item.disabled && $event.preventDefault()">
                <component :is="item.icon" class="nav-item-icon" aria-hidden="true" /><span>{{ item.label }}</span><span v-if="item.disabled" class="nav-item-soon">即将开放</span>
              </router-link>
            </div>
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
            <transition name="fade" mode="out-in">
              <component :is="Component" />
            </transition>
          </router-view>
        </div>
      </main>
      <BottomNav />
    </template>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { useAppStore } from './stores/app'
import { useRoute } from 'vue-router'
import { ArrowDown, Calendar, Connection, CreditCard, DataAnalysis, List, Moon, Notebook, Refresh, Setting, Sunny, SwitchButton, Tickets, Timer, Wallet } from '@element-plus/icons-vue'
import BottomNav from './components/BottomNav.vue'
import LoginView from './views/LoginView.vue'

const store = useAppStore()
const route = useRoute()

const navGroups = [
  { key: 'work', label: '工时记录', items: [{ key: 'punch', to: '/punch', label: '打卡', icon: Timer }, { key: 'records', to: '/records', label: '记录', icon: Calendar }, { key: 'stats', to: '/stats', label: '统计', icon: DataAnalysis }] },
  { key: 'ledger', label: '个人账本', items: [{ key: 'overview', to: '/ledger', label: '总览', icon: Wallet }, { key: 'details', to: '/ledger/transactions', label: '流水', icon: Tickets }, { key: 'accounts', to: { path: '/ledger', query: { view: 'accounts' } }, label: '账户', icon: CreditCard }] },
  { key: 'knowledge', label: '个人知识库', items: [{ key: 'knowledge-home', to: '/knowledge', label: '知识库', icon: Notebook, disabled: true }] },
  { key: 'tasks', label: '任务', items: [{ key: 'task-home', to: '/tasks', label: '任务清单', icon: List, disabled: true }] }
]
const openGroups = ref(new Set())

function groupHasRoute(group) {
  return group.items.some(item => typeof item.to === 'string' ? route.path === item.to : route.path === item.to.path)
}
function isNavItemActive(item) {
  if (item.disabled) return false
  const target = typeof item.to === 'string' ? { path: item.to } : item.to
  if (route.path !== target.path) return false
  return target.query?.view ? route.query.view === target.query.view : !route.query.view
}
function isGroupOpen(group) { return openGroups.value.has(group.key) || groupHasRoute(group) }
function toggleGroup(key) {
  const next = new Set(openGroups.value)
  next.has(key) ? next.delete(key) : next.add(key)
  openGroups.value = next
}

const basis = computed(() => store.settings.basis)
const showBasis = computed(() => ['/punch', '/records', '/stats'].includes(route.path))

function setBasis(value) {
  if (store.settings.basis === value) return
  store.settings.basis = value
  store.saveAll()
}

function onSyncClick() {
  if (store.dbMode) ElMessage.success('数据已安全同步到数据库')
  else store.connectDb().then(() => store.dbMode ? ElMessage.success('已连接数据库') : ElMessage.warning(store.authRequired ? '请先登录' : '数据库仍不可用'))
}

onMounted(() => { store.init() })
</script>

<style scoped>
.fade-enter-active, .fade-leave-active { transition: opacity .18s ease }
.fade-enter-from, .fade-leave-to { opacity: 0 }
</style>
