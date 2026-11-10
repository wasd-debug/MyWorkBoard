<template>
  <HomeWidgetFrame title="预算执行" kicker="BUDGET" :meta="monthLabel" :editing="editing">
    <template #actions>
      <router-link class="bd-manage" to="/ledger">管理</router-link>
    </template>

    <div v-if="rows.length" class="bd-body">
      <div class="bd-head">
        <div>
          <small>{{ totalRow ? (totalRow.remaining >= 0 ? '本月剩余' : '本月超支') : '已支出' }}</small>
          <b :class="totalRow && totalRow.remaining < 0 ? 'is-over' : ''">
            ¥{{ money(totalRow ? Math.abs(totalRow.remaining) : rows.reduce((sum, row) => sum + row.spent, 0)) }}
          </b>
        </div>
        <span v-if="totalRow">预算 ¥{{ money(totalRow.amount) }}</span>
      </div>
      <div class="bd-track" :class="{ over: totalRow && totalRow.ratio > 1 }">
        <i :style="{ width: `${Math.min(100, Math.round((totalRow?.ratio || 0) * 100))}%` }" />
      </div>
      <p class="bd-share">{{ totalRow ? `${Math.round(totalRow.ratio * 100)}% 已用` : `${rows.length} 项分类预算` }}</p>

      <ul class="bd-list">
        <li v-for="row in detailRows" :key="row.id">
          <span class="bd-name" :title="row.name">{{ row.name }}</span>
          <span class="bd-value">¥{{ money(row.spent) }}<i> / ¥{{ money(row.amount) }}</i></span>
          <div class="bd-track small" :class="{ over: row.ratio > 1 }">
            <i :style="{ width: `${Math.min(100, Math.round(row.ratio * 100))}%` }" />
          </div>
        </li>
      </ul>
    </div>
    <div v-else class="hw-empty">
      <b>本月还没有预算</b>
      <span>在账本页设置月度总预算或分类预算</span>
    </div>

    <template #footer>
      <span>{{ rows.length }} 项预算</span>
      <router-link class="bd-more" to="/ledger">去设置 →</router-link>
    </template>
  </HomeWidgetFrame>
</template>

<script setup>
import { computed } from 'vue'
import HomeWidgetFrame from './HomeWidgetFrame.vue'
import { useLedgerStore } from '../../stores/ledger.js'
import { budgetExecutions, monthKeyOf } from './homeMetrics.js'

defineProps({ editing: Boolean })

const ledgerStore = useLedgerStore()
const monthKey = monthKeyOf(new Date())
const monthLabel = `${Number(monthKey.slice(5))} 月`
const rows = computed(() => budgetExecutions(ledgerStore.budgets, ledgerStore.transactions, ledgerStore.visibleCategories, monthKey))
const totalRow = computed(() => rows.value.find(row => row.total) || null)
const detailRows = computed(() => rows.value.filter(row => !row.total).slice(0, 4))

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
.bd-head > span { color: var(--muted); font-size: 11px; }
.bd-track { flex: none; height: 7px; overflow: hidden; border-radius: 999px; background: var(--paper); }
.bd-track i { display: block; height: 100%; border-radius: inherit; background: var(--accent); transition: width .3s ease; }
.bd-track.over i { background: var(--down); }
.bd-track.small { height: 4px; margin-top: 4px; }
.bd-share { flex: none; margin: 0; color: var(--muted); font-size: 10.5px; }
.bd-list { display: flex; min-height: 0; flex: 1; flex-direction: column; gap: 8px; overflow-y: auto; margin: 2px 0 0; padding: 7px 0 0; border-top: 1px dashed var(--line); list-style: none; scrollbar-width: thin; }
.bd-list li { min-width: 0; flex: none; }
.bd-name { display: block; overflow: hidden; color: var(--ink2); font-size: 11.5px; font-weight: 600; line-height: 1.28; text-overflow: ellipsis; white-space: nowrap; }
.bd-value { display: block; margin-top: 2px; color: var(--ink); font-size: 11px; font-variant-numeric: tabular-nums; line-height: 1.3; }
.bd-value i { color: var(--muted); font-style: normal; }
.bd-more { color: var(--accent); font-weight: 650; }
</style>
