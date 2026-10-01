export interface ApiResponse<T> {
  code: string
  message: string
  data: T | null
  traceId?: string
}

export interface LoginData {
  accessToken: string
  expiresAt: string
}

export interface CurrentUser {
  id: number
  username: string
  displayName: string
  roles: string[]
  permissions: string[]
  employeeId: number | null
}

export interface Employee {
  id: number
  employeeNo: string
  name: string
  idCardNo: string | null
  departmentId: number | null
  status: string
}

export interface PageData<T> {
  items: T[]
  total: number
  page: number
  pageSize: number
}

export interface DocumentUpload {
  employeeId: number
  documentId: number
  versionId: number
  objectKey: string
  sha256: string
  size: number
  status: string
}

export interface ArchiveDocument {
  id: number
  employeeId: number
  documentType: string
  title: string
  status: string
  latestVersionId: number | null
  latestVersionNo: number | null
  latestFileName: string | null
  latestContentType: string | null
  latestSize: number | null
  createdAt: string | null
}

export interface ArchiveVersion {
  id: number
  documentId: number
  versionNo: number
  status: string
  originalName: string
  contentType: string
  sizeBytes: number
  sha256: string
  createdBy: number | null
  createdAt: string | null
}

export interface DetectionPreview {
  url: string | null
  contentType: string | null
  size: number | null
  sha256: string | null
  width: number | null
  height: number | null
}

export interface TextBlock {
  text: string
  confidence: number
  bbox: number[][]
  pageNo?: number
  blockIndex?: number
}

export interface FieldResult {
  fieldCode: string
  value: string | null
  confidence: number | null
  bbox: number[][] | null
  pageNo?: number
  validationStatus?: string
}

export interface OcrResult {
  bindingId: number
  versionId: number
  taskId: string
  status: string
  engineVersion: string | null
  sourceFile: Record<string, unknown> | null
  detectionPreview: DetectionPreview | null
  textBlocks: TextBlock[]
  fields: FieldResult[]
  errorMessage: string | null
}

export interface StatisticsOverview {
  activeEmployeeCount: number
  archiveDocumentCount: number
  ocrTaskCount: number
  ocrSucceededCount: number
  ocrFailedCount: number
  ocrSuccessRate: number
  archiveCompletenessRate: number
  pendingReviewCount: number
}

export type TodoType =
  | 'HR_REQUEST_APPROVAL'
  | 'ARCHIVE_ACCESS_APPROVAL'
  | 'ARCHIVE_RETURN_DUE'
  | 'OCR_FAILED'

export type TodoPriority = 'HIGH' | 'MEDIUM' | 'LOW'

export interface TodoItem {
  id: number
  type: TodoType
  title: string
  resourceId: number
  status: string
  priority: TodoPriority
  dueAt: string | null
  createdAt: string | null
  targetPath: string
}

export interface TodoData {
  items: TodoItem[]
  total: number
  pendingApprovalCount: number
  dueSoonCount: number
  overdueCount: number
  ocrFailedCount: number
  generatedAt: string
}

export interface DepartmentNode {
  id: number
  parentId: number | null
  code: string
  name: string
  sortNo: number
  active: boolean
}

export interface HrRequest {
  id: number
  requestNo: string
  requestType: 'ONBOARDING' | 'TRANSFER' | 'RESIGNATION' | string
  employeeId: number
  applicantId: number
  payload: Record<string, unknown>
  status: string
  currentNode: string
}

export interface ArchiveAccessItem {
  id: number
  archiveDocumentId: number
  archiveVersionId: number | null
  scope: string
}

export interface ArchiveUseRecord {
  id: number
  applicationId: number
  receiverId: number
  checkedOutAt: string
  dueAt: string
  returnedAt: string | null
  returnType: string | null
  handoverToId: number | null
  missingDescription: string | null
  damageDescription: string | null
  status: string
}

export interface ArchiveAccessApplication {
  id: number
  applicationNo: string
  applicantId: number
  employeeId: number
  useType: string
  purpose: string
  startAt: string
  dueAt: string
  returnRequired: boolean
  status: string
  currentNode: string
  items: ArchiveAccessItem[]
  approvals: Array<{
    id: number
    nodeCode: string
    approverId: number
    decision: string
    comment: string | null
    decidedAt: string
  }>
  use: ArchiveUseRecord | null
}

export interface InventoryItem {
  id: number
  archiveDocumentId: number
  expectedVersionId: number
  actualVersionId: number | null
  actualStatus: string | null
  differenceType: string | null
  differenceDescription: string | null
  checkedBy: number | null
  checkedAt: string | null
}

export interface InventoryTask {
  id: number
  taskNo: string
  scopeType: string
  scopeValue: string | null
  initiatorId: number
  status: string
  startedAt: string | null
  completedAt: string | null
  items: InventoryItem[]
  resolutions: Array<{ id: number; operatorId: number; resolution: string; createdAt: string }>
}
