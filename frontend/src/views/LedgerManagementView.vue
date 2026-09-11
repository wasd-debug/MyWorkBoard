<template>
  <section class="ledger-management">
    <LedgerBookSwitcher />
    <div class="page-heading"><div><div class="label">PERSONAL FINANCE / MANAGEMENT</div><h1>账本管理</h1><p class="muted">资源、成员权限、回收站和操作日志统一管理。</p></div></div>
    <div class="manager-nav">
      <button v-for="item in tabs" :key="item.key" :class="{active:tab===item.key}" @click="tab=item.key">{{ item.label }}</button>
    </div>
    <Card>
      <template #header><div class="card-heading"><div><div class="kicker">{{ activeTab.label }}</div><h2>{{ activeTab.title }}</h2></div><Button v-if="tab!=='recycle'&&tab!=='audit'" size="sm" @click="resetForm">{{ activeTab.action }}</Button></div></template>
      <div v-if="tab==='accounts'" class="resource-list"><div v-for="item in ledger.accounts" :key="item.id" class="resource-row"><div><b>{{item.name}}</b><small>{{item.accountType}} · ¥{{money(item.balance)}}</small></div><button @click="removeResource('account',item)">隐藏</button></div><form class="inline-form" @submit.prevent="createAccount"><input v-model="forms.account.name" class="ui-input" placeholder="账户名称" required/><select v-model="forms.account.accountType"><option value="cash">现金</option><option value="bank">银行卡</option><option value="card">信用卡</option><option value="wallet">电子钱包</option></select><input v-model.number="forms.account.openingBalance" class="ui-input" type="number" placeholder="期初余额"/><Button type="submit">新增</Button></form></div>
      <div v-else-if="tab==='categories'" class="resource-list"><div v-for="item in ledger.categories" :key="item.id" class="resource-row"><div><b>{{path(item)}}</b><small>{{item.kind==='INCOME'?'收入':'支出'}} · {{item.hidden?'已隐藏':'可用'}}</small></div><button @click="removeResource('category',item)">隐藏</button></div><form class="inline-form" @submit.prevent="createCategory"><input v-model="forms.category.name" class="ui-input" placeholder="二级分类名称" required/><select v-model="forms.category.kind"><option value="EXPENSE">支出</option><option value="INCOME">收入</option></select><select v-model="forms.category.parentId" required><option value="" disabled>选择一级分类</option><option v-for="parent in parents" :key="parent.id" :value="parent.id">{{parent.name}}</option></select><Button type="submit">新增</Button></form></div>
      <div v-else-if="tab==='merchants'||tab==='projects'" class="resource-list"><div v-for="item in namedItems" :key="item.id" class="resource-row"><div><b>{{item.name}}</b><small>{{item.note||'未填写备注'}}</small></div><button @click="removeResource(tab.slice(0,-1),item)">隐藏</button></div><form class="inline-form" @submit.prevent="createNamed"><input v-model="forms.named.name" class="ui-input" placeholder="名称" required/><input v-model="forms.named.note" class="ui-input" placeholder="备注（可选）"/><Button type="submit">新增</Button></form></div>
      <div v-else-if="tab==='members'" class="resource-list"><div v-for="item in ledger.members" :key="item.id" class="resource-row"><div><b>{{item.displayName}}</b><small>{{item.username}} · {{item.roleName}}</small></div><button v-if="item.roleCode!=='OWNER'" @click="removeResource('member',item)">移除</button></div><form class="inline-form" @submit.prevent="createMember"><input v-model="forms.member.username" class="ui-input" placeholder="已注册用户名" required/><select v-model="forms.member.roleId"><option value="">普通成员</option><option v-for="role in ledger.roles" :key="role.id" :value="role.id">{{role.name}}</option></select><Button type="submit">加入</Button></form></div>
      <div v-else-if="tab==='roles'" class="resource-list"><div v-for="item in ledger.roles" :key="item.id" class="resource-row"><div><b>{{item.name}}</b><small>{{item.systemRole?'系统角色':'自定义角色'}} · {{item.permissions.join('、')}}</small></div><button v-if="!item.systemRole" @click="removeResource('role',item)">删除</button></div><form class="inline-form" @submit.prevent="createRole"><input v-model="forms.role.name" class="ui-input" placeholder="角色名称" required/><input v-model="forms.role.permissions" class="ui-input" placeholder="权限代码，逗号分隔"/><Button type="submit">新增</Button></form></div>
      <div v-else-if="tab==='recycle'" class="resource-list"><div v-if="!recycle.items?.length" class="empty">回收站为空</div><div v-for="item in recycle.items" :key="`${item.type}-${item.id}`" class="resource-row"><div><b>{{item.label}}</b><small>{{item.type}} · {{item.deletedAt}}</small></div><span class="row-actions"><button @click="restore(item)">恢复</button><button v-if="canPurge" @click="purge(item)">永久删除</button></span></div></div>
      <div v-else class="audit-list"><div v-if="!audit.items?.length" class="empty">暂无操作日志</div><div v-for="item in audit.items" :key="item.id" class="audit-row"><b>{{item.action}}</b><span>{{item.targetType}} / {{item.targetId}}</span><small>{{item.actor?.nickname||item.actor?.username}} · {{item.createdAt}}</small></div></div>
    </Card>
  </section>
</template>

<script setup>
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { apiClearLedgerAuditLogs, apiListLedgerAuditLogs, apiListLedgerRecycle, apiPurgeLedgerRecycle, apiRestoreLedgerRecycle } from '../api'
import { useLedgerStore } from '../stores/ledger'
import LedgerBookSwitcher from '../components/ledger/LedgerBookSwitcher.vue'
import Button from '../components/ui/Button.vue'
import Card from '../components/ui/Card.vue'

const ledger = useLedgerStore()
const tab = ref('accounts')
const recycle = ref({ items: [] })
const audit = ref({ items: [] })
const forms = reactive({
  account: { name: '', accountType: 'cash', openingBalance: 0 },
  category: { name: '', kind: 'EXPENSE', parentId: '' },
  named: { name: '', note: '' },
  member: { username: '', roleId: '' },
  role: { name: '', permissions: '' }
})
const tabs = [
  { key: 'accounts', label: '账户', title: '账户', action: '清空表单' },
  { key: 'categories', label: '分类', title: '两级分类', action: '清空表单' },
  { key: 'merchants', label: '商家', title: '商家', action: '清空表单' },
  { key: 'members', label: '成员', title: '账本成员', action: '清空表单' },
  { key: 'projects', label: '项目', title: '项目', action: '清空表单' },
  { key: 'roles', label: '角色权限', title: '角色与权限', action: '清空表单' },
  { key: 'recycle', label: '回收站', title: '回收站', action: '' },
  { key: 'audit', label: '操作日志', title: '操作日志', action: '' }
]
const activeTab = computed(() => tabs.find(item => item.key === tab.value) || tabs[0])
const parents = computed(() => ledger.categories.filter(item => !item.parentId && item.kind === forms.category.kind && !item.hidden))
const namedItems = computed(() => tab.value === 'merchants' ? ledger.merchants : ledger.projects)
const canPurge = computed(() => ledger.currentBook?.roleCode === 'OWNER' || ledger.currentBook?.roleCode === 'ADMIN')

onMounted(async () => { await ledger.init(); await loadSecondary() })
watch(tab, loadSecondary)
watch(() => ledger.currentBookId, loadSecondary)

async function loadSecondary() {
  if (!ledger.currentBookId) return
  if (tab.value === 'recycle') recycle.value = await apiListLedgerRecycle(ledger.currentBookId)
  if (tab.value === 'audit') audit.value = await apiListLedgerAuditLogs(ledger.currentBookId)
}
async function createAccount() { await ledger.put('account', forms.account); resetForm() }
async function createCategory() { await ledger.put('category', forms.category); resetForm() }
async function createNamed() { await ledger.put(tab.value.slice(0, -1), forms.named); resetForm() }
async function createMember() { await ledger.put('member', forms.member); resetForm() }
async function createRole() { await ledger.put('role', { name: forms.role.name, permissions: forms.role.permissions.split(/[,，]/).map(item => item.trim()).filter(Boolean) }); resetForm() }
async function removeResource(type, item) { await ledger.remove(type, item); ElMessage.success('已移入回收站') }
async function restore(item) { await apiRestoreLedgerRecycle(ledger.currentBookId, item.type, item.id); await loadSecondary(); await ledger.refreshCurrentBook(); ElMessage.success('已恢复') }
async function purge(item) { await apiPurgeLedgerRecycle(ledger.currentBookId, item.type, item.id); await loadSecondary(); ElMessage.success('已永久删除') }
function resetForm() { Object.assign(forms.account, { name: '', accountType: 'cash', openingBalance: 0 }); Object.assign(forms.category, { name: '', kind: 'EXPENSE', parentId: '' }); Object.assign(forms.named, { name: '', note: '' }); Object.assign(forms.member, { username: '', roleId: '' }); Object.assign(forms.role, { name: '', permissions: '' }) }
function path(item) { const parent = ledger.categories.find(value => value.id === item.parentId); return parent ? `${parent.name} / ${item.name}` : item.name }
function money(value) { return Number(value || 0).toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 }) }
</script>

<style scoped>
.ledger-management{min-width:0}.manager-nav{display:flex;gap:4px;overflow:auto;margin:0 0 14px;padding-bottom:2px}.manager-nav button{white-space:nowrap;border:1px solid var(--line);border-radius:3px;background:var(--card);color:var(--muted);padding:8px 12px;font-size:12px}.manager-nav button.active{border-color:var(--accent);background:var(--accent-soft);color:var(--ink);font-weight:700}.card-heading{display:flex;align-items:center;justify-content:space-between;gap:12px}.kicker{color:var(--muted);font-size:10px;letter-spacing:.14em}.card-heading h2{margin:4px 0 0;font:700 20px Georgia,serif}.resource-list,.audit-list{display:flex;flex-direction:column;gap:0}.resource-row{display:flex;align-items:center;justify-content:space-between;gap:12px;padding:12px 0;border-bottom:1px solid var(--line)}.resource-row b,.resource-row small{display:block}.resource-row b{color:var(--ink);font-size:13px}.resource-row small{margin-top:3px;color:var(--muted);font-size:11px}.resource-row button,.row-actions button{border:0;background:transparent;color:var(--accent);font-size:11px;cursor:pointer}.row-actions{display:flex;gap:8px}.inline-form{display:grid;grid-template-columns:repeat(4,minmax(0,1fr));gap:8px;margin-top:14px}.inline-form .ui-input,.inline-form select{min-width:0;width:100%;box-sizing:border-box}.empty{padding:42px 0;text-align:center;color:var(--muted);font-size:12px}.audit-row{display:grid;grid-template-columns:180px 1fr auto;gap:12px;padding:11px 0;border-bottom:1px solid var(--line);font-size:12px}.audit-row span,.audit-row small{color:var(--muted)}@media(max-width:680px){.inline-form{grid-template-columns:1fr 1fr}.audit-row{grid-template-columns:1fr;gap:3px}}
</style>
