package com.hrplatform.employee;

import com.hrplatform.audit.OperationLogService;
import com.hrplatform.common.security.DataScope;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EmployeeServiceTest {
    @Mock
    private EmployeeMapper mapper;
    @Mock
    private OperationLogService operationLogService;

    @Test
    void duplicateEmployeeNumberIsRejectedBeforeInsert() {
        when(mapper.findByEmployeeNo("EMP-001")).thenReturn(new Employee(
                1L, "EMP-001", "已有员工", null, 10L, 20L, EmployeeStatus.ACTIVE, null, null
        ));
        EmployeeService service = new EmployeeService(mapper, operationLogService);

        assertThatThrownBy(() -> service.create(new EmployeeCreateRequest(
                        "EMP-001", "新员工", null, 10L, 20L, EmployeeStatus.ACTIVE, null
                ), 99L))
                .isInstanceOf(EmployeeExistsException.class);
    }

    @Test
    void employeeCreateWritesAndReturnsPersistedRecord() {
        Employee persisted = new Employee(2L, "EMP-002", "新员工", "13800000000", 10L, 20L,
                EmployeeStatus.ACTIVE, null, null);
        when(mapper.findByEmployeeNo("EMP-002")).thenReturn(null);
        when(mapper.insert(org.mockito.ArgumentMatchers.any(Employee.class))).thenReturn(persisted);

        Employee result = new EmployeeService(mapper, operationLogService).create(new EmployeeCreateRequest(
                "EMP-002", "新员工", "13800000000", 10L, 20L, EmployeeStatus.ACTIVE, null
        ), 99L);

        assertThat(result.id()).isEqualTo(2L);
        assertThat(result.employeeNo()).isEqualTo("EMP-002");
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
}
