<script setup lang="ts">
import { ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useAuthStore } from '../stores/auth'

const auth = useAuthStore()
const route = useRoute()
const router = useRouter()
const username = ref('demo-admin')
const password = ref('password')
const loading = ref(false)
async function submit() {
  if (!username.value || !password.value) return ElMessage.warning('请输入账号和密码')
  loading.value = true
  try { await auth.login(username.value, password.value); router.replace(String(route.query.redirect || '/dashboard')) }
  catch (error) { ElMessage.error(error instanceof Error ? error.message : '登录失败') }
  finally { loading.value = false }
}
</script>

<template>
  <main class="login-screen"><section class="login-aside"><span class="eyebrow">HR OPERATIONS / 01</span><h1>把每一份档案，<br><em>变成可追溯的流程。</em></h1><p>员工档案、OCR 证据、人事审批与授权盘点，在一个工作台完成闭环。</p><div class="login-stamp"><span>DEMO MODE</span><strong>脱敏数据环境</strong></div></section><section class="login-panel"><div class="login-card"><div class="brand-lockup dark"><span class="brand-mark">档</span><div><strong>档案中枢</strong><small>企业员工档案与人事流程平台</small></div></div><div class="login-heading"><span class="section-kicker">SECURE ACCESS</span><h2>登录工作台</h2><p>使用你的企业账号继续</p></div><el-form @submit.prevent="submit"><el-form-item label="账号"><el-input v-model="username" autocomplete="username" size="large" placeholder="输入账号" /></el-form-item><el-form-item label="密码"><el-input v-model="password" type="password" autocomplete="current-password" size="large" show-password placeholder="输入密码" /></el-form-item><el-button class="login-button" type="primary" size="large" native-type="submit" :loading="loading">进入工作台 <span>↗</span></el-button></el-form><p class="demo-hint">演示账号：<code>demo-admin</code> / <code>password</code></p></div></section></main>
</template>
