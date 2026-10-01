<script setup lang="ts">
import { ref } from 'vue'

const signedIn = ref(false)
</script>

<template>
  <main class="app-shell">
    <section class="app-card" aria-labelledby="app-title">
      <p class="eyebrow">HR PLATFORM</p>
      <h1 id="app-title">员工档案与人事流程平台</h1>
      <p class="description">新业务平台骨架已就绪，后续模块按领域逐步接入。</p>
      <el-button type="primary" @click="signedIn = !signedIn">
        {{ signedIn ? '退出演示' : '进入演示' }}
      </el-button>
      <p v-if="signedIn" class="status" role="status">演示会话已开启</p>
    </section>
  </main>
</template>
