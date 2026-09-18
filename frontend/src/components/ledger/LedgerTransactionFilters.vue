<template>
  <div class="flow-filters">
    <div class="flow-filter-section">
      <div class="flow-filter-heading"><span>日期范围</span><button type="button" @click="emit('reset-period')">全部</button></div>
      <div class="flow-date-grid">
        <label>开始<input :value="model.from" class="ui-input" type="date" @input="set('from', $event.target.value)"></label>
        <label>结束<input :value="model.to" class="ui-input" type="date" @input="set('to', $event.target.value)"></label>
      </div>
      <div class="flow-quick-periods">
        <button type="button" @click="emit('quick-period', 'month')">本月</button>
        <button type="button" @click="emit('quick-period', 'year')">本年</button>
      </div>
    </div>

    <div class="flow-filter-section flow-filter-grid">
      <label>流水类型<select :value="model.kind" @change="set('kind', $event.target.value)"><option value="">全部类型</option><option v-for="item in kinds" :key="item.value" :value="item.value">{{ item.label }}</option></select></label>
      <label>账户<select :value="model.accountId" @change="set('accountId', $event.target.value)"><option value="">全部账户</option><option v-for="account in accounts" :key="account.id" :value="String(account.id)">{{ account.name }}</option></select></label>
      <label>一级分类<div class="filter-combobox"><input v-model="primaryQuery" class="ui-input" autocomplete="off" placeholder="输入一级分类，留空为全部" @focus="primaryOpen=true" @input="handlePrimaryInput" @blur="closeMenusSoon"><div v-if="primaryOpen" class="filter-options"><button v-for="category in filteredPrimaryCategories" :key="category.id" type="button" @mousedown.prevent="selectPrimary(category)">{{category.name}}</button><span v-if="!filteredPrimaryCategories.length">没有匹配分类</span></div></div></label>
      <label>二级分类<div class="filter-combobox"><input v-model="secondaryQuery" class="ui-input" autocomplete="off" placeholder="输入二级分类或一级 / 二级" @focus="secondaryOpen=true" @input="handleSecondaryInput" @blur="closeMenusSoon"><div v-if="secondaryOpen" class="filter-options"><button v-for="category in filteredSecondaryCategories" :key="category.id" type="button" @mousedown.prevent="selectSecondary(category)"><b>{{category.name}}</b><small>{{categoryParentName(category)}}</small></button><span v-if="!filteredSecondaryCategories.length">没有匹配分类</span></div></div></label>
    </div>

    <div class="flow-filter-section flow-filter-grid">
      <label>商家<select :value="model.payee" @change="set('payee', $event.target.value)"><option value="">全部商家</option><option v-for="value in merchantOptions" :key="value" :value="value">{{ value }}</option></select></label>
      <label>成员<select :value="model.member" @change="set('member', $event.target.value)"><option value="">全部成员</option><option v-for="value in memberOptions" :key="value" :value="value">{{ value }}</option></select></label>
      <label>项目<select :value="model.project" @change="set('project', $event.target.value)"><option value="">全部项目</option><option v-for="value in projectOptions" :key="value" :value="value">{{ value }}</option></select></label>
      <label>备注关键词<input :value="model.note" class="ui-input" placeholder="搜索备注" @input="set('note', $event.target.value)"></label>
    </div>

    <Button class="flow-reset-button" size="sm" variant="ghost" type="button" @click="emit('reset')">清除筛选</Button>
  </div>
</template>

<script setup>
import { computed, ref, watch } from 'vue'
import Button from '../ui/Button.vue'
import { exactLedgerSecondaryCategory, filterLedgerPrimaryCategories, filterLedgerSecondaryCategories, ledgerCategoryParent } from './ledgerCategoryMatcher'

const props = defineProps({
  model: { type: Object, required: true },
  accounts: { type: Array, default: () => [] },
  categories: { type: Array, default: () => [] },
  kinds: { type: Array, default: () => [] },
  merchantOptions: { type: Array, default: () => [] },
  memberOptions: { type: Array, default: () => [] },
  projectOptions: { type: Array, default: () => [] }
})
const emit = defineEmits(['update', 'reset', 'reset-period', 'quick-period'])
const primaryQuery = ref('')
const secondaryQuery = ref('')
const primaryOpen = ref(false)
const secondaryOpen = ref(false)
const filteredPrimaryCategories = computed(() => props.categories.filter(item => !item.parentId && (!primaryQuery.value.trim() || item.name.toLocaleLowerCase().includes(primaryQuery.value.trim().toLocaleLowerCase()))))
const filteredSecondaryCategories = computed(() => filterLedgerSecondaryCategories(props.categories, '', secondaryQuery.value, props.model.primaryCategoryId).filter(item => item.kind))

watch([() => props.model.primaryCategoryId, () => props.model.secondaryCategoryId, () => props.categories], () => {
  if (!primaryOpen.value && !secondaryOpen.value) syncQueries()
}, { immediate: true, deep: true })

function set(key, value) {
  emit('update', { key, value })
  if (key === 'primaryCategoryId' && value !== props.model.primaryCategoryId) emit('update', { key: 'secondaryCategoryId', value: '' })
}
function syncQueries() {
  const primary = props.categories.find(item => String(item.id) === String(props.model.primaryCategoryId || ''))
  const secondary = props.categories.find(item => String(item.id) === String(props.model.secondaryCategoryId || ''))
  primaryQuery.value = primary?.name || ''
  secondaryQuery.value = secondary?.name || ''
}
function categoryParentName(category) { return ledgerCategoryParent(props.categories, category)?.name || '' }
function selectPrimary(category) {
  primaryQuery.value = category.name
  secondaryQuery.value = ''
  set('primaryCategoryId', String(category.id))
  primaryOpen.value = false
  secondaryOpen.value = true
}
function selectSecondary(category) {
  const parent = ledgerCategoryParent(props.categories, category)
  emit('update', { key: 'primaryCategoryId', value: String(category.parentId) })
  emit('update', { key: 'secondaryCategoryId', value: String(category.id) })
  primaryQuery.value = parent?.name || ''
  secondaryQuery.value = category.name
  secondaryOpen.value = false
}
function handlePrimaryInput() {
  const query = primaryQuery.value.trim().toLocaleLowerCase()
  const exact = filterLedgerPrimaryCategories(props.categories, 'EXPENSE', primaryQuery.value)
    .concat(filterLedgerPrimaryCategories(props.categories, 'INCOME', primaryQuery.value))
    .find(item => item.name.trim().toLocaleLowerCase() === query)
  emit('update', { key: 'primaryCategoryId', value: exact ? String(exact.id) : '' })
  emit('update', { key: 'secondaryCategoryId', value: '' })
  secondaryQuery.value = ''
  primaryOpen.value = true
}
function handleSecondaryInput() {
  const kinds = ['EXPENSE', 'INCOME']
  const matches = kinds.map(kind => exactLedgerSecondaryCategory(props.categories, kind, secondaryQuery.value, props.model.primaryCategoryId)).filter(Boolean)
  if (matches.length === 1) selectSecondary(matches[0])
  else emit('update', { key: 'secondaryCategoryId', value: '' })
  secondaryOpen.value = true
}
function closeMenusSoon() { window.setTimeout(() => { primaryOpen.value=false; secondaryOpen.value=false }, 120) }
</script>

<style scoped>
.flow-filters { display:flex; min-width:0; flex-direction:column; gap:14px }
.flow-filter-section { display:flex; min-width:0; flex-direction:column; gap:9px; padding-bottom:14px; border-bottom:1px solid var(--line) }
.flow-filter-grid { display:grid; grid-template-columns:repeat(2,minmax(0,1fr)); gap:9px 12px }
.flow-filter-section:last-of-type { padding-bottom: 0; border-bottom: 0 }
.flow-filter-heading { display: flex; align-items: center; justify-content: space-between; color: var(--ink); font-size: 12px; font-weight: 700 }
.flow-filter-heading button { padding: 0; border: 0; background: transparent; color: var(--accent); font-size:12px }
.flow-filters label { display:flex; min-width:0; flex-direction:column; gap:5px; color:var(--muted); font-size:12px }
.flow-filters input,.flow-filters select { width:100%; min-width:0; max-width:100%; box-sizing:border-box }
.flow-date-grid { display:grid; grid-template-columns:repeat(2,minmax(0,1fr)); gap:9px }
.flow-date-grid .ui-input { min-width: 0 }
.flow-quick-periods { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 6px }
.flow-quick-periods button { height: 30px; border: 1px solid var(--line2); border-radius: 3px; background: var(--card); color: var(--ink2); font-size:12px }
.flow-quick-periods button:hover { border-color: var(--accent); color: var(--accent) }
.flow-reset-button { width: 100% }
.filter-combobox{position:relative;min-width:0}.filter-options{position:absolute;z-index:40;top:calc(100% + 4px);left:0;right:0;max-height:210px;overflow:auto;padding:4px;border:1px solid var(--line2);border-radius:4px;background:var(--card);box-shadow:0 12px 28px rgba(20,23,28,.14)}.filter-options button{display:flex;width:100%;min-height:32px;align-items:center;justify-content:space-between;gap:8px;padding:6px 8px;border:0;border-radius:3px;background:transparent;color:var(--ink2);font-size:12px;text-align:left;cursor:pointer}.filter-options button:hover{background:var(--accent-soft);color:var(--ink)}.filter-options small{color:var(--muted);font-size:12px}.filter-options>span{display:block;padding:8px;color:var(--muted);font-size:12px;text-align:center}
</style>
