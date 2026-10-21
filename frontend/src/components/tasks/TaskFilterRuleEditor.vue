<template>
  <section class="advanced-filter-editor" aria-label="高级筛选条件">
    <div v-if="!conditions.length" class="advanced-filter-empty">
      <select aria-label="筛选条件" value="" @change="selectCondition($event.target.value)"><option value="" disabled>筛选条件</option><option v-for="item in availableOptions" :key="item.value" :value="item.value">{{ item.label }}</option></select>
      <button type="button" class="advanced-filter-empty-logic" disabled>逻辑</button>
      <select aria-label="添加筛选条件" value="" @change="selectCondition($event.target.value)"><option value="" disabled>筛选条件</option><option v-for="item in availableOptions" :key="item.value" :value="item.value">{{ item.label }}</option></select>
      <button type="button" aria-label="添加筛选条件" @click="addCondition"><Plus :size="14" />更多条件</button>
    </div>
    <template v-else>
      <div v-for="(condition, index) in conditions" :key="condition.id" class="advanced-filter-sequence">
        <div v-if="index" class="advanced-filter-join-row">
          <span aria-hidden="true"></span>
          <select :value="joins[index - 1] || 'AND'" :aria-label="`第 ${index} 和 ${index + 1} 个条件的逻辑`" @change="setJoin(index - 1, $event.target.value)">
            <option value="AND">且</option><option value="OR">或</option>
          </select>
          <span aria-hidden="true"></span>
        </div>
        <article class="advanced-filter-card" :class="{ 'is-legacy': condition.type === 'LEGACY' }">
          <template v-if="condition.type === 'LEGACY'">
            <strong>已保存条件</strong><span>此条件来自旧版高级筛选，将原样保留。</span>
          </template>
          <template v-else>
            <header>
              <select :value="condition.type" :aria-label="`第 ${index + 1} 个筛选条件`" @change="changeCondition(index, $event.target.value)"><option v-for="item in optionsFor(index)" :key="item.value" :value="item.value">{{ item.label }}</option></select>
              <button type="button" :aria-label="condition.type === 'LEGACY' ? '删除旧条件' : `删除${labelFor(condition.type)}条件`" @click="removeCondition(index)"><X :size="14" /></button>
            </header>
            <div v-if="condition.type === 'LIST'" class="advanced-filter-logic-row">
              <span>清单的逻辑</span><div class="advanced-filter-segment" role="group" aria-label="清单的逻辑">
                <button type="button" :aria-pressed="condition.logic !== 'NOT'" @click="updateCondition(index, { logic: 'OR' })">或</button>
                <button type="button" :aria-pressed="condition.logic === 'NOT'" @click="updateCondition(index, { logic: 'NOT' })">非</button>
              </div>
            </div>
            <div v-if="condition.type === 'LIST'" class="advanced-filter-choice-list" role="group" aria-label="筛选清单">
              <label class="advanced-filter-choice"><input type="checkbox" :checked="lists.length > 0 && lists.every(list => condition.listIds.includes(list.publicId))" @change="updateCondition(index, { listIds: $event.target.checked ? lists.map(list => list.publicId) : [] })" /><span>所有清单</span></label>
              <template v-for="group in groups" :key="group.publicId">
                <label class="advanced-filter-choice"><input type="checkbox" :checked="groupSelected(condition, group)" @change="toggleGroup(index, group, $event.target.checked)" /><span><Folder :size="14" />{{ group.name }}</span></label>
                <label v-for="list in lists.filter(item => item.groupId === group.publicId)" :key="list.publicId" class="advanced-filter-choice indented"><input type="checkbox" :checked="condition.listIds.includes(list.publicId)" @change="toggleValue(index, 'listIds', list.publicId, $event.target.checked)" /><span>{{ list.name }}</span></label>
              </template>
              <label v-for="list in lists.filter(item => !item.groupId || !groups.some(group => group.publicId === item.groupId))" :key="list.publicId" class="advanced-filter-choice"><input type="checkbox" :checked="condition.listIds.includes(list.publicId)" @change="toggleValue(index, 'listIds', list.publicId, $event.target.checked)" /><span>{{ list.name }}</span></label>
            </div>
            <div v-if="condition.type === 'TAG'" class="advanced-filter-logic-row"><span>标签的逻辑</span><span class="advanced-filter-fixed-logic">且</span></div>
            <div v-if="condition.type === 'TAG'" class="advanced-filter-choice-list" role="group" aria-label="筛选标签">
              <label v-for="tag in tags" :key="tag.publicId" class="advanced-filter-choice"><input type="checkbox" :checked="condition.tagIds.includes(tag.publicId)" @change="toggleValue(index, 'tagIds', tag.publicId, $event.target.checked)" /><span>#{{ tag.name }}</span></label>
            </div>
            <div v-if="condition.type === 'DATE'" class="advanced-filter-logic-row"><span>日期的逻辑</span><div class="advanced-filter-segment" role="group" aria-label="日期的逻辑"><button type="button" :aria-pressed="condition.logic !== 'NOT'" @click="updateCondition(index, { logic: 'OR' })">或</button><button type="button" :aria-pressed="condition.logic === 'NOT'" @click="updateCondition(index, { logic: 'NOT' })">非</button></div></div>
            <div v-if="condition.type === 'DATE'" class="advanced-filter-date-range"><label>从<input type="date" :value="condition.start" aria-label="日期开始" @input="updateCondition(index, { start: $event.target.value })" /></label><span>至</span><label>到<input type="date" :value="condition.end" aria-label="日期结束" @input="updateCondition(index, { end: $event.target.value })" /></label></div>
            <div v-if="condition.type === 'PRIORITY'" class="advanced-filter-logic-row"><span>优先级的逻辑</span><span class="advanced-filter-fixed-logic">或</span></div>
            <div v-if="condition.type === 'PRIORITY'" class="advanced-filter-choice-list inline" role="group" aria-label="筛选优先级"><label v-for="item in priorityOptions" :key="item.value" class="advanced-filter-choice"><input type="checkbox" :checked="condition.priorities.includes(item.value)" @change="toggleValue(index, 'priorities', item.value, $event.target.checked)" /><span>{{ item.label }}</span></label></div>
            <label v-if="condition.type === 'CONTENT'" class="advanced-filter-content-input"><span>包含关键词</span><input :value="condition.keyword" maxlength="500" placeholder="输入关键词" aria-label="内容包含关键词" @input="updateCondition(index, { keyword: $event.target.value })" /></label>
            <div v-if="condition.type === 'TASK_TYPE'" class="advanced-filter-logic-row"><span>任务类型的逻辑</span><span class="advanced-filter-fixed-logic">或</span></div>
            <div v-if="condition.type === 'TASK_TYPE'" class="advanced-filter-choice-list inline" role="group" aria-label="筛选任务类型"><label class="advanced-filter-choice"><input type="checkbox" :checked="condition.taskTypes.includes('TASK')" @change="toggleValue(index, 'taskTypes', 'TASK', $event.target.checked)" /><span>任务</span></label><label class="advanced-filter-choice"><input type="checkbox" :checked="condition.taskTypes.includes('NOTE')" @change="toggleValue(index, 'taskTypes', 'NOTE', $event.target.checked)" /><span>笔记</span></label></div>
          </template>
        </article>
      </div>
      <div class="advanced-filter-more"><button type="button" aria-label="添加筛选条件" @click="addCondition"><Plus :size="14" />更多条件</button></div>
    </template>
    <div v-if="menuOpen" class="advanced-filter-picker-backdrop" @click="menuOpen = false"><div class="advanced-filter-picker" role="menu" aria-label="选择筛选条件" @click.stop><button v-for="item in availableOptions" :key="item.value" type="button" role="menuitem" @click="selectCondition(item.value)">{{ item.label }}</button></div></div>
  </section>
</template>

<script setup>
import { computed, ref } from 'vue'
import { Folder, Plus, X } from 'lucide-vue-next'
import { ADVANCED_FILTER_OPTIONS, newAdvancedFilterCondition } from '../../views/taskAdvancedFilter.js'

defineOptions({ name: 'TaskFilterRuleEditor' })
const props = defineProps({ modelValue: { type: Object, required: true }, lists: { type: Array, default: () => [] }, groups: { type: Array, default: () => [] }, tags: { type: Array, default: () => [] } })
const emit = defineEmits(['update:modelValue'])
const menuOpen = ref(false)
const conditions = computed(() => Array.isArray(props.modelValue.conditions) ? props.modelValue.conditions : [])
const joins = computed(() => Array.isArray(props.modelValue.joins) ? props.modelValue.joins : [])
const availableOptions = computed(() => ADVANCED_FILTER_OPTIONS.filter(item => item.value !== 'LIST' || !conditions.value.some(condition => condition.type === 'LIST')))
const priorityOptions = [{ value: 'HIGH', label: '高' }, { value: 'MEDIUM', label: '中' }, { value: 'LOW', label: '低' }, { value: 'NONE', label: '无' }]
function labelFor(type) { return ADVANCED_FILTER_OPTIONS.find(item => item.value === type)?.label || '筛选条件' }
function optionsFor(index) { const current = conditions.value[index]; return ADVANCED_FILTER_OPTIONS.filter(item => item.value !== 'LIST' || current.type === 'LIST' || !conditions.value.some((condition, conditionIndex) => conditionIndex !== index && condition.type === 'LIST')) }
function publish(nextConditions, nextJoins = joins.value) { emit('update:modelValue', { conditions: nextConditions, joins: nextJoins }) }
function addCondition() { if (availableOptions.value.length) menuOpen.value = true }
function selectCondition(type) { const next = [...conditions.value, newAdvancedFilterCondition(type)]; const nextJoins = next.length > 1 ? [...joins.value, 'AND'] : []; menuOpen.value = false; publish(next, nextJoins) }
function changeCondition(index, type) { const next = conditions.value.map((condition, conditionIndex) => conditionIndex === index ? newAdvancedFilterCondition(type) : condition); publish(next) }
function setJoin(index, value) { const next = [...joins.value]; next[index] = value; publish(conditions.value, next) }
function updateCondition(index, patch) { const next = conditions.value.map((item, itemIndex) => itemIndex === index ? { ...item, ...patch } : item); publish(next) }
function toggleValue(index, key, value, checked) { const current = conditions.value[index][key] || []; const values = checked ? [...new Set([...current, value])] : current.filter(item => item !== value); updateCondition(index, { [key]: values }) }
function removeCondition(index) { const next = conditions.value.filter((_, itemIndex) => itemIndex !== index); const nextJoins = joins.value.filter((_, joinIndex) => joinIndex !== Math.max(0, index - 1)); publish(next, nextJoins) }
function groupSelected(condition, group) { const ids = props.lists.filter(item => item.groupId === group.publicId).map(item => item.publicId); return ids.length > 0 && ids.every(id => condition.listIds.includes(id)) }
function toggleGroup(index, group, checked) { const ids = props.lists.filter(item => item.groupId === group.publicId).map(item => item.publicId); const current = conditions.value[index].listIds; const values = checked ? [...new Set([...current, ...ids])] : current.filter(id => !ids.includes(id)); updateCondition(index, { listIds: values }) }
</script>

<style scoped>
.advanced-filter-editor { display: grid; gap: 12px; min-width: 0; }
.advanced-filter-empty { display: flex; flex-wrap: wrap; align-items: center; gap: 8px; min-height: 40px; }
.advanced-filter-empty > select, .advanced-filter-empty-logic { min-width: 118px; min-height: 38px; padding: 6px 9px; border: 1px solid var(--line); border-radius: 6px; background: var(--card); color: var(--ink2); font: inherit; }
.advanced-filter-empty-logic { min-width: 72px; border-style: dashed; }
.advanced-filter-more { display: flex; justify-content: flex-start; }
.advanced-filter-more button, .advanced-filter-empty button { display: inline-flex; min-height: 34px; align-items: center; gap: 5px; padding: 4px 2px; border: 0; background: transparent; color: var(--accent); font: inherit; cursor: pointer; }
.advanced-filter-sequence { display: grid; gap: 8px; }
.advanced-filter-join-row { display: grid; grid-template-columns: 1fr 90px 1fr; align-items: center; gap: 8px; }
.advanced-filter-join-row > span { height: 1px; background: var(--line); }
.advanced-filter-join-row select, .advanced-filter-segment { min-height: 32px; padding: 3px; border: 1px dashed var(--line); border-radius: 6px; background: var(--card); color: var(--ink2); text-align: center; }
.advanced-filter-card { display: grid; gap: 10px; padding: 12px; border: 1px solid var(--line); border-radius: 7px; background: var(--card); }
.advanced-filter-card > header { display: flex; align-items: center; justify-content: space-between; color: var(--ink); }
.advanced-filter-card > header select { flex: 1; min-width: 0; min-height: 36px; padding: 5px 9px; border: 1px solid var(--line); border-radius: 5px; background: var(--card); color: var(--ink); font: inherit; }
.advanced-filter-card > header button { display: grid; width: 28px; height: 28px; place-items: center; border: 0; border-radius: 5px; background: transparent; color: var(--muted); cursor: pointer; }
.advanced-filter-card > header button:hover { background: var(--paper); color: var(--ink); }
.advanced-filter-card.is-legacy { grid-template-columns: auto 1fr; align-items: center; color: var(--muted); font-size: 12px; }
.advanced-filter-logic-row { display: flex; align-items: center; justify-content: space-between; gap: 10px; color: var(--ink2); font-size: 12px; }
.advanced-filter-segment { display: inline-flex; overflow: hidden; border-style: solid; }
.advanced-filter-segment button { min-width: 38px; border: 0; border-radius: 4px; background: transparent; color: var(--muted); cursor: pointer; }
.advanced-filter-segment button[aria-pressed="true"] { background: var(--accent); color: white; }
.advanced-filter-fixed-logic { min-width: 38px; padding: 5px 10px; border-radius: 5px; background: var(--accent-soft); color: var(--accent); text-align: center; }
.advanced-filter-choice-list { display: grid; gap: 2px; max-height: 210px; overflow: auto; padding: 5px 3px; }
.advanced-filter-choice-list.inline { display: flex; flex-wrap: wrap; gap: 10px 16px; }
.advanced-filter-choice { display: flex; min-height: 34px; align-items: center; gap: 9px; padding: 4px 8px; border-radius: 5px; color: var(--ink2); cursor: pointer; }
.advanced-filter-choice:hover { background: var(--paper); }
.advanced-filter-choice input { width: 16px; height: 16px; margin: 0; accent-color: var(--accent); }
.advanced-filter-group-name { padding: 7px 8px 2px; color: var(--muted); font-size: 11px; }
.advanced-filter-choice.indented { padding-left: 24px; }
.advanced-filter-date-range { display: grid; grid-template-columns: minmax(0, 1fr) 20px minmax(0, 1fr); align-items: end; gap: 6px; }
.advanced-filter-date-range label, .advanced-filter-content-input { display: grid; gap: 6px; min-width: 0; color: var(--ink2); font-size: 12px; }
.advanced-filter-date-range input, .advanced-filter-content-input input { width: 100%; min-width: 0; min-height: 36px; padding: 6px 9px; border: 1px solid var(--line); border-radius: 5px; background: var(--card); color: var(--ink); font: inherit; }
.advanced-filter-date-range > span { padding-bottom: 10px; color: var(--muted); text-align: center; }
.advanced-filter-picker-backdrop { position: fixed; z-index: 120; inset: 0; display: flex; align-items: flex-start; justify-content: center; padding: 24px 16px; background: transparent; }
.advanced-filter-picker { display: grid; width: min(320px, calc(100vw - 32px)); max-height: min(65vh, 480px); overflow: auto; padding: 6px; border: 1px solid var(--line); border-radius: 7px; background: var(--card); box-shadow: 0 12px 32px rgb(15 23 42 / 20%); }
.advanced-filter-picker button { min-height: 42px; padding: 7px 12px; border: 0; border-radius: 5px; background: transparent; color: var(--ink); text-align: left; font: inherit; cursor: pointer; }
.advanced-filter-picker button:hover { background: var(--paper); }
@media (max-width: 600px) { .advanced-filter-date-range { grid-template-columns: 1fr; } .advanced-filter-date-range > span { padding: 0; } }
</style>
