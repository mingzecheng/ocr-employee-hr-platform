package com.hrplatform.workflow;

import com.hrplatform.auth.SysUserMapper;
import com.hrplatform.employee.Employee;
import com.hrplatform.employee.EmployeeMapper;
import com.hrplatform.employee.EmployeeStatus;
import com.hrplatform.organization.OrganizationMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EmployeeChangeServiceLeaveTest {
    @Mock
    private RequestMapper requestMapper;
    @Mock
    private EmployeeMapper employeeMapper;
    @Mock
    private SysUserMapper sysUserMapper;
    @Mock
    private OrganizationMapper organizationMapper;

    private EmployeeChangeService service() {
        return new EmployeeChangeService(requestMapper, employeeMapper, sysUserMapper, organizationMapper);
    }

    private Employee employee(long id, EmployeeStatus status) {
        return new Employee(id, "EMP-007", "演示员工", null, 9L, 20L, status, null, null);
    }

    @Test
    void markLeaveEffectiveWindowWritesRequestedLeaveDates() {
        LocalDate start = LocalDate.now().minusDays(1);
        LocalDate end = LocalDate.now().plusDays(3);
        HrRequest leave = new HrRequest(12L, "HR-LEAVE-001", "LEAVE", 7L, null, 9L, 20L, 7L, null,
                "{}", "APPROVED", null, 2, null, null, null,
                "ANNUAL", start, end, "休假");

        service().markLeaveEffectiveWindow(leave);

        verify(requestMapper).markLeaveEffectiveWindow(12L, start, end);
    }

    @Test
    void markLeaveEffectiveWindowIgnoresNonLeaveRequests() {
        HrRequest onboarding = new HrRequest(13L, "HR-013", "ONBOARDING", 7L, null, 9L, 20L, 7L, null,
                "{}", "APPROVED", null, 2, null, null, null,
                null, null, null, null);

        service().markLeaveEffectiveWindow(onboarding);

        verify(requestMapper, never()).markLeaveEffectiveWindow(any(), any(), any());
    }

    @Test
    void syncLeaveStatusEntersOnLeaveWhenTodayWithinWindow() {
        LocalDate today = LocalDate.now();
        when(employeeMapper.lockById(7L)).thenReturn(employee(7L, EmployeeStatus.ACTIVE));
        when(employeeMapper.updateStatusAndOrganization(any())).thenReturn(1);
        LeaveStatusWindow window = new LeaveStatusWindow(12L, 7L, today.minusDays(1), today.plusDays(2));

        boolean changed = service().syncLeaveStatus(window, today);

        assertThat(changed).isTrue();
        verify(employeeMapper).updateStatusAndOrganization(
                org.mockito.ArgumentMatchers.argThat(m -> m.status() == EmployeeStatus.ON_LEAVE));
        verify(employeeMapper).insertStatusHistory(org.mockito.ArgumentMatchers.argThat(history ->
                "ACTIVE".equals(history.beforeStatus()) && "ON_LEAVE".equals(history.afterStatus())
                        && Long.valueOf(12L).equals(history.sourceRequestId())));
    }

    @Test
    void syncLeaveStatusReturnsToActiveAfterWindowEnds() {
        LocalDate today = LocalDate.now();
        when(employeeMapper.lockById(7L)).thenReturn(employee(7L, EmployeeStatus.ON_LEAVE));
        when(requestMapper.countOtherActiveLeaveWindows(eq(7L), eq(today), eq(12L))).thenReturn(0L);
        when(employeeMapper.updateStatusAndOrganization(any())).thenReturn(1);
        LeaveStatusWindow window = new LeaveStatusWindow(12L, 7L, today.minusDays(5), today.minusDays(1));

        boolean changed = service().syncLeaveStatus(window, today);

        assertThat(changed).isTrue();
        verify(employeeMapper).updateStatusAndOrganization(
                org.mockito.ArgumentMatchers.argThat(m -> m.status() == EmployeeStatus.ACTIVE));
    }

    @Test
    void syncLeaveStatusIsIdempotentWhenAlreadyInTargetState() {
        LocalDate today = LocalDate.now();
        when(employeeMapper.lockById(7L)).thenReturn(employee(7L, EmployeeStatus.ON_LEAVE));
        LeaveStatusWindow window = new LeaveStatusWindow(12L, 7L, today.minusDays(1), today.plusDays(2));

        boolean changed = service().syncLeaveStatus(window, today);

        assertThat(changed).isFalse();
        verify(employeeMapper, never()).updateStatusAndOrganization(any());
        verify(employeeMapper, never()).insertStatusHistory(any());
    }

    @Test
    void syncLeaveStatusDoesNotFlipBackWhileAnotherLeaveIsStillActive() {
        LocalDate today = LocalDate.now();
        when(employeeMapper.lockById(7L)).thenReturn(employee(7L, EmployeeStatus.ON_LEAVE));
        when(requestMapper.countOtherActiveLeaveWindows(eq(7L), eq(today), eq(12L))).thenReturn(1L);
        LeaveStatusWindow window = new LeaveStatusWindow(12L, 7L, today.minusDays(5), today.minusDays(1));

        boolean changed = service().syncLeaveStatus(window, today);

        assertThat(changed).isFalse();
        verify(employeeMapper, never()).updateStatusAndOrganization(any());
        verify(employeeMapper, never()).insertStatusHistory(any());
    }

    @Test
    void syncLeaveStatusWaitsBeforeWindowStarts() {
        LocalDate today = LocalDate.now();
        when(employeeMapper.lockById(7L)).thenReturn(employee(7L, EmployeeStatus.ACTIVE));
        LeaveStatusWindow window = new LeaveStatusWindow(12L, 7L, today.plusDays(1), today.plusDays(3));

        boolean changed = service().syncLeaveStatus(window, today);

        assertThat(changed).isFalse();
        verify(employeeMapper, never()).updateStatusAndOrganization(any());
    }
}
