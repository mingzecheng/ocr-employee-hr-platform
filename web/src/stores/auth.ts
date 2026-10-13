import { computed, ref } from 'vue'
import { defineStore } from 'pinia'
import { request, type AuthResponse, type AuthUser, type RegisterInput } from '../api/http'

export const useAuthStore = defineStore('auth', () => {
  const token = ref(sessionStorage.getItem('hr.accessToken') || '')
  const user = ref<AuthUser | null>(JSON.parse(sessionStorage.getItem('hr.user') || 'null'))
  // refresh token 生命周期 7 天，放在 localStorage 以实现"7天免登录"。
  const refreshToken = ref(localStorage.getItem('hr.refreshToken') || '')
  const signedIn = computed(() => Boolean(token.value && user.value))
  const roles = computed(() => user.value?.roles || [])

  async function login(username: string, password: string, rememberMe = true) {
    const data = await request<AuthResponse>({
      url: '/auth/login',
      method: 'POST',
      data: { username, password, rememberMe },
    })
    persist(data)
  }
  async function register(input: RegisterInput) {
    const data = await request<AuthResponse>({ url: '/auth/register', method: 'POST', data: input })
    persist(data)
  }
  function persist(data: AuthResponse) {
    token.value = data.accessToken
    user.value = data.user
    sessionStorage.setItem('hr.accessToken', data.accessToken)
    sessionStorage.setItem('hr.user', JSON.stringify(data.user))
    if (data.refreshToken) {
      refreshToken.value = data.refreshToken
      localStorage.setItem('hr.refreshToken', data.refreshToken)
    } else {
      refreshToken.value = ''
      localStorage.removeItem('hr.refreshToken')
    }
  }
  function logout() {
    token.value = ''
    user.value = null
    refreshToken.value = ''
    sessionStorage.removeItem('hr.accessToken')
    sessionStorage.removeItem('hr.user')
    localStorage.removeItem('hr.refreshToken')
  }
  function canAccess(allowed: string[]) { return allowed.length === 0 || roles.value.some((role) => allowed.includes(role)) }
  return { token, user, refreshToken, signedIn, roles, login, register, logout, canAccess }
})
