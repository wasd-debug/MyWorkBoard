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
        <button v-for="item in conversations" :key="item.id" type="button" :class="{ active: item.id === activeId }" @click="openConversation(item.id)">
          <MessageSquareText aria-hidden="true" />
          <span><b>{{ item.title }}</b><small>{{ formatTime(item.updatedAt) }}</small></span>
          <i role="button" tabindex="0" title="删除会话" aria-label="删除会话" @click.stop="deleteConversation(item.id)" @keydown.enter.stop="deleteConversation(item.id)"><Trash2 /></i>
        </button>
      </nav>
      <div class="chat-sidebar-foot"><span class="status-dot" :class="{ online: store.dbMode }"></span>{{ store.dbMode ? '数据已同步' : '本地会话' }}</div>
    </aside>

    <section class="chat-workspace">
      <header class="chat-workspace-head">
        <button v-if="!sidebarOpen" class="chat-icon-button" type="button" title="展开历史会话" aria-label="展开历史会话" @click="sidebarOpen = true"><PanelLeftOpen /></button>
        <div><b>{{ activeConversation?.title || '新对话' }}</b><small>AI 个人工作台</small></div>
      </header>

      <div ref="messageViewport" class="chat-message-viewport">
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
              <p>{{ item.content }}<span v-if="item.typing" class="typing-cursor" aria-label="正在输入">▍</span></p>
              <div v-if="item.attachments?.length" class="message-attachments">
                <span v-for="file in item.attachments" :key="file.id"><ImageIcon v-if="file.type === 'image'" /><Mic v-else-if="file.type === 'audio'" /><Paperclip v-else />{{ file.name }}</span>
              </div>
              <button v-if="item.route && !item.typing" class="message-route" type="button" @click="router.push(item.route)">进入{{ item.routeLabel }}<ArrowUpRight /></button>
            </div>
          </article>
        </div>
      </div>

      <div class="chat-composer-wrap">
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
import { ArrowUp, ArrowUpRight, Image as ImageIcon, MessageSquareText, Mic, PanelLeftClose, PanelLeftOpen, Paperclip, Sparkles, Square, SquarePen, Trash2, X } from 'lucide-vue-next'
import { Calendar, List, Timer, Wallet } from '../icons.js'
import { message } from '../services/message.js'
import { useAppStore } from '../stores/app'

const router = useRouter()
const store = useAppStore()
const sidebarOpen = ref(true)
const conversations = ref([])
const activeId = ref('')
const prompt = ref('')
const attachments = ref([])
const fileInput = ref(null)
const imageInput = ref(null)
const messageViewport = ref(null)
const recording = ref(false)
let mediaRecorder = null
let mediaStream = null
let recordingStartedAt = 0
const typingTimers = new Set()

const historyKey = computed(() => `workspace_ai_conversations_v1:${store.accountScope || 'local'}`)
const displayName = computed(() => store.authUser?.nickname || store.authUser?.username || '建胜')
const activeConversation = computed(() => conversations.value.find(item => item.id === activeId.value) || null)
const activeMessages = computed(() => activeConversation.value?.messages || [])
const canSubmit = computed(() => Boolean(prompt.value.trim() || attachments.value.length))
const cards = [
  { key: 'ledger', title: '账本', description: '记账、流水与报表', route: '/ledger', icon: Wallet, tone: 'yellow' },
  { key: 'knowledge', title: '知识库', description: '整理与连接知识', icon: Calendar, tone: 'cyan', planned: true },
  { key: 'worktime', title: '工时', description: '打卡、补录与统计', route: '/punch', icon: Timer, tone: 'pink' },
  { key: 'tasks', title: '任务', description: '拆解与推进计划', icon: List, tone: 'peach', planned: true }
]

function uid() { return `${Date.now().toString(36)}-${Math.random().toString(36).slice(2, 8)}` }
function persist() { localStorage.setItem(historyKey.value, JSON.stringify(conversations.value.slice(0, 60))) }
function loadConversations() {
  try {
    const stored = JSON.parse(localStorage.getItem(historyKey.value) || '[]')
    conversations.value = stored.map(item => ({ ...item, messages: (item.messages || []).map(message => ({ ...message, typing: false })) }))
  } catch { conversations.value = [] }
  activeId.value = conversations.value[0]?.id || ''
}
function newConversation() { activeId.value = ''; prompt.value = ''; attachments.value = []; if (window.innerWidth < 900) sidebarOpen.value = false }
function openConversation(id) { activeId.value = id; if (window.innerWidth < 900) sidebarOpen.value = false; scrollToBottom() }
function deleteConversation(id) { conversations.value = conversations.value.filter(item => item.id !== id); if (activeId.value === id) activeId.value = conversations.value[0]?.id || ''; persist() }
function formatTime(value) { return new Intl.DateTimeFormat('zh-CN', { month: 'numeric', day: 'numeric', hour: '2-digit', minute: '2-digit' }).format(new Date(value)) }
function openCard(card) { if (card.planned) { prompt.value = `打开${card.title}`; submitPrompt(); return } router.push(card.route) }
function addFiles(event, type) {
  attachments.value.push(...Array.from(event.target.files || []).map(file => ({ id: uid(), type, name: file.name, size: file.size })))
  event.target.value = ''
}
function removeAttachment(id) { attachments.value = attachments.value.filter(item => item.id !== id) }
function typeReply(conversation, reply) {
  conversation.messages.push({ id: uid(), role: 'assistant', content: '', typing: true, route: reply.route, routeLabel: reply.routeLabel })
  const assistantMessage = conversation.messages[conversation.messages.length - 1]
  let index = 0
  const timer = window.setInterval(() => {
    assistantMessage.content = reply.content.slice(0, index + 1)
    index += 1
    persist()
    scrollToBottom()
    if (index >= reply.content.length) {
      window.clearInterval(timer)
      typingTimers.delete(timer)
      assistantMessage.typing = false
      persist()
    }
  }, 35)
  typingTimers.add(timer)
}
function getReply(text) {
  if (/工时|打卡|上班|下班/.test(text)) return { content: '我已经准备好工时工作区，你可以继续打卡、补录或查看统计。', route: '/punch', routeLabel: '工时' }
  if (/报表/.test(text)) return { content: '我已经为你定位到账本报表，可以按分类、账户、商家和时间范围继续分析。', route: '/ledger/reports', routeLabel: '账本报表' }
  if (/流水/.test(text)) return { content: '我已经为你定位到账本流水，可以继续筛选、编辑或导入记录。', route: '/ledger/transactions', routeLabel: '账本流水' }
  if (/记账|支出|收入|账本/.test(text)) return { content: '我已经准备好账本工作区，可以继续用自然语言记账或查看总览。', route: '/ledger', routeLabel: '账本' }
  if (/知识/.test(text)) return { content: '知识库能力正在规划中，这段需求已经保留在当前会话。' }
  if (/任务|待办|计划/.test(text)) return { content: '任务能力正在规划中，这段需求已经保留在当前会话。' }
  return { content: `已记录“${text || '附件内容'}”。当前可以直接驱动账本与工时，知识库和任务会在后续接入。` }
}
async function submitPrompt() {
  if (!canSubmit.value) return
  const text = prompt.value.trim()
  const now = Date.now()
  let conversation = activeConversation.value
  if (!conversation) {
    conversation = { id: uid(), title: text.slice(0, 24) || attachments.value[0]?.name || '新对话', updatedAt: now, messages: [] }
    conversations.value.unshift(conversation)
    activeId.value = conversation.id
  }
  conversation.messages.push({ id: uid(), role: 'user', content: text || '请分析这些附件', attachments: attachments.value.map(item => ({ ...item })) })
  const reply = getReply(text)
  typeReply(conversation, reply)
  conversation.updatedAt = now
  conversations.value = [conversation, ...conversations.value.filter(item => item.id !== conversation.id)]
  prompt.value = ''
  attachments.value = []
  persist()
  await scrollToBottom()
}
async function scrollToBottom() { await nextTick(); if (messageViewport.value) messageViewport.value.scrollTop = messageViewport.value.scrollHeight }
async function toggleRecording() {
  if (recording.value) { mediaRecorder?.stop(); return }
  if (!navigator.mediaDevices?.getUserMedia || typeof MediaRecorder === 'undefined') { message.warning('当前浏览器不支持录音'); return }
  try {
    mediaStream = await navigator.mediaDevices.getUserMedia({ audio: true })
    mediaRecorder = new MediaRecorder(mediaStream)
    recordingStartedAt = Date.now()
    mediaRecorder.addEventListener('stop', () => {
      const seconds = Math.max(1, Math.round((Date.now() - recordingStartedAt) / 1000))
      attachments.value.push({ id: uid(), type: 'audio', name: `语音 ${seconds} 秒` })
      mediaStream?.getTracks().forEach(track => track.stop())
      recording.value = false
    }, { once: true })
    mediaRecorder.start(); recording.value = true
  } catch { message.warning('无法使用麦克风，请检查浏览器权限') }
}

onMounted(() => { loadConversations(); sidebarOpen.value = window.innerWidth >= 900 })
onBeforeUnmount(() => {
  typingTimers.forEach(timer => window.clearInterval(timer))
  typingTimers.clear()
  if (mediaRecorder?.state === 'recording') mediaRecorder.stop()
  mediaStream?.getTracks().forEach(track => track.stop())
})
</script>
