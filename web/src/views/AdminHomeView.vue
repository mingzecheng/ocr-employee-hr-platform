<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ArrowRight, Briefcase, Notebook, Refresh, Setting, User } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { request, type AdminUser, type DepartmentNode, type Page, type StatisticsOverview } from '../api/http'

const loading = ref(false)
const stats = ref<StatisticsOverview | null>(null)
const users = ref<Page<AdminUser> | null>(null)
const departments = ref<DepartmentNode[] | null>(null)
const departmentCount = computed(() => {
  let count = 0
  const visit = (nodes: DepartmentNode[]) => nodes.forEach((node) => { count += 1; visit(node.children || []) })
  visit(departments.value || [])
  return count
})
async function load() {
  loading.value = true
  const results = await Promise.allSettled([
    request<StatisticsOverview>({ url: '/statistics/overview' }),
    request<Page<AdminUser>>({ url: '/admin/users', params: { page: 1, pageSize: 1 } }),
    request<DepartmentNode[]>({ url: '/departments/tree' }),
  ])
  if (results[0].status === 'fulfilled') stats.value = results[0].value
  if (results[1].status === 'fulfilled') users.value = results[1].value
  if (results[2].status === 'fulfilled') departments.value = results[2].value
  if (results.every((result) => result.status === 'rejected')) ElMessage.error('平台管理数据暂时无法加载，请稍后重试')
  loading.value = false
}
onMounted(load)
</script>

<template>
  <div class="page-head"><div><span class="section-kicker">SYSTEM / ADMINISTRATION</span><h1>平台管理</h1><p>管理账号、组织配置与平台运行审计。</p></div><el-button class="quiet-button" :loading="loading" @click="load"><el-icon><Refresh /></el-icon>刷新</el-button></div>
  <section class="portal-metric-grid" v-loading="loading">
    <article><span>账号总数</span><strong>{{ users?.total ?? '—' }}</strong><small>含停用账号</small><el-icon><User /></el-icon></article>
    <article><span>组织节点</span><strong>{{ departments === null ? '—' : departmentCount }}</strong><small>部门与子部门</small><el-icon><Setting /></el-icon></article>
    <article><span>员工总量</span><strong>{{ stats?.employeeCount ?? '—' }}</strong><small>仅聚合统计</small><el-icon><Briefcase /></el-icon></article>
    <article><span>档案总量</span><strong>{{ stats?.archiveCount ?? '—' }}</strong><small>不展示档案内容</small><el-icon><Notebook /></el-icon></article>
  </section>
  <section class="portal-admin-links">
    <RouterLink to="/admin/users"><span class="portal-admin-icon"><el-icon><User /></el-icon></span><span><strong>账号与角色</strong><small>账号状态、员工绑定和工作台角色</small></span><el-icon class="shortcut-arrow"><ArrowRight /></el-icon></RouterLink>
    <RouterLink to="/organization"><span class="portal-admin-icon"><el-icon><Setting /></el-icon></span><span><strong>组织结构</strong><small>部门节点与岗位配置</small></span><el-icon class="shortcut-arrow"><ArrowRight /></el-icon></RouterLink>
    <RouterLink to="/audit-logs"><span class="portal-admin-icon"><el-icon><Notebook /></el-icon></span><span><strong>平台审计</strong><small>检索关键操作和异常记录</small></span><el-icon class="shortcut-arrow"><ArrowRight /></el-icon></RouterLink>
  </section>
</template>
