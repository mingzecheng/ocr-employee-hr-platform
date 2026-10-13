import { createRouter, createWebHashHistory, type RouteRecordRaw } from 'vue-router'
import { useAuthStore } from '../stores/auth'
import type { PortalId } from './portal-config'
import { portalConfigs } from './portal-config'

const employeeRoles = ['EMPLOYEE']
const managerRoles = ['DEPT_MANAGER']
const hrRoles = ['HR_ADMIN']
const organizationRoles = ['HR_ADMIN', 'SYSTEM_ADMIN']
const adminRoles = ['SYSTEM_ADMIN']

function commonRoutes(roles: string[], ocrRoles?: string[]): RouteRecordRaw[] {
  const routes: RouteRecordRaw[] = [
    { path: '/todos', component: () => import('../views/TodoView.vue'), meta: { title: '待办中心', roles } },
    { path: '/todos/:kind/:id', component: () => import('../views/TodoDetailView.vue'), meta: { title: '待办详情', roles } },
    { path: '/employees', component: () => import('../views/EmployeeListView.vue'), meta: { title: '员工与档案', roles } },
    { path: '/employees/:id/archive', component: () => import('../views/EmployeeArchiveView.vue'), meta: { title: '档案详情', roles } },
    { path: '/workflows', component: () => import('../views/WorkflowView.vue'), meta: { title: '人事流程', roles } },
    { path: '/contracts', component: () => import('../views/ContractsView.vue'), meta: { title: '合同台账', roles } },
    { path: '/archive-access', component: () => import('../views/ArchiveAccessView.vue'), meta: { title: '档案访问', roles } },
  ]
  if (ocrRoles) {
    routes.push(
      { path: '/todos/ocr/:versionId', component: () => import('../views/OcrReviewView.vue'), meta: { title: 'OCR 识别结果', roles: ocrRoles } },
      { path: '/ocr/:versionId', component: () => import('../views/OcrReviewView.vue'), meta: { title: 'OCR 识别结果', roles: ocrRoles } },
    )
  }
  return routes
}

const portalRoutes: Record<PortalId, RouteRecordRaw[]> = {
  employee: [
    { path: '/', component: () => import('../views/EmployeeHomeView.vue'), meta: { title: '个人工作台', roles: employeeRoles } },
    ...commonRoutes(employeeRoles, employeeRoles),
  ],
  manager: [
    { path: '/', component: () => import('../views/ManagerHomeView.vue'), meta: { title: '团队概览', roles: managerRoles } },
    ...commonRoutes(managerRoles, managerRoles),
    { path: '/inventory', component: () => import('../views/InventoryView.vue'), meta: { title: '部门盘点', roles: managerRoles } },
  ],
  hr: [
    { path: '/', component: () => import('../views/HrHomeView.vue'), meta: { title: '人事运营总览', roles: hrRoles } },
    ...commonRoutes(hrRoles, hrRoles),
    { path: '/inventory', component: () => import('../views/InventoryView.vue'), meta: { title: '盘点与异常', roles: hrRoles } },
    { path: '/organization', component: () => import('../views/OrganizationAdminView.vue'), meta: { title: '组织岗位', roles: hrRoles } },
    { path: '/audit-logs', component: () => import('../views/AuditLogView.vue'), meta: { title: '审计日志', roles: hrRoles } },
  ],
  admin: [
    { path: '/', component: () => import('../views/AdminHomeView.vue'), meta: { title: '平台管理概览', roles: adminRoles } },
    { path: '/admin/users', component: () => import('../views/AdminUsersView.vue'), meta: { title: '账号与角色', roles: adminRoles } },
    { path: '/admin/monitor', component: () => import('../views/ServerMonitorView.vue'), meta: { title: '服务器监控', roles: adminRoles } },
    { path: '/admin/online-users', component: () => import('../views/OnlineUsersView.vue'), meta: { title: '在线用户', roles: adminRoles } },
    { path: '/admin/roles', component: () => import('../views/RolesView.vue'), meta: { title: '角色权限矩阵', roles: adminRoles } },
    { path: '/organization', component: () => import('../views/OrganizationAdminView.vue'), meta: { title: '组织结构', roles: organizationRoles } },
    { path: '/audit-logs', component: () => import('../views/AuditLogView.vue'), meta: { title: '审计日志', roles: adminRoles } },
    { path: '/admin/login-logs', component: () => import('../views/LoginLogsView.vue'), meta: { title: '登录日志', roles: adminRoles } },
  ],
}

export function createPortalRouter(portalId: PortalId) {
  const portal = portalConfigs[portalId]
  const routes: RouteRecordRaw[] = [
    ...portalRoutes[portalId],
    { path: '/forbidden', component: () => import('../views/PortalForbiddenView.vue'), meta: { public: true, title: '无权访问' } },
    { path: '/:pathMatch(.*)*', redirect: '/forbidden' },
  ]
  const router = createRouter({ history: createWebHashHistory(), routes })

  router.beforeEach((to) => {
    const auth = useAuthStore()
    document.title = `${String(to.meta.title || portal.homeTitle)} · ${portal.title}`

    if (!auth.signedIn) {
      sessionStorage.setItem('hr.portalReturnTo', window.location.href)
      window.location.assign('/#/login')
      return false
    }
    if (to.meta.public) return true
    if (!portal.allowedRoles.some((role) => auth.roles.includes(role))) return { name: 'forbidden' }
    const roles = to.meta.roles as string[] | undefined
    if (roles && !auth.canAccess(roles)) return { name: 'forbidden' }
    return true
  })

  return router
}
