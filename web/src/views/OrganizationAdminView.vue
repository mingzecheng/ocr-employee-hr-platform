<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import type { FormInstance } from 'element-plus'
import { Edit, Plus, Refresh } from '@element-plus/icons-vue'
import { request, type DepartmentAdmin, type PositionAdmin } from '../api/http'
import { boundedNumber, maxLength, patternRule, positiveId, requiredText } from '../utils/live-validation'

const loading = ref(false)
const saving = ref(false)
const departments = ref<DepartmentAdmin[]>([])
const positions = ref<PositionAdmin[]>([])
const selectedDepartmentId = ref<number | null>(null)
const departmentDialog = ref(false)
const positionDialog = ref(false)
const departmentFormRef = ref<FormInstance>()
const positionFormRef = ref<FormInstance>()
const editingDepartmentId = ref<number | null>(null)
const editingPositionId = ref<number | null>(null)
const departmentForm = reactive({ code: '', name: '', parentId: '', managerEmployeeId: '', sortOrder: 10, enabled: true })
const positionForm = reactive({ code: '', name: '', enabled: true })

async function loadDepartments() {
  loading.value = true
  try {
    departments.value = await request<DepartmentAdmin[]>({ url: '/departments/admin' })
    if (selectedDepartmentId.value === null && departments.value.length) selectedDepartmentId.value = departments.value[0].id
    if (selectedDepartmentId.value !== null) await loadPositions(selectedDepartmentId.value)
  } catch (error) { ElMessage.error(error instanceof Error ? error.message : '组织数据加载失败') }
  finally { loading.value = false }
}

async function loadPositions(departmentId: number) {
  try { positions.value = await request<PositionAdmin[]>({ url: `/departments/${departmentId}/positions/admin` }) }
  catch (error) { ElMessage.error(error instanceof Error ? error.message : '岗位数据加载失败') }
}

function chooseDepartment(department: DepartmentAdmin | null) {
  selectedDepartmentId.value = department?.id ?? null
  positions.value = []
  if (department) loadPositions(department.id)
}

function openDepartment(department?: DepartmentAdmin) {
  editingDepartmentId.value = department?.id || null
  departmentForm.code = department?.code || ''
  departmentForm.name = department?.name || ''
  departmentForm.parentId = department?.parentId ? String(department.parentId) : ''
  departmentForm.managerEmployeeId = department?.managerEmployeeId ? String(department.managerEmployeeId) : ''
  departmentForm.sortOrder = department?.sortOrder ?? 10
  departmentForm.enabled = department?.enabled ?? true
  departmentDialog.value = true
}

async function saveDepartment() {
  const valid = await departmentFormRef.value?.validate().then(() => true).catch(() => false)
  if (!valid) return
  if (!departmentForm.code.trim() || !departmentForm.name.trim()) return ElMessage.warning('请填写部门编码和名称')
  saving.value = true
  try {
    const data = { code: departmentForm.code.trim(), name: departmentForm.name.trim(), parentId: departmentForm.parentId ? Number(departmentForm.parentId) : null, managerEmployeeId: departmentForm.managerEmployeeId ? Number(departmentForm.managerEmployeeId) : null, sortOrder: Number(departmentForm.sortOrder), enabled: departmentForm.enabled }
    await request<DepartmentAdmin>({ url: editingDepartmentId.value ? `/departments/${editingDepartmentId.value}` : '/departments', method: editingDepartmentId.value ? 'PUT' : 'POST', data })
    departmentDialog.value = false
    ElMessage.success(editingDepartmentId.value ? '部门已更新' : '部门已创建')
    await loadDepartments()
  } catch (error) { ElMessage.error(error instanceof Error ? error.message : '部门保存失败') }
  finally { saving.value = false }
}

function openPosition(position?: PositionAdmin) {
  editingPositionId.value = position?.id || null
  positionForm.code = position?.code || ''
  positionForm.name = position?.name || ''
  positionForm.enabled = position?.enabled ?? true
  positionDialog.value = true
}

async function savePosition() {
  const valid = await positionFormRef.value?.validate().then(() => true).catch(() => false)
  if (!valid) return
  if (!selectedDepartmentId.value) return ElMessage.warning('请先选择部门')
  if (!positionForm.code.trim() || !positionForm.name.trim()) return ElMessage.warning('请填写岗位编码和名称')
  saving.value = true
  try {
    const data = { code: positionForm.code.trim(), name: positionForm.name.trim(), enabled: positionForm.enabled }
    await request<PositionAdmin>({ url: editingPositionId.value ? `/departments/${selectedDepartmentId.value}/positions/${editingPositionId.value}` : `/departments/${selectedDepartmentId.value}/positions`, method: editingPositionId.value ? 'PUT' : 'POST', data })
    positionDialog.value = false
    ElMessage.success(editingPositionId.value ? '岗位已更新' : '岗位已创建')
    await loadPositions(selectedDepartmentId.value)
  } catch (error) { ElMessage.error(error instanceof Error ? error.message : '岗位保存失败') }
  finally { saving.value = false }
}

onMounted(loadDepartments)
</script>

<template>
  <div class="page-head">
    <div><span class="section-kicker">ORGANIZATION / MASTER DATA</span><h1>组织岗位</h1><p>维护部门层级和岗位选项；停用记录会保留历史关系。</p></div>
    <div class="head-actions"><el-button class="quiet-button" :loading="loading" @click="loadDepartments"><el-icon><Refresh /></el-icon>刷新</el-button><el-button type="primary" class="action-button" @click="openDepartment()"><el-icon><Plus /></el-icon>新增部门</el-button></div>
  </div>
  <div class="admin-columns">
    <section class="surface-panel table-panel">
      <div class="panel-heading"><div><span class="section-kicker">DEPARTMENTS</span><h2>部门目录</h2></div><span class="filter-total">{{ departments.length }} 个部门</span></div>
      <el-table v-loading="loading" :data="departments" row-key="id" highlight-current-row @current-change="chooseDepartment" empty-text="暂无部门">
        <el-table-column prop="code" label="编码" width="140"><template #default="scope"><code class="employee-code">{{ scope.row.code }}</code></template></el-table-column>
        <el-table-column prop="name" label="部门名称" min-width="150" />
        <el-table-column prop="parentId" label="上级 ID" width="90"><template #default="scope">{{ scope.row.parentId || '根部门' }}</template></el-table-column>
        <el-table-column prop="enabled" label="状态" width="90"><template #default="scope"><el-tag :type="scope.row.enabled ? 'success' : 'info'" effect="plain">{{ scope.row.enabled ? '启用' : '停用' }}</el-tag></template></el-table-column>
        <el-table-column label="操作" width="78"><template #default="scope"><el-button link type="primary" title="编辑部门" @click="openDepartment(scope.row as DepartmentAdmin)"><el-icon><Edit /></el-icon></el-button></template></el-table-column>
      </el-table>
    </section>
    <section class="surface-panel table-panel">
      <div class="panel-heading"><div><span class="section-kicker">POSITIONS</span><h2>{{ departments.find((item) => item.id === selectedDepartmentId)?.name || '选择部门' }} / 岗位</h2></div><el-button v-if="selectedDepartmentId" link type="primary" @click="openPosition()"><el-icon><Plus /></el-icon>新增岗位</el-button></div>
      <el-table :data="positions" empty-text="请选择部门或暂无岗位">
        <el-table-column prop="code" label="编码" width="145"><template #default="scope"><code class="employee-code">{{ scope.row.code }}</code></template></el-table-column>
        <el-table-column prop="name" label="岗位名称" min-width="160" />
        <el-table-column prop="enabled" label="状态" width="90"><template #default="scope"><el-tag :type="scope.row.enabled ? 'success' : 'info'" effect="plain">{{ scope.row.enabled ? '启用' : '停用' }}</el-tag></template></el-table-column>
        <el-table-column label="操作" width="78"><template #default="scope"><el-button link type="primary" title="编辑岗位" @click="openPosition(scope.row as PositionAdmin)"><el-icon><Edit /></el-icon></el-button></template></el-table-column>
      </el-table>
    </section>
  </div>

  <el-dialog v-model="departmentDialog" :title="editingDepartmentId ? '编辑部门' : '新增部门'" width="520px" destroy-on-close>
    <el-form ref="departmentFormRef" label-position="top" :model="departmentForm"><el-form-item label="部门编码" prop="code" :rules="[requiredText('请输入部门编码。'), patternRule(/^[A-Za-z0-9][A-Za-z0-9_-]{0,31}$/, '编码以字母或数字开头，最多 32 位。')]"><el-input v-model="departmentForm.code" maxlength="32" /></el-form-item><el-form-item label="部门名称" prop="name" :rules="[requiredText('请输入部门名称。'), maxLength(128)]"><el-input v-model="departmentForm.name" maxlength="128" /></el-form-item><el-form-item label="上级部门 ID" prop="parentId" :rules="[positiveId()]"><el-input v-model="departmentForm.parentId" inputmode="numeric" placeholder="根部门留空" /></el-form-item><el-form-item label="负责人 ID" prop="managerEmployeeId" :rules="[positiveId()]"><el-input v-model="departmentForm.managerEmployeeId" inputmode="numeric" placeholder="可选，仅限在职员工" /></el-form-item><el-form-item label="排序" prop="sortOrder" :rules="[boundedNumber(0, 100000, '排序值须为 0-100000 的整数。')]"><el-input-number v-model="departmentForm.sortOrder" :min="0" :max="100000" /></el-form-item><el-form-item label="状态"><el-switch v-model="departmentForm.enabled" active-text="启用" inactive-text="停用" /></el-form-item></el-form>
    <template #footer><el-button @click="departmentDialog = false">取消</el-button><el-button type="primary" :loading="saving" @click="saveDepartment">保存部门</el-button></template>
  </el-dialog>
  <el-dialog v-model="positionDialog" :title="editingPositionId ? '编辑岗位' : '新增岗位'" width="460px" destroy-on-close>
    <el-form ref="positionFormRef" label-position="top" :model="positionForm"><el-form-item label="岗位编码" prop="code" :rules="[requiredText('请输入岗位编码。'), patternRule(/^[A-Za-z0-9][A-Za-z0-9_-]{0,31}$/, '编码以字母或数字开头，最多 32 位。')]"><el-input v-model="positionForm.code" maxlength="32" /></el-form-item><el-form-item label="岗位名称" prop="name" :rules="[requiredText('请输入岗位名称。'), maxLength(128)]"><el-input v-model="positionForm.name" maxlength="128" /></el-form-item><el-form-item label="状态"><el-switch v-model="positionForm.enabled" active-text="启用" inactive-text="停用" /></el-form-item></el-form>
    <template #footer><el-button @click="positionDialog = false">取消</el-button><el-button type="primary" :loading="saving" @click="savePosition">保存岗位</el-button></template>
  </el-dialog>
</template>

<style scoped>
.admin-columns { display: grid; grid-template-columns: minmax(0, 1fr) minmax(0, 1fr); gap: 18px; }
@media (max-width: 860px) { .admin-columns { grid-template-columns: 1fr; } }
</style>
