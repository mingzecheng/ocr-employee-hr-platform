import { createPinia, setActivePinia } from 'pinia'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import * as httpModule from '../api/http'
import { useAuthStore } from './auth'

vi.mock('element-plus', () => ({
  ElMessage: { warning: vi.fn(), error: vi.fn(), success: vi.fn() },
}))

const fakeUser = { id: 1, username: 'u', roles: ['EMPLOYEE'], dataScope: 'SELF' }

beforeEach(() => {
  setActivePinia(createPinia())
  localStorage.clear()
  sessionStorage.clear()
})

describe('auth store refresh token', () => {
  it('persists refresh token to localStorage after login with rememberMe', async () => {
    vi.spyOn(httpModule, 'request').mockResolvedValue({
      accessToken: 'access-1', expiresAt: '', refreshToken: 'refresh-1', user: fakeUser,
    })
    const auth = useAuthStore()
    await auth.login('u', 'p', true)

    expect(httpModule.request).toHaveBeenCalledWith(
      expect.objectContaining({ data: { username: 'u', password: 'p', rememberMe: true } }),
    )
    expect(localStorage.getItem('hr.refreshToken')).toBe('refresh-1')
    expect(auth.refreshToken).toBe('refresh-1')
  })

  it('login without rememberMe stores no refresh token', async () => {
    vi.spyOn(httpModule, 'request').mockResolvedValue({
      accessToken: 'access-1', expiresAt: '', refreshToken: undefined, user: fakeUser,
    })
    const auth = useAuthStore()
    await auth.login('u', 'p', false)

    expect(localStorage.getItem('hr.refreshToken')).toBeNull()
    expect(auth.refreshToken).toBe('')
  })

  it('logout clears the refresh token from localStorage', async () => {
    vi.spyOn(httpModule, 'request').mockResolvedValue({
      accessToken: 'access-1', expiresAt: '', refreshToken: 'refresh-1', user: fakeUser,
    })
    const auth = useAuthStore()
    await auth.login('u', 'p', true)
    expect(localStorage.getItem('hr.refreshToken')).toBe('refresh-1')

    auth.logout()
    expect(localStorage.getItem('hr.refreshToken')).toBeNull()
    expect(auth.refreshToken).toBe('')
    expect(auth.signedIn).toBe(false)
  })
})
