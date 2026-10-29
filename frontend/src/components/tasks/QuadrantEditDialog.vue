<template>
  <Dialog :open="open" title="编辑象限" content-class="quadrant-dialog" @update:open="$emit('update:open', $event)">
    <div class="qe-form">
      <label class="qe-name-row">
        <span class="qe-icon" :style="{ background: draft.color }"><component :is="iconComponent" :size="13" /></span>
        <input v-model="draft.name" maxlength="24" aria-label="象限名称" placeholder="象限名称" />
      </label>

      <div class="qe-row">
        <span class="qe-label">清单</span>
        <div class="qe-field">
          <button type="button" class="qe-trigger" :aria-expanded="panel === 'list'" @click.stop="panel = panel === 'list' ? '' : 'list'">
            <span>{{ listSummary }}</span><ChevronDown :size="14" />
          </button>
          <div v-if="panel === 'list'" class="qe-pop" @click.stop @pointerdown.stop>
            <button type="button" class="qe-pop-all" @click="clearList">所有清单</button>
            <label v-for="list in lists" :key="list.publicId" class="qe-check-row">
              <input type="checkbox" :checked="draft.rule.listIds.includes(list.publicId)" @change="toggleList(list.publicId)" />
              <TaskOrganizationIcon :value="list.icon" :size="14" :style="{ color: list.color || 'var(--ink2)' }" />
              <span>{{ list.name }}</span>
            </label>
          </div>
        </div>
      </div>

      <div class="qe-row">
        <span class="qe-label">标签</span>
        <div class="qe-field">
          <button type="button" class="qe-trigger" :aria-expanded="panel === 'tag'" @click.stop="panel = panel === 'tag' ? '' : 'tag'">
            <span>{{ tagSummary }}</span><ChevronDown :size="14" />
          </button>
          <div v-if="panel === 'tag'" class="qe-pop" @click.stop @pointerdown.stop>
            <button type="button" class="qe-pop-all" @click="clearTag">所有标签</button>
            <label v-for="tag in tags" :key="tag.publicId" class="qe-check-row">
              <input type="checkbox" :checked="draft.rule.tagIds.includes(tag.publicId)" @change="toggleTag(tag.publicId)" />
              <i class="qe-dot" :style="{ background: tag.color }"></i>
              <span>{{ tag.name }}</span>
            </label>
            <p v-if="!tags.length" class="qe-empty">还没有标签</p>
          </div>
        </div>
      </div>

      <div class="qe-row">
        <span class="qe-label">日期</span>
        <div class="qe-field">
          <button type="button" class="qe-trigger" :aria-expanded="panel === 'date'" @click.stop="panel = panel === 'date' ? '' : 'date'">
            <span>{{ dateSummary }}</span><ChevronDown :size="14" />
          </button>
          <div v-if="panel === 'date'" class="qe-pop" @click.stop @pointerdown.stop>
            <button type="button" class="qe-pop-all" @click="draft.rule.dates = []">所有日期</button>
            <label v-for="bucket in DATE_BUCKETS" :key="bucket.value" class="qe-check-row">
              <input type="checkbox" :checked="draft.rule.dates.includes(bucket.value)" @change="toggleDate(bucket.value)" />
              <span>{{ bucket.label }}</span>
            </label>
          </div>
        </div>
      </div>

      <div class="qe-row is-stack">
        <span class="qe-label">优先级</span>
        <div class="qe-options">
          <label class="qe-radio"><input v-model="priorityAll" type="radio" :value="true" />所有</label>
          <label v-for="option in PRIORITY_OPTIONS" :key="option.value" class="qe-check">
            <input type="checkbox" :checked="draft.rule.priorities.includes(option.value)" @change="togglePriority(option.value)" />
            {{ option.label }}
          </label>
        </div>
      </div>

      <div class="qe-row is-stack">
        <span class="qe-label">任务类型</span>
        <div class="qe-options">
          <label v-for="option in ITEM_KIND_OPTIONS" :key="option.value" class="qe-radio">
            <input v-model="draft.rule.itemKind" type="radio" :value="option.value" />{{ option.label }}
          </label>
        </div>
      </div>

      <p v-if="sampleText" class="qe-sample">规则示例：{{ sampleText }}</p>
    </div>

    <template #footer>
      <div class="qe-foot">
        <button type="button" class="qe-link" @click="reset">恢复默认</button>
        <button type="button" class="qe-link" @click="showSample = !showSample">{{ showSample ? '隐藏示例' : '规则示例' }}</button>
        <span class="qe-spacer"></span>
        <button type="button" class="qe-primary" @click="save">保存</button>
        <button type="button" class="qe-ghost" @click="$emit('update:open', false)">取消</button>
      </div>
    </template>
  </Dialog>
</template>

<script setup>
import { computed, onBeforeUnmount, reactive, ref, watch } from 'vue'
import { ChevronDown } from 'lucide-vue-next'
import Dialog from '../ui/Dialog.vue'
import TaskOrganizationIcon from './TaskOrganizationIcon.vue'
import {
  DATE_BUCKETS, ITEM_KIND_OPTIONS, PRIORITY_OPTIONS, describeRule, summarizeRule
} from '../../views/taskQuadrant.js'
import { resolveQuadrantIcon } from '../../views/taskQuadrantIcons.js'

const props = defineProps({
  open: { type: Boolean, default: false },
  quadrant: { type: Object, default: null },
  lists: { type: Array, default: () => [] },
  tags: { type: Array, default: () => [] }
})
const emit = defineEmits(['update:open', 'save', 'reset'])

const panel = ref('')
const showSample = ref(false)
const draft = reactive({
  key: '', name: '', icon: 'alert', color: '#e34d59',
  rule: { listIds: [], tagIds: [], dates: [], priorities: [], itemKind: 'ALL' }
})

watch(() => props.quadrant, value => {
  if (!value) return
  Object.assign(draft, {
    key: value.key,
    name: value.name,
    icon: value.icon,
    color: value.color,
    rule: {
      listIds: [...(value.rule?.listIds || [])],
      tagIds: [...(value.rule?.tagIds || [])],
      dates: [...(value.rule?.dates || [])],
      priorities: [...(value.rule?.priorities || [])],
      itemKind: value.rule?.itemKind || 'ALL'
    }
  })
  panel.value = ''
  showSample.value = false
}, { immediate: true })

// 弹层内的 pointerdown 不能触发关闭，否则点复选框时弹层会先消失（点击落空）
function onDocPointerDown(event) {
  if (event?.target?.closest?.('.qe-pop') || event?.target?.closest?.('.qe-trigger')) return
  panel.value = ''
}
watch(panel, value => {
  document.removeEventListener('pointerdown', onDocPointerDown)
  if (value) document.addEventListener('pointerdown', onDocPointerDown)
}, { flush: 'post' })
onBeforeUnmount(() => document.removeEventListener('pointerdown', onDocPointerDown))

const iconComponent = computed(() => resolveQuadrantIcon(draft.icon))
const summary = computed(() => summarizeRule(draft.rule, { lists: props.lists, tags: props.tags }))
const listSummary = computed(() => summary.value.list)
const tagSummary = computed(() => summary.value.tag)
const dateSummary = computed(() => summary.value.date)
const priorityAll = computed({
  get: () => !draft.rule.priorities.length,
  set: value => { if (value) draft.rule.priorities = [] }
})
const sampleText = computed(() => (showSample.value ? describeRule(draft.rule, { lists: props.lists, tags: props.tags }) : ''))

function toggleList(id) {
  const set = new Set(draft.rule.listIds)
  set.has(id) ? set.delete(id) : set.add(id)
  draft.rule.listIds = [...set]
}
function clearList() { draft.rule.listIds = []; panel.value = '' }
function toggleTag(id) {
  const set = new Set(draft.rule.tagIds)
  set.has(id) ? set.delete(id) : set.add(id)
  draft.rule.tagIds = [...set]
}
function clearTag() { draft.rule.tagIds = []; panel.value = '' }
function toggleDate(value) {
  const set = new Set(draft.rule.dates)
  set.has(value) ? set.delete(value) : set.add(value)
  draft.rule.dates = [...set]
}
function togglePriority(value) {
  const set = new Set(draft.rule.priorities)
  set.has(value) ? set.delete(value) : set.add(value)
  draft.rule.priorities = [...set]
}
function save() {
  const name = draft.name.trim()
  if (!name) return
  emit('save', { ...draft, name, rule: { ...draft.rule, listIds: [...draft.rule.listIds], tagIds: [...draft.rule.tagIds], dates: [...draft.rule.dates], priorities: [...draft.rule.priorities] } })
  emit('update:open', false)
}
function reset() {
  emit('reset', draft.key)
  emit('update:open', false)
}
</script>

<style scoped>
.qe-form { display: grid; gap: 14px; padding: 4px 2px 2px; }
.qe-name-row { display: flex; align-items: center; gap: 8px; padding: 0 10px 0 8px; border: 1.5px solid var(--accent); border-radius: 8px; background: var(--card); }
.qe-name-row input { flex: 1; min-width: 0; min-height: 40px; border: 0; background: transparent; color: var(--ink); font: inherit; font-size: 14px; font-weight: 600; }
.qe-name-row input:focus { outline: none; }
.qe-icon { display: grid; flex: 0 0 auto; place-items: center; width: 20px; height: 20px; border-radius: 50%; color: #fff; }
.qe-row { display: grid; grid-template-columns: 62px minmax(0, 1fr); align-items: center; gap: 10px; }
.qe-row.is-stack { align-items: start; }
.qe-label { color: var(--ink2); font-size: 13px; font-weight: 600; }
.qe-row.is-stack .qe-label { padding-top: 6px; }
.qe-field { position: relative; }
.qe-trigger { display: flex; width: 100%; align-items: center; justify-content: space-between; gap: 8px; min-height: 36px; padding: 0 10px; border: 1px solid var(--line); border-radius: 8px; background: var(--card); color: var(--ink); font: inherit; font-size: 13px; text-align: left; cursor: pointer; }
.qe-trigger:hover { border-color: color-mix(in srgb, var(--accent) 45%, var(--line)); }
.qe-trigger span { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.qe-pop { position: absolute; z-index: 5; top: calc(100% + 4px); left: 0; display: grid; gap: 2px; width: 100%; max-height: 240px; overflow: auto; padding: 6px; border: 1px solid var(--line2); border-radius: 8px; background: var(--card); box-shadow: 0 14px 32px rgb(15 23 42 / 18%); }
.qe-pop-all { min-height: 28px; padding: 0 8px; border: 0; border-radius: 6px; background: transparent; color: var(--accent); font: inherit; font-size: 12.5px; text-align: left; cursor: pointer; }
.qe-check-row { display: flex; align-items: center; gap: 7px; min-height: 28px; padding: 0 8px; border-radius: 6px; color: var(--ink2); font-size: 12.5px; cursor: pointer; }
.qe-check-row:hover { background: var(--paper); color: var(--ink); }
.qe-check-row input { width: 14px; height: 14px; margin: 0; }
.qe-dot { width: 8px; height: 8px; border-radius: 50%; }
.qe-empty { margin: 6px 8px; color: var(--muted); font-size: 12px; }
.qe-options { display: flex; flex-wrap: wrap; align-items: center; gap: 14px; min-height: 36px; }
.qe-radio, .qe-check { display: inline-flex; align-items: center; gap: 6px; color: var(--ink2); font-size: 13px; cursor: pointer; }
.qe-radio input, .qe-check input { width: 15px; height: 15px; margin: 0; }
.qe-sample { margin: 0; padding: 8px 10px; border-radius: 8px; background: var(--paper); color: var(--muted); font-size: 12px; line-height: 1.6; }
.qe-foot { display: flex; align-items: center; gap: 10px; width: 100%; }
.qe-spacer { flex: 1; }
.qe-link { padding: 4px 2px; border: 0; background: transparent; color: var(--accent); font: inherit; font-size: 13px; cursor: pointer; }
.qe-primary { min-width: 84px; min-height: 34px; padding: 0 16px; border: 0; border-radius: 7px; background: var(--accent); color: var(--accent-contrast, #fff); font: inherit; font-size: 13px; font-weight: 600; cursor: pointer; }
.qe-ghost { min-width: 84px; min-height: 34px; padding: 0 16px; border: 1px solid var(--line); border-radius: 7px; background: transparent; color: var(--ink2); font: inherit; font-size: 13px; cursor: pointer; }
</style>
