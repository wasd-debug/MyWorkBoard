<template>
  <section class="scheduled-page">
    <LoadingOverlay :open="loading || saving" :label="saving ? '正在保存定时任务…' : '正在读取定时任务…'" />
    <header class="page-heading">
      <div><p class="eyebrow">账本 / 定时任务</p><h1>定时任务</h1><p>周期流水和未来账单拉取统一在这里管理。</p></div>
      <button class="ui-button primary" type="button" @click="openCreate">新增任务</button>
    </header>
    <Card class="task-card">
      <div class="task-table">
        <div class="task-head"><span>任务</span><span>执行规则</span><span>下次执行</span><span>状态</span><span>操作</span></div>
        <Empty v-if="!tasks.length" description="暂无定时任务" />
        <div v-for="task in tasks" :key="task.id" class="task-row">
          <div><b>{{ task.name }}</b><small>{{ task.taskType === 'STATEMENT_IMPORT' ? '账单拉取（待接入）' : '周期流水' }} · 已执行 {{ task.runCount || 0 }} 次</small></div>
          <span>{{ scheduleLabel(task) }}</span>
          <span>{{ task.nextRunOn }}</span>
          <span :class="task.lastRunStatus === 'FAILED' ? 'failed' : ''">{{ task.enabled ? '启用' : '暂停' }}<small v-if="task.lastRunStatus">{{ task.lastRunStatus === 'APPLIED' ? '最近成功' : task.lastError }}</small></span>
          <div class="actions"><button class="icon-button" title="立即执行" type="button" @click="runTask(task)">▶</button><button class="icon-button" title="启停" type="button" @click="toggleTask(task)">{{ task.enabled ? 'Ⅱ' : '▶' }}</button><button class="icon-button danger" title="删除" type="button" @click="removeTask(task)">×</button></div>
        </div>
      </div>
    </Card>

    <Dialog v-model:open="formOpen" title="新增定时任务">
      <form class="task-form" @submit.prevent="saveTask">
        <label>任务名称<input v-model="form.name" required placeholder="例如：每月工资" /></label>
        <div class="form-grid"><label>任务类型<select v-model="form.taskType"><option value="RECURRING_TRANSACTION">周期流水</option><option value="STATEMENT_IMPORT">账单拉取（待接入）</option></select></label><label>生效日期<input v-model="form.startOn" type="date" required /></label></div>
        <div class="schedule-mode">
          <button type="button" :class="{active:form.scheduleMode==='CALENDAR'}" @click="setScheduleMode('CALENDAR')"><b>固定日期</b><small>按星期、月中日期或年度日期</small></button>
          <button type="button" :class="{active:form.scheduleMode==='INTERVAL'}" @click="setScheduleMode('INTERVAL')"><b>间隔执行</b><small>从生效日期开始按周期重复</small></button>
        </div>

        <template v-if="form.scheduleMode === 'CALENDAR'">
          <label>固定周期<select v-model="form.frequency" @change="resetCalendarRule"><option value="WEEKLY">每周</option><option value="MONTHLY">每月</option><option value="YEARLY">每年</option></select></label>
          <div v-if="form.frequency === 'WEEKLY'" class="form-grid single-rule"><label>每周星期<select v-model.number="form.calendarRule.dayOfWeek"><option v-for="item in weekdays" :key="item.value" :value="item.value">{{ item.label }}</option></select></label></div>
          <template v-else-if="form.frequency === 'MONTHLY'">
            <label>每月规则<select v-model="form.calendarRule.monthlyMode"><option value="DAY_OF_MONTH">每月几号</option><option value="NTH_WEEKDAY">第几周星期几</option></select></label>
            <div v-if="form.calendarRule.monthlyMode === 'DAY_OF_MONTH'" class="form-grid single-rule"><label>每月日期<input v-model.number="form.calendarRule.dayOfMonth" type="number" min="1" max="31" required /></label></div>
            <div v-else class="form-grid"><label>第几周<select v-model.number="form.calendarRule.weekOfMonth"><option v-for="week in 5" :key="week" :value="week">第 {{ week }} 周</option></select></label><label>星期<select v-model.number="form.calendarRule.dayOfWeek"><option v-for="item in weekdays" :key="item.value" :value="item.value">{{ item.label }}</option></select></label></div>
            <p v-if="form.calendarRule.monthlyMode === 'NTH_WEEKDAY' && form.calendarRule.weekOfMonth === 5" class="hint">当某个月没有第 5 个指定星期时，该月不会执行。</p>
          </template>
          <div v-else class="form-grid"><label>月份<select v-model.number="form.calendarRule.month"><option v-for="month in 12" :key="month" :value="month">{{ month }} 月</option></select></label><label>日期<input v-model.number="form.calendarRule.dayOfMonth" type="number" min="1" :max="maxYearlyDay" required /></label></div>
        </template>
        <template v-else>
          <div class="form-grid"><label>间隔单位<select v-model="form.frequency"><option value="ONCE">仅一次</option><option value="DAILY">天</option><option value="WEEKLY">周</option><option value="MONTHLY">月</option><option value="YEARLY">年</option></select></label><label v-if="form.frequency !== 'ONCE'">间隔数量<input v-model.number="form.intervalValue" type="number" min="1" required /></label></div>
          <p class="hint">{{ form.frequency === 'ONCE' ? '仅在生效日期执行一次。' : `从生效日期开始，每 ${form.intervalValue || 1} ${intervalUnitLabel}执行一次。` }}</p>
        </template>

        <template v-if="form.taskType === 'RECURRING_TRANSACTION'">
          <div class="form-grid"><label>流水类型<select v-model="payload.kind"><option value="EXPENSE">支出</option><option value="INCOME">收入</option><option value="BORROW_IN">借入</option><option value="LEND_OUT">借出</option><option value="COLLECT_DEBT">收债</option><option value="REPAY_DEBT">还债</option></select></label><label>金额<input v-model.number="payload.amount" type="number" min="0.01" step="0.01" required /></label></div>
          <div class="form-grid"><label>账户<select v-model="payload.accountId" required><option value="" disabled>请选择账户</option><option v-for="item in ledger.visibleAccounts" :key="item.id" :value="item.id">{{ item.name }}</option></select></label></div>
          <div v-if="['EXPENSE','INCOME'].includes(payload.kind)" class="form-grid"><LedgerCategoryCombobox v-model="payload.categoryId" :kind="payload.kind" :categories="ledger.categories" /></div>
          <label>备注<input v-model="payload.note" placeholder="自动生成的备注" /></label>
        </template>
        <p v-else class="hint">支付宝、微信支付、银行卡账单拉取将在后续接入，当前不会伪造执行结果。</p>
        <footer><button class="ui-button" type="button" @click="formOpen=false">取消</button><button class="ui-button primary" type="submit">保存</button></footer>
      </form>
    </Dialog>
  </section>
</template>

<script setup>
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { ElMessage } from '../services/message.js'
import Card from '../components/ui/Card.vue'
import Dialog from '../components/ui/Dialog.vue'
import Empty from '../components/ui/Empty.vue'
import LoadingOverlay from '../components/ledger/LoadingOverlay.vue'
import LedgerCategoryCombobox from '../components/ledger/LedgerCategoryCombobox.vue'
import { useLedgerStore } from '../stores/ledger'
import { apiListLedgerScheduledTasks } from '../api'

const ledger = useLedgerStore()
const tasks = ref([])
const loading = ref(true)
const saving = ref(false)
const formOpen = ref(false)
const weekdays = [{value:1,label:'星期一'},{value:2,label:'星期二'},{value:3,label:'星期三'},{value:4,label:'星期四'},{value:5,label:'星期五'},{value:6,label:'星期六'},{value:7,label:'星期日'}]
const form = reactive({ name:'', taskType:'RECURRING_TRANSACTION', scheduleMode:'CALENDAR', frequency:'MONTHLY', intervalValue:1, startOn:new Date().toISOString().slice(0,10), calendarRule:defaultCalendarRule() })
const payload = reactive({ kind:'EXPENSE', amount:null, accountId:'', categoryId:'', note:'' })
const intervalUnitLabel = computed(() => ({DAILY:'天',WEEKLY:'周',MONTHLY:'个月',YEARLY:'年',ONCE:'次'}[form.frequency] || '个周期'))
const maxYearlyDay = computed(() => [4,6,9,11].includes(Number(form.calendarRule.month)) ? 30 : Number(form.calendarRule.month) === 2 ? 29 : 31)

function defaultCalendarRule() { return { monthlyMode:'DAY_OF_MONTH', dayOfMonth:1, weekOfMonth:1, dayOfWeek:1, month:1 } }
async function load() { loading.value=true; try { await ledger.init({waitForRemote:false}); tasks.value=ledger.online ? await apiListLedgerScheduledTasks(ledger.currentBookId) : [] } catch(error) { ElMessage.error(error.response?.data?.detail || error.message || '定时任务读取失败') } finally { loading.value=false } }
function openCreate() { Object.assign(form,{name:'',taskType:'RECURRING_TRANSACTION',scheduleMode:'CALENDAR',frequency:'MONTHLY',intervalValue:1,startOn:new Date().toISOString().slice(0,10),calendarRule:defaultCalendarRule()}); Object.assign(payload,{kind:'EXPENSE',amount:null,accountId:ledger.visibleAccounts[0]?.id||'',categoryId:'',note:''}); formOpen.value=true }
function setScheduleMode(mode) { form.scheduleMode=mode; form.frequency='MONTHLY'; form.intervalValue=1; form.calendarRule=defaultCalendarRule() }
function resetCalendarRule() { form.calendarRule=defaultCalendarRule() }
watch(maxYearlyDay, maximum => {
  if (form.scheduleMode === 'CALENDAR' && form.frequency === 'YEARLY' && Number(form.calendarRule.dayOfMonth) > maximum) {
    form.calendarRule.dayOfMonth = maximum
  }
})
async function saveTask() { saving.value=true; try { if(form.taskType==='RECURRING_TRANSACTION'&&['EXPENSE','INCOME'].includes(payload.kind)){const category=ledger.categories.find(item=>String(item.id)===String(payload.categoryId||''));if(!category?.parentId||category.kind!==payload.kind)throw new Error('请选择对应流水类型的二级分类')} const request={...form,calendarRule:form.scheduleMode==='CALENDAR'?{...form.calendarRule}:{},payload:form.taskType==='RECURRING_TRANSACTION'?{...payload}:{}}; await ledger.createScheduledTask(request); formOpen.value=false; await load(); ElMessage.success('定时任务已创建') } catch(error) { ElMessage.error(error.response?.data?.detail||error.message||'保存失败') } finally { saving.value=false } }
async function toggleTask(task) { try { await ledger.updateScheduledTask(task,{enabled:!task.enabled}); await load() } catch(error) { ElMessage.error(error.response?.data?.detail||error.message||'更新失败') } }
async function runTask(task) { try { const result=await ledger.runScheduledTask(task); ElMessage.success(result.status==='APPLIED'?'已生成流水':result.status==='DUPLICATE'?'该日期已执行':'任务未执行'); await load() } catch(error) { ElMessage.error(error.response?.data?.detail||error.message||'执行失败') } }
async function removeTask(task) { if(!window.confirm(`删除“${task.name}”？`))return; try { await ledger.deleteScheduledTask(task); await load() } catch(error) { ElMessage.error(error.response?.data?.detail||error.message||'删除失败') } }
function scheduleLabel(task) { if(task.scheduleMode!=='CALENDAR'||task.frequency==='ONCE')return task.frequency==='ONCE'?'仅一次':`每 ${task.intervalValue||1} ${intervalLabel(task.frequency)}`;const rule=task.calendarRule||{};if(task.frequency==='WEEKLY')return `每周${weekdayShort(rule.dayOfWeek)}`;if(task.frequency==='YEARLY')return `每年 ${rule.month} 月 ${rule.dayOfMonth} 日`;if(rule.monthlyMode==='NTH_WEEKDAY')return `每月第 ${rule.weekOfMonth} 周${weekdayShort(rule.dayOfWeek)}`;return `每月 ${rule.dayOfMonth} 日` }
function intervalLabel(frequency) { return ({DAILY:'天',WEEKLY:'周',MONTHLY:'个月',YEARLY:'年'}[frequency]||'个周期') }
function weekdayShort(value) { return weekdays.find(item=>item.value===Number(value))?.label.replace('星期','周')||'' }
onMounted(load)
</script>

<style scoped>
.scheduled-page{max-width:1180px;margin:0 auto}.page-heading{display:flex;justify-content:space-between;align-items:flex-start;margin-bottom:22px}.eyebrow{color:var(--muted);font-size:11px;letter-spacing:.12em}.page-heading h1{margin:6px 0;font:700 30px Georgia,serif}.page-heading p:last-child{color:var(--muted);font-size:12px}.task-card{padding:0;overflow:hidden}.task-table{width:100%}.task-head,.task-row{display:grid;grid-template-columns:minmax(180px,2fr) 1.3fr 1.2fr 1.2fr 150px;gap:16px;align-items:center;padding:14px 18px}.task-head{background:var(--paper);color:var(--muted);font-size:11px}.task-row{border-top:1px solid var(--line);font-size:12px}.task-row small{display:block;color:var(--muted);font-size:10px;margin-top:4px}.failed{color:var(--down)}.actions{display:flex;gap:6px;justify-content:flex-end}.icon-button{width:30px;height:30px;border:1px solid var(--line2);border-radius:4px;background:var(--card);color:var(--ink2);cursor:pointer}.icon-button:hover{border-color:var(--accent);color:var(--accent)}.icon-button.danger:hover{color:var(--down)}.task-form{display:flex;flex-direction:column;gap:14px}.task-form label{display:flex;flex-direction:column;gap:6px;color:var(--muted);font-size:11px}.task-form input,.task-form select{height:34px;padding:0 9px;border:1px solid var(--line2);border-radius:4px;background:var(--card);color:var(--ink)}.form-grid{display:grid;grid-template-columns:1fr 1fr;gap:12px}.form-grid.single-rule{grid-template-columns:1fr}.schedule-mode{display:grid;grid-template-columns:1fr 1fr;gap:8px}.schedule-mode button{display:flex;min-height:58px;flex-direction:column;align-items:flex-start;justify-content:center;gap:4px;padding:9px 11px;border:1px solid var(--line2);border-radius:5px;background:var(--card);color:var(--ink2);text-align:left;cursor:pointer}.schedule-mode button.active{border-color:var(--accent);background:var(--accent-soft);color:var(--accent)}.schedule-mode small{color:var(--muted);font-size:10px}.hint{margin:0;padding:10px;background:var(--paper);color:var(--muted);font-size:11px;line-height:1.6}.task-form footer{display:flex;justify-content:flex-end;gap:8px;padding-top:6px}@media(max-width:700px){.task-head{display:none}.task-row{grid-template-columns:1fr auto;gap:8px}.task-row>span{font-size:10px}.task-row>span:nth-child(2),.task-row>span:nth-child(3){display:none}.page-heading{gap:10px}.form-grid,.schedule-mode{grid-template-columns:1fr}}
</style>
