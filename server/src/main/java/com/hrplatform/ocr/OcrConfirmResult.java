package com.hrplatform.ocr;

/**
 * OCR 结果人工确认/拒绝的返回结果。
 *
 * @param bindingId       ocr_binding 主键
 * @param confirmStatus   确认后状态：CONFIRMED / REJECTED
 * @param employeeId      关联员工 id（未回填员工时为 null）
 * @param employeeUpdated 是否实际写入 employee 主档
 */
public record OcrConfirmResult(Long bindingId, String confirmStatus, Long employeeId,
                               boolean employeeUpdated) {
}
