<template>
  <div>
    <div class="page-heading"><div><p class="eyebrow">Organization / Directory</p><h1>组织与员工</h1><p class="heading-copy">查看组织层级和员工归属，作为流程与档案权限的基础。</p></div><el-button type="primary" :icon="Refresh" :loading="loading" @click="load">刷新组织</el-button></div>
    <div v-if="error" class="dashboard-error">{{ error }}</div>
    <div class="organization-layout">
      <section class="paper-panel organization-tree"><div class="panel-heading"><div><p class="eyebrow">Department tree</p><h2>部门结构</h2></div><span class="toolbar-note">{{ departments.length }} 个部门</span></div><div v-if="loading" class="empty-state">正在加载部门结构</div><div v-else-if="!departments.length" class="empty-state">暂无可用部门</div><div v-else class="tree-list"><div v-for="department in flatDepartments" :key="department.id" class="tree-row" :style="{ paddingLeft: `${18 + department.depth * 22}px` }"><span class="tree-marker"></span><div><strong>{{ department.name }}</strong><span>{{ department.code }}</span></div><em>{{ departmentEmployeeCount(department.id) }} 人</em></div></div></section>
      <section class="paper-panel directory-panel"><div class="panel-heading"><div><p class="eyebrow">People directory</p><h2>员工归属</h2></div><span class="toolbar-note">{{ employees.length }} 人</span></div><div class="directory-stats"><div><strong>{{ activeCount }}</strong><span>在职员工</span></div><div><strong>{{ departmentCount }}</strong><span>覆盖部门</span></div><div><strong>{{ unassignedCount }}</strong><span>待归属</span></div></div><div class="directory-list"><router-link v-for="employee in employees" :key="employee.id" class="directory-row" :to="{ name: 'employee-archive', params: { id: employee.id } }"><div><strong>{{ employee.name }}</strong><span>{{ employee.employeeNo }}</span></div><span class="directory-department">{{ departmentName(employee.departmentId) }}</span><el-icon><ArrowRight /></el-icon></router-link><div v-if="!employees.length" class="empty-state">暂无员工数据</div></div></section>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { ArrowRight, Refresh } from '@element-plus/icons-vue'
import { listEmployees } from '@/api/archive'
import { getDepartmentTree } from '@/api/organization'
import type { DepartmentNode, Employee } from '@/types/api'
const departments = ref<DepartmentNode[]>([]); const employees = ref<Employee[]>([]); const loading = ref(false); const error = ref('')
const activeCount = computed(() => employees.value.filter((employee) => employee.status === 'ACTIVE').length)
const unassignedCount = computed(() => employees.value.filter((employee) => !employee.departmentId).length)
const departmentCount = computed(() => new Set(employees.value.map((employee) => employee.departmentId).filter(Boolean)).size)
const flatDepartments = computed(() => { const output: Array<DepartmentNode & { depth: number }> = []; const walk = (parentId: number | null, depth: number) => { departments.value.filter((item) => item.parentId === parentId).sort((a, b) => a.sortNo - b.sortNo).forEach((item) => { output.push({ ...item, depth }); walk(item.id, depth + 1) }) }; walk(null, 0); return output })
function departmentName(id: number | null) { return departments.value.find((department) => department.id === id)?.name || '未归属部门' }
function departmentEmployeeCount(id: number) { return employees.value.filter((employee) => employee.departmentId === id).length }
async function load() { loading.value = true; error.value = ''; try { const [departmentResult, employeeResult] = await Promise.all([getDepartmentTree(), listEmployees(1, 100)]); departments.value = departmentResult; employees.value = employeeResult.items } catch (e) { error.value = e instanceof Error ? e.message : '组织数据加载失败'; ElMessage.error(error.value) } finally { loading.value = false } }
onMounted(load)
</script>
