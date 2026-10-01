package com.hrplatform.archive;

import java.util.List;

public record ArchiveVersionPage(List<ArchiveVersion> items, long total, int page, int pageSize) {
}
