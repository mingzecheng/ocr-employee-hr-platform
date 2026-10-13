<script setup lang="ts">
import { nextTick, onMounted, reactive, ref, watch } from 'vue'
import { RouterLink, useRouter } from 'vue-router'
import { ElMessage, type FormInstance } from 'element-plus'
import { CircleCheck, CopyDocument } from '@element-plus/icons-vue'
import type { HttpError } from '../api/http'
import { useAuthStore } from '../stores/auth'
import { authorizedReturnUrl, availablePortals, portalHref } from '../portal/portal-config'
import { maxLength, passwordRule, patternRule, requiredText, sameAs } from '../utils/live-validation'

const auth = useAuthStore()
const router = useRouter()
const formRef = ref<FormInstance>()
const form = reactive({ password: '', passwordConfirm: '', name: '', phone: '' })
const loading = ref(false)
const errorMessage = ref('')
const registrationSucceeded = ref(false)
const passwordRules = [requiredText('请设置密码。'), passwordRule()]
const confirmPasswordRules = [requiredText('请再次输入密码。'), sameAs(() => form.password, '两次输入的密码不一致。')]

onMounted(() => {
  const nameInput = document.querySelector<HTMLInputElement>('input[autocomplete="name"]')
  nameInput?.focus()
})

function normalizeName() {
  form.name = form.name.replace(/^\s+/, '')
}

function normalizePhone() {
  form.phone = form.phone.replace(/\D/g, '').slice(0, 11)
}

watch(() => form.password, async () => {
  if (!form.passwordConfirm) return
  await nextTick()
  formRef.value?.validateField('passwordConfirm').catch(() => undefined)
})

async function submit() {
  errorMessage.value = ''
  const valid = await formRef.value?.validate().then(() => true).catch(() => false)
  if (!valid) return

  loading.value = true
  try {
    await auth.register({
      password: form.password,
      name: form.name.trim(),
      phone: form.phone.trim(),
    })
    registrationSucceeded.value = true
  } catch (error) {
    const detail = error as Partial<HttpError>
    if (detail.isNetworkError) errorMessage.value = '无法连接人事服务，请稍后重试。'
    else if (detail.isTimeout) errorMessage.value = '连接人事服务超时，请稍后重试。'
    else if (detail.status && detail.status >= 500) errorMessage.value = '人事服务暂时不可用，请稍后重试。'
    else errorMessage.value = error instanceof Error ? error.message : '注册失败，请重试。'
  } finally {
    loading.value = false
  }
}

async function copyEmployeeNo() {
  const employeeNo = auth.user?.username
  if (!employeeNo) return
  try {
    await navigator.clipboard.writeText(employeeNo)
    ElMessage.success('员工编号已复制')
  } catch {
    ElMessage.error('复制失败，请手动复制员工编号')
  }
}

function enterWorkspace() {
  const returnUrl = authorizedReturnUrl(auth.roles)
  const portals = availablePortals(auth.roles)
  if (returnUrl) window.location.assign(returnUrl)
  else if (portals.length === 1) window.location.assign(portalHref(portals[0].id))
  else router.replace('/workspaces')
}
</script>

<template>
  <main class="login-screen login-simple">
    <div class="login-container register-container">
      <header class="login-brand">
        <img class="brand-logo-image" src="/brand-logo.png" alt="" />
        <div><strong>档案工作台</strong><small>企业员工档案与人事流程管理平台</small></div>
      </header>

      <section class="login-panel" aria-labelledby="register-title">
        <div class="login-card">
          <div v-if="!registrationSucceeded" class="login-heading">
            <h1 id="register-title">创建员工账号</h1>
            <p>使用档案中的姓名和预留手机号完成注册，员工编号由系统生成。</p>
          </div>

          <div v-if="!registrationSucceeded && errorMessage" class="login-error" role="alert" aria-live="polite">
            <strong>{{ errorMessage }}</strong>
          </div>

          <el-form v-if="!registrationSucceeded" ref="formRef" class="login-form register-form" label-position="top" :model="form" @submit.prevent="submit">
            <el-form-item label="姓名" prop="name" :rules="[requiredText('请输入姓名。'), maxLength(128)]">
              <el-input v-model="form.name" autocomplete="name" maxlength="128" placeholder="请输入姓名" @input="normalizeName" />
            </el-form-item>
            <el-form-item label="预留手机号" prop="phone" :rules="[requiredText('请输入预留手机号。'), patternRule(/^1[3-9]\d{9}$/, '请输入 11 位有效手机号。')]">
              <el-input v-model="form.phone" autocomplete="tel" maxlength="11" inputmode="numeric" placeholder="请输入 11 位手机号" @input="normalizePhone" />
            </el-form-item>
            <el-form-item label="登录密码" prop="password" :rules="passwordRules">
              <el-input v-model="form.password" type="password" autocomplete="new-password" minlength="8" maxlength="72" show-password placeholder="请设置 8-72 位密码" />
            </el-form-item>
            <el-form-item label="确认密码" prop="passwordConfirm" :rules="confirmPasswordRules">
              <el-input v-model="form.passwordConfirm" type="password" autocomplete="new-password" minlength="8" maxlength="72" show-password placeholder="请再次输入密码" />
            </el-form-item>
            <el-button class="login-button" type="primary" size="large" native-type="submit" :loading="loading">
              注册账号
            </el-button>
          </el-form>
          <div v-if="!registrationSucceeded" class="login-switch">已有账号？<RouterLink to="/login">返回登录</RouterLink></div>

          <div v-else class="register-success" role="status" aria-live="polite">
            <el-icon class="register-success-icon"><CircleCheck /></el-icon>
            <h1 id="register-title">注册成功</h1>
            <p>员工编号已由系统生成，后续可使用该编号登录。</p>
            <div class="register-account">
              <span>员工编号 / 登录账号</span>
              <strong>{{ auth.user?.username }}</strong>
              <el-tooltip content="复制员工编号" placement="top">
                <el-button class="register-copy" text :icon="CopyDocument" aria-label="复制员工编号" @click="copyEmployeeNo" />
              </el-tooltip>
            </div>
            <el-button class="login-button" type="primary" size="large" @click="enterWorkspace">
              进入员工工作台
            </el-button>
          </div>
        </div>
      </section>
    </div>
  </main>
</template>
