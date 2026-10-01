package com.hrplatform.inventory;

import com.hrplatform.common.security.JwtPrincipal;
import com.hrplatform.common.web.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/inventory-tasks")
public class InventoryController {
    private final InventoryService service;

    public InventoryController(InventoryService service) {
        this.service = service;
    }

    @PostMapping
    public ApiResponse<InventoryTask> create(@Valid @RequestBody InventoryCreateRequest request,
                                             Authentication authentication) {
        return ApiResponse.success(service.create(request, actor(authentication)), "unknown");
    }

    @PostMapping("/{id}/start")
    public ApiResponse<InventoryTask> start(@PathVariable Long id, Authentication authentication) {
        return ApiResponse.success(service.start(id, actor(authentication)), "unknown");
    }

    @PostMapping("/{id}/complete")
    public ApiResponse<InventoryTask> complete(@PathVariable Long id, Authentication authentication) {
        return ApiResponse.success(service.complete(id, actor(authentication)), "unknown");
    }

    @PostMapping("/{id}/items/{itemId}/resolve")
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
