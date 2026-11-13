<template>
  <Sheet :open="open" :title="transaction ? '编辑流水' : '记一笔'" @update:open="emit('update:open', $event)">
    <form class="hte-form" @submit.prevent="submit">
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
      <div class="hte-foot">
        <button v-if="transaction" class="hw-btn hte-delete" type="button" :disabled="saving" @click="remove">
          <Trash2 :size="13" />删除
        </button>
        <span v-if="error" class="hte-error">{{ error }}</span>
        <button class="hw-btn" type="button" @click="emit('update:open', false)">取消</button>
        <button class="hw-btn primary" type="submit" :disabled="saving">{{ saving ? '保存中…' : '保存' }}</button>
      </div>
    </form>
  </Sheet>
</template>

<script setup>
import { computed, reactive, ref, watch } from 'vue'
import { Trash2 } from 'lucide-vue-next'
import Sheet from '../ui/Sheet.vue'
import LedgerTransactionEditor from '../ledger/LedgerTransactionEditor.vue'
import { useLedgerStore } from '../../stores/ledger.js'
import { message } from '../../services/message.js'
import {
  ledgerFormFromTransaction, ledgerTransactionDraft, preferredDraftAccountId, recentLedgerTransactions
} from '../ledger/ledgerPreferences.js'
import { dateKeyOf } from './homeMetrics.js'

/**
 * 首页流水编辑入口。
 *
 * 「最近流水」与「日历」两张卡片共用这一个 Sheet：表单仍是账本域的
 * `LedgerTransactionEditor`，保存仍走 `ledgerStore.saveTransaction`，
 * 首页只负责把「哪一笔」传进来，不再各自实现一套表单与校验。
 */
const props = defineProps({
  open: { type: Boolean, default: false },
  transaction: { type: Object, default: null }
})
const emit = defineEmits(['update:open', 'saved'])

const KIND_OPTIONS = [
  { value: 'EXPENSE', label: '支出' }, { value: 'INCOME', label: '收入' }, { value: 'TRANSFER', label: '转账' },
  { value: 'BORROW_IN', label: '借入' }, { value: 'LEND_OUT', label: '借出' },
  { value: 'COLLECT_DEBT', label: '收债' }, { value: 'REPAY_DEBT', label: '还债' }
]

const ledgerStore = useLedgerStore()
const saving = ref(false)
const error = ref('')
const form = reactive(blankForm())

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

function unique(values) {
  return [...new Set(values.map(item => String(item || '').trim()).filter(Boolean))]
}

function blankForm() {
  const accountId = preferredDraftAccountId(
    ledgerStore.visibleAccounts, recentLedgerTransactions(ledgerStore.transactions), '')
  return {
    kind: 'EXPENSE', amount: '', occurredOn: dateKeyOf(new Date()), accountId: accountId ? String(accountId) : '',
    targetAccountId: '', categoryId: '', payee: '', member: '', project: '', note: ''
  }
}

/** 打开时按传入流水回填；新建则回到空白草稿，避免上一笔的残留字段串到下一笔 */
watch(() => [props.open, props.transaction?.id], ([open]) => {
  if (!open) return
  error.value = ''
  Object.assign(form, props.transaction ? ledgerFormFromTransaction(props.transaction) : blankForm())
}, { immediate: true })

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
    const payload = props.transaction
      ? { ...draft, id: props.transaction.id, revision: props.transaction.revision }
      : draft
    await ledgerStore.saveTransaction(payload)
    emit('update:open', false)
    message.success(ledgerStore.online ? (props.transaction ? '流水已更新' : '已记一笔') : '已离线保存，联网后自动同步')
    emit('saved')
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

async function remove() {
  if (!props.transaction || saving.value) return
  saving.value = true
  error.value = ''
  try {
    await ledgerStore.deleteTransaction(props.transaction)
    emit('update:open', false)
    message.success(ledgerStore.online ? '流水已删除' : '已离线删除，联网后自动同步')
    emit('saved')
    if (ledgerStore.online) {
      await ledgerStore.syncNow()
      if (!ledgerStore.syncError) await ledgerStore.refreshAccounts()
    }
  } catch (exception) {
    error.value = exception?.response?.data?.detail || exception?.message || '删除失败'
  } finally {
    saving.value = false
  }
}
</script>

<style scoped>
.hte-form { display: flex; flex-direction: column; gap: 14px; }
.hte-foot { display: flex; align-items: center; justify-content: flex-end; gap: 8px; }
.hte-error { flex: 1; color: var(--down); font-size: 12px; }
.hte-delete { margin-right: auto; color: var(--down); }
.hte-delete:hover { border-color: var(--down); color: var(--down); }
</style>
