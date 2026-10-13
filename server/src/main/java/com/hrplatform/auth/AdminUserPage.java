package com.hrplatform.auth;

import java.util.List;

public record AdminUserPage(List<AdminUserView> items, long total, int page, int pageSize) {
}
