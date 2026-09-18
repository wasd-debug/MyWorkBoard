<template>
  <div class="ledger-category-fields">
    <label>一级分类
      <div class="category-combobox">
        <input
          v-model="primaryQuery"
          class="ui-input"
          autocomplete="off"
          :placeholder="primaryPlaceholder"
          @focus="primaryOpen = true"
          @input="handlePrimaryInput"
          @blur="closeMenusSoon"
        >
        <div v-if="primaryOpen" class="category-options">
          <button v-for="category in filteredParents" :key="category.id" type="button" @mousedown.prevent="selectPrimary(category)">{{ category.name }}</button>
          <span v-if="!filteredParents.length" class="category-empty">没有匹配的一级分类</span>
        </div>
      </div>
    </label>
    <label>二级分类
      <div class="category-combobox">
        <input
          v-model="secondaryQuery"
          class="ui-input"
          autocomplete="off"
          :placeholder="secondaryPlaceholder"
          @focus="secondaryOpen = true"
          @input="handleSecondaryInput"
          @blur="closeMenusSoon"
        >
        <div v-if="secondaryOpen" class="category-options">
          <button v-for="category in filteredChildren" :key="category.id" type="button" @mousedown.prevent="selectSecondary(category)">
            <b>{{ category.name }}</b><small>{{ categoryParentName(category) }}</small>
          </button>
          <span v-if="!filteredChildren.length" class="category-empty">没有匹配的二级分类</span>
        </div>
      </div>
    </label>
  </div>
</template>

<script setup>
import { computed, ref, watch } from 'vue'
import { exactLedgerSecondaryCategory, filterLedgerPrimaryCategories, filterLedgerSecondaryCategories, ledgerCategoryParent } from './ledgerCategoryMatcher'
import { rankLedgerOptions, recentLedgerTransactions } from './ledgerPreferences'

const props = defineProps({
  modelValue: { type: [String, Number], default: '' },
  kind: { type: String, required: true },
  categories: { type: Array, default: () => [] },
  usageTransactions: { type: Array, default: () => [] },
  primaryPlaceholder: { type: String, default: '输入或选择一级分类' },
  secondaryPlaceholder: { type: String, default: '输入二级分类或一级 / 二级' }
})
const emit = defineEmits(['update:modelValue', 'change'])
const primaryId = ref('')
const primaryQuery = ref('')
const secondaryQuery = ref('')
const primaryOpen = ref(false)
const secondaryOpen = ref(false)
const lastEmittedValue = ref(null)
const recentTransactions = computed(() => recentLedgerTransactions(props.usageTransactions))
const orderedParents = computed(() => rankLedgerOptions(
  filterLedgerPrimaryCategories(props.categories, props.kind), recentTransactions.value,
  row => [props.categories.find(category => String(category.id) === String(row.categoryId))?.parentId]
))
const orderedChildren = computed(() => rankLedgerOptions(
  filterLedgerSecondaryCategories(props.categories, props.kind, '', primaryId.value), recentTransactions.value,
  row => [row.categoryId]
))
const filteredParents = computed(() => {
  const allowed = new Set(filterLedgerPrimaryCategories(props.categories, props.kind, primaryQuery.value).map(item => String(item.id)))
  return orderedParents.value.filter(item => allowed.has(String(item.id)))
})
const filteredChildren = computed(() => {
  const allowed = new Set(filterLedgerSecondaryCategories(props.categories, props.kind, secondaryQuery.value, primaryId.value).map(item => String(item.id)))
  return orderedChildren.value.filter(item => allowed.has(String(item.id)))
})

watch(() => props.modelValue, value => {
  if (lastEmittedValue.value !== null && String(value || '') === lastEmittedValue.value) {
    lastEmittedValue.value = null
    return
  }
  syncSelection()
}, { immediate: true })
watch([() => props.categories, () => props.kind], syncSelection, { deep: true })

function syncSelection() {
  const selected = props.categories.find(item => String(item.id) === String(props.modelValue || ''))
  if (!selected?.parentId || selected.kind !== props.kind) {
    primaryId.value = ''
    primaryQuery.value = ''
    secondaryQuery.value = ''
    if (props.modelValue && props.categories.length) emitModel('')
    return
  }
  const parent = ledgerCategoryParent(props.categories, selected)
  primaryId.value = String(selected.parentId)
  primaryQuery.value = parent?.name || ''
  secondaryQuery.value = selected.name || ''
}
function categoryParentName(category) {
  return ledgerCategoryParent(props.categories, category)?.name || ''
}
function selectPrimary(category) {
  primaryId.value = String(category.id)
  primaryQuery.value = category.name
  secondaryQuery.value = ''
  emitModel('')
  primaryOpen.value = false
  secondaryOpen.value = true
}
function selectSecondary(category) {
  const parent = ledgerCategoryParent(props.categories, category)
  primaryId.value = String(category.parentId)
  primaryQuery.value = parent?.name || ''
  secondaryQuery.value = category.name
  emitModel(String(category.id))
  emit('change', category)
  secondaryOpen.value = false
}
function handlePrimaryInput() {
  const query = primaryQuery.value.trim().toLocaleLowerCase()
  const exact = orderedParents.value.find(item => item.name.trim().toLocaleLowerCase() === query)
  primaryId.value = exact ? String(exact.id) : ''
  secondaryQuery.value = ''
  emitModel('')
  primaryOpen.value = true
}
function handleSecondaryInput() {
  const exact = exactLedgerSecondaryCategory(props.categories, props.kind, secondaryQuery.value, primaryId.value)
  if (exact) selectSecondary(exact)
  else emitModel('')
  secondaryOpen.value = true
}
function closeMenusSoon() {
  window.setTimeout(() => { primaryOpen.value = false; secondaryOpen.value = false }, 120)
}
function emitModel(value) {
  lastEmittedValue.value = String(value || '')
  emit('update:modelValue', value)
}
</script>

<style scoped>
.ledger-category-fields{display:contents}.ledger-category-fields label{display:flex;min-width:0;flex-direction:column;gap:5px;color:var(--ink2);font-size:12px}.category-combobox{position:relative;min-width:0}.category-combobox input{width:100%;min-width:0;max-width:100%;box-sizing:border-box}.category-options{position:absolute;z-index:40;top:calc(100% + 4px);left:0;right:0;max-height:220px;overflow:auto;padding:4px;border:1px solid var(--line2);border-radius:4px;background:var(--card);box-shadow:0 12px 28px rgba(20,23,28,.14)}.category-options button{display:flex;width:100%;min-height:34px;align-items:center;justify-content:space-between;gap:10px;padding:7px 9px;border:0;border-radius:3px;background:transparent;color:var(--ink2);font-size:12px;text-align:left;cursor:pointer}.category-options button:hover{background:var(--accent-soft);color:var(--ink)}.category-options small{color:var(--muted);font-size:12px}.category-empty{display:block;padding:9px;color:var(--muted);font-size:12px;text-align:center}
</style>
