package com.hrplatform.access;

import java.util.List;

public record AccessPage(List<ArchiveAccessApplication> items, long total, int page, int pageSize) {
}
