export const worktimeNavigation = [
  { key: 'punch', to: '/punch', label: '打卡' },
  { key: 'records', to: '/records', label: '记录' },
  { key: 'stats', to: '/stats', label: '统计' },
  { key: 'settings', to: '/worktime/settings', label: '设置' }
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

export const taskNavigation = [
  { key: 'task', to: '/tasks/today', label: '任务', activePrefix: '/tasks', activeExclude: ['/tasks/habits', '/tasks/calendar', '/tasks/focus', '/tasks/countdowns', '/tasks/board', '/tasks/quadrant', '/tasks/search', '/tasks/stats', '/tasks/settings'] },
  { key: 'focus', to: '/tasks/focus', label: '专注' },
  { key: 'habits', to: '/tasks/habits', label: '习惯' },
  { key: 'calendar', to: '/tasks/calendar', label: '日历' },
  { key: 'countdowns', to: '/tasks/countdowns', label: '倒数日' },
  { key: 'board', to: '/tasks/board', label: '看板' },
  { key: 'quadrant', to: '/tasks/quadrant', label: '四象限' },
  { key: 'search', to: '/tasks/search', label: '搜索与智能清单' },
  { key: 'stats', to: '/tasks/stats', label: '统计与回顾' },
  { key: 'settings', to: '/tasks/settings', label: '设置' }
]

export function navigationItemIsActive(route, item) {
  if (item.activePrefix && (route.path === item.activePrefix || route.path.startsWith(`${item.activePrefix}/`))) {
    return !(item.activeExclude || []).some(path => route.path === path || route.path.startsWith(`${path}/`))
  }
  const target = typeof item.to === 'string' ? { path: item.to } : item.to
  if (route.path !== target.path) return false
  if (item.views) return item.views.includes(String(route.query.view || 'categories'))
  return target.query?.view ? route.query.view === target.query.view : true
}
