package com.hrplatform.workflow;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public sealed interface RequestCreateRequest
        permits RequestCreateRequest.Leave, RequestCreateRequest.Onboarding,
        RequestCreateRequest.Transfer, RequestCreateRequest.Offboarding {
    String requestType();

    Long employeeId();

    Long sourceVersionId();

    record Leave(@NotNull @Positive Long employeeId, Long sourceVersionId,
                 @NotBlank String leaveType, @NotNull LocalDate leaveStartDate,
                 @NotNull LocalDate leaveEndDate, @NotBlank @Size(max = 512) String reason)
            implements RequestCreateRequest {
        @Override
        public String requestType() {
            return "LEAVE";
        }
    }

    record Onboarding(@NotNull @Positive Long employeeId, Long sourceVersionId,
                      @NotNull @Positive Long targetDepartmentId, @NotNull @Positive Long targetPositionId,
                      @NotNull LocalDate effectiveDate, @NotBlank @Size(max = 32) String contractType)
            implements RequestCreateRequest {
        @Override
        public String requestType() {
            return "ONBOARDING";
        }
    }

    record Transfer(@NotNull @Positive Long employeeId, Long sourceVersionId,
                    @NotNull @Positive Long targetDepartmentId, @NotNull @Positive Long targetPositionId,
                    @NotNull LocalDate effectiveDate, @NotBlank @Size(max = 512) String reason)
            implements RequestCreateRequest {
        @Override
        public String requestType() {
            return "TRANSFER";
        }
    }

    record Offboarding(@NotNull @Positive Long employeeId, Long sourceVersionId,
                       @NotNull LocalDate leaveDate, @NotBlank @Size(max = 128) String handoverPerson,
                       @Size(max = 512) String reason)
            implements RequestCreateRequest {
        @Override
        public String requestType() {
            return "OFFBOARDING";
        }
    }
}
