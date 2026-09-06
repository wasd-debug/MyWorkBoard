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
          <router-link v-for="(item, index) in navItems" :key="item.to" :to="item.to">
            <span>{{ item.label }}</span>
            <span class="nav-index num">{{ String(index + 1).padStart(2, '0') }}</span>
          </router-link>
        </nav>
        <div class="sidebar-foot">
          <div class="sidebar-status">
            <span class="status-dot" :class="{ online: store.dbMode }"></span>
            <span>{{ store.dbMode ? '数据库已同步' : '本地离线' }}</span>
          </div>
          <div class="sidebar-actions">
            <button class="ui-button variant-icon size-sm" type="button" aria-label="同步状态" :title="store.dbMode ? '数据库已连接' : '本地离线'" @click="onSyncClick">
              {{ store.dbMode ? '◉' : '◌' }}
            </button>
            <button class="ui-button variant-icon size-sm" type="button" aria-label="退出登录" @click="store.logout()">→</button>
          </div>
        </div>
      </aside>

      <main class="app-main">
        <header class="top-ticker" aria-label="实时指标">
          <div class="tick">
            <span class="k">DATE</span>
            <span class="v num">{{ todayText }}</span>
          </div>
          <div class="tick">
            <span class="k">BASE</span>
            <span class="v num">{{ baseRate > 0 ? money(baseRate) + '/h' : '—' }}</span>
          </div>
          <div class="tick">
            <span class="k">WEEK_AVG</span>
            <span class="v num" :class="week.realRate >= baseRate ? 'up' : 'warn'">{{ week.realRate > 0 ? money(week.realRate) + '/h' : '—' }}</span>
          </div>
          <div class="tick">
            <span class="k">OT_WEEK</span>
            <span class="v num" :class="week.otMin > 0 ? 'warn' : ''">{{ hours(week.otMin) }}h</span>
          </div>
          <div class="tick">
            <span class="k">ΔBASE</span>
            <span class="v num" :class="weekDiff >= 0 ? 'up' : 'down'">{{ weekDiff >= 0 ? '▲' : '▼' }} {{ Math.abs(weekDiff).toFixed(1) }}%</span>
          </div>
          <button class="theme-icon-toggle" type="button" :aria-label="store.theme === 'dark' ? '切换日间模式' : '切换暗夜模式'" :aria-pressed="store.theme === 'dark'" @click="store.toggleTheme()">
            <span aria-hidden="true">{{ store.theme === 'dark' ? '☀' : '☾' }}</span>
          </button>
          <div class="basis-seg" aria-label="工资口径">
            <button :class="{ on: basis === 'pre' }" type="button" @click="setBasis('pre')">税前</button>
            <button :class="{ on: basis === 'post' }" type="button" @click="setBasis('post')">税后</button>
          </div>
        </header>
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
import { computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { useAppStore } from './stores/app'
import BottomNav from './components/BottomNav.vue'
import LoginView from './views/LoginView.vue'
import { CALC } from './utils/calc'

const store = useAppStore()

const navItems = [
  { to: '/punch', label: '打卡' },
  { to: '/records', label: '记录' },
  { to: '/stats', label: '统计' },
  { to: '/settings', label: '设置' }
]

const basis = computed(() => store.settings.basis)
const today = new Date()
const todayText = `${today.getFullYear()}/${String(today.getMonth() + 1).padStart(2, '0')}/${String(today.getDate()).padStart(2, '0')} ${CALC.WEEK_CN[today.getDay()]}`

const monthCtx = computed(() => CALC.monthCtx(store.settings.salaries, store.settings, CALC.dateKey(today).slice(0, 7)))
const effDays = computed(() => CALC.effDaysPerMonth(monthCtx.value, CALC.dateKey(today).slice(0, 7), store.holidays, store.records))
const baseRate = computed(() => CALC.baseRate(monthCtx.value, store.settings.basis, effDays.value))
const week = computed(() => CALC.periodStats(
  CALC.weekKeysTo(today),
  store.records,
  monthCtx.value,
  store.settings.basis,
  undefined,
  store.holidays,
  ym => CALC.monthSalary(store.settings.salaries, store.settings, store.settings.basis, ym)
))
const weekDiff = computed(() => baseRate.value > 0 && week.value.realRate > 0 ? (week.value.realRate - baseRate.value) / baseRate.value * 100 : 0)

const hours = CALC.fmtHours
const money = CALC.fmtMoney

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
