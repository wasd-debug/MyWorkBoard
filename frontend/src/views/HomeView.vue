<template>
  <main class="ai-chat-home" :class="{ 'sidebar-collapsed': !sidebarOpen }">
    <button v-if="sidebarOpen" class="chat-sidebar-backdrop" type="button" aria-label="关闭会话侧栏" @click="sidebarOpen = false"></button>
    <aside class="chat-sidebar" aria-label="历史会话">
      <div class="chat-sidebar-head">
        <strong>历史会话</strong>
        <button class="chat-icon-button" type="button" title="收起侧栏" aria-label="收起侧栏" @click="sidebarOpen = false"><PanelLeftClose /></button>
      </div>
      <button class="new-chat-button" type="button" @click="newConversation"><SquarePen /><span>新对话</span></button>
      <nav class="conversation-list" aria-label="会话列表">
        <p v-if="!conversations.length" class="conversation-empty">还没有历史会话</p>
        <div v-for="item in conversations" :key="item.id" :class="{ active: item.id === activeId }" @contextmenu.prevent="openConversationMenu($event, item)">
          <button class="conversation-open" type="button" :aria-current="item.id === activeId ? 'page' : undefined" @click="openConversation(item.id)">
            <MessageSquareText aria-hidden="true" />
            <span><b>{{ item.title }}</b><small>{{ formatTime(item.updatedAt) }}</small></span>
          </button>
          <button type="button" title="更多会话操作" :aria-label="`${item.title}的更多操作`" aria-haspopup="menu" :aria-expanded="conversationMenu.item?.id === item.id" @click.stop="openConversationMenu($event, item, true)"><Ellipsis /></button>
        </div>
      </nav>
      <div class="chat-sidebar-foot"><span class="status-dot" :class="{ online: store.dbMode }"></span>{{ store.dbMode ? '数据已同步' : '本地会话' }}</div>
    </aside>

    <div v-if="conversationMenu.item" ref="conversationMenuEl" class="conversation-context-menu" :style="conversationMenuStyle" role="menu" :aria-label="`${conversationMenu.item.title}的会话操作`" @keydown="handleConversationMenuKeydown">
      <button type="button" role="menuitem" @click="showRenameDialog(conversationMenu.item)"><Pencil /><span>重命名</span></button>
      <button type="button" role="menuitem" @click="archiveConversation(conversationMenu.item)"><Archive /><span>归档</span></button>
      <hr />
      <button class="danger" type="button" role="menuitem" @click="showDeleteDialog(conversationMenu.item)"><Trash2 /><span>删除</span></button>
    </div>

    <div v-if="conversationDialog.type" class="conversation-dialog-backdrop" role="presentation" @pointerdown.self="closeConversationDialog">
      <section class="conversation-dialog" role="dialog" aria-modal="true" :aria-labelledby="`conversation-${conversationDialog.type}-title`" @keydown.esc="closeConversationDialog">
        <template v-if="conversationDialog.type === 'rename'">
          <header><h2 id="conversation-rename-title">重命名会话</h2><button type="button" aria-label="关闭" @click="closeConversationDialog"><X /></button></header>
          <form @submit.prevent="renameConversation">
            <label for="conversation-title">会话名称</label>
            <input id="conversation-title" ref="renameInput" v-model="renameTitle" maxlength="120" autocomplete="off" />
            <footer><button type="button" @click="closeConversationDialog">取消</button><button class="primary" type="submit" :disabled="!renameTitle.trim()">保存</button></footer>
          </form>
        </template>
        <template v-else>
          <header><h2 id="conversation-delete-title">删除会话？</h2><button type="button" aria-label="关闭" @click="closeConversationDialog"><X /></button></header>
          <p>“{{ conversationDialog.item?.title }}”将被永久删除，此操作无法撤销。</p>
          <footer><button type="button" @click="closeConversationDialog">取消</button><button class="danger" type="button" @click="deleteConversation(conversationDialog.item?.id)">删除</button></footer>
        </template>
      </section>
    </div>

    <section class="chat-workspace">
      <header class="chat-workspace-head">
        <button v-if="!sidebarOpen" class="chat-icon-button" type="button" title="展开历史会话" aria-label="展开历史会话" @click="sidebarOpen = true"><PanelLeftOpen /></button>
        <div><b>{{ activeConversation?.title || '新对话' }}</b><small>AI 个人工作台</small></div>
      </header>

      <div ref="messageViewport" class="chat-message-viewport" @scroll.passive="handleMessageScroll" @wheel.passive="handleMessageWheel">
        <div v-if="!activeMessages.length" class="chat-empty-state">
          <div class="chat-empty-copy">
            <span class="chat-ai-mark"><Sparkles /></span>
            <h1>下午好，{{ displayName }}</h1>
            <p>今天想从哪里开始？</p>
          </div>
          <div class="chat-module-grid" aria-label="工作模块">
            <button v-for="card in cards" :key="card.key" type="button" :class="`tone-${card.tone}`" @click="openCard(card)">
              <span><component :is="card.icon" aria-hidden="true" /></span>
              <div><b>{{ card.title }}</b><small>{{ card.description }}</small></div>
              <ArrowUpRight aria-hidden="true" />
            </button>
          </div>
        </div>

        <div v-else class="chat-thread">
          <article v-for="item in activeMessages" :key="item.id" class="chat-message" :class="item.role">
            <span v-if="item.role === 'assistant'" class="message-avatar"><Sparkles /></span>
            <div class="message-content">
              <template v-if="item.role === 'assistant'">
                <div class="message-markdown" v-html="renderMarkdown(item.content)"></div>
                <span v-if="item.typing" class="typing-cursor" aria-label="正在输入">▍</span>
              </template>
              <p v-else>{{ item.content }}</p>
              <div v-if="item.attachments?.length" class="message-attachments">
                <span v-for="file in item.attachments" :key="file.id"><ImageIcon v-if="file.type === 'image'" /><Mic v-else-if="file.type === 'audio'" /><Paperclip v-else />{{ file.name }}</span>
              </div>
              <button v-if="item.route && !item.typing" class="message-route" type="button" @click="router.push(item.route)">进入{{ item.routeLabel }}<ArrowUpRight /></button>
              <div v-if="!item.typing" class="message-meta">
                <time v-if="item.createdAt" :datetime="new Date(item.createdAt).toISOString()"><Clock3 />{{ formatMessageTime(item.createdAt) }}</time>
                <details v-if="item.role === 'assistant' && item.toolExecutions?.length" class="tool-call-details" @toggle="handleMetaToggle">
                  <summary><Wrench />已调用 {{ item.toolExecutions.length }} 个工具</summary>
                  <div class="tool-call-list">
                    <div v-for="tool in item.toolExecutions" :key="`${tool.name}-${tool.durationMs}`" class="tool-call-item">
                      <span class="tool-status" :class="tool.status?.toLowerCase()"><Check v-if="tool.status === 'COMPLETED'" /><X v-else /></span>
                      <div><b>{{ toolLabel(tool.name) }}</b><small>{{ tool.summary }}</small></div>
                      <em>{{ formatDuration(tool.durationMs) }}</em>
                    </div>
                  </div>
                </details>
                <details v-if="item.role === 'assistant' && item.usage?.totalTokens" class="token-details" @toggle="handleMetaToggle">
                  <summary><Database />{{ formatNumber(item.usage.totalTokens) }} Tokens</summary>
                  <div class="token-popover">
                    <header><span>Token 用量</span><b>{{ formatNumber(item.usage.totalTokens) }}</b></header>
                    <p><span>输入</span><b>{{ formatNumber(item.usage.inputTokens) }}</b></p>
                    <p><span>缓存命中</span><b>{{ formatNumber(item.usage.cacheHitTokens) }}</b></p>
                    <p><span>缓存未命中</span><b>{{ formatNumber(item.usage.cacheMissTokens) }}</b></p>
                    <p><span>输出</span><b>{{ formatNumber(item.usage.outputTokens) }}</b></p>
                    <p><span>思考过程</span><b>{{ formatNumber(item.usage.reasoningTokens) }}</b></p>
                    <p><span>回复内容</span><b>{{ formatNumber(replyTokens(item.usage)) }}</b></p>
                    <div class="token-cache-bar"><i :style="{ width: `${cacheHitRate(item.usage)}%` }"></i></div>
                    <small>输入缓存命中率 {{ cacheHitRate(item.usage) }}%</small>
                  </div>
                </details>
                <details v-if="item.role === 'assistant' && item.durationMs != null" class="timing-details" @toggle="handleMetaToggle">
                  <summary><TimerReset />{{ formatDuration(item.durationMs) }}</summary>
                  <div class="timing-popover">
                    <header><span>总耗时</span><b>{{ formatDuration(item.durationMs) }}</b></header>
                    <p><span>首字时延</span><b>{{ item.firstTokenMs > 0 ? formatDuration(item.firstTokenMs) : '未采集' }}</b></p>
                    <template v-for="model in item.modelExecutions || []" :key="`model-${model.round}`">
                      <p><span>模型调用 {{ model.round }}</span><b>{{ formatDuration(model.durationMs) }}</b></p>
                      <p class="timing-sub"><span>首字</span><b>{{ model.firstTokenMs > 0 ? formatDuration(model.firstTokenMs) : '未采集' }}</b></p>
                    </template>
                    <p v-for="(tool, index) in item.toolExecutions || []" :key="`timing-${tool.name}-${index}`"><span>{{ toolLabel(tool.name) }}</span><b>{{ formatDuration(tool.durationMs) }}</b></p>
                  </div>
                </details>
                <button type="button" :title="copiedId === item.id ? '已复制' : '复制消息'" :aria-label="copiedId === item.id ? '消息已复制' : '复制消息'" @click="copyMessage(item)"><Check v-if="copiedId === item.id" /><Copy v-else />{{ copiedId === item.id ? '已复制' : '复制' }}</button>
              </div>
            </div>
          </article>
        </div>
      </div>

      <button v-if="activeMessages.length && !autoFollow" class="scroll-to-bottom" type="button" title="滚动到底部并继续跟随" aria-label="滚动到底部并继续跟随" @click="scrollToBottom(true)"><ArrowDown /></button>

      <div class="chat-composer-wrap">
        <section v-if="activeQueuedPrompts.length" class="prompt-queue" aria-label="待发送消息队列">
          <header><span><ListOrdered />等待发送 {{ activeQueuedPrompts.length }} 条</span><button type="button" @click="clearPromptQueue(activeId)">清空</button></header>
          <ol>
            <li v-for="(item, index) in activeQueuedPrompts" :key="item.id">
              <span>{{ index + 1 }}</span><p>{{ item.text }}</p><button type="button" :aria-label="`移除队列消息：${item.text}`" @click="removeQueuedPrompt(item.id)"><X /></button>
            </li>
          </ol>
        </section>
        <div v-if="attachments.length" class="composer-attachments">
          <span v-for="file in attachments" :key="file.id"><ImageIcon v-if="file.type === 'image'" /><Mic v-else-if="file.type === 'audio'" /><Paperclip v-else />{{ file.name }}<button type="button" :aria-label="`移除 ${file.name}`" @click="removeAttachment(file.id)"><X /></button></span>
        </div>
        <form class="chat-composer" @submit.prevent="submitPrompt">
          <label class="sr-only" for="workspace-prompt">给 AI 发送消息</label>
          <textarea id="workspace-prompt" v-model="prompt" rows="1" placeholder="给个人工作台发送消息" @keydown.enter.exact.prevent="submitPrompt"></textarea>
          <div class="composer-toolbar">
            <button class="chat-icon-button" type="button" title="上传文件" aria-label="上传文件" @click="fileInput?.click()"><Paperclip /></button>
            <button class="chat-icon-button" type="button" title="上传图片" aria-label="上传图片" @click="imageInput?.click()"><ImageIcon /></button>
            <button class="chat-icon-button" :class="{ recording }" type="button" :title="recording ? '停止录音' : '语音输入'" :aria-label="recording ? '停止录音' : '语音输入'" @click="toggleRecording"><Square v-if="recording" /><Mic v-else /></button>
            <span v-if="recording" class="recording-label"><i></i>正在录音</span>
            <button class="composer-send" type="submit" title="发送" aria-label="发送" :disabled="!canSubmit"><ArrowUp /></button>
          </div>
          <input ref="fileInput" class="sr-only" type="file" multiple @change="event => addFiles(event, 'file')" />
          <input ref="imageInput" class="sr-only" type="file" accept="image/*" multiple @change="event => addFiles(event, 'image')" />
        </form>
        <p>AI 可能会出错，请核对重要信息。</p>
      </div>
    </section>
  </main>
</template>

<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import DOMPurify from 'dompurify'
import { marked } from 'marked'
import { Archive, ArrowDown, ArrowUp, ArrowUpRight, Check, Clock3, Copy, Database, Ellipsis, Image as ImageIcon, ListOrdered, MessageSquareText, Mic, PanelLeftClose, PanelLeftOpen, Paperclip, Pencil, Sparkles, Square, SquarePen, TimerReset, Trash2, Wrench, X } from 'lucide-vue-next'
import { Calendar, List, Timer, Wallet } from '../icons.js'
import { message } from '../services/message.js'
import { useAppStore } from '../stores/app'
import { apiArchiveAgentSession, apiChatWithAssistant, apiCreateAgentSession, apiDeleteAgentSession, apiGetAgentTurn, apiListAgentMessages, apiListAgentSessions, apiStreamAgentTurn, apiUpdateAgentSession } from '../../packages/api-client/src/index.js'

const router = useRouter(), store = useAppStore()
const sidebarOpen = ref(true), conversations = ref([]), activeId = ref(''), prompt = ref(''), attachments = ref([])
const fileInput = ref(null), imageInput = ref(null), messageViewport = ref(null), recording = ref(false), autoFollow = ref(true), copiedId = ref('')
const conversationMenu = ref({ item: null, x: 0, y: 0 }), conversationMenuEl = ref(null)
const conversationDialog = ref({ type: '', item: null }), renameTitle = ref(''), renameInput = ref(null)
const queuedPrompts = ref([]), turnRunning = ref(false)
let mediaRecorder = null, mediaStream = null, recordingStartedAt = 0, activeTurnController = null, conversationCreationPromise = null, lastMessageScrollTop = 0, programmaticScroll = false, userPausedFollow = false
const historyKey = computed(() => `workspace_ai_conversations_v1:${store.accountScope || 'local'}`)
const displayName = computed(() => store.authUser?.nickname || store.authUser?.username || '建胜')
const activeConversation = computed(() => conversations.value.find(item => item.id === activeId.value) || null)
const activeMessages = computed(() => activeConversation.value?.messages || [])
const canSubmit = computed(() => Boolean(prompt.value.trim() || attachments.value.length))
const activeQueuedPrompts = computed(() => queuedPrompts.value.filter(item => item.conversationId === activeId.value))
const conversationMenuStyle = computed(() => ({ left: `${conversationMenu.value.x}px`, top: `${conversationMenu.value.y}px` }))
const cards = [
  { key: 'ledger', title: '账本', description: '记账、流水与报表', route: '/ledger', icon: Wallet, tone: 'yellow' },
  { key: 'knowledge', title: '知识库', description: '整理与连接知识', icon: Calendar, tone: 'cyan', planned: true },
  { key: 'worktime', title: '工时', description: '打卡、补录与统计', route: '/punch', icon: Timer, tone: 'pink' },
  { key: 'tasks', title: '任务', description: '拆解与推进计划', icon: List, tone: 'peach', planned: true }
]

function uid() { return `${Date.now().toString(36)}-${Math.random().toString(36).slice(2, 8)}` }
function persist() { localStorage.setItem(historyKey.value, JSON.stringify(conversations.value.slice(0, 60))) }
function parseMetadata(value) { if (!value) return {}; if (typeof value === 'object') return value; try { return JSON.parse(value) } catch { return {} } }
function responseFields(value = {}) { return { usage: value.usage, durationMs: value.durationMs, firstTokenMs: value.firstTokenMs, modelExecutions: value.modelExecutions || [], toolExecutions: value.toolExecutions || [] } }
function inferRoute(text) { if (/工时|打卡|上班|下班/.test(text)) return { route: '/punch', routeLabel: '工时' }; if (/报表/.test(text)) return { route: '/ledger/reports', routeLabel: '账本报表' }; if (/流水/.test(text)) return { route: '/ledger/transactions', routeLabel: '账本流水' }; if (/记账|支出|收入|账本/.test(text)) return { route: '/ledger', routeLabel: '账本' }; return {} }
function loadLocalConversations() { try { conversations.value = JSON.parse(localStorage.getItem(historyKey.value) || '[]').map(item => ({ ...item, messages: (item.messages || []).map(row => ({ ...row, typing: false })) })) } catch { conversations.value = [] }; activeId.value = conversations.value[0]?.id || '' }
async function loadMessages(id) { const target = conversations.value.find(item => item.id === id); if (!target) return; const rows = await apiListAgentMessages(id); target.messages = (rows || []).map(row => ({ id: `server-${row.id}`, turnId: row.turnId, role: row.role, content: row.content, createdAt: row.createdAt, typing: false, ...responseFields(row.role === 'assistant' ? parseMetadata(row.metadataJson) : {}), ...inferRoute(row.content) })); persist(); await scrollToBottom(true) }
async function loadConversations() { if (!store.authUser || store.offlineSession) return loadLocalConversations(); try { conversations.value = (await apiListAgentSessions() || []).map(item => ({ ...item, messages: [] })); activeId.value = conversations.value[0]?.id || ''; if (activeId.value) await loadMessages(activeId.value) } catch { loadLocalConversations() } }
function closeConversationMenu() { conversationMenu.value = { item: null, x: 0, y: 0 } }
async function openConversationMenu(event, item, fromButton = false) {
  const width = 190, height = 146, margin = 8
  const anchor = fromButton ? event.currentTarget.getBoundingClientRect() : null
  const rawX = anchor ? anchor.right - width : event.clientX
  const rawY = anchor ? anchor.bottom + 6 : event.clientY
  conversationMenu.value = { item, x: Math.max(margin, Math.min(rawX, window.innerWidth - width - margin)), y: Math.max(margin, Math.min(rawY, window.innerHeight - height - margin)) }
  await nextTick()
  conversationMenuEl.value?.querySelector('button')?.focus()
}
function handleConversationMenuKeydown(event) {
  if (event.key === 'Escape') { event.preventDefault(); closeConversationMenu(); return }
  if (!['ArrowDown', 'ArrowUp', 'Home', 'End'].includes(event.key)) return
  event.preventDefault()
  const items = Array.from(conversationMenuEl.value?.querySelectorAll('[role="menuitem"]') || [])
  if (!items.length) return
  const current = Math.max(0, items.indexOf(document.activeElement))
  const next = event.key === 'Home' ? 0 : event.key === 'End' ? items.length - 1 : (current + (event.key === 'ArrowDown' ? 1 : -1) + items.length) % items.length
  items[next].focus()
}
function closeConversationDialog() { conversationDialog.value = { type: '', item: null }; renameTitle.value = '' }
async function showRenameDialog(item) { closeConversationMenu(); conversationDialog.value = { type: 'rename', item }; renameTitle.value = item.title; await nextTick(); renameInput.value?.focus(); renameInput.value?.select() }
function showDeleteDialog(item) { closeConversationMenu(); conversationDialog.value = { type: 'delete', item } }
function removeConversationFromList(id) { conversations.value = conversations.value.filter(item => item.id !== id); if (activeId.value === id) activeId.value = conversations.value[0]?.id || ''; persist() }
function newConversation() { closeConversationMenu(); activeId.value = ''; prompt.value = ''; attachments.value = []; autoFollow.value = true; if (window.innerWidth < 900) sidebarOpen.value = false }
async function openConversation(id) { closeConversationMenu(); activeId.value = id; autoFollow.value = true; const target = activeConversation.value; if (store.authUser && !store.offlineSession && target && !target.messages?.length) try { await loadMessages(id) } catch { message.error('历史消息加载失败') }; if (window.innerWidth < 900) sidebarOpen.value = false; scrollToBottom(true) }
async function archiveConversation(item) { closeConversationMenu(); if (!store.authUser || store.offlineSession) return message.warning('本地会话暂不支持归档'); try { await apiArchiveAgentSession(item.id); removeConversationFromList(item.id); message.success('会话已归档') } catch { message.error('归档会话失败') } }
async function deleteConversation(id) { if (!id) return; if (store.authUser && !store.offlineSession) try { await apiDeleteAgentSession(id) } catch { return message.error('删除会话失败') }; removeConversationFromList(id); closeConversationDialog(); message.success('会话已删除') }
async function renameConversation() { const item = conversationDialog.value.item, title = renameTitle.value.trim(); if (!item || !title || title === item.title) return closeConversationDialog(); try { const updated = store.authUser && !store.offlineSession ? await apiUpdateAgentSession(item.id, { title }) : { title }; item.title = updated?.title || title; item.updatedAt = updated?.updatedAt || item.updatedAt; persist(); closeConversationDialog(); message.success('会话已重命名') } catch { message.error('修改标题失败') } }
function formatTime(value) { return new Intl.DateTimeFormat('zh-CN', { month: 'numeric', day: 'numeric', hour: '2-digit', minute: '2-digit' }).format(new Date(value)) }
function formatMessageTime(value) { return new Intl.DateTimeFormat('zh-CN', { hour: '2-digit', minute: '2-digit' }).format(new Date(value)) }
function formatNumber(value) { return new Intl.NumberFormat('zh-CN').format(Number(value || 0)) }
function formatDuration(value) { const ms = Math.max(0, Number(value || 0)); if (ms < 1000) return `${ms}ms`; if (ms < 60_000) return `${(ms / 1000).toFixed(ms < 10_000 ? 1 : 0)}s`; return `${Math.floor(ms / 60_000)}m ${Math.round((ms % 60_000) / 1000)}s` }
function replyTokens(usage) { return Math.max(0, Number(usage?.outputTokens || 0) - Number(usage?.reasoningTokens || 0)) }
function cacheHitRate(usage) { const hit = Number(usage?.cacheHitTokens || 0), miss = Number(usage?.cacheMissTokens || 0); return hit + miss ? Math.round(hit / (hit + miss) * 100) : 0 }
function toolLabel(name) { return ({ 'ledger.books.list': '查询账本', 'ledger.overview': '查询账本概览', 'ledger.transactions.search': '查询账本流水', 'ledger.reports.summary': '生成账本报表', 'ledger.budgets.list': '查询预算', 'worktime.settings.get': '读取工时设置', 'worktime.records.search': '查询工时记录' })[name] || name }
async function copyMessage(item) { try { await navigator.clipboard.writeText(item.content || '') } catch { const area = document.createElement('textarea'); area.value = item.content || ''; document.body.appendChild(area); area.select(); document.execCommand('copy'); area.remove() }; copiedId.value = item.id; window.setTimeout(() => { if (copiedId.value === item.id) copiedId.value = '' }, 1600) }
function handleMetaToggle(event) { const current = event.currentTarget; if (!current.open) return; current.closest('.message-meta')?.querySelectorAll('details[open]').forEach(detail => { if (detail !== current) detail.open = false }) }
function closeMetaPopovers(event) { document.querySelectorAll('.message-meta details[open]').forEach(detail => { if (!detail.contains(event.target)) detail.open = false }); if (conversationMenu.value.item && !conversationMenuEl.value?.contains(event.target) && !event.target.closest?.('[aria-haspopup="menu"]')) closeConversationMenu() }
function openCard(card) { if (card.planned) { prompt.value = `打开${card.title}`; submitPrompt(); return }; router.push(card.route) }
function addFiles(event, type) { attachments.value.push(...Array.from(event.target.files || []).map(file => ({ id: uid(), type, name: file.name, size: file.size }))); event.target.value = '' }
function removeAttachment(id) { attachments.value = attachments.value.filter(item => item.id !== id) }
function removeQueuedPrompt(id) { queuedPrompts.value = queuedPrompts.value.filter(item => item.id !== id) }
function clearPromptQueue(conversationId) { queuedPrompts.value = queuedPrompts.value.filter(item => item.conversationId !== conversationId) }
function renderMarkdown(content) { return DOMPurify.sanitize(marked.parse(content || '', { breaks: true }), { ALLOWED_TAGS: ['p', 'br', 'strong', 'em', 'del', 'h1', 'h2', 'h3', 'h4', 'ul', 'ol', 'li', 'blockquote', 'pre', 'code', 'table', 'thead', 'tbody', 'tr', 'th', 'td', 'a', 'hr'], ALLOWED_ATTR: ['href', 'title'] }) }
function handleMessageWheel(event) { if (event.deltaY < 0) { userPausedFollow = true; autoFollow.value = false } }
function handleMessageScroll() {
  const viewport = messageViewport.value
  if (!viewport) return
  const movingUp = viewport.scrollTop < lastMessageScrollTop - 1
  const movingDown = viewport.scrollTop > lastMessageScrollTop + 1
  const atBottom = viewport.scrollHeight - viewport.scrollTop - viewport.clientHeight <= 2
  if (!programmaticScroll && movingUp) { userPausedFollow = true; autoFollow.value = false }
  else if (!programmaticScroll && movingDown && atBottom) { userPausedFollow = false; autoFollow.value = true }
  else if (!programmaticScroll && userPausedFollow) autoFollow.value = false
  lastMessageScrollTop = viewport.scrollTop
}
function createReplyPlaceholder(conversation) { const reply = { id: uid(), role: 'assistant', content: '', createdAt: Date.now(), typing: true, status: 'RECEIVED', toolExecutions: [] }; conversation.messages.push(reply); persist(); scrollToBottom(); return conversation.messages.at(-1) }
function applyReply(target, response) { Object.assign(target, responseFields(response), inferRoute(response.content || ''), { content: response.content || '模型没有返回可显示的内容，请稍后重试。', typing: false, status: 'COMPLETED' }); persist(); scrollToBottom() }
async function recoverTurn(target) { for (let attempt = 0; attempt < 4; attempt++) { try { const turn = await apiGetAgentTurn(target.turnId); target.status = turn.status; if (turn.status === 'COMPLETED' && turn.responseJson) { applyReply(target, parseMetadata(turn.responseJson)); return true }; if (['FAILED', 'CANCELLED'].includes(turn.status)) { target.content = turn.errorMessage || (turn.status === 'CANCELLED' ? '本轮对话已取消。' : 'Agent 执行失败。'); target.typing = false; persist(); return true } } catch { /* bounded recovery */ }; await new Promise(resolve => window.setTimeout(resolve, 500 * (attempt + 1))) }; target.content ||= '响应连接已中断，后台仍可能在执行。刷新页面后可恢复已完成消息。'; target.typing = false; persist(); return false }
async function ensureConversation(text, pendingAttachments) {
  let conversation = activeConversation.value
  if (conversation) return conversation
  if (conversationCreationPromise) return conversationCreationPromise
  conversationCreationPromise = (async () => {
    const now = Date.now(), draft = { id: uid(), title: text.slice(0, 24) || pendingAttachments[0]?.name || '新对话', updatedAt: now, messages: [] }
    if (store.authUser && !store.offlineSession) try { conversation = { ...(await apiCreateAgentSession({ id: draft.id, title: draft.title })), messages: [] } } catch { message.error('无法创建服务端会话'); return null }
    else conversation = draft
    conversations.value.unshift(conversation); activeId.value = conversation.id
    return conversation
  })()
  try { return await conversationCreationPromise } finally { conversationCreationPromise = null }
}
async function submitPrompt() {
  if (!canSubmit.value) return
  const text = prompt.value.trim() || '请分析这些附件', pendingAttachments = attachments.value.map(item => ({ ...item }))
  prompt.value = ''; attachments.value = []
  const conversation = await ensureConversation(text, pendingAttachments)
  if (!conversation) return
  const pending = { id: uid(), conversationId: conversation.id, text, attachments: pendingAttachments }
  if (turnRunning.value) { queuedPrompts.value.push(pending); message.success('消息已加入队列'); return }
  await executePrompt(pending)
}
async function executePrompt(pending) {
  const conversation = conversations.value.find(item => item.id === pending.conversationId)
  if (!conversation) return processNextPrompt()
  turnRunning.value = true
  const { text, attachments: pendingAttachments } = pending, now = Date.now()
  conversation.messages ||= []
  conversation.messages.push({ id: uid(), role: 'user', content: text, createdAt: now, attachments: pendingAttachments })
  conversation.updatedAt = now; conversations.value = [conversation, ...conversations.value.filter(item => item.id !== conversation.id)]; persist(); await scrollToBottom()
  const assistant = createReplyPlaceholder(conversation), clientRequestId = uid(); let received = false
  activeTurnController = new AbortController()
  try {
    await apiStreamAgentTurn(conversation.id, { clientRequestId, message: text }, async (event, data) => {
      received = true
      if (event === 'turn.received') { assistant.turnId = data.turnId; assistant.status = data.status }
      else if (event === 'turn.status') assistant.status = data.status
      else if (event === 'assistant.delta') { assistant.content += data.content || ''; await scrollToBottom() }
      else if (event === 'tool.started') assistant.toolExecutions.push({ name: data.name, status: 'RUNNING', summary: '正在调用', durationMs: 0 })
      else if (event === 'tool.completed') { const index = assistant.toolExecutions.findIndex(tool => tool.name === data.execution?.name && tool.status === 'RUNNING'); if (index >= 0) assistant.toolExecutions.splice(index, 1, data.execution); else assistant.toolExecutions.push(data.execution) }
      else if (event === 'turn.completed') applyReply(assistant, data.response || {})
      else if (event === 'turn.failed') throw new Error(data.message || 'Agent 执行失败')
    }, activeTurnController.signal)
    if (assistant.typing && assistant.turnId) await recoverTurn(assistant)
  } catch (error) {
    if (!received) try { applyReply(assistant, await apiChatWithAssistant(text, conversation.id)); return } catch (fallback) { error = fallback }
    else if (assistant.turnId && await recoverTurn(assistant)) return
    assistant.content = error?.response?.data?.detail || error?.message || '暂时无法连接 AI 服务，请稍后重试。'; assistant.typing = false; assistant.status = 'FAILED'; persist()
  } finally { activeTurnController = null; turnRunning.value = false; await processNextPrompt() }
}
async function processNextPrompt() { if (turnRunning.value || !queuedPrompts.value.length) return; const next = queuedPrompts.value.shift(); await executePrompt(next) }
async function scrollToBottom(force = false) {
  if (force) { userPausedFollow = false; autoFollow.value = true }
  if (!autoFollow.value) return
  await nextTick()
  const viewport = messageViewport.value
  if (!viewport) return
  programmaticScroll = true
  viewport.scrollTop = viewport.scrollHeight
  lastMessageScrollTop = viewport.scrollTop
  window.requestAnimationFrame(() => { programmaticScroll = false })
}
async function toggleRecording() { if (recording.value) return mediaRecorder?.stop(); if (!navigator.mediaDevices?.getUserMedia || typeof MediaRecorder === 'undefined') return message.warning('当前浏览器不支持录音'); try { mediaStream = await navigator.mediaDevices.getUserMedia({ audio: true }); mediaRecorder = new MediaRecorder(mediaStream); recordingStartedAt = Date.now(); mediaRecorder.addEventListener('stop', () => { attachments.value.push({ id: uid(), type: 'audio', name: `语音 ${Math.max(1, Math.round((Date.now() - recordingStartedAt) / 1000))} 秒` }); mediaStream?.getTracks().forEach(track => track.stop()); recording.value = false }, { once: true }); mediaRecorder.start(); recording.value = true } catch { message.warning('无法使用麦克风，请检查浏览器权限') } }

onMounted(async () => { await loadConversations(); sidebarOpen.value = window.innerWidth >= 900; document.addEventListener('pointerdown', closeMetaPopovers); scrollToBottom(true) })
onBeforeUnmount(() => { document.removeEventListener('pointerdown', closeMetaPopovers); activeTurnController?.abort(); if (mediaRecorder?.state === 'recording') mediaRecorder.stop(); mediaStream?.getTracks().forEach(track => track.stop()) })
</script>
