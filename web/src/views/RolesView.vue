<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Check, Close, Refresh } from '@element-plus/icons-vue'
import { request, type RolePermissionMatrix } from '../api/http'

const loading = ref(false)
const data = ref<RolePermissionMatrix>({ roles: [], modules: [], matrix: {} })

const roleLabels: Record<string, string> = {
  SYSTEM_ADMIN: '系统管理员',
  HR_ADMIN: '人事管理员',
  HR_MANAGER: '人事经理',
  DEPT_MANAGER: '部门负责人',
  EMPLOYEE: '普通员工',
}

function roleLabel(code: string): string {
  return roleLabels[code] || code
}

function permitted(role: string, module: string): boolean {
  return Boolean(data.value.matrix?.[role]?.[module])
}

async function load() {
  loading.value = true
  try {
    data.value = await request<RolePermissionMatrix>({ url: '/admin/roles' })
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '角色权限加载失败')
  } finally {
    loading.value = false
  }
}

onMounted(load)
</script>

<template>
  <div class="page-head">
    <div>
      <span class="section-kicker">RBAC / ROLE MATRIX</span>
      <h1>角色权限矩阵</h1>
      <p>只读展示固定角色与功能模块的访问关系；权限由后端注解统一控制，当前不支持在线编辑（ADR-008）。</p>
    </div>
    <el-button class="quiet-button" :loading="loading" @click="load"><el-icon><Refresh /></el-icon>刷新</el-button>
  </div>

  <section class="surface-panel table-panel">
    <el-table v-loading="loading" :data="data.roles.map((role) => ({ role }))" class="data-table" empty-text="暂无角色数据">
      <el-table-column label="角色" min-width="180" fixed>
        <template #default="scope">
          <strong>{{ roleLabel(scope.row.role) }}</strong>
          <code class="role-code">{{ scope.row.role }}</code>
        </template>
      </el-table-column>
      <el-table-column v-for="module in data.modules" :key="module" :label="module" align="center" min-width="110">
        <template #default="scope">
          <el-icon v-if="permitted(scope.row.role, module)" class="cell-check"><Check /></el-icon>
          <el-icon v-else class="cell-cross"><Close /></el-icon>
        </template>
      </el-table-column>
    </el-table>
    <div class="table-footer">
      <span>共 {{ data.roles.length }} 个角色 · {{ data.modules.length }} 个模块</span>
      <span class="legend"><el-icon class="cell-check"><Check /></el-icon> 可访问　<el-icon class="cell-cross"><Close /></el-icon> 不可访问</span>
    </div>
  </section>
</template>

<style scoped>
.role-code { margin-left: 8px; color: #909399; font-size: 10px; font-weight: 400; }
.cell-check { color: #2f9e6e; font-size: 15px; }
.cell-cross { color: #c2c8cf; font-size: 14px; }
.legend { color: var(--muted); font-size: 10px; display: inline-flex; align-items: center; gap: 4px; }
</style>
