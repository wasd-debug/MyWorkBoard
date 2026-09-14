<template>
  <div class="transaction-editor-grid">
    <label>流水类型<select v-model="model.kind" :disabled="lockKind" required @change="handleKindChange"><option v-for="item in kinds" :key="item.value" :value="item.value">{{ item.label }}</option></select></label>
    <label>金额<input v-model="model.amount" class="ui-input" type="number" min="0.01" step="0.01" required></label>
    <label>日期<input v-model="model.occurredOn" class="ui-input" type="date" required></label>
    <label>{{ model.kind === 'TRANSFER' ? '转出账户' : '账户' }}<select v-model="model.accountId" required @change="handleAccountChange"><option value="" disabled>选择账户</option><option v-for="account in preferredAccounts" :key="account.id" :value="String(account.id)">{{ account.name }}</option></select></label>
    <label v-if="model.kind === 'TRANSFER'">转入账户<select v-model="model.targetAccountId" required><option value="" disabled>选择账户</option><option v-for="account in transferTargets" :key="account.id" :value="String(account.id)">{{ account.name }}</option></select></label>
    <template v-if="requiresCategory">
      <label>一级分类
        <div class="category-combobox">
          <input v-model="primaryCategoryQuery" class="ui-input" required autocomplete="off" placeholder="输入或选择一级分类" @focus="primaryOpen=true" @input="handlePrimaryInput" @blur="closeMenusSoon">
          <div v-if="primaryOpen && filteredPrimaryCategories.length" class="category-options">
            <button v-for="category in filteredPrimaryCategories" :key="category.id" type="button" @mousedown.prevent="selectPrimary(category)">{{ category.name }}</button>
          </div>
        </div>
      </label>
      <label>二级分类
        <div class="category-combobox">
          <input v-model="secondaryCategoryQuery" class="ui-input" required autocomplete="off" placeholder="输入二级分类或一级 / 二级" @focus="secondaryOpen=true" @input="handleSecondaryInput" @blur="closeMenusSoon">
          <div v-if="secondaryOpen && filteredSecondaryCategories.length" class="category-options">
            <button v-for="category in filteredSecondaryCategories" :key="category.id" type="button" @mousedown.prevent="selectSecondary(category)"><b>{{ category.name }}</b><small>{{ categoryParentName(category) }}</small></button>
          </div>
        </div>
      </label>
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
const primaryCategoryQuery = ref('')
const secondaryCategoryQuery = ref('')
const primaryOpen = ref(false)
const secondaryOpen = ref(false)
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
  props.categories.filter(item => item.parentId && item.kind === categoryDirection.value && (!primaryCategoryId.value || String(item.parentId) === primaryCategoryId.value)),
  recentTransactions.value,
  item => [item.categoryId]))
const filteredPrimaryCategories = computed(() => {
  const query = primaryCategoryQuery.value.trim().toLocaleLowerCase()
  return primaryCategories.value.filter(item => !query || item.name.toLocaleLowerCase().includes(query))
})
const filteredSecondaryCategories = computed(() => {
  const query = secondaryCategoryQuery.value.trim().toLocaleLowerCase()
  return secondaryCategories.value.filter(item => {
    const path = `${categoryParentName(item)} / ${item.name}`.toLocaleLowerCase()
    return !query || item.name.toLocaleLowerCase().includes(query) || path.includes(query)
  })
})
const transferTargets = computed(() => preferredAccounts.value.filter(item => String(item.id) !== String(props.model.accountId)))
watch([() => props.model.categoryId, () => props.categories], syncPrimaryCategory, { immediate: true, deep: true })

function syncPrimaryCategory() {
  const selected = props.categories.find(item => String(item.id) === String(props.model.categoryId || ''))
  primaryCategoryId.value = selected?.parentId ? String(selected.parentId) : ''
  primaryCategoryQuery.value = props.categories.find(item => String(item.id) === primaryCategoryId.value)?.name || ''
  secondaryCategoryQuery.value = selected?.name || ''
}
function handleKindChange() {
  props.model.categoryId = ''
  primaryCategoryId.value = ''
  primaryCategoryQuery.value = ''
  secondaryCategoryQuery.value = ''
  if (props.model.kind !== 'TRANSFER') props.model.targetAccountId = ''
}
function handleAccountChange() {
  if (props.model.targetAccountId === props.model.accountId) props.model.targetAccountId = ''
}
function categoryParentName(category) {
  return props.categories.find(item => String(item.id) === String(category.parentId))?.name || ''
}
function selectPrimary(category) {
  primaryCategoryId.value = String(category.id)
  primaryCategoryQuery.value = category.name
  props.model.categoryId = ''
  secondaryCategoryQuery.value = ''
  primaryOpen.value = false
}
function selectSecondary(category) {
  props.model.categoryId = String(category.id)
  primaryCategoryId.value = String(category.parentId)
  primaryCategoryQuery.value = categoryParentName(category)
  secondaryCategoryQuery.value = category.name
  secondaryOpen.value = false
}
function handlePrimaryInput() {
  const exact = primaryCategories.value.find(item => item.name.trim().toLocaleLowerCase() === primaryCategoryQuery.value.trim().toLocaleLowerCase())
  primaryCategoryId.value = exact ? String(exact.id) : ''
  props.model.categoryId = ''
  secondaryCategoryQuery.value = ''
  primaryOpen.value = true
}
function handleSecondaryInput() {
  const query = secondaryCategoryQuery.value.trim().toLocaleLowerCase()
  const exact = secondaryCategories.value.filter(item => item.name.toLocaleLowerCase() === query || `${categoryParentName(item)} / ${item.name}`.toLocaleLowerCase() === query)
  if (exact.length === 1) selectSecondary(exact[0])
  else props.model.categoryId = ''
  secondaryOpen.value = true
}
function closeMenusSoon() {
  window.setTimeout(() => { primaryOpen.value = false; secondaryOpen.value = false }, 120)
}
</script>

<style scoped>
.transaction-editor-grid { display:grid; grid-template-columns:repeat(2,minmax(0,1fr)); gap:12px }
.transaction-editor-grid label { display:flex; min-width:0; flex-direction:column; gap:5px; color:var(--ink2); font-size:12px }
.transaction-editor-grid input,.transaction-editor-grid select,.transaction-editor-grid textarea { width:100%; min-width:0; max-width:100%; box-sizing:border-box }
.category-combobox { position:relative; min-width:0 }
.category-options { position:absolute; z-index:20; top:calc(100% + 4px); left:0; right:0; max-height:220px; overflow:auto; padding:4px; border:1px solid var(--line2); border-radius:4px; background:var(--card); box-shadow:0 12px 28px rgba(20,23,28,.14) }
.category-options button { display:flex; width:100%; min-height:34px; align-items:center; justify-content:space-between; gap:10px; padding:7px 9px; border:0; border-radius:3px; background:transparent; color:var(--ink2); font-size:12px; text-align:left }
.category-options button:hover { background:var(--accent-soft); color:var(--ink) }
.category-options small { color:var(--muted); font-size:10px }
.transaction-editor-note { grid-column:1/-1 }
@media(max-width:560px){.transaction-editor-grid{grid-template-columns:1fr;gap:9px}.transaction-editor-note{grid-column:auto}}
</style>
