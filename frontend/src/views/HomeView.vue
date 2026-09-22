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

      <div ref="messageViewport" class="chat-message-viewport" @scroll.passive="handleMessageScroll">
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
            </div>
          </article>
        </div>
      </div>

      <button v-if="activeMessages.length && !autoFollow" class="scroll-to-bottom" type="button" title="滚动到底部并继续跟随" aria-label="滚动到底部并继续跟随" @click="scrollToBottom(true)"><ArrowDown /></button>

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
import DOMPurify from 'dompurify'
import { marked } from 'marked'
import { ArrowDown, ArrowUp, ArrowUpRight, Image as ImageIcon, MessageSquareText, Mic, PanelLeftClose, PanelLeftOpen, Paperclip, Sparkles, Square, SquarePen, Trash2, X } from 'lucide-vue-next'
import { Calendar, List, Timer, Wallet } from '../icons.js'
import { message } from '../services/message.js'
import { useAppStore } from '../stores/app'
import { apiChatWithAssistant } from '../../packages/api-client/src/index.js'

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
const autoFollow = ref(true)
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
function newConversation() { activeId.value = ''; prompt.value = ''; attachments.value = []; autoFollow.value = true; if (window.innerWidth < 900) sidebarOpen.value = false }
function openConversation(id) { activeId.value = id; autoFollow.value = true; if (window.innerWidth < 900) sidebarOpen.value = false; scrollToBottom(true) }
function deleteConversation(id) { conversations.value = conversations.value.filter(item => item.id !== id); if (activeId.value === id) activeId.value = conversations.value[0]?.id || ''; persist() }
function formatTime(value) { return new Intl.DateTimeFormat('zh-CN', { month: 'numeric', day: 'numeric', hour: '2-digit', minute: '2-digit' }).format(new Date(value)) }
function openCard(card) { if (card.planned) { prompt.value = `打开${card.title}`; submitPrompt(); return } router.push(card.route) }
function addFiles(event, type) {
  attachments.value.push(...Array.from(event.target.files || []).map(file => ({ id: uid(), type, name: file.name, size: file.size })))
  event.target.value = ''
}
function removeAttachment(id) { attachments.value = attachments.value.filter(item => item.id !== id) }
function renderMarkdown(content) {
  return DOMPurify.sanitize(marked.parse(content || '', { breaks: true }), {
    ALLOWED_TAGS: ['p', 'br', 'strong', 'em', 'del', 'h1', 'h2', 'h3', 'h4', 'ul', 'ol', 'li', 'blockquote', 'pre', 'code', 'table', 'thead', 'tbody', 'tr', 'th', 'td', 'a', 'hr'],
    ALLOWED_ATTR: ['href', 'title'],
  })
}
function handleMessageScroll() {
  const viewport = messageViewport.value
  if (!viewport) return
  autoFollow.value = viewport.scrollHeight - viewport.scrollTop - viewport.clientHeight <= 48
}
function createReplyPlaceholder(conversation) {
  const assistantMessage = { id: uid(), role: 'assistant', content: '正在思考…', typing: true }
  conversation.messages.push(assistantMessage)
  persist()
  scrollToBottom()
  return conversation.messages[conversation.messages.length - 1]
}
function typeReply(assistantMessage, reply) {
  assistantMessage.content = ''
  assistantMessage.route = reply.route
  assistantMessage.routeLabel = reply.routeLabel
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
function inferRoute(text) {
  if (/工时|打卡|上班|下班/.test(text)) return { route: '/punch', routeLabel: '工时' }
  if (/报表/.test(text)) return { route: '/ledger/reports', routeLabel: '账本报表' }
  if (/流水/.test(text)) return { route: '/ledger/transactions', routeLabel: '账本流水' }
  if (/记账|支出|收入|账本/.test(text)) return { route: '/ledger', routeLabel: '账本' }
  return {}
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
  conversation.updatedAt = now
  conversations.value = [conversation, ...conversations.value.filter(item => item.id !== conversation.id)]
  prompt.value = ''
  attachments.value = []
  persist()
  await scrollToBottom()
  // Re-read the proxied conversation after inserting a brand-new raw object into the ref array.
  conversation = activeConversation.value
  const assistantMessage = createReplyPlaceholder(conversation)
  try {
    const result = await apiChatWithAssistant(text || '请分析这些附件', conversation.id)
    const reply = {
      content: result?.content || '模型没有返回可显示的内容，请稍后重试。',
      ...inferRoute(text),
    }
    if (result?.configured === false) {
      reply.content = 'DeepSeek 尚未配置。请在启动后端的环境中设置 DEEPSEEK_API_KEY，然后重启后端再试。'
    }
    typeReply(assistantMessage, reply)
  } catch (error) {
    const detail = error?.problem?.detail || error?.problem?.message || error?.response?.data?.detail || error?.response?.data?.message || error?.message
    typeReply(assistantMessage, {
      content: detail || '暂时无法连接 AI 服务，请确认后端已启动并稍后重试。',
      ...inferRoute(text),
    })
  }
}
async function scrollToBottom(force = false) {
  if (force) autoFollow.value = true
  if (!autoFollow.value) return
  await nextTick()
  const viewport = messageViewport.value
  if (!viewport) return
  viewport.scrollTop = viewport.scrollHeight
}
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

onMounted(() => { loadConversations(); sidebarOpen.value = window.innerWidth >= 900; scrollToBottom(true) })
onBeforeUnmount(() => {
  typingTimers.forEach(timer => window.clearInterval(timer))
  typingTimers.clear()
  if (mediaRecorder?.state === 'recording') mediaRecorder.stop()
  mediaStream?.getTracks().forEach(track => track.stop())
})
</script>
