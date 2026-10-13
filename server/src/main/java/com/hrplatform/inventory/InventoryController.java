package com.hrplatform.inventory;

import com.hrplatform.common.security.JwtPrincipal;
import com.hrplatform.common.web.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/inventory-tasks")
public class InventoryController {
    private final InventoryService service;

    public InventoryController(InventoryService service) {
        this.service = service;
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('DEPT_MANAGER','HR_ADMIN')")
    public ApiResponse<InventoryTask> create(@Valid @RequestBody InventoryCreateRequest request,
                                             Authentication authentication) {
        return ApiResponse.success(service.create(request, actor(authentication)), "unknown");
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('DEPT_MANAGER','HR_ADMIN')")
    public ApiResponse<InventoryPage> list(@RequestParam(defaultValue = "1") int page,
                                           @RequestParam(defaultValue = "20") int pageSize,
                                           Authentication authentication) {
        return ApiResponse.success(service.list(page, pageSize, actor(authentication)), "unknown");
    }

    @PostMapping("/{id}/start")
    @PreAuthorize("hasAnyRole('DEPT_MANAGER','HR_ADMIN')")
    public ApiResponse<InventoryTask> start(@PathVariable Long id, Authentication authentication) {
        return ApiResponse.success(service.start(id, actor(authentication)), "unknown");
    }

    @GetMapping("/{id}/items")
    @PreAuthorize("hasAnyRole('DEPT_MANAGER','HR_ADMIN')")
    public ApiResponse<List<InventoryItem>> items(@PathVariable Long id, Authentication authentication) {
        return ApiResponse.success(service.listItems(id, actor(authentication)), "unknown");
    }

    @GetMapping("/{id}/items/{itemId}/versions")
    @PreAuthorize("hasAnyRole('DEPT_MANAGER','HR_ADMIN')")
    public ApiResponse<List<InventoryVersionOption>> versions(@PathVariable Long id, @PathVariable Long itemId,
                                                               Authentication authentication) {
        return ApiResponse.success(service.availableVersions(id, itemId, actor(authentication)), "unknown");
    }

    @PostMapping("/{id}/items/{itemId}/check")
    @PreAuthorize("hasAnyRole('DEPT_MANAGER','HR_ADMIN')")
    public ApiResponse<InventoryItem> check(@PathVariable Long id, @PathVariable Long itemId,
                                             @Valid @RequestBody InventoryCheckRequest request,
                                             Authentication authentication) {
        return ApiResponse.success(service.checkItem(id, itemId, request, actor(authentication)), "unknown");
    }

    @PostMapping("/{id}/complete")
    @PreAuthorize("hasAnyRole('DEPT_MANAGER','HR_ADMIN')")
    public ApiResponse<InventoryTask> complete(@PathVariable Long id, Authentication authentication) {
        return ApiResponse.success(service.complete(id, actor(authentication)), "unknown");
    }

    @PostMapping("/{id}/items/{itemId}/resolve")
    @PreAuthorize("hasAnyRole('DEPT_MANAGER','HR_ADMIN')")
    public ApiResponse<InventoryItem> resolve(@PathVariable Long id, @PathVariable Long itemId,
                                              @Valid @RequestBody InventoryResolveRequest request,
                                              Authentication authentication) {
        return ApiResponse.success(service.resolve(id, itemId, request.resolution(), actor(authentication)), "unknown");
    }

    private InventoryActor actor(Authentication authentication) {
        JwtPrincipal principal = (JwtPrincipal) authentication.getPrincipal();
        return new InventoryActor(principal.userId(), principal.dataScope());
    }
}
