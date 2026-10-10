<template>
  <main class="notification-page">
    <header><div><p class="label">TASKS</p><h1>提醒</h1><p>{{ inbox.unread }} 条未读 · {{ inbox.overdue }} 项逾期</p></div><div class="notification-actions"><Button variant="ghost" :disabled="!inbox.unread" @click="inbox.markAllRead()"><CheckCheck :size="16" />全部已读</Button><Button variant="ghost" @click="inbox.clearRead()"><Trash2 :size="16" />清空已读</Button></div></header>
    <div class="notification-tabs" role="tablist"><button :class="{ active: filter === 'all' }" @click="filter = 'all'">全部 {{ inbox.total }}</button><button :class="{ active: filter === 'unread' }" @click="filter = 'unread'">未读 {{ inbox.unread }}</button><span :class="{ online: inbox.connected }">{{ inbox.connected ? '实时连接' : '轮询中' }}</span></div>
    <p v-if="inbox.error" class="tasks-error" role="alert">{{ inbox.error }}</p>
    <div v-if="inbox.loading && !inbox.messages.length" class="notification-empty">正在加载提醒…</div>
    <div v-else-if="!visible.length" class="notification-empty"><BellOff :size="28" /><strong>暂无提醒</strong></div>
    <ul v-else class="notification-list"><li v-for="message in visible" :key="message.publicId" :class="{ unread: !message.readAt }"><button type="button" @click="open(message)"><span class="notification-dot"></span><span><strong>{{ message.title }}</strong><small>{{ message.body }}</small></span><time>{{ formatTime(message.createdAt) }}</time><ChevronRight :size="16" /></button></li></ul>
  </main>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { BellOff, CheckCheck, ChevronRight, Trash2 } from 'lucide-vue-next'
import Button from '../components/ui/Button.vue'
import { useTaskInboxStore } from '../stores/taskInbox.js'
const inbox = useTaskInboxStore(); const router = useRouter(); const filter = ref('all')
const visible = computed(() => filter.value === 'unread' ? inbox.messages.filter(item => !item.readAt) : inbox.messages)
async function open(message) { if (!message.readAt) await inbox.markRead([message.publicId]); if (message.deepLink) router.push(message.deepLink) }
function formatTime(value) { return new Intl.DateTimeFormat('zh-CN', { month: 'numeric', day: 'numeric', hour: '2-digit', minute: '2-digit' }).format(new Date(value)) }
onMounted(async () => { await inbox.refresh(); inbox.connect() })
</script>

<style scoped>
.notification-page{max-width:920px;margin:0 auto}.notification-page>header{display:flex;align-items:flex-end;justify-content:space-between;gap:20px;margin-bottom:24px}.notification-page h1{margin:4px 0 6px;font-size:2rem}.notification-page header p:last-child{margin:0;color:var(--muted)}.notification-actions{display:flex;gap:8px}.notification-tabs{display:flex;align-items:center;gap:6px;border-bottom:1px solid var(--line)}.notification-tabs button{padding:11px 14px;border:0;border-bottom:2px solid transparent;background:transparent;color:var(--muted)}.notification-tabs button.active{border-bottom-color:var(--accent);color:var(--ink);font-weight:700}.notification-tabs span{margin-left:auto;color:var(--muted);font-size:.75rem}.notification-tabs span.online{color:var(--up)}.notification-list{margin:0;padding:0;list-style:none}.notification-list li{border-bottom:1px solid var(--line)}.notification-list li.unread{background:color-mix(in srgb,var(--accent-soft) 45%,transparent)}.notification-list button{display:grid;width:100%;grid-template-columns:10px minmax(0,1fr) auto 18px;align-items:center;gap:12px;padding:18px 14px;border:0;background:transparent;color:var(--ink);text-align:left}.notification-dot{width:7px;height:7px;border-radius:50%;background:transparent}.unread .notification-dot{background:var(--accent)}.notification-list strong,.notification-list small{display:block}.notification-list small{margin-top:5px;color:var(--muted)}.notification-list time{color:var(--muted);font-size:.75rem}.notification-empty{display:grid;min-height:240px;place-content:center;justify-items:center;gap:10px;color:var(--muted)}@media(max-width:640px){.notification-page>header{align-items:flex-start;flex-direction:column}.notification-actions{width:100%;flex-wrap:wrap}.notification-list button{grid-template-columns:10px minmax(0,1fr) 18px}.notification-list time{grid-column:2}}
</style>
