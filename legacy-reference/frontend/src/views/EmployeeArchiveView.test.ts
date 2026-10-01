import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import EmployeeArchiveView from './EmployeeArchiveView.vue'

const { listEmployees, listArchiveDocuments, listArchiveVersions, getVersionFile, getOcrResult, uploadDocument, runOcr } = vi.hoisted(() => ({
  listEmployees: vi.fn(),
  listArchiveDocuments: vi.fn(),
  listArchiveVersions: vi.fn(),
  getVersionFile: vi.fn(),
  getOcrResult: vi.fn(),
  uploadDocument: vi.fn(),
  runOcr: vi.fn(),
}))

vi.mock('@/api/archive', () => ({
  listEmployees,
  listArchiveDocuments,
  listArchiveVersions,
  getVersionFile,
  getOcrResult,
  correctField: vi.fn(),
  runOcr,
  uploadDocument,
}))

vi.mock('vue-router', () => ({
  useRoute: () => ({ params: { id: '7' } }),
  useRouter: () => ({ push: vi.fn() }),
}))

vi.mock('@/components/OcrResultPanel.vue', () => ({
  default: { props: ['result'], template: '<div data-test="ocr-result-panel">{{ result?.textBlocks?.[0]?.text }}</div>' },
}))

const employee = {
  id: 7,
  employeeNo: 'E-007',
  name: '张三',
  idCardNo: null,
  departmentId: 12,
  status: 'ACTIVE',
}

const document = {
  id: 21,
  employeeId: 7,
  documentType: 'EMPLOYEE_PROFILE',
  title: '员工登记表',
  status: 'ACTIVE',
  latestVersionId: 31,
  latestVersionNo: 1,
  latestFileName: 'profile.png',
  latestContentType: 'image/png',
  latestSize: 2048,
  createdAt: '2026-09-18T14:00:00',
}

const version = {
  id: 31,
  documentId: 21,
  versionNo: 1,
  status: 'DRAFT',
  originalName: 'profile.png',
  contentType: 'image/png',
  sizeBytes: 2048,
  sha256: 'a'.repeat(64),
  createdBy: 9,
  createdAt: '2026-09-18T14:00:00',
}

function mountView() {
  return mount(EmployeeArchiveView, {
    global: {
      stubs: {
        'el-button': { emits: ['click'], template: '<button @click="$emit(\'click\')"><slot /></button>' },
        'el-icon': true,
      },
    },
  })
}

describe('EmployeeArchiveView', () => {
  beforeEach(() => {
    listEmployees.mockResolvedValue({ items: [employee], total: 1, page: 1, pageSize: 100 })
    listArchiveDocuments.mockResolvedValue([])
    listArchiveVersions.mockResolvedValue([version])
    getVersionFile.mockResolvedValue(new Blob(['image'], { type: 'image/png' }))
    URL.createObjectURL = vi.fn(() => 'blob:profile-preview')
    URL.revokeObjectURL = vi.fn()
  })

  it('shows an explicit empty state when the employee has no archive materials', async () => {
    const wrapper = mountView()

    await flushPromises()

    expect(listArchiveDocuments).toHaveBeenCalledWith(7)
    expect(wrapper.text()).toContain('暂无档案材料')
  })

  it('loads versions on demand and previews the protected original file', async () => {
    listArchiveDocuments.mockResolvedValue([document])
    const wrapper = mountView()

    await flushPromises()
    await wrapper.get('[data-test="document-21"] [data-test="show-versions"]').trigger('click')
    await flushPromises()

    expect(listArchiveVersions).toHaveBeenCalledWith(21)
    expect(wrapper.text()).toContain('版本 1')

    await wrapper.get('[data-test="version-31"] [data-test="preview-version"]').trigger('click')
    await flushPromises()

    expect(getVersionFile).toHaveBeenCalledWith(31)
    expect(URL.createObjectURL).toHaveBeenCalled()
    expect(wrapper.find('[data-test="version-preview"]').attributes('src')).toBe('blob:profile-preview')
  })

  it('passes the selected material type to upload and OCR requests', async () => {
    uploadDocument.mockResolvedValue({ employeeId: 7, documentId: 21, versionId: 31, objectKey: 'archive/profile.png', sha256: 'a'.repeat(64), size: 10, status: 'DRAFT' })
    runOcr.mockResolvedValue({ bindingId: 31, versionId: 31, taskId: 'task-1', status: 'SUCCEEDED', engineVersion: null, sourceFile: null, detectionPreview: null, textBlocks: [], fields: [], errorMessage: null })
    const wrapper = mountView()

    await flushPromises()
    await wrapper.get('[data-test="document-type"]').setValue('TRANSFER_FORM')
    const file = new File(['image'], 'transfer.png', { type: 'image/png' })
    const fileInput = wrapper.find('input[type="file"]')
    Object.defineProperty(fileInput.element, 'files', { configurable: true, value: [file] })
    await fileInput.trigger('change')
    await wrapper.get('[data-test="recognize-button"]').trigger('click')
    await flushPromises()

    expect(uploadDocument).toHaveBeenCalledWith(7, expect.any(File), 'TRANSFER_FORM')
    expect(runOcr).toHaveBeenCalledWith(31, 'TRANSFER_FORM')
  })

  it('opens an existing version OCR result from the version history', async () => {
    listArchiveDocuments.mockResolvedValue([document])
    getOcrResult.mockResolvedValue({ bindingId: 31, versionId: 31, taskId: 'task-old', status: 'SUCCEEDED', engineVersion: 'paddleocr-local', sourceFile: { originalName: 'profile.png' }, detectionPreview: null, textBlocks: [{ text: '历史识别', confidence: 0.9, bbox: [] }], fields: [], errorMessage: null })
    const wrapper = mountView()

    await flushPromises()
    await wrapper.get('[data-test="document-21"] [data-test="show-versions"]').trigger('click')
    await flushPromises()
    await wrapper.get('[data-test="version-31"] [data-test="view-ocr"]').trigger('click')
    await flushPromises()

    expect(getOcrResult).toHaveBeenCalledWith(31)
    expect(wrapper.get('[data-test="ocr-result-panel"]').text()).toContain('历史识别')
  })

  it('starts OCR for a visible version when no previous result exists', async () => {
    listArchiveDocuments.mockResolvedValue([document])
    getOcrResult.mockRejectedValue({ response: { status: 403 } })
    runOcr.mockClear()
    runOcr.mockResolvedValue({ bindingId: 31, versionId: 31, taskId: 'task-new', status: 'SUCCEEDED', engineVersion: 'paddleocr-local', sourceFile: { originalName: 'profile.png' }, detectionPreview: null, textBlocks: [{ text: '新识别', confidence: 0.95, bbox: [] }], fields: [], errorMessage: null })
    const wrapper = mountView()

    await flushPromises()
    await wrapper.get('[data-test="document-21"] [data-test="show-versions"]').trigger('click')
    await flushPromises()
    await wrapper.get('[data-test="version-31"] [data-test="view-ocr"]').trigger('click')
    await flushPromises()

    expect(runOcr).toHaveBeenCalledWith(31, 'EMPLOYEE_PROFILE')
    expect(wrapper.get('[data-test="ocr-result-panel"]').text()).toContain('新识别')
  })
})
