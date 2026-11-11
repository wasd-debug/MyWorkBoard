<template>
  <HomeWidgetFrame title="AI 助手" kicker="ASSISTANT" :meta="metaLabel" :editing="editing">
    <template #actions>
      <button v-if="history.length" class="hw-btn ghost" type="button" @click="historyOpen = true">{{ history.length }} 条已存</button>
      <button v-if="messages.length" class="hw-btn ghost" type="button" @click="resetChat">新对话</button>
    </template>

    <div ref="viewport" class="ai-thread" :class="{ 'is-empty': !messages.length }">
      <div v-if="!messages.length" class="ai-hint">
        <span class="ai-mark"><Sparkles :size="13" /></span>
        <p>问一句就行：今天还剩什么、本月花了多少、有没有超预算。</p>
        <small>随手问不会进 AI 页面的会话列表；想留下就点「保存会话」。</small>
      </div>
      <article v-for="item in messages" :key="item.id" class="ai-msg" :class="item.role">
        <div v-if="item.role === 'assistant'" class="ai-mark small"><Sparkles :size="11" /></div>
        <div class="ai-bubble">
          <div v-if="item.role === 'assistant'" class="ai-md" v-html="render(item.content)"></div>
          <p v-else>{{ item.content }}</p>
          <span v-if="item.typing" class="ai-typing">▍</span>
        </div>
      </article>
      <p v-if="notice" class="ai-notice">{{ notice }}</p>
    </div>

    <div v-if="pendingActions.length" class="ai-actions">
      <span>
        <b>{{ pendingActions.length }} 个待确认操作</b>
        <small>{{ actionsSummary }}</small>
      </span>
      <button type="button" @click="openActions">{{ actionsTargetLabel }}</button>
    </div>

    <form class="ai-composer" @submit.prevent="send">
      <input v-model="draft" type="text" maxlength="500" :placeholder="sending ? '正在回复…' : '问点什么…'" :disabled="sending" aria-label="给 AI 助手发送消息" />
      <button class="hw-btn primary" type="submit" :disabled="!draft.trim() || sending" :aria-label="sending ? '正在生成' : '发送'"><ArrowUp :size="13" /></button>
    </form>

    <template #footer>
      <span>{{ saved ? '已保存到本机' : '默认不保存' }}</span>
      <button class="ai-save" type="button" :disabled="!messages.length || saved" @click="saveSession">{{ saved ? '已保存' : '保存会话' }}</button>
    </template>

    <Dialog :open="historyOpen" title="已保存的会话" content-class="ai-history-dialog" @update:open="historyOpen = $event">
      <ul v-if="history.length" class="ai-history">
        <li v-for="row in history" :key="row.id">
          <button type="button" @click="loadSession(row)">
            <b>{{ row.title }}</b>
            <small>{{ row.messages.length }} 条 · {{ formatTime(row.savedAt) }}</small>
          </button>
          <button class="ai-history-del" type="button" :aria-label="`删除会话 ${row.title}`" @click="removeSession(row.id)"><Trash2 :size="13" /></button>
        </li>
      </ul>
      <p v-else class="ai-history-empty">还没有保存过会话。</p>
    </Dialog>
  </HomeWidgetFrame>
</template>

<script setup>
import { computed, nextTick, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ArrowUp, Sparkles, Trash2 } from 'lucide-vue-next'
import DOMPurify from 'dompurify'
import { marked } from 'marked'
import HomeWidgetFrame from './HomeWidgetFrame.vue'
import Dialog from '../ui/Dialog.vue'
import { apiChatWithAssistant, apiKeepAgentSession } from '../../../packages/api-client/src/index.js'
import { useAppStore } from '../../stores/app.js'
import { message } from '../../services/message.js'
import { uuid } from '../../utils/uuid.js'
import { actionNeedsApproval, agentActionTypeLabel } from '../../utils/agentActionLabels.js'

defineProps({ editing: Boolean })

const router = useRouter()
const store = useAppStore()
const draft = ref('')
const sending = ref(false)
const messages = ref([])
const sessionId = ref('')
const saved = ref(false)
const notice = ref('')
const viewport = ref(null)
const historyOpen = ref(false)
const history = ref(readHistory())
const pendingActions = ref([])

const storageKey = computed(() => `home_ai_sessions_v1:${store.accountScope || 'local'}`)
const metaLabel = computed(() => messages.value.length ? `${messages.value.length} 条消息 · 未保存` : '临时对话')
const actionsSummary = computed(() => {
  const labels = [...new Set(pendingActions.value.map(action => agentActionTypeLabel(action.structuredContent?.actionType, '待确认操作')))]
  return labels.length > 2 ? `${labels.slice(0, 2).join('、')} 等 ${labels.length} 类` : labels.join('、')
})
// 全是高危动作时只有审批中心能执行；有普通动作则去完整对话页逐笔核对
const onlyApprovalActions = computed(() => pendingActions.value.length > 0 && pendingActions.value.every(actionNeedsApproval))
const actionsTargetLabel = computed(() => onlyApprovalActions.value ? '去审批中心' : '去完整对话处理')

function readHistory() {
  try {
    const rows = JSON.parse(localStorage.getItem(`home_ai_sessions_v1:${store.accountScope || 'local'}`) || '[]')
    return Array.isArray(rows) ? rows : []
  } catch {
    return []
  }
}

function persistHistory() {
  try { localStorage.setItem(storageKey.value, JSON.stringify(history.value.slice(0, 30))) } catch { /* 配额满时忽略 */ }
}

function render(content) {
  return DOMPurify.sanitize(marked.parse(content || '', { breaks: true }), {
    ALLOWED_TAGS: ['p', 'br', 'strong', 'em', 'del', 'ul', 'ol', 'li', 'code', 'pre', 'blockquote'],
    ALLOWED_ATTR: []
  })
}

function formatTime(value) {
  if (!value) return ''
  const date = new Date(value)
  const pad = number => String(number).padStart(2, '0')
  return `${pad(date.getMonth() + 1)}/${pad(date.getDate())} ${pad(date.getHours())}:${pad(date.getMinutes())}`
}

async function scrollToEnd() {
  await nextTick()
  if (viewport.value) viewport.value.scrollTop = viewport.value.scrollHeight
}

function resetChat() {
  messages.value = []
  sessionId.value = ''
  saved.value = false
  notice.value = ''
  pendingActions.value = []
}

async function send() {
  const text = draft.value.trim()
  if (!text || sending.value) return
  draft.value = ''
  notice.value = ''
  saved.value = false
  pendingActions.value = []
  messages.value.push({ id: uuid(), role: 'user', content: text })
  const reply = { id: uuid(), role: 'assistant', content: '', typing: true }
  messages.value.push(reply)
  sending.value = true
  await scrollToEnd()
  try {
    // ephemeral：会话可续聊、动作卡可确认，但不进 AI 页面的会话列表
    const response = await apiChatWithAssistant(text, sessionId.value, { ephemeral: true })
    sessionId.value = response?.sessionId || sessionId.value
    reply.content = response?.content || '本轮没有返回内容。'
    reply.typing = false
    pendingActions.value = response?.actions || []
    if (pendingActions.value.length && pendingActions.value.every(actionNeedsApproval)) notice.value = '有高风险操作，需要到审批中心确认。'
  } catch (error) {
    reply.typing = false
    reply.content = error?.response?.data?.detail || error?.message || '暂时连不上 AI，稍后再试。'
    messages.value = messages.value.filter(item => item.id !== reply.id)
    message.error('AI 助手请求失败')
  } finally {
    sending.value = false
    await scrollToEnd()
  }
}

async function saveSession() {
  if (!messages.value.length) return
  const first = messages.value.find(item => item.role === 'user')?.content || 'AI 对话'
  const row = {
    id: uuid(),
    title: first.slice(0, 30),
    savedAt: new Date().toISOString(),
    sessionId: sessionId.value,
    messages: messages.value.filter(item => !item.typing).map(item => ({ role: item.role, content: item.content }))
  }
  history.value = [row, ...history.value].slice(0, 30)
  persistHistory()
  saved.value = true
  if (sessionId.value) {
    try {
      await apiKeepAgentSession(sessionId.value)
      message.success('会话已保存，可在 AI 页面继续查看')
    } catch {
      message.error('已存到本机，但同步到会话列表失败')
    }
  } else {
    message.success('会话已保存到本机')
  }
}

function openActions() {
  if (onlyApprovalActions.value || !sessionId.value) {
    router.push('/approvals')
    return
  }
  const title = messages.value.find(item => item.role === 'user')?.content?.slice(0, 40) || '临时对话'
  router.push({ path: '/agent/chat', query: { session: sessionId.value, title } })
}

function loadSession(row) {
  messages.value = row.messages.map(item => ({ ...item, id: uuid() }))
  sessionId.value = row.sessionId || ''
  saved.value = true
  notice.value = ''
  pendingActions.value = []
  historyOpen.value = false
  scrollToEnd()
}

function removeSession(id) {
  history.value = history.value.filter(item => item.id !== id)
  persistHistory()
}
</script>

<style scoped>
.ai-thread { display: flex; max-height: 236px; min-height: 118px; flex-direction: column; gap: 8px; overflow-y: auto; padding-right: 2px; }
.ai-thread.is-empty { justify-content: center; }
.ai-hint { display: grid; gap: 6px; justify-items: center; padding: 4px 8px; text-align: center; }
.ai-mark { display: grid; width: 26px; height: 26px; place-items: center; border-radius: 9px; background: var(--accent-soft); color: var(--accent); }
.ai-mark.small { flex: 0 0 20px; width: 20px; height: 20px; border-radius: 7px; }
.ai-hint p { margin: 0; color: var(--ink2); font-size: 11.5px; line-height: 1.6; }
.ai-hint small { color: var(--muted); font-size: 10.5px; line-height: 1.55; }
.ai-msg { display: flex; gap: 7px; align-items: flex-start; }
.ai-msg.user { justify-content: flex-end; }
.ai-bubble { min-width: 0; max-width: 88%; padding: 7px 10px; border-radius: 12px; background: var(--paper); color: var(--ink2); font-size: 12px; line-height: 1.6; }
.ai-msg.user .ai-bubble { background: var(--accent); color: var(--paper); }
.ai-msg.user p { margin: 0; }
.ai-md :deep(p) { margin: 0 0 4px; }
.ai-md :deep(p:last-child) { margin-bottom: 0; }
.ai-md :deep(ul), .ai-md :deep(ol) { margin: 4px 0; padding-left: 17px; }
.ai-md :deep(code) { padding: 1px 4px; border-radius: 4px; background: color-mix(in srgb, var(--ink) 8%, transparent); font-size: 11px; }
.ai-typing { color: var(--accent); font-size: 11px; }
.ai-notice { margin: 0; color: var(--warn, var(--down)); font-size: 10.5px; }
.ai-actions { display: flex; align-items: center; gap: 9px; margin-top: 10px; padding: 8px 10px; border: 1px solid color-mix(in srgb, var(--accent) 30%, var(--line)); border-radius: 10px; background: color-mix(in srgb, var(--accent-soft) 48%, var(--card)); }
.ai-actions > span { display: grid; min-width: 0; flex: 1; gap: 2px; }
.ai-actions b { color: var(--ink); font-size: 11.5px; }
.ai-actions small { overflow: hidden; color: var(--muted); font-size: 10px; text-overflow: ellipsis; white-space: nowrap; }
.ai-actions button { min-width: 0; min-height: 0; flex: 0 0 auto; padding: 6px 10px; border: 1px solid var(--accent); border-radius: 8px; background: var(--accent); color: var(--paper); font-size: 11px; font-weight: 650; }
.ai-composer { display: flex; gap: 6px; margin-top: 10px; }
.ai-composer input { min-width: 0; height: 32px; flex: 1; padding: 0 10px; border: 1px solid var(--line2); border-radius: 9px; background: var(--paper); color: var(--ink); font-size: 12px; }
.ai-composer input:focus { border-color: var(--accent); outline: 0; box-shadow: 0 0 0 3px color-mix(in srgb, var(--accent) 12%, transparent); }
.ai-composer .hw-btn { width: 32px; height: 32px; justify-content: center; padding: 0; }
.ai-save { padding: 0; border: 0; background: transparent; color: var(--accent); font-size: 11px; font-weight: 650; }
.ai-save:disabled { color: var(--muted); cursor: default; }
.ai-history { display: flex; flex-direction: column; gap: 4px; margin: 0; padding: 0; list-style: none; }
.ai-history li { display: flex; align-items: center; gap: 6px; }
.ai-history li > button:first-child { min-width: 0; flex: 1; padding: 9px 10px; border: 1px solid var(--line); border-radius: 9px; background: var(--card); text-align: left; }
.ai-history li > button:first-child:hover { border-color: var(--accent); }
.ai-history b, .ai-history small { display: block; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.ai-history b { color: var(--ink); font-size: 12px; }
.ai-history small { color: var(--muted); font-size: 10px; }
.ai-history-del { display: grid; width: 28px; height: 28px; place-items: center; padding: 0; border: 1px solid var(--line); border-radius: 8px; background: transparent; color: var(--muted); }
.ai-history-del:hover { border-color: var(--down); color: var(--down); }
.ai-history-empty { margin: 0; padding: 18px 4px; color: var(--muted); font-size: 12px; text-align: center; }
</style>
