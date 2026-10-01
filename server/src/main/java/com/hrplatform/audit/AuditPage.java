package com.hrplatform.audit;

import java.util.List;

public record AuditPage(List<OperationLogRecord> items, long total, int page, int pageSize) {
}
