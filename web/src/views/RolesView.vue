<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Check, Checked, Close, Refresh, RefreshLeft } from '@element-plus/icons-vue'
import { request, type RolePermissionMatrix } from '../api/http'

const loading = ref(false)
const saving = ref(false)
const data = ref<RolePermissionMatrix>({ roles: [], modules: [], matrix: {} })

/** 本地可编辑草稿矩阵：role -> module -> allowed。 */
const draft = ref<Record<string, Record<string, boolean>>>({})
/** 未保存的改动，保存成功后清空。 */
const dirty = ref<Array<{ role: string; module: string; allowed: boolean }>>([])

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

function isAllowed(role: string, module: string): boolean {
  return Boolean(draft.value?.[role]?.[module])
}

function rebuildDraft(source: RolePermissionMatrix) {
  const next: Record<string, Record<string, boolean>> = {}
  for (const role of source.roles) {
    next[role] = { ...(source.matrix?.[role] || {}) }
  }
  draft.value = next
  dirty.value = []
}

async function load() {
  loading.value = true
  try {
    const loaded = await request<RolePermissionMatrix>({ url: '/admin/roles' })
    data.value = loaded
    rebuildDraft(loaded)
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '角色权限加载失败')
  } finally {
    loading.value = false
  }
}

function toggle(role: string, module: string) {
  if (saving.value) return
  const nextAllowed = !isAllowed(role, module)
  if (!draft.value[role]) draft.value[role] = {}
  draft.value[role][module] = nextAllowed

  const idx = dirty.value.findIndex((d) => d.role === role && d.module === module)
  const entry = { role, module, allowed: nextAllowed }
  if (idx >= 0) dirty.value[idx] = entry
  else dirty.value.push(entry)
}

function reset() {
  rebuildDraft(data.value)
}

async function save() {
  if (!dirty.value.length || saving.value) return
  saving.value = true
  try {
    await Promise.all(
      dirty.value.map((d) =>
        request({
          url: `/admin/roles/${d.role}/permissions`,
          method: 'PUT',
          data: { module: d.module, allowed: d.allowed },
        }),
      ),
    )
    ElMessage.success('权限已更新')
    // 保存成功后把草稿固化为基线
    data.value.matrix = JSON.parse(JSON.stringify(draft.value))
    dirty.value = []
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '权限保存失败')
  } finally {
    saving.value = false
  }
}

onMounted(load)
</script>

<template>
  <div class="page-head">
    <div>
      <span class="section-kicker">RBAC / ROLE MATRIX</span>
      <h1>角色权限矩阵</h1>
      <p>点击 ✅/❌ 切换各角色对功能模块的访问权限，然后点击“保存”批量写回后端；仅影响管理后台的模块可见性，业务接口级权限仍由注解控制。</p>
    </div>
    <div class="head-actions">
      <el-button class="quiet-button" :disabled="!dirty.length || saving" :loading="saving" @click="reset"><el-icon><RefreshLeft /></el-icon>重置</el-button>
      <el-button type="primary" :disabled="!dirty.length" :loading="saving" @click="save"><el-icon><Checked /></el-icon>保存{{ dirty.length ? `（${dirty.length}）` : '' }}</el-button>
      <el-button class="quiet-button" :loading="loading" @click="load"><el-icon><Refresh /></el-icon>刷新</el-button>
    </div>
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
          <button class="cell-toggle" type="button" :title="`${roleLabel(scope.row.role)} · ${module}`" @click="toggle(scope.row.role, module)">
            <el-icon v-if="isAllowed(scope.row.role, module)" class="cell-check"><Check /></el-icon>
            <el-icon v-else class="cell-cross"><Close /></el-icon>
          </button>
        </template>
      </el-table-column>
    </el-table>
    <div class="table-footer">
      <span>共 {{ data.roles.length }} 个角色 · {{ data.modules.length }} 个模块{{ dirty.length ? ` · ${dirty.length} 项未保存` : '' }}</span>
      <span class="legend"><el-icon class="cell-check"><Check /></el-icon> 可访问　<el-icon class="cell-cross"><Close /></el-icon> 不可访问</span>
    </div>
  </section>
</template>

<style scoped>
.head-actions { display: flex; gap: 8px; align-items: center; }
.role-code { margin-left: 8px; color: #909399; font-size: 10px; font-weight: 400; }
.cell-toggle { appearance: none; border: none; background: transparent; cursor: pointer; padding: 4px; border-radius: 6px; line-height: 1; }
.cell-toggle:hover { background: #eef4fb; }
.cell-toggle:disabled { cursor: not-allowed; opacity: .6; }
.cell-check { color: #2f9e6e; font-size: 16px; }
.cell-cross { color: #c2c8cf; font-size: 15px; }
.legend { color: var(--muted); font-size: 10px; display: inline-flex; align-items: center; gap: 4px; }
</style>
