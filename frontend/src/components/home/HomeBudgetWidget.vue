<template>
  <HomeWidgetFrame title="预算执行" kicker="BUDGET" :meta="monthLabel" :editing="editing">
    <template #actions>
      <button v-if="!rows.length" class="hw-btn" type="button" @click="startEdit(null)"><Plus :size="13" />设总预算</button>
      <button v-else class="hw-btn ghost" type="button" @click="startEdit(totalSource)">改预算</button>
    </template>

    <div v-if="rows.length" class="bd-body">
      <div class="bd-head">
        <div>
          <small>{{ totalRow ? (totalRow.remaining >= 0 ? '本月剩余' : '本月超支') : '已支出' }}</small>
          <b :class="totalRow && totalRow.remaining < 0 ? 'is-over' : ''">
            ¥{{ money(totalRow ? Math.abs(totalRow.remaining) : rows.reduce((sum, row) => sum + row.spent, 0)) }}
          </b>
        </div>
        <button class="bd-total" type="button" :title="totalRow ? '修改月度总预算' : '设置月度总预算'" @click="startEdit(totalSource)">
          {{ totalRow ? `预算 ¥${money(totalRow.amount)}` : '设置总预算' }}
        </button>
      </div>
      <div class="bd-track" :class="{ over: totalRow && totalRow.ratio > 1 }">
        <i :style="{ width: `${Math.min(100, Math.round((totalRow?.ratio || 0) * 100))}%` }" />
      </div>
      <p class="bd-share">{{ totalRow ? `${Math.round(totalRow.ratio * 100)}% 已用` : `${rows.length} 项分类预算` }}</p>

      <ul class="bd-list">
        <li v-for="row in detailSources" :key="row.id">
          <div class="bd-line">
            <span class="bd-name" :title="row.name">{{ row.name }}</span>
            <button class="bd-value" type="button" :title="`修改${row.name}预算`" @click="startEdit(row.source)">
              ¥{{ money(row.spent) }}<i> / ¥{{ money(row.amount) }}</i>
            </button>
          </div>
          <div class="bd-track small" :class="{ over: row.ratio > 1 }">
            <i :style="{ width: `${Math.min(100, Math.round(row.ratio * 100))}%` }" />
          </div>
        </li>
      </ul>
    </div>
    <div v-else class="hw-empty">
      <b>本月还没有预算</b>
      <span>点右上角「设总预算」，或到账本页按分类设置</span>
      <button class="hw-btn" type="button" @click="startEdit(null)"><Plus :size="12" />设总预算</button>
    </div>

    <form v-if="editorOpen" class="bd-editor" @submit.prevent="save">
      <label>
        <span>{{ editingLabel }}</span>
        <input
          ref="amountInput"
          v-model="amount"
          type="number"
          min="0.01"
          step="0.01"
          inputmode="decimal"
          :aria-label="`${editingLabel}金额`"
          required
        />
      </label>
      <button class="hw-btn" type="button" @click="cancelEdit">取消</button>
      <button class="hw-btn primary" type="submit" :disabled="saving">{{ saving ? '保存中…' : '保存' }}</button>
      <p v-if="error" class="bd-error">{{ error }}</p>
    </form>

    <template #footer>
      <span>{{ rows.length }} 项预算</span>
      <router-link class="bd-more" to="/ledger">去账本设置 →</router-link>
    </template>
  </HomeWidgetFrame>
</template>

<script setup>
import { computed, nextTick, ref } from 'vue'
import { Plus } from 'lucide-vue-next'
import HomeWidgetFrame from './HomeWidgetFrame.vue'
import { useLedgerStore } from '../../stores/ledger.js'
import { message } from '../../services/message.js'
import { budgetExecutions, monthKeyOf } from './homeMetrics.js'
import { budgetAmountUpdate } from './homeWidgetEdits.js'

/**
 * 预算就地改额：写路径仍是 `ledgerStore.saveResource('budget', ...)`，
 * 与账本页共用同一套离线队列与 revision 合并，首页不另存一份预算状态。
 */
defineProps({ editing: Boolean })

const ledgerStore = useLedgerStore()
const monthKey = monthKeyOf(new Date())
const monthLabel = `${Number(monthKey.slice(5))} 月`

const rawBudgets = computed(() => ledgerStore.budgets.filter(item => !item.deleted))
const rows = computed(() => budgetExecutions(
  ledgerStore.budgets, ledgerStore.transactions, ledgerStore.visibleCategories, monthKey
))
const totalRow = computed(() => rows.value.find(row => row.total) || null)
const detailRows = computed(() => rows.value.filter(row => !row.total).slice(0, 4))

/** 执行行只带展示字段，改额要拿回原始预算（id / revision / categoryId） */
const budgetById = computed(() => new Map(rawBudgets.value.map(item => [String(item.id || item.publicId), item])))
const rowSource = computed(() => {
  const map = new Map()
  for (const budget of rawBudgets.value) {
    const categoryId = budget.categoryId ? String(budget.categoryId) : ''
    map.set(categoryId ? `c:${categoryId}` : 'total', budget)
  }
  return map
})
const totalSource = computed(() => rowSource.value.get('total') || null)
const detailSources = computed(() => detailRows.value.map(row => ({ ...row, source: rowSource.value.get(`c:${row.id}`) || budgetById.value.get(String(row.id)) || null })))

const editorOpen = ref(false)
const editingBudget = ref(null)
const amount = ref('')
const saving = ref(false)
const error = ref('')
const amountInput = ref(null)

const editingLabel = computed(() => {
  if (!editingBudget.value) return '月度总预算'
  const categoryId = editingBudget.value.categoryId
  if (!categoryId) return '月度总预算'
  const category = ledgerStore.visibleCategories.find(item => String(item.id) === String(categoryId))
  const parent = category?.parentId
    ? ledgerStore.visibleCategories.find(item => String(item.id) === String(category.parentId)) : null
  return category ? `${parent ? `${parent.name} / ` : ''}${category.name}` : '分类预算'
})

async function startEdit(budget) {
  editingBudget.value = budget || null
  amount.value = budget ? String(Number(budget.amount ?? budget.budget ?? '')) : ''
  error.value = ''
  editorOpen.value = true
  await nextTick()
  amountInput.value?.focus()
  amountInput.value?.select?.()
}

function cancelEdit() {
  editorOpen.value = false
  editingBudget.value = null
  error.value = ''
}

async function save() {
  if (saving.value) return
  const payload = budgetAmountUpdate(editingBudget.value, amount.value, monthKey)
  if (!payload) { error.value = '请输入大于 0 的预算金额'; return }
  saving.value = true
  error.value = ''
  try {
    await ledgerStore.saveResource('budget', payload)
    message.success(ledgerStore.online ? '预算已保存' : '预算已离线保存')
    cancelEdit()
    if (ledgerStore.online) {
      await ledgerStore.syncNow()
      if (!ledgerStore.syncError) await ledgerStore.refreshResources(monthKey)
    }
  } catch (exception) {
    error.value = exception?.response?.data?.detail || exception?.message || '预算保存失败'
  } finally {
    saving.value = false
  }
}

function money(value) {
  return Number(value || 0).toLocaleString('zh-CN', { maximumFractionDigits: 0 })
}
</script>

<style scoped>
.bd-manage { color: var(--accent); font-size: 11px; font-weight: 650; }
.bd-body { display: flex; height: 100%; min-height: 0; flex-direction: column; gap: 6px; }
.bd-head { flex: none; display: flex; align-items: flex-end; justify-content: space-between; gap: 10px; }
.bd-head small { display: block; color: var(--muted); font-size: 10px; }
.bd-head b { font-size: 21px; font-weight: 730; letter-spacing: -.03em; font-variant-numeric: tabular-nums; }
.bd-head b.is-over { color: var(--down); }
.bd-total { padding: 2px 6px; border: 1px dashed transparent; border-radius: 7px; background: transparent; color: var(--muted); font-size: 11px; }
.bd-total:hover { border-color: var(--line2); background: var(--paper); color: var(--accent); }
.bd-track { flex: none; height: 7px; overflow: hidden; border-radius: 999px; background: var(--paper); }
.bd-track i { display: block; height: 100%; border-radius: inherit; background: var(--accent); transition: width .3s ease; }
.bd-track.over i { background: var(--down); }
.bd-track.small { height: 4px; margin-top: 4px; }
.bd-share { flex: none; margin: 0; color: var(--muted); font-size: 10.5px; }
.bd-list { display: flex; min-height: 0; flex: 1; flex-direction: column; gap: 8px; overflow-y: auto; margin: 2px 0 0; padding: 7px 0 0; border-top: 1px dashed var(--line); list-style: none; scrollbar-width: thin; }
.bd-list li { min-width: 0; flex: none; }
.bd-line { display: flex; align-items: baseline; justify-content: space-between; gap: 8px; }
.bd-name { min-width: 0; flex: 1; overflow: hidden; color: var(--ink2); font-size: 11.5px; font-weight: 600; line-height: 1.28; text-overflow: ellipsis; white-space: nowrap; }
.bd-value { flex: 0 0 auto; padding: 1px 5px; border: 1px dashed transparent; border-radius: 6px; background: transparent; color: var(--ink); font-size: 11px; font-variant-numeric: tabular-nums; }
.bd-value:hover { border-color: var(--line2); background: var(--paper); color: var(--accent); }
.bd-value i { color: var(--muted); font-style: normal; }
.bd-editor { display: flex; flex: none; flex-wrap: wrap; align-items: flex-end; gap: 8px; margin-top: 8px; padding: 9px 10px; border: 1px solid color-mix(in srgb, var(--accent) 30%, var(--line)); border-radius: 11px; background: color-mix(in srgb, var(--accent-soft) 32%, var(--card)); }
.bd-editor label { display: flex; min-width: 0; flex: 1; flex-direction: column; gap: 3px; color: var(--muted); font-size: 10.5px; }
.bd-editor input { width: 100%; height: 30px; padding: 0 8px; border: 1px solid var(--line2); border-radius: 8px; background: var(--card); color: var(--ink); font-size: 12.5px; font-variant-numeric: tabular-nums; }
.bd-editor input:focus { border-color: var(--accent); outline: 0; box-shadow: 0 0 0 3px color-mix(in srgb, var(--accent) 12%, transparent); }
.bd-editor .hw-btn { margin-left: auto; }
.bd-editor .hw-btn + .hw-btn { margin-left: 0; }
.bd-error { flex-basis: 100%; margin: 0; color: var(--down); font-size: 11px; }
.bd-more { color: var(--accent); font-weight: 650; }
@media (max-width: 767px) {
  .bd-value { padding: 4px 6px; }
}
</style>
