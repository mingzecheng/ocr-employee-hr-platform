<script setup lang="ts">
import { computed } from 'vue'
import { ArrowRight, Briefcase, Grid, User } from '@element-plus/icons-vue'
import { useAuthStore } from '../stores/auth'
import { availablePortals, portalHref, type PortalId } from '../portal/portal-config'

const auth = useAuthStore()
const portals = computed(() => availablePortals(auth.roles))
const iconByPortal: Record<PortalId, typeof Grid> = {
  employee: User,
  manager: Briefcase,
  hr: Grid,
  admin: Briefcase,
}
</script>

<template>
  <main class="workspace-select-screen">
    <header class="workspace-select-topbar">
      <div class="login-brand"><img class="brand-logo-image" src="/brand-logo.png" alt="" /><div><strong>档案工作台</strong><small>企业员工档案与人事流程管理平台</small></div></div>
      <el-button text @click="auth.logout(); $router.replace('/login')">退出登录</el-button>
    </header>
    <section class="workspace-select-content">
      <div class="workspace-select-heading"><span class="section-kicker">APPLICATIONS</span><h1>选择工作台</h1><p>当前账号可访问以下应用端。</p></div>
      <div v-if="portals.length" class="portal-choice-list">
        <a v-for="portal in portals" :key="portal.id" class="portal-choice" :href="portalHref(portal.id)">
          <span class="portal-choice-icon"><el-icon><component :is="iconByPortal[portal.id]" /></el-icon></span>
          <span class="portal-choice-copy"><strong>{{ portal.title }}</strong><small>{{ portal.roleLabel }}</small></span>
          <el-icon class="portal-choice-arrow"><ArrowRight /></el-icon>
        </a>
      </div>
      <el-empty v-else description="当前账号尚未分配可用工作台，请联系系统管理员" />
    </section>
    <footer class="workspace-select-footer">已登录为 {{ auth.user?.username }}</footer>
  </main>
</template>
