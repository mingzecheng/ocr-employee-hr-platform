package com.hrplatform.contract;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record ContractRecord(Long id, String contractNo, Long employeeId, String contractType,
                            LocalDate signedOn, LocalDate startsOn, LocalDate endsOn,
                            String status, String renewalStatus, Long sourceVersionId,
                            Long renewedFromContractId, String notes, int versionNo,
                            Long createdBy, LocalDateTime createdAt, LocalDateTime updatedAt) {
}
