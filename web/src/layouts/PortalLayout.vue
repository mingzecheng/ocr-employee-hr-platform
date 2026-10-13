<script setup lang="ts">
import { computed, ref } from 'vue'
import { useRoute } from 'vue-router'
import { ArrowRight, Menu, Search, SwitchButton, User } from '@element-plus/icons-vue'
import { availablePortals, portalConfigs, type PortalId } from '../portal/portal-config'
import { useAuthStore } from '../stores/auth'

const props = defineProps<{ portal: PortalId }>()
const auth = useAuthStore()
const route = useRoute()
const mobileMenuOpen = ref(false)
const config = computed(() => portalConfigs[props.portal])
const available = computed(() => availablePortals(auth.roles))
const mobilePaths: Record<PortalId, string[]> = {
  employee: ['/', '/todos', '/employees', '/workflows', '/archive-access'],
  manager: ['/', '/todos', '/employees', '/workflows', '/archive-access'],
  hr: ['/', '/todos', '/employees', '/workflows', '/archive-access'],
  admin: ['/', '/admin/users', '/organization', '/audit-logs'],
}
const mobilePrimaryNav = computed(() => config.value.nav.filter((item) => mobilePaths[props.portal].includes(item.path)))
const mobileMoreNav = computed(() => config.value.nav.filter((item) => !mobilePaths[props.portal].includes(item.path)))
const activePath = computed(() => [...config.value.nav]
  .sort((left, right) => right.path.length - left.path.length)
  .find((item) => route.path === item.path || (item.path !== '/' && route.path.startsWith(`${item.path}/`)))?.path)

function openWorkspaces() { window.location.assign('/#/workspaces') }
function logout() {
  auth.logout()
  window.location.assign('/#/login')
}
</script>

<template>
  <div class="console-shell portal-shell" :class="`portal-${portal}`">
    <aside class="side-rail">
      <div class="brand-lockup"><img class="brand-logo-image" src="/brand-logo.png" alt="" /><div><strong>{{ config.title }}</strong><small>ENTERPRISE HR</small></div></div>
      <nav class="primary-nav" aria-label="主导航">
        <template v-for="(item, index) in config.nav" :key="item.path">
          <p v-if="index === 0 || item.section !== config.nav[index - 1]?.section" class="nav-section">{{ item.section }}</p>
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
      <header class="topbar">
        <div class="topbar-context"><span class="crumb">{{ config.title }}</span><strong>{{ route.meta.title || config.homeTitle }}</strong></div>
        <div class="global-search" aria-label="快速检索"><el-icon><Search /></el-icon><span>搜索当前业务数据</span><kbd>⌘ K</kbd></div>
        <div class="topbar-actions">
          <span class="role-chip"><el-icon><User /></el-icon>{{ config.roleLabel }}</span>
          <span class="user-name">{{ auth.user?.username }}</span>
          <span class="user-avatar" aria-hidden="true">{{ auth.user?.username?.slice(0, 1).toUpperCase() || 'U' }}</span>
          <el-button v-if="available.length > 1" text class="portal-switch" @click="openWorkspaces">切换工作台</el-button>
          <el-button text circle aria-label="退出登录" title="退出登录" @click="logout"><el-icon><SwitchButton /></el-icon></el-button>
        </div>
      </header>
      <main class="workspace"><RouterView /></main>
    </section>
  </div>
</template>
