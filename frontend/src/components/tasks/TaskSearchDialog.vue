<template>
  <Teleport to="body">
    <Transition name="tsd-fade">
      <div v-if="open" class="tsd-backdrop" @click.self="close">
        <section class="tsd" role="dialog" aria-modal="true" aria-label="搜索">
          <div class="tsd-search">
            <Search :size="18" class="tsd-search-icon" aria-hidden="true" />
            <input
              ref="inputEl"
              v-model="query"
              class="tsd-input"
              type="text"
              maxlength="200"
              placeholder="搜索"
              aria-label="搜索"
              @keydown.enter.prevent="openFullPage"
              @keydown.esc.prevent="close"
            />
            <button type="button" class="tsd-close" aria-label="关闭搜索" title="关闭搜索" @click="close"><X :size="18" /></button>
          </div>

          <div class="tsd-scopes" role="tablist" aria-label="搜索范围">
            <template v-if="!activeScope">
              <button v-for="scope in SEARCH_SCOPES" :key="scope.value" type="button" role="tab" :aria-selected="false" class="tsd-chip" @click="pickScope(scope.value)">{{ scope.label }}</button>
            </template>
            <template v-else>
              <button type="button" class="tsd-chip is-picked" :aria-label="`取消${activeScopeLabel}范围`" @click="activeScope = ''">
                <Check :size="13" aria-hidden="true" />{{ activeScopeLabel }}<X :size="12" class="tsd-chip-x" aria-hidden="true" />
              </button>
              <template v-if="activeScope === 'TASK'">
                <SearchFilterSelect
                  label="清单"
                  :options="listOptions"
                  :model-value="criteria.listIds"
                  multiple
                  all-label="所有清单"
                  @update:model-value="value => criteria.listIds = value"
                />
                <SearchFilterSelect
                  label="标签"
                  :options="tagOptions"
                  :model-value="criteria.tagIds"
                  multiple
                  all-label="所有标签"
                  @update:model-value="value => criteria.tagIds = value"
                />
                <SearchFilterSelect
                  label="日期"
                  :options="SEARCH_DATE_OPTIONS"
                  :model-value="criteria.date"
                  custom
                  :custom-range="{ start: criteria.start, end: criteria.end }"
                  @update:model-value="value => criteria.date = value"
                  @update:custom-range="value => Object.assign(criteria, value)"
                />
                <SearchFilterSelect label="优先级" :options="SEARCH_PRIORITY_OPTIONS" :model-value="criteria.priority" @update:model-value="value => criteria.priority = value" />
                <SearchFilterSelect label="类型" :options="SEARCH_TYPE_OPTIONS" :model-value="criteria.type" @update:model-value="value => criteria.type = value" />
              </template>
            </template>
          </div>

          <div class="tsd-body">
            <template v-if="!query.trim()">
              <section v-if="history.length" class="tsd-history" aria-label="搜索历史">
                <button v-for="item in history" :key="item" type="button" class="tsd-history-row" @click="applyHistory(item)">
                  <Search :size="15" aria-hidden="true" /><span>{{ item }}</span>
                </button>
                <button type="button" class="tsd-history-clear" @click="clearHistory">清除历史记录</button>
              </section>
              <div v-else class="tsd-empty">
                <div class="tsd-illustration" aria-hidden="true">
                  <span class="tsd-blob"><ClipboardList :size="52" :stroke-width="1.4" /><Sparkles :size="20" class="tsd-spark" /><Pencil :size="18" class="tsd-pen" /></span>
                </div>
                <p>输入关键词搜索任务</p>
              </div>
            </template>
            <template v-else-if="!total">
              <div class="tsd-empty">
                <div class="tsd-illustration" aria-hidden="true">
                  <span class="tsd-blob"><ClipboardList :size="52" :stroke-width="1.4" /><Sparkles :size="20" class="tsd-spark" /><Pencil :size="18" class="tsd-pen" /></span>
                </div>
                <p>没有找到与「{{ query.trim() }}」相关的内容</p>
              </div>
            </template>
            <template v-else>
              <section v-if="visibleTasks.length" class="tsd-group" aria-label="任务">
                <h3>任务 <span>{{ taskResults.length }}</span></h3>
                <button v-for="task in visibleTasks" :key="task.publicId" type="button" class="tsd-row" @click="openTask(task)">
                  <span class="tsd-row-check" :class="{ done: task.status === 'COMPLETED' }" aria-hidden="true"><Check v-if="task.status === 'COMPLETED'" :size="11" /></span>
                  <span class="tsd-row-main">
                    <b :class="{ done: task.status === 'COMPLETED' }"><template v-for="(part, index) in highlightParts(task.title, query)" :key="index"><mark v-if="part.hit">{{ part.text }}</mark><template v-else>{{ part.text }}</template></template></b>
                    <small>{{ taskMeta(task) }}</small>
                  </span>
                  <span class="tsd-row-tail">{{ listName(task.listId) }}</span>
                </button>
              </section>
              <section v-if="visibleHabits.length" class="tsd-group" aria-label="习惯">
                <h3>习惯 <span>{{ habitResults.length }}</span></h3>
                <button v-for="habit in visibleHabits" :key="habit.publicId" type="button" class="tsd-row" @click="openHabit(habit)">
                  <span class="tsd-row-icon" :style="badgeStyle(habit.color)">{{ habit.icon || '✓' }}</span>
                  <span class="tsd-row-main">
                    <b><template v-for="(part, index) in highlightParts(habit.name, query)" :key="index"><mark v-if="part.hit">{{ part.text }}</mark><template v-else>{{ part.text }}</template></template></b>
                    <small>习惯 · {{ habitDone(habit) ? '今天已打卡' : '今天待打卡' }}</small>
                  </span>
                </button>
              </section>
              <section v-if="visibleCountdowns.length" class="tsd-group" aria-label="倒数日">
                <h3>倒数纪念日 <span>{{ countdownResults.length }}</span></h3>
                <button v-for="item in visibleCountdowns" :key="item.publicId" type="button" class="tsd-row" @click="openCountdown(item)">
                  <span class="tsd-row-icon" :style="badgeStyle(item.iconColor)">{{ item.iconValue || '◷' }}</span>
                  <span class="tsd-row-main">
                    <b><template v-for="(part, index) in highlightParts(item.name || item.title, query)" :key="index"><mark v-if="part.hit">{{ part.text }}</mark><template v-else>{{ part.text }}</template></template></b>
                    <small>{{ countdownMeta(item) }}</small>
                  </span>
                </button>
              </section>
              <section v-if="visibleTags.length" class="tsd-group" aria-label="标签">
                <h3>标签 <span>{{ tagResults.length }}</span></h3>
                <button v-for="tag in visibleTags" :key="tag.publicId" type="button" class="tsd-row" @click="openTag(tag)">
                  <span class="tsd-row-icon" :style="badgeStyle(tag.color)"><TagIcon :size="14" /></span>
                  <span class="tsd-row-main"><b>{{ tag.name }}</b><small>标签</small></span>
                </button>
              </section>
              <section v-if="visibleLists.length" class="tsd-group" aria-label="清单">
                <h3>清单 <span>{{ listResults.length }}</span></h3>
                <button v-for="list in visibleLists" :key="list.publicId" type="button" class="tsd-row" @click="openList(list)">
                  <span class="tsd-row-icon" :style="badgeStyle(list.color)"><ListTodo :size="14" /></span>
                  <span class="tsd-row-main"><b>{{ list.name }}</b><small>清单</small></span>
                </button>
              </section>
              <section v-if="visibleFilters.length" class="tsd-group" aria-label="过滤器">
                <h3>过滤器 <span>{{ filterResults.length }}</span></h3>
                <button v-for="filter in visibleFilters" :key="filter.publicId" type="button" class="tsd-row" @click="openFilter(filter)">
                  <span class="tsd-row-icon" :style="badgeStyle('#8b8f98')"><SlidersHorizontal :size="14" /></span>
                  <span class="tsd-row-main"><b>{{ filter.name }}</b><small>过滤器</small></span>
                </button>
              </section>
              <footer class="tsd-more">
                <button type="button" @click="openFullPage">查看更多</button>
              </footer>
            </template>
          </div>
        </section>
      </div>
    </Transition>
  </Teleport>
</template>

<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { Check, ClipboardList, ListTodo, Pencil, Search, SlidersHorizontal, Sparkles, Tag as TagIcon, X } from 'lucide-vue-next'
import SearchFilterSelect from './SearchFilterSelect.vue'
import {
  SEARCH_DATE_OPTIONS, SEARCH_PRIORITY_OPTIONS, SEARCH_SCOPES, SEARCH_TYPE_OPTIONS,
  countdownDays, emptySearchCriteria, highlightParts, matchCountdown, matchHabit, matchOrganizations, matchTask,
  searchCriteriaFromQuery, searchCriteriaToQuery
} from './taskSearch.js'
import { useTasksStore } from '../../stores/tasks.js'
import { apiListTaskFilters } from '../../../packages/api-client/src/index.js'
import { closeTaskSearch, searchDialogOpen, searchDialogSeed } from '../../utils/searchBus.js'

const PAGE_SIZE = 10
const HISTORY_KEY = 'task_search_history'

const router = useRouter()
const store = useTasksStore()

const open = computed(() => searchDialogOpen.value)
const inputEl = ref(null)
const query = ref('')
const activeScope = ref('')
const criteria = reactive(emptySearchCriteria())
const history = ref([])
const filters = ref([])

const activeScopeLabel = computed(() => SEARCH_SCOPES.find(item => item.value === activeScope.value)?.label || '')

const listOptions = computed(() => store.lists
  .filter(item => !item.archived)
  .map(item => ({ value: item.publicId, label: item.name, dot: item.color || undefined })))
const tagOptions = computed(() => (store.tags || []).map(item => ({ value: item.publicId, label: item.name, dot: item.color || undefined })))
const activeLists = computed(() => store.lists.filter(item => !item.archived))
const activeTags = computed(() => store.tags || [])

const searchCriteria = computed(() => ({ ...criteria, q: query.value.trim() }))

/** 任务/习惯/倒数日三类结果；页签非任务时只保留页签对应的对象 */
const taskResults = computed(() => {
  if (activeScope.value && activeScope.value !== 'TASK') return []
  const context = { today: undefined, listsById: new Map(activeLists.value.map(item => [item.publicId, item])), tagsById: new Map(activeTags.value.map(item => [item.publicId, item])) }
  return (store.tasks || []).filter(task => matchTask(task, searchCriteria.value, context))
})
const habitResults = computed(() => (activeScope.value && activeScope.value !== 'TASK'
  ? []
  : (store.habits || []).filter(habit => matchHabit(habit, searchCriteria.value, {})).filter(habit => !habit.archived)))
const countdownResults = computed(() => (activeScope.value && activeScope.value !== 'TASK'
  ? []
  : (store.countdowns || []).filter(item => matchCountdown(item, searchCriteria.value, {}))))
const tagResults = computed(() => (activeScope.value && activeScope.value !== 'TAG' ? [] : matchOrganizations(activeTags.value, query.value)))
const listResults = computed(() => (activeScope.value && activeScope.value !== 'LIST' ? [] : matchOrganizations(activeLists.value, query.value)))
const filterResults = computed(() => (activeScope.value && activeScope.value !== 'FILTER' ? [] : matchOrganizations(filters.value, query.value)))

const total = computed(() => taskResults.value.length + habitResults.value.length + countdownResults.value.length
  + tagResults.value.length + listResults.value.length + filterResults.value.length)

// 搜索结果区域合计只显示 10 条，剩下的交给「查看更多」
const budget = computed(() => {
  let left = PAGE_SIZE
  const take = list => {
    const slice = list.slice(0, Math.max(0, left))
    left -= slice.length
    return slice
  }
  return {
    tasks: take(taskResults.value),
    habits: take(habitResults.value),
    countdowns: take(countdownResults.value),
    tags: take(tagResults.value),
    lists: take(listResults.value),
    filters: take(filterResults.value)
  }
})
const visibleTasks = computed(() => budget.value.tasks)
const visibleHabits = computed(() => budget.value.habits)
const visibleCountdowns = computed(() => budget.value.countdowns)
const visibleTags = computed(() => budget.value.tags)
const visibleLists = computed(() => budget.value.lists)
const visibleFilters = computed(() => budget.value.filters)

watch(open, async value => {
  if (!value) return
  const seed = searchDialogSeed.value
  query.value = String(seed?.q || '')
  activeScope.value = String(seed?.scope || '')
  Object.assign(criteria, emptySearchCriteria(seed?.criteria || {}))
  loadHistory()
  await nextTick()
  inputEl.value?.focus()
  // 筛选器不在 store 里，打开时顺手拉一次
  try {
    filters.value = await apiListTaskFilters()
  } catch {
    filters.value = []
  }
})

function pickScope(value) {
  activeScope.value = value
  nextTick(() => inputEl.value?.focus())
}

function close() {
  closeTaskSearch()
}

function loadHistory() {
  try {
    const raw = JSON.parse(localStorage.getItem(HISTORY_KEY) || '[]')
    history.value = Array.isArray(raw) ? raw.filter(item => typeof item === 'string').slice(0, 8) : []
  } catch {
    history.value = []
  }
}

function pushHistory(value) {
  const text = String(value || '').trim()
  if (!text) return
  history.value = [text, ...history.value.filter(item => item !== text)].slice(0, 8)
  localStorage.setItem(HISTORY_KEY, JSON.stringify(history.value))
}

function clearHistory() {
  history.value = []
  localStorage.removeItem(HISTORY_KEY)
}

function applyHistory(value) {
  query.value = value
  nextTick(() => inputEl.value?.focus())
}

function openFullPage() {
  const text = query.value.trim()
  if (!text) return
  pushHistory(text)
  const next = searchCriteriaToQuery({ ...criteria, q: text })
  close()
  router.push({ path: '/tasks/search', query: next })
}

function openTask(task) {
  pushHistory(query.value)
  const next = searchCriteriaToQuery({ ...criteria, q: query.value.trim() })
  close()
  router.push({ path: '/tasks/search', query: { ...next, open: task.publicId } })
}

function openHabit(habit) {
  pushHistory(query.value)
  close()
  router.push({ path: '/tasks/habits', query: { habit: habit.publicId } })
}

function openCountdown(item) {
  pushHistory(query.value)
  close()
  router.push({ path: '/tasks/countdowns', query: { focus: item.publicId } })
}

function openTag(tag) {
  close()
  router.push(`/tasks/tag/${tag.publicId}`)
}

function openList(list) {
  close()
  router.push(list.systemKey === 'INBOX' ? '/tasks/inbox' : `/tasks/list/${list.publicId}`)
}

function openFilter(filter) {
  close()
  router.push(`/tasks/filter/${filter.publicId}`)
}

function listName(listId) {
  const list = store.lists.find(item => item.publicId === listId)
  return list ? (list.systemKey === 'INBOX' ? '收集箱' : list.name) : ''
}

function taskMeta(task) {
  const parts = []
  if (task.dueAt) {
    const date = new Date(task.dueAt)
    if (!Number.isNaN(date.getTime())) parts.push(new Intl.DateTimeFormat('zh-CN', { month: 'numeric', day: 'numeric' }).format(date))
  }
  if (task.status === 'COMPLETED') parts.push('已完成')
  return parts.join(' · ') || '无日期'
}

function countdownMeta(item) {
  const days = countdownDays(item)
  return days >= 0 ? `还有 ${days} 天` : `已过 ${Math.abs(days)} 天`
}

function habitDone(habit) {
  const today = new Date()
  const key = `${today.getFullYear()}-${String(today.getMonth() + 1).padStart(2, '0')}-${String(today.getDate()).padStart(2, '0')}`
  return (store.habitCheckins || []).some(row => row.habitId === habit.publicId && String(row.date).slice(0, 10) === key && row.status === 'DONE')
}

function badgeStyle(color) {
  if (!color) return undefined
  return { background: `color-mix(in srgb, ${color} 20%, transparent)`, color }
}

function onKeydown(event) {
  if (event.key === 'Escape' && open.value) {
    event.preventDefault()
    close()
  }
}

onMounted(() => {
  loadHistory()
  document.addEventListener('keydown', onKeydown)
})
onBeforeUnmount(() => {
  document.removeEventListener('keydown', onKeydown)
})
</script>

<style scoped>
.tsd-backdrop { position: fixed; z-index: 300; inset: 0; display: flex; justify-content: center; align-items: flex-start; padding: 6vh 16px 16px; background: rgb(10 12 16 / 55%); backdrop-filter: blur(2px); }
.tsd { display: flex; flex-direction: column; width: min(680px, 100%); max-height: min(78vh, 760px); border: 1px solid var(--line); border-radius: 12px; background: var(--card); box-shadow: 0 30px 80px rgb(0 0 0 / 40%); overflow: hidden; }
.tsd-search { display: flex; align-items: center; gap: 10px; padding: 14px 16px 12px; border-bottom: 1px solid color-mix(in srgb, var(--accent) 45%, var(--line)); }
.tsd-search-icon { flex: none; color: var(--muted); }
.tsd-input { flex: 1; min-width: 0; border: 0; background: transparent; color: var(--ink); font: inherit; font-size: 16px; line-height: 1.4; }
.tsd-input:focus, .tsd-input:focus-visible { outline: none; box-shadow: none; }
.tsd-input::placeholder { color: var(--muted); }
.tsd-close { display: grid; width: 28px; height: 28px; flex: none; place-items: center; border: 0; border-radius: 6px; background: transparent; color: var(--muted); cursor: pointer; }
.tsd-close:hover { background: color-mix(in srgb, var(--ink) 8%, transparent); color: var(--ink); }
.tsd-scopes { display: flex; flex-wrap: wrap; align-items: center; gap: 6px; padding: 10px 16px; border-bottom: 1px solid var(--line); }
.tsd-chip { display: inline-flex; align-items: center; gap: 4px; height: 30px; padding: 0 12px; border: 1px solid var(--line2); border-radius: 8px; background: transparent; color: var(--ink2); font: inherit; font-size: 13px; cursor: pointer; }
.tsd-chip:hover { border-color: var(--muted); color: var(--ink); }
.tsd-chip.is-picked { border-color: color-mix(in srgb, var(--accent) 55%, var(--line)); background: color-mix(in srgb, var(--accent) 12%, transparent); color: var(--accent); font-weight: 600; }
.tsd-chip-x { opacity: .7; }
.tsd-body { flex: 1; min-height: 320px; overflow: auto; padding: 6px 8px 10px; }
.tsd-empty { display: grid; place-items: center; gap: 14px; padding: 56px 12px 64px; color: var(--muted); font-size: 14px; }
.tsd-empty p { margin: 0; }
.tsd-illustration { display: grid; place-items: center; }
.tsd-blob { position: relative; display: grid; width: 168px; height: 128px; place-items: center; border-radius: 48% 52% 44% 56% / 58% 46% 54% 42%; background: color-mix(in srgb, var(--ink) 7%, transparent); color: var(--muted); }
.tsd-spark { position: absolute; top: 6px; left: 22px; color: #6aa8e8; }
.tsd-pen { position: absolute; right: 26px; bottom: 12px; color: #6aa8e8; }
.tsd-history { display: grid; gap: 2px; padding: 6px 4px 0; }
.tsd-history-row { display: flex; align-items: center; gap: 10px; min-height: 40px; padding: 0 10px; border: 0; border-radius: 7px; background: transparent; color: var(--ink2); font: inherit; font-size: 14px; text-align: left; cursor: pointer; }
.tsd-history-row:hover { background: color-mix(in srgb, var(--ink) 6%, transparent); color: var(--ink); }
.tsd-history-row span { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.tsd-history-clear { justify-self: center; margin-top: 18px; padding: 7px 14px; border: 0; border-radius: 7px; background: transparent; color: var(--muted); font: inherit; font-size: 13px; cursor: pointer; }
.tsd-history-clear:hover { background: color-mix(in srgb, var(--ink) 6%, transparent); color: var(--ink); }
.tsd-group { display: grid; gap: 1px; padding: 4px 0; }
.tsd-group h3 { display: flex; align-items: center; gap: 6px; margin: 6px 0 2px; padding: 0 10px; color: var(--muted); font-size: 12px; font-weight: 600; letter-spacing: .02em; }
.tsd-group h3 span { color: color-mix(in srgb, var(--muted) 70%, transparent); }
.tsd-row { display: flex; align-items: center; gap: 10px; width: 100%; min-height: 46px; padding: 6px 10px; border: 0; border-radius: 8px; background: transparent; color: var(--ink); font: inherit; text-align: left; cursor: pointer; }
.tsd-row:hover { background: color-mix(in srgb, var(--ink) 6%, transparent); }
.tsd-row-check { display: grid; width: 15px; height: 15px; flex: none; place-items: center; border: 1.5px solid var(--line2); border-radius: 4px; color: #fff; }
.tsd-row-check.done { border-color: var(--accent); background: var(--accent); }
.tsd-row-icon { display: grid; width: 26px; height: 26px; flex: none; place-items: center; border-radius: 50%; background: color-mix(in srgb, var(--ink) 8%, transparent); font-size: 14px; }
.tsd-row-main { display: grid; flex: 1; min-width: 0; gap: 1px; }
.tsd-row-main b { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; font-size: 14px; font-weight: 500; }
.tsd-row-main b.done { color: var(--muted); text-decoration: line-through; }
.tsd-row-main small { overflow: hidden; color: var(--muted); font-size: 11.5px; text-overflow: ellipsis; white-space: nowrap; }
.tsd-row-tail { flex: none; max-width: 120px; overflow: hidden; color: var(--muted); font-size: 11.5px; text-overflow: ellipsis; white-space: nowrap; }
.tsd-row mark { background: color-mix(in srgb, #f3c623 45%, transparent); color: inherit; border-radius: 2px; }
.tsd-more { display: flex; justify-content: center; padding: 8px 0 4px; }
.tsd-more button { padding: 7px 16px; border: 1px solid var(--line2); border-radius: 8px; background: transparent; color: var(--ink2); font: inherit; font-size: 13px; cursor: pointer; }
.tsd-more button:hover { border-color: var(--accent); color: var(--accent); }
.tsd-fade-enter-active, .tsd-fade-leave-active { transition: opacity .16s ease; }
.tsd-fade-enter-from, .tsd-fade-leave-to { opacity: 0; }
@media (max-width: 640px) {
  .tsd-backdrop { padding: 0; }
  .tsd { width: 100%; max-height: 100dvh; height: 100dvh; border-radius: 0; border: 0; }
  .tsd-body { min-height: 0; }
  .tsd-row-tail { display: none; }
}
</style>
