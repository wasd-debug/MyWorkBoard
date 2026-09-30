<template>
  <section class="mcp-action-page">
    <header>
      <div><span>EXTERNAL AGENT / MCP</span><h1>外部操作确认</h1><p>核对外部 Agent 准备的操作。批准只冻结本次参数，不会在当前页面直接写入业务数据。</p></div>
      <router-link to="/settings">管理 MCP Token</router-link>
    </header>

    <main v-if="loading" class="state-card"><RefreshCw class="spinning" /><h2>正在验证确认链接</h2></main>
    <main v-else-if="error" class="state-card error"><CircleAlert /><h2>无法打开该操作</h2><p>{{ error }}</p><router-link to="/">返回工作台</router-link></main>
    <main v-else-if="action" class="action-card">
      <div class="action-heading">
        <div class="action-icon"><ShieldCheck /></div>
        <div><small>{{ toolLabel(action.toolName) }}</small><h2>{{ action.summary || '待确认的外部操作' }}</h2><p>由 {{ action.clientName || 'MCP Client' }} 使用 Token“{{ action.tokenName }}”创建</p></div>
        <em :class="`status-${String(action.status).toLowerCase()}`">{{ statusLabel(action.status) }}</em>
      </div>

      <dl class="action-meta">
        <div><dt>风险等级</dt><dd>{{ action.riskLevel }}</dd></div>
        <div><dt>创建时间</dt><dd>{{ formatTime(action.createdAt) }}</dd></div>
        <div><dt>过期时间</dt><dd>{{ formatTime(action.expiresAt) }}</dd></div>
        <div><dt>Action ID</dt><dd><code>{{ action.actionId }}</code></dd></div>
      </dl>

      <section class="preview-block">
        <h3>操作预览</h3>
        <div class="preview-grid">
          <article v-for="item in previewFields" :key="item.key">
            <small>{{ item.label }}</small><b>{{ item.value }}</b>
          </article>
        </div>
        <p v-if="!previewFields.length" class="empty-preview">该操作没有额外预览字段，请核对工具名称和原始参数。</p>
      </section>

      <details class="input-details">
        <summary>查看冻结参数 <ChevronDown /></summary>
        <dl><div v-for="item in inputFields" :key="item.key"><dt>{{ item.label }}</dt><dd>{{ item.value }}</dd></div></dl>
      </details>

      <div class="safety-note"><LockKeyhole /><p><b>批准与提交严格分离</b><span>{{ commitAllowed ? '批准后，原 MCP 客户端还必须具备对应 commit scope 并显式调用 agent.action.commit；一个 action 最多成功写入一次。' : '该操作不是 R2 低风险操作，批准后仍禁止通过 MCP commit；参数变化必须重新 prepare。' }}</span></p></div>

      <div v-if="action.status === 'APPROVED'" class="result approved"><CheckCircle2 /><div><b>已批准并冻结参数</b><small>{{ commitAllowed ? '请返回原外部 Agent 发起显式提交；批准本身没有写入业务数据。' : '该风险等级不允许 MCP commit，不会产生业务写入。' }}</small></div></div>
      <div v-else-if="action.status === 'COMPLETED'" class="result approved"><CheckCircle2 /><div><b>{{ action.commitSummary || '操作已完成' }}</b><small>提交结果已经固化；重复调用只会返回首次结果，不会再次写入。</small></div></div>
      <div v-else-if="action.status === 'CONFLICT'" class="result rejected"><CircleAlert /><div><b>{{ action.commitSummary || '提交发生数据冲突' }}</b><small>业务数据没有被陈旧参数覆盖，请重新 prepare 并确认最新内容。</small></div></div>
      <div v-else-if="action.status === 'FAILED'" class="result rejected"><CircleX /><div><b>{{ action.commitSummary || '提交失败' }}</b><small>该 action 不会自动重试，请返回外部 Agent 查询详情并重新准备操作。</small></div></div>
      <div v-else-if="['DENIED','CANCELLED'].includes(action.status)" class="result rejected"><CircleX /><div><b>该操作已拒绝</b><small>外部 Agent 无法再使用此 action。</small></div></div>
      <div v-else-if="action.status === 'EXPIRED'" class="result rejected"><Clock3 /><div><b>该操作已过期</b><small>请让外部 Agent 重新生成操作预览。</small></div></div>

      <footer v-if="action.status === 'WAITING_CONFIRMATION'" class="confirmation-actions">
        <button type="button" :disabled="busy" @click="reject"><X />拒绝</button>
        <button class="approve" type="button" :disabled="busy" @click="approve"><ShieldCheck />{{ busy ? '正在处理…' : '批准并冻结参数' }}</button>
      </footer>
      <footer v-else class="resolved-actions"><router-link to="/">返回 AI 工作台</router-link><router-link class="approve" to="/settings">查看 MCP Token</router-link></footer>
    </main>
  </section>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { CheckCircle2, ChevronDown, CircleAlert, CircleX, Clock3, LockKeyhole, RefreshCw, ShieldCheck, X } from 'lucide-vue-next'
import { apiApproveMcpActionConfirmation, apiGetMcpActionConfirmation, apiRejectMcpActionConfirmation } from '../../packages/api-client/src/index.js'
import { message } from '../services/message.js'

const route = useRoute()
const action = ref(null)
const loading = ref(true)
const busy = ref(false)
const error = ref('')
const token = computed(() => String(route.query.token || ''))
const commitAllowed = computed(() => action.value?.riskLevel === 'R2')
const previewFields = computed(() => fields(action.value?.structuredContent?.preview || action.value?.structuredContent || {}, true))
const inputFields = computed(() => fields(action.value?.input || {}, false))

const labels = {
  actionType: '操作类型', kind: '流水类型', amount: '金额', occurredOn: '发生日期', date: '日期',
  bookId: '账本', bookName: '账本名称', accountId: '账户', accountName: '账户名称',
  targetAccountId: '目标账户', targetAccountName: '目标账户名称', categoryId: '分类', categoryName: '分类名称',
  merchantId: '商家或对方', merchantName: '商家或对方名称', memberId: '成员', memberName: '成员名称',
  projectId: '项目', projectName: '项目名称', note: '备注', recordId: '工时记录',
  start: '上班时间', end: '下班时间', rest: '额外休息分钟', restMin: '休息分钟',
  overtimeMin: '加班分钟', realHourlyWage: '实际时薪', revision: '数据版本', expectedRevision: '预期版本'
}

function fields(value, compact) {
  if (!value || typeof value !== 'object' || Array.isArray(value)) return []
  const excluded = new Set(['entityMatches', 'missingFields', 'suggestedFields', 'ambiguousFields', 'resolutionRequired', 'preview', 'before', 'after'])
  return Object.entries(value).filter(([key, item]) => !excluded.has(key) && item !== null && item !== '' && typeof item !== 'object')
    .slice(0, compact ? 16 : 30).map(([key, item]) => ({ key, label: labels[key] || key, value: formatValue(key, item) }))
}
function formatValue(key, value) {
  if (key === 'amount' && Number.isFinite(Number(value))) return `¥${Number(value).toFixed(2)}`
  if (typeof value === 'boolean') return value ? '是' : '否'
  return String(value)
}
function toolLabel(value) { return ({
  'ledger.transaction.create.prepare': '新增账本流水', 'ledger.transaction.update.prepare': '修改账本流水',
  'ledger.transaction.delete.prepare': '删除账本流水', 'worktime.record.create.prepare': '新增工时记录',
  'worktime.record.update.prepare': '修改工时记录', 'worktime.record.delete.prepare': '删除工时记录'
})[value] || value }
function statusLabel(value) { return ({ WAITING_CONFIRMATION: '待确认', APPROVED: '已批准', DENIED: '已拒绝', CANCELLED: '已取消', EXPIRED: '已过期', COMPLETED: '已完成', CONFLICT: '有冲突', FAILED: '失败' })[value] || value }
function formatTime(value) { return value ? new Intl.DateTimeFormat('zh-CN', { dateStyle: 'medium', timeStyle: 'medium' }).format(new Date(value)) : '—' }

async function load() {
  loading.value = true; error.value = ''
  try {
    if (!token.value) throw new Error('确认链接缺少令牌')
    action.value = await apiGetMcpActionConfirmation(token.value)
  } catch (exception) { error.value = exception?.response?.data?.detail || exception.message || '确认链接无效或已过期' }
  finally { loading.value = false }
}
async function approve() {
  if (busy.value) return
  busy.value = true
  try { action.value = await apiApproveMcpActionConfirmation(token.value); message.success('参数已批准并冻结，批准本身没有写入业务数据') }
  catch (exception) { message.error(exception?.response?.data?.detail || '批准失败') }
  finally { busy.value = false }
}
async function reject() {
  if (busy.value) return
  busy.value = true
  try { action.value = await apiRejectMcpActionConfirmation(token.value); message.success('外部操作已拒绝') }
  catch (exception) { message.error(exception?.response?.data?.detail || '拒绝失败') }
  finally { busy.value = false }
}

onMounted(load)
</script>

<style scoped>
.mcp-action-page{max-width:980px;margin:0 auto;padding:30px 24px 64px}.mcp-action-page>header{display:flex;align-items:flex-end;justify-content:space-between;gap:20px;margin-bottom:20px}.mcp-action-page>header span{color:var(--accent);font-size:11px;font-weight:800;letter-spacing:.16em}.mcp-action-page>header h1{margin:5px 0 4px;font-size:30px}.mcp-action-page>header p{margin:0;color:var(--muted);line-height:1.6}.mcp-action-page>header a,.action-card footer a,.action-card footer button,.state-card a{display:inline-flex;align-items:center;justify-content:center;gap:7px;padding:10px 14px;border:1px solid var(--line);border-radius:10px;background:var(--card);color:var(--ink);text-decoration:none}.action-card,.state-card{padding:26px;border:1px solid var(--line);border-radius:18px;background:var(--card);box-shadow:0 18px 50px rgba(18,24,35,.07)}.state-card{display:grid;min-height:340px;place-content:center;text-align:center;color:var(--muted)}.state-card svg{width:42px;margin:0 auto 12px;color:var(--accent)}.state-card h2{margin:0 0 8px;color:var(--ink)}.state-card p{max-width:520px}.state-card a{margin:12px auto 0}.state-card.error svg{color:var(--down)}.spinning{animation:spin 1s linear infinite}@keyframes spin{to{transform:rotate(360deg)}}.action-heading{display:grid;grid-template-columns:auto minmax(0,1fr) auto;gap:14px;align-items:start}.action-icon{display:grid;width:46px;height:46px;place-items:center;border-radius:14px;background:var(--accent-soft);color:var(--accent)}.action-icon svg{width:22px}.action-heading small,.action-heading p{color:var(--muted)}.action-heading h2{margin:3px 0 5px;font-size:21px}.action-heading p{margin:0;font-size:12px}.action-heading em{padding:6px 10px;border-radius:999px;background:var(--accent-soft);color:var(--accent);font-size:11px;font-style:normal;font-weight:800}.action-meta{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:1px;margin:24px 0 18px;overflow:hidden;border:1px solid var(--line);border-radius:13px;background:var(--line)}.action-meta div{padding:13px;background:var(--card)}.action-meta dt,.input-details dt{color:var(--muted);font-size:11px}.action-meta dd,.input-details dd{margin:4px 0 0;overflow-wrap:anywhere}.action-meta code{font-size:11px}.preview-block{padding:17px;border:1px solid var(--line);border-radius:14px}.preview-block h3{margin:0 0 12px;font-size:14px}.preview-grid{display:grid;grid-template-columns:repeat(3,minmax(0,1fr));gap:9px}.preview-grid article{display:flex;min-width:0;flex-direction:column;gap:5px;padding:12px;border-radius:10px;background:var(--bg)}.preview-grid small{color:var(--muted);font-size:11px}.preview-grid b{overflow-wrap:anywhere;font-size:13px}.empty-preview{margin:0;color:var(--muted)}.input-details{margin-top:16px;overflow:hidden;border:1px solid var(--line);border-radius:13px}.input-details summary{display:flex;align-items:center;justify-content:space-between;padding:13px 15px;cursor:pointer;font-weight:700}.input-details summary svg{width:16px}.input-details dl{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:1px;margin:0;border-top:1px solid var(--line);background:var(--line)}.input-details dl div{padding:12px;background:var(--card)}.safety-note,.result{display:flex;gap:11px;margin-top:17px;padding:14px;border-radius:13px;background:color-mix(in srgb,var(--accent-soft) 45%,var(--card))}.safety-note>svg,.result>svg{flex:0 0 20px;width:20px;color:var(--accent)}.safety-note p,.result div{display:flex;flex-direction:column;gap:3px;margin:0}.safety-note span,.result small{color:var(--muted);font-size:12px;line-height:1.5}.result.approved{background:color-mix(in srgb,var(--up) 11%,var(--card))}.result.approved>svg{color:var(--up)}.result.rejected{background:color-mix(in srgb,var(--down) 9%,var(--card))}.result.rejected>svg{color:var(--down)}.action-card footer{display:flex;justify-content:flex-end;gap:10px;margin-top:22px}.action-card footer .approve{border-color:var(--accent);background:var(--accent);color:var(--card);font-weight:800}.action-card footer button svg{width:16px}.action-card footer button:disabled{cursor:wait;opacity:.6}.status-approved,.status-completed{color:var(--up)!important}.status-denied,.status-cancelled,.status-expired,.status-failed{color:var(--down)!important}
@media(max-width:720px){.mcp-action-page{padding:16px 12px calc(112px + env(safe-area-inset-bottom))}.mcp-action-page>header{align-items:flex-start;flex-direction:column}.mcp-action-page>header h1{font-size:25px}.action-card{padding:17px}.action-heading{grid-template-columns:auto minmax(0,1fr)}.action-heading em{grid-column:2}.action-meta,.input-details dl{grid-template-columns:1fr}.preview-grid{grid-template-columns:repeat(2,minmax(0,1fr))}.action-card footer.confirmation-actions{position:sticky;bottom:calc(84px + env(safe-area-inset-bottom));z-index:2;margin:20px -17px -17px;padding:11px 17px;border-top:1px solid var(--line);background:var(--card)}.action-card footer.resolved-actions{margin-bottom:0}.action-card footer>*{flex:1}}@media(max-width:420px){.preview-grid{grid-template-columns:1fr}}
.action-card footer .approve { color:var(--accent-contrast,#fff) }
.action-card footer .approve:hover:not(:disabled) { border-color:color-mix(in srgb,var(--accent) 86%,#000); background:color-mix(in srgb,var(--accent) 86%,#000); color:var(--accent-contrast,#fff) }
</style>
