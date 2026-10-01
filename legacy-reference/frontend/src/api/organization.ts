import { http, unwrap } from './http'
import type { ApiResponse, DepartmentNode } from '@/types/api'

export async function getDepartmentTree(): Promise<DepartmentNode[]> {
  const response = await http.get<ApiResponse<DepartmentNode[]>>('/departments/tree')
  return unwrap(response)
}
