package com.hrplatform.workflow.inventory;

import com.hrplatform.common.api.ApiResponse;
import com.hrplatform.common.api.TraceId;
import com.hrplatform.workflow.security.WorkflowActor;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class InventoryController {
    private final InventoryService service;

    public InventoryController(InventoryService service) {
        this.service = service;
    }

    @PostMapping("/api/inventory-tasks")
    @PreAuthorize("hasAuthority('PERM_INVENTORY_WRITE')")
    public ApiResponse<InventoryDtos.TaskData> create(@Valid @RequestBody InventoryDtos.CreateRequest request,
                                                      Authentication authentication) {
        return ApiResponse.success(service.create(request, actor(authentication)), TraceId.current());
    }

    @PostMapping("/api/inventory-tasks/{id}/start")
    @PreAuthorize("hasAuthority('PERM_INVENTORY_WRITE')")
    public ApiResponse<InventoryDtos.TaskData> start(@PathVariable long id, Authentication authentication) {
        return ApiResponse.success(service.start(id, actor(authentication)), TraceId.current());
    }

    @PostMapping("/api/inventory-tasks/{id}/items/{itemId}/check")
    @PreAuthorize("hasAuthority('PERM_INVENTORY_WRITE')")
    public ApiResponse<InventoryDtos.ItemData> check(@PathVariable long id, @PathVariable long itemId,
                                                     @Valid @RequestBody InventoryDtos.CheckRequest request,
                                                     Authentication authentication) {
        return ApiResponse.success(service.checkItem(id, itemId, actor(authentication), request),
                TraceId.current());
    }

    @PostMapping("/api/inventory-tasks/{id}/complete")
    @PreAuthorize("hasAuthority('PERM_INVENTORY_WRITE')")
    public ApiResponse<InventoryDtos.TaskData> complete(@PathVariable long id, Authentication authentication) {
        return ApiResponse.success(service.complete(id, actor(authentication)), TraceId.current());
    }

    @PostMapping("/api/inventory-tasks/{id}/resolve")
    @PreAuthorize("hasAuthority('PERM_INVENTORY_WRITE')")
    public ApiResponse<InventoryDtos.TaskData> resolve(@PathVariable long id,
                                                       @Valid @RequestBody InventoryDtos.ResolveRequest request,
                                                       Authentication authentication) {
        return ApiResponse.success(service.resolve(id, actor(authentication), request), TraceId.current());
    }

    @GetMapping("/api/inventory-tasks/{id}")
    @PreAuthorize("hasAuthority('PERM_INVENTORY_READ')")
    public ApiResponse<InventoryDtos.TaskData> get(@PathVariable long id, Authentication authentication) {
        return ApiResponse.success(service.get(id, actor(authentication)), TraceId.current());
    }

    private WorkflowActor actor(Authentication authentication) {
        return WorkflowActor.from(authentication);
    }
}
