import { http, unwrap } from './http'
import type { ApiResponse, StatisticsOverview } from '@/types/api'

export async function getStatisticsOverview(): Promise<StatisticsOverview> {
  const response = await http.get<ApiResponse<StatisticsOverview>>('/statistics/overview')
  return unwrap(response)
}
