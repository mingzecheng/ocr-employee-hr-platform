package com.hrplatform.workflow;

import java.time.LocalDate;

public record OnboardingDetail(String requestNo, LocalDate hireDate, String contractType) {
}
