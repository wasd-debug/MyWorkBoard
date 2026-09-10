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
      <label>一级分类<select :value="model.primaryCategoryId" @change="set('primaryCategoryId', $event.target.value)"><option value="">全部一级分类</option><option v-for="category in primaryCategories" :key="category.id" :value="String(category.id)">{{ category.name }}</option></select></label>
      <label>二级分类<select :value="model.secondaryCategoryId" :disabled="!model.primaryCategoryId" @change="set('secondaryCategoryId', $event.target.value)"><option value="">{{ model.primaryCategoryId ? '全部二级分类' : '请先选择一级分类' }}</option><option v-for="category in secondaryCategories" :key="category.id" :value="String(category.id)">{{ category.name }}</option></select></label>
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
import { computed } from 'vue'
import Button from '../ui/Button.vue'

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
const primaryCategories = computed(() => props.categories.filter(item => !item.parentId))
const secondaryCategories = computed(() => props.categories.filter(item => item.parentId && (!props.model.primaryCategoryId || String(item.parentId) === String(props.model.primaryCategoryId))))

function set(key, value) {
  emit('update', { key, value })
  if (key === 'primaryCategoryId' && value !== props.model.primaryCategoryId) emit('update', { key: 'secondaryCategoryId', value: '' })
}
</script>

<style scoped>
.flow-filters { display:flex; min-width:0; flex-direction:column; gap:14px }
.flow-filter-section { display:flex; min-width:0; flex-direction:column; gap:9px; padding-bottom:14px; border-bottom:1px solid var(--line) }
.flow-filter-grid { display:grid; grid-template-columns:repeat(2,minmax(0,1fr)); gap:9px 12px }
.flow-filter-section:last-of-type { padding-bottom: 0; border-bottom: 0 }
.flow-filter-heading { display: flex; align-items: center; justify-content: space-between; color: var(--ink); font-size: 12px; font-weight: 700 }
.flow-filter-heading button { padding: 0; border: 0; background: transparent; color: var(--accent); font-size: 11px }
.flow-filters label { display:flex; min-width:0; flex-direction:column; gap:5px; color:var(--muted); font-size:11px }
.flow-filters input,.flow-filters select { width:100%; min-width:0; max-width:100%; box-sizing:border-box }
.flow-date-grid { display:grid; grid-template-columns:repeat(2,minmax(0,1fr)); gap:9px }
.flow-date-grid .ui-input { min-width: 0 }
.flow-quick-periods { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 6px }
.flow-quick-periods button { height: 30px; border: 1px solid var(--line2); border-radius: 3px; background: var(--card); color: var(--ink2); font-size: 11px }
.flow-quick-periods button:hover { border-color: var(--accent); color: var(--accent) }
.flow-reset-button { width: 100% }
</style>
