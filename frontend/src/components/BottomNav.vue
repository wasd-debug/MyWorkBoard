<template>
  <nav class="bottom-nav" aria-label="移动端主导航">
    <div class="inner">
      <button v-for="item in items" :key="item.key" class="bottom-nav-item" :class="{ on: isActive(item) }" type="button" @click="select(item)">
        <component :is="item.icon" class="bottom-nav-icon" aria-hidden="true" /><span>{{ item.label }}</span>
      </button>
    </div>
    <Drawer :open="Boolean(openItem)" :title="openItem?.label || ''" @update:open="!$event && (openItem = null)">
        <div v-if="openItem" class="mobile-nav-options">
          <button v-for="child in openItem.children" :key="child.key" class="mobile-nav-option" :class="{ active: isChildActive(child), disabled: child.disabled }" type="button" :disabled="child.disabled" @click="go(child)">
            <component :is="child.icon" class="mobile-nav-option-icon" aria-hidden="true" /><span>{{ child.label }}</span><small v-if="child.disabled">即将开放</small><ArrowRight v-else class="mobile-nav-arrow" aria-hidden="true" />
          </button>
        </div>
    </Drawer>
  </nav>
</template>

<script setup>
import { ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ArrowRight, Calendar, CreditCard, DataAnalysis, DeleteFilled, Document, List, Management, MoreFilled, Notebook, Setting, Tickets, Timer, UserFilled, Wallet } from '../icons.js'
import Drawer from './ui/Drawer.vue'

const route = useRoute()
const router = useRouter()
const openItem = ref(null)
const items = [
  { key: 'work', label: '工时', icon: Timer, children: [{ key: 'punch', to: '/punch', label: '打卡', icon: Timer }, { key: 'records', to: '/records', label: '记录', icon: Calendar }, { key: 'stats', to: '/stats', label: '统计', icon: DataAnalysis }] },
  { key: 'ledger', label: '账本', icon: Wallet, children: [
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
  { key: 'knowledge', label: '知识库', icon: Notebook, children: [{ key: 'knowledge-home', to: '/knowledge', label: '知识库', icon: Notebook, disabled: true }] },
  { key: 'tasks', label: '任务', icon: List, children: [{ key: 'task-home', to: '/tasks', label: '任务清单', icon: List, disabled: true }] },
  { key: 'more', label: '更多', icon: MoreFilled, children: [{ key: 'settings', to: '/settings', label: '设置', icon: Setting }] }
]
function isChildActive(child) {
  if (child.views?.length) return route.path === child.to.path && child.views.includes(String(route.query.view))
  return route.path === (typeof child.to === 'string' ? child.to : child.to.path) && (!child.to.query || route.query.view === child.to.query.view)
}
function isActive(item) { return item.children.some(child => !child.disabled && isChildActive(child)) }
function select(item) { if (item.children.length === 1 && !item.children[0].disabled) return go(item.children[0]); openItem.value = openItem.value?.key === item.key ? null : item }
function go(child) { if (child.disabled) return; router.push(child.to); openItem.value = null }
</script>
