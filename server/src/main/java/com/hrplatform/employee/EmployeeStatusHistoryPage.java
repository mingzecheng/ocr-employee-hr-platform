package com.hrplatform.employee;

import java.util.List;

public record EmployeeStatusHistoryPage(List<EmployeeStatusHistoryView> items, long total, int page, int pageSize) {
}
