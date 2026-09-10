<template>
  <section class="ledger-flow-page">
    <div class="page-heading flow-heading">
      <div><div class="label">PERSONAL FINANCE / TRANSACTIONS</div><h1>流水 <span class="heading-slash">// DETAILS</span></h1><p class="muted">{{ rangeLabel }} · {{ filteredTransactions.length }} 笔</p></div>
      <div class="flow-heading-actions">
        <Button class="flow-filter-button" size="sm" variant="ghost" @click="openFilters"><Filter aria-hidden="true" />筛选<span v-if="activeFilterCount">{{ activeFilterCount }}</span></Button>
        <Button size="sm" variant="ghost" title="设置显示列" @click="columnsOpen=true"><Setting aria-hidden="true" />显示列</Button>
        <Button size="sm" @click="openCreate"><Plus aria-hidden="true" />记一笔</Button>
      </div>
    </div>

    <div class="flow-workspace">
      <aside class="flow-sidebar">
        <Card class="flow-summary-card">
          <template #header><div class="flow-card-kicker">当前筛选</div><div class="flow-result-count"><strong>{{ filteredTransactions.length }}</strong><span>笔流水</span></div></template>
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
              <div class="flow-search"><input v-model="searchText" class="ui-input" placeholder="搜索商家、项目、成员或备注"><button v-if="searchText" type="button" aria-label="清空搜索" @click="searchText=''">×</button></div>
            </div>
          </template>

          <div v-if="loading" class="flow-loading">正在读取流水…</div>
          <Empty v-else-if="!sortedTransactions.length" description="没有符合条件的流水" />
          <template v-else>
            <div class="flow-desktop-table">
              <Table>
                <colgroup><col v-for="column in visibleColumns" :key="column.key" :style="{width:`${columnWidths[column.key]}px`}"><col style="width:108px"></colgroup>
                <thead><tr>
                  <th v-for="column in visibleColumns" :key="column.key" :class="columnClass(column)">
                    <button class="flow-sort-button" type="button" @click="sortBy(column)"><span>{{ column.label }}</span><SortUp v-if="sort.key===column.sortKey&&sort.direction==='asc'" aria-hidden="true" /><SortDown v-else-if="sort.key===column.sortKey" aria-hidden="true" /></button>
                    <i class="flow-resize-handle" @pointerdown.stop.prevent="startResize($event,column.key)"></i>
                  </th>
                  <th class="flow-actions-column fixed-actions">操作</th>
                </tr></thead>
                <tbody><tr v-for="item in paginatedTransactions" :key="item.id">
                  <td v-for="column in visibleColumns" :key="column.key" :class="columnClass(column)">
                    <template v-if="column.key==='date'"><span class="flow-date">{{ formatDate(item.occurredOn) }}</span></template>
                    <template v-else-if="column.key==='kind'"><span class="flow-kind" :class="`tone-${kindMeta(item.kind).tone}`">{{ kindMeta(item.kind).label }}</span></template>
                    <template v-else-if="column.key==='category'"><b class="flow-category">{{ item.categoryName|| (item.kind==='TRANSFER'?'账户互转':'未分类') }}</b><small v-if="item.parentCategoryName">{{ item.parentCategoryName }}</small></template>
                    <template v-else-if="column.key==='account'"><span>{{ item.accountName }}</span></template>
                    <template v-else-if="column.key==='targetAccount'"><span>{{ item.targetAccountName||'—' }}</span></template>
                    <template v-else-if="column.key==='payee'"><span>{{ item.payee||'—' }}</span></template>
                    <template v-else-if="column.key==='member'"><span>{{ item.member||'—' }}</span></template>
                    <template v-else-if="column.key==='project'"><span>{{ item.project||'—' }}</span></template>
                    <template v-else-if="column.key==='note'"><span class="flow-note" :title="item.note||''">{{ item.note||'—' }}</span></template>
                    <template v-else-if="column.key==='amount'"><b :class="amountClass(item)">{{ amountPrefix(item) }}¥{{ money(item.amount) }}</b></template>
                  </td>
                  <td class="flow-row-actions fixed-actions"><button type="button" title="复制流水" aria-label="复制流水" @click="openCopy(item)"><CopyDocument aria-hidden="true" /></button><button type="button" title="编辑流水" aria-label="编辑流水" @click="openEdit(item)"><EditPen aria-hidden="true" /></button><button class="danger" type="button" title="删除流水" aria-label="删除流水" @click="deleteTarget=item"><Delete aria-hidden="true" /></button></td>
                </tr></tbody>
              </Table>
            </div>

            <div class="flow-mobile-list">
              <article v-for="item in paginatedTransactions" :key="item.id" class="flow-mobile-item">
                <div class="flow-mobile-top"><span class="flow-kind" :class="`tone-${kindMeta(item.kind).tone}`">{{ kindMeta(item.kind).label }}</span><b :class="amountClass(item)">{{ amountPrefix(item) }}¥{{ money(item.amount) }}</b></div>
                <div class="flow-mobile-title"><strong>{{ item.payee||item.categoryName||(item.kind==='TRANSFER'?'账户互转':'未命名流水') }}</strong><span>{{ formatDate(item.occurredOn) }}</span></div>
                <div class="flow-mobile-meta"><span>{{ item.accountName }}<template v-if="item.targetAccountName"> → {{ item.targetAccountName }}</template></span><span>{{ item.categoryName||'未分类' }}</span><span v-if="item.member">{{ item.member }}</span><span v-if="item.project">{{ item.project }}</span></div>
                <p v-if="item.note" class="flow-mobile-note" :title="item.note">{{ item.note }}</p>
                <div class="flow-mobile-actions"><button type="button" @click="openCopy(item)"><CopyDocument aria-hidden="true" />复制</button><button type="button" @click="openEdit(item)"><EditPen aria-hidden="true" />编辑</button><button class="danger" type="button" @click="deleteTarget=item"><Delete aria-hidden="true" />删除</button></div>
              </article>
            </div>
          </template>
          <template #footer>
            <div class="flow-table-footer">
              <div class="flow-table-totals"><span>共 {{ sortedTransactions.length }} 笔</span><span>收入 ¥{{ money(summary.income) }} · 支出 ¥{{ money(summary.expense) }}</span></div>
              <div v-if="sortedTransactions.length" class="flow-pagination">
                <label>每页<select v-model.number="pageSize" class="flow-page-size" @change="currentPage=1"><option v-for="size in pageSizeOptions" :key="size" :value="size">{{ size }} 条</option></select></label>
                <span>{{ pageStart + 1 }}–{{ pageEnd }} / {{ sortedTransactions.length }}</span>
                <button type="button" :disabled="currentPage<=1" @click="currentPage--">上一页</button>
                <b>{{ currentPage }} / {{ totalPages }}</b>
                <button type="button" :disabled="currentPage>=totalPages" @click="currentPage++">下一页</button>
              </div>
            </div>
          </template>
        </Card>
      </div>
    </div>

    <Sheet v-model:open="editorOpen" :title="editing?'编辑流水':'新增流水'">
      <form class="flow-editor" @submit.prevent="saveTransaction">
        <LedgerTransactionEditor :model="form" :accounts="accounts" :categories="categories" :kinds="kindOptions" :merchant-options="merchantOptions" :member-options="memberOptions" :project-options="projectOptions" :lock-kind="editing?.kind==='TRANSFER'" />
        <div class="flow-editor-footer"><Button type="button" variant="ghost" @click="editorOpen=false">取消</Button><Button type="submit" :disabled="saving">{{ saving?'保存中…':'保存流水' }}</Button></div>
      </form>
    </Sheet>

    <Dialog v-model:open="filterOpen" title="筛选流水"><LedgerTransactionFilters :model="filters" :accounts="accounts" :categories="categories" :kinds="kindOptions" :merchant-options="merchantOptions" :member-options="memberOptions" :project-options="projectOptions" @update="updateFilter" @reset="resetFilters" @reset-period="resetPeriod" @quick-period="setQuickPeriod" /><template #footer><Button variant="ghost" @click="resetFilters">清除筛选</Button><Button @click="filterOpen=false">查看结果</Button></template></Dialog>
    <Drawer v-model:open="mobileFilterOpen" title="筛选流水"><LedgerTransactionFilters :model="filters" :accounts="accounts" :categories="categories" :kinds="kindOptions" :merchant-options="merchantOptions" :member-options="memberOptions" :project-options="projectOptions" @update="updateFilter" @reset="resetFilters" @reset-period="resetPeriod" @quick-period="setQuickPeriod" /></Drawer>
    <Drawer v-model:open="columnsOpen" title="显示列"><div class="flow-column-settings"><div v-for="column in configurableColumns" :key="column.key"><span><b>{{ column.label }}</b><small>{{ column.description }}</small></span><Toggle :model-value="column.visible" @update:model-value="setColumnVisibility(column.key,$event)" /></div><div class="flow-fixed-column"><span><b>分类、金额</b><small>固定显示</small></span><span>已固定</span></div></div></Drawer>
    <Dialog :open="Boolean(deleteTarget)" title="删除流水" @update:open="value=>{if(!value) deleteTarget=null}"><p class="flow-delete-copy">删除后这笔{{ deleteTarget ? kindMeta(deleteTarget.kind).label : '' }}流水将不再计入账本统计，确定继续吗？</p><template #footer><Button variant="ghost" :disabled="deleting" @click="deleteTarget=null">取消</Button><Button class="flow-delete-confirm" :disabled="deleting" @click="removeTransaction">{{ deleting?'删除中…':'确认删除' }}</Button></template></Dialog>
  </section>
</template>

<script setup>
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { CopyDocument, Delete, EditPen, Filter, Plus, Setting, SortDown, SortUp } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { apiCreateLedgerTransaction, apiDeleteLedgerTransaction, apiListLedgerAccounts, apiListLedgerCategories, apiListLedgerTransactions, apiUpdateLedgerTransaction } from '../api'
import LedgerTransactionEditor from '../components/ledger/LedgerTransactionEditor.vue'
import LedgerTransactionFilters from '../components/ledger/LedgerTransactionFilters.vue'
import Button from '../components/ui/Button.vue'
import Card from '../components/ui/Card.vue'
import Dialog from '../components/ui/Dialog.vue'
import Drawer from '../components/ui/Drawer.vue'
import Empty from '../components/ui/Empty.vue'
import Sheet from '../components/ui/Sheet.vue'
import Table from '../components/ui/Table.vue'
import Toggle from '../components/ui/Toggle.vue'

const today = new Date().toISOString().slice(0, 10)
const monthStart = `${today.slice(0, 7)}-01`
const kindOptions = [
  { value: 'EXPENSE', label: '支出', tone: 'out' }, { value: 'INCOME', label: '收入', tone: 'in' },
  { value: 'TRANSFER', label: '转账', tone: 'transfer' }, { value: 'BORROW_IN', label: '借入', tone: 'in' },
  { value: 'LEND_OUT', label: '借出', tone: 'out' }, { value: 'COLLECT_DEBT', label: '收债', tone: 'in' },
  { value: 'REPAY_DEBT', label: '还债', tone: 'out' }
]
const columnDefaults = [
  { key: 'date', label: '日期', sortKey: 'occurredOn', visible: true, width: 112, description: '流水发生日期' },
  { key: 'kind', label: '类型', sortKey: 'kind', visible: true, width: 86, description: '收支及债务类型' },
  { key: 'category', label: '分类', sortKey: 'categoryName', visible: true, fixed: 'category', width: 132, description: '固定显示' },
  { key: 'account', label: '账户', sortKey: 'accountName', visible: true, width: 126, description: '资金所在账户' },
  { key: 'targetAccount', label: '转入账户', sortKey: 'targetAccountName', visible: true, width: 126, description: '转账目标账户' },
  { key: 'payee', label: '商家', sortKey: 'payee', visible: true, width: 150, description: '商家或交易对方' },
  { key: 'member', label: '成员', sortKey: 'member', visible: false, width: 112, description: '流水所属成员' },
  { key: 'project', label: '项目', sortKey: 'project', visible: true, width: 132, description: '关联项目' },
  { key: 'note', label: '备注', sortKey: 'note', visible: false, width: 120, description: '流水备注（最多展示 8 个汉字）' },
  { key: 'amount', label: '金额', sortKey: 'amount', visible: true, fixed: 'amount', width: 126, description: '固定显示' }
]
const accounts = ref([]), categories = ref([]), transactions = ref([]), loading = ref(true), editorOpen = ref(false), filterOpen = ref(false), mobileFilterOpen = ref(false), columnsOpen = ref(false), editing = ref(null), saving = ref(false), searchText = ref(''), deleteTarget = ref(null), deleting = ref(false)
const pageSizeOptions = [10, 20, 50, 100]
const pageSize = ref(20)
const currentPage = ref(1)
const filters = reactive({ from: monthStart, to: today, kind: '', accountId: '', primaryCategoryId: '', secondaryCategoryId: '', payee: '', project: '', member: '', note: '' })
const sort = reactive({ key: 'occurredOn', direction: 'desc' })
const columns = reactive(columnDefaults.map(item => ({ ...item })))
const columnWidths = reactive(Object.fromEntries(columnDefaults.map(item => [item.key, item.width])))
const form = reactive({ kind: 'EXPENSE', amount: '', occurredOn: today, accountId: '', targetAccountId: '', categoryId: '', payee: '', member: '', project: '', note: '' })

const visibleColumns = computed(() => columns.filter(item => item.fixed || item.visible))
const configurableColumns = computed(() => columns.filter(item => !item.fixed))
const merchantOptions = computed(() => uniqueOptions('payee'))
const memberOptions = computed(() => uniqueOptions('member'))
const projectOptions = computed(() => uniqueOptions('project'))
const activeFilterCount = computed(() => (filters.from || filters.to ? 1 : 0) + Object.entries(filters).filter(([key, value]) => !['from', 'to'].includes(key) && value).length)
const rangeLabel = computed(() => filters.from || filters.to ? `${filters.from || '最早'} 至 ${filters.to || '今天'}` : '全部日期')

const filteredTransactions = computed(() => transactions.value.filter(item => {
  if (filters.from && item.occurredOn < filters.from) return false
  if (filters.to && item.occurredOn > filters.to) return false
  if (filters.kind && item.kind !== filters.kind) return false
  if (filters.accountId && ![item.accountId, item.targetAccountId].some(value => String(value || '') === filters.accountId)) return false
  const category = categories.value.find(value => String(value.id) === String(item.categoryId))
  if (filters.primaryCategoryId && String(category?.parentId || category?.id || '') !== filters.primaryCategoryId) return false
  if (filters.secondaryCategoryId && String(item.categoryId || '') !== filters.secondaryCategoryId) return false
  if (!includes(item.payee, filters.payee) || !includes(item.project, filters.project) || !includes(item.member, filters.member) || !includes(item.note, filters.note)) return false
  if (searchText.value && ![item.payee, item.project, item.member, item.note, item.categoryName, item.accountName, item.targetAccountName].some(value => includes(value, searchText.value))) return false
  return true
}))
const sortedTransactions = computed(() => [...filteredTransactions.value].sort((left, right) => {
  const a = sort.key === 'amount' ? Number(left[sort.key]) : String(left[sort.key] || '').toLocaleLowerCase()
  const b = sort.key === 'amount' ? Number(right[sort.key]) : String(right[sort.key] || '').toLocaleLowerCase()
  const result = a < b ? -1 : a > b ? 1 : Number(left.id) - Number(right.id)
  return sort.direction === 'asc' ? result : -result
}))
const totalPages = computed(() => Math.max(1, Math.ceil(sortedTransactions.value.length / pageSize.value)))
const pageStart = computed(() => (currentPage.value - 1) * pageSize.value)
const pageEnd = computed(() => Math.min(pageStart.value + pageSize.value, sortedTransactions.value.length))
const paginatedTransactions = computed(() => sortedTransactions.value.slice(pageStart.value, pageEnd.value))
const summary = computed(() => filteredTransactions.value.reduce((result, item) => {
  const amount = Number(item.amount) || 0
  if (item.kind === 'INCOME') result.income += amount
  if (item.kind === 'EXPENSE') result.expense += amount
  if (['INCOME', 'BORROW_IN', 'COLLECT_DEBT'].includes(item.kind)) result.net += amount
  if (['EXPENSE', 'LEND_OUT', 'REPAY_DEBT'].includes(item.kind)) result.net -= amount
  return result
}, { income: 0, expense: 0, net: 0 }))
const kindSummary = computed(() => kindOptions.map(item => ({ ...item, count: filteredTransactions.value.filter(row => row.kind === item.value).length })))

function includes(value, keyword) { return !keyword || String(value || '').toLocaleLowerCase().includes(String(keyword).trim().toLocaleLowerCase()) }
function uniqueOptions(field) { return [...new Set(transactions.value.map(item => String(item[field] || '').trim()).filter(Boolean))].sort((left, right) => left.localeCompare(right, 'zh-CN')) }
function kindMeta(kind) { return kindOptions.find(item => item.value === kind) || kindOptions[0] }
function amountClass(item) { return kindMeta(item.kind).tone === 'in' ? 'flow-income' : kindMeta(item.kind).tone === 'out' ? 'flow-expense' : 'flow-transfer' }
function amountPrefix(item) { return kindMeta(item.kind).tone === 'in' ? '+' : kindMeta(item.kind).tone === 'out' ? '−' : '' }
function money(value) { return Number(value || 0).toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 }) }
function formatDate(value) { return String(value || '').replaceAll('-', '/') }
function columnClass(column) { return { 'fixed-category': column.fixed === 'category', 'fixed-amount': column.fixed === 'amount', 'amount-column': column.key === 'amount', 'note-column': column.key === 'note' } }
function openFilters() { if (window.matchMedia('(max-width: 767px)').matches) mobileFilterOpen.value = true; else filterOpen.value = true }
function updateFilter({ key, value }) { filters[key] = value }
function resetFilters() { Object.assign(filters, { from: '', to: '', kind: '', accountId: '', primaryCategoryId: '', secondaryCategoryId: '', payee: '', project: '', member: '', note: '' }); searchText.value = '' }
function resetPeriod() { filters.from = ''; filters.to = '' }
function setQuickPeriod(period) { const year = today.slice(0, 4); filters.from = period === 'month' ? monthStart : `${year}-01-01`; filters.to = period === 'month' ? today : `${year}-12-31` }
function toggleKind(kind) { filters.kind = filters.kind === kind ? '' : kind }
function setColumnVisibility(key, value) { const column = columns.find(item => item.key === key); if (column) column.visible = value }
function sortBy(column) { if (!column.sortKey) return; if (sort.key === column.sortKey) sort.direction = sort.direction === 'asc' ? 'desc' : 'asc'; else { sort.key = column.sortKey; sort.direction = column.sortKey === 'occurredOn' ? 'desc' : 'asc' }; currentPage.value = 1 }
function startResize(event, key) { const startX = event.clientX, startWidth = columnWidths[key], maxWidth = key === 'note' ? 120 : 360; const move = current => { columnWidths[key] = Math.max(82, Math.min(maxWidth, startWidth + current.clientX - startX)) }; const stop = () => { window.removeEventListener('pointermove', move); window.removeEventListener('pointerup', stop) }; window.addEventListener('pointermove', move); window.addEventListener('pointerup', stop) }
function blankForm() { Object.assign(form, { kind: 'EXPENSE', amount: '', occurredOn: today, accountId: accounts.value[0] ? String(accounts.value[0].id) : '', targetAccountId: '', categoryId: '', payee: '', member: '', project: '', note: '' }) }
function openCreate() { editing.value = null; blankForm(); editorOpen.value = true }
function openEdit(item) { editing.value = item; Object.assign(form, { kind: item.kind, amount: item.amount, occurredOn: item.occurredOn, accountId: String(item.accountId), targetAccountId: item.targetAccountId ? String(item.targetAccountId) : '', categoryId: item.categoryId ? String(item.categoryId) : '', payee: item.payee || '', member: item.member || '', project: item.project || '', note: item.note || '' }); editorOpen.value = true }
function openCopy(item) { editing.value = null; Object.assign(form, { kind: item.kind, amount: item.amount, occurredOn: today, accountId: String(item.accountId), targetAccountId: item.targetAccountId ? String(item.targetAccountId) : '', categoryId: item.categoryId ? String(item.categoryId) : '', payee: item.payee || '', member: item.member || '', project: item.project || '', note: item.note || '' }); editorOpen.value = true }
async function saveTransaction() { saving.value = true; try { const payload = { ...form, amount: Number(form.amount), accountId: Number(form.accountId), targetAccountId: form.targetAccountId ? Number(form.targetAccountId) : null, categoryId: Number(form.categoryId), clientOpId: `web-flow-${Date.now()}` }; if (editing.value) await apiUpdateLedgerTransaction(editing.value.id, payload, editing.value.revision); else await apiCreateLedgerTransaction(payload, payload.clientOpId); editorOpen.value = false; ElMessage.success(editing.value ? '流水已更新' : '流水已添加'); await loadData() } catch (error) { ElMessage.error(error.response?.data?.detail || '流水保存失败') } finally { saving.value = false } }
async function removeTransaction() { if (!deleteTarget.value) return; deleting.value = true; try { await apiDeleteLedgerTransaction(deleteTarget.value.id, deleteTarget.value.revision); deleteTarget.value = null; ElMessage.success('流水已删除'); await loadData() } catch (error) { ElMessage.error(error.response?.data?.detail || '流水删除失败') } finally { deleting.value = false } }
async function loadData() { loading.value = true; try { const [accountRows, categoryRows, transactionRows] = await Promise.all([apiListLedgerAccounts(), apiListLedgerCategories(), apiListLedgerTransactions({ limit: 2000 })]); accounts.value = accountRows || []; categories.value = categoryRows || []; transactions.value = transactionRows || [] } catch (error) { ElMessage.error(error.response?.data?.detail || '流水读取失败') } finally { loading.value = false } }

watch(filteredTransactions, () => { currentPage.value = 1 })
watch(totalPages, pages => { if (currentPage.value > pages) currentPage.value = pages })
onMounted(loadData)
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
.flow-resize-handle { position: absolute; top: 8px; right: -2px; width: 5px; height: 26px; cursor: col-resize; z-index: 7 }
.flow-resize-handle::after { content: ''; position: absolute; left: 2px; top: 5px; width: 1px; height: 16px; background: var(--line2) }
.flow-desktop-table :deep(.fixed-category) { position: sticky; left: 0; z-index: 3; box-shadow: 1px 0 0 var(--line) }
.flow-desktop-table :deep(th.fixed-category) { z-index: 6 }
.flow-desktop-table :deep(.fixed-amount) { position: sticky; right: 108px; z-index: 3; box-shadow: -1px 0 0 var(--line) }
.flow-desktop-table :deep(th.fixed-amount) { z-index: 6 }
.flow-desktop-table :deep(.fixed-actions) { position: sticky; right: 0; z-index: 3; background: var(--card) }
.flow-desktop-table :deep(th.fixed-actions) { z-index: 6; background: var(--paper) }
.amount-column { text-align: right }.amount-column .flow-sort-button { justify-content: flex-end }
.flow-category { color: var(--ink); font-weight: 650 }
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
.flow-editor { display: flex; flex-direction: column; gap: 15px }
.flow-editor-footer { display: flex; justify-content: flex-end; gap: 8px; padding-top: 8px }
.flow-delete-copy { margin: 0; color: var(--ink2); font-size: 13px; line-height: 1.7 }
.flow-delete-confirm { border-color: var(--down) !important; background: var(--down) !important; color: #fff !important }
.flow-column-settings { display: flex; flex-direction: column }
.flow-column-settings>div { display: flex; align-items: center; justify-content: space-between; gap: 20px; min-height: 58px; border-bottom: 1px solid var(--line) }
.flow-column-settings>div:last-child { border-bottom: 0 }.flow-column-settings span { min-width: 0 }.flow-column-settings b,.flow-column-settings small { display: block }.flow-column-settings b { color: var(--ink); font-size: 13px }.flow-column-settings small { margin-top: 2px; color: var(--muted); font-size: 11px }
.flow-fixed-column>span:last-child { color: var(--muted); font-size: 11px }
@media(max-width:1023px){.flow-workspace{grid-template-columns:220px minmax(0,1fr)}.flow-sidebar{position:static}.flow-desktop-table :deep(.ui-table-wrap){max-height:none}}
@media(max-width:767px){.ledger-flow-page,.flow-main,.flow-table-card{min-width:0;max-width:100%}.flow-heading{align-items:flex-start}.flow-heading-actions{width:100%;min-width:0}.flow-heading-actions .ui-button{min-width:0;flex:1;padding:0 7px}.flow-workspace{display:block;min-width:0}.flow-sidebar{display:none}.flow-table-card{width:100%;margin:0 auto;box-sizing:border-box}.flow-table-card :deep(> header){padding:13px 16px}.flow-table-head{min-width:0;align-items:flex-start;flex-direction:column;gap:10px}.flow-search{width:100%;max-width:100%}.flow-desktop-table{display:none}.flow-mobile-list{display:flex;min-width:0;flex-direction:column}.flow-mobile-item{min-width:0;max-width:100%;padding:11px 12px;border-bottom:1px solid var(--line);background:var(--card)}.flow-mobile-item:last-child{border-bottom:0}.flow-mobile-top,.flow-mobile-title,.flow-mobile-actions{display:flex;min-width:0;max-width:100%;align-items:center;justify-content:space-between;gap:8px}.flow-mobile-top>b{min-width:0;font-size:14px}.flow-mobile-title{margin-top:8px}.flow-mobile-title strong{min-width:0;overflow:hidden;color:var(--ink);font-size:13px;text-overflow:ellipsis;white-space:nowrap}.flow-mobile-title span{flex:0 0 auto;color:var(--muted);font-size:10px}.flow-mobile-meta{display:flex;min-width:0;max-width:100%;flex-wrap:wrap;gap:3px 9px;margin-top:5px;color:var(--ink2);font-size:10px}.flow-mobile-meta span{min-width:0;max-width:100%;overflow:hidden;text-overflow:ellipsis;white-space:nowrap}.flow-mobile-note{display:block;max-width:100%;margin:6px 0 0;overflow:hidden;color:var(--muted);font-size:10px;text-overflow:ellipsis;white-space:nowrap}.flow-mobile-actions{justify-content:flex-end;margin-top:7px}.flow-mobile-actions button{display:inline-flex;min-width:0;align-items:center;gap:3px;padding:3px 5px;border:0;background:transparent;color:var(--ink2);font-size:10px}.flow-mobile-actions button.danger{color:var(--down)}.flow-mobile-actions svg{width:12px;height:12px}.flow-table-footer{min-height:42px;padding:8px 12px;align-items:flex-start;flex-direction:column;font-size:10px}.flow-table-totals,.flow-pagination{width:100%;justify-content:space-between;gap:6px}.flow-pagination label{gap:4px}.flow-pagination button{min-width:46px;padding:0 5px}.flow-pagination>b{display:none}}
</style>
