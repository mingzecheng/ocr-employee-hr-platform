import { http, unwrap } from './http'
import type { ApiResponse, TodoData } from '@/types/api'

export async function getTodos(limit = 50): Promise<TodoData> {
  const response = await http.get<ApiResponse<TodoData>>('/todos', { params: { limit } })
  return unwrap(response)
}
