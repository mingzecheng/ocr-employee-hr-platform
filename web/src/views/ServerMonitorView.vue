<script setup lang="ts">
import { computed, nextTick, onMounted, onUnmounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import * as echarts from 'echarts'
import { Box, Coin, Cpu, Monitor, PieChart, Refresh, Timer } from '@element-plus/icons-vue'
import { request, type ServerMonitor } from '../api/http'

const loading = ref(false)
const monitor = ref<ServerMonitor | null>(null)
let timer: ReturnType<typeof setInterval> | null = null

const REFRESH_INTERVAL = 5000
const MAX_SAMPLES = 20

/** CPU / MySQL 连接数的内存采样序列，最多保留 MAX_SAMPLES 个点。 */
const cpuHistory = ref<number[]>([])
const mysqlHistory = ref<number[]>([])

// 图表 DOM 容器与 echarts 实例
const heapChartEl = ref<HTMLDivElement | null>(null)
const cpuChartEl = ref<HTMLDivElement | null>(null)
const memGaugeEl = ref<HTMLDivElement | null>(null)
const redisChartEl = ref<HTMLDivElement | null>(null)
const mysqlChartEl = ref<HTMLDivElement | null>(null)

let heapChart: echarts.ECharts | null = null
let cpuChart: echarts.ECharts | null = null
let memGauge: echarts.ECharts | null = null
let redisChart: echarts.ECharts | null = null
let mysqlChart: echarts.ECharts | null = null

async function load() {
  loading.value = true
  try {
    monitor.value = await request<ServerMonitor>({ url: '/admin/monitor' })
    pushSample()
    await nextTick()
    renderCharts()
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '监控数据加载失败')
  } finally {
    loading.value = false
  }
}

function pushSample() {
  const sys = monitor.value?.system
  if (sys && typeof sys.systemLoad === 'number') {
    cpuHistory.value = [...cpuHistory.value, Number((sys.systemLoad * 100).toFixed(1))].slice(-MAX_SAMPLES)
  }
  const mc = monitor.value?.mysql?.threadsConnected
  if (typeof mc === 'number') {
    mysqlHistory.value = [...mysqlHistory.value, mc].slice(-MAX_SAMPLES)
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

const memUsedPercent = computed(() => {
  const sys = monitor.value?.system
  if (!sys || !sys.physicalMemTotal) return 0
  return Number(((sys.physicalMemUsed / sys.physicalMemTotal) * 100).toFixed(1))
})

/** jsdom 无 canvas 2d 上下文，守卫后跳过 echarts 初始化，避免测试环境抛错。 */
function canvasAvailable(): boolean {
  if (typeof document === 'undefined') return false
  try {
    const el = document.createElement('canvas')
    return Boolean(el.getContext?.('2d'))
  } catch {
    return false
  }
}

function initCharts() {
  if (!canvasAvailable()) return
  if (heapChartEl.value && !heapChart) heapChart = echarts.init(heapChartEl.value)
  if (cpuChartEl.value && !cpuChart) cpuChart = echarts.init(cpuChartEl.value)
  if (memGaugeEl.value && !memGauge) memGauge = echarts.init(memGaugeEl.value)
  if (redisChartEl.value && !redisChart) redisChart = echarts.init(redisChartEl.value)
  if (mysqlChartEl.value && !mysqlChart) mysqlChart = echarts.init(mysqlChartEl.value)
}

function renderCharts() {
  const m = monitor.value
  if (!m) return

  // JVM 堆内存：已用 vs 空闲（后端仅提供 heapUsed/heapMax）
  if (heapChart) {
    const used = m.jvm.heapUsed
    const free = Math.max(0, m.jvm.heapMax - used)
    heapChart.setOption({
      tooltip: { trigger: 'item', formatter: (p: { name: string; value: number; percent: number }) => `${p.name}：${formatBytes(p.value)}（${p.percent}%）` },
      legend: { bottom: 0, textStyle: { fontSize: 11 } },
      series: [{
        type: 'pie', radius: ['42%', '68%'], center: ['50%', '44%'],
        label: { fontSize: 11 },
        data: [
          { name: '已用堆内存', value: used, itemStyle: { color: '#3b82c4' } },
          { name: '空闲堆内存', value: free, itemStyle: { color: '#dbe5f0' } },
        ],
      }],
    })
  }

  // CPU 使用率折线（最近 20 个采样点）
  if (cpuChart) {
    cpuChart.setOption({
      grid: { left: 40, right: 16, top: 18, bottom: 24 },
      tooltip: { trigger: 'axis', valueFormatter: (v: number) => `${v}%` },
      xAxis: { type: 'category', boundaryGap: false, data: cpuHistory.value.map((_, i) => `${i + 1}`) },
      yAxis: { type: 'value', max: 100, axisLabel: { formatter: '{value}%' } },
      series: [{ name: 'CPU', type: 'line', smooth: true, data: cpuHistory.value, areaStyle: { opacity: 0.15 }, lineStyle: { width: 2 }, itemStyle: { color: '#2f9e6e' } }],
    })
  }

  // 物理内存仪表盘
  if (memGauge) {
    memGauge.setOption({
      series: [{
        type: 'gauge', startAngle: 210, endAngle: -30, min: 0, max: 100,
        radius: '95%', progress: { show: true, width: 12 }, axisLine: { lineStyle: { width: 12 } },
        axisTick: { show: false }, splitLine: { show: false }, axisLabel: { show: false },
        pointer: { show: true, length: '60%' }, anchor: { show: true },
        detail: { valueAnimation: true, formatter: '{value}%', fontSize: 18, offsetCenter: [0, '70%'] },
        data: [{ value: memUsedPercent.value }],
      }],
    })
  }

  // Redis 命中 / 未命中饼图
  if (redisChart) {
    redisChart.setOption({
      tooltip: { trigger: 'item', formatter: '{b}：{c} 次（{d}%）' },
      legend: { bottom: 0, textStyle: { fontSize: 11 } },
      series: [{
        type: 'pie', radius: '60%', center: ['50%', '44%'],
        data: m.redis
          ? [
              { name: '命中 hits', value: m.redis.hits, itemStyle: { color: '#2f9e6e' } },
              { name: '未命中 misses', value: m.redis.misses, itemStyle: { color: '#e0a458' } },
            ]
          : [],
      }],
    })
  }

  // MySQL 连接数小趋势
  if (mysqlChart) {
    mysqlChart.setOption({
      grid: { left: 30, right: 12, top: 14, bottom: 20 },
      tooltip: { trigger: 'axis' },
      xAxis: { type: 'category', show: false, data: mysqlHistory.value.map((_, i) => `${i + 1}`) },
      yAxis: { type: 'value', minInterval: 1 },
      series: [{ name: '连接数', type: 'line', smooth: true, data: mysqlHistory.value, areaStyle: { opacity: 0.12 }, itemStyle: { color: '#b0543f' } }],
    })
  }
}

function resizeAll() {
  heapChart?.resize()
  cpuChart?.resize()
  memGauge?.resize()
  redisChart?.resize()
  mysqlChart?.resize()
}

function disposeCharts() {
  heapChart?.dispose(); heapChart = null
  cpuChart?.dispose(); cpuChart = null
  memGauge?.dispose(); memGauge = null
  redisChart?.dispose(); redisChart = null
  mysqlChart?.dispose(); mysqlChart = null
}

onMounted(async () => {
  await load()
  await nextTick()
  initCharts()
  renderCharts()
  window.addEventListener('resize', resizeAll)
  timer = setInterval(() => { void load() }, REFRESH_INTERVAL)
})

onUnmounted(() => {
  if (timer) clearInterval(timer)
  timer = null
  window.removeEventListener('resize', resizeAll)
  disposeCharts()
})
</script>

<template>
  <div class="page-head">
    <div>
      <span class="section-kicker">INFRA / SERVER MONITOR</span>
      <h1>服务器监控</h1>
      <p>采集 JVM、系统资源、MySQL 与 Redis 的实时运行指标；每 5 秒自动刷新一次，图表为内存采样，最多保留最近 20 个点。</p>
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
      <div ref="mysqlChartEl" class="chart-box chart-box-sm" data-test="mysql-trend"></div>
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

  <section class="chart-grid">
    <article class="surface-panel chart-card">
      <header class="monitor-card-head"><el-icon><PieChart /></el-icon><h2>JVM 堆内存分布</h2></header>
      <div ref="heapChartEl" class="chart-box"></div>
      <p class="chart-caption">已用 {{ monitor ? formatBytes(monitor.jvm?.heapUsed) : '—' }} / 最大 {{ monitor ? formatBytes(monitor.jvm?.heapMax) : '—' }}</p>
    </article>

    <article class="surface-panel chart-card">
      <header class="monitor-card-head"><el-icon><Cpu /></el-icon><h2>CPU 使用率趋势</h2></header>
      <div ref="cpuChartEl" class="chart-box"></div>
      <p class="chart-caption">当前 {{ monitor ? formatPercent(monitor.system?.systemLoad) : '—' }} · 最近 {{ cpuHistory.length }} 个采样点</p>
    </article>

    <article class="surface-panel chart-card">
      <header class="monitor-card-head"><el-icon><Monitor /></el-icon><h2>物理内存使用率</h2></header>
      <div ref="memGaugeEl" class="chart-box"></div>
      <p class="chart-caption">已用 {{ memUsedPercent.toFixed(1) }}% · {{ monitor ? formatBytes(monitor.system?.physicalMemUsed) : '—' }} / {{ monitor ? formatBytes(monitor.system?.physicalMemTotal) : '—' }}</p>
    </article>

    <article v-if="!redisUnavailable" class="surface-panel chart-card">
      <header class="monitor-card-head"><el-icon><PieChart /></el-icon><h2>Redis 命中分布</h2></header>
      <div ref="redisChartEl" class="chart-box"></div>
      <p class="chart-caption">命中 {{ monitor?.redis?.hits ?? '—' }} 次 / 未命中 {{ monitor?.redis?.misses ?? '—' }} 次</p>
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

.chart-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 14px; margin-top: 14px; }
.chart-card { padding: 18px 20px; }
.chart-box { width: 100%; height: 240px; }
.chart-box-sm { width: 100%; height: 90px; margin-top: 10px; }
.chart-caption { margin: 8px 0 0; color: var(--muted); font-size: 11px; text-align: center; }
@media (max-width: 900px) { .monitor-grid, .chart-grid { grid-template-columns: 1fr; } }
</style>
