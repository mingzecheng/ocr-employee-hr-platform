import { http, unwrap } from './http'
import type {
  ApiResponse,
  ArchiveAccessApplication,
  ArchiveUseRecord,
  HrRequest,
  InventoryItem,
  InventoryTask,
} from '@/types/api'

export interface HrRequestInput {
  requestType: string
  employeeId: number
  payload: Record<string, unknown>
}

export async function createHrRequest(input: HrRequestInput): Promise<HrRequest> {
  const response = await http.post<ApiResponse<HrRequest>>('/hr-requests', input)
  return unwrap(response)
}

export async function getHrRequest(id: number): Promise<HrRequest> {
  const response = await http.get<ApiResponse<HrRequest>>(`/hr-requests/${id}`)
  return unwrap(response)
}

export async function submitHrRequest(id: number): Promise<HrRequest> {
  const response = await http.post<ApiResponse<HrRequest>>(`/hr-requests/${id}/submit`)
  return unwrap(response)
}

export async function approveHrRequest(id: number, comment = ''): Promise<HrRequest> {
  const response = await http.post<ApiResponse<HrRequest>>(`/approvals/${id}/approve`, { comment })
  return unwrap(response)
}

export async function rejectHrRequest(id: number, comment = ''): Promise<HrRequest> {
  const response = await http.post<ApiResponse<HrRequest>>(`/approvals/${id}/reject`, { comment })
  return unwrap(response)
}

export interface ArchiveAccessInput {
  employeeId: number
  useType: string
  purpose: string
  startAt: string
  dueAt: string
  returnRequired: boolean
  items: Array<{ archiveDocumentId: number; archiveVersionId: number | null; scope: string }>
}

export async function createArchiveAccess(input: ArchiveAccessInput): Promise<ArchiveAccessApplication> {
  const response = await http.post<ApiResponse<ArchiveAccessApplication>>('/archive-access-applications', input)
  return unwrap(response)
}

export async function getArchiveAccess(id: number): Promise<ArchiveAccessApplication> {
  const response = await http.get<ApiResponse<ArchiveAccessApplication>>(`/archive-access-applications/${id}`)
  return unwrap(response)
}

export async function submitArchiveAccess(id: number): Promise<ArchiveAccessApplication> {
  const response = await http.post<ApiResponse<ArchiveAccessApplication>>(`/archive-access-applications/${id}/submit`)
  return unwrap(response)
}

export async function approveArchiveAccess(id: number, comment = ''): Promise<ArchiveAccessApplication> {
  const response = await http.post<ApiResponse<ArchiveAccessApplication>>(`/archive-access-applications/${id}/approve`, { comment })
  return unwrap(response)
}

export async function rejectArchiveAccess(id: number, comment = ''): Promise<ArchiveAccessApplication> {
  const response = await http.post<ApiResponse<ArchiveAccessApplication>>(`/archive-access-applications/${id}/reject`, { comment })
  return unwrap(response)
}

export async function checkoutArchiveAccess(id: number): Promise<ArchiveUseRecord> {
  const response = await http.post<ApiResponse<ArchiveUseRecord>>(`/archive-access-applications/${id}/checkout`)
  return unwrap(response)
}

export interface ArchiveReturnInput {
  returnType: string
  handoverToId: number | null
  missingDescription: string
  damageDescription: string
}

export async function returnArchiveUse(id: number, input: ArchiveReturnInput): Promise<ArchiveUseRecord> {
  const response = await http.post<ApiResponse<ArchiveUseRecord>>(`/archive-uses/${id}/return`, input)
  return unwrap(response)
}

export interface InventoryInput {
  scopeType: string
  scopeValue: string
  items: Array<{ archiveDocumentId: number; expectedVersionId: number }>
}

export async function createInventory(input: InventoryInput): Promise<InventoryTask> {
  const response = await http.post<ApiResponse<InventoryTask>>('/inventory-tasks', input)
  return unwrap(response)
}

export async function getInventory(id: number): Promise<InventoryTask> {
  const response = await http.get<ApiResponse<InventoryTask>>(`/inventory-tasks/${id}`)
  return unwrap(response)
}

export async function startInventory(id: number): Promise<InventoryTask> {
  const response = await http.post<ApiResponse<InventoryTask>>(`/inventory-tasks/${id}/start`)
  return unwrap(response)
}

export interface InventoryCheckInput {
  actualVersionId: number | null
  actualStatus: string
  differenceDescription: string
}

export async function checkInventoryItem(taskId: number, itemId: number, input: InventoryCheckInput): Promise<InventoryItem> {
  const response = await http.post<ApiResponse<InventoryItem>>(`/inventory-tasks/${taskId}/items/${itemId}/check`, input)
  return unwrap(response)
}

export async function completeInventory(id: number): Promise<InventoryTask> {
  const response = await http.post<ApiResponse<InventoryTask>>(`/inventory-tasks/${id}/complete`)
  return unwrap(response)
}

export async function resolveInventory(id: number, resolution: string): Promise<InventoryTask> {
  const response = await http.post<ApiResponse<InventoryTask>>(`/inventory-tasks/${id}/resolve`, { resolution })
  return unwrap(response)
}
