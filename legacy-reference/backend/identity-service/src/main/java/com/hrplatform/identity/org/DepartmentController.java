package com.hrplatform.identity.org;

import com.hrplatform.common.api.ApiResponse;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/departments")
public class DepartmentController {

    private final DepartmentService departmentService;

    public DepartmentController(DepartmentService departmentService) {
        this.departmentService = departmentService;
    }

    @GetMapping("/tree")
    @PreAuthorize("hasAuthority('PERM_ORG_READ')")
    public ApiResponse<List<DepartmentNode>> tree() {
        return ApiResponse.success(departmentService.findActiveTree(), com.hrplatform.common.api.TraceId.current());
    }
}
