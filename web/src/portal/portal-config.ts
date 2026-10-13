import type { Component } from 'vue'
import {
  Bell, Briefcase, Collection, Files, Grid, List, Lock, Notebook, Setting, User, Warning,
} from '@element-plus/icons-vue'

export type PortalId = 'employee' | 'manager' | 'hr' | 'admin'

export interface PortalNavItem {
  section: string
  path: string
  label: string
  icon: Component
}

export interface PortalConfig {
  id: PortalId
  entry: string
  title: string
  roleLabel: string
  homeTitle: string
  allowedRoles: string[]
  nav: PortalNavItem[]
}

export const portalOrder: PortalId[] = ['employee', 'manager', 'hr', 'admin']

export const portalConfigs: Record<PortalId, PortalConfig> = {
  employee: {
    id: 'employee', entry: '/employee.html', title: '员工自助服务', roleLabel: '员工 / 经办人', homeTitle: '个人服务',
    allowedRoles: ['EMPLOYEE'],
    nav: [
      { section: '个人服务', path: '/', label: '个人工作台', icon: Grid },
      { section: '个人服务', path: '/todos', label: '我的待办', icon: Bell },
      { section: '个人服务', path: '/employees', label: '我的档案', icon: User },
      { section: '个人服务', path: '/workflows', label: '申请与进度', icon: List },
      { section: '个人服务', path: '/archive-access', label: '档案访问', icon: Lock },
      { section: '个人服务', path: '/contracts', label: '合同信息', icon: Files },
    ],
  },
  manager: {
    id: 'manager', entry: '/manager.html', title: '部门工作台', roleLabel: '部门负责人', homeTitle: '团队运营',
    allowedRoles: ['DEPT_MANAGER'],
    nav: [
      { section: '团队工作', path: '/', label: '团队概览', icon: Grid },
      { section: '团队工作', path: '/todos', label: '审批与待办', icon: Bell },
      { section: '团队工作', path: '/employees', label: '部门员工', icon: User },
      { section: '团队工作', path: '/workflows', label: '人事流程', icon: List },
      { section: '档案资源', path: '/archive-access', label: '档案访问', icon: Lock },
      { section: '档案资源', path: '/inventory', label: '部门盘点', icon: Warning },
      { section: '团队工作', path: '/contracts', label: '合同信息', icon: Files },
    ],
  },
  hr: {
    id: 'hr', entry: '/hr.html', title: '人事运营工作台', roleLabel: '人事管理员', homeTitle: '人事运营',
    allowedRoles: ['HR_ADMIN'],
    nav: [
      { section: '运营中心', path: '/', label: '运营总览', icon: Grid },
      { section: '运营中心', path: '/todos', label: '人事待办', icon: Bell },
      { section: '人员与档案', path: '/employees', label: '员工与档案', icon: User },
      { section: '人员与档案', path: '/workflows', label: '人事流程', icon: List },
      { section: '人员与档案', path: '/contracts', label: '合同台账', icon: Files },
      { section: '档案资源', path: '/archive-access', label: '档案授权', icon: Lock },
      { section: '档案资源', path: '/inventory', label: '盘点与异常', icon: Warning },
      { section: '系统运营', path: '/organization', label: '组织岗位', icon: Setting },
      { section: '系统运营', path: '/audit-logs', label: '审计日志', icon: Notebook },
    ],
  },
  admin: {
    id: 'admin', entry: '/admin.html', title: '系统管理控制台', roleLabel: '系统管理员', homeTitle: '平台管理',
    allowedRoles: ['SYSTEM_ADMIN'],
    nav: [
      { section: '平台管理', path: '/', label: '管理概览', icon: Grid },
      { section: '平台管理', path: '/admin/users', label: '账号与角色', icon: Briefcase },
      { section: '平台配置', path: '/organization', label: '组织结构', icon: Setting },
      { section: '平台审计', path: '/audit-logs', label: '审计日志', icon: Notebook },
    ],
  },
}

export function availablePortals(roles: string[]): PortalConfig[] {
  return portalOrder.map((id) => portalConfigs[id]).filter((portal) => portal.allowedRoles.some((role) => roles.includes(role)))
}

export function portalHref(portalId: PortalId, route = '/'): string {
  return `${portalConfigs[portalId].entry}#${route}`
}

export function authorizedReturnUrl(roles: string[]): string | null {
  const target = sessionStorage.getItem('hr.portalReturnTo')
  sessionStorage.removeItem('hr.portalReturnTo')
  if (!target) return null

  try {
    const url = new URL(target, window.location.origin)
    if (url.origin !== window.location.origin) return null
    const portal = portalOrder.map((id) => portalConfigs[id]).find((item) => item.entry === url.pathname)
    return portal && portal.allowedRoles.some((role) => roles.includes(role)) ? url.href : null
  } catch {
    return null
  }
}
