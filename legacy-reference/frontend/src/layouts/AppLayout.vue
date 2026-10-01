<template>
  <div class="shell">
    <aside class="sidebar">
      <router-link class="brand" to="/app">
        <span class="brand-mark">档</span>
        <span>
          <span class="brand-title">档案工作台</span>
          <span class="brand-subtitle">HR ARCHIVE OPERATIONS</span>
        </span>
      </router-link>
      <nav>
        <div class="nav-label">工作区</div>
        <div class="nav-list">
          <router-link class="nav-item nav-home" to="/app">
            <el-icon><HomeFilled /></el-icon><span>概览</span>
          </router-link>
          <router-link class="nav-item" to="/app/todos">
            <el-icon><List /></el-icon><span>待办中心</span><span v-if="todoCount !== null" class="nav-count">{{ todoCount }}</span>
          </router-link>
          <router-link class="nav-item" to="/app/employees">
            <el-icon><User /></el-icon><span>员工档案</span>
          </router-link>
        </div>
        <div class="nav-label nav-label-spaced">业务流程</div>
        <div class="nav-list">
          <router-link class="nav-item" to="/app/organization"><el-icon><UserFilled /></el-icon><span>组织与员工</span></router-link>
          <router-link class="nav-item" to="/app/workflow"><el-icon><List /></el-icon><span>人事流程</span></router-link>
          <router-link class="nav-item" to="/app/archive-access"><el-icon><Key /></el-icon><span>档案授权</span></router-link>
          <router-link class="nav-item" to="/app/inventory"><el-icon><Finished /></el-icon><span>盘点与异常</span></router-link>
        </div>
        <div class="nav-label nav-label-spaced">资源</div>
        <div class="nav-list">
          <router-link class="nav-item" to="/app/employees"><el-icon><Collection /></el-icon><span>材料与版本</span></router-link>
        </div>
      </nav>
      <div class="sidebar-foot">本地 OCR 引擎<br />证据优先 · 全程留痕</div>
    </aside>
    <section class="content">
      <header class="topbar">
        <span class="breadcrumb">人事管理 / {{ route.meta.label || '工作台' }}</span>
        <div class="user-menu">
          <span class="user-name">{{ auth.user?.displayName || auth.user?.username || '未登录' }}</span>
          <span class="user-avatar">{{ initials }}</span>
          <el-button text :icon="SwitchButton" aria-label="退出登录" title="退出登录" @click="logout" />
        </div>
      </header>
      <main class="main"><router-view /></main>
    </section>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Collection, Finished, HomeFilled, Key, List, SwitchButton, User, UserFilled } from '@element-plus/icons-vue'
import { getTodos } from '@/api/todos'
import { useAuthStore } from '@/stores/auth'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()
const initials = computed(() => (auth.user?.displayName || auth.user?.username || '档').slice(0, 1))
const todoCount = ref<number | null>(null)

function logout() {
  auth.logout()
  router.replace({ name: 'login' })
}

onMounted(async () => {
  try {
    todoCount.value = (await getTodos(100)).total
  } catch {
    todoCount.value = null
  }
})
</script>
