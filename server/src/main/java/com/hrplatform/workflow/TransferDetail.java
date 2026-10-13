package com.hrplatform.workflow;

import java.time.LocalDate;

public record TransferDetail(String requestNo, Long fromDepartmentId, Long fromPositionId,
                             LocalDate effectiveDate, String reason) {
}
