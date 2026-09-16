<template>
  <section class="ledger-flow-page">
    <LoadingOverlay :open="saving || deleting" :label="saving ? '正在保存流水…' : '正在删除流水…'" />
    <div class="page-heading flow-heading">
      <div><div class="label">PERSONAL FINANCE / TRANSACTIONS</div><h1>流水 <span class="heading-slash">// DETAILS</span></h1><p class="muted">{{ rangeLabel }} · {{ totalCount }} 笔</p></div>
      <div class="flow-heading-actions">
        <span class="flow-filter-action"><LedgerActionIcon action="filter" label="筛选流水" @click="openFilters" /><i v-if="activeFilterCount" :aria-label="`已设置 ${activeFilterCount} 项筛选`">{{ activeFilterCount }}</i></span>
        <LedgerActionIcon action="layout" label="设置显示列" @click="columnsOpen=true" />
        <LedgerActionIcon action="add" label="记一笔" @click="openCreate" />
      </div>
    </div>

    <div class="flow-workspace">
      <aside class="flow-sidebar">
        <Card class="flow-summary-card">
          <template #header><div class="flow-card-kicker">当前筛选</div><div class="flow-result-count"><strong>{{ totalCount }}</strong><span>笔流水</span></div></template>
          <div class="flow-summary-list">
            <div><span>总收入</span><b class="flow-income">+¥{{ money(summary.income) }}</b></div>
            <div><span>总支出</span><b class="flow-expense">−¥{{ money(summary.expense) }}</b></div>
            <div><span>资金净额</span><b :class="summary.net>=0?'flow-income':'flow-expense'">{{ summary.net>=0?'+':'−' }}¥{{ money(Math.abs(summary.net)) }}</b></div>
          </div>
          <div class="flow-kind-summary">
            <button v-for="item in kindSummary" :key="item.value" type="button" :class="{active:filters.kind===item.value}" @click="toggleKind(item.value)"><span><i :class="`tone-${item.tone}`"></i>{{ item.label }}</span><b>{{ item.count }}</b></button>
          </div>
        </Card>
      </aside>

      <div class="flow-main">
        <Card class="flow-table-card">
          <template #header>
            <div class="flow-table-head">
              <div><div class="flow-card-kicker">TRANSACTION LEDGER</div><h2>流水明细</h2></div>
              <div class="flow-table-tools"><div class="flow-view-switch" role="group" aria-label="流水展示方式"><button type="button" :class="{active:displayMode==='flat'}" @click="displayMode='flat'">平铺</button><button type="button" :class="{active:displayMode==='day'}" @click="displayMode='day'">按天</button></div><div class="flow-search"><input v-model="searchText" class="ui-input" placeholder="搜索商家、项目、成员或备注"><button v-if="searchText" type="button" aria-label="清空搜索" @click="searchText=''">×</button></div></div>
            </div>
          </template>

          <div v-if="loading && !sortedTransactions.length" class="flow-loading">正在读取流水…</div>
          <Empty v-else-if="!sortedTransactions.length" description="没有符合条件的流水" />
          <template v-else>
            <div class="flow-desktop-table">
              <Table>
                <colgroup><col v-for="column in visibleColumns" :key="column.key" :style="{width:`${columnWidths[column.key]}px`}"><col style="width:108px"></colgroup>
                <thead><tr>
                  <th v-for="column in visibleColumns" :key="column.key" :class="columnClass(column)" :style="columnStickyStyle(column)">
                    <button class="flow-sort-button" type="button" @click="sortBy(column)"><span>{{ column.label }}</span><SortUp v-if="sort.key===column.sortKey&&sort.direction==='asc'" aria-hidden="true" /><SortDown v-else-if="sort.key===column.sortKey" aria-hidden="true" /></button>
                    <i class="flow-resize-handle" @pointerdown.stop.prevent="startResize($event,column.key)"></i>
                  </th>
                  <th class="flow-actions-column fixed-actions">操作</th>
                </tr></thead>
                <tbody v-if="displayMode==='flat'"><tr v-for="item in paginatedTransactions" :key="item.id">
                  <td v-for="column in visibleColumns" :key="column.key" :class="columnClass(column)" :style="columnStickyStyle(column)">
                    <template v-if="column.key==='date'"><span class="flow-date">{{ formatDate(item.occurredOn) }}</span></template>
                    <template v-else-if="column.key==='kind'"><span class="flow-kind" :class="`tone-${kindMeta(item.kind).tone}`">{{ kindMeta(item.kind).label }}</span></template>
                    <template v-else-if="column.key==='category'"><span class="flow-resource"><LedgerResourceIcon :icon="item.categoryIcon || item.parentCategoryIcon" type="category" :color="item.categoryColor || item.parentCategoryColor" compact /><span><b class="flow-category">{{ item.categoryName|| (item.kind==='TRANSFER'?'账户互转':'未分类') }}</b><small v-if="item.parentCategoryName">{{ item.parentCategoryName }}</small></span></span></template>
                    <template v-else-if="column.key==='account'"><span>{{ item.accountName }}</span></template>
                    <template v-else-if="column.key==='targetAccount'"><span>{{ item.targetAccountName||'—' }}</span></template>
                    <template v-else-if="column.key==='payee'"><span>{{ item.payee||'—' }}</span></template>
                    <template v-else-if="column.key==='member'"><span>{{ item.member||'—' }}</span></template>
                    <template v-else-if="column.key==='project'"><span v-if="item.project" class="flow-resource"><LedgerResourceIcon :icon="item.projectIcon" type="project" :color="item.projectColor" compact /><span>{{ item.project }}</span></span><span v-else>—</span></template>
                    <template v-else-if="column.key==='note'"><span class="flow-note" :title="item.note||''">{{ item.note||'—' }}</span></template>
                    <template v-else-if="column.key==='amount'"><b :class="amountClass(item)">{{ amountPrefix(item) }}¥{{ money(item.amount) }}</b></template>
                  </td>
                  <td class="flow-row-actions fixed-actions"><LedgerActionIcon action="copy" label="复制流水" @click="openCopy(item)" /><LedgerActionIcon action="edit" label="编辑流水" @click="openEdit(item)" /><LedgerActionIcon action="delete" label="删除流水" @click="deleteTarget=item" /></td>
                </tr></tbody>
                <tbody v-else><template v-for="group in transactionGroups" :key="group.date"><tr class="flow-day-divider"><td :colspan="visibleColumns.length + 1"><b>{{ formatDate(group.date) }}</b><span>收入 ¥{{ money(group.income) }} · 支出 ¥{{ money(group.expense) }} · {{ group.items.length }} 笔</span></td></tr><tr v-for="item in group.items" :key="item.id">
                  <td v-for="column in visibleColumns" :key="column.key" :class="columnClass(column)" :style="columnStickyStyle(column)">
                    <template v-if="column.key==='date'"><span class="flow-date">{{ formatDate(item.occurredOn) }}</span></template>
                    <template v-else-if="column.key==='kind'"><span class="flow-kind" :class="`tone-${kindMeta(item.kind).tone}`">{{ kindMeta(item.kind).label }}</span></template>
                    <template v-else-if="column.key==='category'"><span class="flow-resource"><LedgerResourceIcon :icon="item.categoryIcon || item.parentCategoryIcon" type="category" :color="item.categoryColor || item.parentCategoryColor" compact /><span><b class="flow-category">{{ item.categoryName|| (item.kind==='TRANSFER'?'账户互转':'未分类') }}</b><small v-if="item.parentCategoryName">{{ item.parentCategoryName }}</small></span></span></template>
                    <template v-else-if="column.key==='account'"><span>{{ item.accountName }}</span></template><template v-else-if="column.key==='targetAccount'"><span>{{ item.targetAccountName||'—' }}</span></template><template v-else-if="column.key==='payee'"><span>{{ item.payee||'—' }}</span></template><template v-else-if="column.key==='member'"><span>{{ item.member||'—' }}</span></template><template v-else-if="column.key==='project'"><span v-if="item.project" class="flow-resource"><LedgerResourceIcon :icon="item.projectIcon" type="project" :color="item.projectColor" compact /><span>{{ item.project }}</span></span><span v-else>—</span></template><template v-else-if="column.key==='note'"><span class="flow-note" :title="item.note||''">{{ item.note||'—' }}</span></template><template v-else-if="column.key==='amount'"><b :class="amountClass(item)">{{ amountPrefix(item) }}¥{{ money(item.amount) }}</b></template>
                  </td><td class="flow-row-actions fixed-actions"><LedgerActionIcon action="copy" label="复制流水" @click="openCopy(item)" /><LedgerActionIcon action="edit" label="编辑流水" @click="openEdit(item)" /><LedgerActionIcon action="delete" label="删除流水" @click="deleteTarget=item" /></td>
                </tr></template></tbody>
              </Table>
            </div>

            <div class="flow-mobile-list" v-if="displayMode==='flat'">
              <article v-for="item in paginatedTransactions" :key="item.id" class="flow-mobile-item">
                <div class="flow-mobile-top"><span class="flow-kind" :class="`tone-${kindMeta(item.kind).tone}`">{{ kindMeta(item.kind).label }}</span><b :class="amountClass(item)">{{ amountPrefix(item) }}¥{{ money(item.amount) }}</b></div>
                <div class="flow-mobile-title"><strong>{{ item.payee||item.categoryName||(item.kind==='TRANSFER'?'账户互转':'未命名流水') }}</strong><span>{{ formatDate(item.occurredOn) }}</span></div>
                <div class="flow-mobile-meta"><span>{{ item.accountName }}<template v-if="item.targetAccountName"> → {{ item.targetAccountName }}</template></span><span class="flow-mobile-resource"><i :style="{ background: item.categoryColor || item.parentCategoryColor || 'var(--muted)' }" />{{ item.categoryName||'未分类' }}</span><span v-if="item.member">{{ item.member }}</span><span v-if="item.project" class="flow-mobile-resource"><i :style="{ background: item.projectColor || 'var(--muted)' }" />{{ item.project }}</span></div>
                <p v-if="item.note" class="flow-mobile-note" :title="item.note">{{ item.note }}</p>
                <div class="flow-mobile-actions"><LedgerActionIcon action="copy" label="复制流水" @click="openCopy(item)" /><LedgerActionIcon action="edit" label="编辑流水" @click="openEdit(item)" /><LedgerActionIcon action="delete" label="删除流水" @click="deleteTarget=item" /></div>
              </article>
            </div>
            <div v-else class="flow-mobile-list flow-mobile-day-list"><template v-for="group in transactionGroups" :key="group.date"><div class="flow-day-heading"><b>{{ formatDate(group.date) }}</b><span>收入 ¥{{ money(group.income) }} · 支出 ¥{{ money(group.expense) }}</span></div><article v-for="item in group.items" :key="item.id" class="flow-mobile-item">
              <div class="flow-mobile-top"><span class="flow-kind" :class="`tone-${kindMeta(item.kind).tone}`">{{ kindMeta(item.kind).label }}</span><b :class="amountClass(item)">{{ amountPrefix(item) }}¥{{ money(item.amount) }}</b></div><div class="flow-mobile-title"><strong>{{ item.payee||item.categoryName||(item.kind==='TRANSFER'?'账户互转':'未命名流水') }}</strong><span>{{ formatDate(item.occurredOn) }}</span></div><div class="flow-mobile-meta"><span>{{ item.accountName }}<template v-if="item.targetAccountName"> → {{ item.targetAccountName }}</template></span><span class="flow-mobile-resource"><i :style="{ background: item.categoryColor || item.parentCategoryColor || 'var(--muted)' }" />{{ item.categoryName||'未分类' }}</span><span v-if="item.member">{{ item.member }}</span><span v-if="item.project" class="flow-mobile-resource"><i :style="{ background: item.projectColor || 'var(--muted)' }" />{{ item.project }}</span></div><p v-if="item.note" class="flow-mobile-note" :title="item.note">{{ item.note }}</p><div class="flow-mobile-actions"><LedgerActionIcon action="copy" label="复制流水" @click="openCopy(item)" /><LedgerActionIcon action="edit" label="编辑流水" @click="openEdit(item)" /><LedgerActionIcon action="delete" label="删除流水" @click="deleteTarget=item" /></div>
            </article></template></div>
          </template>
          <template #footer>
            <div class="flow-table-footer">
              <div class="flow-table-totals"><span>共 {{ totalCount }} 笔</span><span>收入 ¥{{ money(summary.income) }} · 支出 ¥{{ money(summary.expense) }}</span></div>
              <div v-if="totalCount" class="flow-pagination">
                <label>每页<select v-model.number="pageSize" class="flow-page-size" @change="currentPage=1"><option v-for="size in pageSizeOptions" :key="size" :value="size">{{ size }} 条</option></select></label>
                <span>{{ pageStart + 1 }}–{{ pageEnd }} / {{ totalCount }}</span>
                <LedgerActionIcon action="previous" label="上一页" :disabled="currentPage<=1" @click="currentPage--" />
                <b>{{ currentPage }} / {{ totalPages }}</b>
                <LedgerActionIcon action="next" label="下一页" :disabled="currentPage>=totalPages" @click="currentPage++" />
              </div>
            </div>
          </template>
        </Card>
      </div>
    </div>

    <Sheet v-model:open="editorOpen" :title="editing?'编辑流水':'新增流水'">
      <form class="flow-editor" @submit.prevent="saveTransaction">
        <LedgerTransactionEditor :model="form" :accounts="accounts" :categories="categories" :usage-transactions="ledgerStore.transactions" :kinds="kindOptions" :merchant-options="merchantOptions" :member-options="memberOptions" :project-options="projectOptions" :lock-kind="editing?.kind==='TRANSFER'" />
        <div class="flow-editor-footer"><LedgerActionIcon action="close" label="取消" @click="editorOpen=false" /><LedgerActionIcon action="confirm" type="submit" :label="saving?'保存中':'保存流水'" :disabled="saving" /></div>
      </form>
    </Sheet>

    <Dialog v-model:open="filterOpen" title="筛选流水"><LedgerTransactionFilters :model="filters" :accounts="accounts" :categories="categories" :kinds="kindOptions" :merchant-options="merchantOptions" :member-options="memberOptions" :project-options="projectOptions" @update="updateFilter" @reset="resetFilters" @reset-period="resetPeriod" @quick-period="setQuickPeriod" /><template #footer><LedgerActionIcon action="restore" label="清除筛选" @click="resetFilters" /><LedgerActionIcon action="confirm" label="查看结果" @click="filterOpen=false" /></template></Dialog>
    <Drawer v-model:open="mobileFilterOpen" title="筛选流水"><LedgerTransactionFilters :model="filters" :accounts="accounts" :categories="categories" :kinds="kindOptions" :merchant-options="merchantOptions" :member-options="memberOptions" :project-options="projectOptions" @update="updateFilter" @reset="resetFilters" @reset-period="resetPeriod" @quick-period="setQuickPeriod" /></Drawer>
    <Drawer v-model:open="columnsOpen" title="显示列"><div class="flow-column-settings"><p class="flow-column-hint">拖动调整顺序，锁定的列始终显示。</p><div v-for="column in columns" :key="column.key" class="flow-column-setting" :class="{dragging: draggingColumnKey===column.key, 'drag-over': dragOverColumnKey===column.key && draggingColumnKey!==column.key}" draggable="true" @dragstart="handleColumnDragStart(column.key,$event)" @dragover.prevent="handleColumnDragOver(column.key)" @drop.prevent="handleColumnDrop(column.key)" @dragend="handleColumnDragEnd"><button type="button" class="flow-column-drag" :aria-label="`拖动${column.label}`" title="拖动排序"><Rank /></button><span><b>{{ column.label }}</b><small>{{ column.description }}</small></span><button type="button" class="flow-column-pin" :class="{active:isColumnPinned(column)}" :aria-label="isColumnPinned(column) ? `取消固定${column.label}` : `固定${column.label}`" :title="isColumnPinned(column) ? `取消固定${column.label}` : `固定${column.label}`" @click="toggleColumnPinned(column.key)"><Lock v-if="isColumnPinned(column)" /><Unlock v-else /></button><Toggle :model-value="isColumnVisible(column)" :disabled="isColumnPinned(column)" @update:model-value="setColumnVisibility(column.key,$event)" /></div></div></Drawer>
    <Dialog :open="Boolean(deleteTarget)" title="删除流水" @update:open="value=>{if(!value) deleteTarget=null}"><p class="flow-delete-copy">删除后这笔{{ deleteTarget ? kindMeta(deleteTarget.kind).label : '' }}流水将不再计入账本统计，确定继续吗？</p><template #footer><LedgerActionIcon action="close" label="取消删除" :disabled="deleting" @click="deleteTarget=null" /><LedgerActionIcon action="delete" :label="deleting?'删除中':'确认删除'" :disabled="deleting" @click="removeTransaction" /></template></Dialog>
  </section>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { Lock, Rank, SortDown, SortUp, Unlock } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { apiCreateLedgerTransaction, apiDeleteLedgerTransaction, apiListLedgerTransactions, apiUpdateLedgerTransaction } from '../api'
import LedgerTransactionEditor from '../components/ledger/LedgerTransactionEditor.vue'
import LedgerTransactionFilters from '../components/ledger/LedgerTransactionFilters.vue'
import LedgerResourceIcon from '../components/ledger/LedgerResourceIcon.vue'
import LedgerActionIcon from '../components/ledger/LedgerActionIcon.vue'
import LoadingOverlay from '../components/ledger/LoadingOverlay.vue'
import { currentMemberName, ledgerTransactionDraft, rankLedgerOptions, recentLedgerTransactions } from '../components/ledger/ledgerPreferences'
import { useAppStore } from '../stores/app'
import { useLedgerStore } from '../stores/ledger'
import Card from '../components/ui/Card.vue'
import Dialog from '../components/ui/Dialog.vue'
import Drawer from '../components/ui/Drawer.vue'
import Empty from '../components/ui/Empty.vue'
import Sheet from '../components/ui/Sheet.vue'
import Table from '../components/ui/Table.vue'
import Toggle from '../components/ui/Toggle.vue'

const today = new Date().toISOString().slice(0, 10)
const monthStart = `${today.slice(0, 7)}-01`
const route = useRoute()
const allRange = queryValue(route.query.range) === 'all'
const kindOptions = [
  { value: 'EXPENSE', label: '支出', tone: 'out' }, { value: 'INCOME', label: '收入', tone: 'in' },
  { value: 'TRANSFER', label: '转账', tone: 'transfer' }, { value: 'BORROW_IN', label: '借入', tone: 'in' },
  { value: 'LEND_OUT', label: '借出', tone: 'out' }, { value: 'COLLECT_DEBT', label: '收债', tone: 'in' },
  { value: 'REPAY_DEBT', label: '还债', tone: 'out' }
]
const columnDefaults = [
  { key: 'date', label: '日期', sortKey: 'occurredOn', visible: true, width: 112, description: '流水发生日期' },
  { key: 'kind', label: '类型', sortKey: 'kind', visible: true, width: 86, description: '收支及债务类型' },
  { key: 'category', label: '分类', sortKey: 'categoryName', visible: true, sticky: 'category', pinned: true, width: 132, description: '默认固定显示' },
  { key: 'account', label: '账户', sortKey: 'accountName', visible: true, width: 126, description: '资金所在账户' },
  { key: 'targetAccount', label: '转入账户', sortKey: 'targetAccountName', visible: true, width: 126, description: '转账目标账户' },
  { key: 'payee', label: '商家', sortKey: 'payee', visible: true, width: 150, description: '商家或交易对方' },
  { key: 'member', label: '成员', sortKey: 'member', visible: false, width: 112, description: '流水所属成员' },
  { key: 'project', label: '项目', sortKey: 'project', visible: true, width: 132, description: '关联项目' },
  { key: 'note', label: '备注', sortKey: 'note', visible: false, width: 120, description: '流水备注（最多展示 8 个汉字）' },
  { key: 'amount', label: '金额', sortKey: 'amount', visible: true, sticky: 'amount', pinned: true, width: 126, description: '默认固定显示' }
]
const accounts = ref([]), categories = ref([]), transactions = ref([]), loading = ref(true), editorOpen = ref(false), filterOpen = ref(false), mobileFilterOpen = ref(false), columnsOpen = ref(false), editing = ref(null), saving = ref(false), searchText = ref(''), deleteTarget = ref(null), deleting = ref(false)
const serverTotal = ref(0)
const serverSummary = ref(null)
const pageSizeOptions = [10, 20, 50, 100]
const pageSize = ref(20)
const displayMode = ref(localStorage.getItem('ledger-transactions-display-mode') === 'day' ? 'day' : 'flat')
const currentPage = ref(1)
const filters = reactive({
  from: allRange ? '' : queryValue(route.query.from) || monthStart,
  to: allRange ? '' : queryValue(route.query.to) || today,
  kind: queryValue(route.query.kind),
  accountId: queryValue(route.query.accountId),
  primaryCategoryId: queryValue(route.query.primaryCategoryId),
  secondaryCategoryId: queryValue(route.query.secondaryCategoryId),
  merchantId: queryValue(route.query.merchantId),
  memberId: queryValue(route.query.memberId),
  projectId: queryValue(route.query.projectId),
  payee: queryValue(route.query.payee),
  project: queryValue(route.query.project),
  member: queryValue(route.query.member),
  note: queryValue(route.query.note)
})
const sort = reactive({ key: 'occurredOn', direction: 'desc' })
const columnStorageKey = 'ledger-transaction-columns-v1'
function loadColumnConfig() {
  try {
    const saved = JSON.parse(localStorage.getItem(columnStorageKey) || 'null')
    if (Array.isArray(saved)) {
      const byKey = new Map(saved.map(item => [item.key, item]))
      return saved
        .filter(item => columnDefaults.some(column => column.key === item.key))
        .map(item => {
          const fallback = columnDefaults.find(column => column.key === item.key)
          return { ...fallback, visible: item.visible !== false, pinned: item.pinned !== undefined ? Boolean(item.pinned) : Boolean(fallback.pinned || fallback.fixed), width: Number(item.width) || fallback.width }
        })
        .concat(columnDefaults.filter(column => !byKey.has(column.key)).map(item => ({ ...item, pinned: Boolean(item.fixed) })))
    }
  } catch { /* ignore malformed local preferences */ }
  return columnDefaults.map(item => ({ ...item, pinned: Boolean(item.pinned || item.fixed) }))
}
const initialColumns = loadColumnConfig()
const columns = reactive(initialColumns)
const columnWidths = reactive(Object.fromEntries(initialColumns.map(item => [item.key, item.width])))
const form = reactive({ kind: 'EXPENSE', amount: '', occurredOn: today, accountId: '', targetAccountId: '', categoryId: '', payee: '', member: '', project: '', note: '' })

const isColumnPinned = column => Boolean(column?.fixed || column?.pinned)
const isColumnVisible = column => isColumnPinned(column) || column?.visible !== false
const visibleColumns = computed(() => columns.filter(isColumnVisible))
const configurableColumns = computed(() => columns.filter(item => !isColumnPinned(item)))
const draggingColumnKey = ref('')
const dragOverColumnKey = ref('')
const ledgerStore = useLedgerStore()
const appStore = useAppStore()
const serverMode = computed(() => ledgerStore.online && Boolean(ledgerStore.currentBookId))
const merchantOptions = computed(() => ledgerStore.visibleMerchants.map(item => item.name))
const memberOptions = computed(() => ledgerStore.activeMembers.map(item => item.displayName || item.username))
const projectOptions = computed(() => ledgerStore.visibleProjects.map(item => item.name))
const activeFilterCount = computed(() => (filters.from || filters.to ? 1 : 0) + Object.entries(filters).filter(([key, value]) => !['from', 'to'].includes(key) && value).length)
const rangeLabel = computed(() => filters.from || filters.to ? `${filters.from || '最早'} 至 ${filters.to || '今天'}` : '全部日期')

const filteredTransactions = computed(() => transactions.value.filter(item => {
  if (filters.from && item.occurredOn < filters.from) return false
  if (filters.to && item.occurredOn > filters.to) return false
  if (filters.kind && item.kind !== filters.kind) return false
  if (filters.accountId && ![item.accountId, item.targetAccountId].some(value => String(value || '') === filters.accountId)) return false
  const category = categories.value.find(value => String(value.id) === String(item.categoryId))
  if (filters.primaryCategoryId && String(item.parentCategoryId || category?.parentId || category?.id || '') !== filters.primaryCategoryId) return false
  if (filters.secondaryCategoryId && String(item.categoryId || '') !== filters.secondaryCategoryId) return false
  if (filters.merchantId && String(item.merchantId || '') !== filters.merchantId) return false
  if (filters.memberId && String(item.memberId || '') !== filters.memberId) return false
  if (filters.projectId && String(item.projectId || '') !== filters.projectId) return false
  if (!includes(item.payee, filters.payee) || !includes(item.project, filters.project) || !includes(item.member, filters.member) || !includes(item.note, filters.note)) return false
  if (searchText.value && ![item.payee, item.project, item.member, item.note, item.categoryName, item.accountName, item.targetAccountName].some(value => includes(value, searchText.value))) return false
  return true
}))
const sortedTransactions = computed(() => [...filteredTransactions.value].sort((left, right) => {
  const a = sort.key === 'amount' ? Number(left[sort.key]) : String(left[sort.key] || '').toLocaleLowerCase()
  const b = sort.key === 'amount' ? Number(right[sort.key]) : String(right[sort.key] || '').toLocaleLowerCase()
  const result = a < b ? -1 : a > b ? 1 : String(left.id || '').localeCompare(String(right.id || ''))
  return sort.direction === 'asc' ? result : -result
}))
const totalCount = computed(() => serverMode.value ? serverTotal.value : sortedTransactions.value.length)
const totalPages = computed(() => Math.max(1, Math.ceil(totalCount.value / pageSize.value)))
const pageStart = computed(() => (currentPage.value - 1) * pageSize.value)
const pageEnd = computed(() => Math.min(pageStart.value + paginatedTransactions.value.length, totalCount.value))
const paginatedTransactions = computed(() => serverMode.value
  ? sortedTransactions.value
  : sortedTransactions.value.slice(pageStart.value, pageStart.value + pageSize.value))
const transactionGroups = computed(() => {
  const groups = new Map()
  for (const item of paginatedTransactions.value) {
    const date = String(item.occurredOn || '未设置日期')
    const group = groups.get(date) || { date, income: 0, expense: 0, items: [] }
    const amount = Number(item.amount || 0)
    if (['INCOME', 'BORROW_IN', 'COLLECT_DEBT'].includes(item.kind)) group.income += amount
    if (['EXPENSE', 'LEND_OUT', 'REPAY_DEBT'].includes(item.kind)) group.expense += amount
    group.items.push(item)
    groups.set(date, group)
  }
  return [...groups.values()].sort((left, right) => right.date.localeCompare(left.date))
})
const localSummary = computed(() => filteredTransactions.value.reduce((result, item) => {
  const amount = Number(item.amount) || 0
  if (item.kind === 'INCOME') result.income += amount
  if (item.kind === 'EXPENSE') result.expense += amount
  if (['INCOME', 'BORROW_IN', 'COLLECT_DEBT'].includes(item.kind)) result.net += amount
  if (['EXPENSE', 'LEND_OUT', 'REPAY_DEBT'].includes(item.kind)) result.net -= amount
  return result
}, { income: 0, expense: 0, net: 0 }))
const summary = computed(() => serverMode.value && serverSummary.value
  ? {
      income: Number(serverSummary.value.income || 0),
      expense: Number(serverSummary.value.expense || 0),
      net: Number(serverSummary.value.balance || 0)
    }
  : localSummary.value)
const kindSummary = computed(() => kindOptions.map(item => ({ ...item, count: filteredTransactions.value.filter(row => row.kind === item.value).length })))

function includes(value, keyword) { return !keyword || String(value || '').toLocaleLowerCase().includes(String(keyword).trim().toLocaleLowerCase()) }
function queryValue(value) { return String(Array.isArray(value) ? value[0] || '' : value || '') }
function uniqueOptions(field) { return [...new Set(transactions.value.map(item => String(item[field] || '').trim()).filter(Boolean))].sort((left, right) => left.localeCompare(right, 'zh-CN')) }
function kindMeta(kind) { return kindOptions.find(item => item.value === kind) || kindOptions[0] }
function amountClass(item) { return kindMeta(item.kind).tone === 'in' ? 'flow-income' : kindMeta(item.kind).tone === 'out' ? 'flow-expense' : 'flow-transfer' }
function amountPrefix(item) { return kindMeta(item.kind).tone === 'in' ? '+' : kindMeta(item.kind).tone === 'out' ? '−' : '' }
function money(value) { return Number(value || 0).toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 }) }
function formatDate(value) { return String(value || '').replaceAll('-', '/') }
function columnClass(column) { return { 'fixed-column': isColumnPinned(column), 'fixed-category': column.sticky === 'category' && isColumnPinned(column), 'fixed-amount': column.sticky === 'amount' && isColumnPinned(column), 'amount-column': column.key === 'amount', 'note-column': column.key === 'note' } }
function columnStickyStyle(column) {
  if (!isColumnPinned(column)) return undefined
  if (column.sticky === 'amount') return { position: 'sticky', right: '108px', zIndex: 3 }
  const index = visibleColumns.value.findIndex(item => item.key === column.key)
  const left = visibleColumns.value.slice(0, index)
    .filter(item => isColumnPinned(item) && item.sticky !== 'amount')
    .reduce((total, item) => total + Number(columnWidths[item.key] || item.width || 0), 0)
  return { position: 'sticky', left: `${left}px`, zIndex: 3 }
}
function openFilters() { if (window.matchMedia('(max-width: 767px)').matches) mobileFilterOpen.value = true; else filterOpen.value = true }
function updateFilter({ key, value }) { filters[key] = value }
function resetFilters() { Object.assign(filters, { from: '', to: '', kind: '', accountId: '', primaryCategoryId: '', secondaryCategoryId: '', merchantId: '', memberId: '', projectId: '', payee: '', project: '', member: '', note: '' }); searchText.value = '' }
function resetPeriod() { filters.from = ''; filters.to = '' }
function setQuickPeriod(period) { const year = today.slice(0, 4); filters.from = period === 'month' ? monthStart : `${year}-01-01`; filters.to = period === 'month' ? today : `${year}-12-31` }
function toggleKind(kind) { filters.kind = filters.kind === kind ? '' : kind }
function setColumnVisibility(key, value) { const column = columns.find(item => item.key === key); if (column && !isColumnPinned(column)) column.visible = value }
function toggleColumnPinned(key) {
  const column = columns.find(item => item.key === key)
  if (!column) return
  column.pinned = !isColumnPinned(column)
  if (column.pinned) column.visible = true
}
function handleColumnDragStart(key, event) {
  draggingColumnKey.value = key
  event.dataTransfer?.setData('text/plain', key)
  if (event.dataTransfer) event.dataTransfer.effectAllowed = 'move'
}
function handleColumnDragOver(key) { dragOverColumnKey.value = key }
function handleColumnDrop(key) {
  const from = columns.findIndex(item => item.key === draggingColumnKey.value)
  const to = columns.findIndex(item => item.key === key)
  if (from >= 0 && to >= 0 && from !== to) {
    const [moved] = columns.splice(from, 1)
    columns.splice(to, 0, moved)
  }
  handleColumnDragEnd()
}
function handleColumnDragEnd() { draggingColumnKey.value = ''; dragOverColumnKey.value = '' }
function sortBy(column) { if (!column.sortKey) return; if (sort.key === column.sortKey) sort.direction = sort.direction === 'asc' ? 'desc' : 'asc'; else { sort.key = column.sortKey; sort.direction = column.sortKey === 'occurredOn' ? 'desc' : 'asc' }; currentPage.value = 1 }
function startResize(event, key) { const startX = event.clientX, startWidth = columnWidths[key], maxWidth = key === 'note' ? 120 : 360; const move = current => { columnWidths[key] = Math.max(82, Math.min(maxWidth, startWidth + current.clientX - startX)) }; const stop = () => { window.removeEventListener('pointermove', move); window.removeEventListener('pointerup', stop) }; window.addEventListener('pointermove', move); window.addEventListener('pointerup', stop) }
function blankForm() {
  const account = rankLedgerOptions(accounts.value, recentLedgerTransactions(ledgerStore.transactions),
    item => [item.accountId, item.targetAccountId])[0]
  Object.assign(form, { kind: 'EXPENSE', amount: '', occurredOn: new Date().toLocaleDateString('en-CA'),
    accountId: account ? String(account.id) : '', targetAccountId: '', categoryId: '', payee: '',
    member: currentMemberName(ledgerStore.activeMembers, appStore.authUser), project: '', note: '' })
}
function openCreate() { editing.value = null; blankForm(); editorOpen.value = true }
function openEdit(item) { editing.value = item; Object.assign(form, { kind: item.kind, amount: item.amount, occurredOn: item.occurredOn, accountId: String(item.accountId), targetAccountId: item.targetAccountId ? String(item.targetAccountId) : '', categoryId: item.categoryId ? String(item.categoryId) : '', payee: item.payee || '', member: item.member || '', project: item.project || '', note: item.note || '' }); editorOpen.value = true }
function openCopy(item) { editing.value = null; Object.assign(form, { kind: item.kind, amount: item.amount, occurredOn: today, accountId: String(item.accountId), targetAccountId: item.targetAccountId ? String(item.targetAccountId) : '', categoryId: item.categoryId ? String(item.categoryId) : '', payee: item.payee || '', member: item.member || '', project: item.project || '', note: item.note || '' }); editorOpen.value = true }
async function saveTransaction() { saving.value = true; try { const category = categories.value.find(item => String(item.id) === String(form.categoryId || '')); const requiresCategory = ['INCOME', 'EXPENSE'].includes(form.kind); if (requiresCategory && (!category?.parentId || category.kind !== form.kind)) throw new Error('请选择对应流水类型的二级分类'); const payload = { ...form, amount: Number(form.amount), accountId: String(form.accountId), targetAccountId: form.targetAccountId ? String(form.targetAccountId) : null, categoryId: requiresCategory ? String(category.id) : null, clientOpId: `web-flow-${Date.now()}` }; if (editing.value) await apiUpdateLedgerTransaction(ledgerStore.currentBookId, editing.value.id, payload, editing.value.revision, payload.clientOpId); else await apiCreateLedgerTransaction(ledgerStore.currentBookId, payload, payload.clientOpId); editorOpen.value = false; ElMessage.success(editing.value ? '流水已更新' : '流水已添加'); await loadData() } catch (error) { ElMessage.error(error.response?.data?.detail || error.message || '流水保存失败') } finally { saving.value = false } }
async function removeTransaction() { if (!deleteTarget.value) return; deleting.value = true; try { await apiDeleteLedgerTransaction(ledgerStore.currentBookId, deleteTarget.value.id, deleteTarget.value.revision, `web-flow-delete-${Date.now()}`); await ledgerStore.refreshAccounts(); deleteTarget.value = null; ElMessage.success('流水已删除'); await loadData() } catch (error) { ElMessage.error(error.response?.data?.detail || '流水删除失败') } finally { deleting.value = false } }
function applyCachedData() {
  applyCachedResources()
  transactions.value = ledgerStore.transactions.filter(item => !item.deleted)
}
function applyCachedResources() {
  accounts.value = ledgerStore.visibleAccounts
  categories.value = ledgerStore.visibleCategories
}

function serverSortKey() {
  return ({ occurredOn: 'occurredOn', amount: 'amount', kind: 'kind', categoryName: 'category', accountName: 'account', payee: 'merchant', project: 'project' })[sort.key] || 'occurredOn'
}

let requestSequence = 0
async function loadPage() {
  if (!serverMode.value) return
  const requestId = ++requestSequence
  loading.value = true
  try {
    const response = await apiListLedgerTransactions(ledgerStore.currentBookId, {
      page: currentPage.value,
      pageSize: pageSize.value,
      sort: serverSortKey(),
      direction: sort.direction,
      q: searchText.value.trim() || undefined,
      ...Object.fromEntries(Object.entries(filters).filter(([, value]) => value))
    })
    if (requestId !== requestSequence) return
    transactions.value = response.items || []
    serverTotal.value = Number(response.total || 0)
    serverSummary.value = response.summary || null
  } catch (error) {
    if (requestId !== requestSequence) return
    applyCachedData()
    serverTotal.value = filteredTransactions.value.length
    serverSummary.value = null
    ElMessage.error(error.response?.data?.detail || '流水读取失败')
  } finally {
    if (requestId === requestSequence) loading.value = false
  }
}

async function loadData() {
  loading.value = true
  try {
    await ledgerStore.init({ waitForRemote: false })
    applyCachedResources()
    if (!serverMode.value) applyCachedData()
    loading.value = false
    if (serverMode.value) await loadPage()
  } catch (error) {
    ElMessage.error(error.response?.data?.detail || '流水读取失败')
  } finally {
    loading.value = false
  }
}

async function saveTransactionFixed() {
  try {
    const payload = {
      ...ledgerTransactionDraft(form, { categories: categories.value, merchants: ledgerStore.visibleMerchants,
        members: ledgerStore.activeMembers, projects: ledgerStore.visibleProjects }),
      clientOpId: `web-flow-${Date.now()}`
    }
    saving.value = true
    if (editing.value) await apiUpdateLedgerTransaction(ledgerStore.currentBookId, editing.value.id, payload, editing.value.revision, payload.clientOpId)
    else await apiCreateLedgerTransaction(ledgerStore.currentBookId, payload, payload.clientOpId)
    await ledgerStore.refreshAccounts()
    editorOpen.value = false
    ElMessage.success(editing.value ? '流水已更新' : '流水已添加')
    await loadData()
  } catch (error) {
    ElMessage.error(error.response?.data?.detail || error.message || '流水保存失败')
  } finally { saving.value = false }
}
saveTransaction = saveTransactionFixed
watch(totalPages, pages => { if (currentPage.value > pages) currentPage.value = pages })
watch(displayMode, value => localStorage.setItem('ledger-transactions-display-mode', value))
watch([columns, columnWidths], () => {
  localStorage.setItem(columnStorageKey, JSON.stringify(columns.map(column => ({ key: column.key, visible: column.visible, pinned: isColumnPinned(column), width: columnWidths[column.key] }))))
}, { deep: true })
watch([
  () => ledgerStore.accounts,
  () => ledgerStore.categories,
  () => ledgerStore.merchants,
  () => ledgerStore.members,
  () => ledgerStore.projects
], applyCachedResources, { deep: true })
watch(() => ledgerStore.online, online => {
  if (online) loadPage()
  else applyCachedData()
})
let filterTimer
watch([filters, searchText], () => {
  currentPage.value = 1
  window.clearTimeout(filterTimer)
  filterTimer = window.setTimeout(loadPage, 220)
}, { deep: true })
watch([currentPage, pageSize, () => sort.key, () => sort.direction], loadPage)
onMounted(() => { loadData(); window.addEventListener('ledger-book-changed', loadData) })
onBeforeUnmount(() => {
  requestSequence++
  window.clearTimeout(filterTimer)
  window.removeEventListener('ledger-book-changed', loadData)
})
</script>

<style scoped>
.ledger-flow-page { max-width: 1560px; margin: 0 auto }
.heading-slash { color: var(--muted); font: 500 12px ui-sans-serif,system-ui; letter-spacing: .16em }
.flow-heading { align-items: center; margin-bottom: 22px }
.flow-heading-actions { display: flex; align-items: center; gap: 8px }
.flow-heading-actions svg { width: 15px; height: 15px }
.flow-workspace { display: grid; grid-template-columns: 252px minmax(0,1fr); gap: 18px; align-items: start }
.flow-sidebar { display: flex; flex-direction: column; gap: 14px; position: sticky; top: 22px }
.flow-sidebar :deep(.card) { padding: 18px }
.flow-card-kicker { color: var(--muted); font-size: 10px; font-weight: 650; letter-spacing: .16em }
.flow-result-count { display: flex; align-items: baseline; gap: 7px; margin: 7px 0 17px }
.flow-result-count strong { color: var(--ink); font: 700 34px Georgia,serif }
.flow-result-count span { color: var(--muted); font-size: 11px }
.flow-summary-list { display: flex; flex-direction: column; gap: 10px; padding: 14px 0; border-top: 1px solid var(--line); border-bottom: 1px solid var(--line) }
.flow-summary-list div { display: flex; align-items: center; justify-content: space-between; gap: 10px; color: var(--muted); font-size: 11px }
.flow-summary-list b { font-size: 13px; font-variant-numeric: tabular-nums }
.flow-income { color: var(--down) !important }
.flow-expense { color: var(--up) !important }
.flow-transfer { color: var(--ink2) !important }
.flow-kind-summary { display: flex; flex-direction: column; padding-top: 12px }
.flow-kind-summary button { display: flex; align-items: center; justify-content: space-between; min-height: 31px; padding: 0 6px; border: 0; border-radius: 3px; background: transparent; color: var(--ink2); font-size: 11px }
.flow-kind-summary button:hover,.flow-kind-summary button.active { background: var(--accent-soft); color: var(--ink) }
.flow-kind-summary button span { display: inline-flex; align-items: center; gap: 8px }
.flow-kind-summary i { width: 6px; height: 6px; border-radius: 50%; background: var(--muted) }
.flow-kind-summary i.tone-in { background: var(--down) }.flow-kind-summary i.tone-out { background: var(--up) }.flow-kind-summary i.tone-transfer { background: var(--ink2) }
.flow-card-title { display: flex; align-items: center; justify-content: space-between; margin-bottom: 17px; color: var(--ink); font-size: 13px; font-weight: 700 }
.flow-card-title small { color: var(--muted); font-size: 10px; font-weight: 500 }
.flow-main { min-width: 0 }
.flow-table-card { padding: 0; overflow: hidden }
.flow-table-card :deep(> header) { padding: 19px 20px 16px; border-bottom: 1px solid var(--line) }
.flow-table-card :deep(> div) { min-width: 0 }
.flow-table-card :deep(> footer) { border-top: 1px solid var(--line) }
.flow-table-head { display: flex; align-items: center; justify-content: space-between; gap: 20px }
.flow-table-tools { display:flex; align-items:center; justify-content:flex-end; gap:10px; min-width:0 }
.flow-view-switch { display:flex; flex:0 0 auto; align-items:center; gap:2px; padding:2px; border:1px solid var(--line2); border-radius:4px; background:var(--paper) }
.flow-view-switch button { height:27px; padding:0 9px; border:0; border-radius:3px; background:transparent; color:var(--muted); font-size:10px }
.flow-view-switch button.active { background:var(--card); color:var(--accent); box-shadow:0 1px 3px #00000012; font-weight:650 }
.flow-table-head h2 { margin: 3px 0 0; color: var(--ink); font: 700 21px Georgia,serif }
.flow-search { position: relative; width: min(330px,42vw) }
.flow-search .ui-input { height: 34px; padding-right: 34px; font-size: 12px }
.flow-search button { position: absolute; right: 5px; top: 4px; width: 26px; height: 26px; border: 0; background: transparent; color: var(--muted); font-size: 18px }
.flow-loading { padding: 70px 20px; color: var(--muted); text-align: center }
.flow-desktop-table { max-width: 100%; overflow: hidden }
.flow-desktop-table :deep(.ui-table-wrap) { overflow: auto; max-height: calc(100vh - 225px); border: 0; border-radius: 0 }
.flow-desktop-table :deep(.ui-table) { width: max-content; min-width: 100%; table-layout: fixed }
.flow-desktop-table :deep(th) { position: sticky; top: 0; z-index: 4; height: 42px; padding: 0 12px; background: var(--paper); white-space: nowrap }
.flow-desktop-table :deep(td) { height: 50px; padding: 8px 12px; overflow: hidden; background: var(--card); text-overflow: ellipsis; white-space: nowrap }
.flow-desktop-table :deep(td small) { display: block; overflow: hidden; color: var(--muted); font-size: 10px; text-overflow: ellipsis }
.flow-desktop-table :deep(tr:hover td) { background: var(--accent-soft) }
.flow-sort-button { display: inline-flex; align-items: center; gap: 4px; width: 100%; padding: 0; border: 0; background: transparent; color: var(--muted); font-size: 10px; font-weight: 650; letter-spacing: .08em; text-align: left }
.flow-sort-button svg { width: 12px; height: 12px; color: var(--accent) }
.flow-day-divider td { height:36px!important; padding:7px 12px!important; background:var(--paper)!important; border-bottom:1px solid var(--line); color:var(--ink2); font-size:11px }
.flow-day-divider td span { margin-left:12px; color:var(--muted); font-size:10px }
.flow-resize-handle { position: absolute; top: 8px; right: -2px; width: 5px; height: 26px; cursor: col-resize; z-index: 7 }
.flow-resize-handle::after { content: ''; position: absolute; left: 2px; top: 5px; width: 1px; height: 16px; background: var(--line2) }
.flow-desktop-table :deep(.fixed-category) { position: sticky; left: 0; z-index: 3; box-shadow: 1px 0 0 var(--line) }
.flow-desktop-table :deep(th.fixed-category) { z-index: 6 }
.flow-desktop-table :deep(.fixed-amount) { position: sticky; right: 108px; z-index: 3; box-shadow: -1px 0 0 var(--line) }
.flow-desktop-table :deep(th.fixed-amount) { z-index: 6 }
.flow-desktop-table :deep(td.fixed-column) { background: var(--card) }
.flow-desktop-table :deep(th.fixed-column) { background: var(--paper) }
.flow-desktop-table :deep(.fixed-actions) { position: sticky; right: 0; z-index: 3; background: var(--card) }
.flow-desktop-table :deep(th.fixed-actions) { z-index: 6; background: var(--paper) }
.amount-column { text-align: right }.amount-column .flow-sort-button { justify-content: flex-end }
.flow-category { color: var(--ink); font-weight: 650 }
.flow-resource { display: inline-flex; min-width: 0; align-items: center; gap: 7px; vertical-align: middle }
.flow-resource>span { min-width: 0; overflow: hidden; text-overflow: ellipsis; white-space: nowrap }
.flow-kind { display: inline-flex; align-items: center; min-height: 22px; padding: 2px 7px; border: 1px solid var(--line); border-radius: 3px; color: var(--ink2); background: var(--paper); font-size: 10px; font-weight: 650 }
.flow-kind.tone-in { color: var(--down); border-color: color-mix(in srgb,var(--down) 28%,var(--line)); background: color-mix(in srgb,var(--down) 6%,var(--card)) }
.flow-kind.tone-out { color: var(--up); border-color: color-mix(in srgb,var(--up) 28%,var(--line)); background: color-mix(in srgb,var(--up) 6%,var(--card)) }
.flow-kind.tone-transfer { color: var(--ink2) }
.flow-date { font-variant-numeric: tabular-nums }
.note-column { width: 120px; max-width: 120px }
.flow-note { display: block; width: 8em; max-width: 100%; overflow: hidden; color: var(--muted); text-overflow: ellipsis; white-space: nowrap }
.flow-actions-column { width: 108px; text-align: center }
.flow-row-actions { display: flex; align-items: center; justify-content: center; gap: 4px }
.flow-row-actions button { display: inline-flex; align-items: center; justify-content: center; width: 27px; height: 27px; padding: 0; border: 1px solid transparent; border-radius: 3px; background: transparent; color: var(--muted) }
.flow-row-actions button:hover { border-color: var(--line2); color: var(--ink) }.flow-row-actions button.danger:hover { color: var(--down); border-color: color-mix(in srgb,var(--down) 35%,var(--line)) }
.flow-row-actions svg { width: 14px; height: 14px }
.flow-table-footer { display: flex; align-items: center; justify-content: space-between; gap: 16px; min-height: 48px; padding: 8px 20px; color: var(--muted); font-size: 11px }
.flow-table-totals,.flow-pagination { display: flex; align-items: center; gap: 12px }
.flow-pagination { justify-content: flex-end; font-variant-numeric: tabular-nums }
.flow-pagination label { display: inline-flex; align-items: center; gap: 6px; white-space: nowrap }
.flow-page-size { height: 28px; padding: 0 24px 0 8px; border: 1px solid var(--line2); border-radius: 3px; outline: 0; color: var(--ink2); background: var(--card); font: inherit }
.flow-page-size:focus { border-color: var(--ink2) }
.flow-pagination button { min-width: 52px; height: 28px; padding: 0 8px; border: 1px solid var(--line2); border-radius: 3px; color: var(--ink2); background: var(--card); font: inherit; cursor: pointer }
.flow-pagination button:hover:not(:disabled) { border-color: var(--ink2); color: var(--ink) }
.flow-pagination button:disabled { opacity: .38; cursor: not-allowed }
.flow-pagination b { min-width: 42px; color: var(--ink2); text-align: center; white-space: nowrap }
.flow-mobile-list { display: none }
.flow-day-heading { display:flex; align-items:baseline; justify-content:space-between; gap:8px; padding:9px 12px 5px; border-bottom:1px solid var(--line); background:var(--paper); color:var(--ink2); font-size:11px }
.flow-day-heading span { color:var(--muted); font-size:10px }
.flow-editor { display: flex; flex-direction: column; gap: 15px }
.flow-editor-footer { display: flex; justify-content: flex-end; gap: 8px; padding-top: 8px }
.flow-delete-copy { margin: 0; color: var(--ink2); font-size: 13px; line-height: 1.7 }
.flow-delete-confirm { border-color: var(--down) !important; background: var(--down) !important; color: #fff !important }
.flow-column-settings { display: flex; flex-direction: column }
.flow-column-hint { margin: 0 0 4px; color: var(--muted); font-size: 11px; line-height: 1.5 }
.flow-column-setting { display: flex; align-items: center; justify-content: space-between; gap: 10px; min-height: 58px; border-bottom: 1px solid var(--line); transition: background .16s ease, opacity .16s ease }
.flow-column-setting:last-child { border-bottom: 0 }
.flow-column-setting.dragging { opacity: .45 }
.flow-column-setting.drag-over { background: var(--accent-soft) }
.flow-column-setting>span { flex: 1; min-width: 0 }
.flow-column-setting b,.flow-column-setting small { display: block }
.flow-column-setting b { color: var(--ink); font-size: 13px }
.flow-column-setting small { margin-top: 2px; color: var(--muted); font-size: 11px }
.flow-column-drag,.flow-column-pin { display: inline-grid; place-items: center; flex: 0 0 30px; width: 30px; height: 30px; padding: 0; border: 1px solid transparent; border-radius: 4px; background: transparent; color: var(--muted); cursor: grab }
.flow-column-drag:active { cursor: grabbing }
.flow-column-drag:hover,.flow-column-pin:hover,.flow-column-pin.active { border-color: var(--line2); background: var(--card); color: var(--accent) }
.flow-column-drag svg,.flow-column-pin svg { width: 15px; height: 15px }
.flow-column-setting :deep(.ui-toggle) { flex: 0 0 auto }
@media(max-width:1023px){.flow-workspace{grid-template-columns:220px minmax(0,1fr)}.flow-sidebar{position:static}.flow-desktop-table :deep(.ui-table-wrap){max-height:none}}
@media(max-width:767px){.ledger-flow-page,.flow-main,.flow-table-card{min-width:0;max-width:100%}.flow-heading{align-items:flex-start}.flow-heading-actions{width:100%;min-width:0}.flow-heading-actions .ui-button{min-width:0;flex:1;padding:0 7px}.flow-workspace{display:block;min-width:0}.flow-sidebar{display:none}.flow-table-card{width:100%;margin:0 auto;box-sizing:border-box}.flow-table-card :deep(> header){padding:13px 16px}.flow-table-head{min-width:0;align-items:flex-start;flex-direction:column;gap:10px}.flow-table-tools{width:100%;justify-content:stretch;flex-direction:column;align-items:stretch;gap:8px}.flow-view-switch{align-self:flex-start}.flow-search{width:100%;max-width:100%}.flow-desktop-table{display:none}.flow-mobile-list{display:flex;min-width:0;flex-direction:column}.flow-mobile-item{min-width:0;max-width:100%;padding:11px 12px;border-bottom:1px solid var(--line);background:var(--card)}.flow-mobile-item:last-child{border-bottom:0}.flow-mobile-top,.flow-mobile-title,.flow-mobile-actions{display:flex;min-width:0;max-width:100%;align-items:center;justify-content:space-between;gap:8px}.flow-mobile-top>b{min-width:0;font-size:14px}.flow-mobile-title{margin-top:8px}.flow-mobile-title strong{min-width:0;overflow:hidden;color:var(--ink);font-size:13px;text-overflow:ellipsis;white-space:nowrap}.flow-mobile-title span{flex:0 0 auto;color:var(--muted);font-size:10px}.flow-mobile-meta{display:flex;min-width:0;max-width:100%;flex-wrap:wrap;gap:3px 9px;margin-top:5px;color:var(--ink2);font-size:10px}.flow-mobile-meta span{min-width:0;max-width:100%;overflow:hidden;text-overflow:ellipsis;white-space:nowrap}.flow-mobile-resource{display:inline-flex;align-items:center;gap:4px}.flow-mobile-resource i{flex:0 0 6px;width:6px;height:6px;border-radius:50%}.flow-mobile-note{display:block;max-width:100%;margin:6px 0 0;overflow:hidden;color:var(--muted);font-size:10px;text-overflow:ellipsis;white-space:nowrap}.flow-mobile-actions{justify-content:flex-end;margin-top:7px}.flow-mobile-actions button{display:inline-flex;min-width:0;align-items:center;gap:3px;padding:3px 5px;border:0;background:transparent;color:var(--ink2);font-size:10px}.flow-mobile-actions button.danger{color:var(--down)}.flow-mobile-actions svg{width:12px;height:12px}.flow-table-footer{min-height:42px;padding:8px 12px;align-items:flex-start;flex-direction:column;font-size:10px}.flow-table-totals,.flow-pagination{width:100%;justify-content:space-between;gap:6px}.flow-pagination label{gap:4px}.flow-pagination button{min-width:46px;padding:0 5px}.flow-pagination>b{display:none}}
.flow-filter-action{position:relative;display:inline-flex}
.flow-filter-action i{position:absolute;top:-5px;right:-5px;display:grid;place-items:center;min-width:15px;height:15px;padding:0 2px;border-radius:999px;background:var(--accent);color:var(--card);font-size:9px;font-style:normal}
.flow-row-actions .ledger-action-icon{display:inline-grid;flex:0 0 28px;width:28px;height:28px;padding:0;border:1px solid var(--line2);background:var(--card);color:var(--ink2)}
.flow-row-actions .ledger-action-icon:hover{border-color:var(--accent);background:var(--accent-soft);color:var(--accent)}
.flow-mobile-actions .ledger-action-icon{display:inline-grid;flex:0 0 32px;width:32px;height:32px;padding:0;border:1px solid var(--line2);background:var(--card);color:var(--ink2)}
.flow-pagination .ledger-action-icon{display:inline-grid;flex:0 0 30px;width:30px;min-width:30px;height:30px;padding:0}
@media(max-width:767px){.flow-heading-actions{justify-content:flex-end}.flow-pagination .ledger-action-icon{min-width:30px;padding:0}}
</style>
