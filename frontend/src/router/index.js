import { createRouter, createWebHistory } from 'vue-router'

const routes = [
  { path: '/', redirect: '/punch' },
  { path: '/punch', name: 'punch', component: () => import('../views/PunchView.vue'), meta: { title: '打卡' } },
  { path: '/records', name: 'records', component: () => import('../views/RecordsView.vue'), meta: { title: '记录' } },
  { path: '/stats', name: 'stats', component: () => import('../views/StatsView.vue'), meta: { title: '统计' } },
  { path: '/settings', name: 'settings', component: () => import('../views/SettingsView.vue'), meta: { title: '设置' } }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

export default router
