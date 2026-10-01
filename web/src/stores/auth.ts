import { computed, ref } from 'vue'
import { defineStore } from 'pinia'
import { request, type AuthResponse, type AuthUser } from '../api/http'

export const useAuthStore = defineStore('auth', () => {
  const token = ref(sessionStorage.getItem('hr.accessToken') || '')
  const user = ref<AuthUser | null>(JSON.parse(sessionStorage.getItem('hr.user') || 'null'))
  const signedIn = computed(() => Boolean(token.value && user.value))
  const roles = computed(() => user.value?.roles || [])

  async function login(username: string, password: string) {
    const data = await request<AuthResponse>({ url: '/auth/login', method: 'POST', data: { username, password } })
    token.value = data.accessToken
    user.value = data.user
    sessionStorage.setItem('hr.accessToken', data.accessToken)
    sessionStorage.setItem('hr.user', JSON.stringify(data.user))
  }
  function logout() {
    token.value = ''
    user.value = null
    sessionStorage.removeItem('hr.accessToken')
    sessionStorage.removeItem('hr.user')
  }
  function canAccess(allowed: string[]) { return allowed.length === 0 || roles.value.some((role) => allowed.includes(role)) }
  return { token, user, signedIn, roles, login, logout, canAccess }
})
