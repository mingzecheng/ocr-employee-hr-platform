package com.hrplatform.identity.auth;

import java.util.List;

public record CurrentUserData(long id,
                              String username,
                              List<String> roles,
                              List<String> permissions,
                              Long employeeId,
                              Long departmentId) {
}
