package com.hrplatform.workflow;

import com.hrplatform.workflow.access.ArchiveAccessController;
import com.hrplatform.workflow.inventory.InventoryController;
import com.hrplatform.workflow.request.RequestController;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;

import java.lang.reflect.Method;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

class PermissionGuardTest {
    @Test
    void workflowEndpointsDeclareCreateReadWriteAndApprovalAuthorities() {
        assertAuthority(RequestController.class, "create", "PERM_HR_REQUEST_CREATE");
        assertAuthority(RequestController.class, "submit", "PERM_HR_REQUEST_CREATE");
        assertAuthority(RequestController.class, "approve", "PERM_HR_REQUEST_APPROVE");
        assertAuthority(RequestController.class, "reject", "PERM_HR_REQUEST_APPROVE");
        assertAuthority(RequestController.class, "get", "PERM_HR_REQUEST_READ");

        assertAuthority(ArchiveAccessController.class, "create", "PERM_ARCHIVE_ACCESS_CREATE");
        assertAuthority(ArchiveAccessController.class, "submit", "PERM_ARCHIVE_ACCESS_CREATE");
        assertAuthority(ArchiveAccessController.class, "approve", "PERM_ARCHIVE_ACCESS_APPROVE");
        assertAuthority(ArchiveAccessController.class, "reject", "PERM_ARCHIVE_ACCESS_APPROVE");
        assertAuthority(ArchiveAccessController.class, "checkout", "PERM_ARCHIVE_ACCESS_CHECKOUT");
        assertAuthority(ArchiveAccessController.class, "get", "PERM_ARCHIVE_READ");
        assertAuthority(ArchiveAccessController.class, "returnUse", "PERM_ARCHIVE_ACCESS_RETURN");

        assertAuthority(InventoryController.class, "create", "PERM_INVENTORY_WRITE");
        assertAuthority(InventoryController.class, "start", "PERM_INVENTORY_WRITE");
        assertAuthority(InventoryController.class, "check", "PERM_INVENTORY_WRITE");
        assertAuthority(InventoryController.class, "complete", "PERM_INVENTORY_WRITE");
        assertAuthority(InventoryController.class, "resolve", "PERM_INVENTORY_WRITE");
        assertAuthority(InventoryController.class, "get", "PERM_INVENTORY_READ");
    }

    private void assertAuthority(Class<?> type, String methodName, String permission) {
        Method method = Arrays.stream(type.getDeclaredMethods())
                .filter(candidate -> candidate.getName().equals(methodName))
                .findFirst().orElseThrow();
        PreAuthorize annotation = method.getAnnotation(PreAuthorize.class);
        assertThat(annotation).isNotNull();
        assertThat(annotation.value()).contains(permission);
    }
}
