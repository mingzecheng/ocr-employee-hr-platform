<script setup lang="ts">
import { ArrowRight, Files, List, Warning } from '@element-plus/icons-vue'
import DashboardView from './DashboardView.vue'
</script>

<template>
  <DashboardView />
  <section class="hr-home-links" aria-label="人事运营模块">
    <RouterLink to="/workflows"><el-icon><List /></el-icon><span><strong>人事审批</strong><small>申请复核与离职交接</small></span><el-icon class="shortcut-arrow"><ArrowRight /></el-icon></RouterLink>
    <RouterLink to="/archive-access"><el-icon><Files /></el-icon><span><strong>档案授权</strong><small>申请审批与归还异常</small></span><el-icon class="shortcut-arrow"><ArrowRight /></el-icon></RouterLink>
    <RouterLink to="/contracts"><el-icon><Files /></el-icon><span><strong>合同台账</strong><small>查看到期合同与续签状态</small></span><el-icon class="shortcut-arrow"><ArrowRight /></el-icon></RouterLink>
    <RouterLink to="/inventory"><el-icon><Warning /></el-icon><span><strong>盘点异常</strong><small>差异处理与任务归档</small></span><el-icon class="shortcut-arrow"><ArrowRight /></el-icon></RouterLink>
  </section>
</template>
