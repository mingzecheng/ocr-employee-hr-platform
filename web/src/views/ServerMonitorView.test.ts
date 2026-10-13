import { flushPromises, mount } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import ElementPlus, { ElMessage } from 'element-plus'
import { request } from '../api/http'
import ServerMonitorView from './ServerMonitorView.vue'

vi.mock('../api/http', () => ({ request: vi.fn() }))
vi.mock('element-plus', async (importOriginal) => {
  const actual = await importOriginal<typeof import('element-plus')>()
  return { ...actual, ElMessage: { error: vi.fn(), success: vi.fn(), warning: vi.fn() } }
})

const requestMock = vi.mocked(request)

const baseMonitor = {
  jvm: { heapUsed: 268435456, heapMax: 1073741824, nonHeapUsed: 134217728, threadCount: 42, classLoaded: 12000, gcCount: 30, gcTimeMs: 120 },
  system: { cpuCores: 8, systemLoad: 0.35, physicalMemTotal: 17179869184, physicalMemUsed: 8589934592, diskTotal: 250790426624, diskFree: 107374182400 },
  mysql: { threadsConnected: 12, uptimeSec: 86400 },
  redis: { usedMemory: 104857600, connectedClients: 5, hits: 9000, misses: 100 },
  app: { startTime: '2026-10-13T08:00:00', uptimeMs: 3600000, version: '1.0.0' },
}

function monitorPayload() {
  return { ...baseMonitor } as never
}

describe('ServerMonitorView', () => {
  beforeEach(() => {
    requestMock.mockReset()
    requestMock.mockResolvedValue(monitorPayload())
  })
  afterEach(() => {
    vi.useRealTimers()
    document.body.innerHTML = ''
  })

  it('loads /admin/monitor once on mount and renders all five metric cards', async () => {
    const wrapper = mount(ServerMonitorView, { attachTo: document.body, global: { plugins: [ElementPlus] } })
    await flushPromises()

    expect(requestMock).toHaveBeenCalledTimes(1)
    expect(requestMock).toHaveBeenCalledWith(expect.objectContaining({ url: '/admin/monitor' }))

    const text = wrapper.text()
    expect(text).toContain('JVM 虚拟机')
    expect(text).toContain('系统资源')
    expect(text).toContain('MySQL 数据库')
    expect(text).toContain('Redis 缓存')
    expect(text).toContain('应用运行时')
    // 版本号与线程数等具体指标已渲染
    expect(text).toContain('1.0.0')
    expect(text).toContain('42')
    wrapper.unmount()
  })

  it('refreshes automatically every 5 seconds', async () => {
    vi.useFakeTimers()
    const wrapper = mount(ServerMonitorView, { attachTo: document.body, global: { plugins: [ElementPlus] } })
    await flushPromises()
    expect(requestMock).toHaveBeenCalledTimes(1)

    await vi.advanceTimersByTimeAsync(5000)
    await flushPromises()
    expect(requestMock).toHaveBeenCalledTimes(2)

    await vi.advanceTimersByTimeAsync(5000)
    await flushPromises()
    expect(requestMock).toHaveBeenCalledTimes(3)
    wrapper.unmount()
  })

  it('shows Redis offline hint when redis payload is null', async () => {
    requestMock.mockResolvedValue({ ...baseMonitor, redis: null } as never)
    const wrapper = mount(ServerMonitorView, { attachTo: document.body, global: { plugins: [ElementPlus] } })
    await flushPromises()
    expect(wrapper.text()).toContain('Redis 未连接')
    wrapper.unmount()
  })

  it('stops the interval after unmount', async () => {
    vi.useFakeTimers()
    const wrapper = mount(ServerMonitorView, { attachTo: document.body, global: { plugins: [ElementPlus] } })
    await flushPromises()
    expect(requestMock).toHaveBeenCalledTimes(1)
    wrapper.unmount()

    await vi.advanceTimersByTimeAsync(10000)
    await flushPromises()
    expect(requestMock).toHaveBeenCalledTimes(1)
  })

  it('surfaces load failures via toast', async () => {
    requestMock.mockRejectedValue(new Error('没有权限查看监控'))
    const errorMock = vi.mocked(ElMessage.error)
    mount(ServerMonitorView, { attachTo: document.body, global: { plugins: [ElementPlus] } })
    await flushPromises()
    expect(errorMock).toHaveBeenCalledWith(expect.stringContaining('没有权限查看监控'))
  })
})
