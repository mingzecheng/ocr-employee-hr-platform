import { flushPromises, mount } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import ElementPlus, { ElMessage } from 'element-plus'
import { request } from '../api/http'
import AuditLogView from './AuditLogView.vue'

vi.mock('../api/http', () => ({ request: vi.fn() }))
vi.mock('element-plus', async (importOriginal) => {
  const actual = await importOriginal<typeof import('element-plus')>()
  return { ...actual, ElMessage: { error: vi.fn(), success: vi.fn(), warning: vi.fn() } }
})

const requestMock = vi.mocked(request)

function mockList() {
  requestMock.mockImplementation(async (config: { url?: string }) => {
    if (config.url === '/operation-logs') {
      return {
        items: [{
          id: 1,
          operatorId: 3,
          operatorName: '张三',
          action: 'HR_REQUEST_STATUS_CHANGED',
          objectType: 'HR_REQUEST',
          objectId: 8,
          result: 'SUCCESS',
          traceId: 'trace-audit-1',
          clientIpMasked: '192.168.x.x',
          createdAt: '2026-10-12T10:00:00',
        }],
        total: 1,
        page: 1,
        pageSize: 20,
      } as never
    }
    if (config.url === '/operation-logs/1') {
      return {
        id: 1,
        operatorId: 3,
        operatorName: '张三',
        action: 'HR_REQUEST_STATUS_CHANGED',
        objectType: 'HR_REQUEST',
        objectId: 8,
        result: 'SUCCESS',
        createdAt: '2026-10-12T10:00:00',
        traceId: 'trace-audit-1',
        clientIp: '203.0.113.7',
        userAgent: 'Mozilla/5.0 (Macintosh)',
        requestMethod: 'POST',
        requestUri: '/api/hr/requests/8',
        sessionId: 'sess-abc-123',
        beforeSummary: 'PENDING',
        afterSummary: 'APPROVED',
      } as never
    }
    return undefined as never
  })
}

describe('AuditLogView', () => {
  beforeEach(() => {
    requestMock.mockReset()
    mockList()
  })
  afterEach(() => { document.body.innerHTML = '' })

  it('loads the list from /operation-logs and shows the masked IP column', async () => {
    const wrapper = mount(AuditLogView, { attachTo: document.body, global: { plugins: [ElementPlus] } })
    await flushPromises()

    expect(requestMock).toHaveBeenCalledWith(expect.objectContaining({ url: '/operation-logs' }))
    // 列表展示脱敏 IP，不展示完整 IP
    expect(wrapper.text()).toContain('192.168.x.x')
    expect(wrapper.text()).not.toContain('203.0.113.7')
    // 列表已去掉变更前/后列：该行的 before/after 值（PENDING/APPROVED）只在抽屉里出现
    expect(wrapper.text()).not.toContain('PENDING')
    expect(wrapper.text()).not.toContain('APPROVED')
    wrapper.unmount()
  })

  it('opens the detail drawer on click and renders full IP / UA / before-after', async () => {
    const wrapper = mount(AuditLogView, { attachTo: document.body, global: { plugins: [ElementPlus] } })
    await flushPromises()

    const detailBtn = wrapper.find('button[title="查看详情"]')
    expect(detailBtn.exists()).toBe(true)
    await detailBtn.trigger('click')
    await flushPromises()

    expect(requestMock).toHaveBeenCalledWith(expect.objectContaining({ url: '/operation-logs/1' }))
    const body = document.body.textContent || ''
    // 风控指纹（完整不脱敏）
    expect(body).toContain('203.0.113.7')
    expect(body).toContain('Mozilla/5.0 (Macintosh)')
    expect(body).toContain('POST')
    expect(body).toContain('/api/hr/requests/8')
    expect(body).toContain('sess-abc-123')
    // 基本信息 + 变更前后
    expect(body).toContain('trace-audit-1')
    expect(body).toContain('PENDING')
    expect(body).toContain('APPROVED')
    wrapper.unmount()
  })

  it('renders a dash instead of leaking null for legacy log fields', async () => {
    requestMock.mockImplementation(async (config: { url?: string }) => {
      if (config.url === '/operation-logs') {
        return {
          items: [{ id: 2, operatorId: 7, action: 'LOGIN_SUCCESS', objectType: 'AUTH', result: 'SUCCESS', clientIpMasked: null, createdAt: '2026-10-12T10:01:00' }],
          total: 1,
          page: 1,
          pageSize: 20,
        } as never
      }
      if (config.url === '/operation-logs/2') {
        return {
          id: 2,
          operatorId: 7,
          operatorName: null,
          action: 'LOGIN_SUCCESS',
          objectType: 'AUTH',
          objectId: null,
          result: 'SUCCESS',
          createdAt: '2026-10-12T10:01:00',
          traceId: null,
          clientIp: null,
          userAgent: null,
          requestMethod: null,
          requestUri: null,
          sessionId: null,
          beforeSummary: null,
          afterSummary: null,
        } as never
      }
      return undefined as never
    })

    const wrapper = mount(AuditLogView, { attachTo: document.body, global: { plugins: [ElementPlus] } })
    await flushPromises()

    // 列表脱敏 IP 为 null 时显示 “—”
    expect(wrapper.text()).toContain('—')

    await wrapper.find('button[title="查看详情"]').trigger('click')
    await flushPromises()

    const body = document.body.textContent || ''
    // 历史日志新字段全为 NULL：不得渲染字符串 "null"，且占位符 “—” 出现
    expect(body).not.toContain('null')
    expect(body).toContain('client_ip')
    expect(body).toContain('user_agent')
    wrapper.unmount()
  })

  it('surfaces detail load failures via toast and closes the drawer', async () => {
    requestMock.mockImplementation(async (config: { url?: string }) => {
      if (config.url === '/operation-logs') {
        return { items: [{ id: 9, operatorId: 1, action: 'X', objectType: 'Y', result: 'SUCCESS', clientIpMasked: '10.0.0.x', createdAt: '2026-10-12T10:02:00' }], total: 1, page: 1, pageSize: 20 } as never
      }
      throw new Error('没有权限查看该日志')
    })
    const errorMock = vi.mocked(ElMessage.error)

    const wrapper = mount(AuditLogView, { attachTo: document.body, global: { plugins: [ElementPlus] } })
    await flushPromises()
    await wrapper.find('button[title="查看详情"]').trigger('click')
    await flushPromises()

    expect(errorMock).toHaveBeenCalledWith(expect.stringContaining('没有权限查看该日志'))
    wrapper.unmount()
  })
})
