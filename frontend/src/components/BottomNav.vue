<template>
  <nav class="bottom-nav" aria-label="移动端主导航">
    <div class="inner">
      <button v-for="item in dockItems" :key="item.key" class="bottom-nav-item" :class="{ on: isActive(item) }" type="button" @click="go(item)"><component :is="item.icon" class="bottom-nav-icon" aria-hidden="true" /><span>{{ item.label }}</span></button>
      <button class="bottom-nav-item" :class="{ on: moreOpen }" type="button" @click="moreOpen = !moreOpen"><MoreFilled class="bottom-nav-icon" aria-hidden="true" /><span>更多</span></button>
    </div>

    <Transition name="dock-sheet">
      <section v-if="moreOpen" class="mobile-more-sheet">
        <div class="mobile-more-head"><div><b>更多功能</b><small>长按无须设置，在下方选择第四个快捷入口</small></div><button type="button" aria-label="关闭" @click="moreOpen = false"><Close /></button></div>
        <div class="mobile-more-grid">
          <button v-for="item in moreItems" :key="item.key" type="button" :class="{ active: isActive(item), disabled: item.disabled }" :disabled="item.disabled" @click="go(item)"><component :is="item.icon" aria-hidden="true" /><span>{{ item.label }}</span><small v-if="item.disabled">即将开放</small></button>
        </div>
        <div class="mobile-pin-row"><span>第四个快捷入口</span><select v-model="pinnedKey" aria-label="选择第四个快捷入口" @change="savePinned"><option v-for="item in pinOptions" :key="item.key" :value="item.key">{{ item.label }}</option></select></div>
        <div class="mobile-theme-row"><span>配色风格</span><div><button v-for="item in palettes" :key="item.key" type="button" class="mobile-theme-swatch" :class="{ active: app.accent === item.key }" :style="{ '--swatch': item.color }" :aria-label="item.label" @click="app.setAccent(item.key)"></button></div></div>
      </section>
    </Transition>
  </nav>
</template>

<script setup>
import { computed, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Calendar, Close, DataAnalysis, List, MoreFilled, Notebook, Setting, Tickets, Timer, Wallet } from '../icons.js'
import { useAppStore } from '../stores/app.js'

const route = useRoute()
const router = useRouter()
const app = useAppStore()
const moreOpen = ref(false)
const LS_PIN = 'workspace_mobile_pin_v1'
const all = {
  ledger: { key: 'ledger', label: '账本', icon: Wallet, to: '/ledger' },
  punch: { key: 'punch', label: '工时', icon: Timer, to: '/punch' },
  records: { key: 'records', label: '记录', icon: Calendar, to: '/records' },
  stats: { key: 'stats', label: '统计', icon: DataAnalysis, to: '/stats' },
  transactions: { key: 'transactions', label: '流水', icon: Tickets, to: '/ledger/transactions' },
  settings: { key: 'settings', label: '设置', icon: Setting, to: '/settings' },
  knowledge: { key: 'knowledge', label: '知识库', icon: Notebook, disabled: true },
  tasks: { key: 'tasks', label: '任务', icon: List, disabled: true }
}
const storedPin = typeof localStorage === 'undefined' ? '' : localStorage.getItem(LS_PIN)
const pinnedKey = ref(['stats', 'transactions', 'settings'].includes(storedPin) ? storedPin : 'stats')
const pinOptions = [all.stats, all.transactions, all.settings]
const dockItems = computed(() => [all.ledger, all.punch, all.records, all[pinnedKey.value]])
const moreItems = [all.stats, all.transactions, all.settings, all.knowledge, all.tasks]
const palettes = [{ key: 'sun', label: '日光', color: '#ffd22e' }, { key: 'ocean', label: '海洋', color: '#68d5cf' }, { key: 'forest', label: '森林', color: '#91bd58' }, { key: 'berry', label: '莓果', color: '#c85f8c' }, { key: 'night', label: '暗夜', color: '#242933' }]
function isActive(item) { return item.to === '/ledger' ? route.path === '/ledger' : route.path === item.to }
function go(item) { if (item.disabled || !item.to) return; router.push(item.to); moreOpen.value = false }
function savePinned() { localStorage.setItem(LS_PIN, pinnedKey.value) }
</script>
