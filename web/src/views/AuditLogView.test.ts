import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import ElementPlus from 'element-plus'
import { request } from '../api/http'
import AuditLogView from './AuditLogView.vue'

vi.mock('../api/http', () => ({ request: vi.fn() }))

const requestMock = vi.mocked(request)

beforeEach(() => {
  requestMock.mockResolvedValue({
    items: [{
      id: 1,
      action: 'HR_REQUEST_STATUS_CHANGED',
      objectType: 'HR_REQUEST',
      objectId: 8,
      result: 'SUCCESS',
      traceId: 'trace-audit-1',
      beforeSummary: 'PENDING',
      afterSummary: 'APPROVED',
      createdAt: '2026-10-12T10:00:00',
    }],
    total: 1,
    page: 1,
    pageSize: 20,
  } as never)
})

describe('AuditLogView', () => {
  it('shows before and after summaries for an audit record', async () => {
    const wrapper = mount(AuditLogView, { global: { plugins: [ElementPlus] } })
    await flushPromises()

    expect(wrapper.text()).toContain('变更前')
    expect(wrapper.text()).toContain('变更后')
    expect(wrapper.text()).toContain('PENDING')
    expect(wrapper.text()).toContain('APPROVED')
    expect(requestMock).toHaveBeenCalledWith(expect.objectContaining({ url: '/operation-logs' }))
  })

  it('uses a dash when an audit summary is empty', async () => {
    requestMock.mockResolvedValueOnce({
      items: [{ id: 2, action: 'LOGIN_SUCCESS', objectType: 'AUTH', result: 'SUCCESS', traceId: 'trace-audit-2', createdAt: '2026-10-12T10:01:00' }],
      total: 1,
      page: 1,
      pageSize: 20,
    } as never)
    const wrapper = mount(AuditLogView, { global: { plugins: [ElementPlus] } })
    await flushPromises()

    expect(wrapper.findAll('.audit-summary').map((item) => item.text())).toEqual(['—', '—'])
  })
})
