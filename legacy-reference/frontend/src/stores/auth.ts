import { computed, ref } from 'vue'
import { defineStore } from 'pinia'
import * as authApi from '@/api/auth'
import type { CurrentUser } from '@/types/api'

const TOKEN_KEY = 'hr.access_token'
const USER_KEY = 'hr.current_user'
let unauthorizedListenerBound = false

export const useAuthStore = defineStore('auth', () => {
  const token = ref<string | null>(localStorage.getItem(TOKEN_KEY))
  const storedUser = localStorage.getItem(USER_KEY)
  const user = ref<CurrentUser | null>(storedUser ? JSON.parse(storedUser) : null)
  const isAuthenticated = computed(() => Boolean(token.value))

  function persist() {
    if (token.value) localStorage.setItem(TOKEN_KEY, token.value)
    else localStorage.removeItem(TOKEN_KEY)
    if (user.value) localStorage.setItem(USER_KEY, JSON.stringify(user.value))
    else localStorage.removeItem(USER_KEY)
  }

  function handleUnauthorized() {
    token.value = null
    user.value = null
    persist()
  }

  async function login(username: string, password: string) {
    const result = await authApi.login(username, password)
    token.value = result.accessToken
    persist()
    user.value = await authApi.getCurrentUser()
    persist()
  }

  function logout() {
    handleUnauthorized()
  }

  if (!unauthorizedListenerBound && typeof window !== 'undefined') {
    window.addEventListener('hr:unauthorized', handleUnauthorized)
    unauthorizedListenerBound = true
  }

  return { token, user, isAuthenticated, login, logout, handleUnauthorized }
})
