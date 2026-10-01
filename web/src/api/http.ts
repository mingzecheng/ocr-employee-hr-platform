import axios, { type AxiosRequestConfig } from 'axios'

export interface ApiResponse<T> { code: string; message: string; data: T; traceId: string }
export interface Page<T> { items: T[]; total: number; page: number; pageSize: number }
export interface AuthUser { id: number; username: string; employeeId?: number | null; departmentId?: number | null; roles: string[]; dataScope: string }
export interface AuthResponse { accessToken: string; expiresAt: string; user: AuthUser }
export interface Employee { id: number; employeeNo: string; name: string; phone?: string; departmentId: number; positionId: number; status: string; hireDate?: string; leaveDate?: string }
export interface DepartmentNode { id: number; code: string; name: string; children?: DepartmentNode[] }
export interface ArchiveDocument { id: number; employeeId: number; documentType: string; title: string }
export interface ArchiveVersion { id: number; documentId: number; versionNo: number; originalName: string; contentType: string; size: number; sha256: string; status: string; isCurrent: boolean; createdAt: string; createdBy?: number; changeReason?: string }
export interface BusinessField { fieldCode: string; value: string; confidence: number; bbox?: number[]; pageNo: number; validationStatus: string; reviewRequired: boolean; validationMessage?: string }
export interface OcrResult { bindingId: number; versionId: number; taskId?: string; status: string; documentType: string; engineVersion?: string; processingDurationMs: number; reviewRequired: boolean; errorCode?: string; errorMessage?: string; previewUrl?: string; textBlocks: Array<{ text: string; confidence: number; bbox: number[]; pageNo: number }>; rawFields: Array<{ fieldCode: string; value: string; confidence: number }>; fields: BusinessField[]; revisions: Array<{ id: number; fieldCode: string; previousValue: string; currentValue: string; reason?: string; createdAt: string }> }
export interface HrRequest { id: number; requestNo: string; requestType: string; employeeId: number; targetDepartmentId?: number; targetPositionId?: number; applicantId: number; status: string; currentNode?: string; versionNo: number; createdAt: string; submittedAt?: string; completedAt?: string }
export interface AccessApplication { id: number; applicationNo: string; applicantId: number; departmentId?: number; purpose: string; useType: string; startAt: string; dueAt: string; status: string; versionNo: number; createdAt: string }
export interface StatisticsOverview { employeeCount: number; completeArchiveCount: number; archiveCount: number; archiveCompleteness: number; workflowCount: number; pendingWorkflowCount: number; activeAccessCount: number; abnormalAccessCount: number; ocrSucceededCount: number; ocrFailedCount: number; ocrReviewCount: number; averageOcrDurationMs: number; generatedAt: string }
export interface OperationLog { id: number; operatorId?: number; action: string; objectType: string; objectId?: number; result: string; traceId: string; createdAt: string }
export interface InventoryTask { id: number; taskNo: string; status: string; initiatorId?: number }

export const http = axios.create({ baseURL: import.meta.env.VITE_API_BASE_URL || '/api', timeout: 15000 })

http.interceptors.request.use((config) => {
  const token = sessionStorage.getItem('hr.accessToken')
  if (token) config.headers.Authorization = `Bearer ${token}`
  return config
})

http.interceptors.response.use((response) => response, (error) => {
  if (error.response?.status === 401) {
    sessionStorage.removeItem('hr.accessToken')
    sessionStorage.removeItem('hr.user')
  }
  const payload = error.response?.data as Partial<ApiResponse<never>> | undefined
  const normalized = new Error(payload?.message || error.message || '网络请求失败') as Error & { code?: string; status?: number; traceId?: string }
  normalized.code = payload?.code
  normalized.status = error.response?.status
  normalized.traceId = payload?.traceId
  return Promise.reject(normalized)
})

export async function request<T>(config: AxiosRequestConfig): Promise<T> {
  const response = await http.request<ApiResponse<T>>(config)
  if (response.data.code !== '0') throw Object.assign(new Error(response.data.message), { code: response.data.code, traceId: response.data.traceId })
  return response.data.data
}
