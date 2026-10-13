<script setup lang="ts">
import { Lock } from '@element-plus/icons-vue'
function goWorkspace() { window.location.assign('/#/workspaces') }
</script>

<template>
  <section class="portal-forbidden">
    <span class="portal-forbidden-icon"><el-icon><Lock /></el-icon></span>
    <h1>当前账号无权访问此页面</h1>
    <p>请切换到已授权的工作台，或联系管理员调整角色。</p>
    <el-button type="primary" @click="goWorkspace">返回工作台选择</el-button>
  </section>
</template>
