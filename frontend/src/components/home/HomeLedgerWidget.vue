<template>
  <HomeWidgetFrame title="最近流水" kicker="LEDGER" :meta="bookName" :editing="editing">
    <template #actions>
      <button class="hw-btn primary" type="button" @click="openCreate"><Plus :size="13" />记一笔</button>
    </template>

    <div class="lg-body">
      <ol v-if="rows.length" class="lg-list">
      <li v-for="row in rows" :key="row.id">
        <span class="lg-date">{{ row.dayLabel }}</span>
        <span class="lg-main">
          <b :title="row.title">{{ row.title }}</b>
          <small :title="`${row.accountName}${row.categoryName ? ` · ${row.categoryName}` : ''}`">{{ row.accountName }}<template v-if="row.categoryName"> · {{ row.categoryName }}</template></small>
        </span>
        <span class="lg-amount" :class="row.tone">{{ row.sign }}¥{{ money(row.amount) }}</span>
      </li>
      </ol>
      <div v-else class="hw-empty">
        <b>还没有流水</b>
        <span>用右上角「记一笔」开始记录</span>
      </div>
    </div>

    <template #footer>
      <span>本月支出 ¥{{ money(monthExpense) }}</span>
      <router-link class="lg-more" to="/ledger/transactions">全部流水 →</router-link>
    </template>

    <Sheet v-model:open="editorOpen" title="记一笔">
      <form class="lg-form" @submit.prevent="submit">
        <LedgerTransactionEditor
          :model="form"
          :accounts="ledgerStore.visibleAccounts"
          :categories="ledgerStore.visibleCategories"
          :usage-transactions="ledgerStore.transactions"
          :kinds="KIND_OPTIONS"
          :merchant-options="merchantOptions"
          :member-options="memberOptions"
          :project-options="projectOptions"
        />
        <div class="lg-form-foot">
          <span v-if="error" class="lg-error">{{ error }}</span>
          <button class="hw-btn" type="button" @click="editorOpen = false">取消</button>
          <button class="hw-btn primary" type="submit" :disabled="saving">{{ saving ? '保存中…' : '保存' }}</button>
        </div>
      </form>
    </Sheet>
  </HomeWidgetFrame>
</template>

<script setup>
import { computed, reactive, ref } from 'vue'
import { Plus } from 'lucide-vue-next'
import HomeWidgetFrame from './HomeWidgetFrame.vue'
import Sheet from '../ui/Sheet.vue'
import LedgerTransactionEditor from '../ledger/LedgerTransactionEditor.vue'
import { useLedgerStore } from '../../stores/ledger.js'
import { message } from '../../services/message.js'
import { ledgerTransactionDraft, preferredDraftAccountId, recentLedgerTransactions } from '../ledger/ledgerPreferences.js'
import { dateKeyOf, monthKeyOf, periodSummary } from './homeMetrics.js'

defineProps({ editing: Boolean })

const KIND_OPTIONS = [
  { value: 'EXPENSE', label: '支出' }, { value: 'INCOME', label: '收入' }, { value: 'TRANSFER', label: '转账' },
  { value: 'BORROW_IN', label: '借入' }, { value: 'LEND_OUT', label: '借出' },
  { value: 'COLLECT_DEBT', label: '收债' }, { value: 'REPAY_DEBT', label: '还债' }
]
const KIND_LABEL = Object.fromEntries(KIND_OPTIONS.map(item => [item.value, item.label]))
const IN_KINDS = ['INCOME', 'BORROW_IN', 'COLLECT_DEBT']
const OUT_KINDS = ['EXPENSE', 'LEND_OUT', 'REPAY_DEBT']

const ledgerStore = useLedgerStore()
const editorOpen = ref(false)
const saving = ref(false)
const error = ref('')
const form = reactive(blankForm())

const bookName = computed(() => ledgerStore.currentBook?.name || '未选择账本')
const accountNames = computed(() => new Map(ledgerStore.visibleAccounts.map(item => [String(item.id), item.name])))
const categoryNames = computed(() => new Map(ledgerStore.visibleCategories.map(item => [String(item.id), item.name])))
const monthExpense = computed(() => periodSummary(ledgerStore.transactions, `${monthKeyOf(new Date())}-01`, `${monthKeyOf(new Date())}-31`).expense)

const merchantOptions = computed(() => unique([
  ...ledgerStore.visibleMerchants.map(item => item.name),
  ...recentLedgerTransactions(ledgerStore.transactions).map(item => item.payee)
]))
const memberOptions = computed(() => unique([
  ...ledgerStore.activeMembers.map(item => item.displayName || item.username),
  ...recentLedgerTransactions(ledgerStore.transactions).map(item => item.memberName)
]))
const projectOptions = computed(() => unique([
  ...ledgerStore.visibleProjects.map(item => item.name),
  ...recentLedgerTransactions(ledgerStore.transactions).map(item => item.projectName)
]))

const rows = computed(() => ledgerStore.transactions
  .filter(item => item && !item.deleted)
  .slice()
  .sort((left, right) => String(right.occurredOn || '').localeCompare(String(left.occurredOn || ''))
    || String(right.createdAt || '').localeCompare(String(left.createdAt || '')))
  .slice(0, 10)
  .map(item => ({
    id: item.id || item.publicId,
    dayLabel: String(item.occurredOn || '').slice(5).replace('-', '/'),
    title: item.payee || item.note || KIND_LABEL[item.kind] || '流水',
    accountName: accountNames.value.get(String(item.accountId)) || '未指定账户',
    categoryName: categoryNames.value.get(String(item.categoryId)) || '',
    amount: Number(item.amount) || 0,
    tone: IN_KINDS.includes(item.kind) ? 'is-in' : OUT_KINDS.includes(item.kind) ? 'is-out' : 'is-move',
    sign: IN_KINDS.includes(item.kind) ? '+' : OUT_KINDS.includes(item.kind) ? '−' : ''
  })))

function unique(values) {
  return [...new Set(values.map(item => String(item || '').trim()).filter(Boolean))]
}

function blankForm() {
  const accountId = preferredDraftAccountId(ledgerStore.visibleAccounts, recentLedgerTransactions(ledgerStore.transactions), '')
  return {
    kind: 'EXPENSE', amount: '', occurredOn: dateKeyOf(new Date()), accountId: accountId ? String(accountId) : '',
    targetAccountId: '', categoryId: '', payee: '', member: '', project: '', note: ''
  }
}

function resetForm() {
  Object.assign(form, blankForm())
  error.value = ''
}

function openCreate() {
  resetForm()
  editorOpen.value = true
}

function money(value) {
  return Number(value || 0).toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
}

async function submit() {
  if (saving.value) return
  saving.value = true
  error.value = ''
  try {
    if (!ledgerStore.currentBookId) throw new Error('请先在账本页创建账本')
    if (form.kind === 'TRANSFER' && !form.targetAccountId) throw new Error('请选择转入账户')
    const draft = ledgerTransactionDraft(form, {
      categories: ledgerStore.visibleCategories,
      merchants: ledgerStore.visibleMerchants,
      members: ledgerStore.activeMembers,
      projects: ledgerStore.visibleProjects
    })
    await ledgerStore.saveTransaction(draft)
    editorOpen.value = false
    message.success(ledgerStore.online ? '已记一笔' : '已离线保存，联网后自动同步')
    resetForm()
    if (ledgerStore.online) {
      await ledgerStore.syncNow()
      if (!ledgerStore.syncError) await ledgerStore.refreshAccounts()
    }
  } catch (exception) {
    error.value = exception?.response?.data?.detail || exception?.message || '保存失败'
  } finally {
    saving.value = false
  }
}
</script>

<style scoped>
.lg-body { display: flex; height: 100%; min-height: 0; flex-direction: column; }
.lg-list { display: flex; min-height: 0; flex: 1; flex-direction: column; gap: 1px; overflow-y: auto; margin: 0; padding: 0; list-style: none; scrollbar-width: thin; }
.lg-list li { display: grid; grid-template-columns: 34px minmax(0, 1fr) auto; min-height: 30px; align-items: center; gap: 8px; padding: 4px 0; border-bottom: 1px dashed color-mix(in srgb, var(--line) 70%, transparent); }
.lg-list li:last-child { border-bottom: 0; }
.lg-date { color: var(--muted); font-size: 11px; font-variant-numeric: tabular-nums; }
.lg-main { display: flex; min-width: 0; align-items: baseline; gap: 6px; }
.lg-main b, .lg-main small { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.lg-main b { min-width: 0; flex: 0 1 auto; color: var(--ink); font-size: 12.5px; font-weight: 600; }
.lg-main small { flex: 0 1 auto; color: var(--muted); font-size: 10.5px; }
.lg-amount { font-size: 12.5px; font-weight: 700; font-variant-numeric: tabular-nums; }
.lg-amount.is-in { color: var(--down); }
.lg-amount.is-out { color: var(--up); }
.lg-amount.is-move { color: var(--muted); }
.lg-more { color: var(--accent); font-weight: 650; }
.lg-form { display: flex; flex-direction: column; gap: 14px; }
.lg-form-foot { display: flex; align-items: center; justify-content: flex-end; gap: 8px; }
.lg-error { flex: 1; color: var(--down); font-size: 12px; }
</style>
