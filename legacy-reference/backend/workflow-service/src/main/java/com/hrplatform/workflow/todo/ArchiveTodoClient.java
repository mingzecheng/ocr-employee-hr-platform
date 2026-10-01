package com.hrplatform.workflow.todo;

import java.util.List;

public interface ArchiveTodoClient {
    List<TodoDtos.OcrFailureItem> listFailedOcr(String bearerToken, int limit);
}
