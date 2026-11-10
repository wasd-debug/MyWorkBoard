<template>
  <HomeWidgetFrame title="支出分类" kicker="CATEGORY" :meta="`${scope === 'month' ? '本月' : '本年'}共 ${rowCount} 类支出`" :editing="editing">
    <template #actions>
      <div class="hw-seg">
        <button type="button" :class="{ active: scope === 'month' }" @click="scope = 'month'">本月</button>
        <button type="button" :class="{ active: scope === 'year' }" @click="scope = 'year'">本年</button>
      </div>
    </template>

    <div class="ct-head">
      <span>总支出 <b>¥{{ money(total) }}</b></span>
      <div class="hw-seg">
        <button type="button" :class="{ active: level === 'primary' }" @click="level = 'primary'">一级</button>
        <button type="button" :class="{ active: level === 'secondary' }" @click="level = 'secondary'">二级</button>
      </div>
    </div>

    <ul v-if="rows.length" class="ct-list">
      <li v-for="row in rows" :key="row.name">
        <div class="ct-line">
          <span class="ct-name"><i :style="{ background: row.color }" />{{ row.name }}</span>
          <span class="ct-amount">¥{{ money(row.amount) }}<small>{{ Math.round(row.share * 100) }}%</small></span>
        </div>
        <div class="ct-track"><i :style="{ width: `${Math.max(2, Math.round(row.share * 100))}%`, background: row.color }" /></div>
      </li>
    </ul>
    <div v-else class="hw-empty">
      <b>还没有支出记录</b>
      <span>{{ scope === 'month' ? '本月' : '本年' }}记一笔后这里会出现分类排行</span>
    </div>

    <template #footer>
      <span>共 {{ rowCount }} 类{{ rows.length < rowCount ? ` · 显示前 ${rows.length}` : '' }}</span>
      <router-link class="ct-more" to="/ledger/reports">分类报表 →</router-link>
    </template>
  </HomeWidgetFrame>
</template>

<script setup>
import { computed, ref } from 'vue'
import HomeWidgetFrame from './HomeWidgetFrame.vue'
import { useLedgerStore } from '../../stores/ledger.js'
import { categoryBreakdown } from './homeMetrics.js'

defineProps({ editing: Boolean })

const ledgerStore = useLedgerStore()
const scope = ref('month')
const level = ref('primary')

const all = computed(() => categoryBreakdown(ledgerStore.transactions, ledgerStore.visibleCategories, { scope: scope.value, level: level.value }))
const rows = computed(() => all.value.slice(0, 5))
const rowCount = computed(() => all.value.length)
const total = computed(() => all.value.reduce((sum, row) => sum + row.amount, 0))

function money(value) {
  return Number(value || 0).toLocaleString('zh-CN', { maximumFractionDigits: 0 })
}
</script>

<style scoped>
.ct-head { display: flex; align-items: center; justify-content: space-between; gap: 10px; margin-bottom: 8px; }
.ct-head > span { color: var(--muted); font-size: 11px; }
.ct-head b { color: var(--ink); font-size: 13px; font-variant-numeric: tabular-nums; }
.ct-list { display: flex; min-height: 0; flex-direction: column; gap: 8px; overflow-y: auto; margin: 0; padding: 0; list-style: none; scrollbar-width: thin; }
.ct-list li { flex: none; }
.ct-line { display: flex; align-items: center; justify-content: space-between; gap: 10px; }
.ct-name { display: inline-flex; min-width: 0; align-items: center; gap: 6px; overflow: hidden; color: var(--ink2); font-size: 11.5px; text-overflow: ellipsis; white-space: nowrap; }
.ct-name i { flex: 0 0 7px; width: 7px; height: 7px; border-radius: 2px; }
.ct-amount { flex: 0 0 auto; color: var(--ink); font-size: 11.5px; font-weight: 650; font-variant-numeric: tabular-nums; }
.ct-amount small { margin-left: 5px; color: var(--muted); font-size: 10px; font-weight: 500; }
.ct-track { height: 4px; margin-top: 4px; overflow: hidden; border-radius: 999px; background: var(--paper); }
.ct-track i { display: block; height: 100%; border-radius: inherit; }
.ct-more { color: var(--accent); font-weight: 650; }
</style>
