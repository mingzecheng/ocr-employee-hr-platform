package com.hrplatform.workflow;

import java.time.LocalDate;

public record OffboardingDetail(String requestNo, LocalDate leaveDate, String handoverPerson,
                                String handoverStatus, String reason) {
}
