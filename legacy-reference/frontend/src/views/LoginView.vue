<template>
  <div class="login-page">
    <section class="login-visual">
      <div>
        <div class="login-kicker">Personnel records / 01</div>
        <h1 class="login-title">把每一份档案，变成可追溯的证据。</h1>
        <p class="login-desc">员工资料、原始影像与 OCR 检测结果在同一工作台中关联。识别结果可核验，人工修订有记录。</p>
      </div>
    </section>
    <section class="login-form-side">
      <div class="login-card">
        <p class="eyebrow">内部管理端</p>
        <h1>登录工作台</h1>
        <p class="heading-copy">使用组织账号进入档案管理空间</p>
        <el-form ref="formRef" :model="form" :rules="rules" label-position="top" @submit.prevent="submit">
          <el-form-item label="账号" prop="username">
            <el-input v-model="form.username" size="large" autocomplete="username" placeholder="输入账号" />
          </el-form-item>
          <el-form-item label="密码" prop="password">
            <el-input v-model="form.password" size="large" type="password" show-password autocomplete="current-password" placeholder="输入密码" />
          </el-form-item>
          <el-button class="login-submit" type="primary" size="large" native-type="submit" :loading="loading" style="width: 100%">
            进入工作台
          </el-button>
        </el-form>
        <p v-if="errorMessage" class="login-error">{{ errorMessage }}</p>
        <p class="login-footer">数据在本地服务链路内处理 · v0.1</p>
      </div>
    </section>
  </div>
</template>

<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import type { FormInstance, FormRules } from 'element-plus'
import { ElMessage } from 'element-plus'
import { useAuthStore } from '@/stores/auth'

const formRef = ref<FormInstance>()
const form = reactive({ username: '', password: '' })
const rules: FormRules = {
  username: [{ required: true, message: '请输入账号', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }],
}
const loading = ref(false)
const errorMessage = ref('')
const router = useRouter()
const auth = useAuthStore()

async function submit() {
  if (!(await formRef.value?.validate().catch(() => false))) return
  loading.value = true
  errorMessage.value = ''
  try {
    await auth.login(form.username, form.password)
    await router.replace({ name: 'dashboard' })
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : '登录失败，请稍后重试'
    ElMessage.error(errorMessage.value)
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.login-submit { margin-top: 8px; }
.login-error { margin: 16px 0 0; color: #ad4f44; font-size: 13px; }
</style>
