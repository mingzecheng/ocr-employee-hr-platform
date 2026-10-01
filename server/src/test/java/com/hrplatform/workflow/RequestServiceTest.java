package com.hrplatform.workflow;

import com.hrplatform.common.security.DataScope;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RequestServiceTest {
    @Mock
    private RequestMapper mapper;
    @Mock
    private com.hrplatform.employee.EmployeeMapper employeeMapper;

    @Test
    void departmentAndHrApprovalUpdatesRequestAndEmployee() {
        HrRequest draft = new HrRequest(10L, "HR-001", "ONBOARDING", 7L, 9L, 20L, 9L, 2L,
                "{}", "DRAFT", null, 0, LocalDateTime.now(), null, null);
        when(mapper.findByIdWithScope(10L, null, 9L, DataScope.Type.DEPARTMENT.name())).thenReturn(draft);
        when(mapper.updateState(any())).thenReturn(1);
        when(mapper.findByIdWithScope(10L, null, null, DataScope.Type.ALL.name())).thenReturn(
                new HrRequest(10L, "HR-001", "ONBOARDING", 7L, 9L, 20L, 9L, 2L,
                        "{}", "PENDING_HR_APPROVAL", "HR", 1, draft.createdAt(), null, null));
        when(employeeMapper.updateStatusAndOrganization(any())).thenReturn(1);

        RequestService service = new RequestService(mapper, employeeMapper);
        RequestActor manager = new RequestActor(20L, Set.of("DEPT_MANAGER"),
                new DataScope(DataScope.Type.DEPARTMENT, 20L, null, 9L));
        RequestActor hr = new RequestActor(30L, Set.of("HR_ADMIN"),
                new DataScope(DataScope.Type.ALL, 30L, null, null));

        HrRequest submitted = service.submit(10L, manager);
        assertThat(submitted.status()).isEqualTo("PENDING_DEPT_APPROVAL");
        when(mapper.findByIdWithScope(10L, null, 9L, DataScope.Type.DEPARTMENT.name())).thenReturn(
                submitted);
        HrRequest pendingHr = service.approve(10L, manager, "同意");
        assertThat(pendingHr.status()).isEqualTo("PENDING_HR_APPROVAL");
        HrRequest approved = service.approve(10L, hr, "确认");
        assertThat(approved.status()).isEqualTo("APPROVED");
    }
}
