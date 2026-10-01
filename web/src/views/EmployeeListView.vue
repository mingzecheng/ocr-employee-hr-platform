<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Search, Plus, Refresh, FolderOpened } from '@element-plus/icons-vue'
import { request, type Employee, type Page } from '../api/http'

const loading = ref(false)
const filters = reactive({ keyword: '', status: '', departmentId: '' })
const page = ref(1)
const pageSize = ref(10)
const data = ref<Page<Employee>>({ items: [], total: 0, page: 1, pageSize: 10 })
const statusLabel: Record<string, string> = { ACTIVE: '在职', PENDING_ONBOARD: '待入职', ON_LEAVE: '休假', INACTIVE: '离职' }
async function load() { loading.value = true; try { data.value = await request<Page<Employee>>({ url: '/employees', params: { ...filters, page: page.value, pageSize: pageSize.value, departmentId: filters.departmentId || undefined, status: filters.status || undefined } }) } catch (e) { ElMessage.error(e instanceof Error ? e.message : '员工列表加载失败') } finally { loading.value = false } }
function search() { page.value = 1; load() }
onMounted(load)
</script>

<template>
  <div class="page-head"><div><span class="section-kicker">PEOPLE / DIRECTORY</span><h1>员工与档案</h1><p>按员工编号、姓名和状态检索可访问的人员主数据。</p></div><el-button type="primary" class="action-button"><el-icon><Plus /></el-icon>新建员工</el-button></div>
  <section class="surface-panel table-panel"><div class="filter-row"><el-input v-model="filters.keyword" clearable placeholder="搜索员工编号或姓名" class="search-input" @keyup.enter="search"><template #prefix><el-icon><Search /></el-icon></template></el-input><el-select v-model="filters.status" clearable placeholder="员工状态" class="compact-select"><el-option v-for="(label, value) in statusLabel" :key="value" :label="label" :value="value" /></el-select><el-input v-model="filters.departmentId" clearable placeholder="部门 ID" class="compact-input" /><el-button type="primary" @click="search">查询</el-button><el-button text @click="filters.keyword = ''; filters.status = ''; filters.departmentId = ''; search()"><el-icon><Refresh /></el-icon>重置</el-button><span class="filter-total">共 {{ data.total }} 人</span></div><el-table v-loading="loading" :data="data.items" row-key="id" class="data-table" empty-text="当前范围暂无员工数据"><el-table-column prop="employeeNo" label="员工编号" width="170"><template #default="scope"><code class="employee-code">{{ scope.row.employeeNo }}</code></template></el-table-column><el-table-column prop="name" label="姓名" min-width="150"><template #default="scope"><strong>{{ scope.row.name }}</strong></template></el-table-column><el-table-column prop="departmentId" label="部门" width="120"><template #default="scope">部门 {{ scope.row.departmentId }}</template></el-table-column><el-table-column prop="positionId" label="岗位" width="120"><template #default="scope">岗位 {{ scope.row.positionId }}</template></el-table-column><el-table-column prop="status" label="状态" width="110"><template #default="scope"><el-tag :type="scope.row.status === 'ACTIVE' ? 'success' : scope.row.status === 'INACTIVE' ? 'info' : 'warning'" effect="plain">{{ statusLabel[scope.row.status] || scope.row.status }}</el-tag></template></el-table-column><el-table-column prop="hireDate" label="入职日期" width="130" /><el-table-column label="操作" width="130" fixed="right"><template #default="scope"><el-button link type="primary" @click="$router.push(`/employees/${scope.row.id}/archive`)"><el-icon><FolderOpened /></el-icon>档案</el-button></template></el-table-column></el-table><div class="table-footer"><span>第 {{ page }} 页</span><el-pagination v-model:current-page="page" v-model:page-size="pageSize" layout="prev, pager, next" :total="data.total" @current-change="load" @size-change="load" /></div></section>
</template>
