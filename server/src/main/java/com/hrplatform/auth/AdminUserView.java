package com.hrplatform.auth;

import java.util.Set;

public record AdminUserView(Long id, String username, Long employeeId, Long departmentId,
                            boolean enabled, Set<String> roleCodes) {
}
