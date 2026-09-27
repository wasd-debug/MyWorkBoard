<template>
  <section class="approval-page">
    <header class="approval-page-head">
      <div><span>HIGH-RISK ACTIONS</span><h1>站内审批中心</h1><p>高风险操作只能在这里由当前登录用户再次确认。</p></div>
      <button type="button" :disabled="loading" @click="load"><RefreshCw :class="{ spinning: loading }" />刷新</button>
    </header>

    <nav class="approval-filters" aria-label="审批状态筛选">
      <button v-for="item in filters" :key="item.value" type="button" :class="{ active: filter === item.value }" @click="setFilter(item.value)">{{ item.label }}</button>
    </nav>

    <div class="approval-layout">
      <aside class="approval-list" aria-label="审批列表">
        <p v-if="!loading && !approvals.length" class="approval-empty">当前没有符合条件的审批</p>
        <button v-for="item in approvals" :key="item.id" type="button" :class="{ active: selected?.id === item.id }" @click="select(item)">
          <span><FileSpreadsheet /><b>{{ item.summary }}</b></span>
          <small>{{ formatTime(item.createdAt) }} · {{ statusLabel(item.status) }}</small>
        </button>
      </aside>

      <main v-if="selected" class="approval-detail">
        <header>
          <span class="approval-icon"><ShieldCheck /></span>
          <div><small>R4 HIGH RISK</small><h2>{{ selected.summary }}</h2><p>审批编号 {{ selected.id }}</p></div>
          <em :class="`status-${selected.status.toLowerCase()}`">{{ statusLabel(selected.status) }}</em>
        </header>

        <div class="approval-stats">
          <span><b>{{ preview.validCount || 0 }}</b><small>预计写入</small></span>
          <span><b>{{ preview.duplicateCount || 0 }}</b><small>重复跳过</small></span>
          <span><b>{{ preview.errorCount || 0 }}</b><small>错误跳过</small></span>
        </div>

        <dl class="approval-meta">
          <div><dt>文件</dt><dd>{{ preview.filename || '未知文件' }}</dd></div>
          <div><dt>账本</dt><dd>{{ selected.bookId }}</dd></div>
          <div><dt>模板</dt><dd>{{ preview.template || 'AUTO' }}</dd></div>
          <div><dt>重复策略</dt><dd>跳过重复流水</dd></div>
          <div><dt>过期时间</dt><dd>{{ formatTime(selected.expiresAt) }}</dd></div>
        </dl>

        <section class="approval-effects">
          <h3>执行影响</h3>
          <ul><li v-for="effect in selected.payload?.effects || []" :key="effect"><CheckCircle2 />{{ effect }}</li></ul>
        </section>

        <details v-if="preview.rows?.length" class="approval-rows" open>
          <summary>解析明细（展示前 50 行）<ChevronDown /></summary>
          <div>
            <article v-for="row in preview.rows.slice(0, 50)" :key="`${row.sheet}-${row.rowNumber}`">
              <span>{{ row.sheet }} · 第 {{ row.rowNumber }} 行</span>
              <b>{{ transactionLabel(row.kind) }} · {{ money(row.amount) }}</b>
              <em :class="`row-${String(row.status).toLowerCase()}`">{{ rowStatus(row.status) }}</em>
              <small>{{ row.errors?.join('；') || [row.account, row.parentCategory, row.category].filter(Boolean).join(' / ') || '无补充信息' }}</small>
            </article>
          </div>
        </details>

        <div v-if="selected.result" class="approval-result" :class="{ failed: selected.status === 'FAILED' }">
          <CheckCircle2 v-if="selected.status === 'COMPLETED'" /><CircleX v-else />
          <div><b>{{ resultSummary }}</b><small>该结果已持久化，刷新页面不会重复执行。</small></div>
        </div>

        <footer v-if="selected.status === 'PENDING'">
          <button type="button" :disabled="busy" @click="reject"><X />拒绝</button>
          <button class="approve" type="button" :disabled="busy" @click="approve"><ShieldCheck />{{ busy ? '正在校验并执行…' : `批准并导入 ${preview.validCount || 0} 笔` }}</button>
        </footer>
        <footer v-else><router-link to="/">返回 AI 工作台</router-link><router-link class="approve" to="/ledger/transactions">查看账本流水</router-link></footer>
      </main>
      <main v-else class="approval-placeholder"><ShieldCheck /><h2>选择一条审批查看详情</h2><p>批准前请核对文件、账本、数量和错误行。</p></main>
    </div>
  </section>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { CheckCircle2, ChevronDown, CircleX, FileSpreadsheet, RefreshCw, ShieldCheck, X } from 'lucide-vue-next'
import { apiApproveAgentApproval, apiGetAgentApproval, apiListAgentApprovals, apiRejectAgentApproval } from '../../packages/api-client/src/index.js'
import { message } from '../services/message.js'
import { useLedgerStore } from '../stores/ledger.js'

const route = useRoute()
const router = useRouter()
const ledgerStore = useLedgerStore()
const approvals = ref([])
const selected = ref(null)
const loading = ref(false)
const busy = ref(false)
const filter = ref('')
const filters = [
  { value: '', label: '全部' }, { value: 'PENDING', label: '待审批' },
  { value: 'COMPLETED', label: '已完成' }, { value: 'REJECTED', label: '已拒绝' },
  { value: 'FAILED', label: '失败' }, { value: 'EXPIRED', label: '已过期' }
]
const preview = computed(() => selected.value?.payload?.preview || {})
const resultSummary = computed(() => selected.value?.result?.summary || (selected.value?.status === 'COMPLETED' ? '导入已完成' : '导入执行失败'))

function statusLabel(value) { return ({ PENDING: '待审批', APPROVED: '已批准', EXECUTING: '执行中', COMPLETED: '已完成', REJECTED: '已拒绝', FAILED: '失败', EXPIRED: '已过期', CANCELLED: '已取消' })[value] || value }
function rowStatus(value) { return ({ VALID: '有效', DUPLICATE: '重复', ERROR: '错误' })[value] || value }
function transactionLabel(value) { return ({ EXPENSE: '支出', INCOME: '收入', TRANSFER: '转账', BORROW_IN: '借入', LEND_OUT: '借出', COLLECT_DEBT: '收债', REPAY_DEBT: '还款' })[value] || value || '流水' }
function money(value) { return Number.isFinite(Number(value)) ? `¥${Number(value).toFixed(2)}` : '金额待确认' }
function formatTime(value) { return value ? new Intl.DateTimeFormat('zh-CN', { dateStyle: 'medium', timeStyle: 'short' }).format(new Date(value)) : '—' }

async function load() {
  loading.value = true
  try {
    approvals.value = await apiListAgentApprovals(filter.value)
    const targetId = String(route.params.id || selected.value?.id || '')
    if (targetId) selected.value = await apiGetAgentApproval(targetId)
    else if (approvals.value.length) selected.value = approvals.value[0]
  } catch (error) { message.error(error?.response?.data?.detail || '加载审批失败') }
  finally { loading.value = false }
}
async function setFilter(value) { filter.value = value; selected.value = null; await router.replace({ path: '/approvals' }); await load() }
async function select(item) { selected.value = await apiGetAgentApproval(item.id); await router.replace(`/approvals/${item.id}`) }
async function approve() {
  if (!selected.value || busy.value) return
  busy.value = true
  try {
    selected.value = await apiApproveAgentApproval(selected.value.id)
    message.success(selected.value.status === 'COMPLETED' ? '审批通过，导入已完成' : '审批已处理')
    await ledgerStore.refreshCurrentBook(undefined, { sync: false }).catch(() => {})
    await load()
  } catch (error) { message.error(error?.response?.data?.detail || '批准失败') }
  finally { busy.value = false }
}
async function reject() {
  if (!selected.value || busy.value) return
  busy.value = true
  try { selected.value = await apiRejectAgentApproval(selected.value.id); message.success('审批已拒绝'); await load() }
  catch (error) { message.error(error?.response?.data?.detail || '拒绝失败') }
  finally { busy.value = false }
}

onMounted(load)
</script>

<style scoped>
.approval-page{max-width:1280px;margin:0 auto;padding:28px 24px 56px}.approval-page-head{display:flex;align-items:flex-end;justify-content:space-between;gap:20px;margin-bottom:20px}.approval-page-head span{font-size:11px;letter-spacing:.16em;color:var(--accent);font-weight:800}.approval-page-head h1{margin:5px 0 4px;font-size:30px}.approval-page-head p{margin:0;color:var(--muted)}.approval-page-head button,.approval-filters button,.approval-detail footer button,.approval-detail footer a{display:inline-flex;align-items:center;justify-content:center;gap:7px;border:1px solid var(--line);border-radius:10px;background:var(--card);color:var(--ink);padding:9px 13px}.approval-page-head svg{width:16px}.spinning{animation:spin 1s linear infinite}@keyframes spin{to{transform:rotate(360deg)}}.approval-filters{display:flex;gap:8px;overflow:auto;margin-bottom:14px}.approval-filters button.active{background:var(--accent);border-color:var(--accent);color:var(--accent-contrast)}.approval-layout{display:grid;grid-template-columns:minmax(240px,320px) minmax(0,1fr);min-height:620px;border:1px solid var(--line);border-radius:18px;overflow:hidden;background:var(--card)}.approval-list{padding:12px;border-right:1px solid var(--line);background:color-mix(in srgb,var(--card) 88%,var(--bg))}.approval-list>button{display:flex;width:100%;flex-direction:column;gap:7px;text-align:left;padding:13px;border:1px solid transparent;border-radius:12px;background:transparent;color:var(--ink)}.approval-list>button:hover,.approval-list>button.active{border-color:color-mix(in srgb,var(--accent) 42%,var(--line));background:var(--card)}.approval-list>button span{display:flex;gap:9px;align-items:flex-start}.approval-list svg{width:17px;flex:0 0 auto;color:var(--accent)}.approval-list b{font-size:13px;line-height:1.5}.approval-list small{color:var(--muted);padding-left:26px}.approval-empty{padding:24px 10px;color:var(--muted);text-align:center}.approval-detail{padding:26px;min-width:0}.approval-detail>header{display:grid;grid-template-columns:auto minmax(0,1fr) auto;gap:14px;align-items:start}.approval-icon{display:grid;place-items:center;width:44px;height:44px;border-radius:13px;background:color-mix(in srgb,var(--accent) 14%,transparent);color:var(--accent)}.approval-icon svg{width:22px}.approval-detail h2{margin:3px 0 4px;font-size:20px}.approval-detail header p,.approval-detail header small{margin:0;color:var(--muted);font-size:11px}.approval-detail header em{padding:5px 9px;border-radius:999px;background:var(--accent-soft);color:var(--accent);font-style:normal;font-size:11px;font-weight:800}.approval-stats{display:grid;grid-template-columns:repeat(3,1fr);gap:10px;margin:24px 0}.approval-stats span{display:flex;flex-direction:column;padding:16px;border:1px solid var(--line);border-radius:13px;background:var(--bg)}.approval-stats b{font-size:25px}.approval-stats small{color:var(--muted)}.approval-meta{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:1px;background:var(--line);border:1px solid var(--line);border-radius:13px;overflow:hidden}.approval-meta div{padding:12px;background:var(--card)}.approval-meta dt{font-size:11px;color:var(--muted)}.approval-meta dd{margin:4px 0 0;overflow-wrap:anywhere}.approval-effects{margin-top:18px;padding:15px;border-radius:13px;background:color-mix(in srgb,var(--accent-soft) 35%,var(--card))}.approval-effects h3{margin:0 0 9px;font-size:13px}.approval-effects ul{display:grid;gap:7px;margin:0;padding:0;list-style:none}.approval-effects li{display:flex;gap:7px;align-items:flex-start;font-size:12px}.approval-effects svg{width:15px;color:var(--up);flex:0 0 auto}.approval-rows{margin-top:18px;border:1px solid var(--line);border-radius:13px;overflow:hidden}.approval-rows summary{display:flex;align-items:center;justify-content:space-between;padding:13px;cursor:pointer;font-weight:700}.approval-rows summary svg{width:16px}.approval-rows>div{max-height:320px;overflow:auto;border-top:1px solid var(--line)}.approval-rows article{display:grid;grid-template-columns:1fr 1fr auto;gap:5px 12px;padding:11px 13px;border-bottom:1px solid var(--line);font-size:12px}.approval-rows article small{grid-column:1/-1;color:var(--muted)}.approval-rows article em{font-style:normal;font-weight:800}.row-valid{color:var(--up)}.row-duplicate{color:var(--warn)}.row-error{color:var(--down)}.approval-result{display:flex;gap:10px;margin-top:18px;padding:14px;border-radius:13px;background:color-mix(in srgb,var(--up) 10%,var(--card));color:var(--ink)}.approval-result.failed{background:color-mix(in srgb,var(--down) 10%,var(--card))}.approval-result svg{width:20px;color:var(--up)}.approval-result.failed svg{color:var(--down)}.approval-result div{display:flex;flex-direction:column}.approval-result small{color:var(--muted)}.approval-detail footer{display:flex;justify-content:flex-end;gap:10px;margin-top:22px}.approval-detail footer .approve{border-color:var(--accent);background:var(--accent);color:var(--accent-contrast);font-weight:800;text-decoration:none}.approval-detail footer svg{width:16px}.approval-placeholder{display:grid;place-content:center;text-align:center;color:var(--muted)}.approval-placeholder svg{width:42px;margin:0 auto 12px;color:var(--accent)}.approval-placeholder h2{margin:0 0 6px;color:var(--ink)}.approval-placeholder p{margin:0}.status-completed{color:var(--up)!important}.status-rejected,.status-failed,.status-expired{color:var(--down)!important}
@media(max-width:760px){.approval-page{padding:16px 12px 96px}.approval-page-head{align-items:flex-start}.approval-page-head h1{font-size:24px}.approval-page-head button{padding:8px}.approval-page-head button{font-size:0}.approval-layout{display:block;min-height:0}.approval-list{display:flex;gap:8px;overflow:auto;border-right:0;border-bottom:1px solid var(--line)}.approval-list>button{min-width:230px}.approval-detail{padding:17px}.approval-detail>header{grid-template-columns:auto minmax(0,1fr)}.approval-detail header em{grid-column:2}.approval-stats{grid-template-columns:repeat(3,1fr)}.approval-stats span{padding:10px}.approval-stats b{font-size:20px}.approval-meta{grid-template-columns:1fr}.approval-rows article{grid-template-columns:1fr auto}.approval-rows article b{grid-column:1/-1}.approval-detail footer{position:sticky;bottom:74px;z-index:2;padding:10px;margin:20px -17px -17px;background:var(--card);border-top:1px solid var(--line)}.approval-detail footer>*{flex:1}.approval-placeholder{min-height:360px}}
</style>
