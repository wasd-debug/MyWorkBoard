<template>
  <main class="efficiency-page">
    <header class="efficiency-head">
      <div><p class="label">TASKS / HABITS</p><h1>习惯</h1><p>按用户时区计算周期，跳过不会记为完成。</p></div>
      <Button @click="openForm()"><Plus :size="16" />新建习惯</Button>
    </header>
    <p v-if="error" class="efficiency-error">{{ error }}</p>
    <section v-if="!habits.length" class="empty-copy">还没有习惯，先创建一个每日目标。</section>
    <section v-for="habit in habits" :key="habit.publicId" class="habit-card">
      <div class="habit-card-head">
        <span class="habit-mark" :style="{ background: habit.color }">{{ habit.icon }}</span>
        <div><h2>{{ habit.name }}</h2><p>{{ frequencyLabel(habit) }} · 开始于 {{ habit.startDate }}</p></div>
        <div class="card-actions"><Button size="small" variant="ghost" @click="openForm(habit)"><Pencil :size="15" />编辑</Button><Button size="small" variant="danger" @click="remove(habit)"><Trash2 :size="15" /></Button></div>
      </div>
      <div class="habit-stats"><span>30 天完成率 {{ stats[habit.publicId]?.completionRate30Days || 0 }}%</span><span>本月 {{ stats[habit.publicId]?.monthCompleted || 0 }}/{{ stats[habit.publicId]?.monthTarget || 0 }}</span></div>
      <div class="habit-actions"><Button @click="checkin(habit, 'DONE')"><Check :size="16" />今日打卡</Button><Button variant="ghost" @click="checkin(habit, 'SKIP')">跳过今日</Button><Button variant="ghost" @click="openBackfill(habit)">补卡</Button></div>
    </section>
    <Dialog v-model:open="formOpen" :title="editing ? '编辑习惯' : '新建习惯'">
      <form id="habit-form" class="form-grid" @submit.prevent="save">
        <label>名称<input v-model="form.name" required maxlength="120"></label><label>图标<input v-model="form.icon" maxlength="32"></label>
        <label>颜色<input v-model="form.color" type="color"></label><label>频率<select v-model="form.frequency"><option value="DAILY">每天</option><option value="WEEKLY_N">每周 N 次</option><option value="MONTHLY_N">每月 N 次</option><option value="CUSTOM">自定义星期</option></select></label>
        <label>目标次数<input v-model.number="form.targetCount" type="number" min="1"></label><label>开始日期<input v-model="form.startDate" type="date" required></label>
        <fieldset v-if="form.frequency === 'CUSTOM'" class="weekday-field"><legend>执行星期</legend><label v-for="day in weekdays" :key="day.v"><input v-model="form.customDays" type="checkbox" :value="day.v">周{{ day.l }}</label></fieldset>
      </form>
      <template #footer><Button type="submit" form="habit-form">保存</Button></template>
    </Dialog>
    <Dialog v-model:open="backfillOpen" title="补卡"><label class="dialog-field">日期<input v-model="backfillDate" type="date"></label><template #footer><Button @click="submitBackfill">确认补卡</Button></template></Dialog>
  </main>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { Check, Pencil, Plus, Trash2 } from 'lucide-vue-next'
import Button from '../components/ui/Button.vue'
import Dialog from '../components/ui/Dialog.vue'
import { useTasksStore } from '../stores/tasks.js'

const store = useTasksStore()
const habits = computed(() => store.habits)
const error = computed(() => store.error)
const formOpen = ref(false)
const backfillOpen = ref(false)
const editing = ref(null)
const backfillHabit = ref(null)
const backfillDate = ref(new Date().toISOString().slice(0, 10))
const form = reactive({ name: '', icon: '✓', color: '#2f746f', frequency: 'DAILY', targetCount: 1, customDays: [], startDate: new Date().toISOString().slice(0, 10), archived: false, sortOrder: 0 })
const weekdays = [{ v: 1, l: '一' }, { v: 2, l: '二' }, { v: 3, l: '三' }, { v: 4, l: '四' }, { v: 5, l: '五' }, { v: 6, l: '六' }, { v: 7, l: '日' }]
const stats = computed(() => Object.fromEntries(habits.value.map(habit => [habit.publicId, localStats(habit)])))

function frequencyLabel(item) { return { DAILY: '每天', WEEKLY_N: `每周 ${item.targetCount} 次`, MONTHLY_N: `每月 ${item.targetCount} 次`, CUSTOM: '自定义星期' }[item.frequency] || item.frequency }
function openForm(item = null) { editing.value = item; Object.assign(form, item ? { ...item, customDays: [...(item.customDays || [])] } : { name: '', icon: '✓', color: '#2f746f', frequency: 'DAILY', targetCount: 1, customDays: [], startDate: new Date().toISOString().slice(0, 10), archived: false, sortOrder: habits.value.length }); formOpen.value = true }
async function save() { const payload = { name: form.name, icon: form.icon, color: form.color, frequency: form.frequency, targetCount: form.targetCount, customDays: form.frequency === 'CUSTOM' ? form.customDays : [], startDate: form.startDate, archived: false, sortOrder: form.sortOrder }; if (editing.value) await store.updateHabit(editing.value, payload); else await store.createHabit(payload); formOpen.value = false }
async function checkin(item, status, date = new Date().toISOString().slice(0, 10)) { await store.checkinHabit(item, { date, count: status === 'SKIP' ? 0 : 1, status }) }
function openBackfill(item) { backfillHabit.value = item; backfillDate.value = new Date().toISOString().slice(0, 10); backfillOpen.value = true }
async function submitBackfill() { backfillOpen.value = false; if (backfillHabit.value) await checkin(backfillHabit.value, 'DONE', backfillDate.value) }
async function remove(item) { if (window.confirm(`删除习惯“${item.name}”？`)) await store.deleteHabit(item) }
function localStats(habit) { const rows = store.habitCheckins.filter(item => item.habitId === habit.publicId && item.status === 'DONE'); const today = new Date().toISOString().slice(0, 10); const month = today.slice(0, 7); const completed = rows.filter(item => item.date.startsWith(month)).reduce((sum, item) => sum + item.count, 0); const last30 = new Date(); last30.setDate(last30.getDate() - 29); const recent = rows.filter(item => item.date >= last30.toISOString().slice(0, 10)).reduce((sum, item) => sum + item.count, 0); const target = habit.frequency === 'MONTHLY_N' ? habit.targetCount : habit.frequency === 'WEEKLY_N' ? habit.targetCount * 5 : habit.targetCount * 30; return { completionRate30Days: Math.min(100, Math.round(recent * 100 / Math.max(1, target))), monthCompleted: completed, monthTarget: habit.frequency === 'MONTHLY_N' ? habit.targetCount : habit.targetCount * new Date().getDate() } }
onMounted(() => store.init({ waitForRemote: false }))
</script>

<style scoped>
.efficiency-page{max-width:980px;margin:0 auto;padding:34px 28px 100px}.efficiency-head{display:flex;align-items:flex-end;justify-content:space-between;gap:18px}.efficiency-head h1{margin:5px 0;font-size:2rem}.efficiency-head p:last-child{margin:0;color:var(--muted)}.efficiency-error{padding:10px;border-left:3px solid var(--down);background:color-mix(in srgb,var(--down) 8%,transparent)}.empty-copy{padding:46px 0;color:var(--muted)}.habit-card{margin-top:16px;padding:18px;border:1px solid var(--line);background:var(--panel)}.habit-card-head{display:flex;align-items:center;gap:12px}.habit-mark{display:grid;width:40px;height:40px;place-items:center;border-radius:50%;color:#fff;font-weight:700}.habit-card h2{margin:0;font-size:1.05rem}.habit-card p{margin:4px 0 0;color:var(--muted);font-size:.78rem}.card-actions{display:flex;gap:5px;margin-left:auto}.habit-stats{display:flex;flex-wrap:wrap;gap:18px;margin:18px 0;color:var(--muted);font-size:.8rem}.habit-actions{display:flex;flex-wrap:wrap;gap:8px}.form-grid{display:grid;grid-template-columns:repeat(2,1fr);gap:12px}.form-grid label,.dialog-field{display:grid;gap:6px;color:var(--muted);font-size:.78rem}.form-grid input,.form-grid select,.dialog-field input{min-height:38px;padding:7px 9px;border:1px solid var(--line2);background:var(--card);color:var(--ink)}.weekday-field{grid-column:1/-1;display:flex;flex-wrap:wrap;gap:10px;border:1px solid var(--line)}.weekday-field label{display:flex;align-items:center;gap:4px}.weekday-field input{min-height:auto}@media(max-width:640px){.efficiency-page{padding:22px 14px 100px}.efficiency-head{align-items:flex-start;flex-direction:column}.habit-card-head{align-items:flex-start}.card-actions{flex-wrap:wrap}.form-grid{grid-template-columns:1fr}}
</style>
