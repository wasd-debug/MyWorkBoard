<template>
  <main class="ai-chat-home" :class="{ 'sidebar-collapsed': !sidebarOpen }">
    <button v-if="sidebarOpen" class="chat-sidebar-backdrop" type="button" aria-label="关闭会话侧栏" @click="sidebarOpen = false"></button>
    <aside class="chat-sidebar" aria-label="历史会话">
      <div class="chat-sidebar-head">
        <strong>历史会话</strong>
        <button class="chat-icon-button" type="button" title="收起侧栏" aria-label="收起侧栏" @click="sidebarOpen = false"><PanelLeftClose /></button>
      </div>
      <div class="conversation-create-actions">
        <button class="new-chat-button" type="button" @click="newConversation"><SquarePen /><span>新对话</span></button>
        <button type="button" title="新建分组" aria-label="新建会话分组" @click="showGroupDialog('create')"><FolderPlus /></button>
      </div>
      <nav class="conversation-list" aria-label="会话列表">
        <p v-if="!conversations.length" class="conversation-empty">还没有历史会话</p>
        <section
          v-for="section in conversationSections"
          :key="section.id"
          class="conversation-group"
          :class="{ 'conversation-drop-target': conversationDropTarget === section.id }"
          :data-group-id="section.id"
          @dragover.prevent="handleConversationDragOver($event, section)"
          @dragleave="handleConversationDragLeave($event, section)"
          @drop.prevent="dropConversation(section)"
        >
          <header>
            <button type="button" :aria-label="`${section.name}${collapsedGroups[section.id] ? '展开' : '收起'}`" :aria-expanded="!collapsedGroups[section.id]" @click="toggleGroup(section.id)"><ChevronDown :class="{ collapsed: collapsedGroups[section.id] }" />{{ section.name }}<small>{{ section.items.length }}</small></button>
            <button v-if="section.group" class="conversation-group-actions" type="button" :aria-label="`${section.name}分组操作`" @click="openGroupMenu($event, section.group)"><Ellipsis /></button>
          </header>
          <Transition name="conversation-group-collapse">
            <div v-if="!collapsedGroups[section.id]" class="conversation-group-body">
              <div
                v-for="item in section.items"
                :key="item.id"
                :class="{ active: item.id === activeId, dragging: draggedConversationId === item.id }"
                draggable="true"
                :data-session-id="item.id"
                @dragstart="startConversationDrag($event, item)"
                @dragend="endConversationDrag"
                @contextmenu.prevent="openConversationMenu($event, item)"
              >
                <button class="conversation-open" type="button" :aria-current="item.id === activeId ? 'page' : undefined" @click="openConversation(item.id)">
                  <Pin v-if="item.pinnedAt" aria-hidden="true" />
                  <MessageSquareText v-else aria-hidden="true" />
                  <span><b>{{ item.title }}</b><small>{{ formatTime(item.updatedAt) }}</small></span>
                </button>
                <button type="button" title="更多会话操作" :aria-label="`${item.title}的更多操作`" aria-haspopup="menu" :aria-expanded="conversationMenu.item?.id === item.id" @click.stop="openConversationMenu($event, item, true)"><Ellipsis /></button>
              </div>
              <p v-if="!section.items.length" class="conversation-group-empty">拖入会话</p>
            </div>
          </Transition>
        </section>
      </nav>
      <div class="chat-sidebar-foot"><span class="status-dot" :class="{ online: store.dbMode }"></span>{{ store.dbMode ? '数据已同步' : '本地会话' }}</div>
    </aside>

    <div v-if="conversationMenu.item" ref="conversationMenuEl" class="conversation-context-menu" :style="conversationMenuStyle" role="menu" :aria-label="`${conversationMenu.item.title}的会话操作`" @keydown="handleConversationMenuKeydown">
      <button type="button" role="menuitem" @click="togglePinned(conversationMenu.item)"><PinOff v-if="conversationMenu.item.pinnedAt" /><Pin v-else /><span>{{ conversationMenu.item.pinnedAt ? '取消置顶' : '置顶' }}</span></button>
      <button type="button" role="menuitem" @click="showRenameDialog(conversationMenu.item)"><Pencil /><span>重命名</span></button>
      <details class="conversation-move-menu">
        <summary><FolderInput /><span>移动到分组</span><ChevronRight /></summary>
        <div>
          <button type="button" role="menuitem" @click="moveConversation(conversationMenu.item, null)"><Inbox /><span>未分组</span></button>
          <button v-for="group in sessionGroups" :key="group.id" type="button" role="menuitem" @click="moveConversation(conversationMenu.item, group.id)"><Folder /><span>{{ group.name }}</span></button>
        </div>
      </details>
      <button type="button" role="menuitem" @click="archiveConversation(conversationMenu.item)"><Archive /><span>归档</span></button>
      <hr />
      <button class="danger" type="button" role="menuitem" @click="showDeleteDialog(conversationMenu.item)"><Trash2 /><span>删除</span></button>
    </div>

    <div v-if="groupMenu.item" ref="groupMenuEl" class="conversation-context-menu" :style="groupMenuStyle" role="menu" :aria-label="`${groupMenu.item.name}分组操作菜单`">
      <button type="button" role="menuitem" @click="showGroupDialog('rename', groupMenu.item)"><Pencil /><span>重命名分组</span></button>
      <button class="danger" type="button" role="menuitem" @click="deleteSessionGroup(groupMenu.item)"><Trash2 /><span>删除分组</span></button>
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
        <template v-else-if="conversationDialog.type === 'delete'">
          <header><h2 id="conversation-delete-title">删除会话？</h2><button type="button" aria-label="关闭" @click="closeConversationDialog"><X /></button></header>
          <p>“{{ conversationDialog.item?.title }}”将被永久删除，此操作无法撤销。</p>
          <footer><button type="button" @click="closeConversationDialog">取消</button><button class="danger" type="button" @click="deleteConversation(conversationDialog.item?.id)">删除</button></footer>
        </template>
        <template v-else>
          <header><h2 :id="`conversation-${conversationDialog.type}-title`">{{ conversationDialog.type === 'group-create' ? '新建分组' : '重命名分组' }}</h2><button type="button" aria-label="关闭" @click="closeConversationDialog"><X /></button></header>
          <form @submit.prevent="saveSessionGroup">
            <label for="conversation-group-name">分组名称</label>
            <input id="conversation-group-name" ref="groupNameInput" v-model="groupName" maxlength="80" autocomplete="off" />
            <footer><button type="button" @click="closeConversationDialog">取消</button><button class="primary" type="submit" :disabled="!groupName.trim()">保存</button></footer>
          </form>
        </template>
      </section>
    </div>

    <section class="chat-workspace">
      <header class="chat-workspace-head">
        <button v-if="!sidebarOpen" class="chat-icon-button" type="button" title="展开历史会话" aria-label="展开历史会话" @click="sidebarOpen = true"><PanelLeftOpen /></button>
        <div><b>{{ activeConversation?.title || '新对话' }}</b><small>AI 个人工作台</small></div>
        <select v-if="store.authUser && !store.offlineSession" class="chat-model-select" :value="activeConversation?.modelConnectionId || defaultModelId" aria-label="当前会话模型" :disabled="turnRunning || activeQueuedPrompts.length > 0" @change="changeConversationModel">
          <option v-for="item in modelConnections" :key="item.id" :value="item.id">{{ item.displayName }}{{ item.isDefault ? ' · 默认' : '' }}</option>
        </select>
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
          <article v-for="item in activeMessages" :key="item.id" class="chat-message" :class="[item.role, { 'message-failed': ['FAILED', 'CANCELLED'].includes(item.status) }]">
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
                <span v-if="item.role === 'assistant' && item.modelName" class="message-model"><Sparkles />{{ item.modelName }}</span>
                <span v-if="item.role === 'assistant' && item.estimatedCost != null" class="message-cost">≈ {{ formatCost(item.estimatedCost, item.currency) }}</span>
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
                <button v-if="item.role === 'assistant' && ['FAILED', 'CANCELLED'].includes(item.status) && item.turnId" type="button" aria-label="重试这条消息" @click="retryTurn(item)"><RefreshCw />重试</button>
              </div>
            </div>
          </article>
        </div>
      </div>

      <button v-if="activeMessages.length && !autoFollow" class="scroll-to-bottom" type="button" title="滚动到底部并继续跟随" aria-label="滚动到底部并继续跟随" @click="scrollToBottom(true)"><ArrowDown /></button>

      <div class="chat-composer-wrap">
        <section v-if="activeQueuedPrompts.length" class="prompt-queue" aria-label="消息处理队列">
          <header><span><ListOrdered />队列 {{ activeQueuedPrompts.length }} 条</span><button v-if="activeQueuedPrompts.some(item => item.status === 'QUEUED')" type="button" @click="clearPromptQueue(activeId)">清空等待项</button></header>
          <ol>
            <li v-for="(item, index) in activeQueuedPrompts" :key="item.id" :class="{ dragging: draggedQueueId === item.id, 'drag-over': queueDropIndex === index, running: item.status !== 'QUEUED' }" :draggable="item.status === 'QUEUED'" @dragstart="startQueueDrag($event, item)" @dragover.prevent="item.status === 'QUEUED' && (queueDropIndex = index)" @drop.prevent="dropQueuedPrompt(index)" @dragend="endQueueDrag">
              <GripVertical v-if="item.status === 'QUEUED'" aria-label="拖拽排序" /><span v-else class="queue-running-dot" aria-hidden="true"></span><span>{{ item.status === 'QUEUED' ? index + 1 : '执行' }}</span><p><b>{{ queueStatusLabel(item.status) }}</b>{{ item.text }}</p><button type="button" :aria-label="item.status === 'QUEUED' ? `移除队列消息：${item.text}` : '停止生成'" @click="item.status === 'QUEUED' ? removeQueuedPrompt(item.id) : cancelTurn(item.id)"><X /></button>
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
import { Archive, ArrowDown, ArrowUp, ArrowUpRight, Check, ChevronDown, ChevronRight, Clock3, Copy, Database, Ellipsis, Folder, FolderInput, FolderPlus, GripVertical, Image as ImageIcon, Inbox, ListOrdered, MessageSquareText, Mic, PanelLeftClose, PanelLeftOpen, Paperclip, Pencil, Pin, PinOff, RefreshCw, Sparkles, Square, SquarePen, TimerReset, Trash2, Wrench, X } from 'lucide-vue-next'
import { Calendar, List, Timer, Wallet } from '../icons.js'
import { message } from '../services/message.js'
import { useAppStore } from '../stores/app'
import { apiArchiveAgentSession, apiCancelAgentTurn, apiChangeAgentSessionModel, apiChatWithAssistant, apiCreateAgentSession, apiCreateAgentSessionGroup, apiDeleteAgentSession, apiDeleteAgentSessionGroup, apiEnqueueAgentTurn, apiGetAgentTrace, apiGetAgentTurn, apiListAgentMessages, apiListAgentModelConnections, apiListAgentQueue, apiListAgentSessionGroups, apiListAgentSessions, apiMoveAgentSession, apiRemoveQueuedAgentTurn, apiRenameAgentSessionGroup, apiReorderAgentQueue, apiRetryAgentTurn, apiStreamAgentTurn, apiUpdateAgentSession } from '../../packages/api-client/src/index.js'

const router = useRouter(), store = useAppStore()
const sidebarOpen = ref(true), conversations = ref([]), activeId = ref(''), prompt = ref(''), attachments = ref([])
const fileInput = ref(null), imageInput = ref(null), messageViewport = ref(null), recording = ref(false), autoFollow = ref(true), copiedId = ref('')
const conversationMenu = ref({ item: null, x: 0, y: 0 }), conversationMenuEl = ref(null)
const groupMenu = ref({ item: null, x: 0, y: 0 }), groupMenuEl = ref(null)
const conversationDialog = ref({ type: '', item: null }), renameTitle = ref(''), renameInput = ref(null)
const sessionGroups = ref([]), collapsedGroups = ref({}), groupName = ref(''), groupNameInput = ref(null)
const queuedPrompts = ref([]), queueRevisions = ref({}), turnRunning = ref(false), draggedQueueId = ref(''), queueDropIndex = ref(-1)
const modelConnections = ref([])
const draggedConversationId = ref(''), conversationDropTarget = ref('')
let mediaRecorder = null, mediaStream = null, recordingStartedAt = 0, activeTurnController = null, conversationCreationPromise = null, queuePollTimer = null, lastMessageScrollTop = 0, programmaticScroll = false, userPausedFollow = false
const queuePresence = new Map()
const historyKey = computed(() => `workspace_ai_conversations_v1:${store.accountScope || 'local'}`)
const displayName = computed(() => store.authUser?.nickname || store.authUser?.username || '建胜')
const activeConversation = computed(() => conversations.value.find(item => item.id === activeId.value) || null)
const activeMessages = computed(() => activeConversation.value?.messages || [])
const canSubmit = computed(() => Boolean(prompt.value.trim() || attachments.value.length))
const activeQueuedPrompts = computed(() => queuedPrompts.value.filter(item => item.conversationId === activeId.value))
const defaultModelId = computed(() => modelConnections.value.find(item => item.isDefault)?.id || modelConnections.value[0]?.id || '')
const conversationMenuStyle = computed(() => ({ left: `${conversationMenu.value.x}px`, top: `${conversationMenu.value.y}px` }))
const groupMenuStyle = computed(() => ({ left: `${groupMenu.value.x}px`, top: `${groupMenu.value.y}px` }))
const conversationSections = computed(() => {
  const pinned = conversations.value.filter(item => item.pinnedAt)
  const normal = conversations.value.filter(item => !item.pinnedAt)
  const sections = []
  if (pinned.length) sections.push({ id: 'pinned', name: '置顶', items: pinned, group: null })
  for (const group of sessionGroups.value) sections.push({ id: group.id, name: group.name, items: normal.filter(item => item.groupId === group.id), group })
  const ungrouped = normal.filter(item => !item.groupId || !sessionGroups.value.some(group => group.id === item.groupId))
  sections.push({ id: 'ungrouped', name: '未分组', items: ungrouped, group: null })
  return sections
})
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
async function loadMessages(id, forceScroll = false) { const target = conversations.value.find(item => item.id === id); if (!target) return; const rows = await apiListAgentMessages(id); target.messages = (rows || []).map(row => { const metadata = row.role === 'assistant' ? parseMetadata(row.metadataJson) : {}; return { id: `server-${row.id}`, turnId: row.turnId, role: row.role, content: row.content, createdAt: row.createdAt, typing: false, status: metadata.status || 'COMPLETED', ...responseFields(metadata), ...inferRoute(row.content) } }); persist(); if (forceScroll) await scrollToBottom(true); else await scrollToBottom() }
function normalizeQueueItem(item) { return { id: item.id, conversationId: item.sessionId, text: item.userMessage, status: item.status, createdAt: item.createdAt, retryOfTurnId: item.retryOfTurnId } }
async function loadQueue(id) { if (!id || !store.authUser || store.offlineSession) return; const queue = await apiListAgentQueue(id); queueRevisions.value = { ...queueRevisions.value, [id]: Number(queue?.revision || 0) }; const others = queuedPrompts.value.filter(item => item.conversationId !== id); queuedPrompts.value = [...others, ...(queue?.items || []).map(normalizeQueueItem)]; if (id === activeId.value) turnRunning.value = activeQueuedPrompts.value.some(item => item.status !== 'QUEUED') }
async function loadConversations() { if (!store.authUser || store.offlineSession) return loadLocalConversations(); try { const [sessions, groups, models] = await Promise.all([apiListAgentSessions(), apiListAgentSessionGroups(), apiListAgentModelConnections()]); conversations.value = (sessions || []).map(item => ({ ...item, messages: [] })); sessionGroups.value = groups || []; modelConnections.value = models || []; activeId.value = conversations.value[0]?.id || ''; if (activeId.value) await Promise.all([loadMessages(activeId.value, true), loadQueue(activeId.value)]) } catch { loadLocalConversations() } }
async function changeConversationModel(event) { const id = event.target.value; if (!activeConversation.value || !id) return; try { const updated = await apiChangeAgentSessionModel(activeConversation.value.id, id); Object.assign(activeConversation.value, updated); message.success('当前会话模型已切换') } catch (error) { event.target.value = activeConversation.value.modelConnectionId || defaultModelId.value; message.error(error?.response?.data?.detail || '切换模型失败') } }
function closeConversationMenu() { conversationMenu.value = { item: null, x: 0, y: 0 } }
function closeGroupMenu() { groupMenu.value = { item: null, x: 0, y: 0 } }
async function openConversationMenu(event, item, fromButton = false) {
  const width = 190, height = 146, margin = 8
  const anchor = fromButton ? event.currentTarget.getBoundingClientRect() : null
  const rawX = anchor ? anchor.right - width : event.clientX
  const rawY = anchor ? anchor.bottom + 6 : event.clientY
  conversationMenu.value = { item, x: Math.max(margin, Math.min(rawX, window.innerWidth - width - margin)), y: Math.max(margin, Math.min(rawY, window.innerHeight - height - margin)) }
  await nextTick()
  conversationMenuEl.value?.querySelector('button')?.focus()
}
function openGroupMenu(event, item) { closeConversationMenu(); const rect = event.currentTarget.getBoundingClientRect(); groupMenu.value = { item, x: Math.max(8, Math.min(rect.right - 190, window.innerWidth - 198)), y: Math.max(8, Math.min(rect.bottom + 6, window.innerHeight - 112)) } }
function toggleGroup(id) { collapsedGroups.value = { ...collapsedGroups.value, [id]: !collapsedGroups.value[id] } }
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
function closeConversationDialog() { conversationDialog.value = { type: '', item: null }; renameTitle.value = ''; groupName.value = '' }
async function showRenameDialog(item) { closeConversationMenu(); conversationDialog.value = { type: 'rename', item }; renameTitle.value = item.title; await nextTick(); renameInput.value?.focus(); renameInput.value?.select() }
function showDeleteDialog(item) { closeConversationMenu(); conversationDialog.value = { type: 'delete', item } }
async function showGroupDialog(mode, item = null) { closeGroupMenu(); conversationDialog.value = { type: mode === 'create' ? 'group-create' : 'group-rename', item }; groupName.value = item?.name || ''; await nextTick(); groupNameInput.value?.focus(); groupNameInput.value?.select() }
function removeConversationFromList(id) { conversations.value = conversations.value.filter(item => item.id !== id); if (activeId.value === id) activeId.value = conversations.value[0]?.id || ''; persist() }
function newConversation() { closeConversationMenu(); activeId.value = ''; prompt.value = ''; attachments.value = []; autoFollow.value = true; if (window.innerWidth < 900) sidebarOpen.value = false }
async function openConversation(id) { closeConversationMenu(); activeId.value = id; autoFollow.value = true; const target = activeConversation.value; if (store.authUser && !store.offlineSession && target) try { await Promise.all([target.messages?.length ? Promise.resolve() : loadMessages(id), loadQueue(id)]) } catch { message.error('会话状态加载失败') }; if (window.innerWidth < 900) sidebarOpen.value = false; scrollToBottom(true) }
async function archiveConversation(item) { closeConversationMenu(); if (!store.authUser || store.offlineSession) return message.warning('本地会话暂不支持归档'); try { await apiArchiveAgentSession(item.id); removeConversationFromList(item.id); message.success('会话已归档') } catch { message.error('归档会话失败') } }
async function deleteConversation(id) { if (!id) return; if (store.authUser && !store.offlineSession) try { await apiDeleteAgentSession(id) } catch { return message.error('删除会话失败') }; removeConversationFromList(id); closeConversationDialog(); message.success('会话已删除') }
async function renameConversation() { const item = conversationDialog.value.item, title = renameTitle.value.trim(); if (!item || !title || title === item.title) return closeConversationDialog(); try { const updated = store.authUser && !store.offlineSession ? await apiUpdateAgentSession(item.id, { title }) : { title }; item.title = updated?.title || title; item.updatedAt = updated?.updatedAt || item.updatedAt; persist(); closeConversationDialog(); message.success('会话已重命名') } catch { message.error('修改标题失败') } }
async function togglePinned(item) { closeConversationMenu(); try { const updated = await apiUpdateAgentSession(item.id, { pinned: !item.pinnedAt }); Object.assign(item, updated); message.success(item.pinnedAt ? '会话已置顶' : '已取消置顶') } catch { message.error('更新置顶状态失败') } }
async function moveConversation(item, groupId) { closeConversationMenu(); try { const updated = await apiMoveAgentSession(item.id, groupId); Object.assign(item, updated); message.success(groupId ? '会话已移动' : '会话已移至未分组') } catch { message.error('移动会话失败') } }
function startConversationDrag(event, item) { draggedConversationId.value = item.id; event.dataTransfer.effectAllowed = 'move'; event.dataTransfer.setData('text/plain', item.id) }
function handleConversationDragOver(event, section) { if (!draggedConversationId.value || section.id === 'pinned') return; event.dataTransfer.dropEffect = 'move'; conversationDropTarget.value = section.id }
function handleConversationDragLeave(event, section) { if (!event.currentTarget.contains(event.relatedTarget) && conversationDropTarget.value === section.id) conversationDropTarget.value = '' }
async function dropConversation(section) {
  const item = conversations.value.find(session => session.id === draggedConversationId.value)
  const groupId = section.id === 'ungrouped' ? null : section.group?.id
  if (!item || section.id === 'pinned' || item.groupId === groupId) return endConversationDrag()
  try {
    const updated = await apiMoveAgentSession(item.id, groupId)
    Object.assign(item, updated)
    if (item.pinnedAt) Object.assign(item, await apiUpdateAgentSession(item.id, { pinned: false }))
    message.success(groupId ? `已移入“${section.name}”` : '已移出分组')
  }
  catch { message.error('移动会话失败') }
  finally { endConversationDrag() }
}
function endConversationDrag() { draggedConversationId.value = ''; conversationDropTarget.value = '' }
async function saveSessionGroup() { const name = groupName.value.trim(), item = conversationDialog.value.item; if (!name) return; try { if (conversationDialog.value.type === 'group-create') sessionGroups.value.push(await apiCreateAgentSessionGroup({ id: uid(), name })); else Object.assign(item, await apiRenameAgentSessionGroup(item.id, { name })); closeConversationDialog(); message.success('分组已保存') } catch { message.error('保存分组失败') } }
async function deleteSessionGroup(item) { closeGroupMenu(); if (!window.confirm(`删除分组“${item.name}”？其中的会话会移到未分组。`)) return; try { await apiDeleteAgentSessionGroup(item.id); conversations.value.forEach(session => { if (session.groupId === item.id) session.groupId = null }); sessionGroups.value = sessionGroups.value.filter(group => group.id !== item.id); message.success('分组已删除') } catch { message.error('删除分组失败') } }
function formatTime(value) { return new Intl.DateTimeFormat('zh-CN', { month: 'numeric', day: 'numeric', hour: '2-digit', minute: '2-digit' }).format(new Date(value)) }
function formatMessageTime(value) { return new Intl.DateTimeFormat('zh-CN', { hour: '2-digit', minute: '2-digit' }).format(new Date(value)) }
function formatNumber(value) { return new Intl.NumberFormat('zh-CN').format(Number(value || 0)) }
function formatDuration(value) { const ms = Math.max(0, Number(value || 0)); if (ms < 1000) return `${ms}ms`; if (ms < 60_000) return `${(ms / 1000).toFixed(ms < 10_000 ? 1 : 0)}s`; return `${Math.floor(ms / 60_000)}m ${Math.round((ms % 60_000) / 1000)}s` }
function formatCost(value, currency = 'CNY') { const amount = Number(value || 0); return new Intl.NumberFormat('zh-CN', { style: 'currency', currency: currency || 'CNY', minimumFractionDigits: amount < 0.01 ? 6 : 2, maximumFractionDigits: 8 }).format(amount) }
function replyTokens(usage) { return Math.max(0, Number(usage?.outputTokens || 0) - Number(usage?.reasoningTokens || 0)) }
function cacheHitRate(usage) { const hit = Number(usage?.cacheHitTokens || 0), miss = Number(usage?.cacheMissTokens || 0); return hit + miss ? Math.round(hit / (hit + miss) * 100) : 0 }
function toolLabel(name) { return ({ 'ledger.books.list': '查询账本', 'ledger.overview': '查询账本概览', 'ledger.transactions.search': '查询账本流水', 'ledger.reports.summary': '生成账本报表', 'ledger.budgets.list': '查询预算', 'worktime.settings.get': '读取工时设置', 'worktime.records.search': '查询工时记录' })[name] || name }
async function copyMessage(item) { try { await navigator.clipboard.writeText(item.content || '') } catch { const area = document.createElement('textarea'); area.value = item.content || ''; document.body.appendChild(area); area.select(); document.execCommand('copy'); area.remove() }; copiedId.value = item.id; window.setTimeout(() => { if (copiedId.value === item.id) copiedId.value = '' }, 1600) }
function handleMetaToggle(event) { const current = event.currentTarget; if (!current.open) return; current.closest('.message-meta')?.querySelectorAll('details[open]').forEach(detail => { if (detail !== current) detail.open = false }) }
function closeMetaPopovers(event) {
  document.querySelectorAll('.message-meta details[open]').forEach(detail => { if (!detail.contains(event.target)) detail.open = false })
  const insideMenu = event.target.closest?.('.conversation-context-menu')
  const menuTrigger = event.target.closest?.('[aria-haspopup="menu"]')
  if (conversationMenu.value.item && !insideMenu && !menuTrigger) closeConversationMenu()
  if (groupMenu.value.item && !insideMenu && !event.target.closest?.('[aria-label$="分组操作"]')) closeGroupMenu()
}
function openCard(card) { if (card.planned) { prompt.value = `打开${card.title}`; submitPrompt(); return }; router.push(card.route) }
function addFiles(event, type) { attachments.value.push(...Array.from(event.target.files || []).map(file => ({ id: uid(), type, name: file.name, size: file.size }))); event.target.value = '' }
function removeAttachment(id) { attachments.value = attachments.value.filter(item => item.id !== id) }
async function removeQueuedPrompt(id) { try { await apiRemoveQueuedAgentTurn(id); await loadQueue(activeId.value) } catch { message.error('移除排队消息失败') } }
async function clearPromptQueue(conversationId) { const queued = queuedPrompts.value.filter(item => item.conversationId === conversationId && item.status === 'QUEUED'); await Promise.all(queued.map(item => apiRemoveQueuedAgentTurn(item.id).catch(() => null))); await loadQueue(conversationId) }
function startQueueDrag(event, item) { if (item.status !== 'QUEUED') return; draggedQueueId.value = item.id; event.dataTransfer.effectAllowed = 'move'; event.dataTransfer.setData('text/plain', item.id) }
async function dropQueuedPrompt(targetIndex) {
  const visible = activeQueuedPrompts.value.filter(item => item.status === 'QUEUED'), dragged = visible.find(item => item.id === draggedQueueId.value)
  if (!dragged) return endQueueDrag()
  const reordered = visible.filter(item => item.id !== dragged.id)
  const runningOffset = activeQueuedPrompts.value.filter(item => item.status !== 'QUEUED').length
  reordered.splice(Math.max(0, Math.min(targetIndex - runningOffset, reordered.length)), 0, dragged)
  const previous = activeQueuedPrompts.value
  queuedPrompts.value = [...queuedPrompts.value.filter(item => item.conversationId !== activeId.value), ...activeQueuedPrompts.value.filter(item => item.status !== 'QUEUED'), ...reordered]
  endQueueDrag()
  try { const result = await apiReorderAgentQueue(activeId.value, { revision: queueRevisions.value[activeId.value] || 0, turnIds: reordered.map(item => item.id) }); queueRevisions.value = { ...queueRevisions.value, [activeId.value]: result.revision }; await loadQueue(activeId.value) } catch { queuedPrompts.value = [...queuedPrompts.value.filter(item => item.conversationId !== activeId.value), ...previous]; message.warning('队列已变化，已恢复服务端最新顺序'); await loadQueue(activeId.value).catch(() => {}) }
}
function endQueueDrag() { draggedQueueId.value = ''; queueDropIndex.value = -1 }
function queueStatusLabel(status) { return status === 'QUEUED' ? '排队中 · ' : status === 'RECEIVED' ? '正在启动 · ' : '正在处理 · ' }
async function cancelTurn(turnId) { try { await apiCancelAgentTurn(turnId); activeTurnController?.abort(); await loadQueue(activeId.value); const target = activeMessages.value.find(item => item.turnId === turnId); if (target) Object.assign(target, { status: 'CANCELLED', typing: false, content: target.content || '本轮对话已取消。' }); message.success('已停止生成') } catch { message.error('停止生成失败') } }
async function retryTurn(item) { try { const turn = await apiRetryAgentTurn(item.turnId, uid()); queuedPrompts.value = [...queuedPrompts.value, normalizeQueueItem(turn)]; item.retryTurnId = turn.id; message.success('已加入重试队列'); await loadQueue(activeId.value) } catch (error) { message.error(error?.response?.data?.detail || '重试失败') } }
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
async function hydrateTrace(target) { if (!target?.turnId || !store.authUser || store.offlineSession) return; try { const trace = await apiGetAgentTrace(target.turnId); const usage = (trace.modelExecutions || []).reduce((sum, row) => ({ inputTokens: sum.inputTokens + Number(row.inputTokens || 0), outputTokens: sum.outputTokens + Number(row.outputTokens || 0), cacheHitTokens: sum.cacheHitTokens + Number(row.cacheHitTokens || 0), cacheMissTokens: sum.cacheMissTokens + Number(row.cacheMissTokens || 0), reasoningTokens: sum.reasoningTokens + Number(row.reasoningTokens || 0), totalTokens: sum.totalTokens + Number(row.totalTokens || 0) }), { inputTokens: 0, outputTokens: 0, cacheHitTokens: 0, cacheMissTokens: 0, reasoningTokens: 0, totalTokens: 0 }); Object.assign(target, { usage, durationMs: trace.totalDurationMs, firstTokenMs: trace.firstTokenMs, modelName: trace.modelName, providerType: trace.providerType, currency: trace.currency, estimatedCost: trace.estimatedCost, modelExecutions: trace.modelExecutions || [], toolExecutions: trace.toolExecutions || [] }); persist() } catch { /* Trace is optional and must never hide the answer. */ } }
function applyReply(target, response) { Object.assign(target, responseFields(response), inferRoute(response.content || ''), { content: response.content || '模型没有返回可显示的内容，请稍后重试。', typing: false, status: 'COMPLETED' }); persist(); hydrateTrace(target); scrollToBottom() }
async function recoverTurn(target) { for (let attempt = 0; attempt < 4; attempt++) { try { const turn = await apiGetAgentTurn(target.turnId); target.status = turn.status; if (turn.status === 'COMPLETED' && turn.responseJson) { applyReply(target, parseMetadata(turn.responseJson)); return true }; if (['FAILED', 'CANCELLED'].includes(turn.status)) { target.content = turn.errorMessage || (turn.status === 'CANCELLED' ? '本轮对话已取消。' : 'Agent 执行失败。'); target.typing = false; persist(); return true } } catch { /* bounded recovery */ }; await new Promise(resolve => window.setTimeout(resolve, 500 * (attempt + 1))) }; target.content ||= '响应连接已中断，后台仍可能在执行。刷新页面后可恢复已完成消息。'; target.typing = false; persist(); return false }
async function ensureConversation(text, pendingAttachments) {
  let conversation = activeConversation.value
  if (conversation) return conversation
  if (conversationCreationPromise) return conversationCreationPromise
  conversationCreationPromise = (async () => {
    const now = Date.now(), draft = { id: uid(), title: text.slice(0, 24) || pendingAttachments[0]?.name || '新对话', updatedAt: now, messages: [] }
    if (store.authUser && !store.offlineSession) try { conversation = { ...(await apiCreateAgentSession({ id: draft.id, title: draft.title, modelConnectionId: defaultModelId.value || null })), messages: [] } } catch { message.error('无法创建服务端会话'); return null }
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
  if (turnRunning.value || activeQueuedPrompts.value.length) {
    try {
      const turn = await apiEnqueueAgentTurn(conversation.id, { clientRequestId: pending.id, message: text })
      queuedPrompts.value = [...queuedPrompts.value, normalizeQueueItem(turn)]
      await loadQueue(conversation.id)
      message.success('消息已加入服务端队列')
    } catch { message.error('消息入队失败，请重试') }
    return
  }
  await executePrompt(pending)
}
async function executePrompt(pending) {
  const conversation = conversations.value.find(item => item.id === pending.conversationId)
  if (!conversation) return
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
      else if (event === 'turn.cancelled') Object.assign(assistant, { content: assistant.content || '本轮对话已取消。', typing: false, status: 'CANCELLED' })
      else if (event === 'turn.failed') throw new Error(data.message || 'Agent 执行失败')
    }, activeTurnController.signal)
    if (assistant.typing && assistant.turnId) await recoverTurn(assistant)
  } catch (error) {
    if (!received) try { applyReply(assistant, await apiChatWithAssistant(text, conversation.id)); return } catch (fallback) { error = fallback }
    else if (assistant.turnId && await recoverTurn(assistant)) return
    assistant.content = error?.response?.data?.detail || error?.message || '暂时无法连接 AI 服务，请稍后重试。'; assistant.typing = false; assistant.status = 'FAILED'; persist()
  } finally {
    activeTurnController = null
    await loadQueue(conversation.id).catch(() => {})
    turnRunning.value = queuedPrompts.value.some(item => item.status !== 'QUEUED')
    await loadMessages(conversation.id).catch(() => {})
  }
}
async function pollAgentState() {
  if (!activeId.value || !store.authUser || store.offlineSession) return
  const sessionId = activeId.value
  const before = activeQueuedPrompts.value.map(item => `${item.id}:${item.status}`).join('|')
  const hadItems = queuePresence.get(sessionId) || false
  try {
    await loadQueue(sessionId)
    if (activeId.value !== sessionId) return
    const after = activeQueuedPrompts.value.map(item => `${item.id}:${item.status}`).join('|')
    if ((before !== after || (hadItems && !after)) && !activeTurnController) await loadMessages(sessionId)
    queuePresence.set(sessionId, Boolean(after))
  } catch { /* next poll reconciles transient failures */ }
}
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

onMounted(async () => { await loadConversations(); sidebarOpen.value = window.innerWidth >= 900; document.addEventListener('pointerdown', closeMetaPopovers); queuePollTimer = window.setInterval(pollAgentState, 1500); scrollToBottom(true) })
onBeforeUnmount(() => { document.removeEventListener('pointerdown', closeMetaPopovers); window.clearInterval(queuePollTimer); activeTurnController?.abort(); if (mediaRecorder?.state === 'recording') mediaRecorder.stop(); mediaStream?.getTracks().forEach(track => track.stop()) })
</script>
