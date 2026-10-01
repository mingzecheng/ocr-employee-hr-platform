package com.hrplatform.employee;

import java.util.List;

public record EmployeePage(List<Employee> items, long total, int page, int pageSize) {
}
