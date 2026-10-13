package com.hrplatform.employee;

import java.util.List;

public record EmployeeSummaryPage(List<EmployeeView> items, long total, int page, int pageSize) {
}
