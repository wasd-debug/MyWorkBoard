<template>
  <aside class="task-module-rail" aria-label="任务功能导航">
    <RouterLink class="task-rail-avatar" to="/" aria-label="返回个人工作台" title="个人工作台">{{ userInitial }}</RouterLink>
    <nav>
      <RouterLink v-for="item in items" :key="item.to" :to="item.to" :aria-label="item.label" :title="item.label" :class="{ active: active(item) }">
        <component :is="item.icon" aria-hidden="true" />
      </RouterLink>
    </nav>
    <div class="task-rail-bottom">
      <button type="button" class="task-rail-sync" :class="{ 'is-syncing': tasks.syncing, 'has-error': tasks.syncError, 'has-pending': tasks.pending }" :aria-label="syncLabel" :title="syncLabel" :aria-busy="tasks.syncing" :disabled="!tasks.online || tasks.syncing || !tasks.accountScope" @mousedown.prevent @click="requestSync">
        <RefreshCw aria-hidden="true" />
        <b v-if="tasks.pending && !tasks.syncing">{{ tasks.pending > 99 ? '99+' : tasks.pending }}</b>
        <i v-else-if="tasks.syncError && !tasks.syncing" aria-hidden="true"></i>
      </button>
      <RouterLink to="/tasks/inbox-notify" aria-label="任务提醒" title="任务提醒"><Bell aria-hidden="true" /><b v-if="inbox.unread">{{ inbox.unread > 99 ? '99+' : inbox.unread }}</b></RouterLink>
      <RouterLink to="/tasks/settings" aria-label="任务设置" title="任务设置"><Settings aria-hidden="true" /></RouterLink>
    </div>
  </aside>
</template>

<script setup>
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import { Bell, CalendarDays, CalendarHeart, CheckSquare2, CircleDot, LayoutGrid, RefreshCw, Search, Settings, Sparkles, Target } from 'lucide-vue-next'
import { useAppStore } from '../../stores/app.js'
import { useTaskInboxStore } from '../../stores/taskInbox.js'
import { useTasksStore } from '../../stores/tasks.js'

const route = useRoute()
const app = useAppStore()
const inbox = useTaskInboxStore()
const tasks = useTasksStore()
const userInitial = computed(() => String(app.authUser?.nickname || app.authUser?.username || '我').slice(0, 1))
const syncLabel = computed(() => !tasks.online
  ? '离线，变更保存在本机'
  : tasks.syncing
    ? `正在同步${tasks.pending ? ` ${tasks.pending} 项变更` : ''}`
    : tasks.syncError
      ? `${tasks.syncError}，点击重试`
      : tasks.pending
        ? `${tasks.pending} 项待同步，点击立即同步`
        : '已同步，点击检查更新')
function requestSync() {
  const event = new Event('task:manual-sync', { cancelable: true })
  window.dispatchEvent(event)
  if (!event.defaultPrevented) void tasks.syncNow()
}
// 顺序与滴答一致：任务 → 日历 → 四象限 → 专注 → 习惯 → 倒数纪念日 → AI → 搜索
const items = [
  { to: '/tasks/today', label: '任务', icon: CheckSquare2, matches: ['/tasks/inbox', '/tasks/today', '/tasks/tomorrow', '/tasks/next7', '/tasks/all', '/tasks/completed', '/tasks/abandoned', '/tasks/trash', '/tasks/list/', '/tasks/tag/', '/tasks/filter/', '/tasks/timeline'] },
  { to: '/tasks/calendar', label: '日历', icon: CalendarDays },
  { to: '/tasks/quadrant', label: '四象限', icon: LayoutGrid },
  { to: '/tasks/focus', label: '专注', icon: CircleDot },
  { to: '/tasks/habits', label: '习惯', icon: Target },
  { to: '/tasks/countdowns', label: '倒数纪念日', icon: CalendarHeart },
  { to: '/', label: 'AI', icon: Sparkles },
  { to: '/tasks/search', label: '搜索', icon: Search, matches: ['/tasks/search', '/tasks/board', '/tasks/stats'] }
]
function active(item) {
  return (item.matches || [item.to]).some(path => {
    // '/' 必须精确匹配，否则 startsWith('/') 会让 AI 入口在任何页面都高亮
    if (path === '/') return route.path === '/'
    return path.endsWith('/') ? route.path.startsWith(path) : route.path === path
  })
}
</script>

<style scoped>
.task-module-rail{position:fixed;z-index:52;inset:0 auto 0 0;width:50px;display:flex;flex-direction:column;align-items:center;padding:13px 5px 10px;border-right:1px solid var(--line);background:color-mix(in srgb,var(--panel) 94%,var(--paper));box-shadow:1px 0 0 rgba(15,23,42,.02)}
.task-rail-avatar{display:grid;width:34px;height:34px;place-items:center;margin-bottom:15px;border-radius:9px;background:var(--ink);color:var(--paper);font-size:13px;font-weight:800;text-decoration:none}
.task-module-rail nav,.task-rail-bottom{display:grid;gap:6px;width:100%}.task-module-rail nav{flex:1;align-content:start}.task-module-rail a,.task-rail-sync{position:relative;display:grid;width:40px;height:40px;place-items:center;margin:auto;border:0;border-radius:10px;background:transparent;color:var(--muted);text-decoration:none;cursor:pointer}.task-module-rail a:hover,.task-module-rail a:focus-visible,.task-rail-sync:hover:not(:disabled),.task-rail-sync:focus-visible{background:var(--panel2);color:var(--ink);outline:none}.task-module-rail a.active{background:var(--accent-soft);color:var(--accent)}.task-module-rail svg{width:20px;height:20px}.task-rail-sync:disabled{opacity:.55;cursor:default}.task-rail-sync.has-pending{color:var(--accent)}.task-rail-sync.has-error{color:var(--danger,#c2413b)}.task-rail-sync.is-syncing svg{animation:task-rail-sync-spin .9s linear infinite}.task-rail-bottom b{position:absolute;top:2px;right:1px;display:grid;min-width:16px;height:16px;place-items:center;padding:0 3px;border-radius:8px;background:#ef4444;color:#fff;font-size:9px}.task-rail-sync b{position:absolute;top:1px;right:-2px;display:grid;min-width:15px;height:15px;place-items:center;padding:0 3px;border:1px solid var(--panel);border-radius:9px;background:var(--accent);color:#fff;font-size:8px}.task-rail-sync i{position:absolute;top:5px;right:5px;width:7px;height:7px;border:1px solid var(--panel);border-radius:50%;background:var(--danger,#c2413b)}@keyframes task-rail-sync-spin{to{transform:rotate(360deg)}}
@media(max-width:767px){.task-module-rail{display:none}}
</style>
