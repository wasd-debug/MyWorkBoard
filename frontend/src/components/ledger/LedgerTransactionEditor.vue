<template>
  <div class="transaction-editor-grid">
    <label>流水类型<select v-model="model.kind" :disabled="lockKind" required @change="handleKindChange"><option v-for="item in kinds" :key="item.value" :value="item.value">{{ item.label }}</option></select></label>
    <label>金额<input v-model="model.amount" class="ui-input" type="number" min="0.01" step="0.01" required></label>
    <label>日期<input v-model="model.occurredOn" class="ui-input" type="date" required></label>
    <label>{{ model.kind === 'TRANSFER' ? '转出账户' : '账户' }}<select v-model="model.accountId" required @change="handleAccountChange"><option value="" disabled>选择账户</option><option v-for="account in preferredAccounts" :key="account.id" :value="String(account.id)">{{ account.name }}</option></select></label>
    <label v-if="model.kind === 'TRANSFER'">转入账户<select v-model="model.targetAccountId" required><option value="" disabled>选择账户</option><option v-for="account in transferTargets" :key="account.id" :value="String(account.id)">{{ account.name }}</option></select></label>
    <template v-if="requiresCategory">
      <label>一级分类<select v-model="primaryCategoryId" required @change="model.categoryId = ''"><option value="" disabled>选择一级分类</option><option v-for="category in primaryCategories" :key="category.id" :value="String(category.id)">{{ category.name }}</option></select></label>
      <label>二级分类<select v-model="model.categoryId" :disabled="!primaryCategoryId" required><option value="" disabled>{{ primaryCategoryId ? '选择二级分类' : '请先选择一级分类' }}</option><option v-for="category in secondaryCategories" :key="category.id" :value="String(category.id)">{{ category.name }}</option></select></label>
    </template>
    <label>商家 / 对方<select v-model="model.payee"><option value="">未指定</option><option v-for="value in merchantOptions" :key="value" :value="value">{{ value }}</option></select></label>
    <label>成员<select v-model="model.member"><option value="">未指定</option><option v-for="value in memberOptions" :key="value" :value="value">{{ value }}</option></select></label>
    <label>项目<select v-model="model.project"><option value="">未指定</option><option v-for="value in projectOptions" :key="value" :value="value">{{ value }}</option></select></label>
    <label class="transaction-editor-note">备注<textarea v-model="model.note" class="ui-textarea" maxlength="500" rows="3"></textarea></label>
  </div>
</template>

<script setup>
import { computed, ref, watch } from 'vue'
import { rankLedgerOptions, recentLedgerTransactions } from './ledgerPreferences'

const props = defineProps({
  model: { type: Object, required: true },
  accounts: { type: Array, default: () => [] },
  categories: { type: Array, default: () => [] },
  kinds: { type: Array, default: () => [] },
  merchantOptions: { type: Array, default: () => [] },
  memberOptions: { type: Array, default: () => [] },
  projectOptions: { type: Array, default: () => [] },
  usageTransactions: { type: Array, default: () => [] },
  lockKind: Boolean
})

const primaryCategoryId = ref('')
const incomeKinds = ['INCOME', 'BORROW_IN', 'COLLECT_DEBT']
const expenseKinds = ['EXPENSE', 'LEND_OUT', 'REPAY_DEBT']
const categoryDirection = computed(() => incomeKinds.includes(props.model.kind) ? 'INCOME' : expenseKinds.includes(props.model.kind) ? 'EXPENSE' : '')
const requiresCategory = computed(() => ['INCOME', 'EXPENSE'].includes(props.model.kind))
const recentTransactions = computed(() => recentLedgerTransactions(props.usageTransactions))
const preferredAccounts = computed(() => rankLedgerOptions(props.accounts, recentTransactions.value,
  item => [item.accountId, item.targetAccountId]))
const primaryCategories = computed(() => rankLedgerOptions(
  props.categories.filter(item => !item.parentId && item.kind === categoryDirection.value),
  recentTransactions.value,
  item => [props.categories.find(category => String(category.id) === String(item.categoryId))?.parentId]))
const secondaryCategories = computed(() => rankLedgerOptions(
  props.categories.filter(item => item.parentId && String(item.parentId) === primaryCategoryId.value && item.kind === categoryDirection.value),
  recentTransactions.value,
  item => [item.categoryId]))
const transferTargets = computed(() => preferredAccounts.value.filter(item => String(item.id) !== String(props.model.accountId)))
watch([() => props.model.categoryId, () => props.categories], syncPrimaryCategory, { immediate: true, deep: true })

function syncPrimaryCategory() {
  const selected = props.categories.find(item => String(item.id) === String(props.model.categoryId || ''))
  primaryCategoryId.value = selected?.parentId ? String(selected.parentId) : ''
}
function handleKindChange() {
  props.model.categoryId = ''
  primaryCategoryId.value = ''
  if (props.model.kind !== 'TRANSFER') props.model.targetAccountId = ''
}
function handleAccountChange() {
  if (props.model.targetAccountId === props.model.accountId) props.model.targetAccountId = ''
}
</script>

<style scoped>
.transaction-editor-grid { display:grid; grid-template-columns:repeat(2,minmax(0,1fr)); gap:12px }
.transaction-editor-grid label { display:flex; min-width:0; flex-direction:column; gap:5px; color:var(--ink2); font-size:12px }
.transaction-editor-grid input,.transaction-editor-grid select,.transaction-editor-grid textarea { width:100%; min-width:0; max-width:100%; box-sizing:border-box }
.transaction-editor-note { grid-column:1/-1 }
@media(max-width:560px){.transaction-editor-grid{grid-template-columns:1fr;gap:9px}.transaction-editor-note{grid-column:auto}}
</style>
