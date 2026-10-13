package com.hrplatform.employee;

import com.hrplatform.audit.OperationLogService;
import com.hrplatform.common.security.DataScope;
import com.hrplatform.organization.OrganizationMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EmployeeServiceTest {
    @Mock
    private EmployeeMapper mapper;
    @Mock
    private OperationLogService operationLogService;
    @Mock
    private OrganizationMapper organizationMapper;

    @Test
    void employeeNumberIsGeneratedWhenCreatingRecord() {
        when(mapper.insert(org.mockito.ArgumentMatchers.any(Employee.class))).thenReturn(1);
        when(mapper.findByEmployeeNo(org.mockito.ArgumentMatchers.anyString()))
                .thenAnswer(invocation -> new Employee(1L, invocation.getArgument(0), "新员工", null,
                        10L, 20L, EmployeeStatus.ACTIVE, null, null));
        EmployeeService service = new EmployeeService(mapper, operationLogService);

        Employee created = service.create(new EmployeeCreateRequest(
                "新员工", null, 10L, 20L, EmployeeStatus.ACTIVE, null), 99L);

        assertThat(created.employeeNo()).matches("EMP-\\d{4}-[A-F0-9]{20}");
    }

    @Test
    void employeeCreateWritesAndReturnsPersistedRecord() {
        when(mapper.insert(org.mockito.ArgumentMatchers.any(Employee.class))).thenReturn(1);
        when(mapper.findByEmployeeNo(org.mockito.ArgumentMatchers.anyString()))
                .thenAnswer(invocation -> new Employee(2L, invocation.getArgument(0), "新员工", "13800000000",
                        10L, 20L, EmployeeStatus.ACTIVE, null, null));
        when(organizationMapper.isEnabledPosition(20L, 10L)).thenReturn(true);

        Employee result = new EmployeeService(mapper, organizationMapper, operationLogService).create(new EmployeeCreateRequest(
                "新员工", "13800000000", 10L, 20L, EmployeeStatus.ACTIVE, null
        ), 99L);

        assertThat(result.id()).isEqualTo(2L);
        assertThat(result.employeeNo()).matches("EMP-\\d{4}-[A-F0-9]{20}");
    }

    @Test
    void employeeCreateRejectsPositionFromAnotherDepartment() {
        when(organizationMapper.isEnabledPosition(30L, 10L)).thenReturn(false);

        assertThatThrownBy(() -> new EmployeeService(mapper, organizationMapper, operationLogService).create(
                new EmployeeCreateRequest("新员工", null, 10L, 30L,
                        EmployeeStatus.ACTIVE, null), 99L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("不属于所选部门");

        org.mockito.Mockito.verify(mapper, org.mockito.Mockito.never())
                .insert(org.mockito.ArgumentMatchers.any(Employee.class));
    }

    @Test
    void scopeMismatchRaisesDataScopeDenied() {
        when(mapper.findByIdWithScope(7L, null, 100L, DataScope.Type.DEPARTMENT.name())).thenReturn(null);

        assertThatThrownBy(() -> new EmployeeService(mapper, operationLogService)
                .getRequired(7L, new DataScope(DataScope.Type.DEPARTMENT, 5L, null, 100L)))
                .isInstanceOf(DataScopeDeniedException.class);
    }

    @Test
    void listUsesEmployeeScopeAndReturnsPageMetadata() {
        when(mapper.list(null, null, null, 42L, "EMPLOYEE", 0, 20))
                .thenReturn(java.util.List.of(new Employee(42L, "DEMO-42", "演示员工", null,
                        7L, 8L, EmployeeStatus.ACTIVE, null, null)));
        when(mapper.count(null, null, null, 42L, "EMPLOYEE")).thenReturn(1L);

        EmployeePage page = new EmployeeService(mapper, operationLogService).list(null, null, null, 1, 20,
                new DataScope(DataScope.Type.EMPLOYEE, 9L, 42L, 7L));

        assertThat(page.total()).isEqualTo(1L);
        assertThat(page.items()).hasSize(1);
        verify(mapper).list(null, null, null, 42L, "EMPLOYEE", 0, 20);
    }

    @Test
    void updateProfileTrimsNameClearsBlankPhoneAndRecordsAudit() {
        Employee original = new Employee(7L, "EMP-007", "原姓名", "13800138001", 3L, 4L,
                EmployeeStatus.ACTIVE, null, null);
        Employee updated = new Employee(7L, "EMP-007", "新姓名", null, 3L, 4L,
                EmployeeStatus.ACTIVE, null, null);
        when(mapper.findByIdWithScope(7L, null, null, DataScope.Type.ALL.name()))
                .thenReturn(original, updated);
        when(mapper.updateProfile(any(EmployeeProfileMutation.class))).thenReturn(1);

        Employee result = new EmployeeService(mapper, organizationMapper, operationLogService)
                .updateProfile(7L, new EmployeeProfileUpdateRequest("  新姓名  ", "   "), 99L);

        assertThat(result).isEqualTo(updated);
        org.mockito.ArgumentCaptor<EmployeeProfileMutation> mutation =
                org.mockito.ArgumentCaptor.forClass(EmployeeProfileMutation.class);
        verify(mapper).updateProfile(mutation.capture());
        assertThat(mutation.getValue().name()).isEqualTo("新姓名");
        assertThat(mutation.getValue().phone()).isNull();
        verify(operationLogService).record(99L, "UPDATE", "EMPLOYEE", 7L, "SUCCESS");
    }

    @Test
    void updateProfileRejectsInvalidPhoneWithoutWriting() {
        assertThatThrownBy(() -> new EmployeeService(mapper, organizationMapper, operationLogService)
                .updateProfile(7L, new EmployeeProfileUpdateRequest("新姓名", "123"), 99L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("联系电话");

        verify(mapper, org.mockito.Mockito.never()).updateProfile(any(EmployeeProfileMutation.class));
    }

    @Test
    void updateProfileRejectsBlankNameWithoutWriting() {
        assertThatThrownBy(() -> new EmployeeService(mapper, organizationMapper, operationLogService)
                .updateProfile(7L, new EmployeeProfileUpdateRequest("  ", null), 99L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("姓名");

        verify(mapper, org.mockito.Mockito.never()).updateProfile(any(EmployeeProfileMutation.class));
    }
}
