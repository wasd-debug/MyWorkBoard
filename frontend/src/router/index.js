import { createRouter, createWebHistory } from 'vue-router'

const routes = [
  { path: '/', name: 'workspace-home', component: () => import('../views/HomeView.vue'), meta: { title: 'AI 工作台' } },
  { path: '/approvals/:id?', name: 'agent-approvals', component: () => import('../views/AgentApprovalsView.vue'), meta: { title: '审批中心' } },
  { path: '/mcp/actions/confirm', name: 'mcp-action-confirm', component: () => import('../views/McpActionConfirmationView.vue'), meta: { title: '外部操作确认' } },
  { path: '/oauth/consent', name: 'mcp-oauth-consent', component: () => import('../views/McpOAuthConsentView.vue'), meta: { title: '授权外部应用' } },
  { path: '/punch', name: 'punch', component: () => import('../views/PunchView.vue'), meta: { title: '打卡' } },
  { path: '/records', name: 'records', component: () => import('../views/RecordsView.vue'), meta: { title: '记录' } },
  { path: '/stats', name: 'stats', component: () => import('../views/StatsView.vue'), meta: { title: '统计' } },
  { path: '/worktime/settings', name: 'worktime-settings', component: () => import('../views/WorktimeSettingsView.vue'), meta: { title: '工时设置' } },
  { path: '/tasks', redirect: '/tasks/today' },
  { path: '/tasks/inbox', name: 'tasks-inbox', component: () => import('../views/TasksInboxView.vue'), meta: { title: '任务收件箱' } },
  { path: '/tasks/today', name: 'tasks-today', component: () => import('../views/TasksInboxView.vue'), meta: { title: '今天' } },
  { path: '/tasks/next7', name: 'tasks-next7', component: () => import('../views/TasksInboxView.vue'), meta: { title: '最近 7 天' } },
  { path: '/tasks/all', name: 'tasks-all', component: () => import('../views/TasksInboxView.vue'), meta: { title: '全部任务' } },
  { path: '/tasks/completed', name: 'tasks-completed', component: () => import('../views/TasksInboxView.vue'), meta: { title: '已完成任务' } },
  { path: '/tasks/inbox-notify', name: 'tasks-notifications', component: () => import('../views/TaskNotificationsView.vue'), meta: { title: '任务提醒' } },
  { path: '/tasks/trash', name: 'tasks-trash', component: () => import('../views/TasksInboxView.vue'), meta: { title: '任务垃圾桶' } },
  { path: '/tasks/list/:publicId', name: 'tasks-list', component: () => import('../views/TasksInboxView.vue'), meta: { title: '任务清单' } },
  { path: '/tasks/tag/:publicId', name: 'tasks-tag', component: () => import('../views/TasksInboxView.vue'), meta: { title: '任务标签' } },
  { path: '/ledger', name: 'ledger', component: () => import('../views/LedgerView.vue'), meta: { title: '账本' } },
  { path: '/ledger/transactions', name: 'ledger-transactions', component: () => import('../views/LedgerTransactionsView.vue'), meta: { title: '流水' } },
  { path: '/ledger/reports', name: 'ledger-reports', component: () => import('../views/LedgerReportsView.vue'), meta: { title: '账本报表' } },
  { path: '/ledger/scheduled-tasks', name: 'ledger-scheduled-tasks', component: () => import('../views/LedgerScheduledTasksView.vue'), meta: { title: '定时任务' } },
  { path: '/ledger/manage', name: 'ledger-manage', component: () => import('../views/LedgerManagementView.vue'), meta: { title: '账本管理' } },
  { path: '/settings', name: 'settings', component: () => import('../views/SettingsView.vue'), meta: { title: '设置' } }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

export default router
