import { AxiosError } from 'axios'
import { ElMessage } from 'element-plus'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { http } from './http'

vi.mock('element-plus', () => ({
  ElMessage: { warning: vi.fn(), error: vi.fn(), success: vi.fn() },
}))

const warningMock = vi.mocked(ElMessage.warning)

afterEach(() => {
  warningMock.mockClear()
})

describe('HTTP error diagnostics', () => {
  it('marks a request without an HTTP response as a connection failure', async () => {
    const adapter = vi.fn().mockRejectedValue(new AxiosError('Network Error', AxiosError.ERR_NETWORK))

    await expect(http.get('/auth/me', { adapter })).rejects.toMatchObject({
      message: 'Network Error',
      code: AxiosError.ERR_NETWORK,
      isNetworkError: true,
    })
    expect(warningMock).not.toHaveBeenCalled()
  })

  it('surfaces a friendly conflict toast and flags the error as already handled on 409', async () => {
    const response = {
      status: 409,
      statusText: 'Conflict',
      headers: {},
      config: { headers: {} },
      data: { code: 'STATE_CONFLICT', message: '请勿重复提交', traceId: 't-1' },
    } as never
    const adapter = vi.fn().mockRejectedValue(
      new AxiosError('Request failed with status code 409', 'ERR_BAD_REQUEST', {} as never, undefined, response),
    )

    await expect(http.get('/hr-requests/33/approve', { adapter })).rejects.toMatchObject({
      status: 409,
      code: 'STATE_CONFLICT',
      handled: true,
    })
    expect(warningMock).toHaveBeenCalledWith('操作正在处理中，请勿重复提交')
  })
})
