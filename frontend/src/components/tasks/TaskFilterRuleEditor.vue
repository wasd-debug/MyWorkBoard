<template>
  <section class="rule-group" :class="{ nested: depth > 0 }" :aria-label="depth ? '嵌套条件组' : '高级条件树'">
    <header>
      <strong>{{ depth ? '条件组' : '条件树' }}</strong>
      <select :value="modelValue.op" aria-label="条件组合方式" @change="updateNode({ op: $event.target.value })">
        <option value="AND">同时满足（AND）</option><option value="OR">满足任一（OR）</option>
      </select>
      <button v-if="depth" type="button" aria-label="删除条件组" @click="$emit('remove')"><X :size="14" /></button>
    </header>
    <div class="rule-children">
      <template v-for="(child, index) in modelValue.children" :key="child.id || index">
        <TaskFilterRuleEditor v-if="child.op" :model-value="child" :depth="depth + 1" :lists="lists" :tags="tags" :node-count="nodeCount" @update:model-value="updateChild(index, $event)" @remove="removeChild(index)" />
        <div v-else class="rule-leaf">
          <select :value="child.field" aria-label="筛选字段" @change="changeField(index, $event.target.value)"><option v-for="field in fields" :key="field.value" :value="field.value">{{ field.label }}</option></select>
          <select :value="child.operator" aria-label="筛选操作符" @change="updateLeaf(index, { operator: $event.target.value })"><option v-for="operator in operatorsForRule(child.field)" :key="operator.value" :value="operator.value">{{ operator.label }}</option></select>
          <fieldset v-if="child.operator === 'IN'" class="rule-values"><label v-for="option in valuesForRule(child.field)" :key="option.value"><input type="checkbox" :checked="(child.value || []).includes(option.value)" @change="toggleInValue(index, option.value, $event.target.checked)" />{{ option.label }}</label></fieldset>
          <select v-else-if="['status','priority','listId','tagId'].includes(child.field)" :value="child.value" :disabled="['IS_SET','IS_NOT_SET'].includes(child.operator)" aria-label="筛选值" @change="updateLeaf(index, { value: $event.target.value })"><option v-for="option in valuesForRule(child.field)" :key="option.value" :value="option.value">{{ option.label }}</option></select>
          <input v-else-if="['IS_SET','IS_NOT_SET'].includes(child.operator)" disabled aria-label="无需填写值" value="无需填写" />
          <input v-else-if="child.field === 'hasDueDate'" type="checkbox" :checked="Boolean(child.value)" aria-label="是否有截止日期" @change="updateLeaf(index, { value: $event.target.checked })" />
          <input v-else-if="child.field === 'dueAt'" type="date" :value="child.value" aria-label="截止日期" @input="updateLeaf(index, { value: $event.target.value })" />
          <input v-else maxlength="500" :value="child.value" aria-label="筛选值" @input="updateLeaf(index, { value: $event.target.value })" />
          <button type="button" aria-label="删除条件" @click="removeChild(index)"><X :size="14" /></button>
        </div>
      </template>
    </div>
    <footer>
      <button type="button" :disabled="modelValue.children.length >= 20 || nodeCount >= 60" @click="addLeaf"><Plus :size="14" />添加条件</button>
      <button type="button" :disabled="depth >= 4 || modelValue.children.length >= 20 || nodeCount + 2 > 60" @click="addGroup"><ListPlus :size="14" />添加子组</button>
    </footer>
  </section>
</template>

<script setup>
import { X, Plus, ListPlus } from 'lucide-vue-next'
import { FILTER_RULE_FIELDS as fields, countFilterRuleNodes, operatorsForRule } from '../../views/taskFilterRules.js'

defineOptions({ name: 'TaskFilterRuleEditor' })
const props = defineProps({ modelValue: { type: Object, required: true }, depth: { type: Number, default: 0 }, lists: { type: Array, default: () => [] }, tags: { type: Array, default: () => [] }, nodeCount: { type: Number, default: 1 } })
const emit = defineEmits(['update:modelValue', 'remove'])
function cloneNode() { return JSON.parse(JSON.stringify(props.modelValue)) }
function updateNode(patch) { emit('update:modelValue', { ...cloneNode(), ...patch }) }
function updateChild(index, child) { const next = cloneNode(); next.children[index] = child; emit('update:modelValue', next) }
function updateLeaf(index, patch) { updateChild(index, { ...props.modelValue.children[index], ...patch }) }
function changeField(index, field) { updateLeaf(index, { field, operator: operatorsForRule(field)[0].value, value: field === 'hasDueDate' ? false : '' }) }
function removeChild(index) { const next = cloneNode(); next.children.splice(index, 1); emit('update:modelValue', next) }
function addLeaf() { updateNode({ children: [...props.modelValue.children, { id: crypto.randomUUID?.() || `${Date.now()}-${Math.random()}`, field: 'title', operator: 'CONTAINS', value: '' }] }) }
function addGroup() { updateNode({ children: [...props.modelValue.children, { id: crypto.randomUUID?.() || `${Date.now()}-${Math.random()}`, op: 'AND', children: [] }] }) }
function toggleInValue(index, value, checked) { const current = props.modelValue.children[index].value || []; updateLeaf(index, { value: checked ? [...new Set([...current, value])] : current.filter(item => item !== value) }) }
function valuesForRule(field) {
  if (field === 'status') return [{ value: 'OPEN', label: '待完成' }, { value: 'COMPLETED', label: '已完成' }, { value: 'ABANDONED', label: '已放弃' }]
  if (field === 'priority') return [{ value: 'NONE', label: '无优先级' }, { value: 'LOW', label: '低' }, { value: 'MEDIUM', label: '中' }, { value: 'HIGH', label: '高' }]
  if (field === 'listId') return props.lists.map(item => ({ value: item.publicId, label: item.name }))
  if (field === 'tagId') return props.tags.map(item => ({ value: item.publicId, label: `#${item.name}` }))
  return []
}
</script>

<style scoped>
.rule-group { display: grid; gap: 8px; min-width: 0; padding: 9px; border: 1px solid var(--line); border-radius: 6px; }
.rule-group.nested { margin-left: 10px; background: color-mix(in srgb, var(--paper) 55%, transparent); }
.rule-group header, .rule-group footer, .rule-leaf { display: flex; align-items: center; gap: 6px; min-width: 0; }
.rule-group header strong { flex: 1; font-size: 12px; }
.rule-leaf select, .rule-leaf input:not([type=checkbox]), .rule-group header select { min-width: 0; width: 100%; height: 34px; border: 1px solid var(--line); border-radius: 5px; background: var(--card); color: var(--ink); padding: 4px 7px; font: inherit; }
.rule-leaf input[type=checkbox] { width: 18px; height: 18px; flex: none; }
.rule-children { display: grid; gap: 7px; }
.rule-group button, .rule-leaf button { display: grid; flex: none; width: 30px; height: 30px; place-items: center; border: 0; border-radius: 5px; background: transparent; color: var(--ink2); cursor: pointer; }
.rule-group button:hover, .rule-leaf button:hover { background: var(--paper); }
.rule-group footer button { display: inline-flex; width: auto; padding: 0 8px; gap: 5px; border: 1px solid var(--line); }
.rule-values { display: grid; max-height: 110px; min-width: 120px; gap: 3px; overflow: auto; padding: 5px; border: 1px solid var(--line); border-radius: 5px; }
.rule-values label { display: flex; align-items: center; gap: 5px; font-size: 11px; }
@media (max-width: 700px) { .rule-leaf { flex-wrap: wrap; } .rule-leaf select { flex: 1 1 42%; } .rule-values { flex: 1 1 100%; } }
</style>
