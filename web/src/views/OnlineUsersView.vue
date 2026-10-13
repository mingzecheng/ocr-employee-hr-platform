<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Refresh, User } from '@element-plus/icons-vue'
import { request, type OnlineUser } from '../api/http'

const loading = ref(false)
const forcingUserId = ref<number | null>(null)
const users = ref<OnlineUser[]>([])

async function load() {
  loading.value = true
  try {
    users.value = await request<OnlineUser[]>({ url: '/admin/online-users' })
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '在线用户加载失败')
  } finally {
    loading.value = false
  }
}

async function forceLogout(row: OnlineUser) {
  try {
    await ElMessageBox.confirm(
      `确认强制下线用户「${row.username}」？该用户的刷新令牌将被吊销，需重新登录。`,
      '强制下线',
      { type: 'warning', confirmButtonText: '强制下线', cancelButtonText: '取消' },
    )
  } catch {
    return // 用户取消
  }
  forcingUserId.value = row.userId
  try {
    await request<null>({ url: `/admin/online-users/${row.userId}`, method: 'DELETE' })
    ElMessage.success(`已强制下线 ${row.username}`)
    await load()
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '强制下线失败')
  } finally {
    forcingUserId.value = null
  }
}

onMounted(load)
</script>

<template>
  <div class="page-head">
    <div>
      <span class="section-kicker">SESSION / ONLINE USERS</span>
      <h1>在线用户</h1>
      <p>根据 Redis 中的有效刷新令牌统计当前在线会话；可对异常会话执行强制下线，吊销其令牌并加入黑名单。</p>
    </div>
    <el-button class="quiet-button" :loading="loading" @click="load"><el-icon><Refresh /></el-icon>刷新</el-button>
  </div>

  <section class="surface-panel table-panel">
    <el-table v-loading="loading" :data="users" class="data-table" empty-text="当前没有在线用户">
      <el-table-column prop="userId" label="用户 ID" width="110" />
      <el-table-column label="用户名" min-width="180">
        <template #default="scope"><strong>{{ scope.row.username }}</strong></template>
      </el-table-column>
      <el-table-column label="角色" min-width="220">
        <template #default="scope">
          <el-tag v-for="role in scope.row.roles" :key="role" class="role-tag" effect="plain">{{ role }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="近似登录时间" min-width="190">
        <template #default="scope">{{ scope.row.loginApprox || '—' }}</template>
      </el-table-column>
      <el-table-column label="操作" width="120" fixed="right">
        <template #default="scope">
          <el-button
            link
            type="danger"
            :loading="forcingUserId === scope.row.userId"
            @click="forceLogout(scope.row as OnlineUser)"
          ><el-icon><User /></el-icon>强制下线</el-button>
        </template>
      </el-table-column>
    </el-table>
    <div class="table-footer"><span>共 {{ users.length }} 个在线用户</span></div>
  </section>
</template>

<style scoped>
.role-tag { margin: 2px 6px 2px 0; }
</style>
