<template>
  <div class="transaction-editor-grid">
    <label>流水类型<select v-model="model.kind" :disabled="lockKind" required @change="handleKindChange"><option v-for="item in kinds" :key="item.value" :value="item.value">{{ item.label }}</option></select></label>
    <label>金额<input v-model="model.amount" class="ui-input" type="number" min="0.01" step="0.01" required></label>
    <label>日期<input v-model="model.occurredOn" class="ui-input" type="date" required></label>
    <label>{{ model.kind === 'TRANSFER' ? '转出账户' : '账户' }}<select v-model="model.accountId" required @change="handleAccountChange"><option value="" disabled>选择账户</option><option v-for="account in preferredAccounts" :key="account.id" :value="String(account.id)">{{ account.name }}</option></select></label>
    <label v-if="model.kind === 'TRANSFER'">转入账户<select v-model="model.targetAccountId" required><option value="" disabled>选择账户</option><option v-for="account in transferTargets" :key="account.id" :value="String(account.id)">{{ account.name }}</option></select></label>
    <LedgerCategoryCombobox v-if="requiresCategory" v-model="model.categoryId" :kind="model.kind" :categories="categories" :usage-transactions="usageTransactions" />
    <label>商家 / 对方<select v-model="model.payee"><option value="">未指定</option><option v-for="value in merchantOptions" :key="value" :value="value">{{ value }}</option></select></label>
    <label>成员<select v-model="model.member"><option value="">未指定</option><option v-for="value in memberOptions" :key="value" :value="value">{{ value }}</option></select></label>
    <label>项目<select v-model="model.project"><option value="">未指定</option><option v-for="value in projectOptions" :key="value" :value="value">{{ value }}</option></select></label>
    <label class="transaction-editor-note">备注<textarea v-model="model.note" class="ui-textarea" maxlength="500" rows="3"></textarea></label>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import LedgerCategoryCombobox from './LedgerCategoryCombobox.vue'
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

const requiresCategory = computed(() => ['INCOME', 'EXPENSE'].includes(props.model.kind))
const recentTransactions = computed(() => recentLedgerTransactions(props.usageTransactions))
const preferredAccounts = computed(() => rankLedgerOptions(props.accounts, recentTransactions.value,
  item => [item.accountId, item.targetAccountId]))
const transferTargets = computed(() => preferredAccounts.value.filter(item => String(item.id) !== String(props.model.accountId)))
function handleKindChange() {
  props.model.categoryId = ''
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
