import { http, unwrap } from './http'
import type { ApiResponse, CurrentUser, LoginData } from '@/types/api'

export async function login(username: string, password: string): Promise<LoginData> {
  const response = await http.post<ApiResponse<LoginData>>('/auth/login', { username, password })
  return unwrap(response)
}

export async function getCurrentUser(): Promise<CurrentUser> {
  const response = await http.get<ApiResponse<CurrentUser>>('/auth/me')
  const user = unwrap(response)
  return { ...user, displayName: user.username }
}
