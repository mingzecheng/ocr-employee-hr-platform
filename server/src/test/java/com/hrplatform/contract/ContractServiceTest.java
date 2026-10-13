package com.hrplatform.contract;

import com.hrplatform.archive.ArchiveMapper;
import com.hrplatform.audit.OperationLogService;
import com.hrplatform.common.security.DataScope;
import com.hrplatform.employee.Employee;
import com.hrplatform.employee.EmployeeMapper;
import com.hrplatform.employee.EmployeeStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ContractServiceTest {
    @Mock private ContractMapper mapper;
    @Mock private EmployeeMapper employeeMapper;
    @Mock private ArchiveMapper archiveMapper;
    @Mock private OperationLogService operationLogService;

    @Test
    void createsContractAndWritesAuditRecord() {
        LocalDate signed = LocalDate.now().minusDays(1);
        ContractRecord saved = new ContractRecord(31L, "CTR-DEMO", 7L, "LABOR",
                signed, LocalDate.now(), LocalDate.now().plusYears(1), "ACTIVE", "NOT_DUE",
                null, null, null, 0, 4L, LocalDateTime.now(), LocalDateTime.now());
        when(employeeMapper.findByIdWithScope(7L, null, null, DataScope.Type.ALL.name()))
                .thenReturn(new Employee(7L, "DEMO-007", "演示员工", null, 9L, 20L,
                        EmployeeStatus.ACTIVE, null, null));
        when(mapper.insert(any(ContractRecord.class))).thenReturn(1);
        when(mapper.findByContractNo(anyString())).thenReturn(saved);

        ContractRecord result = service().create(new ContractCreateRequest(7L, "labor", signed,
                LocalDate.now(), LocalDate.now().plusYears(1), null, null, "演示用"),
                new DataScope(DataScope.Type.ALL, 4L, null, null), 4L);

        assertThat(result.id()).isEqualTo(31L);
        assertThat(result.contractType()).isEqualTo("LABOR");
        verify(operationLogService).record(eq(4L), eq("CREATE"), eq("EMPLOYEE_CONTRACT"),
                eq(31L), eq("SUCCESS"));
    }

    @Test
    void rejectsContractDatesThatEndBeforeTheyStart() {
        LocalDate signed = LocalDate.now();

        assertThatThrownBy(() -> service().create(new ContractCreateRequest(7L, "LABOR", signed,
                signed.plusDays(2), signed.plusDays(1), null, null, null),
                new DataScope(DataScope.Type.ALL, 4L, null, null), 4L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("签署日期");
    }

    @Test
    void departmentContractPageCannotEscapeItsDepartment() {
        DataScope scope = new DataScope(DataScope.Type.DEPARTMENT, 9L, null, 12L);
        LocalDate dueFrom = LocalDate.now();
        LocalDate dueThrough = dueFrom.plusDays(60);
        when(mapper.list(null, null, null, null, 12L, DataScope.Type.DEPARTMENT.name(),
                dueFrom, dueThrough, 0, 25))
                .thenReturn(List.of());
        when(mapper.count(null, null, null, null, 12L, DataScope.Type.DEPARTMENT.name(),
                dueFrom, dueThrough)).thenReturn(0L);

        ContractPage page = service().list(null, null, null, 1, 25, scope);

        assertThat(page.total()).isZero();
        verify(mapper).list(null, null, null, null, 12L, DataScope.Type.DEPARTMENT.name(),
                dueFrom, dueThrough, 0, 25);
    }

    @Test
    void derivesDueRenewalStatusForContractsInsideTheWindow() {
        LocalDate today = LocalDate.now();
        ContractRecord contract = new ContractRecord(40L, "CTR-DUE", 7L, "LABOR",
                today.minusYears(1), today.minusYears(1), today.plusDays(60), "ACTIVE", "NOT_DUE",
                null, null, null, 0, 4L, LocalDateTime.now(), LocalDateTime.now());
        when(mapper.list(null, null, "DUE", null, null, DataScope.Type.ALL.name(),
                today, today.plusDays(60), 0, 20)).thenReturn(List.of(contract));
        when(mapper.count(null, null, "DUE", null, null, DataScope.Type.ALL.name(),
                today, today.plusDays(60))).thenReturn(1L);

        ContractPage page = service().list(null, null, "DUE", 1, 20,
                new DataScope(DataScope.Type.ALL, 4L, null, null));

        assertThat(page.items()).extracting(ContractRecord::renewalStatus).containsExactly("DUE");
    }

    private ContractService service() {
        return new ContractService(mapper, employeeMapper, archiveMapper, operationLogService);
    }
}
