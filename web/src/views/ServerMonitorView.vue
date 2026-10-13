<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Box, Coin, Cpu, Monitor, Refresh, Timer } from '@element-plus/icons-vue'
import { request, type ServerMonitor } from '../api/http'

const loading = ref(false)
const monitor = ref<ServerMonitor | null>(null)
let timer: ReturnType<typeof setInterval> | null = null

const REFRESH_INTERVAL = 5000

async function load() {
  loading.value = true
  try {
    monitor.value = await request<ServerMonitor>({ url: '/admin/monitor' })
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '监控数据加载失败')
  } finally {
    loading.value = false
  }
}

function formatBytes(bytes?: number | null): string {
  if (bytes === null || bytes === undefined || Number.isNaN(bytes)) return '—'
  if (bytes < 1024) return `${bytes} B`
  const units = ['KB', 'MB', 'GB', 'TB']
  let value = bytes
  let unit = 'B'
  for (const next of units) {
    if (value < 1024) break
    value /= 1024
    unit = next
  }
  return `${value.toFixed(unit === 'B' ? 0 : 1)} ${unit}`
}

function formatPercent(value?: number | null): string {
  if (value === null || value === undefined || Number.isNaN(value)) return '—'
  return `${(value * 100).toFixed(1)}%`
}

function formatUptime(ms?: number | null): string {
  if (ms === null || ms === undefined || Number.isNaN(ms) || ms < 0) return '—'
  const totalSec = Math.floor(ms / 1000)
  const days = Math.floor(totalSec / 86400)
  const hours = Math.floor((totalSec % 86400) / 3600)
  const minutes = Math.floor((totalSec % 3600) / 60)
  const secs = totalSec % 60
  if (days > 0) return `${days}天 ${hours}时`
  if (hours > 0) return `${hours}时 ${minutes}分`
  if (minutes > 0) return `${minutes}分 ${secs}秒`
  return `${secs}秒`
}

function formatUptimeSec(sec?: number | null): string {
  if (sec === null || sec === undefined || Number.isNaN(sec) || sec < 0) return '—'
  const days = Math.floor(sec / 86400)
  const hours = Math.floor((sec % 86400) / 3600)
  if (days > 0) return `${days} 天 ${hours} 时`
  const minutes = Math.floor((sec % 3600) / 60)
  return `${hours} 时 ${minutes} 分`
}

const redisUnavailable = computed(() => monitor.value?.redis === null || monitor.value?.redis === undefined)

onMounted(() => {
  void load()
  timer = setInterval(() => { void load() }, REFRESH_INTERVAL)
})

onUnmounted(() => {
  if (timer) clearInterval(timer)
  timer = null
})
</script>

<template>
  <div class="page-head">
    <div>
      <span class="section-kicker">INFRA / SERVER MONITOR</span>
      <h1>服务器监控</h1>
      <p>采集 JVM、系统资源、MySQL 与 Redis 的实时运行指标；每 5 秒自动刷新一次。</p>
    </div>
    <el-button class="quiet-button" :loading="loading" @click="load"><el-icon><Refresh /></el-icon>立即刷新</el-button>
  </div>

  <section v-loading="loading" class="monitor-grid">
    <article class="surface-panel monitor-card">
      <header class="monitor-card-head"><el-icon><Monitor /></el-icon><h2>JVM 虚拟机</h2></header>
      <dl class="monitor-metrics">
        <div><dt>堆内存（已用 / 最大）</dt><dd>{{ monitor ? formatBytes(monitor.jvm?.heapUsed) : '—' }} / {{ monitor ? formatBytes(monitor.jvm?.heapMax) : '—' }}</dd></div>
        <div><dt>非堆内存</dt><dd>{{ monitor ? formatBytes(monitor.jvm?.nonHeapUsed) : '—' }}</dd></div>
        <div><dt>线程数</dt><dd>{{ monitor?.jvm?.threadCount ?? '—' }}</dd></div>
        <div><dt>已加载类</dt><dd>{{ monitor?.jvm?.classLoaded ?? '—' }}</dd></div>
        <div><dt>GC 次数 / 耗时</dt><dd>{{ monitor?.jvm?.gcCount ?? '—' }} 次 / {{ monitor?.jvm?.gcTimeMs ?? '—' }} ms</dd></div>
      </dl>
    </article>

    <article class="surface-panel monitor-card">
      <header class="monitor-card-head"><el-icon><Cpu /></el-icon><h2>系统资源</h2></header>
      <dl class="monitor-metrics">
        <div><dt>CPU 核心数</dt><dd>{{ monitor?.system?.cpuCores ?? '—' }}</dd></div>
        <div><dt>系统使用率</dt><dd>{{ monitor ? formatPercent(monitor.system?.systemLoad) : '—' }}</dd></div>
        <div><dt>物理内存（已用 / 总量）</dt><dd>{{ monitor ? formatBytes(monitor.system?.physicalMemUsed) : '—' }} / {{ monitor ? formatBytes(monitor.system?.physicalMemTotal) : '—' }}</dd></div>
        <div><dt>磁盘（已用 / 可用）</dt><dd>{{ monitor ? formatBytes((monitor.system?.diskTotal ?? 0) - (monitor.system?.diskFree ?? 0)) : '—' }} / {{ monitor ? formatBytes(monitor.system?.diskFree) : '—' }}</dd></div>
      </dl>
    </article>

    <article class="surface-panel monitor-card">
      <header class="monitor-card-head"><el-icon><Coin /></el-icon><h2>MySQL 数据库</h2></header>
      <dl class="monitor-metrics">
        <div><dt>当前连接数</dt><dd>{{ monitor?.mysql?.threadsConnected ?? '—' }}</dd></div>
        <div><dt>运行时间</dt><dd>{{ monitor ? formatUptimeSec(monitor.mysql?.uptimeSec) : '—' }}</dd></div>
      </dl>
    </article>

    <article class="surface-panel monitor-card">
      <header class="monitor-card-head"><el-icon><Box /></el-icon><h2>Redis 缓存</h2></header>
      <dl v-if="!redisUnavailable" class="monitor-metrics">
        <div><dt>已用内存</dt><dd>{{ monitor ? formatBytes(monitor.redis?.usedMemory) : '—' }}</dd></div>
        <div><dt>连接客户端</dt><dd>{{ monitor?.redis?.connectedClients ?? '—' }}</dd></div>
        <div><dt>命中 / 未命中</dt><dd>{{ monitor?.redis?.hits ?? '—' }} / {{ monitor?.redis?.misses ?? '—' }}</dd></div>
      </dl>
      <p v-else class="monitor-offline">Redis 未连接</p>
    </article>

    <article class="surface-panel monitor-card">
      <header class="monitor-card-head"><el-icon><Timer /></el-icon><h2>应用运行时</h2></header>
      <dl class="monitor-metrics">
        <div><dt>启动时间</dt><dd>{{ monitor?.app?.startTime ?? '—' }}</dd></div>
        <div><dt>已运行</dt><dd>{{ monitor ? formatUptime(monitor.app?.uptimeMs) : '—' }}</dd></div>
        <div><dt>版本</dt><dd>{{ monitor?.app?.version ?? '—' }}</dd></div>
      </dl>
    </article>
  </section>
</template>

<style scoped>
.monitor-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 14px; }
.monitor-card { padding: 18px 20px; }
.monitor-card-head { display: flex; align-items: center; gap: 9px; margin-bottom: 14px; color: #386b9e; }
.monitor-card-head h2 { margin: 0; font-size: 14px; font-weight: 650; color: var(--ink); }
.monitor-metrics { display: grid; gap: 10px; margin: 0; }
.monitor-metrics > div { display: flex; align-items: baseline; justify-content: space-between; gap: 12px; }
.monitor-metrics dt { color: var(--muted); font-size: 11px; }
.monitor-metrics dd { margin: 0; color: var(--ink); font-size: 12px; font-weight: 600; font-variant-numeric: tabular-nums; text-align: right; }
.monitor-offline { margin: 8px 0 0; padding: 14px; border: 1px dashed #d9c9c4; border-radius: 6px; color: #b0543f; font-size: 12px; text-align: center; }
@media (max-width: 900px) { .monitor-grid { grid-template-columns: 1fr; } }
</style>
