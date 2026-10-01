<script setup lang="ts">
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '../stores/auth'
import { Document, Grid, Files, Histogram, List, Lock, Notebook, SwitchButton, User, Warning } from '@element-plus/icons-vue'

const auth = useAuthStore()
const route = useRoute()
const router = useRouter()
const nav = [
  { path: '/dashboard', label: '工作台', icon: Grid, roles: [] },
  { path: '/employees', label: '员工与档案', icon: User, roles: [] },
  { path: '/workflows', label: '人事流程', icon: List, roles: ['DEPT_MANAGER', 'HR_ADMIN', 'SYSTEM_ADMIN'] },
  { path: '/archive-access', label: '档案授权', icon: Lock, roles: [] },
  { path: '/inventory', label: '盘点异常', icon: Warning, roles: ['HR_ADMIN', 'SYSTEM_ADMIN'] },
  { path: '/audit-logs', label: '审计日志', icon: Notebook, roles: ['HR_ADMIN', 'SYSTEM_ADMIN'] },
]
const visibleNav = computed(() => nav.filter((item) => auth.canAccess(item.roles)))
const roleLabel = computed(() => auth.roles[0] === 'SYSTEM_ADMIN' ? '系统管理员' : auth.roles[0] === 'HR_ADMIN' ? '人事管理员' : auth.roles[0] === 'DEPT_MANAGER' ? '部门负责人' : '员工/经办人')
function logout() { auth.logout(); router.push('/login') }
</script>

<template>
  <div class="console-shell">
    <aside class="side-rail">
      <div class="brand-lockup"><span class="brand-mark">档</span><div><strong>档案中枢</strong><small>HR OPERATIONS</small></div></div>
      <nav class="primary-nav" aria-label="主导航">
        <RouterLink v-for="item in visibleNav" :key="item.path" :to="item.path" :class="{ active: route.path === item.path || route.path.startsWith(`${item.path}/`) }">
          <el-icon><component :is="item.icon" /></el-icon><span>{{ item.label }}</span>
        </RouterLink>
      </nav>
      <div class="rail-note"><span class="pulse-dot" />系统运行正常<small>数据范围：{{ auth.user?.dataScope || 'NONE' }}</small></div>
    </aside>
    <section class="console-main">
      <header class="topbar"><div><span class="crumb">企业人事管理 /</span><strong>{{ route.meta.title || '业务总览' }}</strong></div><div class="topbar-actions"><span class="role-chip"><el-icon><User /></el-icon>{{ roleLabel }}</span><span class="user-name">{{ auth.user?.username }}</span><el-button text circle aria-label="退出登录" title="退出登录" @click="logout"><el-icon><SwitchButton /></el-icon></el-button></div></header>
      <main class="workspace"><RouterView /></main>
    </section>
  </div>
</template>
