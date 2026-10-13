package com.hrplatform.workflow;

import java.time.LocalDate;

public record LeaveRequestDetail(Long requestId, String requestNo, String leaveType,
                                 LocalDate startDate, LocalDate endDate, String reason) {
}
