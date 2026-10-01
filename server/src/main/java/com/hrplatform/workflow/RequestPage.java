package com.hrplatform.workflow;

import java.util.List;

public record RequestPage(List<HrRequest> items, long total, int page, int pageSize) {
}
