import axios, { AxiosError } from 'axios'
import { ElMessage } from 'element-plus'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { authSession, http } from './http'

vi.mock('element-plus', () => ({
  ElMessage: { warning: vi.fn(), error: vi.fn(), success: vi.fn() },
}))

function rejectWith401(config: unknown) {
  return new AxiosError('Request failed with status code 401', 'ERR_BAD_REQUEST', config as never, null, {
    status: 401,
    statusText: 'Unauthorized',
    headers: {},
    config,
    data: { code: 'UNAUTHORIZED', message: '登录已过期', traceId: 't-401' },
  } as never)
}

beforeEach(() => {
  localStorage.clear()
  sessionStorage.clear()
})

afterEach(() => {
  vi.restoreAllMocks()
})

describe('401 silent refresh', () => {
  it('refreshes the access token and retries the original request', async () => {
    localStorage.setItem('hr.refreshToken', 'old-refresh')
    sessionStorage.setItem('hr.accessToken', 'old-access')

    const refreshPost = vi.spyOn(axios, 'post').mockResolvedValue({
      data: { code: '0', message: 'ok', data: { accessToken: 'new-access', refreshToken: 'rotated-refresh' }, traceId: 'tr' },
    })

    let calls = 0
    const adapter = vi.fn().mockImplementation(async (config) => {
      calls += 1
      if (calls === 1) throw rejectWith401(config)
      return {
        data: { code: '0', message: 'ok', data: { done: true }, traceId: 't-ok' },
        status: 200, statusText: 'OK', headers: {}, config,
      }
    })

    const res = await http.get('/employees', { adapter })
    expect(refreshPost).toHaveBeenCalledWith('/api/auth/refresh', { refreshToken: 'old-refresh' }, expect.anything())
    expect(calls).toBe(2)
    expect((adapter.mock.calls[1][0] as { headers: { Authorization?: string } }).headers.Authorization).toBe('Bearer new-access')
    expect(sessionStorage.getItem('hr.accessToken')).toBe('new-access')
    expect(localStorage.getItem('hr.refreshToken')).toBe('rotated-refresh')
    expect((res.data as { data: { done: boolean } }).data).toEqual({ done: true })
  })

  it('clears local session and redirects to login when refresh also fails', async () => {
    localStorage.setItem('hr.refreshToken', 'bad-refresh')
    sessionStorage.setItem('hr.accessToken', 'old-access')
    sessionStorage.setItem('hr.user', '{"id":1}')

    vi.spyOn(axios, 'post').mockRejectedValue(new AxiosError('refresh failed', 'ERR_BAD_REQUEST'))
    const redirectSpy = vi.spyOn(authSession, 'redirectToLogin').mockImplementation(() => {})

    const adapter = vi.fn().mockImplementation(async (config) => {
      throw rejectWith401(config)
    })

    await expect(http.get('/employees', { adapter })).rejects.toMatchObject({ status: 401 })
    expect(localStorage.getItem('hr.refreshToken')).toBeNull()
    expect(sessionStorage.getItem('hr.accessToken')).toBeNull()
    expect(sessionStorage.getItem('hr.user')).toBeNull()
    expect(redirectSpy).toHaveBeenCalled()
  })

  it('does not attempt refresh when there is no stored refresh token', async () => {
    sessionStorage.setItem('hr.accessToken', 'old-access')
    const refreshPost = vi.spyOn(axios, 'post')
    const redirectSpy = vi.spyOn(authSession, 'redirectToLogin').mockImplementation(() => {})
    const adapter = vi.fn().mockImplementation(async (config) => {
      throw rejectWith401(config)
    })

    await expect(http.get('/employees', { adapter })).rejects.toMatchObject({ status: 401 })
    expect(refreshPost).not.toHaveBeenCalled()
    expect(redirectSpy).toHaveBeenCalled()
  })
})
