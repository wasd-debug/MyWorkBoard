<template>
  <HomeWidgetFrame title="最近流水" kicker="LEDGER" :meta="bookName" :editing="editing">
    <template #actions>
      <button class="hw-btn primary" type="button" @click="openCreate"><Plus :size="13" />记一笔</button>
    </template>

    <div class="lg-body">
      <ol v-if="rows.length" class="lg-list">
        <li v-for="row in rows" :key="row.id">
          <button class="lg-row" type="button" :title="`编辑「${row.title}」`" @click="openEdit(row)">
            <span class="lg-date">{{ row.dayLabel }}</span>
            <span class="lg-main">
              <b :title="row.title">{{ row.title }}</b>
              <small :title="`${row.accountName}${row.categoryName ? ` · ${row.categoryName}` : ''}`">{{ row.accountName }}<template v-if="row.categoryName"> · {{ row.categoryName }}</template></small>
            </span>
            <span class="lg-amount" :class="row.tone">{{ row.sign }}¥{{ money(row.amount) }}</span>
          </button>
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

    <HomeTransactionEditor v-model:open="editorOpen" :transaction="editingTransaction" @saved="editingTransaction = null" />
  </HomeWidgetFrame>
</template>

<script setup>
import { computed, ref } from 'vue'
import { Plus } from 'lucide-vue-next'
import HomeWidgetFrame from './HomeWidgetFrame.vue'
import HomeTransactionEditor from './HomeTransactionEditor.vue'
import { useLedgerStore } from '../../stores/ledger.js'
import { monthKeyOf, periodSummary } from './homeMetrics.js'

defineProps({ editing: Boolean })

const KIND_LABEL = {
  EXPENSE: '支出', INCOME: '收入', TRANSFER: '转账', BORROW_IN: '借入',
  LEND_OUT: '借出', COLLECT_DEBT: '收债', REPAY_DEBT: '还债'
}
const IN_KINDS = ['INCOME', 'BORROW_IN', 'COLLECT_DEBT']
const OUT_KINDS = ['EXPENSE', 'LEND_OUT', 'REPAY_DEBT']

const ledgerStore = useLedgerStore()
const editorOpen = ref(false)
const editingTransaction = ref(null)

const bookName = computed(() => ledgerStore.currentBook?.name || '未选择账本')
const accountNames = computed(() => new Map(ledgerStore.visibleAccounts.map(item => [String(item.id), item.name])))
const categoryNames = computed(() => new Map(ledgerStore.visibleCategories.map(item => [String(item.id), item.name])))
const monthExpense = computed(() => periodSummary(ledgerStore.transactions, `${monthKeyOf(new Date())}-01`, `${monthKeyOf(new Date())}-31`).expense)

const rows = computed(() => ledgerStore.transactions
  .filter(item => item && !item.deleted)
  .slice()
  .sort((left, right) => String(right.occurredOn || '').localeCompare(String(left.occurredOn || ''))
    || String(right.createdAt || '').localeCompare(String(left.createdAt || '')))
  .slice(0, 10)
  .map(item => ({
    id: item.id || item.publicId,
    transaction: item,
    dayLabel: String(item.occurredOn || '').slice(5).replace('-', '/'),
    title: item.payee || item.note || KIND_LABEL[item.kind] || '流水',
    accountName: accountNames.value.get(String(item.accountId)) || '未指定账户',
    categoryName: categoryNames.value.get(String(item.categoryId)) || '',
    amount: Number(item.amount) || 0,
    tone: IN_KINDS.includes(item.kind) ? 'is-in' : OUT_KINDS.includes(item.kind) ? 'is-out' : 'is-move',
    sign: IN_KINDS.includes(item.kind) ? '+' : OUT_KINDS.includes(item.kind) ? '−' : ''
  })))

function money(value) {
  return Number(value || 0).toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
}

function openCreate() {
  editingTransaction.value = null
  editorOpen.value = true
}

function openEdit(row) {
  editingTransaction.value = row.transaction
  editorOpen.value = true
}
</script>

<style scoped>
.lg-body { display: flex; height: 100%; min-height: 0; flex-direction: column; }
.lg-list { display: flex; min-height: 0; flex: 1; flex-direction: column; gap: 1px; overflow-y: auto; margin: 0; padding: 0; list-style: none; scrollbar-width: thin; }
.lg-list li { min-height: 30px; border-bottom: 1px dashed color-mix(in srgb, var(--line) 70%, transparent); }
.lg-list li:last-child { border-bottom: 0; }
.lg-row {
  display: grid; width: 100%; min-width: 0; grid-template-columns: 34px minmax(0, 1fr) auto;
  align-items: center; gap: 8px; padding: 4px 4px 4px 2px; border: 0; border-radius: 8px;
  background: transparent; text-align: left;
}
.lg-row:hover { background: var(--paper); }
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
@media (max-width: 767px) {
  .lg-list li { min-height: 34px; }
  .lg-row { padding: 6px 4px 6px 2px; }
}
</style>
