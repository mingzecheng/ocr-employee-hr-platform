import { setActivePinia, createPinia } from 'pinia'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { useAuthStore } from './auth'
import * as authApi from '@/api/auth'

vi.mock('@/api/auth', () => ({
  login: vi.fn(),
  getCurrentUser: vi.fn(),
}))

describe('auth store', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
    localStorage.clear()
  })

  it('persists the access token and current user after login', async () => {
    vi.mocked(authApi.login).mockResolvedValue({
      accessToken: 'jwt-token',
      expiresAt: '2026-09-18T00:00:00Z',
    })
    vi.mocked(authApi.getCurrentUser).mockImplementation(async () => {
      expect(localStorage.getItem('hr.access_token')).toBe('jwt-token')
      return {
        id: 1,
        username: 'admin',
        displayName: '系统管理员',
        roles: ['ADMIN'],
        permissions: ['ARCHIVE_READ'],
        employeeId: null,
      }
    })

    const store = useAuthStore()
    await store.login('admin', 'password')

    expect(store.token).toBe('jwt-token')
    expect(store.user?.displayName).toBe('系统管理员')
    expect(localStorage.getItem('hr.access_token')).toBe('jwt-token')
    expect(JSON.parse(localStorage.getItem('hr.current_user')!)).toMatchObject({ id: 1 })
  })

  it('clears the session when the API reports an unauthorized request', () => {
    const store = useAuthStore()
    store.token = 'expired-token'
    store.user = {
      id: 1,
      username: 'admin',
      displayName: '系统管理员',
      roles: ['ADMIN'],
      permissions: [],
      employeeId: null,
    }
    localStorage.setItem('hr.access_token', 'expired-token')

    store.handleUnauthorized()

    expect(store.token).toBeNull()
    expect(store.user).toBeNull()
    expect(localStorage.getItem('hr.access_token')).toBeNull()
  })
})
