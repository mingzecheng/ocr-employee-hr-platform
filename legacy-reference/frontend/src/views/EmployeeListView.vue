<template>
  <div>
    <div class="page-heading">
      <div>
        <p class="eyebrow">Archive / People</p>
        <h1>员工档案</h1>
        <p class="heading-copy">按员工进入材料、识别结果和原始影像。</p>
      </div>
      <div class="heading-actions">
        <el-button text :icon="Refresh" :loading="loading" @click="load">刷新列表</el-button>
        <el-button type="primary" :icon="Plus" @click="openCreateForm">新建员工</el-button>
      </div>
    </div>

    <section v-if="showCreateForm" class="paper-panel employee-create-panel">
      <div class="panel-heading">
        <div><p class="eyebrow">New profile</p><h2>建立员工档案</h2></div>
        <span class="panel-index">PROFILE</span>
      </div>
      <form data-test="employee-create-form" class="operation-form" @submit.prevent="submitCreate">
        <div class="form-grid two-columns">
          <label class="form-field"><span>员工编号</span><input v-model.trim="createForm.employeeNo" data-test="employee-no" required maxlength="64" placeholder="例如 E-2026-001" /></label>
          <label class="form-field"><span>姓名</span><input v-model.trim="createForm.name" data-test="employee-name" required maxlength="128" placeholder="员工姓名" /></label>
        </div>
        <div class="form-grid two-columns">
          <label class="form-field"><span>身份证号（可选）</span><input v-model.trim="createForm.idCardNo" data-test="employee-id-card" maxlength="64" placeholder="仅在业务需要时填写" /></label>
          <label class="form-field"><span>部门 ID（可选）</span><input v-model="createForm.departmentId" data-test="employee-department" type="number" min="1" placeholder="部门负责人可后续维护" /></label>
        </div>
        <div v-if="createError" class="inline-error">{{ createError }}</div>
        <div class="form-actions">
          <el-button type="primary" native-type="submit" :loading="creating" :icon="Check">保存员工</el-button>
          <el-button text type="info" @click="closeCreateForm">取消</el-button>
        </div>
      </form>
    </section>

    <div v-if="loadError" class="dashboard-error todo-page-error">
      <span>{{ loadError }}</span>
      <el-button text :icon="Refresh" @click="load">重新加载</el-button>
    </div>

    <div class="toolbar employee-toolbar">
      <div class="toolbar-summary">
        <span class="toolbar-note" data-test="employee-result-count">本页 {{ employees.length }} 条 · 共 {{ total }} 条</span>
        <span class="toolbar-note" v-if="keyword || statusFilter !== 'ALL'">已应用筛选</span>
      </div>
      <div class="employee-filters">
        <el-input v-model="keyword" clearable placeholder="姓名或员工编号，回车搜索" style="max-width: 260px" @keyup.enter="applyFilters" />
        <el-button text :icon="Search" aria-label="搜索员工" title="搜索员工" @click="applyFilters" />
        <label class="filter-select">
          <span>状态</span>
          <select v-model="statusFilter" data-test="employee-status-filter" aria-label="按状态筛选">
            <option value="ALL">全部</option>
            <option value="ACTIVE">在职</option>
            <option value="INACTIVE">离职</option>
          </select>
        </label>
      </div>
    </div>

    <section class="paper-panel table-panel">
      <el-table v-loading="loading" :data="employees" row-key="id" class="employee-table">
        <el-table-column prop="employeeNo" label="员工编号" width="160" />
        <el-table-column label="姓名" min-width="160"><template #default="scope"><router-link class="employee-link" :to="{ name: 'employee-archive', params: { id: scope.row.id } }">{{ scope.row.name }}</router-link></template></el-table-column>
        <el-table-column prop="idCardNo" label="身份证号" min-width="180" class-name="mobile-hide" label-class-name="mobile-hide" />
        <el-table-column prop="departmentId" label="部门 ID" width="120" class-name="mobile-hide" label-class-name="mobile-hide" />
        <el-table-column label="状态" width="120"><template #default="scope"><span class="status-dot" :class="scope.row.status === 'ACTIVE' ? 'status-active' : 'status-inactive'">{{ scope.row.status === 'ACTIVE' ? '在职' : '离职' }}</span></template></el-table-column>
        <el-table-column label="操作" width="120" class-name="mobile-hide" label-class-name="mobile-hide"><template #default="scope"><el-button text :icon="ArrowRight" aria-label="查看档案" title="查看档案" @click="router.push({ name: 'employee-archive', params: { id: scope.row.id } })" /></template></el-table-column>
        <template #empty><div class="empty-state">没有匹配的员工记录</div></template>
      </el-table>
    </section>

    <div class="pagination-bar" aria-label="员工列表分页">
      <el-button text :icon="ArrowLeft" :disabled="currentPage === 1 || loading" aria-label="上一页" title="上一页" @click="goToPage(currentPage - 1)" />
      <span>第 {{ currentPage }} / {{ totalPages }} 页</span>
      <el-button text :icon="ArrowRight" :disabled="currentPage === totalPages || loading" aria-label="下一页" title="下一页" @click="goToPage(currentPage + 1)" />
    </div>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { ArrowLeft, ArrowRight, Check, Plus, Refresh, Search } from '@element-plus/icons-vue'
import { createEmployee, listEmployees } from '@/api/archive'
import type { Employee } from '@/types/api'

const router = useRouter()
const route = useRoute()
const employees = ref<Employee[]>([])
const total = ref(0)
const keyword = ref('')
const statusFilter = ref('ALL')
const currentPage = ref(1)
const pageSize = 10
const loading = ref(false)
const loadError = ref('')
const showCreateForm = ref(false)
const creating = ref(false)
const createError = ref('')
const createForm = reactive({ employeeNo: '', name: '', idCardNo: '', departmentId: '' })
const totalPages = ref(1)

function openCreateForm() { showCreateForm.value = true; createError.value = '' }
function closeCreateForm() { showCreateForm.value = false; createError.value = '' }
function resetCreateForm() { Object.assign(createForm, { employeeNo: '', name: '', idCardNo: '', departmentId: '' }) }

async function load() {
  loading.value = true
  loadError.value = ''
  try {
    const page = await listEmployees(currentPage.value, pageSize, keyword.value.trim(), statusFilter.value)
    employees.value = page.items
    total.value = page.total
    totalPages.value = Math.max(1, Math.ceil(page.total / page.pageSize))
    if (currentPage.value > totalPages.value) {
      currentPage.value = totalPages.value
      return load()
    }
  } catch (error) {
    loadError.value = error instanceof Error ? error.message : '员工列表加载失败'
  } finally {
    loading.value = false
  }
}

function applyFilters() {
  currentPage.value = 1
  load()
}
function goToPage(page: number) {
  if (page < 1 || page > totalPages.value || page === currentPage.value) return
  currentPage.value = page
  load()
}
async function submitCreate() {
  creating.value = true
  createError.value = ''
  try {
    await createEmployee({
      employeeNo: createForm.employeeNo.trim(),
      name: createForm.name.trim(),
      idCardNo: createForm.idCardNo.trim() || undefined,
      departmentId: createForm.departmentId ? Number(createForm.departmentId) : null,
    })
    closeCreateForm()
    resetCreateForm()
    await load()
    ElMessage.success('员工档案已建立')
  } catch (error) {
    createError.value = error instanceof Error ? error.message : '员工创建失败'
  } finally {
    creating.value = false
  }
}

watch(statusFilter, () => {
  currentPage.value = 1
  load()
})

onMounted(() => {
  if (route.query.action === 'create') showCreateForm.value = true
  load()
})
</script>
