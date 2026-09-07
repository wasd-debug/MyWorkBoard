<template>
  <nav class="bottom-nav" aria-label="移动端主导航">
    <div class="inner">
      <button v-for="item in items" :key="item.key" class="bottom-nav-item" :class="{ on: isActive(item) }" type="button" @click="select(item)">
        <span class="bottom-nav-icon" aria-hidden="true">{{ item.icon }}</span><span>{{ item.label }}</span>
      </button>
    </div>
    <div v-if="openItem" class="mobile-nav-menu" role="dialog" aria-label="二级导航">
      <button class="mobile-nav-backdrop" type="button" aria-label="关闭菜单" @click="openItem = null"></button>
      <div class="mobile-nav-sheet">
        <div class="mobile-nav-sheet-head"><div><span class="label">WORKSPACE MODULE</span><strong>{{ openItem.label }}</strong></div><button class="mobile-nav-close" type="button" aria-label="关闭菜单" @click="openItem = null">×</button></div>
        <div class="mobile-nav-options">
          <button v-for="child in openItem.children" :key="child.key" class="mobile-nav-option" :class="{ active: isChildActive(child), disabled: child.disabled }" type="button" :disabled="child.disabled" @click="go(child)">
            <span class="mobile-nav-option-icon" aria-hidden="true">{{ child.icon }}</span><span>{{ child.label }}</span><small v-if="child.disabled">即将开放</small><span v-else class="mobile-nav-arrow" aria-hidden="true">→</span>
          </button>
        </div>
      </div>
    </div>
  </nav>
</template>

<script setup>
import { ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'

const route = useRoute()
const router = useRouter()
const openItem = ref(null)
const items = [
  { key: 'work', label: '工时', icon: '◷', children: [{ key: 'punch', to: '/punch', label: '打卡', icon: '◷' }, { key: 'records', to: '/records', label: '记录', icon: '▦' }, { key: 'stats', to: '/stats', label: '统计', icon: '⌁' }] },
  { key: 'ledger', label: '账本', icon: '◫', children: [{ key: 'overview', to: '/ledger', label: '总览', icon: '◫' }, { key: 'details', to: { path: '/ledger', query: { view: 'details' } }, label: '明细', icon: '≡' }, { key: 'accounts', to: { path: '/ledger', query: { view: 'accounts' } }, label: '账户', icon: '◎' }] },
  { key: 'knowledge', label: '知识库', icon: '◇', children: [{ key: 'knowledge-home', to: '/knowledge', label: '知识库', icon: '◇', disabled: true }] },
  { key: 'tasks', label: '任务', icon: '✓', children: [{ key: 'task-home', to: '/tasks', label: '任务清单', icon: '✓', disabled: true }] },
  { key: 'more', label: '更多', icon: '···', children: [{ key: 'settings', to: '/settings', label: '设置', icon: '⚙' }] }
]
function isChildActive(child) { return route.path === (typeof child.to === 'string' ? child.to : child.to.path) && (!child.to.query || route.query.view === child.to.query.view) }
function isActive(item) { return item.children.some(child => !child.disabled && isChildActive(child)) }
function select(item) { if (item.children.length === 1 && !item.children[0].disabled) return go(item.children[0]); openItem.value = openItem.value?.key === item.key ? null : item }
function go(child) { if (child.disabled) return; router.push(child.to); openItem.value = null }
</script>
