package com.hrplatform.contract;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record ContractCreateRequest(@NotNull @Positive Long employeeId,
                                    @NotBlank @Size(max = 32) String contractType,
                                    @NotNull LocalDate signedOn,
                                    @NotNull LocalDate startsOn,
                                    @NotNull LocalDate endsOn,
                                    Long sourceVersionId,
                                    Long renewedFromContractId,
                                    @Size(max = 512) String notes) {
}
