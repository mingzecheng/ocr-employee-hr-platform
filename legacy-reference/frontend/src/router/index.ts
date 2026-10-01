import { createRouter, createWebHistory } from 'vue-router'
import { useAuthStore } from '@/stores/auth'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/', redirect: '/app' },
    { path: '/login', name: 'login', component: () => import('@/views/LoginView.vue') },
    {
      path: '/app',
      component: () => import('@/layouts/AppLayout.vue'),
      children: [
        { path: '', name: 'dashboard', component: () => import('@/views/DashboardView.vue'), meta: { label: '概览' } },
        { path: 'todos', name: 'todos', component: () => import('@/views/TodoView.vue'), meta: { label: '待办中心' } },
        { path: 'employees', name: 'employees', component: () => import('@/views/EmployeeListView.vue'), meta: { label: '员工档案' } },
        { path: 'employees/:id', name: 'employee-archive', component: () => import('@/views/EmployeeArchiveView.vue'), meta: { label: '员工档案 / 证据' } },
        { path: 'organization', name: 'organization', component: () => import('@/views/OrganizationView.vue'), meta: { label: '组织与员工' } },
        { path: 'workflow', name: 'workflow', component: () => import('@/views/WorkflowView.vue'), meta: { label: '人事流程' } },
        { path: 'archive-access', name: 'archive-access', component: () => import('@/views/ArchiveAccessView.vue'), meta: { label: '档案授权' } },
        { path: 'inventory', name: 'inventory', component: () => import('@/views/InventoryView.vue'), meta: { label: '盘点与异常' } },
      ],
    },
  ],
})

router.beforeEach((to) => {
  const auth = useAuthStore()
  if (to.name !== 'login' && !auth.isAuthenticated) return { name: 'login' }
  if (to.name === 'login' && auth.isAuthenticated) return { name: 'dashboard' }
  return true
})

export default router
