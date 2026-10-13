<script setup lang="ts">
import { computed, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '../stores/auth'
import { Bell, Briefcase, Files, Grid, List, Lock, Menu, Notebook, Search, Setting, SwitchButton, User, Warning } from '@element-plus/icons-vue'

const auth = useAuthStore()
const route = useRoute()
const router = useRouter()
const mobileMenuOpen = ref(false)
const nav = [
  { section: '工作台', path: '/dashboard', label: '业务总览', icon: Grid, roles: [] },
  { section: '工作台', path: '/todos', label: '待办中心', icon: Bell, roles: [] },
  { section: '员工与流程', path: '/employees', label: '员工与档案', icon: User, roles: [] },
  { section: '员工与流程', path: '/workflows', label: '人事流程', icon: List, roles: [] },
  { section: '员工与流程', path: '/contracts', label: '合同台账', icon: Files, roles: [] },
  { section: '档案资源', path: '/archive-access', label: '档案授权', icon: Lock, roles: [] },
  { section: '档案资源', path: '/inventory', label: '盘点异常', icon: Warning, roles: ['DEPT_MANAGER', 'HR_ADMIN', 'SYSTEM_ADMIN'] },
  { section: '系统运营', path: '/organization', label: '组织岗位', icon: Setting, roles: ['HR_ADMIN', 'SYSTEM_ADMIN'] },
  { section: '系统运营', path: '/audit-logs', label: '审计日志', icon: Notebook, roles: ['HR_ADMIN', 'SYSTEM_ADMIN'] },
  { section: '系统运营', path: '/admin/users', label: '账号角色', icon: Briefcase, roles: ['SYSTEM_ADMIN'] },
]
const visibleNav = computed(() => nav.filter((item) => auth.canAccess(item.roles)))
const mobilePrimaryPaths = ['/dashboard', '/todos', '/employees', '/workflows', '/archive-access']
const mobilePrimaryNav = computed(() => visibleNav.value.filter((item) => mobilePrimaryPaths.includes(item.path)))
const mobileMoreNav = computed(() => visibleNav.value.filter((item) => !mobilePrimaryPaths.includes(item.path)))
const roleLabel = computed(() => {
  if (auth.roles.includes('SYSTEM_ADMIN')) return '系统管理员'
  if (auth.roles.includes('HR_ADMIN')) return '人事管理员'
  if (auth.roles.includes('DEPT_MANAGER')) return '部门负责人'
  return '员工/经办人'
})
const activePath = computed(() => [...visibleNav.value]
  .sort((left, right) => right.path.length - left.path.length)
  .find((item) => route.path === item.path || route.path.startsWith(`${item.path}/`))?.path
  || (route.path.startsWith('/ocr/') ? '/employees' : undefined))
function logout() { auth.logout(); router.push('/login') }
</script>

<template>
  <div class="console-shell">
    <aside class="side-rail">
      <div class="brand-lockup"><img class="brand-logo-image" src="/brand-logo.png" alt="" /><div><strong>档案工作台</strong><small>ENTERPRISE HR</small></div></div>
      <nav class="primary-nav" aria-label="主导航">
        <template v-for="(item, index) in visibleNav" :key="item.path">
          <p v-if="index === 0 || item.section !== visibleNav[index - 1]?.section" class="nav-section">{{ item.section }}</p>
          <RouterLink :to="item.path" :title="item.label" :class="{ active: activePath === item.path }">
            <el-icon><component :is="item.icon" /></el-icon><span>{{ item.label }}</span>
          </RouterLink>
        </template>
      </nav>
      <nav class="mobile-nav" aria-label="移动端主导航">
        <RouterLink v-for="item in mobilePrimaryNav" :key="item.path" :to="item.path" :title="item.label" :class="{ active: activePath === item.path }">
          <el-icon><component :is="item.icon" /></el-icon><span>{{ item.label }}</span>
        </RouterLink>
        <button v-if="mobileMoreNav.length" type="button" class="mobile-more" aria-label="更多模块" @click="mobileMenuOpen = true">
          <el-icon><Menu /></el-icon><span>更多</span>
        </button>
      </nav>
      <div class="rail-note"><span class="pulse-dot" />工作台在线<small>数据范围 · {{ auth.user?.dataScope || 'NONE' }}</small></div>
    </aside>
    <el-drawer v-model="mobileMenuOpen" title="更多模块" direction="btt" size="auto" class="mobile-navigation-drawer">
      <nav class="mobile-module-grid" aria-label="更多模块">
        <RouterLink v-for="item in mobileMoreNav" :key="item.path" :to="item.path" :class="{ active: activePath === item.path }" @click="mobileMenuOpen = false">
          <el-icon><component :is="item.icon" /></el-icon><span>{{ item.label }}</span>
        </RouterLink>
      </nav>
    </el-drawer>
    <section class="console-main">
      <header class="topbar"><div class="topbar-context"><span class="crumb">企业人事</span><strong>{{ route.meta.title || '业务总览' }}</strong><span class="topbar-caption">脱敏演示环境</span></div><div class="global-search" aria-label="快速检索"><el-icon><Search /></el-icon><span>搜索员工、档案或申请</span><kbd>⌘ K</kbd></div><div class="topbar-actions"><span class="sync-status"><i />同步正常</span><span class="role-chip"><el-icon><User /></el-icon>{{ roleLabel }}</span><span class="user-name">{{ auth.user?.username }}</span><span class="user-avatar" aria-hidden="true">{{ auth.user?.username?.slice(0, 1).toUpperCase() || 'U' }}</span><el-button text circle aria-label="退出登录" title="退出登录" @click="logout"><el-icon><SwitchButton /></el-icon></el-button></div></header>
      <main class="workspace"><RouterView /></main>
    </section>
  </div>
</template>
