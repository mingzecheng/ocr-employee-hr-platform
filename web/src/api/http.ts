import axios, { type AxiosRequestConfig } from 'axios'
import { ElMessage } from 'element-plus'

export interface ApiResponse<T> { code: string; message: string; data: T; traceId: string }
export interface HttpError extends Error {
  code?: string
  status?: number
  traceId?: string
  /** 拦截器已对该错误弹出过友好提示，视图层应跳过自己的通用错误 toast，避免重复弹层。 */
  handled?: boolean
  isNetworkError: boolean
  isTimeout: boolean
}
export interface Page<T> { items: T[]; total: number; page: number; pageSize: number }
export interface AuthUser { id: number; username: string; employeeId?: number | null; departmentId?: number | null; roles: string[]; dataScope: string }
export interface AuthResponse { accessToken: string; expiresAt: string; refreshToken?: string; user: AuthUser }
export interface RegisterInput { password: string; name: string; phone: string }
export interface Employee { id: number; employeeNo: string; name: string; phone?: string; departmentId: number; positionId: number; status: string; hireDate?: string; leaveDate?: string; leaveStartDate?: string; leaveEndDate?: string }
export interface EmployeeStatusHistory {
  id: number; employeeId: number; beforeStatus?: string; afterStatus: string
  beforeDepartmentId?: number; beforeDepartmentName?: string; afterDepartmentId?: number; afterDepartmentName?: string
  beforePositionId?: number; beforePositionName?: string; afterPositionId?: number; afterPositionName?: string
  sourceRequestId?: number; requestNo?: string; operatorId?: number; operatorName?: string; createdAt: string
}
export interface DepartmentNode { id: number; code: string; name: string; children?: DepartmentNode[] }
export interface PositionOption { id: number; departmentId: number; code: string; name: string }
export interface DepartmentAdmin { id: number; code: string; name: string; parentId?: number | null; managerEmployeeId?: number | null; sortOrder: number; enabled: boolean }
export interface PositionAdmin { id: number; departmentId: number; code: string; name: string; enabled: boolean }
export interface AdminRoleOption { code: string; name: string }
export interface AdminUser { id: number; username: string; employeeId?: number | null; departmentId?: number | null; enabled: boolean; roleCodes: string[] }
export interface ArchiveDocument { id: number; employeeId: number; documentType: string; title: string }
export interface ArchiveVersion { id: number; documentId: number; versionNo: number; originalName: string; contentType: string; size: number; sha256: string; status: string; isCurrent: boolean; createdAt: string; createdBy?: number; changeReason?: string }
export interface BusinessField { fieldCode: string; value: string; confidence: number; bbox?: number[]; pageNo: number; validationStatus: string; reviewRequired: boolean; validationMessage?: string }
export interface OcrResult { bindingId: number; versionId: number; taskId?: string; status: string; documentType: string; engineVersion?: string; processingDurationMs: number; reviewRequired: boolean; errorCode?: string; errorMessage?: string; previewUrl?: string; confirmStatus?: 'PENDING' | 'CONFIRMED' | 'REJECTED'; confirmNote?: string; confirmedAt?: string; textBlocks: Array<{ text: string; confidence: number; bbox: number[]; pageNo: number }>; rawFields: Array<{ fieldCode: string; value: string; confidence: number }>; fields: BusinessField[]; revisions: Array<{ id: number; fieldCode: string; previousValue: string; currentValue: string; reason?: string; createdAt: string }> }
export interface OcrConfirmResult { bindingId: number; confirmStatus: 'CONFIRMED' | 'REJECTED'; employeeId?: number | null; employeeUpdated: boolean }
export interface HrRequest { id: number; requestNo: string; requestType: string; employeeId: number; targetDepartmentId?: number; approvalDepartmentId?: number; targetPositionId?: number; applicantId: number; status: string; currentNode?: string; versionNo: number; createdAt: string; submittedAt?: string; completedAt?: string; employeeChangeAppliedAt?: string; leaveType?: string; leaveStartDate?: string; leaveEndDate?: string; leaveReason?: string; effectiveDate?: string; contractType?: string; leaveDate?: string; handoverPerson?: string; handoverStatus?: string; reason?: string }
export type HrRequestCreate =
  | { requestType: 'LEAVE'; employeeId: number; sourceVersionId?: number; leaveType: 'ANNUAL' | 'SICK' | 'PERSONAL' | 'OTHER'; leaveStartDate: string; leaveEndDate: string; reason: string }
  | { requestType: 'ONBOARDING'; employeeId: number; sourceVersionId?: number; targetDepartmentId: number; targetPositionId: number; effectiveDate: string; contractType: 'FIXED_TERM' | 'OPEN_ENDED' | 'INTERN' }
  | { requestType: 'TRANSFER'; employeeId: number; sourceVersionId?: number; targetDepartmentId: number; targetPositionId: number; effectiveDate: string; reason: string }
  | { requestType: 'OFFBOARDING'; employeeId: number; sourceVersionId?: number; leaveDate: string; handoverPerson: string; reason?: string }
export interface ApprovalRecord { id: number; requestId: number; nodeCode: string; approverId: number; approverName?: string; decision: string; comment?: string; decidedAt: string }
export interface RequestDisplayContext { employeeNo?: string; employeeName?: string; employeeDepartmentId?: number; employeeDepartmentName?: string; employeePositionId?: number; employeePositionName?: string; applicantName?: string; approvalDepartmentName?: string; targetDepartmentName?: string; targetPositionName?: string }
export interface RequestDetail { request: HrRequest; display?: RequestDisplayContext; approvals: ApprovalRecord[] }
export interface ContractRecord { id: number; contractNo: string; employeeId: number; contractType: string; signedOn: string; startsOn: string; endsOn: string; status: 'ACTIVE' | 'EXPIRED'; renewalStatus: 'NOT_DUE' | 'DUE' | 'RENEWED' | 'NOT_RENEWING'; sourceVersionId?: number; renewedFromContractId?: number; notes?: string; versionNo: number; createdBy: number; createdAt: string; updatedAt: string }
export interface AccessApplication { id: number; applicationNo: string; applicantId: number; departmentId?: number; purpose: string; useType: string; startAt: string; dueAt: string; status: string; versionNo: number; createdAt: string }
export interface ArchiveAccessItem { id: number; applicationId: number; versionId: number; employeeId: number; departmentId: number; status: string }
export interface ArchiveUseRecord { id: number; applicationId: number; operatorId: number; operatorName?: string; usedAt: string; returnedAt?: string; returnStatus?: string; remark?: string; returnedByUserId?: number; returnedByName?: string; handoverToEmployeeId?: number; handoverToEmployeeNo?: string; handoverToEmployeeName?: string }
export interface ArchiveAccessDetailItem extends ArchiveAccessItem { versionNo: number; employeeNo?: string; employeeName?: string; departmentName?: string; documentTitle?: string }
export interface ArchiveAccessResolution { id: number; applicationId: number; resolvedByUserId: number; resolvedByName?: string; resolutionNote: string; resolvedAt: string }
export interface ArchiveAccessDecision { id: number; applicationId: number; decidedByUserId: number; decidedByName?: string; decision: 'APPROVED' | 'REJECTED'; decisionNote?: string; decidedAt: string }
export interface ArchiveAccessDetail { application: AccessApplication; applicantName?: string; departmentName?: string; items: ArchiveAccessDetailItem[]; uses: ArchiveUseRecord[]; resolutions: ArchiveAccessResolution[]; decisions: ArchiveAccessDecision[] }
export interface StatisticsOverview { employeeCount: number; completeArchiveCount: number; archiveCount: number; archiveCompleteness: number; workflowCount: number; pendingWorkflowCount: number; activeAccessCount: number; abnormalAccessCount: number; ocrSucceededCount: number; ocrFailedCount: number; ocrReviewCount: number; averageOcrDurationMs: number; generatedAt: string }
export interface StatisticsTrendPoint { period: string; employeeCount: number; workflowCount: number; ocrSucceededCount: number; ocrFailedCount: number }
export interface DepartmentStatisticsRow { departmentId: number; departmentName: string; employeeCount: number; archiveCount: number; workflowCount: number; pendingWorkflowCount: number; abnormalAccessCount: number }
export interface WorkflowCycleStatistics { completedCount: number; averageDurationHours: number; shortestDurationHours: number; longestDurationHours: number }
export interface StatisticsReport { monthlyTrend: StatisticsTrendPoint[]; departments: DepartmentStatisticsRow[]; workflowCycle: WorkflowCycleStatistics }
export type TodoType = 'HR_REQUEST_APPROVAL' | 'ARCHIVE_ACCESS_APPROVAL' | 'ARCHIVE_RETURN_DUE' | 'OCR_FAILED'
export type TodoPriority = 'HIGH' | 'MEDIUM' | 'LOW'
export interface TodoItem { id: number; type: TodoType; title: string; resourceId: number; status: string; priority: TodoPriority; dueAt?: string | null; createdAt?: string | null; targetPath: string }
export interface TodoData { items: TodoItem[]; total: number; pendingApprovalCount: number; dueSoonCount: number; overdueCount: number; ocrFailedCount: number; generatedAt: string }
export interface OperationLog {
  id: number
  operatorId?: number | null
  operatorName?: string | null
  action: string
  objectType: string
  objectId?: number | null
  result: string
  traceId?: string | null
  beforeSummary?: string | null
  afterSummary?: string | null
  createdAt: string
  /** 列表返回的脱敏 IP（如 192.168.x.x），完整 IP 仅在详情接口下发。 */
  clientIpMasked?: string | null
}
/** GET /api/audit/{id} 单条审计日志完整信息（含风控指纹），仅 SYSTEM_ADMIN 可访问。 */
export interface AuditLogDetail {
  id: number
  operatorId?: number | null
  operatorName?: string | null
  action: string
  objectType: string
  objectId?: number | null
  result: string
  createdAt: string
  traceId?: string | null
  clientIp?: string | null
  userAgent?: string | null
  requestMethod?: string | null
  requestUri?: string | null
  sessionId?: string | null
  beforeSummary?: string | null
  afterSummary?: string | null
}
export interface InventoryTask { id: number; taskNo: string; status: string; initiatorId?: number }
export interface InventoryItem {
  id: number; taskId: number; expectedVersionId: number; expectedVersionNo: number
  actualVersionId: number | null
  employeeId: number; departmentId: number; employeeNo?: string; employeeName?: string; documentTitle?: string
  actualStatus: string | null; differenceType: string | null
  differenceDescription: string | null; checkedBy: number | null; resolutionStatus: string; resolution: string | null
}
export interface InventoryVersionOption { id: number; versionNo: number; status: string; isCurrent: boolean }

/** GET /api/admin/monitor 服务器运行指标，仅 SYSTEM_ADMIN 可访问。 */
export interface ServerMonitor {
  jvm: {
    heapUsed: number
    heapMax: number
    nonHeapUsed: number
    threadCount: number
    classLoaded: number
    gcCount: number
    gcTimeMs: number
  }
  system: {
    cpuCores: number
    systemLoad: number
    physicalMemTotal: number
    physicalMemUsed: number
    diskTotal: number
    diskFree: number
  }
  mysql: {
    threadsConnected: number
    uptimeSec: number
  }
  /** Redis 不可用时后端返回 null，前端展示“Redis 未连接”。 */
  redis: {
    usedMemory: number
    connectedClients: number
    hits: number
    misses: number
  } | null
  app: {
    startTime: string
    uptimeMs: number
    version: string
  }
}

/** GET /api/admin/online-users 扫 Redis 刷新令牌得到的近似在线用户。 */
export interface OnlineUser {
  userId: number
  username: string
  roles: string[]
  /** 近似登录时间（≈ refresh token 创建时间）。 */
  loginApprox: string
}

/** GET /api/admin/roles 角色 × 模块 权限矩阵（只读，固定角色注解）。 */
export interface RolePermissionMatrix {
  roles: string[]
  modules: string[]
  matrix: Record<string, Record<string, boolean>>
}

/** GET /api/admin/login-logs 登录/登出/刷新相关日志条目。 */
export interface LoginLogItem {
  id: number
  username: string
  action: string
  clientIp?: string | null
  userAgent?: string | null
  result: string
  createdAt: string
}

export const http = axios.create({ baseURL: import.meta.env.VITE_API_BASE_URL || '/api', timeout: 15000 })

http.interceptors.request.use((config) => {
  const token = sessionStorage.getItem('hr.accessToken')
  if (token) config.headers.Authorization = `Bearer ${token}`
  return config
})

/** 登录态彻底失效后的跳转钩子，挂在对象上便于测试替换。 */
export const authSession = {
  redirectToLogin(): void {
    if (typeof window === 'undefined' || !window.location) return
    try {
      sessionStorage.setItem('hr.portalReturnTo', window.location.href)
      window.location.assign('/#/login')
    } catch {
      /* jsdom / 测试环境可能拦截真实跳转，忽略即可 */
    }
  },
}

function clearLocalSession(): void {
  sessionStorage.removeItem('hr.accessToken')
  sessionStorage.removeItem('hr.user')
  localStorage.removeItem('hr.refreshToken')
}

interface RefreshResult { accessToken: string; refreshToken?: string }

let refreshPromise: Promise<string | null> | null = null

/**
 * 用独立的 axios 实例刷新令牌，避免再次走进本响应拦截器造成递归。
 * 并发的 401 会共享同一个刷新 Promise（单飞）。
 */
function refreshAccessToken(): Promise<string | null> {
  if (refreshPromise) return refreshPromise
  const refreshToken = localStorage.getItem('hr.refreshToken')
  if (!refreshToken) return Promise.resolve(null)
  refreshPromise = (async () => {
    try {
      const resp = await axios.post<ApiResponse<RefreshResult>>(
        `${http.defaults.baseURL || ''}/auth/refresh`,
        { refreshToken },
        { timeout: http.defaults.timeout },
      )
      const body = resp.data
      if (!body || body.code !== '0' || !body.data?.accessToken) return null
      sessionStorage.setItem('hr.accessToken', body.data.accessToken)
      if (body.data.refreshToken) localStorage.setItem('hr.refreshToken', body.data.refreshToken)
      else localStorage.removeItem('hr.refreshToken')
      return body.data.accessToken
    } catch {
      return null
    } finally {
      refreshPromise = null
    }
  })()
  return refreshPromise
}

http.interceptors.response.use((response) => response, async (error) => {
  const originalRequest = error.config as (typeof error.config & { _retried?: boolean }) | undefined
  const status = error.response?.status as number | undefined
  // 登录/注册接口自身的 401 属于账号密码错误，应由视图展示内联错误，不能触发静默刷新或跳登录。
  const isAuthMutation = /\/auth\/(login|register)/.test(originalRequest?.url || '')

  if (status === 401 && !isAuthMutation) {
    if (originalRequest && !originalRequest._retried) {
      const nextToken = await refreshAccessToken()
      if (nextToken) {
        originalRequest._retried = true
        originalRequest.headers.Authorization = `Bearer ${nextToken}`
        return http.request(originalRequest)
      }
    }
    // refresh 也失败（或已重试过仍 401）：清掉本地登录态并跳登录页。
    clearLocalSession()
    authSession.redirectToLogin()
  }

  // 审批幂等命中（STATE_CONFLICT）：统一弹出友好提示，避免视图层展示裸 409。
  const isConflict = status === 409
  if (isConflict) {
    ElMessage.warning('操作正在处理中，请勿重复提交')
  }
  const payload = error.response?.data as Partial<ApiResponse<never>> | undefined
  const axiosError = axios.isAxiosError(error) ? error : undefined
  const isTimeout = axiosError?.code === 'ECONNABORTED' || axiosError?.code === 'ETIMEDOUT'
  const normalized = new Error(payload?.message || error.message || '网络请求失败') as HttpError
  normalized.code = payload?.code || axiosError?.code
  normalized.status = status
  normalized.traceId = payload?.traceId && payload.traceId !== 'unknown'
    ? payload.traceId
    : axiosError?.response?.headers?.['x-trace-id']
  normalized.handled = isConflict
  normalized.isTimeout = Boolean(isTimeout)
  normalized.isNetworkError = Boolean(axiosError && !axiosError.response && !isTimeout)
  return Promise.reject(normalized)
})

export async function request<T>(config: AxiosRequestConfig): Promise<T> {
  const response = await http.request<ApiResponse<T>>(config)
  if (response.data.code !== '0') throw Object.assign(new Error(response.data.message), { code: response.data.code, traceId: response.data.traceId })
  return response.data.data
}
