<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { FormInstance } from 'element-plus'
import { Check, Filter, Plus, Refresh } from '@element-plus/icons-vue'
import { request, type ContractRecord, type Employee, type Page } from '../api/http'
import { useAuthStore } from '../stores/auth'
import { maxLength, notAfter, notBefore, positiveId, requiredText, requiredValue } from '../utils/live-validation'

const auth = useAuthStore()
const loading = ref(false)
const saving = ref(false)
const showCreate = ref(false)
const formRef = ref<FormInstance>()
const page = ref(1)
const pageSize = ref(10)
const filters = reactive({ status: '', renewalStatus: '' })
const employees = ref<Employee[]>([])
const data = ref<Page<ContractRecord>>({ items: [], total: 0, page: 1, pageSize: 10 })
const form = reactive({ employeeId: '', contractType: 'LABOR', signedOn: '', startsOn: '', endsOn: '', sourceVersionId: '', renewedFromContractId: '', notes: '' })
const contractTypeLabel: Record<string, string> = { LABOR: '劳动合同', INTERNSHIP: '实习协议', SERVICE: '服务协议', OTHER: '其他' }
const renewalLabel: Record<string, string> = { NOT_DUE: '暂未到期', DUE: '待续签', RENEWED: '已续签', NOT_RENEWING: '不再续签' }

async function load() {
  loading.value = true
  try {
    data.value = await request<Page<ContractRecord>>({ url: '/contracts', params: { page: page.value, pageSize: pageSize.value, status: filters.status || undefined, renewalStatus: filters.renewalStatus || undefined } })
  } catch (e) { ElMessage.error(e instanceof Error ? e.message : '合同台账加载失败') }
  finally { loading.value = false }
}

async function loadEmployees() {
  try { employees.value = (await request<Page<Employee>>({ url: '/employees', params: { page: 1, pageSize: 100 } })).items }
  catch (e) { ElMessage.error(e instanceof Error ? e.message : '员工列表加载失败') }
}

function openCreate(previous?: ContractRecord) {
  form.employeeId = previous ? String(previous.employeeId) : ''
  form.contractType = previous?.contractType || 'LABOR'
  form.signedOn = ''
  form.startsOn = ''
  form.endsOn = ''
  form.sourceVersionId = ''
  form.renewedFromContractId = previous ? String(previous.id) : ''
  form.notes = ''
  showCreate.value = true
}

async function createContract() {
  const valid = await formRef.value?.validate().then(() => true).catch(() => false)
  if (!valid) return
  if (!form.employeeId || !form.signedOn || !form.startsOn || !form.endsOn) return ElMessage.warning('请选择员工并填写完整合同日期')
  saving.value = true
  try {
    await request<ContractRecord>({ url: '/contracts', method: 'POST', data: {
      employeeId: Number(form.employeeId), contractType: form.contractType,
      signedOn: form.signedOn, startsOn: form.startsOn, endsOn: form.endsOn,
      sourceVersionId: form.sourceVersionId ? Number(form.sourceVersionId) : undefined,
      renewedFromContractId: form.renewedFromContractId ? Number(form.renewedFromContractId) : undefined,
      notes: form.notes.trim() || undefined,
    } })
    showCreate.value = false
    ElMessage.success(form.renewedFromContractId ? '续签合同已登记，原合同状态已更新' : '合同已登记')
    load()
  } catch (e) { ElMessage.error(e instanceof Error ? e.message : '合同保存失败') }
  finally { saving.value = false }
}

async function markNotRenewing(contract: ContractRecord) {
  try {
    await ElMessageBox.confirm(`确认合同 ${contract.contractNo} 到期后不再续签？`, '确认续签计划', { type: 'warning' })
    await request<ContractRecord>({ url: `/contracts/${contract.id}/renewal-status`, method: 'PUT' })
    ElMessage.success('续签计划已记录')
    load()
  } catch (e) { if (e !== 'cancel' && e !== 'close') ElMessage.error(e instanceof Error ? e.message : '状态更新失败') }
}

function employeeName(id: number) { return employees.value.find((employee) => employee.id === id)?.name || `员工 ${id}` }
function canManage() { return auth.roles.includes('HR_ADMIN') }
onMounted(() => { load(); loadEmployees() })
</script>

<template>
  <div class="page-head">
    <div><span class="section-kicker">PEOPLE / CONTRACT REGISTER</span><h1>合同台账</h1><p>合同日期、附件版本和续签决定按员工范围留档。</p></div>
    <div class="head-actions"><el-button class="quiet-button" @click="load"><el-icon><Refresh /></el-icon>刷新</el-button><el-button v-if="canManage()" type="primary" class="action-button" @click="openCreate()"><el-icon><Plus /></el-icon>登记合同</el-button></div>
  </div>
  <section class="surface-panel table-panel">
    <div class="filter-row"><el-select v-model="filters.status" clearable placeholder="合同状态" class="compact-select"><el-option label="生效中" value="ACTIVE" /><el-option label="已到期" value="EXPIRED" /></el-select><el-select v-model="filters.renewalStatus" clearable placeholder="续签状态" class="compact-select"><el-option v-for="(label, value) in renewalLabel" :key="value" :label="label" :value="value" /></el-select><el-button type="primary" @click="page = 1; load()"><el-icon><Filter /></el-icon>筛选</el-button><span class="filter-total">共 {{ data.total }} 份合同</span></div>
    <el-table v-loading="loading" :data="data.items" class="data-table" empty-text="当前范围没有合同记录">
      <el-table-column prop="contractNo" label="合同编号" min-width="190"><template #default="scope"><code class="employee-code">{{ scope.row.contractNo }}</code></template></el-table-column>
      <el-table-column label="员工" min-width="150"><template #default="scope"><strong>{{ employeeName(scope.row.employeeId) }}</strong></template></el-table-column>
      <el-table-column prop="contractType" label="合同类型" width="130"><template #default="scope">{{ contractTypeLabel[scope.row.contractType] || scope.row.contractType }}</template></el-table-column>
      <el-table-column prop="startsOn" label="生效日期" width="125" />
      <el-table-column prop="endsOn" label="到期日期" width="125" />
      <el-table-column prop="status" label="状态" width="100"><template #default="scope"><el-tag :type="scope.row.status === 'ACTIVE' ? 'success' : 'info'" effect="plain">{{ scope.row.status === 'ACTIVE' ? '生效中' : '已到期' }}</el-tag></template></el-table-column>
      <el-table-column prop="renewalStatus" label="续签计划" width="120"><template #default="scope"><el-tag :type="scope.row.renewalStatus === 'DUE' ? 'warning' : scope.row.renewalStatus === 'NOT_RENEWING' ? 'info' : scope.row.renewalStatus === 'RENEWED' ? 'success' : 'primary'" effect="plain">{{ renewalLabel[scope.row.renewalStatus] }}</el-tag></template></el-table-column>
      <el-table-column prop="sourceVersionId" label="档案版本" width="110"><template #default="scope">{{ scope.row.sourceVersionId ? `版本 ${scope.row.sourceVersionId}` : '未关联' }}</template></el-table-column>
      <el-table-column v-if="canManage()" label="操作" width="180" fixed="right"><template #default="scope"><div class="row-actions"><el-button v-if="scope.row.renewalStatus === 'DUE'" link type="primary" @click="openCreate(scope.row as ContractRecord)"><el-icon><Plus /></el-icon>登记续签</el-button><el-button v-if="['DUE', 'NOT_DUE'].includes(scope.row.renewalStatus)" link type="warning" @click="markNotRenewing(scope.row as ContractRecord)"><el-icon><Check /></el-icon>不再续签</el-button></div></template></el-table-column>
    </el-table>
    <div class="table-footer"><span>第 {{ page }} 页</span><el-pagination v-model:current-page="page" layout="prev, pager, next" :page-size="pageSize" :total="data.total" @current-change="load" /></div>
  </section>

  <el-dialog v-model="showCreate" :title="form.renewedFromContractId ? '登记续签合同' : '登记合同'" width="520px" destroy-on-close>
    <el-form ref="formRef" label-position="top" class="dialog-form" :model="form">
      <el-form-item label="员工" prop="employeeId" :rules="[requiredValue('请选择员工。')]"><el-select v-model="form.employeeId" filterable :disabled="Boolean(form.renewedFromContractId)" placeholder="选择员工"><el-option v-for="employee in employees" :key="employee.id" :label="`${employee.employeeNo} · ${employee.name}`" :value="String(employee.id)" /></el-select></el-form-item>
      <el-form-item label="合同类型"><el-select v-model="form.contractType"><el-option v-for="(label, value) in contractTypeLabel" :key="value" :label="label" :value="value" /></el-select></el-form-item>
      <el-form-item label="签署日期" prop="signedOn" :rules="[requiredText('请选择签署日期。'), notAfter(() => form.startsOn, '签署日期不能晚于生效日期。')]"><el-date-picker v-model="form.signedOn" type="date" value-format="YYYY-MM-DD" /></el-form-item>
      <el-form-item label="生效日期" prop="startsOn" :rules="[requiredText('请选择生效日期。'), notBefore(() => form.signedOn, '生效日期不能早于签署日期。'), notAfter(() => form.endsOn, '生效日期不能晚于到期日期。')]"><el-date-picker v-model="form.startsOn" type="date" value-format="YYYY-MM-DD" /></el-form-item>
      <el-form-item label="到期日期" prop="endsOn" :rules="[requiredText('请选择到期日期。'), notBefore(() => form.startsOn, '到期日期不能早于生效日期。')]"><el-date-picker v-model="form.endsOn" type="date" value-format="YYYY-MM-DD" /></el-form-item>
      <el-form-item label="档案版本 ID" prop="sourceVersionId" :rules="[positiveId()]"><el-input v-model="form.sourceVersionId" inputmode="numeric" placeholder="可选，关联合同档案版本" /></el-form-item>
      <el-form-item label="备注" prop="notes" :rules="[maxLength(512)]"><el-input v-model="form.notes" type="textarea" :rows="2" maxlength="512" show-word-limit /></el-form-item>
    </el-form>
    <template #footer><el-button @click="showCreate = false">取消</el-button><el-button type="primary" :loading="saving" @click="createContract">保存合同</el-button></template>
  </el-dialog>
</template>
