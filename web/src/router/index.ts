import { createRouter, createWebHashHistory } from 'vue-router'
import { useAuthStore } from '../stores/auth'

const router = createRouter({
  history: createWebHashHistory(),
  routes: [
    { path: '/login', component: () => import('../views/LoginView.vue'), meta: { public: true, title: '登录' } },
    { path: '/register', component: () => import('../views/RegisterView.vue'), meta: { public: true, title: '员工注册' } },
    { path: '/workspaces', component: () => import('../views/WorkspaceSelectView.vue'), meta: { title: '选择工作台' } },
    { path: '/', redirect: '/workspaces' },
  ],
})

router.beforeEach((to) => {
  const auth = useAuthStore()
  if (to.meta.public) return auth.signedIn && ['/login', '/register'].includes(to.path) ? '/workspaces' : true
  if (!auth.signedIn) return '/login'
  return true
})

export default router
