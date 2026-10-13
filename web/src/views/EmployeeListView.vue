<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import type { FormInstance } from 'element-plus'
import { Search, Plus, Refresh, FolderOpened, EditPen } from '@element-plus/icons-vue'
import { request, type DepartmentNode, type Employee, type Page, type PositionOption } from '../api/http'
import { useAuthStore } from '../stores/auth'
import { maxLength, patternRule, requiredText, requiredValue } from '../utils/live-validation'

const loading = ref(false)
const creating = ref(false)
const showCreate = ref(false)
const createFormRef = ref<FormInstance>()
const editing = ref(false)
const showEdit = ref(false)
const editFormRef = ref<FormInstance>()
const auth = useAuthStore()
const filters = reactive({ keyword: '', status: '', departmentId: '' })
const page = ref(1)
const pageSize = ref(10)
const data = ref<Page<Employee>>({ items: [], total: 0, page: 1, pageSize: 10 })
const departments = ref<DepartmentNode[]>([])
const positions = ref<PositionOption[]>([])
const createForm = reactive({ name: '', phone: '', departmentId: '', positionId: '', status: 'PENDING_ONBOARD', hireDate: '' })
const editForm = reactive({ id: 0, name: '', phone: '' })
const statusLabel: Record<string, string> = { ACTIVE: '在职', PENDING_ONBOARD: '待入职', ON_LEAVE: '请假中', INACTIVE: '离职' }
const flatDepartments = computed(() => {
  const result: DepartmentNode[] = []
  const visit = (nodes: DepartmentNode[]) => nodes.forEach((node) => { result.push(node); visit(node.children || []) })
  visit(departments.value)
  return result
})
async function load() { loading.value = true; try { data.value = await request<Page<Employee>>({ url: '/employees', params: { ...filters, page: page.value, pageSize: pageSize.value, departmentId: filters.departmentId || undefined, status: filters.status || undefined } }) } catch (e) { ElMessage.error(e instanceof Error ? e.message : '员工列表加载失败') } finally { loading.value = false } }
function search() { page.value = 1; load() }
async function openCreate() {
  createForm.name = ''; createForm.phone = ''; createForm.departmentId = ''; createForm.positionId = ''; createForm.status = 'PENDING_ONBOARD'; createForm.hireDate = ''
  try { departments.value = await request<DepartmentNode[]>({ url: '/departments/tree' }); showCreate.value = true }
  catch (e) { ElMessage.error(e instanceof Error ? e.message : '组织数据加载失败') }
}
async function loadPositions(departmentId: string) {
  createForm.positionId = ''
  positions.value = departmentId ? await request<PositionOption[]>({ url: `/departments/${departmentId}/positions` }).catch(() => []) : []
}
async function createEmployee() {
  const valid = await createFormRef.value?.validate().then(() => true).catch(() => false)
  if (!valid) return
  if (!createForm.name.trim() || !createForm.departmentId || !createForm.positionId) return ElMessage.warning('请填写姓名、部门和岗位')
  creating.value = true
  try {
    const created = await request<Employee>({ url: '/employees', method: 'POST', data: { name: createForm.name.trim(), phone: createForm.phone.trim() || undefined, departmentId: Number(createForm.departmentId), positionId: Number(createForm.positionId), status: createForm.status, hireDate: createForm.hireDate || undefined } })
    showCreate.value = false
    ElMessage.success(`员工档案已创建，编号 ${created.employeeNo}`)
    await load()
  } catch (e) { ElMessage.error(e instanceof Error ? e.message : '员工创建失败') }
  finally { creating.value = false }
}
function openProfileEdit(row: unknown) {
  const employee = row as Employee
  editForm.id = employee.id
  editForm.name = employee.name
  editForm.phone = employee.phone || ''
  showEdit.value = true
}
async function saveProfile() {
  const valid = await editFormRef.value?.validate().then(() => true).catch(() => false)
  if (!valid) return
  editing.value = true
  try {
    await request<Employee>({
      url: `/employees/${editForm.id}/profile`,
      method: 'PUT',
      data: { name: editForm.name.trim(), phone: editForm.phone.trim() },
    })
    showEdit.value = false
    ElMessage.success('员工基本资料已更新')
    await load()
  } catch (e) { ElMessage.error(e instanceof Error ? e.message : '员工资料更新失败') }
  finally { editing.value = false }
}
onMounted(load)
</script>

<template>
  <div class="page-head"><div><span class="section-kicker">PEOPLE / DIRECTORY</span><h1>员工与档案</h1><p>按员工编号、姓名和状态检索可访问的人员主数据。</p></div><el-button v-if="auth.roles.includes('HR_ADMIN')" type="primary" class="action-button" @click="openCreate"><el-icon><Plus /></el-icon>新建员工</el-button></div>
  <section class="surface-panel table-panel"><div class="filter-row"><el-input v-model="filters.keyword" clearable placeholder="搜索员工编号或姓名" class="search-input" @keyup.enter="search"><template #prefix><el-icon><Search /></el-icon></template></el-input><el-select v-model="filters.status" clearable placeholder="员工状态" class="compact-select"><el-option v-for="(label, value) in statusLabel" :key="value" :label="label" :value="value" /></el-select><el-input v-model="filters.departmentId" clearable placeholder="部门 ID" class="compact-input" /><el-button type="primary" @click="search">查询</el-button><el-button text @click="filters.keyword = ''; filters.status = ''; filters.departmentId = ''; search()"><el-icon><Refresh /></el-icon>重置</el-button><span class="filter-total">共 {{ data.total }} 人</span></div><el-table v-loading="loading" :data="data.items" row-key="id" class="data-table" empty-text="当前范围暂无员工数据"><el-table-column prop="employeeNo" label="员工编号" width="170"><template #default="scope"><code class="employee-code">{{ scope.row.employeeNo }}</code></template></el-table-column><el-table-column prop="name" label="姓名" min-width="150"><template #default="scope"><strong>{{ scope.row.name }}</strong></template></el-table-column><el-table-column prop="departmentId" label="部门" width="120"><template #default="scope">部门 {{ scope.row.departmentId }}</template></el-table-column><el-table-column prop="positionId" label="岗位" width="120"><template #default="scope">岗位 {{ scope.row.positionId }}</template></el-table-column><el-table-column prop="status" label="状态" width="110"><template #default="scope"><el-tag :type="scope.row.status === 'ACTIVE' ? 'success' : scope.row.status === 'ON_LEAVE' ? 'primary' : scope.row.status === 'INACTIVE' ? 'info' : 'warning'" effect="plain">{{ statusLabel[scope.row.status] || scope.row.status }}</el-tag></template></el-table-column><el-table-column prop="hireDate" label="入职日期" width="130" /><el-table-column label="操作" width="220" fixed="right"><template #default="scope"><el-button v-if="auth.roles.includes('HR_ADMIN')" link type="primary" @click="openProfileEdit(scope.row)"><el-icon><EditPen /></el-icon>维护资料</el-button><el-button link type="primary" @click="$router.push(`/employees/${scope.row.id}/archive`)"><el-icon><FolderOpened /></el-icon>档案</el-button></template></el-table-column></el-table><div class="table-footer"><span>第 {{ page }} 页</span><el-pagination v-model:current-page="page" v-model:page-size="pageSize" layout="prev, pager, next" :total="data.total" @current-change="load" @size-change="load" /></div></section>
<el-dialog v-model="showCreate" title="新建员工档案" width="min(560px, calc(100vw - 32px))" destroy-on-close>
  <el-form ref="createFormRef" label-position="top" :model="createForm">
    <el-form-item label="姓名" prop="name" :rules="[requiredText('请输入员工姓名。'), maxLength(128)]"><el-input v-model="createForm.name" maxlength="128" /></el-form-item>
    <el-form-item label="联系电话" prop="phone" :rules="[patternRule(/^1[3-9]\d{9}$/, '请输入 11 位有效手机号。', true)]"><el-input v-model="createForm.phone" maxlength="11" inputmode="tel" placeholder="可选，11 位手机号" /></el-form-item>
    <el-form-item label="部门" prop="departmentId" :rules="[requiredValue('请选择部门。')]"><el-select v-model="createForm.departmentId" filterable placeholder="选择部门" @change="loadPositions"><el-option v-for="department in flatDepartments" :key="department.id" :label="`${department.code} · ${department.name}`" :value="String(department.id)" /></el-select></el-form-item>
    <el-form-item label="岗位" prop="positionId" :rules="[requiredValue('请选择岗位。')]"><el-select v-model="createForm.positionId" filterable :disabled="!createForm.departmentId" placeholder="先选择部门"><el-option v-for="position in positions" :key="position.id" :label="`${position.code} · ${position.name}`" :value="String(position.id)" /></el-select></el-form-item>
    <el-form-item label="状态"><el-select v-model="createForm.status"><el-option label="待入职" value="PENDING_ONBOARD" /><el-option label="在职" value="ACTIVE" /></el-select></el-form-item>
    <el-form-item label="入职日期"><el-date-picker v-model="createForm.hireDate" type="date" value-format="YYYY-MM-DD" placeholder="可选" /></el-form-item>
  </el-form>
  <template #footer><el-button @click="showCreate = false">取消</el-button><el-button type="primary" :loading="creating" @click="createEmployee">创建员工</el-button></template>
</el-dialog>
<el-dialog v-model="showEdit" title="维护员工资料" width="min(480px, calc(100vw - 32px))" destroy-on-close>
  <el-form ref="editFormRef" label-position="top" :model="editForm">
    <el-form-item label="姓名" prop="name" :rules="[requiredText('请输入员工姓名。'), maxLength(128)]"><el-input v-model="editForm.name" maxlength="128" /></el-form-item>
    <el-form-item label="手机号" prop="phone" :rules="[patternRule(/^\s*$|^1[3-9]\d{9}$/, '请输入 11 位有效手机号。', true)]"><el-input v-model="editForm.phone" maxlength="11" inputmode="tel" placeholder="可选，留空可清除" /></el-form-item>
  </el-form>
  <template #footer><el-button @click="showEdit = false">取消</el-button><el-button type="primary" :loading="editing" @click="saveProfile">保存</el-button></template>
</el-dialog>
</template>
