package com.hrplatform.organization;

import com.hrplatform.audit.OperationLogService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrganizationAdminServiceTest {
    @Mock
    private OrganizationMapper mapper;
    @Mock
    private OperationLogService operationLogService;

    @Test
    void duplicateDepartmentCodeIsRejectedBeforeInsert() {
        when(mapper.departmentCodeExists("OPS", null)).thenReturn(true);

        assertThatThrownBy(() -> service().createDepartment(department("OPS", null, null, true), 7L))
                .isInstanceOf(OrganizationStateException.class)
                .hasMessageContaining("编码已存在");

        verify(mapper, never()).insertDepartment(any());
    }

    @Test
    void departmentCannotBeMovedUnderItsOwnDescendant() {
        when(mapper.findDepartment(10L)).thenReturn(departmentView(10L, null, true));
        when(mapper.findDepartment(20L)).thenReturn(departmentView(20L, 10L, true));

        assertThatThrownBy(() -> service().updateDepartment(10L, department("OPS", 20L, null, true), 7L))
                .isInstanceOf(OrganizationStateException.class)
                .hasMessageContaining("层级循环");

        verify(mapper, never()).updateDepartment(eq(10L), any());
    }

    @Test
    void inactiveEmployeeCannotBeAssignedAsDepartmentManager() {
        when(mapper.departmentCodeExists("OPS", 10L)).thenReturn(false);
        when(mapper.findDepartment(10L)).thenReturn(departmentView(10L, null, true));
        when(mapper.isActiveEmployee(99L)).thenReturn(false);

        assertThatThrownBy(() -> service().updateDepartment(10L, department("OPS", null, 99L, true), 7L))
                .isInstanceOf(OrganizationStateException.class)
                .hasMessageContaining("在职员工");

        verify(mapper, never()).updateDepartment(eq(10L), any());
    }

    @Test
    void departmentWithEnabledChildrenCannotBeDisabled() {
        when(mapper.departmentCodeExists("OPS", 10L)).thenReturn(false);
        when(mapper.findDepartment(10L)).thenReturn(departmentView(10L, null, true));
        when(mapper.hasEnabledChildren(10L)).thenReturn(true);

        assertThatThrownBy(() -> service().updateDepartment(10L, department("OPS", null, null, false), 7L))
                .isInstanceOf(OrganizationStateException.class)
                .hasMessageContaining("启用子部门");

        verify(mapper, never()).updateDepartment(eq(10L), any());
    }

    @Test
    void departmentWithActiveEmployeesCannotBeDisabled() {
        when(mapper.departmentCodeExists("OPS", 10L)).thenReturn(false);
        when(mapper.findDepartment(10L)).thenReturn(departmentView(10L, null, true));
        when(mapper.hasActiveEmployees(10L)).thenReturn(true);

        assertThatThrownBy(() -> service().updateDepartment(10L, department("OPS", null, null, false), 7L))
                .isInstanceOf(OrganizationStateException.class)
                .hasMessageContaining("在职员工");

        verify(mapper, never()).updateDepartment(eq(10L), any());
    }

    @Test
    void positionCannotBeUpdatedThroughAnotherDepartmentRoute() {
        when(mapper.findDepartment(8L)).thenReturn(departmentView(8L, null, true));
        when(mapper.findPosition(22L)).thenReturn(new PositionAdminView(22L, 3L, "OPS-1", "运营专员", true));

        assertThatThrownBy(() -> service().updatePosition(8L, 22L,
                        new PositionMutationRequest("OPS-1", "运营专员", true), 7L))
                .isInstanceOf(OrganizationStateException.class)
                .hasMessageContaining("不属于指定部门");

        verify(mapper, never()).updatePosition(eq(8L), eq(22L), any());
    }

    @Test
    void departmentCreationWritesAnAuditRecord() {
        when(mapper.departmentCodeExists("OPS", null)).thenReturn(false);
        when(mapper.insertDepartment(any())).thenReturn(1);
        when(mapper.findDepartmentByCode("OPS")).thenReturn(
                new DepartmentAdminView(33L, "OPS", "运营部", null, null, 10, true));

        service().createDepartment(department("OPS", null, null, true), 7L);

        verify(operationLogService).record(eq(7L), eq("CREATE"), eq("ORG_DEPARTMENT"), eq(33L),
                eq("SUCCESS"), eq(null), eq(null));
    }

    @Test
    void departmentWithChildrenCannotBeDeleted() {
        when(mapper.findDepartment(10L)).thenReturn(departmentView(10L, null, true));
        when(mapper.countChildren(10L)).thenReturn(2);

        assertThatThrownBy(() -> service().deleteDepartment(10L, 7L))
                .isInstanceOf(OrganizationDeletionException.class)
                .hasMessageContaining("2 个子部门");

        verify(mapper, never()).deleteDepartment(eq(10L));
    }

    @Test
    void departmentWithPositionsCannotBeDeleted() {
        when(mapper.findDepartment(10L)).thenReturn(departmentView(10L, null, true));
        when(mapper.countChildren(10L)).thenReturn(0);
        when(mapper.countPositions(10L)).thenReturn(3);

        assertThatThrownBy(() -> service().deleteDepartment(10L, 7L))
                .isInstanceOf(OrganizationDeletionException.class)
                .hasMessageContaining("3 个岗位");

        verify(mapper, never()).deleteDepartment(eq(10L));
    }

    @Test
    void departmentWithEmployeesCannotBeDeleted() {
        when(mapper.findDepartment(10L)).thenReturn(departmentView(10L, null, true));
        when(mapper.countChildren(10L)).thenReturn(0);
        when(mapper.countPositions(10L)).thenReturn(0);
        when(mapper.countEmployeesByDepartment(10L)).thenReturn(5);

        assertThatThrownBy(() -> service().deleteDepartment(10L, 7L))
                .isInstanceOf(OrganizationDeletionException.class)
                .hasMessageContaining("5 名员工");

        verify(mapper, never()).deleteDepartment(eq(10L));
    }

    @Test
    void positionWithActiveEmployeesCannotBeDeleted() {
        when(mapper.findDepartment(8L)).thenReturn(departmentView(8L, null, true));
        when(mapper.findPosition(22L)).thenReturn(new PositionAdminView(22L, 8L, "DEV", "后端工程师", true));
        when(mapper.countActiveEmployeesByPosition(22L)).thenReturn(4);

        assertThatThrownBy(() -> service().deletePosition(8L, 22L, 7L))
                .isInstanceOf(OrganizationDeletionException.class)
                .hasMessageContaining("4 名在职员工");

        verify(mapper, never()).deletePosition(eq(8L), eq(22L));
    }

    @Test
    void deletableDepartmentIsRemovedAndAudited() {
        when(mapper.findDepartment(10L)).thenReturn(
                new DepartmentAdminView(10L, "LEGACY", "临时部", null, null, 10, true));
        when(mapper.countChildren(10L)).thenReturn(0);
        when(mapper.countPositions(10L)).thenReturn(0);
        when(mapper.countEmployeesByDepartment(10L)).thenReturn(0);
        when(mapper.deleteDepartment(10L)).thenReturn(1);

        service().deleteDepartment(10L, 7L);

        verify(mapper).deleteDepartment(10L);
        verify(operationLogService).record(eq(7L), eq("DELETE"), eq("ORG_DEPARTMENT"), eq(10L),
                eq("SUCCESS"), eq("临时部"), eq(null));
    }

    @Test
    void deletablePositionIsRemovedAndAudited() {
        when(mapper.findDepartment(8L)).thenReturn(departmentView(8L, null, true));
        when(mapper.findPosition(22L)).thenReturn(new PositionAdminView(22L, 8L, "DEV", "后端工程师", true));
        when(mapper.countActiveEmployeesByPosition(22L)).thenReturn(0);
        when(mapper.deletePosition(8L, 22L)).thenReturn(1);

        service().deletePosition(8L, 22L, 7L);

        verify(mapper).deletePosition(8L, 22L);
        verify(operationLogService).record(eq(7L), eq("DELETE"), eq("ORG_POSITION"), eq(22L),
                eq("SUCCESS"), eq("后端工程师"), eq(null));
    }

    @Test
    void deletingUnknownDepartmentThrowsStateError() {
        when(mapper.findDepartment(999L)).thenReturn(null);

        assertThatThrownBy(() -> service().deleteDepartment(999L, 7L))
                .isInstanceOf(OrganizationStateException.class)
                .hasMessageContaining("部门不存在");

        verify(mapper, never()).deleteDepartment(eq(999L));
    }

    private OrganizationService service() {
        return new OrganizationService(mapper, operationLogService);
    }

    private DepartmentMutationRequest department(String code, Long parentId, Long managerId, boolean enabled) {
        return new DepartmentMutationRequest(code, "运营部", parentId, managerId, 10, enabled);
    }

    private DepartmentAdminView departmentView(Long id, Long parentId, boolean enabled) {
        return new DepartmentAdminView(id, "OPS", "运营部", parentId, null, 10, enabled);
    }
}
