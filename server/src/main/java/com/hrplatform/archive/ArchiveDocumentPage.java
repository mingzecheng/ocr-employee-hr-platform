package com.hrplatform.archive;

import java.util.List;

public record ArchiveDocumentPage(List<ArchiveDocument> items, long total, int page, int pageSize) {
}
