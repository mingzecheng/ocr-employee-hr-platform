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

        verify(operationLogService).record(7L, "CREATE", "ORG_DEPARTMENT", 33L, "SUCCESS");
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
