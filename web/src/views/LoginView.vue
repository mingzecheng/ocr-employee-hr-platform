<script setup lang="ts">
import { reactive, ref } from 'vue'
import { RouterLink, useRouter } from 'vue-router'
import type { HttpError } from '../api/http'
import { useAuthStore } from '../stores/auth'
import { authorizedReturnUrl, availablePortals, portalHref } from '../portal/portal-config'
import { requiredText } from '../utils/live-validation'

const auth = useAuthStore()
const router = useRouter()
const form = reactive({ username: '', password: '' })
const loading = ref(false)
const errorMessage = ref('')
const errorDetail = ref('')

function showLoginError(error: unknown) {
  const detail = error as Partial<HttpError>
  if (detail.isNetworkError) {
    errorMessage.value = '无法连接人事服务，请确认服务已启动后重试。'
  } else if (detail.isTimeout) {
    errorMessage.value = '连接人事服务超时，请稍后重试。'
  } else if (detail.status && detail.status >= 500) {
    errorMessage.value = '人事服务暂时不可用，请稍后重试。'
  } else {
    errorMessage.value = error instanceof Error ? error.message : '登录失败，请重试。'
  }

  errorDetail.value = [detail.code, detail.traceId ? `追踪编号 ${detail.traceId}` : '']
    .filter(Boolean)
    .join(' · ')
}

async function submit() {
  errorMessage.value = ''
  errorDetail.value = ''
  if (!form.username.trim() || !form.password) {
    errorMessage.value = '请输入账号和密码。'
    return
  }
  loading.value = true
  try {
    await auth.login(form.username, form.password)
    const returnUrl = authorizedReturnUrl(auth.roles)
    const portals = availablePortals(auth.roles)
    if (returnUrl) window.location.assign(returnUrl)
    else if (portals.length === 1) window.location.assign(portalHref(portals[0].id))
    else router.replace('/workspaces')
  }
  catch (error) { showLoginError(error) }
  finally { loading.value = false }
}
</script>

<template>
  <main class="login-screen login-simple">
    <div class="login-container">
      <header class="login-brand">
        <img class="brand-logo-image" src="/brand-logo.png" alt="" />
        <div><strong>档案工作台</strong><small>企业员工档案与人事流程管理平台</small></div>
      </header>

      <section class="login-panel" aria-labelledby="login-title">
        <div class="login-card">
          <div class="login-heading">
            <h1 id="login-title">登录档案工作台</h1>
          </div>

          <div v-if="errorMessage" class="login-error" role="alert" aria-live="polite">
            <strong>{{ errorMessage }}</strong>
            <small v-if="errorDetail">{{ errorDetail }}</small>
          </div>

          <el-form class="login-form" label-position="top" :model="form" @submit.prevent="submit">
            <el-form-item label="账号" prop="username" :rules="[requiredText('请输入账号。')]">
              <el-input v-model="form.username" autocomplete="username" size="large" placeholder="输入企业账号或员工编号" />
            </el-form-item>
            <el-form-item label="密码" prop="password" :rules="[requiredText('请输入密码。')]">
              <el-input v-model="form.password" type="password" autocomplete="current-password" size="large" show-password placeholder="输入密码" />
            </el-form-item>
            <el-button class="login-button" type="primary" size="large" native-type="submit" :loading="loading">
              登录
            </el-button>
          </el-form>
          <div class="login-switch">还没有账号？<RouterLink to="/register">员工注册</RouterLink></div>
        </div>
      </section>
    </div>
  </main>
</template>
