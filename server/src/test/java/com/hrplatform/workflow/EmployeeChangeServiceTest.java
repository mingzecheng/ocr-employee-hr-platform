package com.hrplatform.workflow;

import com.hrplatform.auth.SysUserMapper;
import com.hrplatform.common.security.DataScope;
import com.hrplatform.employee.Employee;
import com.hrplatform.employee.EmployeeMapper;
import com.hrplatform.employee.EmployeeStatus;
import com.hrplatform.organization.OrganizationMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EmployeeChangeServiceTest {
    @Mock
    private RequestMapper requestMapper;
    @Mock
    private EmployeeMapper employeeMapper;
    @Mock
    private SysUserMapper sysUserMapper;
    @Mock
    private OrganizationMapper organizationMapper;

    @Test
    void crossDepartmentTransferSynchronizesAccountAndRevokesOldManagerAssignment() {
        HrRequest request = transferRequest(9L, 10L);
        when(employeeMapper.findByIdWithScope(7L, null, null, DataScope.Type.ALL.name()))
                .thenReturn(new Employee(7L, "EMP-007", "演示员工", null, 9L, 20L,
                        EmployeeStatus.ACTIVE, null, null));
        when(employeeMapper.updateStatusAndOrganization(any())).thenReturn(1);
        when(requestMapper.markEmployeeChangeApplied(eq(16L), any())).thenReturn(1);

        LocalDateTime appliedAt = new EmployeeChangeService(requestMapper, employeeMapper,
                sysUserMapper, organizationMapper).applyApprovedChange(request, 30L);

        assertThat(appliedAt).isNotNull();
        verify(sysUserMapper).updateDepartmentForEmployee(7L, 10L);
        verify(organizationMapper).clearManagerAssignmentForEmployee(7L);
        verify(sysUserMapper).deleteUserRoleForEmployee(7L, "DEPT_MANAGER");
    }

    @Test
    void sameDepartmentPositionChangeKeepsManagerAssignment() {
        HrRequest request = transferRequest(9L, 9L);
        when(employeeMapper.findByIdWithScope(7L, null, null, DataScope.Type.ALL.name()))
                .thenReturn(new Employee(7L, "EMP-007", "演示员工", null, 9L, 20L,
                        EmployeeStatus.ACTIVE, null, null));
        when(employeeMapper.updateStatusAndOrganization(any())).thenReturn(1);
        when(requestMapper.markEmployeeChangeApplied(eq(16L), any())).thenReturn(1);

        new EmployeeChangeService(requestMapper, employeeMapper, sysUserMapper, organizationMapper)
                .applyApprovedChange(request, 30L);

        verify(sysUserMapper).updateDepartmentForEmployee(7L, 9L);
        verify(organizationMapper, org.mockito.Mockito.never()).clearManagerAssignmentForEmployee(7L);
        verify(sysUserMapper, org.mockito.Mockito.never()).deleteUserRoleForEmployee(7L, "DEPT_MANAGER");
    }

    private HrRequest transferRequest(Long oldDepartmentId, Long targetDepartmentId) {
        return new HrRequest(16L, "HR-016", "TRANSFER", 7L, targetDepartmentId, oldDepartmentId, 30L, 20L,
                null, "{}", "APPROVED", null, 2, LocalDateTime.now(), LocalDateTime.now(), null,
                null, null, null, null, LocalDate.now(), null, null, null, null, "岗位调整", null);
    }
}
