<template>
  <aside class="task-module-rail" aria-label="任务功能导航">
    <RouterLink class="task-rail-avatar" to="/" aria-label="返回个人工作台" title="个人工作台">{{ userInitial }}</RouterLink>
    <nav>
      <RouterLink v-for="item in items" :key="item.to" :to="item.to" :aria-label="item.label" :title="item.label" :class="{ active: active(item) }">
        <component :is="item.icon" aria-hidden="true" />
      </RouterLink>
    </nav>
    <div class="task-rail-bottom">
      <RouterLink to="/tasks/inbox-notify" aria-label="任务提醒" title="任务提醒"><Bell aria-hidden="true" /><b v-if="inbox.unread">{{ inbox.unread > 99 ? '99+' : inbox.unread }}</b></RouterLink>
      <RouterLink to="/tasks/settings" aria-label="任务设置" title="任务设置"><Settings aria-hidden="true" /></RouterLink>
    </div>
  </aside>
</template>

<script setup>
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import { Bell, CalendarDays, CheckSquare2, CircleDot, Search, Settings, Sparkles, Target } from 'lucide-vue-next'
import { useAppStore } from '../../stores/app.js'
import { useTaskInboxStore } from '../../stores/taskInbox.js'

const route = useRoute()
const app = useAppStore()
const inbox = useTaskInboxStore()
const userInitial = computed(() => String(app.authUser?.nickname || app.authUser?.username || '我').slice(0, 1))
const items = [
  { to: '/tasks/today', label: '任务', icon: CheckSquare2, matches: ['/tasks/inbox', '/tasks/today', '/tasks/tomorrow', '/tasks/next7', '/tasks/all', '/tasks/completed', '/tasks/trash', '/tasks/list/', '/tasks/tag/', '/tasks/filter/', '/tasks/timeline'] },
  { to: '/tasks/calendar', label: '日历', icon: CalendarDays },
  { to: '/tasks/focus', label: '专注', icon: CircleDot },
  { to: '/tasks/habits', label: '习惯', icon: Target },
  { to: '/tasks/countdowns', label: '倒数日', icon: Sparkles },
  { to: '/tasks/search', label: '搜索', icon: Search, matches: ['/tasks/search', '/tasks/board', '/tasks/quadrant', '/tasks/stats'] }
]
function active(item) { return (item.matches || [item.to]).some(path => path.endsWith('/') ? route.path.startsWith(path) : route.path === path) }
</script>

<style scoped>
.task-module-rail{position:fixed;z-index:52;inset:0 auto 0 0;width:50px;display:flex;flex-direction:column;align-items:center;padding:13px 5px 10px;border-right:1px solid var(--line);background:color-mix(in srgb,var(--panel) 94%,var(--paper));box-shadow:1px 0 0 rgba(15,23,42,.02)}
.task-rail-avatar{display:grid;width:34px;height:34px;place-items:center;margin-bottom:15px;border-radius:9px;background:var(--ink);color:var(--paper);font-size:13px;font-weight:800;text-decoration:none}
.task-module-rail nav,.task-rail-bottom{display:grid;gap:6px;width:100%}.task-module-rail nav{flex:1;align-content:start}.task-module-rail a{position:relative;display:grid;width:40px;height:40px;place-items:center;margin:auto;border-radius:10px;color:var(--muted);text-decoration:none}.task-module-rail a:hover,.task-module-rail a:focus-visible{background:var(--panel2);color:var(--ink);outline:none}.task-module-rail a.active{background:var(--accent-soft);color:var(--accent)}.task-module-rail svg{width:20px;height:20px}.task-rail-bottom b{position:absolute;top:2px;right:1px;display:grid;min-width:16px;height:16px;place-items:center;padding:0 3px;border-radius:8px;background:#ef4444;color:#fff;font-size:9px}
@media(max-width:767px){.task-module-rail{display:none}}
</style>
