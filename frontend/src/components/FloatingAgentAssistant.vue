<template>
  <div ref="root" class="floating-agent" :class="{ open }">
    <Transition name="floating-agent-panel">
      <section v-if="open" id="floating-agent-panel" class="floating-agent-panel" role="dialog" aria-modal="false" aria-labelledby="floating-agent-title" @keydown.esc="closePanel">
        <header>
          <span class="floating-agent-mark"><Sparkles /></span>
          <div><b id="floating-agent-title">随身 AI</b><small>当前页面 · {{ currentPageTitle }}</small></div>
          <button type="button" title="关闭" aria-label="关闭随身 AI" @click="closePanel"><X /></button>
        </header>

        <div ref="messageViewport" class="floating-agent-messages" aria-live="polite">
          <div v-if="!messages.length" class="floating-agent-empty">
            <Sparkles />
            <b>随时问我</b>
            <p>查询账本、工时或报表。需要填写和确认的操作，可进入完整工作台继续。</p>
          </div>
          <article v-for="item in messages" :key="item.id" :class="item.role">
            <div class="floating-agent-message" :class="{ failed: item.status === 'FAILED' }">
              <div v-if="item.role === 'assistant'" class="floating-agent-mark small"><Sparkles /></div>
              <div>
                <div v-if="item.role === 'assistant'" class="floating-agent-markdown" v-html="renderMarkdown(item.content)"></div>
                <p v-else>{{ item.content }}</p>
                <span v-if="item.typing" class="typing-cursor" aria-label="正在输入">▍</span>
                <small v-if="item.toolName"><Wrench />{{ item.toolName }} · {{ item.toolStatus }}</small>
              </div>
            </div>
          </article>
        </div>

        <div v-if="requiresFullView" class="floating-agent-handoff">
          <span><ClipboardCheck />此操作需要补充或确认</span>
          <button type="button" @click="openFullConversation">进入完整对话</button>
        </div>

        <form class="floating-agent-composer" @submit.prevent="submitPrompt">
          <label class="sr-only" for="floating-agent-prompt">给随身 AI 发送消息</label>
          <textarea
            id="floating-agent-prompt"
            v-model="prompt"
            v-auto-grow="132"
            rows="1"
            placeholder="询问当前工作台…"
            :disabled="sending"
            @keydown.enter.exact.prevent="submitPrompt"
          ></textarea>
          <div>
            <button class="floating-agent-expand" type="button" :disabled="!sessionId" @click="openFullConversation"><Maximize2 />完整对话</button>
            <button class="floating-agent-send" type="submit" :disabled="!prompt.trim() || sending" :aria-label="sending ? '正在生成' : '发送'"><LoaderCircle v-if="sending" class="spinning" /><ArrowUp v-else /></button>
          </div>
        </form>
      </section>
    </Transition>

    <button class="floating-agent-trigger" type="button" :aria-expanded="open" aria-controls="floating-agent-panel" :title="open ? '关闭随身 AI' : '打开随身 AI'" @click="togglePanel">
      <X v-if="open" />
      <Sparkles v-else />
      <span v-if="!open">AI</span>
    </button>
  </div>
</template>

<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import DOMPurify from 'dompurify'
import { marked } from 'marked'
import { ArrowUp, ClipboardCheck, LoaderCircle, Maximize2, Sparkles, Wrench, X } from 'lucide-vue-next'
import { apiChatWithAssistant, apiCreateAgentSession, apiListAgentMessages, apiStreamAgentTurn } from '../../packages/api-client/src/index.js'
import { autoGrowTextarea } from '../directives/autoGrowTextarea.js'
import { message } from '../services/message.js'
import { useAppStore } from '../stores/app.js'

const route = useRoute()
const router = useRouter()
const store = useAppStore()
const vAutoGrow = autoGrowTextarea
const root = ref(null)
const messageViewport = ref(null)
const open = ref(false)
const prompt = ref('')
const sending = ref(false)
const sessionId = ref('')
const messages = ref([])
const requiresFullView = ref(false)
let controller = null

const currentPageTitle = computed(() => route.meta?.title || '个人工作台')
const sessionStorageKey = computed(() => `workspace_floating_agent_session:${store.accountScope || 'local'}`)
sessionId.value = localStorage.getItem(sessionStorageKey.value) || ''
function uid() { return `${Date.now().toString(36)}-${Math.random().toString(36).slice(2, 9)}` }
function renderMarkdown(content) {
  return DOMPurify.sanitize(marked.parse(content || '', { breaks: true }), {
    ALLOWED_TAGS: ['p', 'br', 'strong', 'em', 'del', 'h1', 'h2', 'h3', 'ul', 'ol', 'li', 'blockquote', 'pre', 'code', 'a'],
    ALLOWED_ATTR: ['href', 'title']
  })
}
function togglePanel() { open.value = !open.value; if (open.value) void scrollToBottom() }
function closePanel() { open.value = false }
async function scrollToBottom() { await nextTick(); if (messageViewport.value) messageViewport.value.scrollTop = messageViewport.value.scrollHeight }
async function ensureSession(text) {
  if (sessionId.value) return sessionId.value
  const id = uid()
  const session = await apiCreateAgentSession({ id, title: text.slice(0, 24) || '随身 AI 对话' })
  sessionId.value = session?.id || id
  localStorage.setItem(sessionStorageKey.value, sessionId.value)
  return sessionId.value
}
async function restoreSessionMessages() {
  if (!sessionId.value) return
  try {
    const rows = await apiListAgentMessages(sessionId.value)
    messages.value = (rows || []).slice(-20).map(row => ({
      id: `server-${row.id}`,
      role: row.role,
      content: row.content,
      typing: false,
      status: 'COMPLETED'
    }))
    await scrollToBottom()
  } catch (error) {
    if (error?.response?.status === 404) {
      sessionId.value = ''
      localStorage.removeItem(sessionStorageKey.value)
    }
  }
}
async function submitPrompt() {
  const text = prompt.value.trim()
  if (!text || sending.value) return
  prompt.value = ''
  requiresFullView.value = false
  messages.value.push({ id: uid(), role: 'user', content: text })
  const assistant = { id: uid(), role: 'assistant', content: '', typing: true, status: 'RECEIVED', toolName: '', toolStatus: '' }
  messages.value.push(assistant)
  sending.value = true
  await scrollToBottom()
  let received = false
  try {
    const id = await ensureSession(text)
    controller = new AbortController()
    await apiStreamAgentTurn(id, { clientRequestId: uid(), message: text, deepThinking: false }, async (event, data) => {
      received = true
      if (event === 'assistant.delta') assistant.content += data.content || ''
      else if (event === 'tool.started') Object.assign(assistant, { toolName: data.name || '工具调用', toolStatus: '执行中' })
      else if (event === 'tool.completed') Object.assign(assistant, { toolName: data.execution?.name || assistant.toolName, toolStatus: data.execution?.status === 'SUCCESS' ? '已完成' : '执行结束' })
      else if (['input.required', 'confirmation.required'].includes(event)) requiresFullView.value = true
      else if (event === 'turn.completed') Object.assign(assistant, { content: data.response?.content || assistant.content || '本轮已完成。', typing: false, status: 'COMPLETED' })
      else if (event === 'turn.cancelled') Object.assign(assistant, { content: assistant.content || '本轮已取消。', typing: false, status: 'CANCELLED' })
      else if (event === 'turn.failed') throw new Error(data.message || 'Agent 执行失败')
      await scrollToBottom()
    }, controller.signal)
    if (assistant.typing) assistant.typing = false
  } catch (error) {
    if (!received && sessionId.value) {
      try {
        const response = await apiChatWithAssistant(text, sessionId.value)
        Object.assign(assistant, { content: response?.content || '本轮已完成。', typing: false, status: 'COMPLETED' })
        requiresFullView.value = Boolean(response?.actions?.length)
        return
      } catch (fallbackError) {
        if (fallbackError?.response?.status === 404 || error?.status === 404) {
          sessionId.value = ''
          localStorage.removeItem(sessionStorageKey.value)
          try {
            const renewedSessionId = await ensureSession(text)
            const response = await apiChatWithAssistant(text, renewedSessionId)
            Object.assign(assistant, { content: response?.content || '本轮已完成。', typing: false, status: 'COMPLETED' })
            requiresFullView.value = Boolean(response?.actions?.length)
            return
          } catch (renewedError) { error = renewedError }
        } else error = fallbackError
      }
    }
    Object.assign(assistant, { content: error?.response?.data?.detail || error?.message || '暂时无法连接 AI，请稍后重试。', typing: false, status: 'FAILED' })
    message.error('随身 AI 请求失败')
  } finally {
    controller = null
    sending.value = false
    await scrollToBottom()
  }
}
async function openFullConversation() {
  if (!sessionId.value) return
  open.value = false
  await router.push({ path: '/', query: { session: sessionId.value } })
}
function handleOutsidePointer(event) { if (open.value && root.value && !root.value.contains(event.target)) closePanel() }
onMounted(() => { document.addEventListener('pointerdown', handleOutsidePointer); void restoreSessionMessages() })
onBeforeUnmount(() => { document.removeEventListener('pointerdown', handleOutsidePointer); controller?.abort() })
</script>

<style scoped>
.floating-agent { position:fixed; z-index:72; right:24px; bottom:24px; display:flex; flex-direction:column; align-items:flex-end; gap:12px; pointer-events:none }
.floating-agent > * { pointer-events:auto }
.floating-agent-trigger { display:flex; min-width:58px; height:58px; align-items:center; justify-content:center; gap:6px; padding:0 16px; border:1px solid color-mix(in srgb,var(--accent) 54%,var(--line)); border-radius:29px; background:var(--accent); color:#fff; box-shadow:0 14px 36px color-mix(in srgb,var(--accent) 34%,transparent); font-weight:800; transition:transform .18s ease,box-shadow .18s ease }
.floating-agent-trigger:hover { transform:translateY(-2px); box-shadow:0 18px 40px color-mix(in srgb,var(--accent) 42%,transparent) }
.floating-agent-trigger svg { width:21px; height:21px }
.floating-agent.open .floating-agent-trigger { min-width:48px; width:48px; height:48px; padding:0; background:var(--ink); border-color:var(--ink) }
.floating-agent.open .floating-agent-trigger span { display:none }
.floating-agent-panel { display:grid; grid-template-rows:auto minmax(180px,1fr) auto auto; width:min(390px,calc(100vw - 32px)); height:min(590px,calc(100dvh - 150px)); overflow:hidden; border:1px solid var(--line); border-radius:22px; background:var(--card); color:var(--ink); box-shadow:0 24px 70px rgba(15,23,42,.24) }
.floating-agent-panel > header { display:grid; grid-template-columns:38px minmax(0,1fr) 34px; align-items:center; gap:10px; padding:15px 16px; border-bottom:1px solid var(--line) }
.floating-agent-panel > header > div { min-width:0 }.floating-agent-panel > header b,.floating-agent-panel > header small { display:block; overflow:hidden; text-overflow:ellipsis; white-space:nowrap }.floating-agent-panel > header b { font-size:14px }.floating-agent-panel > header small { margin-top:2px; color:var(--muted); font-size:10px }
.floating-agent-panel > header button { display:grid; width:34px; height:34px; place-items:center; padding:0; border:0; border-radius:10px; background:transparent; color:var(--muted) }.floating-agent-panel > header button:hover { background:var(--paper); color:var(--ink) }.floating-agent-panel > header button svg { width:17px }
.floating-agent-mark { display:grid; width:38px; height:38px; place-items:center; border-radius:12px; background:var(--accent-soft); color:var(--accent) }.floating-agent-mark svg { width:18px }.floating-agent-mark.small { flex:0 0 27px; width:27px; height:27px; border-radius:9px }.floating-agent-mark.small svg { width:14px }
.floating-agent-messages { min-height:0; overflow-y:auto; padding:16px; scroll-behavior:smooth }.floating-agent-empty { display:flex; height:100%; min-height:210px; flex-direction:column; align-items:center; justify-content:center; text-align:center }.floating-agent-empty > svg { width:28px; color:var(--accent) }.floating-agent-empty b { margin-top:10px; font-size:17px }.floating-agent-empty p { max-width:260px; margin:7px 0 0; color:var(--muted); font-size:12px; line-height:1.7 }
.floating-agent-messages article { display:flex; margin-bottom:14px }.floating-agent-messages article.user { justify-content:flex-end }.floating-agent-message { display:flex; max-width:92%; gap:8px }.floating-agent-message > div:last-child { min-width:0 }.floating-agent-message p { margin:0; white-space:pre-wrap }.floating-agent-messages article.user .floating-agent-message { padding:9px 12px; border-radius:15px 15px 4px 15px; background:var(--paper) }.floating-agent-message.failed { color:var(--down) }.floating-agent-message small { display:flex; align-items:center; gap:4px; margin-top:7px; color:var(--muted); font-size:10px }.floating-agent-message small svg { width:12px }
.floating-agent-markdown { overflow-wrap:anywhere; font-size:13px; line-height:1.7 }.floating-agent-markdown :deep(> :first-child) { margin-top:0 }.floating-agent-markdown :deep(> :last-child) { margin-bottom:0 }.floating-agent-markdown :deep(p),.floating-agent-markdown :deep(ul),.floating-agent-markdown :deep(ol),.floating-agent-markdown :deep(pre),.floating-agent-markdown :deep(blockquote) { margin:0 0 9px }.floating-agent-markdown :deep(ul),.floating-agent-markdown :deep(ol) { padding-left:19px }.floating-agent-markdown :deep(pre) { overflow-x:auto; padding:9px; border-radius:8px; background:var(--paper) }.floating-agent-markdown :deep(code) { font-family:ui-monospace,SFMono-Regular,Menlo,monospace }
.floating-agent-handoff { display:flex; align-items:center; justify-content:space-between; gap:10px; margin:0 14px 10px; padding:9px 10px; border:1px solid color-mix(in srgb,var(--accent) 34%,var(--line)); border-radius:10px; background:var(--accent-soft); color:var(--ink2); font-size:11px }.floating-agent-handoff span { display:flex; align-items:center; gap:5px }.floating-agent-handoff svg { width:14px }.floating-agent-handoff button { flex:0 0 auto; padding:6px 8px; border:0; border-radius:7px; background:var(--accent); color:#fff; font-size:10px; font-weight:700 }
.floating-agent-composer { margin:0 12px 12px; padding:8px 9px; border:1px solid var(--line2); border-radius:17px; background:var(--card); box-shadow:0 8px 24px rgba(15,23,42,.08) }.floating-agent-composer textarea { display:block; width:100%; min-height:42px; max-height:132px; resize:none; padding:6px 7px 8px; border:0; outline:0; background:transparent; color:var(--ink); font:inherit; line-height:1.5 }.floating-agent-composer textarea::placeholder { color:var(--muted) }.floating-agent-composer > div { display:flex; align-items:center; justify-content:space-between; padding-top:6px; border-top:1px solid var(--line) }.floating-agent-composer button { display:flex; align-items:center; justify-content:center; gap:5px; border:0 }.floating-agent-expand { padding:6px 7px; border-radius:7px!important; background:transparent; color:var(--muted); font-size:10px }.floating-agent-expand:hover:not(:disabled) { background:var(--paper); color:var(--ink) }.floating-agent-expand:disabled { opacity:.45 }.floating-agent-expand svg { width:13px }.floating-agent-send { width:34px; height:34px; border-radius:50%; background:var(--accent); color:#fff }.floating-agent-send:disabled { opacity:.4; cursor:not-allowed }.floating-agent-send svg { width:17px }
.spinning { animation:floating-agent-spin .8s linear infinite }.floating-agent-panel-enter-active,.floating-agent-panel-leave-active { transform-origin:right bottom; transition:opacity .18s ease,transform .2s cubic-bezier(.22,1,.36,1) }.floating-agent-panel-enter-from,.floating-agent-panel-leave-to { opacity:0; transform:translateY(12px) scale(.96) } @keyframes floating-agent-spin { to { transform:rotate(360deg) } }
@media (max-width:767px) { .floating-agent { right:14px; bottom:76px }.floating-agent-panel { width:calc(100vw - 28px); height:min(560px,calc(100dvh - 170px)); border-radius:19px }.floating-agent-trigger { min-width:52px; height:52px; padding:0 14px }.floating-agent.open .floating-agent-trigger { width:44px; min-width:44px; height:44px } }
@media (prefers-reduced-motion:reduce) { .floating-agent-panel-enter-active,.floating-agent-panel-leave-active,.floating-agent-trigger { transition:none }.spinning { animation:none } }
</style>
