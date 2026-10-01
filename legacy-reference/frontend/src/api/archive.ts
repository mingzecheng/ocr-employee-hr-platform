import { http, unwrap } from './http'
import type {
  ApiResponse,
  ArchiveDocument,
  ArchiveVersion,
  DocumentUpload,
  Employee,
  OcrResult,
  PageData,
} from '@/types/api'

export async function listEmployees(page = 1, pageSize = 20): Promise<PageData<Employee>> {
  const response = await http.get<ApiResponse<PageData<Employee> | Employee[]>>('/employees', { params: { page, pageSize } })
  const data = unwrap(response)
  if (Array.isArray(data)) return { items: data, total: data.length, page, pageSize }
  return data
}

export async function createEmployee(input: { employeeNo: string; name: string; idCardNo?: string; departmentId?: number | null }): Promise<Employee> {
  const response = await http.post<ApiResponse<Employee>>('/employees', input)
  return unwrap(response)
}

export async function uploadDocument(
  employeeId: number,
  file: File,
  documentType = 'employee_profile',
): Promise<DocumentUpload> {
  const form = new FormData()
  form.append('file', file)
  form.append('documentType', documentType)
  const response = await http.post<ApiResponse<DocumentUpload>>(
    `/archive/employees/${employeeId}/documents`,
    form,
  )
  return unwrap(response)
}

export async function listArchiveDocuments(employeeId: number): Promise<ArchiveDocument[]> {
  const response = await http.get<ApiResponse<ArchiveDocument[]>>(
    `/archive/employees/${employeeId}/documents`,
  )
  return unwrap(response)
}

export async function listArchiveVersions(documentId: number): Promise<ArchiveVersion[]> {
  const response = await http.get<ApiResponse<ArchiveVersion[]>>(
    `/archive/documents/${documentId}/versions`,
  )
  return unwrap(response)
}

export async function getVersionFile(versionId: number): Promise<Blob> {
  const response = await http.get(`/archive/versions/${versionId}/download`, {
    responseType: 'blob',
  })
  return response.data
}

export async function runOcr(versionId: number, documentType = 'employee_profile'): Promise<OcrResult> {
  const response = await http.post<ApiResponse<OcrResult>>(
    `/archive/versions/${versionId}/ocr`,
    null,
    { params: { documentType } },
  )
  return unwrap(response)
}

export async function getOcrResult(versionId: number): Promise<OcrResult> {
  const response = await http.get<ApiResponse<OcrResult>>(`/archive/versions/${versionId}/ocr-result`)
  return unwrap(response)
}

export async function getDetectionPreview(bindingId: number): Promise<Blob> {
  const response = await http.get(`/archive/ocr-bindings/${bindingId}/detection-preview`, {
    responseType: 'blob',
  })
  return response.data
}

export async function correctField(
  bindingId: number,
  fieldCode: string,
  value: string,
  reason?: string,
): Promise<OcrResult> {
  const response = await http.put<ApiResponse<OcrResult>>(
    `/archive/ocr-bindings/${bindingId}/fields/${encodeURIComponent(fieldCode)}`,
    { value, reason },
  )
  return unwrap(response)
}
