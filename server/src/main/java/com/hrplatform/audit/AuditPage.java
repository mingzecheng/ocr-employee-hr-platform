package com.hrplatform.audit;

import java.util.List;

public record AuditPage(List<OperationLogListItem> items, long total, int page, int pageSize) {
}
