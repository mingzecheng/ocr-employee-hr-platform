<script setup lang="ts">
import { nextTick, onMounted, reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import type { FormInstance } from 'element-plus'
import { Edit, Plus, Refresh } from '@element-plus/icons-vue'
import { request, type AdminRoleOption, type AdminUser, type Page } from '../api/http'
import { maxLength, passwordRule, positiveId, requiredList, requiredText, sameAs } from '../utils/live-validation'

const loading = ref(false)
const saving = ref(false)
const showDialog = ref(false)
const formRef = ref<FormInstance>()
const editingId = ref<number | null>(null)
const data = ref<Page<AdminUser>>({ items: [], total: 0, page: 1, pageSize: 20 })
const roles = ref<AdminRoleOption[]>([])
const form = reactive({ username: '', password: '', passwordConfirm: '', employeeId: '', departmentId: '', enabled: true, roleCodes: [] as string[] })

watch(() => form.password, async () => {
  if (!form.passwordConfirm) return
  await nextTick()
  formRef.value?.validateField('passwordConfirm').catch(() => undefined)
})

async function load() {
  loading.value = true
  try { data.value = await request<Page<AdminUser>>({ url: '/admin/users', params: { page: data.value.page, pageSize: data.value.pageSize } }); roles.value = await request<AdminRoleOption[]>({ url: '/admin/users/roles' }) }
  catch (error) { ElMessage.error(error instanceof Error ? error.message : '账号列表加载失败') }
  finally { loading.value = false }
}

function openForm(user?: AdminUser) {
  editingId.value = user?.id || null
  form.username = user?.username || ''
  form.password = ''
  form.passwordConfirm = ''
  form.employeeId = user?.employeeId ? String(user.employeeId) : ''
  form.departmentId = user?.departmentId ? String(user.departmentId) : ''
  form.enabled = user?.enabled ?? true
  form.roleCodes = [...(user?.roleCodes || [])]
  showDialog.value = true
}

async function save() {
  const valid = await formRef.value?.validate().then(() => true).catch(() => false)
  if (!valid) return
  if (!editingId.value && (!form.username.trim() || form.password.length < 8)) return ElMessage.warning('新账号需要填写用户名和至少 8 位密码')
  if (form.password.length > 72) return ElMessage.warning('密码不能超过 72 位')
  if (form.password && new TextEncoder().encode(form.password).length > 72) return ElMessage.warning('密码按 UTF-8 编码后不能超过 72 字节')
  if (form.password && form.password !== form.passwordConfirm) return ElMessage.warning('两次输入的密码不一致')
  if (!form.roleCodes.length) return ElMessage.warning('至少选择一个角色')
  saving.value = true
  try {
    const binding = { employeeId: form.employeeId ? Number(form.employeeId) : null, departmentId: form.departmentId ? Number(form.departmentId) : null, enabled: form.enabled, roleCodes: form.roleCodes }
    const payload = editingId.value ? { ...binding, password: form.password || null } : { ...binding, username: form.username.trim(), password: form.password }
    await request<AdminUser>({ url: editingId.value ? `/admin/users/${editingId.value}` : '/admin/users', method: editingId.value ? 'PUT' : 'POST', data: payload })
    showDialog.value = false
    ElMessage.success(editingId.value ? '账号已更新；权限将在下次登录生效' : '账号已创建')
    await load()
  } catch (error) { ElMessage.error(error instanceof Error ? error.message : '账号保存失败') }
  finally { saving.value = false }
}

onMounted(load)
</script>

<template>
  <div class="page-head"><div><span class="section-kicker">IDENTITY / ACCESS CONTROL</span><h1>账号角色</h1><p>系统管理员维护登录账号与完整角色集合；角色变更将在账号下次登录时生效。</p><p style="font-size:12px;color:#909399;margin-top:4px;">角色为固定枚举（EMPLOYEE / DEPT_MANAGER / HR_ADMIN / SYSTEM_ADMIN），接口权限由后端注解统一控制；数据库权限表仅作未来扩展预留，当前不支持细粒度权限配置。</p></div><div class="head-actions"><el-button class="quiet-button" :loading="loading" @click="load"><el-icon><Refresh /></el-icon>刷新</el-button><el-button type="primary" class="action-button" @click="openForm()"><el-icon><Plus /></el-icon>新增账号</el-button></div></div>
  <section class="surface-panel table-panel"><el-table v-loading="loading" :data="data.items" class="data-table" empty-text="暂无账号，请创建演示账号"><el-table-column prop="username" label="登录账号" min-width="180"><template #default="scope"><strong>{{ scope.row.username }}</strong></template></el-table-column><el-table-column prop="employeeId" label="绑定员工 ID" width="130"><template #default="scope">{{ scope.row.employeeId || '未绑定' }}</template></el-table-column><el-table-column prop="departmentId" label="部门 ID" width="110"><template #default="scope">{{ scope.row.departmentId || '未绑定' }}</template></el-table-column><el-table-column label="角色" min-width="260"><template #default="scope"><el-tag v-for="role in scope.row.roleCodes" :key="role" class="role-tag" effect="plain">{{ roles.find((option) => option.code === role)?.name || role }}</el-tag></template></el-table-column><el-table-column prop="enabled" label="状态" width="90"><template #default="scope"><el-tag :type="scope.row.enabled ? 'success' : 'info'" effect="plain">{{ scope.row.enabled ? '启用' : '停用' }}</el-tag></template></el-table-column><el-table-column label="操作" width="80"><template #default="scope"><el-button link type="primary" title="编辑账号" @click="openForm(scope.row as AdminUser)"><el-icon><Edit /></el-icon></el-button></template></el-table-column></el-table><div class="table-footer"><span>共 {{ data.total }} 个账号</span><el-pagination v-model:current-page="data.page" layout="prev, pager, next" :page-size="data.pageSize" :total="data.total" @current-change="load" /></div></section>
  <el-dialog v-model="showDialog" :title="editingId ? '编辑账号' : '新增账号'" width="520px" destroy-on-close>
    <el-form ref="formRef" label-position="top" :model="form">
      <el-form-item v-if="!editingId" label="登录账号" prop="username" :rules="[requiredText('请输入登录账号。'), maxLength(64)]">
        <el-input v-model="form.username" maxlength="64" autocomplete="username" />
      </el-form-item>
      <el-form-item label="密码" prop="password" :rules="[...(editingId ? [] : [requiredText('请输入密码。')]), passwordRule(Boolean(editingId))]">
        <el-input v-model="form.password" type="password" minlength="8" maxlength="72" show-password :placeholder="editingId ? '留空表示不修改密码' : '至少 8 位'" autocomplete="new-password" />
      </el-form-item>
      <el-form-item v-if="editingId" label="确认新密码" prop="passwordConfirm" :rules="[sameAs(() => form.password, '两次输入的密码不一致。', true)]">
        <el-input v-model="form.passwordConfirm" type="password" minlength="8" maxlength="72" show-password autocomplete="new-password" />
      </el-form-item>
      <el-form-item v-else label="确认密码" prop="passwordConfirm" :rules="[sameAs(() => form.password, '两次输入的密码不一致。')]">
        <el-input v-model="form.passwordConfirm" type="password" minlength="8" maxlength="72" show-password autocomplete="new-password" />
      </el-form-item>
      <el-form-item label="绑定员工 ID" prop="employeeId" :rules="[positiveId()]">
        <el-input v-model="form.employeeId" inputmode="numeric" placeholder="普通角色必须绑定在职员工" />
      </el-form-item>
      <el-form-item label="部门 ID" prop="departmentId" :rules="[positiveId()]">
        <el-input v-model="form.departmentId" inputmode="numeric" placeholder="必须与员工所属部门一致" />
      </el-form-item>
      <el-form-item label="角色" prop="roleCodes" :rules="[requiredList('至少选择一个角色。')]">
        <el-checkbox-group v-model="form.roleCodes">
          <el-checkbox v-for="role in roles" :key="role.code" :value="role.code">{{ role.name }}</el-checkbox>
        </el-checkbox-group>
      </el-form-item>
      <el-form-item label="状态"><el-switch v-model="form.enabled" active-text="启用" inactive-text="停用" /></el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="showDialog = false">取消</el-button>
      <el-button type="primary" :loading="saving" @click="save">保存账号</el-button>
    </template>
  </el-dialog>
</template>

<style scoped>
.role-tag { margin: 2px 6px 2px 0; }
</style>
