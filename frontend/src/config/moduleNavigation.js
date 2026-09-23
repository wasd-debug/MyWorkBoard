export const worktimeNavigation = [
  { key: 'punch', to: '/punch', label: '打卡' },
  { key: 'records', to: '/records', label: '记录' },
  { key: 'stats', to: '/stats', label: '统计' }
]

export const ledgerNavigation = [
  { key: 'overview', to: '/ledger', label: '总览' },
  { key: 'transactions', to: '/ledger/transactions', label: '流水' },
  { key: 'accounts', to: { path: '/ledger/manage', query: { view: 'accounts' } }, label: '账户' },
  { key: 'reports', to: '/ledger/reports', label: '报表' },
  { key: 'scheduled', to: '/ledger/scheduled-tasks', label: '定时任务' },
  { key: 'manage', to: { path: '/ledger/manage', query: { view: 'categories' } }, label: '管理', views: ['categories', 'merchants', 'projects', 'books'] },
  { key: 'members', to: { path: '/ledger/manage', query: { view: 'members' } }, label: '成员与权限' },
  { key: 'recycle', to: { path: '/ledger/manage', query: { view: 'recycle' } }, label: '回收站' },
  { key: 'audit', to: { path: '/ledger/manage', query: { view: 'audit' } }, label: '操作日志' }
]

export function navigationItemIsActive(route, item) {
  const target = typeof item.to === 'string' ? { path: item.to } : item.to
  if (route.path !== target.path) return false
  if (item.views) return item.views.includes(String(route.query.view || 'categories'))
  return target.query?.view ? route.query.view === target.query.view : true
}
