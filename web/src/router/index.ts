import { createRouter, createWebHistory } from 'vue-router'
import { useAuthStore } from '../stores/auth'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/login', component: () => import('../views/LoginView.vue'), meta: { public: true } },
    { path: '/', redirect: '/dashboard' },
    { path: '/dashboard', component: () => import('../views/DashboardView.vue') },
    { path: '/employees', component: () => import('../views/EmployeeListView.vue') },
    { path: '/employees/:id/archive', component: () => import('../views/EmployeeArchiveView.vue') },
    { path: '/ocr/:versionId', component: () => import('../views/OcrReviewView.vue') },
    { path: '/workflows', component: () => import('../views/WorkflowView.vue') },
    { path: '/archive-access', component: () => import('../views/ArchiveAccessView.vue') },
    { path: '/inventory', component: () => import('../views/InventoryView.vue') },
    { path: '/audit-logs', component: () => import('../views/AuditLogView.vue') },
  ],
})

router.beforeEach((to) => {
  const auth = useAuthStore()
  if (to.meta.public) return auth.signedIn && to.path === '/login' ? '/dashboard' : true
  if (!auth.signedIn) return { path: '/login', query: { redirect: to.fullPath } }
  return true
})

export default router
