package com.hrplatform.organization;

public record DepartmentAdminView(Long id, String code, String name, Long parentId,
                                 Long managerEmployeeId, Integer sortOrder, Boolean enabled) {
}
