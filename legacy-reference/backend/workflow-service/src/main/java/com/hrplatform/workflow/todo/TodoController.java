package com.hrplatform.workflow.todo;

import com.hrplatform.common.api.ApiResponse;
import com.hrplatform.common.api.TraceId;
import com.hrplatform.workflow.security.WorkflowActor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class TodoController {
    private final TodoService service;

    public TodoController(TodoService service) { this.service = service; }

    @GetMapping("/api/todos")
    @PreAuthorize("hasAnyAuthority('PERM_HR_REQUEST_READ','PERM_ARCHIVE_READ')")
    public ApiResponse<TodoDtos.TodoData> list(@RequestParam(defaultValue = "50") int limit,
                                               Authentication authentication) {
        if (limit < 1) throw new IllegalArgumentException("limit must be positive");
        return ApiResponse.success(service.list(WorkflowActor.from(authentication), Math.min(limit, 100)),
                TraceId.current());
    }
}
