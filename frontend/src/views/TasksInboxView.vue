<template>
  <section class="tasks-page">
    <header class="tasks-head">
      <div>
        <p class="label">TASKS</p>
        <h1>收件箱</h1>
        <p class="tasks-summary">{{ store.openTasks.length }} 项待完成 · {{ store.completedTasks.length }} 项已完成</p>
      </div>
      <Button :disabled="writeDisabled" @click="openCreate"><Plus :size="16" />新建任务</Button>
    </header>

    <div v-if="!store.online || appStore.offlineSession || store.pending" class="tasks-warning" role="status">
      <WifiOff v-if="!store.online || appStore.offlineSession" :size="18" />
      <span v-if="!store.online || appStore.offlineSession">当前离线，变更已保存在本机。</span>
      <span v-else>{{ store.pending }} 项变更等待同步。</span>
    </div>
    <div v-if="store.conflicts.length" class="tasks-error" role="alert">
      任务“{{ issueTitle(store.conflicts[0]) }}”发生版本冲突，请选择保留版本。
      <Button size="sm" variant="ghost" @click="resolveConflict('server')">采用服务端</Button>
      <Button size="sm" variant="ghost" @click="resolveConflict('local')">保留本地</Button>
    </div>
    <div v-if="store.rejected.length" class="tasks-error" role="alert">
      任务“{{ issueTitle(store.rejected[0]) }}”未同步：{{ store.rejected[0].result?.message || '服务端拒绝了本次变更' }}
    </div>
    <div v-if="store.error" class="tasks-error" role="alert">{{ store.error }}</div>

    <div v-if="store.loading && !store.ready" class="tasks-empty">正在加载收件箱…</div>
    <template v-else>
      <div v-if="!store.tasks.length" class="tasks-empty">
        <Inbox :size="28" />
        <strong>收件箱是空的</strong>
      </div>

      <div v-else class="tasks-sections">
        <section class="task-group" aria-labelledby="open-task-heading">
          <h2 id="open-task-heading">待完成 <span>{{ store.openTasks.length }}</span></h2>
          <ul class="task-list">
            <li v-for="task in store.openTasks" :key="task.publicId" class="task-row">
              <button class="task-check" type="button" :disabled="writeDisabled" :aria-label="`完成${task.title}`" @click="complete(task)"><Circle :size="21" /></button>
              <button class="task-content" type="button" @click="openEdit(task)">
                <strong>{{ task.title }}</strong>
                <small v-if="task.description">{{ task.description }}</small>
                <span v-if="task.dueAt"><CalendarClock :size="13" />{{ formatTime(task.dueAt) }}</span>
              </button>
              <span v-if="task.priority !== 'NONE'" class="task-priority" :data-priority="task.priority">{{ priorityLabel(task.priority) }}</span>
            </li>
          </ul>
        </section>

        <section v-if="store.completedTasks.length" class="task-group completed" aria-labelledby="completed-task-heading">
          <h2 id="completed-task-heading">已完成 <span>{{ store.completedTasks.length }}</span></h2>
          <ul class="task-list">
            <li v-for="task in store.completedTasks" :key="task.publicId" class="task-row">
              <button class="task-check checked" type="button" :disabled="writeDisabled" :aria-label="`重新打开${task.title}`" @click="reopen(task)"><CheckCircle2 :size="21" /></button>
              <button class="task-content" type="button" @click="openEdit(task)"><strong>{{ task.title }}</strong></button>
              <Button variant="icon" size="sm" :disabled="writeDisabled" :aria-label="`删除${task.title}`" title="删除" @click="remove(task)"><Trash2 :size="15" /></Button>
            </li>
          </ul>
        </section>
      </div>
    </template>

    <Drawer v-model:open="editorOpen" :title="editing ? '编辑任务' : '新建任务'">
      <form id="task-editor" class="task-form" @submit.prevent="save">
        <label>标题<Input v-model="form.title" :disabled="writeDisabled" placeholder="要完成什么？" /></label>
        <label>描述<textarea v-model="form.description" class="ui-textarea" :disabled="writeDisabled" placeholder="补充必要信息"></textarea></label>
        <div class="task-form-grid">
          <label>优先级<select v-model="form.priority" :disabled="writeDisabled"><option value="NONE">无</option><option value="LOW">低</option><option value="MEDIUM">中</option><option value="HIGH">高</option></select></label>
          <label>截止时间<Input v-model="form.dueLocal" type="datetime-local" :disabled="writeDisabled" /></label>
        </div>
        <p v-if="editorError" class="task-form-error" role="alert">{{ editorError }}</p>
      </form>
      <template #footer>
        <Button v-if="editing" variant="danger" :disabled="writeDisabled || store.saving" @click="remove(editing)"><Trash2 :size="15" />删除</Button>
        <Button variant="ghost" @click="editorOpen = false">取消</Button>
        <Button type="submit" form="task-editor" :disabled="writeDisabled || store.saving || !form.title.trim()"><Save :size="15" />保存</Button>
      </template>
    </Drawer>
  </section>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { CalendarClock, CheckCircle2, Circle, Inbox, Plus, Save, Trash2, WifiOff } from 'lucide-vue-next'
import Button from '../components/ui/Button.vue'
import Drawer from '../components/ui/Drawer.vue'
import Input from '../components/ui/Input.vue'
import { useAppStore } from '../stores/app.js'
import { useTasksStore } from '../stores/tasks.js'

const store = useTasksStore()
const appStore = useAppStore()
const editorOpen = ref(false)
const editing = ref(null)
const editorError = ref('')
const form = reactive({ title: '', description: '', priority: 'NONE', dueLocal: '' })
const writeDisabled = computed(() => false)

function resetForm(task = null) {
  form.title = task?.title || ''
  form.description = task?.description || ''
  form.priority = task?.priority || 'NONE'
  form.dueLocal = task?.dueAt ? toLocalInput(task.dueAt) : ''
  editorError.value = ''
}
function openCreate() { editing.value = null; resetForm(); editorOpen.value = true }
function openEdit(task) { editing.value = task; resetForm(task); editorOpen.value = true }
function payload() { return { listId: editing.value?.listId || store.inbox?.publicId, title: form.title.trim(), description: form.description.trim(), priority: form.priority, dueAt: form.dueLocal ? new Date(form.dueLocal).toISOString() : null, allDay: false, timezone: Intl.DateTimeFormat().resolvedOptions().timeZone || 'Asia/Shanghai' } }
function detail(error, fallback) { return error?.response?.data?.detail || error?.message || fallback }
async function save() {
  editorError.value = ''
  try { editing.value ? await store.update(editing.value, payload()) : await store.create(payload()); editorOpen.value = false }
  catch (error) { editorError.value = detail(error, '任务保存失败') }
}
async function complete(task) { try { await store.complete(task) } catch (error) { store.error = detail(error, '完成任务失败') } }
async function reopen(task) { try { await store.reopen(task) } catch (error) { store.error = detail(error, '重新打开失败') } }
async function remove(task) { if (!window.confirm(`删除任务“${task.title}”？`)) return; try { await store.remove(task); if (editing.value?.publicId === task.publicId) editorOpen.value = false } catch (error) { editorError.value = detail(error, '删除任务失败') } }
async function resolveConflict(strategy) { try { await store.resolveConflict(store.conflicts[0].id, strategy) } catch (error) { store.error = detail(error, '冲突处理失败') } }
function priorityLabel(value) { return ({ LOW: '低', MEDIUM: '中', HIGH: '高' })[value] || '' }
function formatTime(value) { return new Intl.DateTimeFormat('zh-CN', { month: 'numeric', day: 'numeric', hour: '2-digit', minute: '2-digit' }).format(new Date(value)) }
function toLocalInput(value) { const date = new Date(value); const offset = date.getTimezoneOffset() * 60000; return new Date(date.getTime() - offset).toISOString().slice(0, 16) }
function issueTitle(issue) { return issue?.operation?.payload?.title || issue?.operation?.entityId || '未知任务' }

onMounted(() => store.init().catch(() => {}))
</script>

<style scoped>
.tasks-page{width:min(900px,100%);margin:0 auto;padding:36px 24px 100px}.tasks-head{display:flex;justify-content:space-between;align-items:flex-end;gap:24px;border-bottom:1px solid var(--line);padding-bottom:22px}.tasks-head h1{margin:2px 0 4px;font:700 34px Georgia,serif;letter-spacing:0}.tasks-summary{margin:0;color:var(--muted)}.tasks-warning,.tasks-error{display:flex;align-items:center;gap:9px;padding:12px 14px;margin-top:18px;border:1px solid #d6a13d;background:#fff8e8;color:#76520c;border-radius:4px}.tasks-error{border-color:rgba(180,35,24,.35);background:rgba(180,35,24,.05);color:var(--down)}.tasks-empty{min-height:280px;display:flex;flex-direction:column;align-items:center;justify-content:center;gap:12px;color:var(--muted)}.tasks-sections{display:grid;gap:32px;margin-top:28px}.task-group h2{margin:0 0 10px;font-size:14px;letter-spacing:0;font-weight:700}.task-group h2 span{color:var(--muted);font-weight:500;margin-left:4px}.task-list{list-style:none;margin:0;padding:0;border-top:1px solid var(--line)}.task-row{display:flex;align-items:center;gap:12px;min-height:62px;border-bottom:1px solid var(--line);padding:8px 4px}.task-check{border:0;background:transparent;padding:6px;color:var(--muted)}.task-check:not(:disabled):hover,.task-check.checked{color:var(--accent)}.task-content{min-width:0;flex:1;text-align:left;border:0;background:transparent;padding:6px}.task-content strong,.task-content small{display:block;overflow:hidden;text-overflow:ellipsis;white-space:nowrap}.task-content strong{font-size:14px;color:var(--ink)}.task-content small{color:var(--muted);margin-top:2px}.task-content span{display:flex;align-items:center;gap:4px;color:var(--muted);font-size:12px;margin-top:5px}.completed .task-content strong{text-decoration:line-through;color:var(--muted)}.task-priority{font-size:11px;border-left:2px solid var(--line2);padding-left:7px;color:var(--muted)}.task-priority[data-priority="HIGH"]{border-color:var(--down);color:var(--down)}.task-priority[data-priority="MEDIUM"]{border-color:var(--warn);color:var(--warn)}.task-form{display:grid;gap:18px}.task-form label{display:grid;gap:7px;font-size:12px;font-weight:600;color:var(--ink2)}.task-form-grid{display:grid;grid-template-columns:1fr 1.4fr;gap:14px}.task-form-error{margin:0;color:var(--down);font-size:13px}.ui-sheet-foot :first-child{margin-right:auto}@media(max-width:640px){.tasks-page{padding:24px 16px 96px}.tasks-head{align-items:flex-start}.tasks-head h1{font-size:28px}.task-form-grid{grid-template-columns:1fr}.task-row{gap:7px}.task-priority{display:none}}
</style>
