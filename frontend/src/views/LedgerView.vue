<template>
  <section class="ledger-page">
    <LoadingOverlay :open="pageLoading || aiLoading || deleting" :label="aiLoading ? '正在等待大模型返回…' : deleting ? '正在删除流水…' : '正在更新账本总览…'" />
    <div class="page-heading ledger-heading"><div><div class="label">PERSONAL FINANCE / LEDGER</div><h1>账本 <span class="heading-slash">// OVERVIEW</span></h1><p class="muted">记录每一笔流动，月底自动对账。</p></div><div class="ledger-actions"><LedgerActionIcon action="ai" label="自然语言记账" @click="aiOpen=true"/><LedgerActionIcon action="download" label="导出 CSV" @click="exportCsv"/><label class="ledger-import-icon" :class="{disabled:importState.active}" title="导入 CSV / Excel" aria-label="导入 CSV / Excel"><Upload aria-hidden="true"/><input type="file" accept=".csv,.xlsx,.xls,text/csv,application/vnd.openxmlformats-officedocument.spreadsheetml.sheet" :disabled="importState.active" hidden @change="importFile" /></label><LedgerActionIcon action="layout" label="编辑首页" @click="layoutOpen=true"/><LedgerActionIcon action="add" label="记一笔" @click="openCreate"/></div></div>
    <div class="ledger-status" :class="{offline:!online}"><span class="status-dot" :class="{online}"></span><span>{{ online ? '在线 · 自动同步' : '离线 · 已保存到本机' }}</span><span v-if="syncing">正在同步…</span><button v-if="!online" class="text-button" @click="loadData">重试</button></div>
    <div v-if="importState.active" class="import-progress" role="status" aria-live="polite">
      <div class="import-progress-head"><span><b>{{importState.phase}}</b><small>{{importState.filename}}</small></span><strong>{{importState.processing?'处理中':`${importState.progress}%`}}</strong></div>
      <div class="import-progress-track" :class="{processing:importState.processing}" role="progressbar" aria-label="账本导入进度" aria-valuemin="0" aria-valuemax="100" :aria-valuenow="importState.processing?null:importState.progress" :aria-valuetext="importState.processing?`${importState.phase}处理中`:`${importState.progress}%`"><i :style="{width:`${importState.progress}%`}"></i></div>
      <small>{{importState.detail}}</small>
    </div>
    <div class="overview-toolbar"><div class="overview-period-control"><div class="seg overview-range-mode"><button :class="{active:rangeMode==='month'}" @click="setRangeMode('month')">按月</button><button :class="{active:rangeMode==='custom'}" @click="setRangeMode('custom')">自定义</button></div><div v-if="rangeMode==='month'" class="period-picker"><button @click="shiftMonth(-1)">‹</button><input v-model="selectedMonth" type="month" aria-label="选择账本月份"/><button @click="shiftMonth(1)">›</button></div><div v-else class="home-custom-range"><input v-model="customFrom" type="date" :max="customTo" aria-label="首页统计开始日期" @change="applyHomeCustomRange('from')"/><span>—</span><input v-model="customTo" type="date" :min="customFrom" aria-label="首页统计结束日期" @change="applyHomeCustomRange('to')"/></div></div><span>{{ overviewRangeLabel }} 首页总览</span></div>
    <div class="ledger-summary-grid">
      <Card v-show="widgetVisible('summary','income')" :style="{order:widgetOrder('summary','income')}"><template #header><div class="summary-label">{{ summaryPeriodLabel }}收入</div></template><div class="summary-value income" :title="`¥${money(summary.income)}`">¥{{ money(summary.income) }}</div><div class="summary-meta">{{ summary.incomeCount }} 笔流入</div></Card>
      <Card v-show="widgetVisible('summary','expense')" :style="{order:widgetOrder('summary','expense')}"><template #header><div class="summary-label">{{ summaryPeriodLabel }}支出</div></template><div class="summary-value expense" :title="`¥${money(summary.expense)}`">¥{{ money(summary.expense) }}</div><div class="summary-meta">{{ summary.expenseCount }} 笔流出</div></Card>
      <Card v-show="widgetVisible('summary','net')" :style="{order:widgetOrder('summary','net')}"><template #header><div class="summary-label">{{ summaryPeriodLabel }}结余</div></template><div class="summary-value" :class="summary.net>=0?'income':'expense'" :title="`¥${money(summary.net)}`">¥{{ money(summary.net) }}</div><div class="summary-meta">收入 − 支出</div></Card>
      <Card v-show="widgetVisible('summary','asset')" :style="{order:widgetOrder('summary','asset')}"><template #header><div class="summary-label">总资产</div></template><div class="summary-value asset" :title="`¥${money(effectiveTotalAssets)}`">¥{{ money(effectiveTotalAssets) }}</div><div class="summary-meta">{{ accounts.length }} 个账户</div></Card>
    </div>
    <div class="home-widget-grid">
      <div v-show="widgetVisible('content','snapshot')" class="widget-full snapshot-grid" :style="{order:widgetOrder('content','snapshot')}"><div v-for="item in snapshots" :key="item.label" class="snapshot-item"><div><strong>{{ item.label }}</strong><small>{{ item.range }}</small></div><div><span class="income">+¥{{ money(item.income) }}</span><span class="expense">−¥{{ money(item.expense) }}</span><b :class="item.net>=0?'income':'expense'">结余 ¥{{ money(item.net) }}</b></div></div></div>
      <Card v-show="widgetVisible('content','daily')" class="chart-card widget-wide" :style="{order:widgetOrder('content','daily')}"><template #header><Head kicker="DAILY SPENDING" :title="rangeMode==='custom'?'所选范围支出趋势':'本月每日支出'" :right="overviewRangeLabel"/></template><div ref="dailyRef" class="chart tall"/></Card>
      <Card v-show="widgetVisible('content','budget')" class="budget-overview widget-narrow" :style="{order:widgetOrder('content','budget')}"><template #header><Head kicker="BUDGET" title="本月支出与预算"><LedgerActionIcon action="manage" label="设置预算" @click="openBudget"/></Head></template><div class="donut-wrap"><div ref="budgetRef" class="donut"/><div class="donut-label"><b>¥{{ money(budgetSummaryFixed.spent) }}</b><span>已支出</span></div></div><div class="budget-total"><span>预算 ¥{{ money(budgetSummaryFixed.budget) }}</span><b :class="budgetLeftFixed<0?'budget-over':'income'">{{ budgetLeftFixed<0?'超出':'剩余' }} ¥{{ money(Math.abs(budgetLeftFixed)) }}</b></div><p v-if="!budgets.length" class="side-empty">设置预算后，在这里跟踪执行情况。</p></Card>
      <Card v-show="widgetVisible('content','category')" class="chart-card category-card widget-wide" :style="{order:widgetOrder('content','category')}"><template #header><Head kicker="CATEGORY RANKING" :title="`${categoryScope==='range'?'所选范围':categoryScope==='month'?'本月':'本年'}支出分类`"><div class="seg"><button :class="{active:categoryScope==='range'}" @click="categoryScope='range'">当前</button><button :class="{active:categoryScope==='month'}" @click="categoryScope='month'">本月</button><button :class="{active:categoryScope==='year'}" @click="categoryScope='year'">本年</button></div><div class="seg"><button :class="{active:categoryLevel==='primary'}" @click="categoryLevel='primary'">一级</button><button :class="{active:categoryLevel==='secondary'}" @click="categoryLevel='secondary'">二级</button></div></Head></template><div ref="categoryRef" class="chart medium"/></Card>
      <Card v-show="widgetVisible('content','budget-detail')" class="widget-narrow" :style="{order:widgetOrder('content','budget-detail')}"><template #header><Head kicker="BUDGET DETAIL" title="预算执行"><LedgerActionIcon action="manage" label="管理预算" @click="openBudget"/></Head></template><div v-if="budgets.length" class="budget-list"><div v-for="budget in budgets" :key="budget.id"><div class="budget-row"><span>{{ budget.category }}</span><span>¥{{ money(budget.spent) }} / ¥{{ money(budget.budget) }}</span></div><div class="budget-track"><i :class="{over:Number(budget.spent)>Number(budget.budget)}" :style="{width:`${Math.min(100,Number(budget.spent)/Math.max(1,Number(budget.budget))*100)}%`}"/></div></div></div><p v-else class="side-empty">还没有预算。</p></Card>
      <Card v-show="widgetVisible('content','annual-bar')" class="chart-card widget-half" :style="{order:widgetOrder('content','annual-bar')}"><template #header><Head kicker="YEARLY CASH FLOW" :title="`${selectedYear} 年每月收支结余`" right="柱状"/></template><div ref="barRef" class="chart medium"/></Card>
      <Card v-show="widgetVisible('content','annual-line')" class="chart-card widget-half" :style="{order:widgetOrder('content','annual-line')}"><template #header><Head kicker="YEARLY TREND" :title="`${selectedYear} 年收支走势`" right="折线"/></template><div ref="lineRef" class="chart medium"/></Card>
      <Card v-show="widgetVisible('content','calendar')" class="widget-full" :style="{order:widgetOrder('content','calendar')}"><template #header><Head kicker="MONTHLY CALENDAR" title="月度记账日历" :right="monthLabel"/></template><div class="weekdays"><span v-for="day in weekdays" :key="day">{{day}}</span></div><div class="calendar"><template v-for="cell in calendarCells" :key="cell.key"><button v-if="cell.day" type="button" :class="['day', {today:cell.today}]" :aria-label="`查看 ${cell.key} 流水`" @click="openDayTransactions(cell.key)"><b>{{cell.day}}</b><span v-if="cell.income" class="income">+{{compact(cell.income)}}</span><span v-if="cell.expense" class="expense">−{{compact(cell.expense)}}</span></button><div v-else class="day blank" aria-hidden="true"></div></template></div></Card>
      <Card v-show="widgetVisible('content','transactions')" class="transactions widget-full" :style="{order:widgetOrder('content','transactions')}"><template #header><Head kicker="TRANSACTIONS" title="最近交易"><div class="seg"><button :class="{active:filter==='all'}" @click="filter='all'">全部</button><button :class="{active:filter==='EXPENSE'}" @click="filter='EXPENSE'">支出</button><button :class="{active:filter==='INCOME'}" @click="filter='INCOME'">收入</button></div></Head></template><div v-if="filteredTransactions.length" class="transaction-table"><Table><thead><tr><th>日期</th><th>描述</th><th>分类</th><th>账户</th><th class="amount">金额</th><th/></tr></thead><tbody><tr v-for="item in filteredTransactions" :key="item.id"><td>{{formatDate(item.occurredOn)}}</td><td class="transaction-description"><b :title="item.payee||item.note||'未命名交易'">{{item.payee||item.note||'未命名交易'}}</b><small v-if="item.note&&item.payee" :title="item.note">{{item.note}}</small></td><td><span class="home-resource"><LedgerResourceIcon :icon="item.categoryIcon||item.parentCategoryIcon" type="category" :color="item.categoryColor||item.parentCategoryColor" compact/><span><b>{{item.categoryName||'未分类'}}</b><small v-if="item.project" class="home-project"><i :style="{background:item.projectColor||'var(--muted)'}"/>{{item.project}}</small></span></span></td><td>{{item.accountName}}</td><td class="amount" :class="amountClass(item.kind)">{{amountSign(item.kind)}}¥{{money(item.amount)}}</td><td class="home-row-actions"><LedgerActionIcon action="edit" label="编辑流水" @click="editTransaction(item)"/><LedgerActionIcon action="delete" label="删除流水" @click="deleteTarget=item"/></td></tr></tbody></Table></div><div v-else class="ledger-empty"><b>还没有交易</b><LedgerActionIcon action="add" label="记一笔" @click="openCreate"/></div><div class="list-footer"><span>显示 {{filteredTransactions.length}} / {{transactions.length}} 笔</span><LedgerActionIcon action="refresh" label="刷新最近交易" @click="loadData"/></div></Card>
      <Card v-show="widgetVisible('content','accounts')" class="widget-full accounts-card" :style="{order:widgetOrder('content','accounts')}"><template #header><Head kicker="ACCOUNTS" title="账户"><LedgerActionIcon action="manage" label="管理账户" @click="openManager"/></Head></template><div class="account-grid"><button v-for="account in accounts" :key="account.id" class="account" @click="openAccountTransactions(account)"><LedgerResourceIcon :icon="account.icon" type="account"/><span><b>{{account.name}}</b><small>{{accountType(account.accountType)}}</small></span><strong>¥{{money(account.balance)}}</strong></button></div><p v-if="!accounts.length" class="side-empty">添加一个账户开始记账。</p><LedgerActionIcon class="account-add" action="add" label="添加账户" @click="openManager"/></Card>
    </div>
    <Sheet v-model:open="editorOpen" :title="editing?'编辑交易':'记一笔'"><form class="form" @submit.prevent="saveTransaction"><LedgerTransactionEditor :model="form" :accounts="accounts" :categories="categories" :usage-transactions="optionTransactions" :kinds="kindOptions" :merchant-options="merchantOptions" :member-options="memberOptions" :project-options="projectOptions" :lock-kind="editing?.kind==='TRANSFER'"/><div class="form-footer"><LedgerActionIcon action="close" label="取消记账" @click="editorOpen=false"/><LedgerActionIcon action="confirm" label="保存交易" type="submit" :disabled="pageLoading"/></div></form></Sheet>
    <Drawer v-model:open="managerOpen" title="账户与分类"><div class="manager-tabs"><button :class="{active:managerTab==='accounts'}" @click="managerTab='accounts'">账户</button><button :class="{active:managerTab==='categories'}" @click="managerTab='categories'">分类</button></div><div v-if="managerTab==='accounts'" class="form"><div v-for="a in accounts" :key="a.id" class="manager-row"><span><b>{{a.name}}</b><small>{{accountType(a.accountType)}} · ¥{{money(a.balance)}}</small></span><LedgerActionIcon action="delete" :label="`删除${a.name}`" @click="removeAccount(a)"/></div><form class="form" @submit.prevent="saveAccount"><input v-model="accountForm.name" class="ui-input" placeholder="账户名称" required/><select v-model="accountForm.accountType"><option value="cash">现金</option><option value="bank">银行卡</option><option value="card">信用卡</option><option value="wallet">电子钱包</option></select><input v-model="accountForm.openingBalance" class="ui-input" type="number" placeholder="期初余额"/><LedgerActionIcon action="add" label="添加账户" type="submit"/></form></div><div v-else class="form"><div v-for="c in categories" :key="c.id" class="manager-row"><span><b>{{categoryPath(c)}}</b><small>{{c.kind==='INCOME'?'收入':'支出'}}</small></span><LedgerActionIcon action="delete" :label="`删除${categoryPath(c)}`" @click="removeCategory(c)"/></div><form class="form" @submit.prevent="saveCategory"><input v-model="categoryForm.name" class="ui-input" placeholder="分类名称" required/><select v-model="categoryForm.kind"><option value="EXPENSE">支出</option><option value="INCOME">收入</option></select><select v-model="categoryForm.parentId"><option value="">一级分类</option><option v-for="c in parents" :key="c.id" :value="String(c.id)">{{c.name}}</option></select><LedgerActionIcon action="add" label="添加分类" type="submit"/></form></div></Drawer>
    <Sheet v-model:open="budgetOpen" title="预算设置"><p class="ai-intro">可设置月度总预算，也可为一级或二级支出分类分别设置预算；流水保存后执行金额会自动更新。</p><div v-for="b in budgets" :key="b.id" class="manager-row"><span><b>{{b.category}}</b><small>¥{{money(b.spent)}} / ¥{{money(b.budget)}}</small></span><LedgerActionIcon action="delete" :label="`删除${b.category}预算`" @click="deleteBudget(b)"/></div><form class="form" @submit.prevent="saveBudget"><label>月份<input v-model="budgetForm.monthKey" class="ui-input" type="month" required/></label><label>预算范围<input v-model="budgetCategoryQuery" class="ui-input" list="budget-category-options" placeholder="输入分类名称，或留空设置月度总预算" autocomplete="off"/><datalist id="budget-category-options"><option value="月度总预算"/><option v-for="c in filteredBudgetCategories" :key="c.id" :value="categoryPath(c)">{{categoryPath(c)}}</option></datalist></label><label>金额<input v-model="budgetForm.amount" class="ui-input" type="number" min=".01" step=".01" required/></label><div class="form-footer"><LedgerActionIcon action="close" label="关闭预算设置" @click="budgetOpen=false"/><LedgerActionIcon action="confirm" label="保存预算" type="submit"/></div></form></Sheet>
    <Sheet v-model:open="aiOpen" title="自然语言记账"><div class="ai-panel"><div class="ai-panel-intro"><div><span class="ai-eyebrow">AI ASSISTED ENTRY</span><h3>把一句话整理成流水</h3><p>识别后会先生成草稿。每一行都可以修改，确认后才会写入账本。</p></div><span class="ai-status"><i :class="{ready:!aiLoading}"></i>{{ aiLoading ? '识别中' : '待审核' }}</span></div><textarea v-model="aiText" class="ui-textarea ai-input" placeholder="例如：今天午饭花了 32 元，晚上打车 26 元"/><div class="ai-panel-actions"><span>支持一段文字解析多笔流水</span><LedgerActionIcon class="ai-button" action="ai" :label="aiLoading?'识别中':'开始识别'" :disabled="aiLoading||!aiText.trim()" @click="aiPreviewRequest"/></div><div v-if="aiPreview" class="ai-preview"><div class="ai-preview-head"><div><b>识别结果</b><span>{{ aiDrafts.length }} 笔草稿 · {{ aiPreview.localFallback ? '本地规则解析' : 'DeepSeek 已返回' }} · 尚未入账</span></div><LedgerActionIcon action="refresh" label="重新识别" :disabled="aiLoading" @click="aiPreviewRequest"/></div><div class="ai-draft-list"><article v-for="(draft,index) in aiDrafts" :key="`${draft.occurredOn||'draft'}-${index}`" class="ai-draft"><div class="ai-draft-head"><span class="ai-draft-index">{{ String(index+1).padStart(2,'0') }}</span><span class="ai-kind">{{ aiKindLabel(draft.kind) }}</span><b :class="aiAmountClass(draft.kind)">{{aiAmountSign(draft.kind)}}¥{{money(draft.amount)}}</b><span class="ai-draft-state" :class="{invalid:draft.warnings?.length}">{{ draft.warnings?.length ? '需要补全' : '已匹配' }}</span></div><div class="ai-draft-fields"><label>金额<input class="ui-input" type="number" min="0.01" step="0.01" :value="draft.amount" @input="updateAiDraft(index,{amount:$event.target.value})"></label><label>日期<input class="ui-input" type="date" :value="draft.occurredOn" @input="updateAiDraft(index,{occurredOn:$event.target.value})"></label><label>账户<select :value="draft.accountId||''" @change="updateAiDraft(index,{accountId:$event.target.value})"><option value="">请选择账户</option><option v-for="account in accounts" :key="account.id" :value="String(account.id)">{{account.name}}</option></select></label><label v-if="draft.kind==='TRANSFER'">转入账户<select :value="draft.targetAccountId||''" @change="updateAiDraft(index,{targetAccountId:$event.target.value})"><option value="">请选择账户</option><option v-for="account in accounts" :key="account.id" :value="String(account.id)">{{account.name}}</option></select></label><template v-if="requiresAiCategory(draft.kind)"><label>一级分类<select :value="draft.parentCategoryId||parentCategoryId(draft)" @change="updateAiCategory(index,{parentCategoryId:$event.target.value})"><option value="">请选择一级分类</option><option v-for="parent in aiCategoryParents(draft.kind)" :key="parent.id" :value="String(parent.id)">{{parent.name}}</option></select></label><label>二级分类<select :value="draft.categoryId||''" @change="updateAiCategory(index,{categoryId:$event.target.value})"><option value="">请选择二级分类</option><option v-for="category in aiCategoryChildren(draft)" :key="category.id" :value="String(category.id)">{{category.name}}</option></select></label></template><label class="ai-note-field">备注<input class="ui-input" :value="draft.note||''" maxlength="500" @input="updateAiDraft(index,{note:$event.target.value})"></label></div><p v-if="draft.warnings?.length" class="ai-warning"><span>!</span>{{draft.warnings.join('；')}}</p></article></div><div class="ai-preview-footer"><span v-if="!aiCanConfirm">请补全带警告的字段后确认</span><LedgerActionIcon action="confirm" label="确认全部记账" :disabled="!aiCanConfirm||pageLoading" @click="confirmAi"/></div></div></div></Sheet>
    <Sheet v-model:open="layoutOpen" title="自定义首页"><p class="ai-intro">选择首页卡片，并调整它们的显示顺序。</p><div v-for="group in widgetGroups" :key="group.key" class="widget-settings"><div class="widget-settings-title">{{group.label}}</div><div v-for="(widget,index) in widgetConfig[group.key]" :key="widget.id" class="widget-setting-row"><LedgerActionIcon :action="widget.visible!==false?'hide':'show'" :label="widget.visible!==false?'移除卡片':'新增卡片'" @click="widget.visible=widget.visible===false"/><span>{{widget.label}}</span><LedgerActionIcon action="previous" label="上移卡片" :disabled="index===0" @click="moveWidget(group.key,index,-1)"/><LedgerActionIcon action="next" label="下移卡片" :disabled="index===widgetConfig[group.key].length-1" @click="moveWidget(group.key,index,1)"/></div></div><div class="form-footer"><LedgerActionIcon action="restore" label="恢复默认" @click="resetWidgets"/><LedgerActionIcon action="close" label="完成" @click="layoutOpen=false"/></div></Sheet>
    <Dialog :open="Boolean(deleteTarget)" title="删除流水" @update:open="value=>{if(!value)deleteTarget=null}"><p class="transaction-delete-copy">删除后这笔流水将不再计入账本统计，确定继续吗？</p><template #footer><LedgerActionIcon action="close" label="取消删除" :disabled="deleting" @click="deleteTarget=null"/><LedgerActionIcon action="delete" :label="deleting?'删除中':'确认删除流水'" :disabled="deleting" @click="removeTransaction"/></template></Dialog>
  </section>
</template>

<script setup>
import { computed, h, nextTick, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { BarChart, LineChart, PieChart } from 'echarts/charts'
import { GridComponent, LegendComponent, TooltipComponent } from 'echarts/components'
import * as echarts from 'echarts/core'
import { CanvasRenderer } from 'echarts/renderers'
import { ElMessage } from 'element-plus'
import { Upload } from '@element-plus/icons-vue'
import { useRouter } from 'vue-router'
import Card from '../components/ui/Card.vue'; import Table from '../components/ui/Table.vue'; import Sheet from '../components/ui/Sheet.vue'; import Drawer from '../components/ui/Drawer.vue'; import Dialog from '../components/ui/Dialog.vue'
import LedgerTransactionEditor from '../components/ledger/LedgerTransactionEditor.vue'
import LedgerResourceIcon from '../components/ledger/LedgerResourceIcon.vue'
import LedgerActionIcon from '../components/ledger/LedgerActionIcon.vue'
import LoadingOverlay from '../components/ledger/LoadingOverlay.vue'
import { categoryColor } from '../components/ledger/chartPalette'
import { totalLedgerAssets } from '../components/ledger/ledgerAccounting'
import { currentMemberName, ledgerTransactionDraft, rankLedgerOptions, recentLedgerTransactions } from '../components/ledger/ledgerPreferences'
import { useAppStore } from '../stores/app'
import { useLedgerStore } from '../stores/ledger'
import { apiCreateLedgerAccount, apiCreateLedgerBudget, apiCreateLedgerCategory, apiCreateLedgerTransaction, apiDeleteLedgerAccount, apiDeleteLedgerBudget, apiDeleteLedgerCategory, apiDeleteLedgerTransaction, apiImportLedgerCsv, apiImportLedgerExcel, apiLedgerAiConfirm, apiLedgerAiPreview, apiListLedgerBudgets, apiUpdateLedgerTransaction } from '../api'

echarts.use([
  BarChart,
  LineChart,
  PieChart,
  GridComponent,
  LegendComponent,
  TooltipComponent,
  CanvasRenderer
])

const Head={props:['kicker','title','right'],setup(props,{slots}){return()=>h('div',{class:'head'},[h('div',[h('div',{class:'kicker'},props.kicker),h('h2',props.title)]),h('div',{class:'head-right'},[slots.default?.(),props.right?h('span',props.right):null])])}}
const router=useRouter()
const kindOptions=[{value:'EXPENSE',label:'支出',tone:'out'},{value:'INCOME',label:'收入',tone:'in'},{value:'TRANSFER',label:'转账',tone:'transfer'},{value:'BORROW_IN',label:'借入',tone:'in'},{value:'LEND_OUT',label:'借出',tone:'out'},{value:'COLLECT_DEBT',label:'收债',tone:'in'},{value:'REPAY_DEBT',label:'还债',tone:'out'}]
const incomeKinds=['INCOME','BORROW_IN','COLLECT_DEBT']; const expenseKinds=['EXPENSE','LEND_OUT','REPAY_DEBT']
const dateNow=()=>{const d=new Date();return new Date(d.getTime()-d.getTimezoneOffset()*60000).toISOString().slice(0,10)}; const today=dateNow(); const selectedMonth=ref(today.slice(0,7)),rangeMode=ref('month'),customFrom=ref(`${today.slice(0,7)}-01`),customTo=ref(today)
const accounts=ref([]),categories=ref([]),transactions=ref([]),optionTransactions=ref([]),budgets=ref([]),online=ref(navigator.onLine),syncing=ref(false),filter=ref('all'),categoryScope=ref('month'),categoryLevel=ref('primary'),pageLoading=ref(true)
const editorOpen=ref(false),editing=ref(null),managerOpen=ref(false),managerTab=ref('accounts'),budgetOpen=ref(false),aiOpen=ref(false),aiText=ref(''),aiPreview=ref(null),aiLoading=ref(false),layoutOpen=ref(false),deleteTarget=ref(null),deleting=ref(false)
const importState=reactive({active:false,progress:0,phase:'',detail:'',filename:'',processing:false})
const budgetCategoryQuery=ref('')
const filteredBudgetCategories=computed(()=>{const q=budgetCategoryQuery.value.trim().toLocaleLowerCase();return expenseCategories.value.filter(c=>!q||categoryPath(c).toLocaleLowerCase().includes(q))})
const form=reactive({kind:'EXPENSE',amount:'',occurredOn:today,accountId:'',targetAccountId:'',categoryId:'',payee:'',member:'',project:'',note:''}); const accountForm=reactive({name:'',accountType:'cash',openingBalance:''}); const categoryForm=reactive({name:'',kind:'EXPENSE',parentId:''}); const budgetForm=reactive({monthKey:selectedMonth.value,categoryId:'',amount:''})
const widgetDefaults={summary:[{id:'income',label:'本月收入',visible:true},{id:'expense',label:'本月支出',visible:true},{id:'net',label:'本月结余',visible:true},{id:'asset',label:'总资产',visible:true}],content:[{id:'snapshot',label:'时间摘要',visible:true},{id:'daily',label:'本月每日支出',visible:true},{id:'budget',label:'本月支出与预算',visible:true},{id:'category',label:'支出分类',visible:true},{id:'budget-detail',label:'预算执行',visible:true},{id:'annual-bar',label:'年度收支柱状图',visible:true},{id:'annual-line',label:'年度收支走势',visible:true},{id:'calendar',label:'月度记账日历',visible:true},{id:'transactions',label:'最近交易',visible:true},{id:'accounts',label:'账户',visible:true}]}
const widgetConfig=ref(loadWidgetConfig()); const widgetGroups=[{key:'summary',label:'汇总卡片'},{key:'content',label:'内容卡片'}]
const dailyRef=ref(null),categoryRef=ref(null),barRef=ref(null),lineRef=ref(null),budgetRef=ref(null),chartMap=new Map(); let themeObserver; const weekdays=['日','一','二','三','四','五','六']; const selectedYear=computed(()=>selectedMonth.value.slice(0,4)); const monthLabel=computed(()=>`${selectedYear.value} 年 ${Number(selectedMonth.value.slice(5))} 月`); const monthStart=computed(()=>`${selectedMonth.value}-01`); const monthEnd=computed(()=>endOfMonth(selectedMonth.value)); const yearStart=computed(()=>`${selectedYear.value}-01-01`); const yearEnd=computed(()=>`${selectedYear.value}-12-31`); const validCustomRange=computed(()=>/^\d{4}-\d{2}-\d{2}$/.test(customFrom.value)&&/^\d{4}-\d{2}-\d{2}$/.test(customTo.value)&&customFrom.value<=customTo.value); const overviewStart=computed(()=>rangeMode.value==='custom'&&validCustomRange.value?customFrom.value:monthStart.value); const overviewEnd=computed(()=>rangeMode.value==='custom'&&validCustomRange.value?customTo.value:monthEnd.value); const overviewRangeLabel=computed(()=>rangeMode.value==='custom'&&validCustomRange.value?`${customFrom.value.replaceAll('-','/')} — ${customTo.value.replaceAll('-','/')}`:monthLabel.value); const summaryPeriodLabel=computed(()=>rangeMode.value==='custom'?'所选范围':'本月'); const homeTrendGranularity=computed(()=>rangeMode.value==='custom'&&daysBetween(overviewStart.value,overviewEnd.value)>93?'month':'day')
const range=(d,a,b)=>String(d||'')>=a&&String(d||'')<=b; const sum=rows=>{const incomeRows=rows.filter(x=>incomeKinds.includes(x.kind)),expenseRows=rows.filter(x=>expenseKinds.includes(x.kind)),income=incomeRows.reduce((n,x)=>n+Number(x.amount||0),0),expense=expenseRows.reduce((n,x)=>n+Number(x.amount||0),0);return{income,expense,net:income-expense,incomeCount:incomeRows.length,expenseCount:expenseRows.length}}; const monthItems=computed(()=>transactions.value.filter(x=>range(x.occurredOn,monthStart.value,monthEnd.value))); const overviewItems=computed(()=>transactions.value.filter(x=>range(x.occurredOn,overviewStart.value,overviewEnd.value))); const summary=computed(()=>sum(overviewItems.value)); const totalBalance=computed(()=>accounts.value.reduce((n,x)=>n+Number(x.balance||0),0)); const expenseCategories=computed(()=>categories.value.filter(x=>x.kind==='EXPENSE')); const expenses=computed(()=>expenseCategories.value.filter(x=>x.parentId)); const parents=computed(()=>categories.value.filter(x=>x.kind===categoryForm.kind&&!x.parentId)); const filteredTransactions=computed(()=>transactions.value.filter(x=>filter.value==='all'||(filter.value==='INCOME'?incomeKinds:expenseKinds).includes(x.kind)).sort((a,b)=>String(b.occurredOn||'').localeCompare(String(a.occurredOn||''))||String(b.createdAt||'').localeCompare(String(a.createdAt||''))||String(b.id||'').localeCompare(String(a.id||''))).slice(0,12));
const ledgerStore=useLedgerStore(); const appStore=useAppStore(); const uniqueOptions=field=>[...new Set(optionTransactions.value.map(item=>String(item[field]||'').trim()).filter(Boolean))].sort((a,b)=>a.localeCompare(b,'zh-CN')); const merchantOptions=computed(()=>ledgerStore.visibleMerchants.map(item=>item.name)); const memberOptions=computed(()=>ledgerStore.activeMembers.map(item=>item.displayName||item.username)); const projectOptions=computed(()=>ledgerStore.visibleProjects.map(item=>item.name)); const amountClass=kind=>incomeKinds.includes(kind)?'income':expenseKinds.includes(kind)?'expense':' '; const amountSign=kind=>incomeKinds.includes(kind)?'+':expenseKinds.includes(kind)?'−':''
const aiDrafts=computed(()=>aiPreview.value?._allDrafts||[aiPreview.value])
const requiresAiCategory=kind=>kind==='INCOME'||kind==='EXPENSE'
const aiCategoryParents=kind=>categories.value.filter(item=>item.kind===kind&&!item.parentId&&!item.deleted&&!item.hidden)
const parentCategoryId=draft=>{const child=categories.value.find(item=>String(item.id)===String(draft?.categoryId||''));return child?.parentId||''}
const aiCategoryChildren=draft=>{const kind=draft?.kind==='INCOME'?'INCOME':'EXPENSE';const parentId=draft?.parentCategoryId||parentCategoryId(draft);return categories.value.filter(item=>item.kind===kind&&item.parentId&&(!parentId||String(item.parentId)===String(parentId))&&!item.deleted&&!item.hidden)}
const aiCanConfirm=computed(()=>aiDrafts.value.length>0&&aiDrafts.value.every(draft=>Number(draft.amount)>0&&Boolean(draft.occurredOn)&&Boolean(draft.accountId)&&(!requiresAiCategory(draft.kind)||Boolean(draft.categoryId))&&(draft.kind!=='TRANSFER'||Boolean(draft.targetAccountId))&&!draft.warnings?.length))
const aiKindLabel=kind=>kindOptions.find(item=>item.value===kind)?.label||'流水'
const aiAmountClass=kind=>incomeKinds.includes(kind)?'income':expenseKinds.includes(kind)?'expense':'transfer'
const aiAmountSign=kind=>incomeKinds.includes(kind)?'+':expenseKinds.includes(kind)?'−':''
const effectiveTotalAssets=computed(()=>totalLedgerAssets(accounts.value,optionTransactions.value))
const dateKeys=(from,to)=>{const keys=[];for(let d=new Date(`${from}T00:00:00`),end=new Date(`${to}T00:00:00`);d<=end;d.setDate(d.getDate()+1)){const local=new Date(d.getTime()-d.getTimezoneOffset()*60000).toISOString().slice(0,10);keys.push(local)}return keys}; const monthKeys=(from,to)=>{const keys=[];let [year,month]=from.slice(0,7).split('-').map(Number);const end=to.slice(0,7);while(`${year}-${String(month).padStart(2,'0')}`<=end){keys.push(`${year}-${String(month).padStart(2,'0')}`);month++;if(month>12){year++;month=1}}return keys}; const dailyRows=computed(()=>{const granularity=homeTrendGranularity.value;const keys=granularity==='month'?monthKeys(overviewStart.value,overviewEnd.value):dateKeys(overviewStart.value,overviewEnd.value);return keys.map(key=>{const rows=overviewItems.value.filter(x=>granularity==='month'?String(x.occurredOn||'').startsWith(key):x.occurredOn===key);return{date:key,label:granularity==='month'?key.replace('-','/'):key.slice(5).replace('-','/'),...sum(rows)}})}); const monthCalendarRows=computed(()=>Array.from({length:Number(monthEnd.value.slice(-2))},(_,i)=>{const date=`${selectedMonth.value}-${String(i+1).padStart(2,'0')}`;return{date,...sum(monthItems.value.filter(x=>x.occurredOn===date))}})); const budgetSummary=computed(()=>({budget:budgets.value.reduce((n,x)=>n+Number(x.budget||x.amount||0),0),spent:budgets.value.reduce((n,x)=>n+Number(x.spent||0),0)})); const budgetLeft=computed(()=>Number(budgetSummary.value.budget||0)-Number(budgetSummary.value.spent||0)); const rank=computed(()=>{const map=new Map,source=(categoryScope.value==='range'?overviewItems.value:categoryScope.value==='month'?monthItems.value:transactions.value.filter(x=>range(x.occurredOn,yearStart.value,yearEnd.value))).filter(x=>expenseKinds.includes(x.kind));if(source){for(const x of source){const current=categories.value.find(c=>String(c.id)===String(x.categoryId)),parent=categories.value.find(c=>String(c.id)===String(current?.parentId));const category=categoryLevel.value==='primary'?(parent||current):current;const key=category?.name||x.categoryName||'未分类';const row=map.get(key)||{name:key,amount:0,color:category?.color,parentId:category?.parentId,id:category?.id,parentColor:parent?.color,paletteIndex:category&&!category.parentId?categoryPaletteIndex(category):undefined,parentPaletteIndex:parent?categoryPaletteIndex(parent):undefined};row.amount+=Number(x.amount||0);map.set(key,row)}}return[...map.values()].sort((a,b)=>b.amount-a.amount).slice(0,8).map((item,index)=>({...item,color:categoryColor(item,index)}))}); const months=computed(()=>Array.from({length:12},(_,i)=>{const key=`${selectedYear.value}-${String(i+1).padStart(2,'0')}`,monthSummary=sum(transactions.value.filter(x=>String(x.occurredOn||'').startsWith(key)));return{label:`${i+1}月`,income:monthSummary.income,expense:monthSummary.expense,net:monthSummary.net}})); const weekStart=()=>{const d=new Date(`${today}T00:00:00`);d.setDate(d.getDate()-(d.getDay()||7)+1);return d.toISOString().slice(0,10)}; const snapshots=computed(()=>[{label:'今天',range:today.replaceAll('-','.'),...sum(transactions.value.filter(x=>x.occurredOn===today))},{label:'本周',range:`${weekStart().slice(5).replace('-','.')} - ${today.slice(5).replace('-','.')}`,...sum(transactions.value.filter(x=>range(x.occurredOn,weekStart(),today)))},{label:'本年',range:`${selectedYear.value}.01.01 - ${selectedYear.value}.12.31`,...sum(transactions.value.filter(x=>range(x.occurredOn,yearStart.value,yearEnd.value)))}]); const calendarCells=computed(()=>{const blank=new Date(`${selectedMonth.value}-01T00:00:00`).getDay(),days=monthCalendarRows.value.map(x=>({key:x.date,day:+x.date.slice(-2),income:x.income,expense:x.expense,today:x.date===today}));return[...Array.from({length:blank},(_,i)=>({key:`a${i}`})),...days,...Array.from({length:(7-(blank+days.length)%7)%7},(_,i)=>({key:`b${i}`}))]})
const money=x=>Number(x||0).toLocaleString('zh-CN',{minimumFractionDigits:2,maximumFractionDigits:2}); const compact=x=>Number(x)>=1000?`${(Number(x)/1000).toFixed(1)}k`:Number(x).toFixed(0); const formatDate=x=>String(x||'').replaceAll('-','/'); const endOfMonth=m=>{const [y,n]=m.split('-').map(Number);return`${m}-${String(new Date(y,n,0).getDate()).padStart(2,'0')}`}; const daysBetween=(from,to)=>Math.max(0,Math.round((new Date(`${to}T00:00:00`)-new Date(`${from}T00:00:00`))/86400000)); const accountIcon=x=>({cash:'¥',bank:'▣',card:'▤',wallet:'◒'})[x]||'·'; const accountType=x=>({cash:'现金',bank:'银行卡',card:'信用卡',wallet:'电子钱包'})[x]||'其他账户'; const categoryPath=x=>{const p=categories.value.find(y=>String(y.id)===String(x.parentId));return p?`${p.name} / ${x.name}`:x.name}; const categoryPaletteIndex=x=>{const index=categories.value.filter(item=>!item.parentId).findIndex(item=>String(item.id)===String(x?.id));return index>=0?index:undefined}; const shiftMonth=n=>{const [y,m]=selectedMonth.value.split('-').map(Number);selectedMonth.value=new Date(y,m-1+n,1).toISOString().slice(0,7)}
const budgetSummaryFixed=computed(()=>{const total=budgets.value.find(x=>x.scope==='TOTAL'||!x.categoryId);const rows=total?[total]:budgets.value;return{budget:rows.reduce((n,x)=>n+Number(x.budget||x.amount||0),0),spent:total?Number(total.spent||0):rows.reduce((n,x)=>n+Number(x.spent||0),0)}})
const budgetLeftFixed=computed(()=>Number(budgetSummaryFixed.value.budget||0)-Number(budgetSummaryFixed.value.spent||0))
function loadWidgetConfig(){try{const raw=JSON.parse(localStorage.getItem('ledger-home-widgets-v1')||'null');if(raw?.summary&&raw?.content){const merge=group=>[...raw[group].filter(x=>widgetDefaults[group].some(d=>d.id===x.id)),...widgetDefaults[group].filter(d=>!raw[group].some(x=>x.id===d.id))];return{summary:merge('summary'),content:merge('content')}}}catch{}return structuredClone(widgetDefaults)}
const widgetVisible=(group,id)=>widgetConfig.value[group].find(x=>x.id===id)?.visible!==false; const widgetOrder=(group,id)=>widgetConfig.value[group].findIndex(x=>x.id===id)
function moveWidget(group,index,direction){const list=widgetConfig.value[group];const target=index+direction;if(target<0||target>=list.length)return;[list[index],list[target]]=[list[target],list[index]]}
function resetWidgets(){widgetConfig.value=structuredClone(widgetDefaults)}
function setRangeMode(mode){
  if(rangeMode.value===mode)return
  rangeMode.value=mode
  if(mode==='custom')categoryScope.value='range'
}
function applyHomeCustomRange(changed){
  if(!customFrom.value||!customTo.value)return
  if(customFrom.value>customTo.value){
    if(changed==='from')customTo.value=customFrom.value
    else customFrom.value=customTo.value
  }
  categoryScope.value='range'
}
let homeLoadingSafetyTimer
let budgetRequestId=0
let drawFrame

function finishHomeLoading(){
  window.clearTimeout(homeLoadingSafetyTimer)
  pageLoading.value=false
}
function startHomeLoading(){
  window.clearTimeout(homeLoadingSafetyTimer)
  pageLoading.value=true
  homeLoadingSafetyTimer=window.setTimeout(()=>{pageLoading.value=false},5000)
}
function applyLedgerProjection(){
  accounts.value=ledgerStore.accounts.filter(item=>!item.deleted&&!item.hidden)
  categories.value=ledgerStore.categories.filter(item=>!item.deleted&&!item.hidden)
  transactions.value=ledgerStore.transactions.filter(item=>!item.deleted)
  optionTransactions.value=transactions.value
  online.value=ledgerStore.online
}
function applyStoreBudgets(){
  const rows=ledgerStore.budgets.filter(item=>String(item.monthKey||item.month||'')===selectedMonth.value)
  if(rows.length||!budgets.value.length)budgets.value=rows
}
async function loadBudgets(){
  const requestId=++budgetRequestId
  const bookId=ledgerStore.currentBookId
  const month=selectedMonth.value
  budgetForm.monthKey=month
  applyStoreBudgets()
  if(!ledgerStore.online||!bookId)return
  try{
    const rows=await apiListLedgerBudgets(bookId,month)
    if(requestId===budgetRequestId&&month===selectedMonth.value&&String(bookId)===String(ledgerStore.currentBookId))budgets.value=rows||[]
  }catch{
    if(requestId===budgetRequestId)applyStoreBudgets()
  }
}
function scheduleDraw(){
  window.cancelAnimationFrame(drawFrame)
  drawFrame=window.requestAnimationFrame(()=>{void draw()})
}
async function loadInitialData(){
  startHomeLoading()
  try{
    await ledgerStore.init({waitForRemote:false})
    applyLedgerProjection()
    applyStoreBudgets()
    await draw()
  }finally{
    finishHomeLoading()
  }
  void loadBudgets()
}
async function loadData(){
  if(syncing.value)return
  syncing.value=true
  startHomeLoading()
  try{
    if(!ledgerStore.ready)await ledgerStore.init({waitForRemote:false})
    if(ledgerStore.online&&ledgerStore.currentBookId){
      await Promise.all([
        ledgerStore.refreshBooks(),
        ledgerStore.refreshResources(selectedMonth.value)
      ])
      void ledgerStore.syncNow()
    }
    applyLedgerProjection()
    applyStoreBudgets()
    await loadBudgets()
    await draw()
  }catch(error){
    online.value=false
    ElMessage.error(error?.response?.data?.detail||error?.message||'账本刷新失败')
  }finally{
    syncing.value=false
    finishHomeLoading()
  }
}
function init(key,el){
  if(!el||!el.clientWidth||!el.clientHeight)return
  let chart=chartMap.get(key)
  if(chart?.getDom()!==el){
    chart?.dispose()
    chart=echarts.init(el)
    chartMap.set(key,chart)
  }
  return chart
}
async function draw(){
  await nextTick()
  const s=getComputedStyle(document.documentElement),text=s.getPropertyValue('--ink2').trim()||'#49505c',line=s.getPropertyValue('--line').trim()||'#e2ded4',up=s.getPropertyValue('--up').trim()||'#067647',down=s.getPropertyValue('--down').trim()||'#b42318',tip={trigger:'axis',valueFormatter:v=>`¥${money(v)}`}
  const axis={axisLine:{lineStyle:{color:line}},axisLabel:{color:text,fontSize:10}}
  init('daily',dailyRef.value)?.setOption({animationDuration:480,grid:{left:12,right:16,top:16,bottom:24,containLabel:true},tooltip:tip,xAxis:{type:'category',data:dailyRows.value.map(x=>x.label),...axis},yAxis:{type:'value',axisLabel:{color:text,fontSize:10},splitLine:{lineStyle:{color:line,type:'dashed'}}},series:[{name:'支出',type:'line',smooth:true,symbol:'circle',symbolSize:5,data:dailyRows.value.map(x=>x.expense),lineStyle:{color:up,width:2},areaStyle:{color:`${up}1f`}}]},true)
  const r=[...rank.value].reverse()
  init('category',categoryRef.value)?.setOption({animationDuration:480,grid:{left:12,right:28,top:14,bottom:12,containLabel:true},tooltip:tip,xAxis:{type:'value',axisLabel:{color:text,fontSize:10},splitLine:{lineStyle:{color:line,type:'dashed'}}},yAxis:{type:'category',data:r.map(x=>x.name),axisTick:{show:false},axisLine:{show:false},axisLabel:{color:text,fontSize:11,width:96,overflow:'truncate'}},series:[{type:'bar',data:r.map(x=>({value:x.amount,itemStyle:{color:x.color,borderRadius:[0,5,5,0]}})),barMaxWidth:14}]},true)
  const opts={animationDuration:480,grid:{left:12,right:12,top:30,bottom:24,containLabel:true},tooltip:tip,legend:{data:['收入','支出','结余'],textStyle:{color:text,fontSize:10},right:0,top:0},xAxis:{type:'category',data:months.value.map(x=>x.label),...axis},yAxis:{type:'value',axisLabel:{color:text,fontSize:10},splitLine:{lineStyle:{color:line,type:'dashed'}}}}
  init('bar',barRef.value)?.setOption({...opts,series:[{name:'收入',type:'bar',data:months.value.map(x=>x.income),barMaxWidth:12,itemStyle:{color:down,borderRadius:[3,3,0,0]}},{name:'支出',type:'bar',data:months.value.map(x=>x.expense),barMaxWidth:12,itemStyle:{color:up,borderRadius:[3,3,0,0]}},{name:'结余',type:'bar',data:months.value.map(x=>x.net),barMaxWidth:12,itemStyle:{color:(p)=>p.value>=0?down:up,borderRadius:[3,3,0,0]}}]},true)
  init('line',lineRef.value)?.setOption({...opts,series:[{name:'收入',type:'line',smooth:true,symbol:'none',data:months.value.map(x=>x.income),lineStyle:{color:down,width:2}},{name:'支出',type:'line',smooth:true,symbol:'none',data:months.value.map(x=>x.expense),lineStyle:{color:up,width:2}},{name:'结余',type:'line',smooth:true,symbol:'none',data:months.value.map(x=>x.net),lineStyle:{color:months.value.every(x=>x.net>=0)?down:up,width:2}}]},true)
  const spent=+budgetSummaryFixed.value.spent||0,budget=+budgetSummaryFixed.value.budget||0
  init('budget',budgetRef.value)?.setOption({animationDuration:480,series:[{type:'pie',radius:['70%','91%'],padAngle:2,label:{show:false},itemStyle:{borderColor:s.getPropertyValue('--card').trim()||'#fff',borderWidth:2,borderRadius:3},data:budget?[{name:'已支出',value:Math.min(spent,budget),itemStyle:{color:spent>budget?down:up}},{name:'剩余预算',value:Math.max(budget-spent,0),itemStyle:{color:line}}]:[{name:'未设置预算',value:1,itemStyle:{color:line}}]}]},true)
}
function openAccountTransactions(account){
  if(!account?.id)return
  router.push({path:'/ledger/transactions',query:{accountId:String(account.id),range:'all'}})
}
function openDayTransactions(date){
  router.push({path:'/ledger/transactions',query:{from:date,to:date}})
}
function openCreatePreferred(){
  editing.value=null
  const preferred=rankLedgerOptions(accounts.value,recentLedgerTransactions(optionTransactions.value),
    item=>[item.accountId,item.targetAccountId])[0]
  Object.assign(form,{kind:'EXPENSE',amount:'',occurredOn:dateNow(),accountId:preferred?String(preferred.id):'',
    targetAccountId:'',categoryId:'',payee:'',member:currentMemberName(ledgerStore.activeMembers,appStore.authUser),
    project:'',note:''})
  editorOpen.value=true
}
function openCreate(){editing.value=null;Object.assign(form,{kind:'EXPENSE',amount:'',occurredOn:today,accountId:accounts.value[0]?String(accounts.value[0].id):'',targetAccountId:'',categoryId:'',payee:'',member:'',project:'',note:''});editorOpen.value=true} function editTransaction(x){editing.value=x;Object.assign(form,{kind:x.kind,amount:x.amount,occurredOn:x.occurredOn,accountId:String(x.accountId),targetAccountId:x.targetAccountId?String(x.targetAccountId):'',categoryId:x.categoryId?String(x.categoryId):'',payee:x.payee||'',member:x.member||'',project:x.project||'',note:x.note||''});editorOpen.value=true} async function saveTransaction(){if(!form.categoryId){ElMessage.error('请选择二级分类');return}const p={...form,amount:+form.amount,accountId:+form.accountId,targetAccountId:form.kind==='TRANSFER'?+form.targetAccountId:null,categoryId:Number(form.categoryId),clientOpId:`web-${Date.now()}`};try{if(editing.value)await apiUpdateLedgerTransaction(editing.value.id,p,editing.value.revision);else await apiCreateLedgerTransaction(p,p.clientOpId);editorOpen.value=false;ElMessage.success('交易已保存');await loadData()}catch(e){ElMessage.error(e.response?.data?.detail||'保存失败')}} async function removeTransaction(){if(!deleteTarget.value)return;deleting.value=true;try{await apiDeleteLedgerTransaction(deleteTarget.value.id,deleteTarget.value.revision);deleteTarget.value=null;ElMessage.success('流水已删除');await loadData()}catch(e){ElMessage.error(e.response?.data?.detail||'删除失败')}finally{deleting.value=false}} function openManager(){managerOpen.value=true} async function saveAccount(){try{await apiCreateLedgerAccount({...accountForm,openingBalance:+(accountForm.openingBalance||0)});Object.assign(accountForm,{name:'',accountType:'cash',openingBalance:''});await loadData()}catch(e){ElMessage.error('账户保存失败')}} async function removeAccount(x){try{await apiDeleteLedgerAccount(x.id,x.revision);await loadData()}catch(e){ElMessage.error('账户删除失败')}} async function saveCategory(){try{await apiCreateLedgerCategory({...categoryForm,parentId:categoryForm.parentId?+categoryForm.parentId:null});Object.assign(categoryForm,{name:'',kind:'EXPENSE',parentId:''});await loadData()}catch(e){ElMessage.error('分类保存失败')}} async function removeCategory(x){try{await apiDeleteLedgerCategory(x.id,x.revision);await loadData()}catch(e){ElMessage.error('分类删除失败')}} function openBudget(){Object.assign(budgetForm,{monthKey:selectedMonth.value,categoryId:'',amount:''});budgetCategoryQuery.value='';budgetOpen.value=true} async function saveBudget(){try{await apiCreateLedgerBudget({monthKey:budgetForm.monthKey,categoryId:+budgetForm.categoryId,amount:+budgetForm.amount});await loadData();ElMessage.success('预算已保存')}catch(e){ElMessage.error('预算保存失败')}} async function deleteBudget(x){try{await apiDeleteLedgerBudget(x.id);await loadData()}catch(e){ElMessage.error('预算删除失败')}} async function importFile(e){const f=e.target.files?.[0];if(!f)return;try{const result=/\.xlsx?$/i.test(f.name)?await apiImportLedgerExcel(f):await apiImportLedgerCsv(f);const rows=Array.isArray(result)?result:result.transactions||[];const skippedTransfers=Array.isArray(result)?0:Number(result.skippedTransfers||0);ElMessage.success(`已导入 ${rows.length} 笔交易${skippedTransfers?`，已跳过 ${skippedTransfers} 笔转账`:''}`);await loadData()}catch(x){ElMessage.error(x.response?.data?.detail||'导入失败')}finally{e.target.value=''}} function exportCsv(){const header='交易类型,日期,一级分类,二级分类,收入账户,金额,成员,商家,项目,备注';const rows=transactions.value.map(x=>{const c=categories.value.find(y=>String(y.id)===String(x.categoryId)),p=c&&categories.value.find(y=>String(y.id)===String(c.parentId));return[x.kind==='INCOME'?'收入':'支出',x.occurredOn,p?.name||x.categoryName||'',p?x.categoryName||'':'',x.accountName||'',x.amount,'',x.payee||'','',x.note||''].map(v=>`"${String(v).replaceAll('"','""')}"`).join(',')});const u=URL.createObjectURL(new Blob([`\ufeff${[header,...rows].join('\n')}`],{type:'text/csv;charset=utf-8'}));const a=document.createElement('a');a.href=u;a.download=`ledger-${selectedMonth.value}.csv`;a.click();URL.revokeObjectURL(u)}
async function aiPreviewRequest(){if(!aiText.value.trim())return;aiLoading.value=true;try{aiPreview.value=await apiLedgerAiPreview(aiText.value)}catch(e){ElMessage.error('暂时无法识别')}finally{aiLoading.value=false}} async function confirmAi(){try{await apiLedgerAiConfirm(aiPreview.value);aiOpen.value=false;await loadData();ElMessage.success('AI 记账完成')}catch(e){ElMessage.error('记账失败')}}
 async function saveTransactionFixed(){
   if(form.kind==='TRANSFER' && !form.targetAccountId){ElMessage.error('请选择转入账户');return}
   try{
     const p={...ledgerTransactionDraft(form,{categories:categories.value,merchants:ledgerStore.visibleMerchants,
       members:ledgerStore.activeMembers,projects:ledgerStore.visibleProjects}),clientOpId:`web-${Date.now()}`}
     pageLoading.value=true
     if(editing.value)await apiUpdateLedgerTransaction(ledgerStore.currentBookId,editing.value.id,p,editing.value.revision,p.clientOpId)
     else await apiCreateLedgerTransaction(ledgerStore.currentBookId,p,p.clientOpId)
     await ledgerStore.refreshAccounts()
     editorOpen.value=false
     ElMessage.success('交易已保存')
     await loadData()
   }catch(e){ElMessage.error(e.response?.data?.detail||e.message||'保存失败')}
   finally{pageLoading.value=false}
 }
 async function removeTransactionFixed(){if(!deleteTarget.value)return;deleting.value=true;try{await apiDeleteLedgerTransaction(ledgerStore.currentBookId,deleteTarget.value.id,deleteTarget.value.revision,`web-delete-${Date.now()}`);await ledgerStore.refreshAccounts();deleteTarget.value=null;ElMessage.success('流水已删除');await loadData()}catch(e){ElMessage.error(e.response?.data?.detail||'删除失败')}finally{deleting.value=false}}
 async function saveCategoryFixed(){pageLoading.value=true;try{await apiCreateLedgerCategory({...categoryForm,parentId:categoryForm.parentId?String(categoryForm.parentId):null});Object.assign(categoryForm,{name:'',kind:'EXPENSE',parentId:''});await loadData()}catch(e){ElMessage.error(e.response?.data?.detail||'分类保存失败')}finally{pageLoading.value=false}}
 async function saveBudgetFixed(){pageLoading.value=true;try{const label=budgetCategoryQuery.value.trim();const matched=label&&label!=='月度总预算'?expenseCategories.value.find(c=>categoryPath(c)===label||c.name===label):null;await apiCreateLedgerBudget({monthKey:budgetForm.monthKey,categoryId:matched?String(matched.id):'',amount:+budgetForm.amount});await loadData();ElMessage.success('预算已保存')}catch(e){ElMessage.error(e.response?.data?.detail||'预算保存失败')}finally{pageLoading.value=false}}
 async function aiPreviewRequestFixed(){if(!aiText.value.trim())return;aiLoading.value=true;try{const result=await apiLedgerAiPreview(aiText.value);const drafts=(result.drafts||[]).map(draft=>{const child=categories.value.find(item=>String(item.id)===String(draft.categoryId||''));return {...draft,parentCategoryId:draft.parentCategoryId||child?.parentId||''}});const draft=drafts[0];if(draft)aiPreview.value={...draft,_draftId:result.draftId,_allDrafts:drafts,localFallback:Boolean(result.localFallback)}}catch(e){ElMessage.error(e.response?.data?.detail||'DeepSeek 暂时不可用，请稍后重试')}finally{aiLoading.value=false}}
 function updateAiDraft(index,patch){
   const drafts=aiDrafts.value.map(item=>({...item}))
   const draft=drafts[index]
   if(!draft)return
   Object.assign(draft,patch)
   const warnings=draft.warnings||[]
   if(Object.prototype.hasOwnProperty.call(patch,'accountId') && patch.accountId){
     draft.warnings=warnings.filter(warning=>warning!=='请选择账户'&&!warning.startsWith('账户“'))
   }
   if(Object.prototype.hasOwnProperty.call(patch,'targetAccountId') && patch.targetAccountId){
     draft.warnings=(draft.warnings||[]).filter(warning=>warning!=='请选择转入账户'&&!warning.startsWith('转入账户“'))
   }
   if(draft.kind==='TRANSFER' && !draft.targetAccountId && !(draft.warnings||[]).includes('请选择转入账户')){
     draft.warnings=[...(draft.warnings||[]),'请选择转入账户']
   }
   if(draft.accountId && draft.kind==='TRANSFER' && draft.targetAccountId===draft.accountId){
     draft.warnings=[...(draft.warnings||[]).filter(warning=>warning!=='请选择转入账户'),'转入账户不能与转出账户相同']
   }
   aiPreview.value={...draft,_draftId:aiPreview.value._draftId,_allDrafts:drafts,localFallback:aiPreview.value.localFallback}
 }
 function updateAiCategory(index,patch){
   const drafts=aiDrafts.value.map(item=>({...item}))
   const draft=drafts[index]
   if(!draft)return
   Object.assign(draft,patch)
   if(Object.prototype.hasOwnProperty.call(patch,'parentCategoryId')){
     draft.categoryId=''
     draft.categoryName=''
     draft.categoryMatchStatus=patch.parentCategoryId?'primary_only':'missing'
   }
   if(Object.prototype.hasOwnProperty.call(patch,'categoryId')){
     const category=categories.value.find(item=>String(item.id)===String(patch.categoryId||''))
     if(category){draft.categoryName=category.name;draft.parentCategoryId=String(category.parentId);draft.categoryMatchStatus='matched'}
   }
   const categoryWarning=/分类|二级分类/.test(''+(draft.warnings||[]).join(''))
   if(draft.categoryId)draft.warnings=(draft.warnings||[]).filter(warning=>!/(分类|二级分类)/.test(warning))
   else if(!categoryWarning)draft.warnings=[...(draft.warnings||[]),'请选择二级分类']
   aiPreview.value={...draft,_draftId:aiPreview.value._draftId,_allDrafts:drafts,localFallback:aiPreview.value.localFallback}
 }
 async function confirmAiFixed(){pageLoading.value=true;try{if(!aiPreview.value?._draftId)throw new Error('请先识别交易');if(!aiCanConfirm.value)throw new Error('请先补全账户和二级分类，并处理未匹配项');await apiLedgerAiConfirm(null,aiPreview.value._draftId,aiDrafts.value,`ai-web-${Date.now()}`);aiOpen.value=false;aiPreview.value=null;await loadData();ElMessage.success('AI 记账完成')}catch(e){ElMessage.error(e.response?.data?.detail||e.message||'记账失败')}finally{pageLoading.value=false}}
 async function importFileWithProgress(event){
   const file=event.target.files?.[0]
   if(!file||importState.active)return
   Object.assign(importState,{active:true,progress:2,phase:'准备导入',detail:'正在读取文件',filename:file.name,processing:false})
   try{
     const progress={
       onStage:stage=>{
         if(stage==='upload')Object.assign(importState,{phase:'上传文件',detail:'正在上传账本文件',processing:false})
         else Object.assign(importState,{progress:70,phase:'写入流水',detail:'正在创建所需资源并写入有效流水',processing:true})
       },
       onUploadProgress:value=>{importState.progress=Math.max(importState.progress,Math.min(50,Math.round(value*.5)))},
       onPreview:preview=>Object.assign(importState,{progress:65,phase:'解析完成',detail:`有效 ${preview.validCount||0} 笔，重复 ${preview.duplicateCount||0} 笔，错误 ${preview.errorCount||0} 笔`,processing:false})
     }
     const result=/\.xlsx?$/i.test(file.name)?await apiImportLedgerExcel(file,progress):await apiImportLedgerCsv(file,progress)
     const count=Number(result.createdCount??result.created?.length??result.transactions?.length??0)
     Object.assign(importState,{progress:95,phase:'刷新账本',detail:`已写入 ${count} 笔，正在更新本地数据`,processing:true})
     await loadData()
     Object.assign(importState,{progress:100,phase:'导入完成',detail:`成功导入 ${count} 笔流水`,processing:false})
     ElMessage.success(`已导入 ${count} 笔交易`)
   }catch(error){
     Object.assign(importState,{phase:'导入失败',detail:error.response?.data?.detail||'导入失败',processing:false})
     ElMessage.error(importState.detail)
   }finally{
     const completed=importState.progress===100
     setTimeout(()=>{importState.active=false},completed?900:1800)
     event.target.value=''
   }
 }
 aiPreviewRequest = aiPreviewRequestFixed; confirmAi = confirmAiFixed; openCreate = openCreatePreferred; saveTransaction = saveTransactionFixed; removeTransaction = removeTransactionFixed; saveCategory = saveCategoryFixed; saveBudget = saveBudgetFixed; importFile = importFileWithProgress

function handleResize(){chartMap.forEach(chart=>chart.resize())}
function handleBookChanged(){
  budgets.value=[]
  applyLedgerProjection()
  applyStoreBudgets()
  scheduleDraw()
  void loadBudgets()
}

watch(widgetConfig,value=>{
  localStorage.setItem('ledger-home-widgets-v1',JSON.stringify(value))
  scheduleDraw()
},{deep:true})
watch(selectedMonth,()=>{
  budgets.value=[]
  applyStoreBudgets()
  void loadBudgets()
  scheduleDraw()
})
watch([categoryScope,categoryLevel,rangeMode,customFrom,customTo],()=>{
  if(rangeMode.value==='custom')categoryScope.value='range'
  scheduleDraw()
})
watch([
  ()=>ledgerStore.accounts,
  ()=>ledgerStore.categories,
  ()=>ledgerStore.transactions
],()=>{
  applyLedgerProjection()
  scheduleDraw()
})
watch(()=>ledgerStore.budgets,()=>{
  applyStoreBudgets()
  scheduleDraw()
})
watch(()=>ledgerStore.online,value=>{online.value=value})

onMounted(()=>{
  window.addEventListener('resize',handleResize)
  window.addEventListener('ledger-book-changed',handleBookChanged)
  themeObserver=new MutationObserver(scheduleDraw)
  themeObserver.observe(document.documentElement,{attributes:true,attributeFilter:['class']})
  void loadInitialData()
})
onBeforeUnmount(()=>{
  finishHomeLoading()
  budgetRequestId++
  window.cancelAnimationFrame(drawFrame)
  themeObserver?.disconnect()
  window.removeEventListener('resize',handleResize)
  window.removeEventListener('ledger-book-changed',handleBookChanged)
  chartMap.forEach(chart=>chart.dispose())
})
</script>

<style scoped>
.ledger-page,.ledger-summary-grid,.home-widget-grid,.home-widget-grid>*,.ledger-summary-grid>*,.form,.form>*{min-width:0;max-width:100%;box-sizing:border-box}.form input,.form select,.form textarea{min-width:0;max-width:100%;box-sizing:border-box}.ledger-actions{max-width:100%}.ledger-actions .ui-button,.ledger-actions .import-label{min-width:0;max-width:100%}.transaction-description{min-width:0;max-width:260px;overflow:hidden}.transaction-description b,.transaction-description small{width:100%;max-width:100%;overflow:hidden;text-overflow:ellipsis;white-space:nowrap}.transaction-delete-copy{margin:0;color:var(--ink2);line-height:1.7}
.ledger-heading{margin-bottom:18px}.heading-slash{color:var(--muted);font:600 12px ui-sans-serif;letter-spacing:.12em}.ledger-heading .muted{margin:8px 0 0}.ledger-actions{display:flex;gap:8px;flex-wrap:wrap}.import-label{cursor:pointer}.import-label.disabled{cursor:wait;opacity:.64}.ledger-status{display:flex;gap:8px;align-items:center;margin-bottom:14px;color:var(--up);font-size:12px}.ledger-status.offline{color:var(--warn)}.status-dot{width:7px;height:7px;border-radius:50%;background:var(--warn)}.status-dot.online{background:var(--up)}.import-progress{display:flex;flex-direction:column;gap:7px;margin:-2px 0 16px;padding:13px 15px;border:1px solid var(--line);border-radius:6px;background:var(--card)}.import-progress-head{display:flex;align-items:center;justify-content:space-between;gap:14px}.import-progress-head>span{display:flex;min-width:0;align-items:baseline;gap:9px}.import-progress-head b{font-size:12px}.import-progress-head small,.import-progress>small{overflow:hidden;color:var(--muted);font-size:10px;text-overflow:ellipsis;white-space:nowrap}.import-progress-head strong{font:650 13px Georgia,serif;font-variant-numeric:tabular-nums}.import-progress-track{height:6px;overflow:hidden;border-radius:6px;background:var(--line)}.import-progress-track i{display:block;height:100%;border-radius:inherit;background:var(--accent);transition:width .24s ease}.import-progress-track.processing i{background:linear-gradient(90deg,var(--accent),color-mix(in srgb,var(--accent) 42%,white),var(--accent));background-size:200% 100%;animation:import-progress-wave 1.4s linear infinite}@keyframes import-progress-wave{to{background-position:-200% 0}}.overview-toolbar{display:flex;justify-content:space-between;align-items:center;margin-bottom:14px;color:var(--muted);font-size:11px}.overview-period-control{display:flex;align-items:center;gap:8px;min-width:0}.overview-range-mode{flex:0 0 auto}.period-picker{display:flex;border:1px solid var(--line2);border-radius:4px;overflow:hidden}.period-picker button{width:31px;border:0;background:var(--card);color:var(--ink2);font:22px Georgia}.period-picker input{width:112px;border:0;border-left:1px solid var(--line);border-right:1px solid var(--line);padding:0 7px;background:var(--card);color:var(--ink);font-size:12px}.home-custom-range{display:grid;grid-template-columns:minmax(124px,1fr) auto minmax(124px,1fr);align-items:center;gap:7px}.home-custom-range input{min-width:0;height:33px;padding:0 8px;border:1px solid var(--line2);border-radius:4px;background:var(--card);color:var(--ink);font-size:11px}.ledger-summary-grid{display:grid;grid-template-columns:repeat(4,minmax(0,1fr));gap:12px;margin-bottom:12px}.ledger-summary-grid :deep(.card){min-height:126px}.summary-label,.summary-meta{color:var(--muted);font-size:11px}.summary-value{font:700 clamp(25px,3vw,34px)/1 Georgia,serif;margin:5px 0 10px}.income,.asset{color:var(--down)}.expense{color:var(--up)}.budget-over{color:var(--up)}.snapshot-grid{display:grid;grid-template-columns:repeat(3,1fr);border:1px solid var(--line);border-radius:4px;background:var(--card);margin-bottom:20px}.snapshot-item{padding:13px 16px;border-right:1px solid var(--line)}.snapshot-item:last-child{border:0}.snapshot-item>div{display:flex;justify-content:space-between;gap:8px}.snapshot-item small{color:var(--muted);font-size:10px}.snapshot-item>div+div{margin-top:9px;align-items:baseline;flex-wrap:wrap;font-size:11px}.snapshot-item b{margin-left:auto}.home-widget-grid{display:grid;grid-template-columns:minmax(0,1.65fr) minmax(250px,.8fr);gap:20px;margin-bottom:20px}.widget-full{grid-column:1 / -1}.widget-wide{grid-column:span 1}.widget-narrow{grid-column:span 1}.widget-half{grid-column:span 1}.records-accounts{display:flex;flex-direction:column;gap:20px;min-width:0}.chart-card{min-width:0}.head{display:flex;align-items:flex-start;justify-content:space-between;gap:12px}.kicker{color:var(--muted);font-size:10px;letter-spacing:.16em;font-weight:600}.head h2{margin:4px 0 0;font:700 21px/1.2 Georgia,serif}.head-right{display:flex;gap:6px;align-items:center;color:var(--muted);font-size:11px}.chart{width:100%}.tall{height:260px}.medium{height:250px}.seg{display:flex;border:1px solid var(--line2);padding:2px;border-radius:3px}.seg button,.manager-tabs button{border:0;background:transparent;color:var(--muted);padding:5px 8px;font-size:11px}.seg button.active,.manager-tabs button.active{background:var(--accent-soft);color:var(--ink);font-weight:600}.donut-wrap{height:196px;position:relative}.donut{height:100%}.donut-label{position:absolute;inset:0;display:flex;flex-direction:column;align-items:center;justify-content:center;pointer-events:none}.donut-label b{font:700 22px Georgia,serif}.donut-label span{font-size:11px;color:var(--muted);margin-top:5px}.budget-total,.budget-row{display:flex;justify-content:space-between;gap:8px}.budget-total{padding-top:11px;border-top:1px solid var(--line);font-size:11px;color:var(--muted)}.budget-list{display:flex;flex-direction:column;gap:14px}.budget-row{font-size:11px;color:var(--ink2);margin-bottom:6px}.budget-track{height:5px;background:var(--accent-soft);border-radius:4px;overflow:hidden}.budget-track i{display:block;height:100%;background:var(--accent)}.budget-track i.over{background:var(--up)}.weekdays,.calendar{display:grid;grid-template-columns:repeat(7,minmax(0,1fr))}.weekdays{border-bottom:1px solid var(--line)}.weekdays span{text-align:right;padding:7px 8px;color:var(--muted);font-size:10px}.day{min-height:76px;padding:8px;display:flex;flex-direction:column;align-items:flex-end;gap:3px;border-right:1px solid var(--line);border-bottom:1px solid var(--line);font-size:10px}.day:nth-child(7n){border-right:0}.day:nth-last-child(-n+7){border-bottom:0}.day b{color:var(--ink2)}.day.today b{width:20px;height:20px;border-radius:50%;display:grid;place-items:center;background:var(--accent);color:#fff}.transaction-table td b{display:block;color:var(--ink);font-size:13px}.transaction-table td small{display:block;color:var(--muted);font-size:11px;margin-top:2px}.home-resource{display:inline-flex;align-items:center;min-width:0;gap:7px}.home-resource>span{display:flex;min-width:0;flex-direction:column}.home-resource b,.home-resource small{max-width:100%;overflow:hidden;text-overflow:ellipsis;white-space:nowrap}.home-project{display:inline-flex!important;align-items:center;gap:4px}.home-project i{flex:0 0 6px;width:6px;height:6px;border:0;border-radius:50%}.amount{text-align:right!important;white-space:nowrap}.row-action,.text-button{border:0;background:transparent;color:var(--accent);padding:5px;cursor:pointer;font-size:12px}.row-action{color:var(--muted)}.row-action.danger:hover{color:var(--up)}.list-footer{display:flex;justify-content:space-between;margin-top:14px;color:var(--muted);font-size:11px}.ledger-empty{min-height:160px;display:flex;flex-direction:column;align-items:center;justify-content:center;gap:10px;color:var(--muted)}.account{display:flex;align-items:center;gap:10px;width:100%;padding:11px 0;border:0;border-bottom:1px solid var(--line);background:transparent;text-align:left;color:var(--ink)}.account i{width:28px;height:28px;border:1px solid var(--line2);border-radius:50%;display:grid;place-items:center;color:var(--accent);font-style:normal}.account span,.manager-row span{display:flex;flex-direction:column;flex:1}.account small,.manager-row small{color:var(--muted);font-size:10px}.account strong{font-size:12px;color:var(--ink2)}.account-add{width:100%;margin-top:12px}.manager-row{display:flex;justify-content:space-between;align-items:center;gap:10px;padding:10px 0;border-bottom:1px solid var(--line);font-size:12px}.side-empty,.ai-intro{color:var(--muted);font-size:12px}.form{display:flex;flex-direction:column;gap:12px}.form label{display:flex;flex-direction:column;gap:5px;color:var(--ink2);font-size:12px;font-weight:600}.form-kind{display:grid;grid-template-columns:1fr 1fr;padding:3px;border:1px solid var(--line);background:var(--paper)}.form-kind button{border:0;background:transparent;padding:9px;color:var(--muted)}.form-kind button.active{background:var(--card);color:var(--ink);box-shadow:0 1px 3px #0002}.form-footer{display:flex;justify-content:flex-end;gap:8px}.manager-tabs{display:flex;gap:4px;border-bottom:1px solid var(--line);padding-bottom:8px;margin-bottom:14px}.ai-button{width:100%;margin-top:10px}.ai-preview{margin-top:20px;border-top:1px solid var(--line);padding-top:16px}.ai-preview>b{font:700 30px Georgia,serif}.widget-settings{display:flex;flex-direction:column;gap:4px;margin-bottom:18px}.widget-settings-title{font-size:11px;color:var(--muted);letter-spacing:.08em;margin-bottom:4px}.widget-setting-row{display:flex;align-items:center;gap:8px;border-bottom:1px solid var(--line);padding:8px 0;font-size:12px}.widget-setting-row>span{flex:1}.widget-visibility{border:1px solid var(--line2);background:transparent;color:var(--muted);padding:4px 8px;cursor:pointer;font-size:11px}.widget-visibility.on{color:var(--accent);border-color:var(--accent)}
@media(max-width:1023px){.home-widget-grid{grid-template-columns:minmax(0,1fr) minmax(0,1fr)}}@media(max-width:767px){.ledger-actions{width:100%}.ledger-actions .ui-button,.ledger-actions .import-label{flex:1 1 auto}.overview-toolbar{align-items:flex-start;flex-direction:column}.overview-period-control{width:100%;align-items:stretch;flex-direction:column}.home-custom-range{grid-template-columns:minmax(0,1fr);width:100%}.home-custom-range span{display:none}.ledger-summary-grid{grid-template-columns:repeat(2,minmax(0,1fr));gap:8px}.ledger-summary-grid :deep(.card){min-height:112px;padding:15px}.summary-value{font-size:25px}.home-widget-grid{grid-template-columns:minmax(0,1fr)}.widget-full,.widget-wide,.widget-narrow,.widget-half{grid-column:1}.snapshot-grid{grid-template-columns:minmax(0,1fr)}.snapshot-item{border-right:0;border-bottom:1px solid var(--line)}.snapshot-item:last-child{border:0}.home-widget-grid{gap:12px;margin-bottom:12px}.day{min-height:57px;padding:5px 4px}.chart.tall,.chart.medium{height:220px}.head{flex-direction:column}.head-right{align-self:stretch;flex-wrap:wrap}.transaction-table{margin:0}.transactions :deep(.ui-table){width:100%;min-width:0;table-layout:fixed}.transactions :deep(th:first-child),.transactions :deep(td:first-child){width:84px;overflow:hidden;white-space:nowrap}.transactions :deep(th:nth-child(2)),.transactions :deep(td:nth-child(2)){width:auto;min-width:0;overflow:hidden}.transactions :deep(th:nth-child(3)),.transactions :deep(td:nth-child(3)),.transactions :deep(th:nth-child(4)),.transactions :deep(td:nth-child(4)){display:none}.transactions :deep(th:nth-child(5)),.transactions :deep(td:nth-child(5)){width:82px}.transactions :deep(th:last-child),.transactions :deep(td:last-child){width:58px}.transactions :deep(th),.transactions :deep(td){box-sizing:border-box;padding-left:4px;padding-right:4px}.transactions .row-action{padding:3px 1px;font-size:10px}.transaction-description{max-width:none;padding-left:8px!important}}
.home-widget-grid{grid-template-columns:repeat(2,minmax(0,1fr))}
.home-widget-grid>:deep(.card){overflow:hidden}
.head{min-width:0;flex-wrap:wrap}
.head-right{min-width:0;flex-wrap:wrap;justify-content:flex-end}
.head-right .seg{flex:0 0 auto;white-space:nowrap}
.accounts-card{align-self:start}
.account-grid{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:8px}
.account{min-width:0;padding:11px 12px;border:1px solid var(--line);border-radius:6px;background:color-mix(in srgb,var(--paper) 60%,var(--card));transition:border-color .16s,background .16s,transform .16s}
.account>.ledger-resource-icon{flex:0 0 30px}
.account>span:not(.ledger-resource-icon){min-width:0}
.day:not(.blank){width:100%;border-top:0;border-left:0;background:transparent;text-align:right;cursor:pointer}
.day:not(.blank):hover,.day:not(.blank):focus-visible{background:var(--accent-soft);outline:1px solid var(--accent);outline-offset:-1px}
.head-right .seg{gap:3px;padding:3px;border:0;border-radius:5px;background:var(--paper)}
.head-right .seg button{min-width:44px;height:29px;padding:0 9px;border-radius:4px;white-space:nowrap}
.head-right .seg button.active{background:var(--card);color:var(--accent);box-shadow:0 2px 8px color-mix(in srgb,var(--ink) 9%,transparent)}
.category-card .head-right .seg:first-child button{min-width:64px}
.category-card .head-right .seg:last-child button{min-width:48px}
.home-widget-grid .text-button,.home-widget-grid .row-action{
  min-height:29px;padding:4px 9px;border:1px solid var(--line2);border-radius:4px;
  background:var(--paper);color:var(--accent);
}
.home-widget-grid .text-button:hover,.home-widget-grid .row-action:hover{background:var(--accent-soft)}
.account:hover,.account:focus-visible{border-color:color-mix(in srgb,var(--accent) 45%,var(--line));background:var(--accent-soft);outline:none;transform:translateY(-1px)}
.account>span{min-width:0}.account b,.account small{max-width:100%;overflow:hidden;text-overflow:ellipsis;white-space:nowrap}.account strong{flex:0 0 auto;margin-left:auto;font-variant-numeric:tabular-nums;white-space:nowrap}.account-add{border-top:1px solid var(--line)}
.ledger-summary-grid :deep(.card){container-type:inline-size;overflow:hidden}
.ledger-summary-grid .summary-value{
  display:block;max-width:100%;overflow:hidden;text-overflow:ellipsis;white-space:nowrap;
  font-size:clamp(16px,8cqi,34px);line-height:1.2;font-variant-numeric:tabular-nums;
}
.category-card .head{flex-direction:column}
.category-card .head-right{width:100%;max-width:100%;justify-content:flex-start;flex-wrap:nowrap;overflow-x:auto}
.category-card .head-right .seg{flex:0 0 auto}
.category-card .head-right .seg:first-child button{min-width:46px}
.account-grid{grid-template-columns:repeat(2,minmax(0,1fr))}
.accounts-card .account{min-width:0;overflow:hidden}
.accounts-card .account>span:not(.ledger-resource-icon){flex:1 1 auto;min-width:0}
.accounts-card .account strong{min-width:0;max-width:55%;overflow:hidden;text-overflow:ellipsis}
.accounts-card .account-add{display:grid;place-items:center;width:100%;height:36px;margin-top:10px}
.ledger-actions{align-items:center}
.home-row-actions{white-space:nowrap}
.home-row-actions .ledger-action-icon{display:inline-grid;vertical-align:middle;margin-left:3px}
.ledger-import-icon{
  display:grid;place-items:center;flex:0 0 32px;width:32px;height:32px;
  border:1px solid var(--line2);border-radius:5px;background:var(--card);color:var(--ink2);cursor:pointer;
}
.ledger-import-icon svg{width:16px;height:16px}
.ledger-import-icon:hover{border-color:var(--accent);background:var(--accent-soft);color:var(--accent)}
.ledger-import-icon.disabled{opacity:.45;cursor:wait}
@media(max-width:767px){
  .ledger-page{width:100%;max-width:100%;overflow-x:clip}
  .overview-toolbar,.overview-period-control{width:100%;min-width:0}
  .overview-period-control{align-items:flex-start;gap:8px}
  .overview-range-mode,.period-picker{align-self:flex-start;width:max-content;max-width:100%;flex:none}
  .period-picker input{width:140px;min-width:0}
  .home-custom-range{grid-template-columns:minmax(0,1fr) minmax(0,1fr);width:100%}
  .home-custom-range input{width:100%;min-width:0}
  .home-widget-grid{grid-template-columns:minmax(0,1fr)}
  .head-right{justify-content:flex-start}
  .head-right .seg{max-width:100%;overflow-x:auto}
  .account-grid{grid-template-columns:repeat(2,minmax(0,1fr))}
  .account{padding:10px}
  .account strong{font-size:11px}
  .ledger-actions .ledger-action-icon,.ledger-actions .ledger-import-icon{flex:0 0 32px}
}
@media(max-width:480px){
  .accounts-card .account{display:grid;grid-template-columns:30px minmax(0,1fr);gap:4px 8px}
  .accounts-card .account>span:not(.ledger-resource-icon){grid-column:2}
  .accounts-card .account strong{grid-column:2;max-width:100%;margin-left:0}
}
.ledger-summary-grid :deep(.card){container-type:inline-size;min-width:0;overflow:hidden}
.ledger-summary-grid .summary-value{
  width:100%;max-width:100%;min-width:0;margin:5px 0 10px;
  overflow:hidden;text-overflow:ellipsis;white-space:nowrap;
  font-size:clamp(14px,7.2cqi,32px);line-height:1.2;font-variant-numeric:tabular-nums;
}
.category-card .head-right{display:flex;width:100%;min-width:0;align-items:center;gap:6px;flex-wrap:nowrap;overflow:visible}
.category-card .head-right .seg{flex:0 1 auto;min-width:0;gap:2px}
.category-card .head-right .seg button{min-width:0;padding:0 clamp(5px,1vw,9px);white-space:nowrap}
.accounts-card{grid-column:1 / -1;min-width:0}
.accounts-card .account-grid{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:9px}
.accounts-card .account{min-width:0;min-height:64px}
.accounts-card .account strong{max-width:48%;min-width:0;overflow:hidden;text-overflow:ellipsis;white-space:nowrap}
.accounts-card .account-add{display:grid;place-items:center;width:100%;height:36px;margin-top:10px}
.overview-period-control{max-width:100%}
.overview-range-mode,.period-picker{width:fit-content;max-width:100%}
.overview-range-mode button{white-space:nowrap}
.ai-button{display:grid;place-items:center;width:100%;height:38px}
.form-footer .ledger-action-icon{width:38px;height:38px;flex-basis:38px}
.home-row-actions{white-space:nowrap}
.home-row-actions .ledger-action-icon{display:inline-grid;vertical-align:middle;margin-left:3px}
.ai-panel{display:flex;flex-direction:column;gap:16px}
.ai-panel-intro{display:flex;align-items:flex-start;justify-content:space-between;gap:16px;padding:15px 16px;border:1px solid var(--line);border-radius:6px;background:linear-gradient(135deg,var(--accent-soft),var(--card) 70%)}
.ai-eyebrow{display:block;color:var(--accent);font-size:9px;font-weight:700;letter-spacing:.16em}
.ai-panel-intro h3{margin:4px 0 3px;color:var(--ink);font:700 20px/1.2 Georgia,serif}
.ai-panel-intro p{margin:0;color:var(--muted);font-size:11px;line-height:1.6}
.ai-status{display:inline-flex;align-items:center;gap:6px;flex:0 0 auto;color:var(--muted);font-size:10px;white-space:nowrap}
.ai-status i{width:7px;height:7px;border-radius:50%;background:var(--warn)}.ai-status i.ready{background:var(--down)}
.ai-input{min-height:104px;resize:vertical}
.ai-panel-actions{display:flex;align-items:center;justify-content:space-between;gap:12px;color:var(--muted);font-size:10px}
.ai-panel-actions .ai-button{width:auto;min-width:108px;margin:0}
.ai-preview{margin-top:2px;padding-top:16px;border-top:1px solid var(--line)}
.ai-preview-head{display:flex;align-items:center;justify-content:space-between;gap:10px;margin-bottom:10px}
.ai-preview-head>div{display:flex;align-items:baseline;gap:8px}.ai-preview-head b{color:var(--ink);font-size:13px}.ai-preview-head span{color:var(--muted);font-size:10px}
.ai-draft-list{display:flex;flex-direction:column;gap:9px}.ai-draft{padding:11px 12px;border:1px solid var(--line);border-radius:5px;background:var(--card)}
.ai-draft-head{display:flex;align-items:center;gap:8px;min-width:0}.ai-draft-index{color:var(--muted);font:600 10px Georgia,serif}.ai-kind{padding:2px 6px;border-radius:3px;background:var(--accent-soft);color:var(--accent);font-size:10px}.ai-draft-head>b{margin-left:auto;font:700 16px Georgia,serif}.ai-draft-head>b.income{color:var(--down)}.ai-draft-head>b.expense{color:var(--up)}.ai-draft-head>b.transfer{color:var(--ink2)}
.ai-draft-state{color:var(--down);font-size:10px}.ai-draft-state.invalid{color:var(--warn)}
.ai-draft-fields{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:9px 10px;margin-top:10px}.ai-draft-fields label{display:flex;min-width:0;flex-direction:column;gap:4px;color:var(--ink2);font-size:10px;font-weight:600}.ai-draft-fields .ui-input,.ai-draft-fields select{height:32px;padding:0 8px;font-size:11px}.ai-note-field{grid-column:1/-1}
.ai-warning{display:flex;align-items:flex-start;gap:5px;margin:9px 0 0;color:var(--warn);font-size:10px;line-height:1.5}.ai-warning span{display:grid;place-items:center;flex:0 0 14px;width:14px;height:14px;border-radius:50%;background:var(--warn);color:#fff;font-size:9px;font-weight:700}
.ai-preview-footer{display:flex;align-items:center;justify-content:space-between;gap:10px;margin-top:12px;padding-top:12px;border-top:1px solid var(--line);color:var(--warn);font-size:10px}.ai-preview-footer .ledger-action-icon{width:auto;padding:0 12px}
@media(max-width:560px){.ai-panel-intro{padding:12px}.ai-panel-intro h3{font-size:18px}.ai-panel-actions{align-items:flex-start;flex-direction:column}.ai-panel-actions .ai-button{width:100%}.ai-draft-fields{grid-template-columns:1fr}.ai-note-field{grid-column:auto}.ai-preview-footer{align-items:stretch;flex-direction:column}.ai-preview-footer .ledger-action-icon{width:100%}}
@media(max-width:767px){
  .overview-period-control{width:auto;max-width:100%;align-items:flex-start}
  .overview-range-mode,.period-picker{width:fit-content;align-self:flex-start;flex:none}
  .home-custom-range{width:min(100%,360px)}
  .category-card .head-right{justify-content:flex-start;overflow:visible}
  .category-card .head-right .seg button{padding:0 5px;font-size:10px}
  .transactions :deep(th:last-child),.transactions :deep(td:last-child){width:72px}
  .transactions .home-row-actions .ledger-action-icon{width:28px;height:28px;min-width:28px;margin-left:2px}
}
@media(max-width:360px){
  .category-card .head-right{gap:3px}
  .category-card .head-right .seg button{padding:0 3px}
  .accounts-card .account{grid-template-columns:24px minmax(0,1fr);padding:8px}
  .accounts-card .account strong{max-width:100%}
}
</style>
